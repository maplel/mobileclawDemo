package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class ToggleFlashlightTool
    @Inject
    constructor(
        private val bridge: DeviceCapabilityBridge,
    ) : Tool {
        override val name: String = "toggle_flashlight"

        override val executionPolicy: ToolExecutionPolicy =
            ToolExecutionPolicy(hasSideEffects = true, requiresForeground = true)

        override val definition =
            ToolDefinition(
                name = name,
                description = "Turn the flashlight (torch) on or off. 开关手电筒。",
                parametersSchema =
                    """{"type":"object","properties":{"on":{"type":"boolean","description":"true to turn on, false to turn off"}},"required":["on"]}""",
            )

        override suspend fun execute(argumentsJson: String): ToolResult =
            try {
                val on = JSONObject(argumentsJson).getBoolean("on")
                val ok = bridge.system.setFlashlight(on)
                if (ok) {
                    ToolResult(ok = true, message = if (on) "Flashlight turned on." else "Flashlight turned off.")
                } else {
                    ToolResult(ok = false, message = "Flashlight not available on this device.")
                }
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "toggle_flashlight failed")
            }
    }
