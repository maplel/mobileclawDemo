package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class ListNotificationsTool
    @Inject
    constructor(
        private val bridge: DeviceCapabilityBridge,
    ) : Tool {
        override val name: String = "list_notifications"

        override val risk: ToolRisk = ToolRisk.LOW

        override val requiredCapabilities: Set<String> = setOf("notifications.read")

        override val definition =
            ToolDefinition(
                name = name,
                description =
                    "List recent notifications mirrored by MobileBot (empty until notification listener is enabled).",
                parametersSchema =
                    """
                    {"type":"object","properties":{"limit":{"type":"integer","description":"Max items"}}}
                    """.trimIndent(),
            )

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val limit = JSONObject(argumentsJson).optInt("limit", 10).coerceIn(1, 50)
                val items = bridge.notifications.listRecent(limit)
                if (items.isEmpty()) {
                    ToolResult(
                        ok = true,
                        message =
                            "No cached notifications yet. If notification access is enabled, new posts will appear here. " +
                                "Instrumented tests can call NotificationHistoryStore.addSyntheticForTesting().",
                    )
                } else {
                    val lines =
                        items.joinToString("\n") { n ->
                            "- [${n.packageName}] ${n.title}: ${n.text.take(200)} (id=${n.id})"
                        }
                    ToolResult(ok = true, message = lines)
                }
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "list failed")
            }
        }
    }
