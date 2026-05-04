package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class CreateNotificationTool @Inject constructor(
    private val bridge: DeviceCapabilityBridge,
) : Tool {

    override val name = "create_notification"

    override val definition = ToolDefinition(
        name = name,
        description = "Create a persistent notification or reminder on the device.",
        parametersSchema = """
        {
          "type": "object",
          "properties": {
            "title": {"type": "string", "description": "Notification title"},
            "message": {"type": "string", "description": "Notification body text"},
            "priority": {"type": "string", "description": "Priority: low, default, high (optional)"}
          },
          "required": ["title", "message"]
        }
        """.trimIndent(),
    )

    override val risk = ToolRisk.LOW

    override val executionPolicy = ToolExecutionPolicy(hasSideEffects = true)

    override suspend fun execute(argumentsJson: String): ToolResult {
        return try {
            val obj = JSONObject(argumentsJson)
            val title = obj.getString("title")
            val message = obj.getString("message")
            val priority = obj.optString("priority", "default")

            bridge.system.showNotification(title, message, priority)
            ToolResult(ok = true, message = "Notification created: $title")
        } catch (e: Exception) {
            ToolResult(ok = false, message = "Failed to create notification: ${e.message}")
        }
    }
}
