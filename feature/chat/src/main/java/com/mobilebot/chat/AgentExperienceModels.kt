package com.mobilebot.chat

import com.mobilebot.domain.todo.TodoStatus

enum class AgentTimelineStatus {
    PENDING,
    RUNNING,
    DONE,
    BLOCKED,
    FAILED,
}

data class AgentTimelineEvent(
    val id: String,
    val title: String,
    val detail: String,
    val status: AgentTimelineStatus = AgentTimelineStatus.DONE,
)

data class AgentStageCard(
    val id: String,
    val title: String,
    val status: AgentTimelineStatus,
)

enum class AgentConversationRole {
    AGENT,
    USER,
}

data class AgentConversationItem(
    val id: String,
    val role: AgentConversationRole,
    val text: String,
)

data class AgentTaskLog(
    val id: String,
    val timeText: String? = null,
    val text: String,
)

data class AgentSystemNotification(
    val id: String,
    val title: String,
    val timeText: String,
    val body: String,
    val actionLabel: String = "OK",
)

data class AgentProgressLine(
    val label: String,
    val detail: String,
    val completed: Int = 0,
    val total: Int = 1,
)

data class DecisionPrompt(
    val text: String,
    val actions: List<ActionButton>,
)

data class AgentScenarioConfig(
    val scenarioId: String,
    val title: String,
    val skillName: String,
    val triggerText: String,
    val expectedSignals: List<String>,
)

data class AgentExperienceFrame(
    val scenario: AgentScenarioConfig,
    val statusLabel: String,
    val clockTimeText: String = "13:00",
    val clockDateText: String = "04/25/2027 Sat",
    val busy: Boolean = false,
    val hasStarted: Boolean = false,
    val conversationItems: List<AgentConversationItem> = emptyList(),
    val taskLogs: List<AgentTaskLog> = emptyList(),
    val systemNotification: AgentSystemNotification? = null,
    val progressLine: AgentProgressLine = AgentProgressLine(
        label = statusLabel,
        detail = "Ready",
    ),
    val systemSignals: List<String> = scenario.expectedSignals,
    val timeline: List<AgentTimelineEvent> = emptyList(),
    val stageCards: List<AgentStageCard> = emptyList(),
    val decisionPrompt: DecisionPrompt? = null,
    val activeActionValue: String? = null,
    val finalSummary: String? = null,
    val debugTrace: List<String> = emptyList(),
    val error: String? = null,
) {
    companion object {
        fun initial(scenario: AgentScenarioConfig): AgentExperienceFrame =
            AgentExperienceFrame(
                scenario = scenario,
                statusLabel = "Ready",
                conversationItems = emptyList(),
                progressLine = AgentProgressLine(
                    label = "Ready",
                    detail = "Waiting for confirmation",
                ),
                timeline = listOf(
                    AgentTimelineEvent(
                        id = "ready",
                        title = "Ready to coordinate",
                        detail = "The agent has the grooming context ready and can begin when started.",
                        status = AgentTimelineStatus.PENDING,
                    ),
                ),
            )
    }
}

fun TodoStatus.toAgentStatus(): AgentTimelineStatus =
    when (this) {
        TodoStatus.PENDING -> AgentTimelineStatus.PENDING
        TodoStatus.RUNNING -> AgentTimelineStatus.RUNNING
        TodoStatus.COMPLETED -> AgentTimelineStatus.DONE
        TodoStatus.FAILED -> AgentTimelineStatus.FAILED
    }
