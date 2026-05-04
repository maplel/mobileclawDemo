package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class SetAlarmTool
    @Inject
    constructor(
        private val bridge: DeviceCapabilityBridge,
    ) : Tool {
        override val name: String = "set_alarm"

        override val executionPolicy: ToolExecutionPolicy =
            ToolExecutionPolicy(hasSideEffects = true)

        override val definition =
            ToolDefinition(
                name = name,
                description = "Set an alarm at a specific time. 设置闹钟。",
                parametersSchema =
                    """{"type":"object","properties":{"hour":{"type":"integer","description":"Hour 0-23"},"minute":{"type":"integer","description":"Minute 0-59"},"label":{"type":"string","description":"Optional alarm label"}},"required":["hour","minute"]}""",
            )

        override suspend fun execute(argumentsJson: String): ToolResult =
            try {
                val obj = JSONObject(argumentsJson)
                val hour = obj.getInt("hour")
                val minute = obj.getInt("minute")
                val label = obj.optString("label", null)?.takeIf { it.isNotBlank() }
                val ok = bridge.system.setAlarm(hour, minute, label)
                if (ok) {
                    val time = "%02d:%02d".format(hour, minute)
                    ToolResult(ok = true, message = "Alarm set for $time." + (label?.let { " Label: $it" } ?: ""))
                } else {
                    ToolResult(ok = false, message = "Could not set alarm (no alarm app found).")
                }
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "set_alarm failed")
            }
    }
