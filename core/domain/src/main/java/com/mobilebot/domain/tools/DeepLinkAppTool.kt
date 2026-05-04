package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class DeepLinkAppTool @Inject constructor(
    private val bridge: DeviceCapabilityBridge,
) : Tool {

    override val name = "deep_link_app"

    override val definition = ToolDefinition(
        name = name,
        description = "Open a specific page in an installed app using a deep link URI.",
        parametersSchema = """
        {
          "type": "object",
          "properties": {
            "uri": {"type": "string", "description": "Deep link URI (e.g. 'didi://ride?from=...&to=...')"},
            "fallbackUrl": {"type": "string", "description": "Web URL to open if the app is not installed (optional)"}
          },
          "required": ["uri"]
        }
        """.trimIndent(),
    )

    override val risk = ToolRisk.MEDIUM

    override val executionPolicy = ToolExecutionPolicy(hasSideEffects = true)

    override suspend fun execute(argumentsJson: String): ToolResult {
        return try {
            val obj = JSONObject(argumentsJson)
            val uri = obj.getString("uri")
            val fallbackUrl = obj.optString("fallbackUrl", "")

            val opened = bridge.system.openDeepLink(uri, fallbackUrl)
            if (opened) {
                ToolResult(ok = true, message = "Opened app with deep link: $uri")
            } else {
                ToolResult(ok = false, message = "Could not open deep link. The app may not be installed.")
            }
        } catch (e: Exception) {
            ToolResult(ok = false, message = "Failed to open deep link: ${e.message}")
        }
    }
}
