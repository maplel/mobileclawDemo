package com.mobilebot.chat

import com.mobilebot.domain.LlmConfigurator
import com.mobilebot.network.LlmClient
import com.mobilebot.network.LlmMessage
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import javax.inject.Inject

class ScenarioDecisionIntentNormalizer
    @Inject
    constructor(
        private val llm: LlmClient,
        private val llmConfigurator: LlmConfigurator,
    ) {
        suspend fun normalize(input: ScenarioDecisionInput): ScenarioDecisionResult {
            val candidates = ScenarioDecisionIntent.forScenario(input.scenarioId)
            if (candidates.isEmpty()) {
                return ScenarioDecisionResult(
                    intent = ScenarioDecisionIntent.Freeform,
                    displayText = input.displayText,
                    agentText = ScenarioDecisionIntent.Freeform.agentText(input.rawText),
                    usedFallback = true,
                )
            }

            val directIntent = directIntent(input, candidates)
            if (directIntent != null) {
                return ScenarioDecisionResult(
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

            val intent = llmIntent ?: ScenarioDecisionIntent.Freeform
            return ScenarioDecisionResult(
                intent = intent,
                displayText = input.displayText,
                agentText = intent.agentText(input.rawText),
                usedFallback = llmIntent == null,
            )
        }

        private fun directIntent(
            input: ScenarioDecisionInput,
            candidates: List<ScenarioDecisionIntent>,
        ): ScenarioDecisionIntent? {
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
            input: ScenarioDecisionInput,
            candidates: List<ScenarioDecisionIntent>,
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
                        Scenario: ${input.scenarioId}
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
            candidates: List<ScenarioDecisionIntent>,
        ): ScenarioDecisionIntent? {
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

data class ScenarioDecisionInput(
    val scenarioId: String,
    val promptText: String,
    val presentedActions: List<ActionButton>,
    val displayText: String,
    val rawText: String,
)

data class ScenarioDecisionResult(
    val intent: ScenarioDecisionIntent,
    val displayText: String,
    val agentText: String,
    val usedFallback: Boolean,
)

sealed class ScenarioDecisionIntent(
    val id: String,
    val displayLabel: String,
    val meaning: String,
) {
    val command: String = "USER_INTENT:$id"

    open fun agentText(rawText: String): String = command

    data object PetGroomingKeepCurrentWeek : ScenarioDecisionIntent(
        id = "pet_grooming.keep_current_week",
        displayLabel = "好的",
        meaning = "Y wants to keep Kylin's regular grooming appointment this week.",
    )

    data object PetGroomingDeferCurrentWeek : ScenarioDecisionIntent(
        id = "pet_grooming.defer_current_week",
        displayLabel = "改天再说",
        meaning = "Y wants to skip or postpone this week's Kylin grooming run.",
    )

    data object PetGroomingBookNine : ScenarioDecisionIntent(
        id = "pet_grooming.book_0900",
        displayLabel = "约9点",
        meaning = "Y chooses the 9:00 PetSmart slot and authorizes routine downstream coordination.",
    )

    data object PetGroomingAskAfternoon : ScenarioDecisionIntent(
        id = "pet_grooming.ask_afternoon",
        displayLabel = "问下午",
        meaning = "Y wants the agent to ask PetSmart about afternoon availability before deciding.",
    ) {
        override fun agentText(rawText: String): String =
            """
            $command
            NEXT_OPERATION: The next assistant step must be a tool call, not user-facing prose. Call system_send_sms to PetSmart with this exact Chinese message: `请问明天下午5点以后可以只安排基础洗澡服务吗？` Then call system_wait_for_sms for PetSmart's reply. Do not repeat the previous options before the new PetSmart SMS is received. Do not write English words such as booking or bath-only in the SMS body.
            """.trimIndent()
    }

    data object PetGroomingBookAfternoonBathOnly : ScenarioDecisionIntent(
        id = "pet_grooming.book_afternoon_bath_only",
        displayLabel = "约下午5点",
        meaning = "Y accepts the afternoon bath-only PetSmart slot and authorizes routine downstream coordination.",
    ) {
        override fun agentText(rawText: String): String =
            """
            $command
            FINAL_SELECTION: Y has accepted the afternoon bath-only PetSmart slot. This is not another availability question.
            NEXT_OPERATION: The next assistant step must be a tool call, not user-facing prose. Call system_send_sms to PetSmart with this exact confirmation message: `您好，确认明天下午5点后给 Kylin 预约洗澡，不做除毛。谢谢。` Then call system_wait_for_sms for PetSmart's booking confirmation. Do not ask PetSmart about availability again. Do not show another action prompt for the afternoon tradeoff. Do not ask Y to confirm the same option again.
            """.trimIndent()
    }

    data object PetGroomingFindAlternative : ScenarioDecisionIntent(
        id = "pet_grooming.find_alternative_shop",
        displayLabel = "换一家",
        meaning = "Y wants to look for another grooming shop.",
    ) {
        override fun agentText(rawText: String): String =
            """
            $command
            NEXT_OPERATION: The next assistant step must be a tool call, not user-facing prose. Use device_system service_call with serviceId `pet_salon_search` to list nearby grooming shops, then choose a non-PetSmart shop that supports both extra-large dog bathing and de-shedding. Prefer Harbor Paws Salon if it is present because it supports both required services. Call get_pet_shop_detail for the chosen shop, then send SMS to that shop with this Chinese message: `请问明天下午2点可以给 Kylin 安排基础洗澡和去浮毛服务吗？她是30公斤以上的伯恩山犬。` Then call system_wait_for_sms for that shop's reply. Use the chosen shop name consistently in later Driver, payment, accounting, and summary steps.
            """.trimIndent()
    }

    data object ModifyPlan : ScenarioDecisionIntent(
        id = "general.modify_plan",
        displayLabel = "修改计划",
        meaning = "The user wants to modify the current plan.",
    )

    data object Continue : ScenarioDecisionIntent(
        id = "general.continue",
        displayLabel = "继续",
        meaning = "The user wants the agent to continue with the current plan or next safe step.",
    )

    data object RewritePlan : ScenarioDecisionIntent(
        id = "general.rewrite_plan",
        displayLabel = "重写计划",
        meaning = "The user wants the agent to rewrite the current plan.",
    )

    data object Cancel : ScenarioDecisionIntent(
        id = "general.cancel",
        displayLabel = "取消",
        meaning = "The user wants to cancel the current action.",
    )

    data object Freeform : ScenarioDecisionIntent(
        id = "general.freeform",
        displayLabel = "Freeform",
        meaning = "The reply does not map cleanly to a declared intent.",
    ) {
        override fun agentText(rawText: String): String =
            "$command\nUSER_TEXT:${rawText.trim()}"
    }

    companion object {
        fun forScenario(scenarioId: String): List<ScenarioDecisionIntent> =
            when (scenarioId) {
                "pet-grooming" -> listOf(
                    PetGroomingKeepCurrentWeek,
                    PetGroomingDeferCurrentWeek,
                    PetGroomingBookNine,
                    PetGroomingAskAfternoon,
                    PetGroomingBookAfternoonBathOnly,
                    PetGroomingFindAlternative,
                    Continue,
                    ModifyPlan,
                    RewritePlan,
                    Cancel,
                    Freeform,
                )
                else -> listOf(Continue, ModifyPlan, RewritePlan, Cancel, Freeform)
            }
    }
}
