package com.mobilebot.domain.permissions

import kotlinx.coroutines.flow.StateFlow

/**
 * Pending request emitted to the UI layer so it can show a confirmation dialog.
 */
data class CapabilityApprovalRequest(
    val capabilityNames: List<String>,
    val toolName: String,
)

/**
 * Mirrors the standard Android runtime-permission dialog choices (Android 11+).
 */
enum class CapabilityApprovalResult {
    /** 始终 — grant permanently, including background / event-triggered tasks. */
    ALWAYS,

    /** 仅在使用该应用时允许 — grant while the app is in the foreground. */
    WHILE_USING_APP,

    /** 每次都询问 — allow this single invocation; ask again next time. */
    ASK_EVERY_TIME,

    /** 不允许 — deny and do not execute the tool. */
    DENY,
}

/**
 * Bridges capability-permission prompts from [com.mobilebot.domain.agent.ToolCallAgentLoop]
 * (background coroutine) to the UI layer (dialog).
 *
 * Pattern mirrors [com.mobilebot.permissions.PermissionRequestSession]:
 * the runtime calls [requestApproval], which suspends until the UI calls [respond].
 */
interface CapabilityApprovalGate {
    /** Current pending request, observed by the Compose UI to show a dialog. */
    val pendingRequest: StateFlow<CapabilityApprovalRequest?>

    /** Suspends until the user picks one of the four choices. */
    suspend fun requestApproval(request: CapabilityApprovalRequest): CapabilityApprovalResult

    /** Called by the UI when the user makes a choice. */
    fun respond(result: CapabilityApprovalResult)
}
