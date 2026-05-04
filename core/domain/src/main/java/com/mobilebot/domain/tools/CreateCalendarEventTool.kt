package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class CreateCalendarEventTool @Inject constructor(
    private val bridge: DeviceCapabilityBridge,
) : Tool {

    override val name = "create_calendar_event"

    override val definition = ToolDefinition(
        name = name,
        description = "Create a calendar event with title, start/end time, and optional location.",
        parametersSchema = """
        {
          "type": "object",
          "properties": {
            "title": {"type": "string", "description": "Event title"},
            "startTime": {"type": "string", "description": "Start time in ISO 8601 format (e.g. 2025-03-15T09:00:00)"},
            "endTime": {"type": "string", "description": "End time in ISO 8601 format"},
            "location": {"type": "string", "description": "Event location (optional)"},
            "description": {"type": "string", "description": "Event description (optional)"}
          },
          "required": ["title", "startTime"]
        }
        """.trimIndent(),
    )

    override val risk = ToolRisk.MEDIUM

    override val executionPolicy = ToolExecutionPolicy(
        hasSideEffects = true,
        requiresUserApproval = false,
    )

    override suspend fun execute(argumentsJson: String): ToolResult {
        return try {
            val obj = JSONObject(argumentsJson)
            val title = obj.getString("title")
            val startTime = obj.getString("startTime")
            val endTime = obj.optString("endTime", "")
            val location = obj.optString("location", "")
            val description = obj.optString("description", "")

            bridge.system.createCalendarEvent(title, startTime, endTime, location, description)
            ToolResult(ok = true, message = "Calendar event '$title' created for $startTime.")
        } catch (e: Exception) {
            ToolResult(ok = false, message = "Failed to create calendar event: ${e.message}")
        }
    }
}
