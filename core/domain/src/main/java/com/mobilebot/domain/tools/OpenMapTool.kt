package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class OpenMapTool
    @Inject
    constructor(
        private val bridge: DeviceCapabilityBridge,
    ) : Tool {
        override val name: String = "open_map"

        override val requiredCapabilities: Set<String> = setOf("maps.external")

        override val definition =
            ToolDefinition(
                name = name,
                description = "Open maps for a place query (search or navigate mode).",
                parametersSchema =
                    """
                    {"type":"object","properties":{"query":{"type":"string"},"mode":{"type":"string","enum":["search","navigate"]}},
                    "required":["query","mode"]}
                    """.trimIndent(),
            )

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val obj = JSONObject(argumentsJson)
                val query = obj.getString("query").trim()
                val mode = obj.optString("mode", "search").trim().ifEmpty { "search" }
                if (query.isEmpty()) {
                    return ToolResult(ok = false, message = "Missing query")
                }
                val normalizedMode = if (mode == "navigate") "navigate" else "search"
                val ok = bridge.maps.openMap(query, normalizedMode)
                if (ok) {
                    ToolResult(ok = true, message = "Opened maps.", dataJson = obj.toString())
                } else {
                    ToolResult(ok = false, message = "Could not open maps app.")
                }
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "open_map failed")
            }
        }
    }
