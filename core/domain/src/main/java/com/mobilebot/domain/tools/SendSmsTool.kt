package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class SendSmsTool
    @Inject
    constructor(
        private val bridge: DeviceCapabilityBridge,
    ) : Tool {
        override val name: String = TOOL_NAME

        override val requiredCapabilities: Set<String> = setOf("messaging.sms", "messaging.sms.send")

        override val executionPolicy = ToolExecutionPolicy(requiresUserApproval = true, hasSideEffects = true)

        override val definition =
            ToolDefinition(
                name = name,
                description =
                    "Send an SMS to phoneNumber with message. Sends immediately when the app has Send SMS permission; " +
                        "otherwise opens the SMS app with the draft (user taps send).",
                parametersSchema =
                    """
                    {"type":"object","properties":{"phoneNumber":{"type":"string"},"message":{"type":"string"}},
                    "required":["phoneNumber","message"]}
                    """.trimIndent(),
            )

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val obj = JSONObject(argumentsJson)
                val phone =
                    listOf("phoneNumber", "phone", "to", "number", "msisdn")
                        .firstNotNullOfOrNull { key ->
                            if (!obj.has(key) || obj.isNull(key)) null
                            else obj.optString(key, "").trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
                        }
                        ?: ""
                val message =
                    listOf("message", "body", "text", "sms_body")
                        .firstNotNullOfOrNull { key ->
                            if (!obj.has(key) || obj.isNull(key)) null
                            else obj.optString(key, "").takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
                        }
                        ?: ""
                if (phone.isEmpty()) {
                    return ToolResult(ok = false, message = "Missing phoneNumber")
                }
                if (message.isEmpty()) {
                    return ToolResult(ok = false, message = "Missing message body")
                }
                val result = bridge.telephony.sendSms(phone, message)
                if (!result.success) {
                    return ToolResult(ok = false, message = "Could not send SMS or open the composer.")
                }
                val msg =
                    if (result.sentDirectly) {
                        "SMS send requested. Android accepted the request, but delivery is not confirmed yet."
                    } else {
                        "SMS composer opened. Review the draft and tap send to finish."
                    }
                ToolResult(ok = true, message = msg)
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "send_sms failed")
            }
        }

        companion object {
            const val TOOL_NAME = "send_sms"
        }
    }
