package com.mobilebot.domain.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentInputTest {
    @Test
    fun userTextInputKeepsRawText() {
        val input = AgentInput(
            chatId = "chat-a",
            source = AgentInputSource.USER_TEXT,
            text = "帮我设置一个提醒",
        )

        assertEquals("帮我设置一个提醒", input.toPromptText())
    }

    @Test
    fun runtimeEventInputKeepsSourceSeparateFromUserText() {
        val input = AgentInput(
            chatId = "event-session",
            source = AgentInputSource.SYSTEM_RUNTIME_EVENT,
            text = "",
            eventFact = "type: IncomingSmsEvent\nsource: PetSmart\nbody: slot opened",
        )

        val prompt = input.toPromptText()

        assertTrue(prompt.contains("inputSource: SYSTEM_RUNTIME_EVENT"))
        assertTrue(prompt.contains("eventFact:"))
        assertTrue(prompt.contains("IncomingSmsEvent"))
        assertFalse(prompt.startsWith("[Scheduled]"))
    }

    @Test
    fun heartbeatInputCarriesMemoryHintWithoutBorrowingAnotherSession() {
        val input = AgentInput(
            chatId = "device-1",
            source = AgentInputSource.HEARTBEAT,
            text = "Review actionable items.",
            bootContext = "boot complete",
            memoryHint = "Use memory context.",
        )

        val prompt = input.toPromptText()

        assertEquals("device-1", input.chatId)
        assertTrue(prompt.contains("inputSource: HEARTBEAT"))
        assertTrue(prompt.contains("bootContext:\nboot complete"))
        assertTrue(prompt.contains("memoryHint:\nUse memory context."))
        assertFalse(prompt.contains("event-session"))
    }
}
