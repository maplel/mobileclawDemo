package com.mobilebot.model

data class InboundMessage(
    val channel: String = "mobile",
    val senderId: String,
    val chatId: String,
    val content: String,
    val metadata: Map<String, String> = emptyMap(),
)

data class OutboundMessage(
    val channel: String,
    val chatId: String,
    val content: String,
    val metadata: Map<String, String> = emptyMap(),
)

enum class ChatRole {
    System,
    User,
    Assistant,
    Tool,
}

data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolCallsJson: String? = null,
)

data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)
