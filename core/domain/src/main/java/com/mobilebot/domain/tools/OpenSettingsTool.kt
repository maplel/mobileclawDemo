package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class OpenSettingsTool
    @Inject
    constructor(
        private val bridge: DeviceCapabilityBridge,
    ) : Tool {
        override val name: String = "open_settings"

        override val definition =
            ToolDefinition(
                name = name,
                description = "Open a system settings page (wifi, bluetooth, display, sound, battery, etc.). 打开系统设置页面。",
                parametersSchema =
                    """{"type":"object","properties":{"page":{"type":"string","description":"Settings page key: wifi, bluetooth, display, sound, battery, storage, apps, location, security, date, accessibility, about, airplane, notification_access. Omit for general settings."}},"required":[]}""",
            )

        override suspend fun execute(argumentsJson: String): ToolResult =
            try {
                val page = runCatching { JSONObject(argumentsJson).optString("page", null) }.getOrNull()
                val ok = bridge.system.openSettings(page)
                if (ok) {
                    val label = page?.ifBlank { null } ?: "general"
                    ToolResult(ok = true, message = "Opened $label settings.")
                } else {
                    ToolResult(ok = false, message = "Could not open settings page.")
                }
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "open_settings failed")
            }
    }
