package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class QueryCalendarTool @Inject constructor(
    private val bridge: DeviceCapabilityBridge,
) : Tool {

    override val name = "query_calendar"

    override val definition = ToolDefinition(
        name = name,
        description = "Query calendar events for a date range.",
        parametersSchema = """
        {
          "type": "object",
          "properties": {
            "startDate": {"type": "string", "description": "Start date in YYYY-MM-DD format"},
            "endDate": {"type": "string", "description": "End date in YYYY-MM-DD format (optional, defaults to same day)"}
          },
          "required": ["startDate"]
        }
        """.trimIndent(),
    )

    override val risk = ToolRisk.LOW

    override val requiredCapabilities: Set<String>
        get() = setOf("calendar.read")

    override suspend fun execute(argumentsJson: String): ToolResult {
        return try {
            val obj = JSONObject(argumentsJson)
            val startDate = obj.getString("startDate")
            val endDate = obj.optString("endDate", startDate)

            val events = bridge.system.queryCalendarEvents(startDate, endDate)
            if (events.isNullOrBlank()) {
                ToolResult(ok = true, message = "No events found for $startDate to $endDate.")
            } else {
                ToolResult(ok = true, message = "Events found:", dataJson = events)
            }
        } catch (e: Exception) {
            ToolResult(ok = false, message = "Failed to query calendar: ${e.message}")
        }
    }
}
