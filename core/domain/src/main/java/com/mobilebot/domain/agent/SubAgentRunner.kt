package com.mobilebot.domain.agent

import android.util.Log
import com.mobilebot.domain.tools.ToolRegistry
import com.mobilebot.model.ToolDefinition
import com.mobilebot.network.LlmClient
import com.mobilebot.network.LlmMessage
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

data class SubAgentResult(
    val summary: String,
    val success: Boolean,
    val turnsUsed: Int,
)

/**
 * Runs an independent sub-agent with its own tool_calls loop.
 *
 * Used by SkillExecutor for `context: fork` skills. The sub-agent:
 * - Has its own system prompt (the skill body)
 * - Has its own tool set (filtered by allowed-tools)
 * - Has its own turn budget
 * - Returns only a final summary to the parent context
 */
@Singleton
class SubAgentRunner @Inject constructor(
    private val llm: LlmClient,
    private val toolRegistryProvider: Provider<ToolRegistry>,
) {

    suspend fun run(
        systemPrompt: String,
        tools: List<ToolDefinition>,
        maxTurns: Int = DEFAULT_MAX_TURNS,
        effort: String = "medium",
        depth: Int = 0,
    ): SubAgentResult {
        if (depth > MAX_DEPTH) {
            return SubAgentResult(
                summary = "Error: maximum skill nesting depth ($MAX_DEPTH) exceeded.",
                success = false,
                turnsUsed = 0,
            )
        }

        val messages = mutableListOf<LlmMessage>()
        messages.add(LlmMessage(role = "system", content = systemPrompt))

        val maxTokens = when (effort) {
            "low" -> 1024
            "high" -> 8192
            else -> 4096
        }

        var turnsUsed = 0
        while (turnsUsed++ < maxTurns) {
            val response = llm.chat(
                messages = messages,
                tools = tools,
                model = null,
                maxTokens = maxTokens,
            )

            if (response.toolCalls.isEmpty()) {
                val reply = response.content?.trim() ?: "Task completed."
                return SubAgentResult(summary = reply, success = true, turnsUsed = turnsUsed)
            }

            messages.add(LlmMessage(
                role = "assistant",
                content = response.content.orEmpty(),
                toolCalls = response.toolCalls,
            ))

            for (tc in response.toolCalls) {
                val result = toolRegistryProvider.get().execute(tc.name, tc.argumentsJson)
                val body = when {
                    !result.ok -> "Error: ${result.message}"
                    !result.dataJson.isNullOrBlank() -> result.message + "\n" + result.dataJson
                    else -> result.message
                }
                messages.add(LlmMessage(
                    role = "tool",
                    content = body,
                    toolCallId = tc.id,
                    name = tc.name,
                ))
                Log.d(TAG, "SubAgent tool ${tc.name} -> ${if (result.ok) "OK" else "FAIL"}: ${body.take(150)}")
            }
        }

        val lastContent = messages.lastOrNull { it.role == "assistant" }?.content
        return SubAgentResult(
            summary = lastContent ?: "Sub-agent reached turn limit without final answer.",
            success = false,
            turnsUsed = turnsUsed,
        )
    }

    companion object {
        private const val TAG = "SubAgentRunner"
        private const val DEFAULT_MAX_TURNS = 15
        const val MAX_DEPTH = 3
    }
}
