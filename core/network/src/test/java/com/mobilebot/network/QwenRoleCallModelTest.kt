package com.mobilebot.network

import com.mobilebot.model.StreamEvent
import com.mobilebot.model.ToolDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QwenRoleCallModelTest {
    @Test
    fun `nextReply uses qwen turbo without tools`() = runBlocking {
        val client = RecordingLlmClient("低脂牛奶和常用洗衣液优先，水果顺路。")
        val model = QwenRoleCallModel(client)

        val reply = model.nextReply(
            RoleCallRequest(
                personaId = "ella",
                personaInstruction = "扮演 Ella",
                contactName = "Ella",
                transcript = listOf(RoleCallTurn("用户", "要买什么？")),
                latestUserText = "要买什么？",
            ),
        )

        assertEquals("低脂牛奶和常用洗衣液优先，水果顺路。", reply.text)
        assertEquals("qwen-turbo", client.model)
        assertNull(client.tools)
        assertTrue(client.messages.first().content.contains("扮演 Ella"))
    }

    private class RecordingLlmClient(
        private val content: String,
    ) : LlmClient {
        lateinit var messages: List<LlmMessage>
        var tools: List<ToolDefinition>? = null
        var model: String? = null
        override var defaultModel: String = "unused"

        override suspend fun chat(
            messages: List<LlmMessage>,
            tools: List<ToolDefinition>?,
            model: String?,
            maxTokens: Int,
        ): LlmResponse {
            this.messages = messages
            this.tools = tools
            this.model = model
            return LlmResponse(content = content, toolCalls = emptyList(), finishReason = "stop")
        }

        override fun chatStream(
            messages: List<LlmMessage>,
            tools: List<ToolDefinition>?,
            model: String?,
            maxTokens: Int,
        ): Flow<StreamEvent> = emptyFlow()
    }
}
