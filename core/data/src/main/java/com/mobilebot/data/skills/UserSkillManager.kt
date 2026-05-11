package com.mobilebot.data.skills

import android.content.Context
import android.util.Log
import com.mobilebot.domain.skill.SkillBootstrapper
import com.mobilebot.domain.skill.SkillEntry
import com.mobilebot.domain.skill.SkillMdParser
import com.mobilebot.domain.skill.SkillRegistry
import com.mobilebot.domain.skill.SkillSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages user-created skills stored in internal storage.
 *
 * User skills are the highest priority source and only affect the local device.
 * They require a first-use activation confirmation.
 */
@Singleton
class UserSkillManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bootstrapper: SkillBootstrapper,
    private val skillRegistry: SkillRegistry,
) {

    private val userDir: File
        get() = File(context.filesDir, USER_SKILLS_DIR).also { it.mkdirs() }

    private val activatedSkills = mutableSetOf<String>()

    fun loadUserSkills() {
        val dirs = userDir.listFiles()?.filter { it.isDirectory } ?: return
        for (dir in dirs) {
            val skillMd = File(dir, "SKILL.md")
            if (!skillMd.exists()) continue

            val content = skillMd.readText()
            val skill = SkillMdParser.parse(content, SkillSource.USER) ?: continue
            val activated = File(dir, ".activated").exists()

            if (activated) {
                activatedSkills.add(skill.manifest.id)
            }

            val entry = SkillEntry(
                manifest = skill.manifest,
                contentPath = "${USER_SKILLS_DIR}/${dir.name}/SKILL.md",
                source = SkillSource.USER,
                eligible = activated,
                ineligibleReason = if (!activated) "User skill not yet activated" else null,
            )
            bootstrapper.registerSkillEntries(listOf(entry))
            Log.d(TAG, "Loaded user skill: ${skill.manifest.id} (activated=$activated)")
        }
    }

    fun saveUserSkill(skillId: String, content: String): SaveResult {
        val skill = SkillMdParser.parse(content, SkillSource.USER)
            ?: return SaveResult(success = false, error = "Invalid SKILL.md format")

        val dirName = skillId.ifBlank { skill.manifest.id }
        val skillDir = File(userDir, dirName).also { it.mkdirs() }
        File(skillDir, "SKILL.md").writeText(content)

        val alreadyActivated = File(skillDir, ".activated").exists()
        val entry = SkillEntry(
            manifest = skill.manifest,
            contentPath = "${USER_SKILLS_DIR}/$dirName/SKILL.md",
            source = SkillSource.USER,
            eligible = alreadyActivated,
            ineligibleReason = if (!alreadyActivated) "User skill not yet activated" else null,
        )
        bootstrapper.registerSkillEntries(listOf(entry))
        skillRegistry.invalidateSnapshot()

        Log.d(TAG, "Saved user skill: ${skill.manifest.id}")
        return SaveResult(success = true, skillId = skill.manifest.id, needsActivation = !alreadyActivated)
    }

    fun activateSkill(skillId: String): Boolean {
        val dirs = userDir.listFiles()?.filter { it.isDirectory } ?: return false
        for (dir in dirs) {
            val skillMd = File(dir, "SKILL.md")
            if (!skillMd.exists()) continue

            val content = skillMd.readText()
            val skill = SkillMdParser.parse(content, SkillSource.USER) ?: continue
            if (skill.manifest.id != skillId) continue

            File(dir, ".activated").writeText("activated")
            activatedSkills.add(skillId)

            val entry = SkillEntry(
                manifest = skill.manifest,
                contentPath = "${USER_SKILLS_DIR}/${dir.name}/SKILL.md",
                source = SkillSource.USER,
                eligible = true,
            )
            bootstrapper.registerSkillEntries(listOf(entry))
            skillRegistry.invalidateSnapshot()

            Log.d(TAG, "Activated user skill: $skillId")
            return true
        }
        return false
    }

    fun deleteUserSkill(skillId: String): Boolean {
        val dirs = userDir.listFiles()?.filter { it.isDirectory } ?: return false
        for (dir in dirs) {
            val skillMd = File(dir, "SKILL.md")
            if (!skillMd.exists()) continue

            val content = skillMd.readText()
            val skill = SkillMdParser.parse(content, SkillSource.USER) ?: continue
            if (skill.manifest.id != skillId) continue

            dir.deleteRecursively()
            skillRegistry.unregister(skillId)
            activatedSkills.remove(skillId)
            skillRegistry.invalidateSnapshot()

            Log.d(TAG, "Deleted user skill: $skillId")
            return true
        }
        return false
    }

    fun listUserSkills(): List<UserSkillInfo> {
        val result = mutableListOf<UserSkillInfo>()
        val dirs = userDir.listFiles()?.filter { it.isDirectory } ?: return result

        for (dir in dirs) {
            val skillMd = File(dir, "SKILL.md")
            if (!skillMd.exists()) continue

            val content = skillMd.readText()
            val skill = SkillMdParser.parse(content, SkillSource.USER) ?: continue
            val activated = File(dir, ".activated").exists()

            result.add(UserSkillInfo(
                id = skill.manifest.id,
                name = skill.manifest.name,
                description = skill.manifest.description,
                category = skill.manifest.category,
                activated = activated,
                filePath = skillMd.absolutePath,
            ))
        }

        return result
    }

    fun getUserSkillContent(skillId: String): String? {
        val dirs = userDir.listFiles()?.filter { it.isDirectory } ?: return null
        for (dir in dirs) {
            val skillMd = File(dir, "SKILL.md")
            if (!skillMd.exists()) continue

            val content = skillMd.readText()
            val skill = SkillMdParser.parse(content, SkillSource.USER) ?: continue
            if (skill.manifest.id == skillId) return content
        }
        return null
    }

    data class SaveResult(
        val success: Boolean,
        val skillId: String? = null,
        val needsActivation: Boolean = false,
        val error: String? = null,
    )

    data class UserSkillInfo(
        val id: String,
        val name: String,
        val description: String,
        val category: String?,
        val activated: Boolean,
        val filePath: String,
    )

    companion object {
        private const val TAG = "UserSkillManager"
        private const val USER_SKILLS_DIR = "skills/user"
    }
}
