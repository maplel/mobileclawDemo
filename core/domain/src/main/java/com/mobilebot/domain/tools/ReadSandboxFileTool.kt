package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class ReadSandboxFileTool
    @Inject
    constructor(
        private val bridge: DeviceCapabilityBridge,
    ) : Tool {
        override val name: String = "read_sandbox_file"

        override val risk: ToolRisk = ToolRisk.LOW

        override val requiredCapabilities: Set<String> = setOf("files.workspace")

        override val definition =
            ToolDefinition(
                name = name,
                description = "Read a text file from the app workspace sandbox (under files/workspace). 读取沙盒工作区中的文本文件。",
                parametersSchema =
                    """
                    {"type":"object","properties":{"path":{"type":"string","description":"Relative path under workspace (e.g. shared/incoming.txt)"}},
                    "required":["path"]}
                    """.trimIndent(),
            )

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val path = JSONObject(argumentsJson).getString("path").trim().replace("..", "")
                val read = bridge.files.readWorkspaceText(path, maxChars = 32_000)
                val error = read.error
                if (error != null) {
                    ToolResult(ok = false, message = error)
                } else {
                    val suffix = if (read.truncated) "\n...(truncated)" else ""
                    ToolResult(ok = true, message = read.text + suffix)
                }
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "read failed")
            }
        }
    }
