package com.mobilebot.domain.agent

import com.mobilebot.domain.todo.PlanTodo
import com.mobilebot.domain.todo.TodoListCodec
import com.mobilebot.domain.todo.TodoListSnapshot
import com.mobilebot.domain.todo.TodoStatus
import com.mobilebot.network.LlmMessage
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class PlanState { NONE, PENDING, EXECUTING, DONE }

data class PendingPlan(
    val chatId: String,
    val snapshot: TodoListSnapshot,
    val savedMessages: List<LlmMessage>,
    val state: PlanState = PlanState.PENDING,
)

/**
 * Per-chat plan state machine. Tracks a plan through
 * NONE -> PENDING -> EXECUTING -> DONE and stores the
 * [TodoListSnapshot] and LLM message history for seamless
 * re-entry after user approval.
 */
@Singleton
class PlanManager @Inject constructor() {

    private val plans = ConcurrentHashMap<String, PendingPlan>()

    fun storePlan(
        chatId: String,
        title: String,
        steps: List<PlanTodo>,
        messages: List<LlmMessage>,
    ): TodoListSnapshot {
        val snapshot = TodoListCodec.fromPlan(
            listId = "plan_$chatId",
            title = title,
            todos = steps,
        )
        plans[chatId] = PendingPlan(
            chatId = chatId,
            snapshot = snapshot,
            savedMessages = messages.toList(),
            state = PlanState.PENDING,
        )
        return snapshot
    }

    fun getPending(chatId: String): PendingPlan? =
        plans[chatId]?.takeIf { it.state == PlanState.PENDING }

    fun isExecuting(chatId: String): Boolean =
        plans[chatId]?.state == PlanState.EXECUTING

    fun approve(chatId: String) {
        plans.computeIfPresent(chatId) { _, plan ->
            val first = plan.snapshot.items.firstOrNull()
            val updatedSnapshot = if (first != null) {
                TodoListCodec.updateStatus(plan.snapshot, first.id, TodoStatus.RUNNING)
            } else {
                plan.snapshot
            }
            plan.copy(state = PlanState.EXECUTING, snapshot = updatedSnapshot)
        }
    }

    fun reject(chatId: String) {
        plans.remove(chatId)
    }

    fun currentSnapshot(chatId: String): TodoListSnapshot? =
        plans[chatId]?.snapshot

    fun savedMessages(chatId: String): List<LlmMessage> =
        plans[chatId]?.savedMessages ?: emptyList()

    fun currentStepId(chatId: String): String? {
        val plan = plans[chatId] ?: return null
        return plan.snapshot.items.firstOrNull { it.status == TodoStatus.RUNNING }?.id
            ?: plan.snapshot.items.firstOrNull { it.status == TodoStatus.PENDING }?.id
    }

    fun updateStepStatus(chatId: String, stepId: String, status: TodoStatus) {
        plans.computeIfPresent(chatId) { _, plan ->
            val updated = TodoListCodec.updateStatus(plan.snapshot, stepId, status)
            val allDone = TodoListCodec.allDone(updated)
            plan.copy(
                snapshot = updated,
                state = if (allDone) PlanState.DONE else plan.state,
            )
        }
    }

    fun advanceToNextStep(chatId: String) {
        plans.computeIfPresent(chatId) { _, plan ->
            val nextPending = plan.snapshot.items.firstOrNull { it.status == TodoStatus.PENDING }
            if (nextPending != null) {
                val updated = TodoListCodec.updateStatus(plan.snapshot, nextPending.id, TodoStatus.RUNNING)
                plan.copy(snapshot = updated)
            } else {
                plan
            }
        }
    }

    fun state(chatId: String): PlanState =
        plans[chatId]?.state ?: PlanState.NONE
}
