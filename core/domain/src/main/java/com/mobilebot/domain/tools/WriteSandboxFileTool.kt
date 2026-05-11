package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class WriteSandboxFileTool @Inject constructor(
    private val bridge: DeviceCapabilityBridge,
) : Tool {

    override val name = "write_sandbox_file"

    override val definition = ToolDefinition(
        name = name,
        description = "Write or append text to a file in the sandbox workspace.",
        parametersSchema = """
        {
          "type": "object",
          "properties": {
            "path": {"type": "string", "description": "File path relative to sandbox root"},
            "content": {"type": "string", "description": "Text content to write"},
            "append": {"type": "boolean", "description": "If true, append to file instead of overwriting (default: false)"}
          },
          "required": ["path", "content"]
        }
        """.trimIndent(),
    )

    override val risk = ToolRisk.LOW

    override val requiredCapabilities: Set<String>
        get() = setOf("files.workspace")

    override val executionPolicy = ToolExecutionPolicy(hasSideEffects = true)

    override suspend fun execute(argumentsJson: String): ToolResult {
        return try {
            val obj = JSONObject(argumentsJson)
            val path = obj.getString("path")
            val content = obj.getString("content")
            val append = obj.optBoolean("append", false)

            bridge.system.writeSandboxFile(path, content, append)
            val action = if (append) "appended to" else "written to"
            ToolResult(ok = true, message = "Content $action $path")
        } catch (e: Exception) {
            ToolResult(ok = false, message = "Failed to write file: ${e.message}")
        }
    }
}
