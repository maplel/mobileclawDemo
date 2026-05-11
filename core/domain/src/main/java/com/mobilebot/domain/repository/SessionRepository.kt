package com.mobilebot.domain.repository

import com.mobilebot.model.ChatMessage

data class SessionMeta(
    val sessionKey: String,
    val createdAt: Long,
    val updatedAt: Long,
)

interface SessionRepository {
    suspend fun listSessionKeys(): List<String>

    suspend fun listSessionMetas(): List<SessionMeta>

    suspend fun getMessages(sessionKey: String): List<ChatMessage>

    suspend fun getFirstUserContent(sessionKey: String): String?

    suspend fun replaceMessages(
        sessionKey: String,
        messages: List<ChatMessage>,
    )

    /** Persists the user turn and returns all messages for this session (same transaction as insert). */
    suspend fun appendUserMessage(
        sessionKey: String,
        content: String,
    ): List<ChatMessage>

    suspend fun appendAssistantMessage(
        sessionKey: String,
        content: String,
        toolCallId: String? = null,
        toolName: String? = null,
        toolCalls: String? = null,
    )

    suspend fun appendToolMessage(
        sessionKey: String,
        content: String,
        toolCallId: String,
        toolName: String,
    )

    suspend fun ensureSession(sessionKey: String)

    suspend fun deleteSession(sessionKey: String)
}
