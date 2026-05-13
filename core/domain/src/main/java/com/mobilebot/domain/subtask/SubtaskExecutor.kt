package com.mobilebot.domain.subtask

import android.util.Log
import com.mobilebot.bus.MessageBus
import com.mobilebot.domain.AgentLoop
import com.mobilebot.domain.ForegroundController
import com.mobilebot.domain.agent.CurrentSessionKeyProvider
import com.mobilebot.domain.agent.RuntimeEvent
import com.mobilebot.domain.agent.ToolCallAgentLoop
import com.mobilebot.domain.memory.MemoryFacade
import com.mobilebot.domain.repository.SessionRepository
import com.mobilebot.model.OutboundMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

enum class SubtaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
}

data class SubtaskState(
    val taskId: String,
    val instruction: String,
    val parentSessionKey: String,
    val parentChatId: String = "",
    val status: SubtaskStatus = SubtaskStatus.PENDING,
    val result: String? = null,
    val error: String? = null,
)

@Singleton
class SubtaskExecutor
    @Inject
    constructor(
        private val toolCallLoopProvider: Provider<ToolCallAgentLoop>,
        private val agentLoopProvider: Provider<AgentLoop>,
        private val sessions: SessionRepository,
        private val memory: MemoryFacade,
        private val bus: MessageBus,
        private val sessionKeyProvider: CurrentSessionKeyProvider,
        private val foreground: ForegroundController,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val tasks = ConcurrentHashMap<String, SubtaskState>()
        private val sharedFacts = ConcurrentHashMap<String, String>()
        private val followUpGuard = ConcurrentHashMap<String, AtomicBoolean>()

        fun spawn(
            taskId: String,
            instruction: String,
            parentSessionKey: String,
            parentChatId: String = "",
        ) {
            val state = SubtaskState(
                taskId = taskId,
                instruction = instruction,
                parentSessionKey = parentSessionKey,
                parentChatId = parentChatId,
                status = SubtaskStatus.PENDING,
            )
            tasks[taskId] = state
            followUpGuard.getOrPut(parentSessionKey) { AtomicBoolean(false) }.set(false)

            emitSubtaskEvent(parentChatId, taskId, "subtask_spawned", instruction.take(120))

            foreground.onAgentStart()
            scope.launch {
                tasks[taskId] = state.copy(status = SubtaskStatus.RUNNING)
                emitSubtaskEvent(parentChatId, taskId, "subtask_running", "Starting...")
                try {
                    val childChatId = "subtask-$taskId-${UUID.randomUUID().toString().take(8)}"
                    val childSession = "${ToolCallAgentLoop.CHANNEL}:$childChatId"
                    sessions.ensureSession(childSession)
                    toolCallLoopProvider.get().processUserMessage(
                        chatId = childChatId,
                        text = instruction,
                        emit = { event -> relayChildEvent(parentChatId, taskId, event) },
                    )
                    val messages = sessions.getMessages(childSession)
                    val lastAssistant = messages
                        .lastOrNull { it.role == com.mobilebot.model.ChatRole.Assistant }
                    val result = lastAssistant?.content
                        ?.take(MAX_RESULT_LENGTH)
                        ?.takeIf { it.isNotBlank() }
                        ?: "Subtask completed (no output)"
                    tasks[taskId] = tasks[taskId]!!.copy(
                        status = SubtaskStatus.COMPLETED,
                        result = result,
                    )
                    emitSubtaskEvent(parentChatId, taskId, "subtask_completed", result.take(200))
                } catch (e: Exception) {
                    val error = e.message ?: "unknown error"
                    tasks[taskId] = tasks[taskId]!!.copy(
                        status = SubtaskStatus.FAILED,
                        error = error,
                    )
                    emitSubtaskEvent(parentChatId, taskId, "subtask_failed", error)
                } finally {
                    foreground.onAgentStop()
                }
                checkAndMaybeFollowUp(parentSessionKey, parentChatId)
            }
        }

        fun getState(taskId: String): SubtaskState? = tasks[taskId]

        fun getAllStates(): Map<String, SubtaskState> = tasks.toMap()

        fun getSharedFact(key: String): String? = sharedFacts[key]

        fun publishFact(key: String, value: String) {
            sharedFacts[key] = value
        }

        fun allSharedFacts(): Map<String, String> = sharedFacts.toMap()

        private suspend fun relayChildEvent(parentChatId: String, taskId: String, event: RuntimeEvent) {
            if (parentChatId.isBlank()) return
            val (type, content) = when (event) {
                is RuntimeEvent.ToolStarted -> "subtask_tool_start" to event.toolName
                is RuntimeEvent.ToolFinished -> {
                    val label = if (event.success) "Done" else "Failed"
                    "subtask_tool_done" to "$label: ${event.toolName} — ${event.summary.take(100)}"
                }
                is RuntimeEvent.AssistantMessage -> "subtask_message" to event.text.take(200)
                is RuntimeEvent.AssistantUpdate -> "subtask_message" to event.text.take(200)
                is RuntimeEvent.StateChanged -> "subtask_state" to event.state
                is RuntimeEvent.Error -> "subtask_error" to event.message
                is RuntimeEvent.PlanReady -> "subtask_plan" to "Plan: ${event.goal} (${event.stepCount} steps)"
                is RuntimeEvent.ApprovalRequired -> return
                is RuntimeEvent.PlanPending -> return
                is RuntimeEvent.ActionPromptRequired -> return
                is RuntimeEvent.PlanStepUpdated -> "subtask_plan_step" to "Step ${event.stepId}: ${event.status}"
            }
            emitSubtaskEvent(parentChatId, taskId, type, content)
        }

        private fun emitSubtaskEvent(parentChatId: String, taskId: String, type: String, content: String) {
            if (parentChatId.isBlank()) return
            bus.tryPublishOutbound(
                OutboundMessage(
                    channel = AgentLoop.CHANNEL,
                    chatId = parentChatId,
                    content = content,
                    metadata = mapOf(
                        "_runtime" to type,
                        "_subtask_id" to taskId,
                    ),
                ),
            )
        }

        private fun checkAndMaybeFollowUp(parentSessionKey: String, parentChatId: String) {
            if (parentChatId.isBlank() || parentSessionKey.isBlank()) return
            val allForParent = tasks.values.filter { it.parentSessionKey == parentSessionKey }
            val anyPending = allForParent.any {
                it.status == SubtaskStatus.RUNNING || it.status == SubtaskStatus.PENDING
            }
            if (anyPending || allForParent.isEmpty()) return

            val guard = followUpGuard.getOrPut(parentSessionKey) { AtomicBoolean(false) }
            if (!guard.compareAndSet(false, true)) return

            val completedIds = allForParent
                .filter { it.status == SubtaskStatus.COMPLETED }
                .map { it.taskId }
            val failedIds = allForParent
                .filter { it.status == SubtaskStatus.FAILED }
                .map { it.taskId }

            Log.i(TAG, "All subtasks done for $parentSessionKey: completed=$completedIds, failed=$failedIds")

            scope.launch {
                delay(FOLLOW_UP_DELAY_MS)
                try {
                    val summary = buildString {
                        append(FOLLOW_UP_PREFIX)
                        if (completedIds.isNotEmpty()) append("完成: ${completedIds.joinToString(", ")}。")
                        if (failedIds.isNotEmpty()) append("失败: ${failedIds.joinToString(", ")}。")
                        append("请使用 check_subtask 查看各子任务结果和共享事实，然后根据技能指导继续下一阶段编排。")
                    }
                    agentLoopProvider.get().processUserMessage(parentChatId, summary)
                } catch (e: Exception) {
                    Log.e(TAG, "Follow-up turn failed for $parentSessionKey", e)
                }
            }
        }

        companion object {
            private const val TAG = "SubtaskExecutor"
            private const val MAX_RESULT_LENGTH = 2000
            private const val FOLLOW_UP_DELAY_MS = 800L
            const val FOLLOW_UP_PREFIX = "[系统自动] 子任务批次已完成。"
        }
    }
