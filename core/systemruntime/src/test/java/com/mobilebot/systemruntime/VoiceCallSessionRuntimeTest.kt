package com.mobilebot.systemruntime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCallSessionRuntimeTest {
    @Test
    fun `finishSession stores runtime transcript with extracted shopping items`() {
        val runtime = VoiceCallSessionRuntime()
        val session = runtime.startSession(
            sessionId = "ella-call",
            contact = "Ella",
            personaId = "ella",
        )

        runtime.appendTurn(session.id, VoiceCallSpeaker.AGENT, "喂，我想麻烦你下午帮家里补点东西。")
        runtime.appendTurn(session.id, VoiceCallSpeaker.USER, "可以，要买什么？")
        runtime.appendTurn(session.id, VoiceCallSpeaker.AGENT, "低脂牛奶和常用洗衣液优先，水果顺路再买就好。")
        val transcript = runtime.finishSession(session.id)

        assertNotNull(transcript)
        assertEquals("runtime-call:ella-call", transcript?.audioRef)
        assertEquals("Ella", transcript?.contact)
        assertTrue(transcript?.transcript.orEmpty().contains("低脂牛奶"))
        assertEquals(listOf("低脂牛奶", "常用洗衣液", "水果"), transcript?.tasks?.single()?.items)
        assertEquals(transcript, runtime.findTranscript("runtime-call:ella-call", "Ella"))
    }

    @Test
    fun `setPhase keeps current session without changing transcript`() {
        val runtime = VoiceCallSessionRuntime()
        runtime.startSession("ella-call", "Ella", "ella")

        val updated = runtime.setPhase("ella-call", VoiceCallPhase.THINKING)

        assertEquals(VoiceCallPhase.THINKING, updated?.phase)
        assertEquals(emptyList<VoiceCallTurn>(), updated?.turns)
    }
}
