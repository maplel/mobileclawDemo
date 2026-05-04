package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class DialNumberTool
    @Inject
    constructor(
        private val bridge: DeviceCapabilityBridge,
    ) : Tool {
        override val name: String = "dial_number"

        override val requiredCapabilities: Set<String> = setOf("telephony.dial")

        override val executionPolicy = ToolExecutionPolicy(hasSideEffects = true)

        override val definition =
            ToolDefinition(
                name = name,
                description = "Open the phone dialer with a number prefilled (user must place the call).",
                parametersSchema =
                    """{"type":"object","properties":{"phoneNumber":{"type":"string"}},"required":["phoneNumber"]}""",
            )

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val num = JSONObject(argumentsJson).getString("phoneNumber").trim()
                if (num.isEmpty()) {
                    return ToolResult(ok = false, message = "Missing phoneNumber")
                }
                val ok = bridge.telephony.dialNumber(num)
                if (ok) {
                    ToolResult(ok = true, message = "Dialer opened.")
                } else {
                    ToolResult(ok = false, message = "Could not open dialer.")
                }
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "dial failed")
            }
        }
    }
