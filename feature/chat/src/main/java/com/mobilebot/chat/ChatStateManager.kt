package com.mobilebot.chat

import android.util.Log
import com.mobilebot.bus.MessageBus
import com.mobilebot.data.settings.UserSettingsRepository
import com.mobilebot.domain.AgentLoop
import com.mobilebot.domain.ForegroundController
import com.mobilebot.domain.interaction.ActionPromptCodec
import com.mobilebot.domain.permissions.CapabilityApprovalGate
import com.mobilebot.domain.permissions.CapabilityApprovalRequest
import com.mobilebot.domain.permissions.CapabilityApprovalResult
import com.mobilebot.domain.repository.SessionRepository
import com.mobilebot.domain.todo.TodoListCodec
import com.mobilebot.model.ChatMessage
import com.mobilebot.model.ChatRole
import com.mobilebot.model.OutboundMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ChatSessionUi(
    val chatId: String,
    val title: String,
    val updatedAt: Long,
)

@Singleton
class ChatStateManager @Inject constructor(
    private val bus: MessageBus,
    private val agent: AgentLoop,
    private val sessions: SessionRepository,
    private val settings: UserSettingsRepository,
    private val foreground: ForegroundController,
    private val capabilityApprovalGate: CapabilityApprovalGate,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _currentChatId = MutableStateFlow("")
    val currentChatId: StateFlow<String> = _currentChatId.asStateFlow()

    private val _sessions = MutableStateFlow<List<ChatSessionUi>>(emptyList())
    val sessionsList: StateFlow<List<ChatSessionUi>> = _sessions.asStateFlow()

    private val _lines = MutableStateFlow<List<ChatLine>>(emptyList())
    val lines: StateFlow<List<ChatLine>> = _lines.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _runtimeState = MutableStateFlow("")
    val runtimeState: StateFlow<String> = _runtimeState.asStateFlow()

    private val _pendingCapabilityRequest = MutableStateFlow<CapabilityApprovalRequest?>(null)
    val pendingCapabilityRequest: StateFlow<CapabilityApprovalRequest?> = _pendingCapabilityRequest.asStateFlow()

    init {
        scope.launch {
            initFromDatabase()
        }
        
        scope.launch {
            bus.outbound.collect { msg ->
                if (msg.chatId == _currentChatId.value) {
                    handleOutboundMessage(msg)
                }
            }
        }

        // Observe capability approval gate directly
        scope.launch {
            capabilityApprovalGate.pendingRequest.collect { request ->
                _pendingCapabilityRequest.value = request
            }
        }
    }

    private fun handleOutboundMessage(msg: OutboundMessage) {
        val runtimeType = msg.metadata["_runtime"]
        
        if (runtimeType == null) {
            // Standard assistant message
            _lines.update { it + ChatLine.Assistant(msg.content) }
            return
        }

        when (runtimeType) {
            "state" -> {
                _runtimeState.value = msg.metadata["_state"] ?: ""
            }
            "tool_start" -> {
                val toolName = msg.metadata["_tool"] ?: "Tool"
                _lines.update { it + ChatLine.Progress("Starting $toolName...") }
            }
            "tool" -> {
                val toolName = msg.metadata["_tool"] ?: "Tool"
                val ok = msg.metadata["_ok"] == "1"
                val summary = msg.content
                _lines.update { it + ChatLine.Progress("${if (ok) "✓" else "✕"} $toolName: ${summary.take(50)}...") }
            }
            "todo_list" -> {
                TodoListCodec.parseJson(msg.content)?.let { snapshot ->
                    _lines.update { current ->
                        current + ChatLine.TodoList(
                            listId = snapshot.listId,
                            title = snapshot.title,
                            items = snapshot.items.map { item ->
                                TodoDisplayItem(
                                    id = item.id,
                                    text = item.text,
                                    status = TodoDisplayStatus.valueOf(item.status.name)
                                )
                            }
                        )
                    }
                }
            }
            "action_prompt" -> {
                val options = ActionPromptCodec.parseJson(msg.content)
                if (options.isNotEmpty()) {
                    _lines.update { current ->
                        current + ChatLine.ActionPrompt(
                            text = "Please choose an action:",
                            actions = options.map { opt ->
                                ActionButton(opt.label, opt.value)
                            }
                        )
                    }
                }
            }
        }
    }

    private suspend fun initFromDatabase() {
        withContext(Dispatchers.IO) {
            refreshSessions()
            val latest = _sessions.value.firstOrNull()
            if (latest != null) {
                _currentChatId.value = latest.chatId
                loadMessagesFor(latest.chatId)
            } else {
                _currentChatId.value = UUID.randomUUID().toString()
            }
        }
    }

    private suspend fun loadMessagesFor(chatId: String) {
        val msgs = sessions.getMessages(sessionKeyFor(chatId))
        _lines.value = msgs.map { msg ->
            when (msg.role) {
                ChatRole.User -> ChatLine.User(msg.content)
                ChatRole.Assistant -> ChatLine.Assistant(msg.content)
                ChatRole.System -> ChatLine.SystemNote(msg.content)
                ChatRole.Tool -> ChatLine.Progress("${msg.toolName ?: "tool"}: ${msg.content.take(80)}")
            }
        }
    }

    private suspend fun refreshSessions() {
        val metas = sessions.listSessionMetas()
        val items = metas.map { meta ->
            val chatId = chatIdFromSessionKey(meta.sessionKey)
            val firstUser = sessions.getFirstUserContent(meta.sessionKey)
            ChatSessionUi(
                chatId = chatId,
                title = firstUser?.take(24)?.ifBlank { null } ?: titleForEmptySession(meta.updatedAt),
                updatedAt = meta.updatedAt,
            )
        }
        _sessions.value = items
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _busy.value) return
        
        val chatId = _currentChatId.value
        _lines.update { it + ChatLine.User(trimmed) }
        
        scope.launch {
            _busy.value = true
            try {
                foreground.onAgentStart()
                agent.processUserMessage(chatId, trimmed)
                withContext(Dispatchers.IO) {
                    refreshSessions()
                }
            } catch (e: Exception) {
                Log.e("ChatStateManager", "Error processing message", e)
                _lines.update { it + ChatLine.SystemNote("Error: ${e.message}") }
            } finally {
                _busy.value = false
            }
        }
    }

    fun onActionSelected(prompt: ChatLine.ActionPrompt, action: ActionButton) {
        if (prompt.answered) return
        
        // Mark as answered in the UI
        _lines.update { list ->
            list.map { line ->
                if (line === prompt) {
                    (line as ChatLine.ActionPrompt).copy(answered = true, selectedAction = action.label)
                } else line
            }
        }
        
        // Send reply to agent
        send(action.value)
    }

    fun respondToCapabilityRequest(result: CapabilityApprovalResult) {
        scope.launch {
            capabilityApprovalGate.respond(result)
            _pendingCapabilityRequest.value = null
        }
    }

    fun startNewChat() {
        if (_busy.value) return
        _currentChatId.value = UUID.randomUUID().toString()
        _lines.value = emptyList()
        _runtimeState.value = ""
    }

    fun switchChat(chatId: String) {
        if (_busy.value || chatId == _currentChatId.value) return
        scope.launch {
            _currentChatId.value = chatId
            _runtimeState.value = ""
            withContext(Dispatchers.IO) {
                loadMessagesFor(chatId)
                refreshSessions()
            }
        }
    }

    fun refreshChatSessions() {
        scope.launch {
            withContext(Dispatchers.IO) {
                refreshSessions()
            }
        }
    }

    private fun sessionKeyFor(chatId: String): String =
        if (chatId.startsWith("mobile:")) chatId else "mobile:$chatId"

    private fun chatIdFromSessionKey(sessionKey: String): String =
        sessionKey.removePrefix("mobile:")

    private fun titleForEmptySession(updatedAt: Long): String {
        val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.US)
        return "New chat ${formatter.format(Date(updatedAt))}"
    }
}
