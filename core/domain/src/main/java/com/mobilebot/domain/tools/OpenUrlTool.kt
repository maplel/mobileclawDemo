package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.domain.agent.CurrentSessionKeyProvider
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class OpenUrlTool
    @Inject
    constructor(
        private val bridge: DeviceCapabilityBridge,
        private val sessionKeyProvider: CurrentSessionKeyProvider,
    ) : Tool {
        override val name: String = "open_url"

        override val requiredCapabilities: Set<String> = setOf("browser.view")

        override val executionPolicy: ToolExecutionPolicy =
            ToolExecutionPolicy(requiresConnectivity = true)

        override val definition =
            ToolDefinition(
                name = name,
                description = "Open a webpage in the default browser.",
                parametersSchema = """{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}""",
            )

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val url = JSONObject(argumentsJson).getString("url").trim()
                if (url.isEmpty()) {
                    return ToolResult(ok = false, message = "Missing url")
                }
                if (sessionKeyProvider.isSubtask()) {
                    val dj = JSONObject().put("url", url).toString()
                    return ToolResult(
                        ok = true,
                        message = "URL noted (background mode, not opening browser): $url",
                        dataJson = dj,
                    )
                }
                val ok = bridge.browser.openUrl(url)
                if (ok) {
                    val dj = JSONObject().put("url", url).toString()
                    ToolResult(ok = true, message = "Opened URL in browser.", dataJson = dj)
                } else {
                    ToolResult(ok = false, message = "Could not open URL (no handler).")
                }
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "open_url failed")
            }
        }
    }
