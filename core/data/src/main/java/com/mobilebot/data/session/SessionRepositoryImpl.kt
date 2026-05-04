package com.mobilebot.data.session

import androidx.room.withTransaction
import com.mobilebot.data.db.AppDatabase
import com.mobilebot.data.db.MessageEntity
import com.mobilebot.data.db.SessionEntity
import com.mobilebot.domain.repository.SessionMeta
import com.mobilebot.domain.repository.SessionRepository
import com.mobilebot.model.ChatMessage
import com.mobilebot.model.ChatRole
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl
    @Inject
    constructor(
        db: AppDatabase,
    ) : SessionRepository {
        private val database = db
        private val sessionDao = db.sessionDao()
        private val messageDao = db.messageDao()

        override suspend fun listSessionKeys(): List<String> = sessionDao.listKeys()

        override suspend fun listSessionMetas(): List<SessionMeta> =
            sessionDao.listAll().map { SessionMeta(it.sessionKey, it.createdAt, it.updatedAt) }

        override suspend fun getMessages(sessionKey: String): List<ChatMessage> =
            messageDao.listForSession(sessionKey).map { it.toModel() }

        override suspend fun getFirstUserContent(sessionKey: String): String? =
            messageDao.firstUserContent(sessionKey)

        override suspend fun replaceMessages(
            sessionKey: String,
            messagesList: List<ChatMessage>,
        ) {
            ensureSession(sessionKey)
            val now = System.currentTimeMillis()
            database.withTransaction {
                messageDao.clearSession(sessionKey)
                for (m in messagesList) {
                    messageDao.insert(
                        MessageEntity(
                            sessionKey = sessionKey,
                            role = m.role.name.lowercase(),
                            content = m.content,
                            toolCallId = m.toolCallId,
                            toolName = m.toolName,
                            toolCallsJson = m.toolCallsJson,
                            createdAt = now,
                        ),
                    )
                }
            }
            touchSession(sessionKey)
        }

        override suspend fun appendUserMessage(
            sessionKey: String,
            content: String,
        ): List<ChatMessage> =
            database.withTransaction {
                ensureSession(sessionKey)
                val now = System.currentTimeMillis()
                messageDao.insert(
                    MessageEntity(
                        sessionKey = sessionKey,
                        role = ChatRole.User.name.lowercase(),
                        content = content,
                        toolCallId = null,
                        toolName = null,
                        toolCallsJson = null,
                        createdAt = now,
                    ),
                )
                touchSession(sessionKey)
                messageDao.listForSession(sessionKey).map { it.toModel() }
            }

        override suspend fun appendAssistantMessage(
            sessionKey: String,
            content: String,
            toolCallId: String?,
            toolName: String?,
            toolCalls: String?,
        ) {
            ensureSession(sessionKey)
            messageDao.insert(
                MessageEntity(
                    sessionKey = sessionKey,
                    role = ChatRole.Assistant.name.lowercase(),
                    content = content,
                    toolCallId = toolCallId,
                    toolName = toolName,
                    toolCallsJson = toolCalls,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            touchSession(sessionKey)
        }

        override suspend fun appendToolMessage(
            sessionKey: String,
            content: String,
            toolCallId: String,
            toolName: String,
        ) {
            ensureSession(sessionKey)
            messageDao.insert(
                MessageEntity(
                    sessionKey = sessionKey,
                    role = ChatRole.Tool.name.lowercase(),
                    content = content,
                    toolCallId = toolCallId,
                    toolName = toolName,
                    toolCallsJson = null,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            touchSession(sessionKey)
        }

        override suspend fun ensureSession(sessionKey: String) {
            val now = System.currentTimeMillis()
            sessionDao.insertIgnore(SessionEntity(sessionKey, now, now))
        }

        override suspend fun deleteSession(sessionKey: String) {
            sessionDao.deleteByKey(sessionKey)
        }

        private suspend fun touchSession(sessionKey: String) {
            val now = System.currentTimeMillis()
            if (sessionDao.updateTimestamp(sessionKey, now) == 0) {
                sessionDao.insertIgnore(SessionEntity(sessionKey, now, now))
            }
        }

        private fun MessageEntity.toModel(): ChatMessage {
            val r =
                when (role.lowercase()) {
                    "user" -> ChatRole.User
                    "assistant" -> ChatRole.Assistant
                    "system" -> ChatRole.System
                    "tool" -> ChatRole.Tool
                    else -> ChatRole.User
                }
            return ChatMessage(
                role = r,
                content = content,
                toolCallId = toolCallId,
                toolName = toolName,
                toolCallsJson = toolCallsJson,
            )
        }
    }
