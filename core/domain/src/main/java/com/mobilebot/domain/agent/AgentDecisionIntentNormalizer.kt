package com.mobilebot.domain.agent

import com.mobilebot.domain.LlmConfigurator
import com.mobilebot.network.LlmClient
import com.mobilebot.network.LlmMessage
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import javax.inject.Inject

class AgentDecisionIntentNormalizer
    @Inject
    constructor(
        private val llm: LlmClient,
        private val llmConfigurator: LlmConfigurator,
    ) {
        suspend fun normalize(input: AgentDecisionInput): AgentDecisionResult {
            val candidates = input.candidateIntents.ifEmpty { AgentDecisionIntents.defaults }

            val directIntent = directIntent(input, candidates)
            if (directIntent != null) {
                return AgentDecisionResult(
                    intent = directIntent,
                    displayText = input.displayText,
                    agentText = directIntent.agentText(input.rawText),
                    usedFallback = false,
                )
            }

            val llmIntent = withTimeoutOrNull(INTENT_TIMEOUT_MS) {
                runCatching {
                    llmConfigurator.beforeRequest()
                    val response = llm.chat(
                        messages = buildMessages(input, candidates),
                        tools = null,
                        model = null,
                        maxTokens = 120,
                    )
                    parseIntent(response.content.orEmpty(), candidates)
                }.getOrNull()
            }

            val intent = llmIntent ?: AgentDecisionIntents.Freeform
            return AgentDecisionResult(
                intent = intent,
                displayText = input.displayText,
                agentText = intent.agentText(input.rawText),
                usedFallback = llmIntent == null,
            )
        }

        private fun directIntent(
            input: AgentDecisionInput,
            candidates: List<AgentDecisionIntent>,
        ): AgentDecisionIntent? {
            val display = input.displayText.trim()
            val raw = input.rawText.trim()
            val exact = candidates.firstOrNull { intent ->
                display.equals(intent.displayLabel, ignoreCase = true) ||
                    raw.equals(intent.id, ignoreCase = true) ||
                    raw.equals(intent.command, ignoreCase = true)
            }
            if (exact != null) return exact

            val selectedAction = input.presentedActions.firstOrNull { action ->
                display.equals(action.label, ignoreCase = true) ||
                    raw.equals(action.value, ignoreCase = true)
            } ?: return null

            return candidates.firstOrNull { intent ->
                selectedAction.label.equals(intent.displayLabel, ignoreCase = true) ||
                    selectedAction.value.equals(intent.id, ignoreCase = true) ||
                    selectedAction.value.equals(intent.command, ignoreCase = true)
            }
        }

        private fun buildMessages(
            input: AgentDecisionInput,
            candidates: List<AgentDecisionIntent>,
        ): List<LlmMessage> {
            val candidateText = candidates.joinToString("\n") { "- ${it.id}: ${it.meaning}" }
            val actionsText = input.presentedActions.joinToString("\n") {
                "- label=${it.label}; value=${it.value}"
            }.ifBlank { "(none)" }
            return listOf(
                LlmMessage(
                    role = "system",
                    content = """
                        You normalize a user's decision into exactly one intent id.
                        Return JSON only, with this shape: {"intent":"<id>"}.
                        Do not explain. Do not translate. Do not invent ids.
                    """.trimIndent(),
                ),
                LlmMessage(
                    role = "user",
                    content = """
                        Context: ${input.contextId}
                        Prompt shown to user:
                        ${input.promptText}

                        Presented actions:
                        $actionsText

                        User visible reply:
                        ${input.displayText}

                        Raw value to interpret:
                        ${input.rawText}

                        Allowed intents:
                        $candidateText
                    """.trimIndent(),
                ),
            )
        }

        private fun parseIntent(
            raw: String,
            candidates: List<AgentDecisionIntent>,
        ): AgentDecisionIntent? {
            val ids = candidates.associateBy { it.id }
            val content = raw.trim()
            val idFromJson = extractJsonObject(content)?.optString("intent")?.trim()
            val id = idFromJson?.ifBlank { null } ?: content.lineSequence().firstOrNull()?.trim()
            return ids[id]
        }

        private fun extractJsonObject(raw: String): JSONObject? {
            val start = raw.indexOf('{')
            if (start < 0) return null
            var depth = 0
            var inString = false
            var escape = false
            for (index in start until raw.length) {
                val char = raw[index]
                if (escape) {
                    escape = false
                    continue
                }
                if (inString) {
                    when (char) {
                        '\\' -> escape = true
                        '"' -> inString = false
                    }
                    continue
                }
                when (char) {
                    '"' -> inString = true
                    '{' -> depth += 1
                    '}' -> {
                        depth -= 1
                        if (depth == 0) {
                            return runCatching { JSONObject(raw.substring(start, index + 1)) }.getOrNull()
                        }
                    }
                }
            }
            return null
        }

        companion object {
            private const val INTENT_TIMEOUT_MS = 6_000L
        }
    }

data class AgentDecisionInput(
    val contextId: String,
    val promptText: String,
    val presentedActions: List<AgentDecisionAction>,
    val candidateIntents: List<AgentDecisionIntent>,
    val displayText: String,
    val rawText: String,
)

data class AgentDecisionAction(
    val label: String,
    val value: String,
)

data class AgentDecisionIntent(
    val id: String,
    val displayLabel: String,
    val meaning: String,
    val agentInstruction: String? = null,
    val includeRawText: Boolean = false,
) {
    val command: String = "USER_INTENT:$id"

    fun agentText(rawText: String): String =
        when {
            agentInstruction != null -> "$command\n$agentInstruction"
            includeRawText -> "$command\nUSER_TEXT:${rawText.trim()}"
            else -> command
        }
}

data class AgentDecisionResult(
    val intent: AgentDecisionIntent,
    val displayText: String,
    val agentText: String,
    val usedFallback: Boolean,
)

object AgentDecisionIntents {
    val ModifyPlan = AgentDecisionIntent(
        id = "general.modify_plan",
        displayLabel = "修改计划",
        meaning = "The user wants to modify the current plan.",
    )

    val Continue = AgentDecisionIntent(
        id = "general.continue",
        displayLabel = "继续",
        meaning = "The user wants the agent to continue with the current plan or next safe step.",
    )

    val RewritePlan = AgentDecisionIntent(
        id = "general.rewrite_plan",
        displayLabel = "重写计划",
        meaning = "The user wants the agent to rewrite the current plan.",
    )

    val Cancel = AgentDecisionIntent(
        id = "general.cancel",
        displayLabel = "取消",
        meaning = "The user wants to cancel the current action.",
    )

    val Freeform = AgentDecisionIntent(
        id = "general.freeform",
        displayLabel = "Freeform",
        meaning = "The reply does not map cleanly to a declared intent.",
        includeRawText = true,
    )

    val defaults = listOf(Continue, ModifyPlan, RewritePlan, Cancel, Freeform)
}
