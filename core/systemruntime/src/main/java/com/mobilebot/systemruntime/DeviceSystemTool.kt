package com.mobilebot.systemruntime

import com.mobilebot.domain.tools.Tool
import com.mobilebot.domain.tools.ToolExecutionPolicy
import com.mobilebot.domain.tools.ToolRisk
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class DeviceSystemTool
    @Inject
    constructor(
        private val runtime: SystemRuntime,
    ) : Tool {
        override val name: String = "device_system"

        override val definition: ToolDefinition = ToolDefinition(
            name = name,
            description = "Use phone and OS capabilities exposed by the device system runtime. Prefer dedicated tools such as system_search_contacts and system_send_sms when available.",
            parametersSchema = """
            {
              "type": "object",
              "properties": {
                "action": {
                  "type": "string",
                  "description": "One of: send_sms, receive_sms, sms, dial_phone, call_log, notification, reminder, long_reminder, location, contacts, social_graph, device_state, memory_read, service_call, mcp_call, payment, accounting."
                },
                "params": {
                  "type": "object",
                  "description": "Action-specific parameters. For reminder or long_reminder, include title, body/message, and scheduledFor or scheduledAt when the trigger time is known."
                }
              },
              "required": ["action"]
            }
            """.trimIndent(),
        )

        override val risk: ToolRisk = ToolRisk.LOW

        override val executionPolicy: ToolExecutionPolicy = ToolExecutionPolicy(
            requiresConnectivity = false,
            requiresUserApproval = false,
            hasSideEffects = true,
        )

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val obj = JSONObject(argumentsJson)
                val action = obj.optString("action").trim()
                if (action.isBlank()) {
                    return ToolResult(false, "Missing required parameter: action")
                }
                val params = obj.optJSONObject("params") ?: JSONObject()
                for (key in obj.keys()) {
                    if (key == "action" || key == "params" || params.has(key)) continue
                    params.put(key, obj.opt(key))
                }
                val result = runtime.execute(action, params)
                ToolResult(
                    ok = result.ok,
                    message = result.message,
                    dataJson = JSONObject(result.data).toString(),
                )
            } catch (e: Exception) {
                ToolResult(false, e.message ?: "device_system failed")
            }
        }
    }
