package com.mobilebot.systemruntime

import com.mobilebot.domain.tools.Tool
import com.mobilebot.domain.tools.ToolExecutionPolicy
import com.mobilebot.domain.tools.ToolRisk
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class SystemSendSmsTool
    @Inject
    constructor(
        private val runtime: SystemRuntime,
    ) : Tool {
        override val name: String = NAME

        override val definition: ToolDefinition = ToolDefinition(
            name = name,
            description = "Send an SMS through the system runtime and start listening for the expected reply.",
            parametersSchema = """
            {
              "type": "object",
              "properties": {
                "to": {"type": "string", "description": "Recipient name or phone number."},
                "message": {"type": "string", "description": "SMS body to send."}
              },
              "required": ["to", "message"]
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
                val args = JSONObject(argumentsJson)
                val to = firstText(args, "to", "contact", "recipient", "name", "party", "phoneNumber", "phone", "number")
                val message = args.optString("message")
                    .ifBlank { args.optString("text") }
                    .ifBlank { args.optString("body") }
                if (to.isBlank()) return ToolResult(false, "Missing recipient")
                if (message.isBlank()) return ToolResult(false, "Missing message body")
                val result = runtime.sendSmsFromTool(
                    JSONObject()
                        .put("to", to)
                        .put("message", message)
                        .also { payload ->
                            for (key in args.keys()) {
                                if (key == "to" || key == "message" || key == "text" || key == "body" || payload.has(key)) continue
                                payload.put(key, args.opt(key))
                            }
                        },
                )
                ToolResult(result.ok, result.message, JSONObject(result.data).toString())
            } catch (e: Exception) {
                ToolResult(false, e.message ?: "$NAME failed")
            }
        }

        companion object {
            const val NAME = "system_send_sms"
        }
    }

class SystemWaitForSmsTool
    @Inject
    constructor(
        private val runtime: SystemRuntime,
    ) : Tool {
        override val name: String = NAME

        override val definition: ToolDefinition = ToolDefinition(
            name = name,
            description = "Listen for the next matching SMS reply. Use after system_send_sms when the workflow needs the reply before continuing.",
            parametersSchema = """
            {
              "type": "object",
              "properties": {
                "watchId": {"type": "string", "description": "Listener id returned by system_send_sms, if available."},
                "from": {"type": "string", "description": "Expected sender name or phone number."},
                "context": {"type": "string", "description": "Short reason for waiting, used to match the pending conversation."}
              }
            }
            """.trimIndent(),
        )

        override val risk: ToolRisk = ToolRisk.LOW

        override val executionPolicy: ToolExecutionPolicy = ToolExecutionPolicy(
            requiresConnectivity = false,
            requiresUserApproval = false,
            hasSideEffects = false,
        )

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val result = runtime.waitForSms(JSONObject(argumentsJson))
                ToolResult(result.ok, result.message, JSONObject(result.data).toString())
            } catch (e: Exception) {
                ToolResult(false, e.message ?: "$NAME failed")
            }
        }

        companion object {
            const val NAME = "system_wait_for_sms"
        }
    }

private fun firstText(
    obj: JSONObject,
    vararg keys: String,
): String =
    keys.firstNotNullOfOrNull { key ->
        if (!obj.has(key) || obj.isNull(key)) null
        else obj.optString(key).trim().takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }.orEmpty()
