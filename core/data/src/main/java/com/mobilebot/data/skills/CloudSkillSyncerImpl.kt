package com.mobilebot.data.skills

import android.content.Context
import android.util.Log
import com.mobilebot.domain.skill.CloudSkillManifestEnvelope
import com.mobilebot.domain.skill.CloudSkillSecurityValidator
import com.mobilebot.domain.skill.CloudSkillSyncer
import com.mobilebot.domain.skill.SkillBootstrapper
import com.mobilebot.domain.skill.SkillEntry
import com.mobilebot.domain.skill.SkillMdParser
import com.mobilebot.domain.skill.SkillSource
import com.mobilebot.domain.skill.SkillUpdateInfo
import com.mobilebot.domain.skill.SyncResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSkillSyncerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bootstrapper: SkillBootstrapper,
) : CloudSkillSyncer {

    private val cloudDir: File
        get() = File(context.filesDir, CLOUD_SKILLS_DIR).also { it.mkdirs() }

    override suspend fun sync(): SyncResult {
        var added = 0
        var updated = 0
        var removed = 0
        val errors = mutableListOf<String>()

        try {
            val localSkills = loadLocalCloudSkills()
            for (entry in localSkills) {
                val validation = CloudSkillSecurityValidator.validate(entry.manifest)
                if (!validation.valid) {
                    errors.addAll(validation.errors.map { "${entry.manifest.id}: $it" })
                    continue
                }
                bootstrapper.registerSkillEntries(listOf(entry))
                added++
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloud skill sync failed", e)
            errors.add("Sync failed: ${e.message}")
        }

        return SyncResult(added = added, updated = updated, removed = removed, errors = errors)
    }

    override suspend fun checkForUpdates(): List<SkillUpdateInfo> {
        // TODO: Implement server-side manifest check
        return emptyList()
    }

    fun saveCloudSkill(envelope: CloudSkillManifestEnvelope, content: String): Boolean {
        if (!verifySignature(envelope, content)) {
            Log.w(TAG, "Signature verification failed for ${envelope.skillId}")
            return false
        }

        if (!verifyChecksum(envelope.checksum, content)) {
            Log.w(TAG, "Checksum verification failed for ${envelope.skillId}")
            return false
        }

        val skill = SkillMdParser.parse(content, SkillSource.CLOUD) ?: return false
        val validation = CloudSkillSecurityValidator.validate(skill.manifest)
        if (!validation.valid) {
            Log.w(TAG, "Security validation failed for ${envelope.skillId}: ${validation.errors}")
            return false
        }

        val skillDir = File(cloudDir, envelope.skillId).also { it.mkdirs() }
        File(skillDir, "SKILL.md").writeText(content)
        File(skillDir, ".signature").writeText(envelope.signature)

        val entry = SkillEntry(
            manifest = skill.manifest,
            contentPath = "${CLOUD_SKILLS_DIR}/${envelope.skillId}/SKILL.md",
            source = SkillSource.CLOUD,
        )
        bootstrapper.registerSkillEntries(listOf(entry))

        Log.d(TAG, "Saved and registered cloud skill: ${envelope.skillId}")
        return true
    }

    fun removeCloudSkill(skillId: String): Boolean {
        val skillDir = File(cloudDir, skillId)
        if (!skillDir.exists()) return false
        skillDir.deleteRecursively()
        Log.d(TAG, "Removed cloud skill: $skillId")
        return true
    }

    private fun loadLocalCloudSkills(): List<SkillEntry> {
        val entries = mutableListOf<SkillEntry>()
        val dirs = cloudDir.listFiles()?.filter { it.isDirectory } ?: return entries

        for (dir in dirs) {
            val skillMd = File(dir, "SKILL.md")
            if (!skillMd.exists()) continue

            val content = skillMd.readText()
            val skill = SkillMdParser.parse(content, SkillSource.CLOUD) ?: continue

            entries.add(SkillEntry(
                manifest = skill.manifest,
                contentPath = "${CLOUD_SKILLS_DIR}/${dir.name}/SKILL.md",
                source = SkillSource.CLOUD,
            ))
        }

        return entries
    }

    private fun verifySignature(envelope: CloudSkillManifestEnvelope, content: String): Boolean {
        // TODO: Implement proper signature verification with server public key
        return envelope.signature.isNotBlank()
    }

    private fun verifyChecksum(expectedChecksum: String, content: String): Boolean {
        if (expectedChecksum.isBlank()) return true
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return hash == expectedChecksum
    }

    companion object {
        private const val TAG = "CloudSkillSyncer"
        private const val CLOUD_SKILLS_DIR = "skills/cloud"
    }
}
