package com.mobilebot.domain.skill

import com.mobilebot.domain.tools.ToolRisk

/**
 * Syncs skills from a cloud endpoint. Downloads SKILL.md files,
 * verifies signatures, and registers them.
 *
 * Cloud skills have security restrictions:
 * - Risk level cannot exceed MEDIUM
 * - High-risk tools (dial_number, send_sms) are not allowed
 * - Content is signature-verified
 */
interface CloudSkillSyncer {
    suspend fun sync(): SyncResult
    suspend fun checkForUpdates(): List<SkillUpdateInfo>
}

data class SyncResult(
    val added: Int,
    val updated: Int,
    val removed: Int,
    val errors: List<String>,
)

data class SkillUpdateInfo(
    val skillId: String,
    val currentVersion: String?,
    val availableVersion: String,
    val action: UpdateAction,
)

enum class UpdateAction { ADD, UPDATE, REMOVE }

data class CloudSkillManifestEnvelope(
    val skillId: String,
    val version: String,
    val signature: String,
    val contentUrl: String,
    val checksum: String,
)

/**
 * Validates cloud skills against security policies before registration.
 */
object CloudSkillSecurityValidator {

    private val BLOCKED_TOOLS_FOR_CLOUD = setOf(
        "dial_number",
        "send_sms",
        "open_camera",
        "spawn_subtask",
    )

    fun validate(manifest: SkillManifest): ValidationResult {
        val errors = mutableListOf<String>()

        if (manifest.risk.ordinal > ToolRisk.MEDIUM.ordinal) {
            errors.add("Cloud skills cannot have risk level above MEDIUM (got ${manifest.risk})")
        }

        val blockedTools = manifest.effectiveAllowedTools.filter { it in BLOCKED_TOOLS_FOR_CLOUD }
        if (blockedTools.isNotEmpty()) {
            errors.add("Cloud skills cannot use high-risk tools: ${blockedTools.joinToString(", ")}")
        }

        if (manifest.context == SkillContext.FORK && manifest.composesSkills.isNotEmpty()) {
            errors.add("Cloud skills cannot orchestrate other skills via composes-skills")
        }

        return if (errors.isEmpty()) ValidationResult.ok()
        else ValidationResult.fail(errors)
    }

    data class ValidationResult(
        val valid: Boolean,
        val errors: List<String> = emptyList(),
    ) {
        companion object {
            fun ok() = ValidationResult(valid = true)
            fun fail(errors: List<String>) = ValidationResult(valid = false, errors = errors)
        }
    }
}
