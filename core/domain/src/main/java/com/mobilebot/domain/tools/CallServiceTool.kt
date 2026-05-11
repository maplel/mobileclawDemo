package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.bridge.ServiceRequest
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class CallServiceTool
    @Inject
    constructor(
        private val bridge: DeviceCapabilityBridge,
    ) : Tool {
        override val name: String = "call_service"

        override val definition =
            ToolDefinition(
                name = name,
                description = "Call any authorized external service API. " +
                    "Use serviceId and action from the skill instructions. " +
                    "Available services can be discovered from the skill's requiredServices field.",
                parametersSchema =
                    """{"type":"object","properties":{"serviceId":{"type":"string","description":"ID of the service to call"},"action":{"type":"string","description":"Action name as defined in the service config"},"params":{"type":"object","description":"Parameters for the action"}},"required":["serviceId","action"]}""",
            )

        override val executionPolicy = ToolExecutionPolicy(requiresConnectivity = true)

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val args = JSONObject(argumentsJson)
                val serviceId = args.getString("serviceId").trim()
                val action = args.getString("action").trim()
                if (serviceId.isEmpty() || action.isEmpty()) {
                    return ToolResult(ok = false, message = "serviceId and action are required")
                }

                val gateway = bridge.services
                if (!gateway.isServiceAuthorized(serviceId)) {
                    return ToolResult(ok = false, message = "Service '$serviceId' is not authorized")
                }

                val params = mutableMapOf<String, Any>()
                val paramsObj = args.optJSONObject("params")
                if (paramsObj != null) {
                    for (key in paramsObj.keys()) {
                        params[key] = paramsObj.get(key)
                    }
                }

                val response = gateway.call(ServiceRequest(serviceId, action, params))
                if (response.ok) {
                    ToolResult(
                        ok = true,
                        message = response.message,
                        dataJson = JSONObject(response.data).toString(),
                    )
                } else {
                    ToolResult(ok = false, message = response.message)
                }
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "call_service failed")
            }
        }
    }
