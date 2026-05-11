package com.mobilebot.domain.tools

import com.mobilebot.domain.agent.CurrentSessionKeyProvider
import com.mobilebot.domain.subtask.SubtaskExecutor
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class SpawnSubtaskTool
    @Inject
    constructor(
        private val subtaskExecutor: SubtaskExecutor,
        private val sessionKeyProvider: CurrentSessionKeyProvider,
    ) : Tool {
        override val name: String = "spawn_subtask"

        override val definition =
            ToolDefinition(
                name = name,
                description = "Start an independent subtask that runs in parallel. " +
                    "Use when a skill instructs you to handle multiple concerns simultaneously. " +
                    "The instruction should be detailed enough for the subtask to work autonomously.",
                parametersSchema =
                    """{"type":"object","properties":{"taskId":{"type":"string","description":"Unique ID for this subtask"},"instruction":{"type":"string","description":"Detailed instruction for the subtask agent"}},"required":["taskId","instruction"]}""",
            )

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val args = JSONObject(argumentsJson)
                val taskId = args.getString("taskId").trim()
                val instruction = args.getString("instruction").trim()
                if (taskId.isEmpty() || instruction.isEmpty()) {
                    return ToolResult(ok = false, message = "taskId and instruction are required")
                }

                if (subtaskExecutor.getState(taskId) != null) {
                    return ToolResult(ok = false, message = "Subtask '$taskId' already exists")
                }

                subtaskExecutor.spawn(
                    taskId = taskId,
                    instruction = instruction,
                    parentSessionKey = sessionKeyProvider.getSessionKey(),
                    parentChatId = sessionKeyProvider.getChatId(),
                )

                ToolResult(
                    ok = true,
                    message = "Subtask '$taskId' spawned and running in parallel.",
                )
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "spawn_subtask failed")
            }
        }
    }
