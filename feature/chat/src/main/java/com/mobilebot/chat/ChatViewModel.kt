package com.mobilebot.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilebot.bus.MessageBus
import com.mobilebot.data.settings.UserSettingsRepository
import com.mobilebot.domain.AgentLoop
import com.mobilebot.domain.ForegroundController
import com.mobilebot.domain.interaction.ActionPromptCodec
import com.mobilebot.domain.permissions.CapabilityApprovalGate
import com.mobilebot.domain.permissions.CapabilityApprovalRequest
import com.mobilebot.domain.permissions.CapabilityApprovalResult
import com.mobilebot.domain.repository.SessionRepository
import com.mobilebot.domain.subtask.SubtaskExecutor
import com.mobilebot.domain.todo.TodoListCodec
import com.mobilebot.domain.todo.TodoStatus
import com.mobilebot.model.ChatMessage
import com.mobilebot.model.ChatRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class ChatSessionInfo(
    val chatId: String,
    val preview: String,
    val updatedAt: Long,
)

@HiltViewModel
class ChatViewModel
    @Inject
    constructor(
        private val bus: MessageBus,
        private val agent: AgentLoop,
        private val sessions: SessionRepository,
        private val settings: UserSettingsRepository,
        private val foreground: ForegroundController,
        private val capabilityApprovalGate: CapabilityApprovalGate,
    ) : ViewModel() {
        private val _currentChatId = MutableStateFlow(generateChatId())
        val currentChatId: StateFlow<String> = _currentChatId.asStateFlow()

        private val _lines = MutableStateFlow<List<ChatLine>>(emptyList())
        val lines: StateFlow<List<ChatLine>> = _lines.asStateFlow()

        private val _busy = MutableStateFlow(false)
        val busy: StateFlow<Boolean> = _busy.asStateFlow()

        private val _chatSessions = MutableStateFlow<List<ChatSessionInfo>>(emptyList())
        val chatSessions: StateFlow<List<ChatSessionInfo>> = _chatSessions.asStateFlow()

        val pendingCapabilityRequest: StateFlow<CapabilityApprovalRequest?> =
            capabilityApprovalGate.pendingRequest

        fun respondToCapabilityRequest(result: CapabilityApprovalResult) {
            capabilityApprovalGate.respond(result)
        }

        init {
            viewModelScope.launch {
                initFromDatabase()
                bus.outbound.collect { msg ->
                    if (msg.channel != AgentLoop.CHANNEL) return@collect
                    if (msg.chatId != _currentChatId.value) return@collect
                    if (msg.metadata["_progress"] == "1") return@collect

                    val subtaskId = msg.metadata["_subtask_id"]
                    if (subtaskId != null) {
                        handleSubtaskEvent(subtaskId, msg.metadata["_runtime"].orEmpty(), msg.content)
                        return@collect
                    }

                    when (msg.metadata["_runtime"]) {
                        "state" -> {
                            val st = msg.metadata["_state"].orEmpty()
                            if (st.isNotBlank()) {
                                _lines.update { it + ChatLine.Progress(st) }
                            }
                        }
                        "tool_start" -> {
                            val t = msg.metadata["_tool"].orEmpty()
                            if (t in SUBTASK_TOOLS) return@collect
                            if (t.isNotBlank()) {
                                _lines.update { it + ChatLine.Progress("Running: $t") }
                            }
                        }
                        "tool" -> {
                            val t = msg.metadata["_tool"].orEmpty()
                            if (t in SUBTASK_TOOLS) return@collect
                            val ok = msg.metadata["_ok"] == "1"
                            val hint = if (ok) "Done" else "Failed"
                            if (msg.content.isNotBlank() || t.isNotBlank()) {
                                _lines.update {
                                    it + ChatLine.Progress("$hint: $t — ${msg.content.take(120)}")
                                }
                            }
                        }
                        "plan" -> {
                            if (msg.content.isNotBlank()) {
                                _lines.update { it + ChatLine.Progress(msg.content) }
                            }
                        }
                        "approval" -> {
                            if (msg.content.isNotBlank()) {
                                _lines.update { it + ChatLine.Progress(msg.content) }
                            }
                        }
                        "action_prompt" -> {
                            val actionsJson = msg.metadata["_actions"].orEmpty()
                            val buttons = parseActionButtons(actionsJson, msg.content)
                            if (buttons.isNotEmpty()) {
                                _lines.update {
                                    it + ChatLine.ActionPrompt(
                                        text = msg.content,
                                        actions = buttons,
                                    )
                                }
                            }
                        }
                        "todo_list" -> {
                            val payload = msg.metadata["_todo_payload"].orEmpty()
                            val snapshot = TodoListCodec.parseJson(payload)
                            if (snapshot != null) {
                                upsertTodoList(
                                    ChatLine.TodoList(
                                        listId = snapshot.listId,
                                        title = snapshot.title,
                                        items = snapshot.items.map { it.toDisplay() },
                                    ),
                                )
                            }
                        }
                        "error" -> {
                            if (msg.content.isNotBlank()) {
                                _lines.update { it + ChatLine.Assistant(msg.content) }
                            }
                        }
                        else -> {
                            if (msg.content.isBlank()) return@collect
                            _lines.update { it + ChatLine.Assistant(msg.content) }
                        }
                    }
                }
            }
        }

        private fun handleSubtaskEvent(taskId: String, type: String, content: String) {
            Log.d(TAG, "subtask event: task=$taskId type=$type content=${content.take(60)}")
            when (type) {
                "subtask_spawned" -> {
                    Log.d(TAG, "Spawning panel for $taskId, current panels=${
                        (_lines.value.firstOrNull { it is ChatLine.SubtaskGroup } as? ChatLine.SubtaskGroup)?.panels?.keys ?: "none"
                    }")
                    val panel = ChatLine.SubtaskPanel(
                        taskId = taskId,
                        label = taskId,
                        status = SubtaskDisplayStatus.SPAWNED,
                        entries = listOf(content),
                    )
                    upsertSubtaskGroup { it + (taskId to panel) }
                    Log.d(TAG, "After spawn: panels=${
                        (_lines.value.firstOrNull { it is ChatLine.SubtaskGroup } as? ChatLine.SubtaskGroup)?.panels?.keys
                    }")
                }
                "subtask_running" -> mutatePanel(taskId) { it.copy(status = SubtaskDisplayStatus.RUNNING) }
                "subtask_tool_start" -> mutatePanel(taskId) { it.copy(entries = it.entries + "▶ $content") }
                "subtask_tool_done" -> mutatePanel(taskId) { it.copy(entries = it.entries + "  $content") }
                "subtask_message" -> mutatePanel(taskId) { it.copy(entries = it.entries + content) }
                "subtask_plan" -> mutatePanel(taskId) { it.copy(entries = it.entries + content) }
                "subtask_state" -> { /* noisy, filtered */ }
                "subtask_completed" -> mutatePanel(taskId) { it.copy(status = SubtaskDisplayStatus.COMPLETED, entries = it.entries + content) }
                "subtask_failed", "subtask_error" -> mutatePanel(taskId) { it.copy(status = SubtaskDisplayStatus.FAILED, entries = it.entries + content) }
            }
        }

        private fun mutatePanel(taskId: String, transform: (ChatLine.SubtaskPanel) -> ChatLine.SubtaskPanel) {
            upsertSubtaskGroup { panels ->
                val existing = panels[taskId]
                    ?: ChatLine.SubtaskPanel(taskId = taskId, label = taskId, status = SubtaskDisplayStatus.RUNNING, entries = emptyList())
                panels + (taskId to transform(existing))
            }
        }

        private fun upsertSubtaskGroup(updatePanels: (Map<String, ChatLine.SubtaskPanel>) -> Map<String, ChatLine.SubtaskPanel>) {
            _lines.update { list ->
                val idx = list.indexOfFirst { it is ChatLine.SubtaskGroup }
                if (idx >= 0) {
                    val old = list[idx] as ChatLine.SubtaskGroup
                    list.toMutableList().apply { set(idx, old.copy(panels = updatePanels(old.panels))) }
                } else {
                    list + ChatLine.SubtaskGroup(panels = updatePanels(emptyMap()))
                }
            }
        }

        private fun upsertTodoList(todo: ChatLine.TodoList) {
            _lines.update { list ->
                val idx = list.indexOfFirst { it is ChatLine.TodoList && it.listId == todo.listId }
                if (idx >= 0) {
                    list.toMutableList().apply { set(idx, todo) }
                } else {
                    list + todo
                }
            }
        }

        private suspend fun initFromDatabase() {
            withContext(Dispatchers.IO) {
                runCatching {
                    refreshSessions()
                    val latest = _chatSessions.value.firstOrNull()
                    if (latest != null) {
                        _currentChatId.value = latest.chatId
                        loadMessagesFor(latest.chatId)
                    }
                }.onFailure { Log.e(TAG, "Failed to restore chat sessions", it) }
            }
        }

        private fun sessionKey(chatId: String = _currentChatId.value) =
            "${AgentLoop.CHANNEL}:$chatId"

        fun newChat() {
            _currentChatId.value = generateChatId()
            _lines.value = emptyList()
        }

        fun switchChat(chatId: String) {
            if (chatId == _currentChatId.value) return
            _currentChatId.value = chatId
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    loadMessagesFor(chatId)
                }
            }
        }

        fun deleteChat(chatId: String) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    sessions.deleteSession(sessionKey(chatId))
                    refreshSessions()
                }
                if (chatId == _currentChatId.value) {
                    val next = _chatSessions.value.firstOrNull()
                    if (next != null) {
                        switchChat(next.chatId)
                    } else {
                        newChat()
                    }
                }
            }
        }

        private suspend fun loadMessagesFor(chatId: String) {
            runCatching {
                val stored = sessions.getMessages(sessionKey(chatId))
                val rebuilt = rebuildUiLines(stored)
                _lines.value = rebuilt
                Log.d(TAG, "Loaded ${stored.size} rows -> ${rebuilt.size} UI lines for chat=$chatId")
            }.onFailure { Log.e(TAG, "Failed to load chat $chatId", it) }
        }

        private fun rebuildUiLines(stored: List<ChatMessage>): List<ChatLine> {
            val out = mutableListOf<ChatLine>()
            stored.forEachIndexed { index, msg ->
                out += msg.toUiLine(stored, index) ?: return@forEachIndexed
            }
            return out
        }

        private suspend fun refreshSessions() {
            val metas = sessions.listSessionMetas()
            val infos =
                metas
                    .filter { meta ->
                        meta.sessionKey.startsWith("${AgentLoop.CHANNEL}:") &&
                            !meta.sessionKey.contains(":subtask-") &&
                            !meta.sessionKey.contains(":event:")
                    }.map { meta ->
                        val chatId = meta.sessionKey.removePrefix("${AgentLoop.CHANNEL}:")
                        val preview =
                            sessions.getFirstUserContent(meta.sessionKey)?.take(50) ?: "New Chat"
                        ChatSessionInfo(chatId, preview, meta.updatedAt)
                    }
            _chatSessions.value = infos
        }

        private fun ChatMessage.toUiLine(
            allMessages: List<ChatMessage>,
            index: Int,
        ): ChatLine? =
            when (role) {
                ChatRole.User -> {
                    if (content.startsWith(SubtaskExecutor.FOLLOW_UP_PREFIX)) {
                        ChatLine.SystemNote(content)
                    } else {
                        ChatLine.User(content)
                    }
                }
                ChatRole.Assistant ->
                    when {
                        toolName == TodoListCodec.MESSAGE_TOOL_NAME -> {
                            val snapshot = TodoListCodec.parseJson(toolCallsJson)
                            if (snapshot != null) {
                                ChatLine.TodoList(
                                    listId = snapshot.listId,
                                    title = snapshot.title,
                                    items = snapshot.items.map { it.toDisplay() },
                                )
                            } else if (content.isNotBlank()) {
                                ChatLine.Assistant(content)
                            } else {
                                null
                            }
                        }
                        toolName == ActionPromptCodec.MESSAGE_TOOL_NAME -> {
                            val buttons = parseActionButtons(toolCallsJson, content)
                            if (buttons.isNotEmpty()) {
                                val nextUserReply =
                                    allMessages.getOrNull(index + 1)
                                        ?.takeIf { it.role == ChatRole.User }
                                        ?.content
                                        ?.trim()
                                        .orEmpty()
                                val matchedSelection =
                                    buttons.firstOrNull {
                                        it.label.equals(nextUserReply, ignoreCase = true) ||
                                            it.value.equals(nextUserReply, ignoreCase = true)
                                    }?.label
                                ChatLine.ActionPrompt(
                                    text = content,
                                    actions = buttons,
                                    answered = nextUserReply.isNotEmpty(),
                                    selectedAction = matchedSelection ?: nextUserReply.ifBlank { null },
                                )
                            } else if (content.isNotBlank()) {
                                ChatLine.Assistant(content)
                            } else {
                                null
                            }
                        }
                        content.isNotBlank() -> ChatLine.Assistant(content)
                        !toolCallsJson.isNullOrBlank() ->
                            ChatLine.Progress("(assistant requested tools)")
                        else -> null
                    }
                ChatRole.Tool -> {
                    val label = toolName?.trim()?.takeIf { it.isNotEmpty() } ?: "tool"
                    ChatLine.Progress("Tool $label: ${content.take(120)}")
                }
                ChatRole.System -> null
            }

        private fun com.mobilebot.domain.todo.TodoItemSnapshot.toDisplay(): TodoDisplayItem =
            TodoDisplayItem(
                id = id,
                text = text,
                status =
                    when (status) {
                        TodoStatus.RUNNING -> TodoDisplayStatus.RUNNING
                        TodoStatus.COMPLETED -> TodoDisplayStatus.COMPLETED
                        TodoStatus.FAILED -> TodoDisplayStatus.FAILED
                        TodoStatus.PENDING -> TodoDisplayStatus.PENDING
                    },
            )

        fun onActionSelected(prompt: ChatLine.ActionPrompt, action: ActionButton) {
            _lines.update { list ->
                list.map {
                    if (it === prompt) prompt.copy(answered = true, selectedAction = action.label)
                    else it
                }
            }
            send(action.value)
        }

        private fun parseActionButtons(json: String?, promptText: String): List<ActionButton> {
            val explicit =
                try {
                    ActionPromptCodec.parseJson(json).map {
                        ActionButton(
                            label = it.label,
                            value = it.value,
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse action buttons", e)
                    emptyList()
                }
            if (explicit.isNotEmpty()) return explicit
            return ActionPromptCodec.resolveOptions(promptText).map {
                ActionButton(
                    label = it.label,
                    value = it.value,
                )
            }
        }

        fun send(text: String) {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return
            val chatId = _currentChatId.value
            viewModelScope.launch {
                _busy.value = true
                _lines.update { it + ChatLine.User(trimmed) }
                try {
                    if (settings.getApiKey().isBlank()) {
                        _lines.update {
                            it + ChatLine.Assistant("Add an API key in Settings before chatting.")
                        }
                        return@launch
                    }
                    foreground.onAgentStart()
                    try {
                        withContext(Dispatchers.IO) {
                            agent.processUserMessage(chatId, trimmed)
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Agent failed", e)
                        _lines.update {
                            it + ChatLine.Assistant("Error: ${e.message ?: e.javaClass.simpleName}")
                        }
                    } finally {
                        foreground.onAgentStop()
                    }
                } finally {
                    _busy.value = false
                    withContext(Dispatchers.IO) { refreshSessions() }
                }
            }
        }

        private companion object {
            private const val TAG = "ChatViewModel"
            private val SUBTASK_TOOLS = setOf("spawn_subtask", "check_subtask", "publish_fact")

            private fun generateChatId(): String = "chat-${UUID.randomUUID()}"
        }
    }
