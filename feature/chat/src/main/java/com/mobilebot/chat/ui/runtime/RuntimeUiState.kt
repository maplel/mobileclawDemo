package com.mobilebot.chat.ui.runtime

sealed interface RuntimeUiState {
    data class Ambient(
        val time: String,
        val proactiveMessage: String,
        val backgroundImage: Any? = null,
    ) : RuntimeUiState

    data class Conversation(
        val messages: List<AgentMessage>,
        val isListening: Boolean = false,
        val isLiveMode: Boolean = false,
        val state: String = "",
        val busy: Boolean = false,
    ) : RuntimeUiState

    data class RunningTask(
        val taskTitle: String,
        val subAgentName: String,
        val traceSteps: List<AgentTraceStep>,
        val messages: List<AgentMessage>,
        val state: String = "",
        val busy: Boolean = false,
    ) : RuntimeUiState

    data class ToolGrid(
        val tools: List<AgentTool>,
    ) : RuntimeUiState

    data class AppConnectors(
        val apps: List<AppConnector>,
    ) : RuntimeUiState

    data class ContextResult(
        val contextTitle: String,
        val backgroundImage: Any?,
        val userQuestion: String,
        val agentAnswer: String,
    ) : RuntimeUiState
}

data class AgentMessage(
    val id: String,
    val role: MessageRole,
    val text: String,
    val timestamp: Long? = null,
    val actionButtons: List<AgentActionButton> = emptyList(),
)

enum class MessageRole {
    User,
    Agent,
    System,
}

data class AgentActionButton(
    val label: String,
    val actionId: String,
)

data class AgentTraceStep(
    val time: String,
    val title: String,
    val description: String,
    val status: TraceStatus,
)

enum class TraceStatus {
    Pending,
    Running,
    Completed,
    Blocked,
    Failed,
}

data class AgentTool(
    val name: String,
    val description: String,
    val enabled: Boolean = true,
)

data class AppConnector(
    val name: String,
    val connected: Boolean = false,
)
