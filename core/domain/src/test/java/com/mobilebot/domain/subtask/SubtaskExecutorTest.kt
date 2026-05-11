package com.mobilebot.domain.subtask

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SubtaskExecutorTest {

    @Test
    fun sharedFactsWorkCorrectly() {
        val executor = createStubExecutor()
        assertNull(executor.getSharedFact("police_case_number"))

        executor.publishFact("police_case_number", "MT-2024-0891")
        assertEquals("MT-2024-0891", executor.getSharedFact("police_case_number"))

        executor.publishFact("tow_destination", "Billings, MT")
        assertEquals("Billings, MT", executor.getSharedFact("tow_destination"))

        val allFacts = executor.allSharedFacts()
        assertEquals(2, allFacts.size)
        assertEquals("MT-2024-0891", allFacts["police_case_number"])
    }

    @Test
    fun overwritesExistingFact() {
        val executor = createStubExecutor()
        executor.publishFact("hotel", "Marriott")
        executor.publishFact("hotel", "Hilton")
        assertEquals("Hilton", executor.getSharedFact("hotel"))
    }

    private fun createStubExecutor(): SubtaskExecutor {
        val noopForeground = object : com.mobilebot.domain.ForegroundController {
            override fun onAgentStart() {}
            override fun onAgentStop() {}
        }
        return SubtaskExecutor(
            toolCallLoopProvider = javax.inject.Provider { throw UnsupportedOperationException("stub") },
            agentLoopProvider = javax.inject.Provider { throw UnsupportedOperationException("stub") },
            sessions = StubSessionRepository(),
            bus = com.mobilebot.bus.MessageBus(),
            sessionKeyProvider = com.mobilebot.domain.agent.CurrentSessionKeyProvider(),
            foreground = noopForeground,
        )
    }
}

private class StubSessionRepository : com.mobilebot.domain.repository.SessionRepository {
    override suspend fun listSessionKeys() = emptyList<String>()
    override suspend fun listSessionMetas() = emptyList<com.mobilebot.domain.repository.SessionMeta>()
    override suspend fun getMessages(sessionKey: String) = emptyList<com.mobilebot.model.ChatMessage>()
    override suspend fun getFirstUserContent(sessionKey: String): String? = null
    override suspend fun replaceMessages(sessionKey: String, messages: List<com.mobilebot.model.ChatMessage>) {}
    override suspend fun appendUserMessage(sessionKey: String, content: String) = emptyList<com.mobilebot.model.ChatMessage>()
    override suspend fun appendAssistantMessage(sessionKey: String, content: String, toolCallId: String?, toolName: String?, toolCalls: String?) {}
    override suspend fun appendToolMessage(sessionKey: String, content: String, toolCallId: String, toolName: String) {}
    override suspend fun ensureSession(sessionKey: String) {}
    override suspend fun deleteSession(sessionKey: String) {}
}
