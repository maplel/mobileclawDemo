package com.mobilebot.domain.agent

import android.util.Log
import com.mobilebot.domain.AgentLoop
import com.mobilebot.domain.LlmConfigurator
import com.mobilebot.domain.repository.SessionRepository
import com.mobilebot.domain.tools.EmitScenarioCommandsTool
import com.mobilebot.model.ChatMessage
import com.mobilebot.model.ChatRole
import com.mobilebot.network.LlmClient
import com.mobilebot.network.LlmMessage
import com.mobilebot.network.LlmToolCall
import com.mobilebot.scenarios.runtime.ScenarioAgentCommand
import com.mobilebot.scenarios.runtime.ScenarioCommandBatch
import com.mobilebot.scenarios.runtime.ScenarioCommandCodec
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScenarioAgentTurnRunner
    @Inject
    constructor(
        private val llm: LlmClient,
        private val llmConfigurator: LlmConfigurator,
        private val sessions: SessionRepository,
        private val outputTool: EmitScenarioCommandsTool,
    ) {
        suspend fun run(input: ScenarioAgentTurnInput): ScenarioAgentTurnResult {
            llmConfigurator.beforeRequest()
            val sessionKey = "${AgentLoop.CHANNEL}:${input.sessionId}"
            val userPrompt = input.toPrompt()
            val history = sessions.appendUserMessage(sessionKey, userPrompt)
            val messages = mutableListOf(LlmMessage(role = "system", content = systemPrompt()))
            history.takeLast(MAX_HISTORY).mapNotNullTo(messages) { it.toLlmMessageOrNull() }

            val response = llm.chat(
                messages = messages,
                tools = listOf(outputTool.definition),
                model = null,
                maxTokens = MAX_TOKENS,
            )

            val outputToolCalls = response.toolCalls.filter { it.name == EmitScenarioCommandsTool.NAME }
            Log.i(
                TAG,
                "turn session=$sessionKey type=${input.turnType} task=${input.taskId.orEmpty()} " +
                    "toolCalls=${response.toolCalls.size} outputCalls=${outputToolCalls.size} " +
                    "contentChars=${response.content?.length ?: 0}",
            )
            return if (outputToolCalls.size == 1) {
                sessions.appendAssistantMessage(
                    sessionKey = sessionKey,
                    content = response.content.orEmpty(),
                    toolCalls = encodeToolCalls(response.toolCalls),
                )
                parseToolCall(sessionKey, outputToolCalls.single(), response.content.orEmpty())
            } else if (outputToolCalls.size > 1) {
                val error = "同一轮只能调用一次 ${EmitScenarioCommandsTool.NAME}。"
                sessions.appendAssistantMessage(
                    sessionKey = sessionKey,
                    content = response.content.orEmpty(),
                    toolCalls = encodeToolCalls(response.toolCalls),
                )
                sessions.appendToolMessage(sessionKey, error, outputToolCalls.first().id, EmitScenarioCommandsTool.NAME)
                ScenarioAgentTurnResult(error = error, rawText = response.content.orEmpty())
            } else {
                response.content?.let { sessions.appendAssistantMessage(sessionKey, it) }
                parseAssistantContent(response.content.orEmpty())
            }
        }

        private suspend fun parseToolCall(
            sessionKey: String,
            toolCall: LlmToolCall,
            rawText: String,
        ): ScenarioAgentTurnResult {
            Log.i(TAG, "parse tool_call session=$sessionKey id=${toolCall.id} args=${preview(toolCall.argumentsJson)}")
            val parsed = ScenarioCommandCodec.parse(toolCall.argumentsJson)
            if (!parsed.isOk) {
                val error = parsed.error ?: "命令解析失败。"
                Log.w(TAG, "parse error session=$sessionKey id=${toolCall.id}: $error")
                sessions.appendToolMessage(sessionKey, error, toolCall.id, toolCall.name)
                return ScenarioAgentTurnResult(error = error, rawText = rawText)
            }
            val batch = parsed.batch ?: ScenarioCommandBatch(emptyList())
            duplicateCommandError(batch)?.let { error ->
                Log.w(TAG, "duplicate command session=$sessionKey id=${toolCall.id}: $error")
                sessions.appendToolMessage(sessionKey, error, toolCall.id, toolCall.name)
                return ScenarioAgentTurnResult(error = error, rawText = rawText)
            }
            val resultJson = ScenarioCommandCodec.toJson(batch)
            Log.i(TAG, "parsed commands session=$sessionKey id=${toolCall.id} count=${batch.commands.size}")
            sessions.appendToolMessage(sessionKey, resultJson, toolCall.id, toolCall.name)
            return ScenarioAgentTurnResult(commands = batch.commands, rawText = rawText)
        }

        private fun parseAssistantContent(rawText: String): ScenarioAgentTurnResult {
            val parsed = ScenarioCommandCodec.parse(rawText)
            if (!parsed.isOk) {
                return ScenarioAgentTurnResult(
                    error = parsed.error ?: "LLM 没有返回结构化命令。",
                    rawText = rawText,
                )
            }
            val batch = parsed.batch ?: ScenarioCommandBatch(emptyList())
            duplicateCommandError(batch)?.let { return ScenarioAgentTurnResult(error = it, rawText = rawText) }
            return ScenarioAgentTurnResult(commands = batch.commands, rawText = rawText)
        }

        private fun duplicateCommandError(batch: ScenarioCommandBatch): String? {
            val seen = mutableSetOf<String>()
            batch.commands.forEach { command ->
                val signature = ScenarioCommandCodec.toJson(ScenarioCommandBatch(listOf(command)))
                if (!seen.add(signature)) return "同一轮出现重复命令，已停止执行。"
            }
            return null
        }

        private fun systemPrompt(): String =
            """
            你是手机端 Agent 的任务编排器。
            你只能通过工具 ${EmitScenarioCommandsTool.NAME} 输出命令，不要直接解释执行过程。
            命令必须严格来自白名单：create_task, update_task, send_sms, wait_sms, create_reminder, ask_user, switch_task, complete_task。
            SystemRuntime 给出的内容是已经发生的外部事实；你负责理解事实、结合 Skill 指令和用户记忆，决定任务更新和工具动作。
            只有确实需要用户决策时才使用 ask_user。
            ask_user 命令必须提供 decision.text 和 decision.actions；每个 action 必须包含 label 和 key。
            用户可见文案使用中文，保持简短、真实、低打扰。
            """.trimIndent()

        private fun ScenarioAgentTurnInput.toPrompt(): String =
            buildString {
                appendLine("scenarioId: $scenarioId")
                appendLine("skillName: $skillName")
                appendLine("taskId: ${taskId.orEmpty()}")
                appendLine("turnType: $turnType")
                appendLine("eventFact:")
                appendLine(eventFact.ifBlank { "(none)" })
                appendLine("currentTask:")
                appendLine(currentTaskSnapshot.ifBlank { "(none)" })
                appendLine("allTasks:")
                appendLine(allTaskSnapshots.ifBlank { "(none)" })
                appendLine("recentToolResults:")
                appendLine(recentToolResults.ifBlank { "(none)" })
                appendLine("userInput:")
                appendLine(userInput.ifBlank { "(none)" })
                appendLine("normalizedIntent:")
                normalizedIntent?.let { intent ->
                    appendLine("id: ${intent.id}")
                    appendLine("command: ${intent.command}")
                    appendLine("meaning: ${intent.meaning}")
                    appendLine("agentText:")
                    appendLine(intent.agentText(userInput))
                } ?: appendLine("(none)")
                appendLine("presentedActions:")
                if (presentedActions.isEmpty()) {
                    appendLine("(none)")
                } else {
                    presentedActions.forEach { appendLine("- ${it.label}: ${it.value}") }
                }
                appendLine("memory:")
                appendLine(memoryDigest.ifBlank { "(none)" })
                appendLine("skillInstruction:")
                appendLine(skillInstruction.ifBlank { "(none)" })
            }

        private fun ChatMessage.toLlmMessageOrNull(): LlmMessage? =
            LlmMessage(
                role = when (role) {
                    ChatRole.System -> "system"
                    ChatRole.User -> "user"
                    ChatRole.Assistant -> "assistant"
                    ChatRole.Tool -> "tool"
                },
                content = content,
                toolCallId = toolCallId,
                name = toolName,
                toolCalls = if (role == ChatRole.Assistant) decodeToolCalls(toolCallsJson) else null,
            )

        private fun encodeToolCalls(toolCalls: List<LlmToolCall>): String =
            JSONArray().apply {
                toolCalls.forEach { tc ->
                    put(
                        JSONObject()
                            .put("id", tc.id)
                            .put("name", tc.name)
                            .put("argumentsJson", tc.argumentsJson),
                    )
                }
            }.toString()

        private fun decodeToolCalls(raw: String?): List<LlmToolCall>? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (index in 0 until arr.length()) {
                        val obj = arr.optJSONObject(index) ?: continue
                        val id = obj.optString("id").trim()
                        val name = obj.optString("name").trim()
                        val args = obj.optString("argumentsJson").ifBlank {
                            obj.optString("arguments", "{}")
                        }
                        if (id.isNotBlank() && name.isNotBlank()) {
                            add(LlmToolCall(id = id, name = name, argumentsJson = args.ifBlank { "{}" }))
                        }
                    }
                }.takeIf { it.isNotEmpty() }
            }.getOrNull()
        }

        companion object {
            private const val TAG = "ScenarioAgentRunner"
            private const val MAX_HISTORY = 12
            private const val MAX_TOKENS = 1800

            private fun preview(value: String): String =
                value.replace('\n', ' ').take(600)
        }
    }

data class ScenarioAgentTurnInput(
    val sessionId: String,
    val scenarioId: String,
    val skillName: String,
    val turnType: String,
    val taskId: String? = null,
    val eventFact: String = "",
    val currentTaskSnapshot: String = "",
    val allTaskSnapshots: String = "",
    val recentToolResults: String = "",
    val userInput: String = "",
    val normalizedIntent: AgentDecisionIntent? = null,
    val presentedActions: List<AgentDecisionAction> = emptyList(),
    val memoryDigest: String = "",
    val skillInstruction: String = "",
)

data class ScenarioAgentTurnResult(
    val commands: List<ScenarioAgentCommand> = emptyList(),
    val rawText: String = "",
    val error: String? = null,
) {
    val isOk: Boolean = error == null
}
