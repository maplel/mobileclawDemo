package com.mobilebot.domain.capabilities

import com.mobilebot.domain.tools.Tool

/**
 * Whether a tool is shown to the JSON planner for this turn.
 *
 * **Always true:** permission and hardware gating belong at execution time
 * ([com.mobilebot.domain.permissions.AgentPermissionCoordinator] + in-app flows), not here.
 * Hiding tools when [CapabilitySnapshot] says permission is missing caused the model to claim
 * capabilities were "unavailable" even though the app can still prompt for runtime permissions before execute.
 */
object ToolCapabilityFilter {
    @Suppress("UNUSED_PARAMETER")
    fun visible(
        tool: Tool,
        snapshot: CapabilitySnapshot,
    ): Boolean = true
}
