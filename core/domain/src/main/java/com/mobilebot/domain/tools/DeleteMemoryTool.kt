package com.mobilebot.domain.tools

import com.mobilebot.domain.memory.PersistentMemoryManager
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

/**
 * Tool for the LLM to delete a previously stored memory.
 */
class DeleteMemoryTool
    @Inject
    constructor(
        private val persistentMemory: PersistentMemoryManager,
    ) : Tool {
        override val name: String = "delete_memory"

        override val definition =
            ToolDefinition(
                name = name,
                description = "Delete a previously saved memory by its file path. " +
                    "Use `recall_memories` first to find the file path of the memory to delete.",
                parametersSchema =
                    """{"type":"object","properties":{"filePath":{"type":"string","description":"Full file path of the memory to delete, as returned by recall_memories"}},"required":["filePath"]}""",
            )

        override val executionPolicy: ToolExecutionPolicy =
            ToolExecutionPolicy(requiresUserApproval = true)

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val args = JSONObject(argumentsJson)
                val filePath = args.getString("filePath").trim()

                if (filePath.isEmpty()) {
                    return ToolResult(ok = false, message = "filePath is required")
                }

                persistentMemory.deleteMemory(filePath)

                ToolResult(ok = true, message = "Memory deleted: $filePath")
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "delete_memory failed")
            }
        }
    }
