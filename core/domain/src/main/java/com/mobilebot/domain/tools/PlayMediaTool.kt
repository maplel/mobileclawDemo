package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class PlayMediaTool @Inject constructor(
    private val bridge: DeviceCapabilityBridge,
) : Tool {

    override val name = "play_media"

    override val definition = ToolDefinition(
        name = name,
        description = "Play music or media on the device. Searches and plays from available music apps.",
        parametersSchema = """
        {
          "type": "object",
          "properties": {
            "query": {"type": "string", "description": "Song name, artist, or search query"},
            "action": {"type": "string", "description": "Action: play, pause, next, previous (default: play)"}
          },
          "required": ["query"]
        }
        """.trimIndent(),
    )

    override val risk = ToolRisk.LOW

    override val executionPolicy = ToolExecutionPolicy(hasSideEffects = true)

    override suspend fun execute(argumentsJson: String): ToolResult {
        return try {
            val obj = JSONObject(argumentsJson)
            val query = obj.getString("query")
            val action = obj.optString("action", "play")

            bridge.system.playMedia(query, action)
            ToolResult(ok = true, message = "Playing: $query")
        } catch (e: Exception) {
            ToolResult(ok = false, message = "Failed to play media: ${e.message}")
        }
    }
}
