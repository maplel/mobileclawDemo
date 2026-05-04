package com.mobilebot.domain.tools

import com.mobilebot.domain.subtask.SubtaskExecutor
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class PublishFactTool
    @Inject
    constructor(
        private val subtaskExecutor: SubtaskExecutor,
    ) : Tool {
        override val name: String = "publish_fact"

        override val definition =
            ToolDefinition(
                name = name,
                description = "Publish a key-value fact to the shared fact store so that " +
                    "other subtasks and the parent orchestrator can read it via check_subtask. " +
                    "Use this to pass important results (e.g. police case number, claim ID, " +
                    "hotel booking confirmation) between subtasks.",
                parametersSchema =
                    """{"type":"object","properties":{"key":{"type":"string","description":"Fact key, e.g. 'police_case_number', 'insurance_claim_id'"},"value":{"type":"string","description":"Fact value to publish"}},"required":["key","value"]}""",
            )

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val args = JSONObject(argumentsJson)
                val key = args.getString("key").trim()
                val value = args.getString("value").trim()
                if (key.isEmpty() || value.isEmpty()) {
                    return ToolResult(ok = false, message = "key and value are required")
                }
                subtaskExecutor.publishFact(key, value)
                ToolResult(
                    ok = true,
                    message = "Fact published: '$key' = '$value'",
                )
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "publish_fact failed")
            }
        }
    }
