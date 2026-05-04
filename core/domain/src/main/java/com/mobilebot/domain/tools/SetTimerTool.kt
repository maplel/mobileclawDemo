package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class SetTimerTool
    @Inject
    constructor(
        private val bridge: DeviceCapabilityBridge,
    ) : Tool {
        override val name: String = "set_timer"

        override val executionPolicy: ToolExecutionPolicy =
            ToolExecutionPolicy(hasSideEffects = true)

        override val definition =
            ToolDefinition(
                name = name,
                description = "Start a countdown timer. 设置倒计时。",
                parametersSchema =
                    """{"type":"object","properties":{"seconds":{"type":"integer","description":"Duration in seconds"},"label":{"type":"string","description":"Optional timer label"}},"required":["seconds"]}""",
            )

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val obj = JSONObject(argumentsJson)
                val seconds = obj.getInt("seconds")
                if (seconds <= 0) {
                    return ToolResult(ok = false, message = "Timer duration must be positive.")
                }
                val label = obj.optString("label", null)?.takeIf { it.isNotBlank() }
                val ok = bridge.system.setTimer(seconds, label)
                if (ok) {
                    val desc =
                        when {
                            seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
                            seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
                            else -> "${seconds}s"
                        }
                    ToolResult(ok = true, message = "Timer started: $desc." + (label?.let { " Label: $it" } ?: ""))
                } else {
                    ToolResult(ok = false, message = "Could not start timer (no clock app found).")
                }
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "set_timer failed")
            }
        }
    }
