package com.mobilebot.domain.tools

import com.mobilebot.domain.subtask.SubtaskExecutor
import com.mobilebot.domain.subtask.SubtaskStatus
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class CheckSubtaskTool
    @Inject
    constructor(
        private val subtaskExecutor: SubtaskExecutor,
    ) : Tool {
        override val name: String = "check_subtask"

        override val definition =
            ToolDefinition(
                name = name,
                description = "Check the status and result of a previously spawned subtask, " +
                    "or list all subtask states. When a subtask is COMPLETED, the result contains " +
                    "the subtask agent's final output. Also read shared facts published by subtasks.",
                parametersSchema =
                    """{"type":"object","properties":{"taskId":{"type":"string","description":"ID of the subtask to check (omit to list all)"},"factKey":{"type":"string","description":"Key of a shared fact to read (optional)"}},"required":[]}""",
            )

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val args = JSONObject(argumentsJson)
                val taskId = args.optString("taskId", "").trim()
                val factKey = args.optString("factKey", "").trim()

                if (factKey.isNotEmpty()) {
                    val value = subtaskExecutor.getSharedFact(factKey)
                    return ToolResult(
                        ok = true,
                        message = if (value != null) "Fact '$factKey' = $value" else "No fact found for key '$factKey'",
                        dataJson = value?.let { JSONObject().put("key", factKey).put("value", it).toString() },
                    )
                }

                if (taskId.isNotEmpty()) {
                    val state = subtaskExecutor.getState(taskId)
                        ?: return ToolResult(ok = false, message = "Unknown subtask: $taskId")
                    val json = JSONObject().apply {
                        put("taskId", state.taskId)
                        put("status", state.status.name)
                        when (state.status) {
                            SubtaskStatus.COMPLETED -> {
                                put("result", state.result ?: "No output")
                            }
                            SubtaskStatus.FAILED -> {
                                put("error", state.error ?: "unknown error")
                            }
                            else -> {
                                put("result", JSONObject.NULL)
                            }
                        }
                    }
                    val statusLabel = when (state.status) {
                        SubtaskStatus.COMPLETED -> "COMPLETED — ${state.result?.take(200) ?: "no output"}"
                        SubtaskStatus.FAILED -> "FAILED — ${state.error ?: "unknown"}"
                        SubtaskStatus.RUNNING -> "RUNNING (still in progress)"
                        SubtaskStatus.PENDING -> "PENDING (not started yet)"
                    }
                    return ToolResult(
                        ok = true,
                        message = "Subtask '$taskId': $statusLabel",
                        dataJson = json.toString(),
                    )
                }

                val allStates = subtaskExecutor.getAllStates()
                if (allStates.isEmpty()) {
                    return ToolResult(ok = true, message = "No subtasks found.")
                }
                val factsSnapshot = subtaskExecutor.allSharedFacts()
                val arr = JSONArray()
                val summary = StringBuilder("Subtask statuses:\n")
                for (s in allStates.values.sortedBy { it.taskId }) {
                    val statusLine = when (s.status) {
                        SubtaskStatus.COMPLETED -> "COMPLETED — ${s.result?.take(150) ?: "no output"}"
                        SubtaskStatus.FAILED -> "FAILED — ${s.error ?: "unknown"}"
                        else -> s.status.name
                    }
                    summary.append("- ${s.taskId}: $statusLine\n")
                    arr.put(JSONObject().apply {
                        put("taskId", s.taskId)
                        put("status", s.status.name)
                        put("result", s.result ?: JSONObject.NULL)
                        put("error", s.error ?: JSONObject.NULL)
                    })
                }
                if (factsSnapshot.isNotEmpty()) {
                    summary.append("\nShared facts:\n")
                    for ((k, v) in factsSnapshot) {
                        summary.append("- $k = $v\n")
                    }
                }
                ToolResult(
                    ok = true,
                    message = summary.toString().trimEnd(),
                    dataJson = JSONObject().put("subtasks", arr).apply {
                        if (factsSnapshot.isNotEmpty()) {
                            put("sharedFacts", JSONObject(factsSnapshot))
                        }
                    }.toString(),
                )
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "check_subtask failed")
            }
        }
    }
