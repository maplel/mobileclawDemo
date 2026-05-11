package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import javax.inject.Inject

class GetDeviceStateTool @Inject constructor(
    private val bridge: DeviceCapabilityBridge,
) : Tool {

    override val name = "get_device_state"

    override val definition = ToolDefinition(
        name = name,
        description = "Get current device state: battery level, network type, storage, screen brightness.",
        parametersSchema = """
        {
          "type": "object",
          "properties": {}
        }
        """.trimIndent(),
    )

    override val risk = ToolRisk.LOW

    override suspend fun execute(argumentsJson: String): ToolResult {
        return try {
            val state = bridge.system.getDeviceState()
            ToolResult(ok = true, message = "Device state retrieved.", dataJson = state)
        } catch (e: Exception) {
            ToolResult(ok = false, message = "Failed to get device state: ${e.message}")
        }
    }
}
