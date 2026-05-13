package com.mobilebot.data.skills

import android.content.Context
import com.mobilebot.domain.SkillsLoader
import com.mobilebot.domain.skill.SkillManifest
import com.mobilebot.domain.skill.SkillMdParser
import com.mobilebot.domain.skill.SkillSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillsLoaderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SkillsLoader {

    override fun activePrompt(): String {
        val raw = runCatching {
            context.assets.open("skills/interaction/SKILL.md").bufferedReader().use { it.readText() }
        }.getOrDefault("")
        val parsed = SkillMdParser.splitFrontmatterAndBody(raw)
        return parsed?.body?.trim() ?: raw.trim()
    }

    override fun alwaysSkillManifest(): SkillManifest? {
        val raw = runCatching {
            context.assets.open("skills/interaction/SKILL.md").bufferedReader().use { it.readText() }
        }.getOrDefault("")
        if (raw.isBlank()) return null

        val skill = SkillMdParser.parse(raw, SkillSource.ALWAYS_PROMPT) ?: return null
        return skill.manifest.copy(
            id = "always.${skill.manifest.id}",
            always = true,
            source = SkillSource.ALWAYS_PROMPT,
        )
    }
}
