package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class ShareTextTool
    @Inject
    constructor(
        private val bridge: DeviceCapabilityBridge,
    ) : Tool {
        override val name: String = "share_text"

        override val requiredCapabilities: Set<String> = setOf("share.generic")

        override val definition =
            ToolDefinition(
                name = name,
                description = "Open the Android share sheet for plain text.",
                parametersSchema = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}""",
            )

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val text = JSONObject(argumentsJson).getString("text")
                if (text.isEmpty()) {
                    return ToolResult(ok = false, message = "Missing text")
                }
                val ok = bridge.share.shareText(text)
                if (ok) {
                    ToolResult(ok = true, message = "Share sheet opened.")
                } else {
                    ToolResult(ok = false, message = "Could not open share sheet.")
                }
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "share failed")
            }
        }
    }
