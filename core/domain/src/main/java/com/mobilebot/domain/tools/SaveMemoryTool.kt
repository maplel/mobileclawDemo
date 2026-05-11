package com.mobilebot.domain.tools

import com.mobilebot.domain.memory.MemoryType
import com.mobilebot.domain.memory.PersistentMemoryManager
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

/**
 * Tool for the LLM to persist a typed memory.
 *
 * Memories are stored as .md files with YAML frontmatter and persisted
 * across sessions. The system recalls relevant memories each turn
 * and injects them into the system prompt.
 */
class SaveMemoryTool
    @Inject
    constructor(
        private val persistentMemory: PersistentMemoryManager,
    ) : Tool {
        override val name: String = "save_memory"

        override val definition =
            ToolDefinition(
                name = name,
                description = "Save a persistent typed memory. " +
                    "Use this when you learn something important about the user that should be remembered " +
                    "across conversations, such as preferences, personal information, project context, or " +
                    "references to external systems. Memories are automatically recalled in future turns.",
                parametersSchema =
                    """{"type":"object","properties":{"type":{"type":"string","description":"Memory type: 'user' (preferences/profile), 'feedback' (behavior guidance), 'project' (goals/context), 'reference' (external pointers)"},"name":{"type":"string","description":"Short unique name for this memory, e.g. 'user_language_preference'"},"description":{"type":"string","description":"One-line summary of what this memory contains"},"content":{"type":"string","description":"The memory content to persist"}},"required":["type","name","description","content"]}""",
            )

        override val executionPolicy: ToolExecutionPolicy =
            ToolExecutionPolicy(requiresUserApproval = true)

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val args = JSONObject(argumentsJson)
                val typeStr = args.getString("type").trim()
                val name = args.getString("name").trim()
                val description = args.getString("description").trim()
                val content = args.getString("content").trim()

                val type = MemoryType.fromLabel(typeStr)
                    ?: return ToolResult(ok = false, message = "Invalid memory type: '$typeStr'. Valid types: user, feedback, project, reference")

                if (name.isEmpty()) {
                    return ToolResult(ok = false, message = "name is required")
                }

                persistentMemory.saveMemory(type = type, name = name, description = description, content = content)

                ToolResult(ok = true, message = "Memory '$name' saved as type '$typeStr'.")
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "save_memory failed")
            }
        }
    }
