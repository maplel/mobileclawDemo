package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class GetCurrentLocationTool
    @Inject
    constructor(
        private val bridge: DeviceCapabilityBridge,
    ) : Tool {
        override val name: String = "get_current_location"

        override val requiredCapabilities: Set<String> = setOf("location.coarse")

        override val definition =
            ToolDefinition(
                name = name,
                description = "Get the device's last known coarse location if permitted.",
                parametersSchema = """{"type":"object","properties":{}}""",
            )

        @Suppress("UNUSED_PARAMETER")
        override suspend fun execute(argumentsJson: String): ToolResult {
            val loc = bridge.location.getCoarseLocation()
            val error = loc.error
            if (error != null) {
                return ToolResult(ok = false, message = error)
            }

            val lat = loc.latitude
            val lon = loc.longitude
            return if (lat != null && lon != null) {
                val json =
                    JSONObject()
                        .put("latitude", lat)
                        .put("longitude", lon)
                        .toString()
                ToolResult(ok = true, message = "Location retrieved.", dataJson = json)
            } else {
                ToolResult(ok = false, message = "Location unavailable.")
            }
        }
    }
