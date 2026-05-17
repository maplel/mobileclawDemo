package com.mobilebot.chat

import com.mobilebot.domain.todo.TodoStatus
import java.time.LocalDateTime

enum class ScenarioClockMode {
    Live,
    FastUntilNextEvent,
}

data class ScenarioTimelineEvent(
    val id: String,
    val triggerAt: LocalDateTime,
    val type: String,
    val source: String,
    val title: String,
    val body: String,
)

data class AgentSystemEvent(
    val id: String,
    val timeText: String,
    val source: String,
    val title: String,
    val body: String,
)

data class AgentTaskCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val status: AgentTimelineStatus,
    val updatedTimeText: String,
    val sortKey: Long = 0L,
    val isActive: Boolean = false,
    val isPinned: Boolean = false,
)

data class AgentTaskState(
    val id: String,
    val title: String,
    val subtitle: String,
    val status: AgentTimelineStatus = AgentTimelineStatus.RUNNING,
    val updatedTimeText: String,
    val sortKey: Long = 0L,
    val conversationItems: List<AgentConversationItem> = emptyList(),
    val taskLogs: List<AgentTaskLog> = emptyList(),
    val participants: List<AgentParticipant> = emptyList(),
    val progressLine: AgentProgressLine = AgentProgressLine("进行中", "等待下一步"),
    val timeline: List<AgentTimelineEvent> = emptyList(),
    val stageCards: List<AgentStageCard> = emptyList(),
    val decisionPrompt: DecisionPrompt? = null,
    val activeActionValue: String? = null,
    val selectedAction: ActionButton? = null,
    val finalSummary: String? = null,
    val error: String? = null,
)

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

data class AgentParticipant(
    val id: String,
    val label: String,
    val displayName: String,
    val role: String,
)

data class AgentSystemNotification(
    val id: String,
    val title: String,
    val timeText: String,
    val body: String,
    val actionLabel: String = "OK",
    val callTranscriptText: String? = null,
)

data class AgentActiveCall(
    val id: String,
    val caller: String,
    val startedTimeText: String,
    val statusText: String,
    val transcriptText: String,
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
    val clockMode: ScenarioClockMode = ScenarioClockMode.Live,
    val busy: Boolean = false,
    val hasStarted: Boolean = false,
    val activeTaskId: String? = null,
    val activeTaskTitle: String = "AIOS",
    val activeTaskSubtitle: String = "正在等待系统事件",
    val taskCards: List<AgentTaskCard> = emptyList(),
    val recentSystemEvents: List<AgentSystemEvent> = emptyList(),
    val conversationItems: List<AgentConversationItem> = emptyList(),
    val taskLogs: List<AgentTaskLog> = emptyList(),
    val participants: List<AgentParticipant> = emptyList(),
    val systemNotification: AgentSystemNotification? = null,
    val activeCall: AgentActiveCall? = null,
    val progressLine: AgentProgressLine = AgentProgressLine(
        label = statusLabel,
        detail = "Ready",
    ),
    val systemSignals: List<String> = scenario.expectedSignals,
    val timeline: List<AgentTimelineEvent> = emptyList(),
    val stageCards: List<AgentStageCard> = emptyList(),
    val decisionPrompt: DecisionPrompt? = null,
    val activeActionValue: String? = null,
    val selectedAction: ActionButton? = null,
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
                        detail = "The agent is waiting for the next system signal.",
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
