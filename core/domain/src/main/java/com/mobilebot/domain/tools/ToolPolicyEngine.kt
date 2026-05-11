package com.mobilebot.domain.tools

import com.mobilebot.bridge.ConnectivityState
import com.mobilebot.bridge.DeviceCapabilityBridge
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolPolicyEngine
    @Inject
    constructor(
        private val probe: DeviceCapabilityProbe,
        private val foreground: ForegroundStateReader,
        private val bridge: DeviceCapabilityBridge,
    ) {
        suspend fun check(tool: Tool): PolicyDecision {
            if (!probe.hasCapabilities(tool.requiredCapabilities)) {
                return PolicyDecision(false, "Required Android capability not granted")
            }
            if (tool.executionPolicy.requiresForeground && !foreground.isInteractiveForeground()) {
                return PolicyDecision(false, "Tool requires foreground interactive state")
            }
            if (tool.executionPolicy.requiresConnectivity) {
                val snap = bridge.appState.snapshot()
                if (snap.connectivity == ConnectivityState.NONE) {
                    return PolicyDecision(false, "This tool needs network connectivity")
                }
            }
            return PolicyDecision(true)
        }
    }
