package com.mobilebot.domain.skill

import com.mobilebot.domain.tools.ToolRisk

enum class SkillSource(val priority: Int) {
    ALWAYS_PROMPT(50),
    BUILTIN(100),
    BUNDLED_ASSET(200),
    CLOUD(300),
    USER(400),
}

data class SkillRequirements(
    val permissions: List<String> = emptyList(),
    val connectivity: Boolean = false,
    val apps: List<String> = emptyList(),
    val minApi: Int = 0,
)

data class SkillConditions(
    val time: String? = null,
    val locationType: String? = null,
    val deviceState: String? = null,
)

data class SkillManifest(
    val id: String,
    val name: String,
    val description: String,
    val version: String = "1",
    val source: SkillSource,
    val category: String? = null,

    // --- Execution strategy (from Claude Code) ---
    val allowedTools: List<String> = emptyList(),
    val context: SkillContext = SkillContext.INLINE,
    val effort: SkillEffort = SkillEffort.MEDIUM,

    // --- Mobile security ---
    val risk: ToolRisk = ToolRisk.LOW,
    val requires: SkillRequirements = SkillRequirements(),
    val conditions: SkillConditions? = null,

    // --- Orchestration ---
    val composesSkills: List<String> = emptyList(),
    val userConfirmationPoints: List<String> = emptyList(),
    val requiredServices: List<String> = emptyList(),
    val references: List<String> = emptyList(),
    val runtimeContext: List<String> = emptyList(),

    // --- Invocation control ---
    val always: Boolean = false,
    val disableModelInvocation: Boolean = false,

    // --- Content ---
    val promptSummary: String = description,
    val promptBody: String? = null,

) {
    val effectiveAllowedTools: List<String>
        get() = allowedTools
}

enum class SkillContext {
    INLINE,
    FORK,
    ;

    companion object {
        fun fromString(value: String?): SkillContext =
            when (value?.lowercase()?.trim()) {
                "fork" -> FORK
                else -> INLINE
            }
    }
}

enum class SkillEffort {
    LOW,
    MEDIUM,
    HIGH,
    ;

    companion object {
        fun fromString(value: String?): SkillEffort =
            when (value?.lowercase()?.trim()) {
                "low" -> LOW
                "high" -> HIGH
                else -> MEDIUM
            }
    }
}

data class SkillEntry(
    val manifest: SkillManifest,
    val contentPath: String,
    val source: SkillSource,
    val eligible: Boolean = true,
    val ineligibleReason: String? = null,
)

data class SkillSnapshot(
    val catalogPrompt: String,
    val eligibleSkillIds: List<String>,
    val version: Int,
    val builtAt: Long = System.currentTimeMillis(),
)

interface Skill {
    val manifest: SkillManifest

    val id: String get() = manifest.id
    val name: String get() = manifest.name
    val description: String get() = manifest.description

    fun toEntry(
        contentPath: String = "",
        eligible: Boolean = true,
        ineligibleReason: String? = null,
    ): SkillEntry = SkillEntry(
        manifest = manifest,
        contentPath = contentPath,
        source = manifest.source,
        eligible = eligible,
        ineligibleReason = ineligibleReason,
    )
}

data class FileBackedSkill(
    override val manifest: SkillManifest,
) : Skill

fun compareSkillVersion(left: String, right: String): Int {
    val leftParts = left.split('.', '-', '_')
    val rightParts = right.split('.', '-', '_')
    val max = maxOf(leftParts.size, rightParts.size)
    for (i in 0 until max) {
        val lp = leftParts.getOrNull(i).orEmpty()
        val rp = rightParts.getOrNull(i).orEmpty()
        val ln = lp.toIntOrNull()
        val rn = rp.toIntOrNull()
        val cmp = when {
            ln != null && rn != null -> ln.compareTo(rn)
            else -> lp.compareTo(rp)
        }
        if (cmp != 0) return cmp
    }
    return 0
}
