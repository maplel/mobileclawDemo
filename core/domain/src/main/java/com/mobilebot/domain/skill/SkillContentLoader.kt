package com.mobilebot.domain.skill

/**
 * Loads SKILL.md content from assets or internal storage.
 * Abstracted as an interface so the data layer provides the actual implementation.
 */
interface SkillContentLoader {

    /** Load the full SKILL.md text for a given skill entry. */
    suspend fun loadContent(entry: SkillEntry): String?

    /** Load the full SKILL.md text by path. */
    suspend fun loadContent(path: String): String?
}
