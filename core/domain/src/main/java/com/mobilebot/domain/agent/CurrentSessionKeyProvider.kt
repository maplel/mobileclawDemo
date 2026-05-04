package com.mobilebot.domain.agent

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the session key and chat ID of the currently executing agent turn.
 * Set by [ToolCallAgentLoop] at the start of each turn so that tools
 * like SpawnSubtaskTool can link child sessions to the real parent.
 *
 * Subtask detection is derived from the session key convention:
 * child sessions created by SubtaskExecutor always start with "subtask:".
 */
@Singleton
class CurrentSessionKeyProvider
    @Inject
    constructor() {
        @Volatile
        private var currentSessionKey: String? = null

        @Volatile
        private var currentChatId: String? = null

        fun set(sessionKey: String, chatId: String) {
            currentSessionKey = sessionKey
            currentChatId = chatId
        }

        fun getSessionKey(): String = currentSessionKey ?: "default"

        fun getChatId(): String = currentChatId ?: "default"

        fun isSubtask(): Boolean = currentSessionKey?.startsWith(SUBTASK_SESSION_PREFIX) == true

        fun clear() {
            currentSessionKey = null
            currentChatId = null
        }

        companion object {
            const val SUBTASK_SESSION_PREFIX = "subtask:"
        }
    }
