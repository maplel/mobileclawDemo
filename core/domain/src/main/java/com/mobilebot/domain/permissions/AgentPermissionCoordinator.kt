package com.mobilebot.domain.permissions

/**
 * Ensures Android runtime permissions are granted for the given capability tokens
 * (e.g. [com.mobilebot.domain.tools] `Tool.requiredCapabilities`) before tool execution or prompt assembly.
 */
fun interface AgentPermissionCoordinator {
    /**
     * Requests any mapped runtime permissions for [capabilities] that are not yet satisfied.
     * Capabilities that only need system settings (no runtime permission mapping) return false if still unsatisfied.
     *
     * @return true if every [capability] in [capabilities] passes [com.mobilebot.domain.tools.DeviceCapabilityProbe] after this call.
     */
    suspend fun ensureRuntimePermissionsForCapabilities(capabilities: Set<String>): Boolean
}
