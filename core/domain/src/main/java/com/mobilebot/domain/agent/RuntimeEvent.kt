package com.mobilebot.domain.agent

import com.mobilebot.domain.interaction.ActionOption
import com.mobilebot.domain.todo.TodoListSnapshot
import com.mobilebot.domain.todo.TodoStatus

sealed interface RuntimeEvent {
    data class StateChanged(val state: String) : RuntimeEvent

    data class ToolStarted(val toolName: String, val toolCallId: String) : RuntimeEvent

    data class ToolFinished(
        val toolName: String,
        val toolCallId: String,
        val success: Boolean,
        val summary: String,
        val detail: String = summary,
    ) : RuntimeEvent

    data class ApprovalRequired(
        val toolName: String,
        val toolCallId: String,
        val reason: String,
    ) : RuntimeEvent

    data class AssistantMessage(val text: String) : RuntimeEvent

    data class AssistantUpdate(val text: String) : RuntimeEvent

    data class PlanReady(val goal: String, val stepCount: Int) : RuntimeEvent

    data class Error(val message: String) : RuntimeEvent

    data class PlanPending(
        val plan: TodoListSnapshot,
        val actions: List<ActionOption>,
    ) : RuntimeEvent

    data class ActionPromptRequired(
        val promptText: String,
        val actions: List<ActionOption>,
    ) : RuntimeEvent

    data class PlanStepUpdated(
        val plan: TodoListSnapshot,
        val stepId: String,
        val status: TodoStatus,
    ) : RuntimeEvent
}
