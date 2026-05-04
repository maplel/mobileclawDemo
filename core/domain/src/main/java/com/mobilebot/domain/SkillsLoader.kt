package com.mobilebot.domain

import com.mobilebot.domain.skill.SkillManifest

interface SkillsLoader {
    fun activePrompt(): String

    fun alwaysSkillManifest(): SkillManifest? = null
}
