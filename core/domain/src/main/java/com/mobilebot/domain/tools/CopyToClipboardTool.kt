package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class CopyToClipboardTool
    @Inject
    constructor(
        private val bridge: DeviceCapabilityBridge,
    ) : Tool {
        override val name: String = "copy_to_clipboard"

        override val requiredCapabilities: Set<String> = setOf("clipboard.write")

        override val definition =
            ToolDefinition(
                name = name,
                description = "Copy text to the system clipboard.",
                parametersSchema = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}""",
            )

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val text = JSONObject(argumentsJson).getString("text")
                if (text.isEmpty()) {
                    return ToolResult(ok = false, message = "Missing text")
                }
                val ok = bridge.clipboard.copyToClipboard(text)
                if (ok) {
                    ToolResult(ok = true, message = "Copied to clipboard.")
                } else {
                    ToolResult(ok = false, message = "Clipboard unavailable.")
                }
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "copy failed")
            }
        }
    }
