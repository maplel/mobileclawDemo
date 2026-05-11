package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.domain.agent.CurrentSessionKeyProvider
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import javax.inject.Inject

class OpenCameraTool
    @Inject
    constructor(
        private val bridge: DeviceCapabilityBridge,
        private val sessionKeyProvider: CurrentSessionKeyProvider,
    ) : Tool {
        override val name: String = "open_camera"

        override val risk: ToolRisk = ToolRisk.MEDIUM

        override val requiredCapabilities: Set<String> = setOf("media.camera")

        override val executionPolicy: ToolExecutionPolicy =
            ToolExecutionPolicy(
                requiresForeground = true,
            )

        override val definition =
            ToolDefinition(
                name = name,
                description =
                    "Open the device camera app in still-photo mode. " +
                        "Call this when the user asks to open the camera, take a photo, or snap a picture.",
                parametersSchema =
                    """
                    {"type":"object","properties":{}}
                    """.trimIndent(),
            )

        @Suppress("UNUSED_PARAMETER")
        override suspend fun execute(argumentsJson: String): ToolResult {
            if (sessionKeyProvider.isSubtask()) {
                return ToolResult(
                    ok = true,
                    message = "Camera noted (background mode, not launching camera app).",
                )
            }
            val msg = bridge.media.launchStillCamera()
            return if (msg.startsWith("Could not")) {
                ToolResult(ok = false, message = msg)
            } else {
                ToolResult(ok = true, message = msg)
            }
        }
    }
