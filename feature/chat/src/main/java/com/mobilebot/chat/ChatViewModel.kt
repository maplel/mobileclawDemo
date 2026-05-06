package com.mobilebot.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilebot.domain.permissions.CapabilityApprovalRequest
import com.mobilebot.domain.permissions.CapabilityApprovalResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val stateManager: ChatStateManager
) : ViewModel() {

    val currentChatId: StateFlow<String> = stateManager.currentChatId
    val sessionsList: StateFlow<List<ChatSessionUi>> = stateManager.sessionsList
    val lines: StateFlow<List<ChatLine>> = stateManager.lines
    val busy: StateFlow<Boolean> = stateManager.busy
    val runtimeState: StateFlow<String> = stateManager.runtimeState
    val pendingCapabilityRequest: StateFlow<CapabilityApprovalRequest?> = stateManager.pendingCapabilityRequest

    fun send(text: String) {
        stateManager.send(text)
    }

    fun startNewChat() {
        stateManager.startNewChat()
    }

    fun switchChat(chatId: String) {
        stateManager.switchChat(chatId)
    }

    fun refreshChatSessions() {
        stateManager.refreshChatSessions()
    }

    fun onActionSelected(prompt: ChatLine.ActionPrompt, action: ActionButton) {
        stateManager.onActionSelected(prompt, action)
    }

    fun respondToCapabilityRequest(result: CapabilityApprovalResult) {
        stateManager.respondToCapabilityRequest(result)
    }
}
