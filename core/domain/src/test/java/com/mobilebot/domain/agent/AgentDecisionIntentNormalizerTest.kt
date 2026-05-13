package com.mobilebot.domain.agent

import com.mobilebot.domain.LlmConfigurator
import com.mobilebot.model.StreamEvent
import com.mobilebot.model.ToolDefinition
import com.mobilebot.network.LlmClient
import com.mobilebot.network.LlmMessage
import com.mobilebot.network.LlmResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentDecisionIntentNormalizerTest {
    @Test
    fun selectedActionCommandNormalizesWithoutLlm() = runBlocking {
        val llm = RecordingLlmClient()
        val normalizer = normalizer(llm)

        val result = normalizer.normalize(
            baseInput(
                displayText = "可以",
                rawText = ACCEPT.command,
                presentedActions = listOf(AgentDecisionAction("可以", ACCEPT.command)),
            ),
        )

        assertEquals(ACCEPT, result.intent)
        assertEquals(ACCEPT.command, result.agentText)
        assertFalse(result.usedFallback)
        assertEquals(0, llm.requests.size)
    }

    @Test
    fun typedLabelNormalizesThroughSameCandidateSetWithoutLlm() = runBlocking {
        val llm = RecordingLlmClient()
        val normalizer = normalizer(llm)

        val result = normalizer.normalize(
            baseInput(
                displayText = "可以",
                rawText = "可以",
                presentedActions = listOf(AgentDecisionAction("可以", ACCEPT.command)),
            ),
        )

        assertEquals(ACCEPT, result.intent)
        assertEquals(ACCEPT.command, result.agentText)
        assertFalse(result.usedFallback)
        assertEquals(0, llm.requests.size)
    }

    @Test
    fun ambiguousTextUsesLlmWithPresentedActionsAndAllowedIntents() = runBlocking {
        val llm = RecordingLlmClient("""{"intent":"pet.accept"}""")
        val normalizer = normalizer(llm)

        val result = normalizer.normalize(
            baseInput(
                displayText = "行，就这么办",
                rawText = "行，就这么办",
                presentedActions = listOf(AgentDecisionAction("可以", ACCEPT.command)),
            ),
        )

        assertEquals(ACCEPT, result.intent)
        assertFalse(result.usedFallback)
        assertEquals(1, llm.requests.size)
        val prompt = llm.requests.single().joinToString("\n") { it.content.orEmpty() }
        assertTrue(prompt.contains("label=可以; value=${ACCEPT.command}"))
        assertTrue(prompt.contains("- pet.accept: User accepts the proposed slot."))
    }

    private fun normalizer(llm: RecordingLlmClient): AgentDecisionIntentNormalizer =
        AgentDecisionIntentNormalizer(
            llm = llm,
            llmConfigurator = LlmConfigurator {},
        )

    private fun baseInput(
        displayText: String,
        rawText: String,
        presentedActions: List<AgentDecisionAction>,
    ): AgentDecisionInput =
        AgentDecisionInput(
            contextId = "pet",
            promptText = "是否改到 14:00？",
            presentedActions = presentedActions,
            candidateIntents = listOf(ACCEPT, DECLINE, AgentDecisionIntents.Freeform),
            displayText = displayText,
            rawText = rawText,
        )

    private companion object {
        val ACCEPT = AgentDecisionIntent(
            id = "pet.accept",
            displayLabel = "可以",
            meaning = "User accepts the proposed slot.",
        )
        val DECLINE = AgentDecisionIntent(
            id = "pet.decline",
            displayLabel = "不改了",
            meaning = "User declines the proposed slot.",
        )
    }
}

private class RecordingLlmClient(
    private val content: String = """{"intent":"general.freeform"}""",
) : LlmClient {
    val requests = mutableListOf<List<LlmMessage>>()
    override var defaultModel: String = "stub"

    override suspend fun chat(
        messages: List<LlmMessage>,
        tools: List<ToolDefinition>?,
        model: String?,
        maxTokens: Int,
    ): LlmResponse {
        requests += messages
        return LlmResponse(
            content = content,
            toolCalls = emptyList(),
            finishReason = "stop",
        )
    }

    override fun chatStream(
        messages: List<LlmMessage>,
        tools: List<ToolDefinition>?,
        model: String?,
        maxTokens: Int,
    ): Flow<StreamEvent> = emptyFlow()
}
