package com.mobilebot.domain

import android.util.Log
import com.mobilebot.domain.agent.AgentInput
import com.mobilebot.domain.agent.ToolCallAgentLoop
import com.mobilebot.domain.repository.SessionRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Top-level entry point: delegates to [ToolCallAgentLoop] (tool_calls protocol).
 * Provides error fallback and per-session concurrency protection.
 */
@Singleton
class AgentLoop
    @Inject
    constructor(
        private val toolCallLoop: ToolCallAgentLoop,
        private val sessions: SessionRepository,
    ) {
        private val sessionMutexes = ConcurrentHashMap<String, Mutex>()

        suspend fun processUserMessage(
            chatId: String,
            text: String,
        ) {
            val sessionKey = "$CHANNEL:$chatId"
            // 不同任务各自串行，同一任务内保持消息顺序。
            val mutex = sessionMutexes.computeIfAbsent(sessionKey) { Mutex() }
            mutex.withLock {
                try {
                    toolCallLoop.processUserMessage(chatId = chatId, text = text)
                } catch (e: Exception) {
                    Log.e(TAG, "Agent turn failed for session=$sessionKey", e)
                    val errorMsg = "Sorry, an internal error occurred: ${e.message ?: e.javaClass.simpleName}"
                    sessions.appendAssistantMessage(sessionKey, errorMsg)
                    throw e
                }
            }
        }

        suspend fun processInput(input: AgentInput) {
            processUserMessage(
                chatId = input.chatId,
                text = input.toPromptText(),
            )
        }

        companion object {
            const val CHANNEL = "mobile"
            private const val TAG = "AgentLoop"
        }
    }
