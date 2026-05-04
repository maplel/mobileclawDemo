package com.mobilebot.network

import com.mobilebot.model.StreamEvent
import com.mobilebot.model.ToolDefinition
import kotlinx.coroutines.flow.Flow

data class LlmMessage(
    val role: String,
    val content: String,
    val toolCallId: String? = null,
    val name: String? = null,
    val toolCalls: List<LlmToolCall>? = null,
)

interface LlmClient {
    var defaultModel: String

    suspend fun chat(
        messages: List<LlmMessage>,
        tools: List<ToolDefinition>?,
        model: String?,
        maxTokens: Int,
    ): LlmResponse

    fun chatStream(
        messages: List<LlmMessage>,
        tools: List<ToolDefinition>?,
        model: String?,
        maxTokens: Int,
    ): Flow<StreamEvent>
}

data class LlmResponse(
    val content: String?,
    val toolCalls: List<LlmToolCall>,
    val finishReason: String?,
)

data class LlmToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)
