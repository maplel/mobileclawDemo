package com.mobilebot.domain.agent

import android.util.Log
import com.mobilebot.domain.LlmConfigurator
import com.mobilebot.domain.SkillsLoader
import com.mobilebot.domain.interaction.ActionOption
import com.mobilebot.domain.memory.MemoryDigestBuilder
import com.mobilebot.domain.memory.MemoryType
import com.mobilebot.domain.memory.WorkspaceContextManager
import com.mobilebot.domain.memory.PersistentMemoryManager
import com.mobilebot.domain.permissions.AgentCapability
import com.mobilebot.domain.permissions.AgentCapabilityStore
import com.mobilebot.domain.permissions.AgentPermissionCoordinator
import com.mobilebot.domain.permissions.CapabilityApprovalGate
import com.mobilebot.domain.permissions.CapabilityApprovalRequest
import com.mobilebot.domain.permissions.CapabilityApprovalResult
import com.mobilebot.domain.repository.MemoryFileRepository
import com.mobilebot.domain.repository.SessionRepository
import com.mobilebot.domain.skill.SkillRegistry
import com.mobilebot.domain.skill.SkillSnapshot
import com.mobilebot.domain.todo.TodoListCodec
import com.mobilebot.domain.todo.TodoStatus
import com.mobilebot.domain.tools.CreatePlanTool
import com.mobilebot.domain.tools.ToolPermissionGate
import com.mobilebot.domain.tools.ToolRegistry
import com.mobilebot.model.ChatMessage
import com.mobilebot.model.ChatRole
import com.mobilebot.model.OutboundMessage
import com.mobilebot.network.LlmClient
import com.mobilebot.network.LlmMessage
import com.mobilebot.network.LlmToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main agent loop using standard OpenAI tool_calls protocol.
 *
 *   1. Build system prompt (with skill catalog)
 *   2. Send messages + tool definitions to LLM
 *   3. If LLM returns tool_calls, execute them and loop
 *   4. If LLM returns text, emit as assistant message
 */
@Singleton
class ToolCallAgentLoop @Inject constructor(
    private val llm: LlmClient,
    private val toolRegistry: ToolRegistry,
    private val skillRegistry: SkillRegistry,
    private val toolGate: ToolPermissionGate,
    private val sessions: SessionRepository,
    private val memoryFiles: MemoryFileRepository,
    private val persistentMemory: PersistentMemoryManager,
    private val skillsLoader: SkillsLoader,
    private val permissionCoordinator: AgentPermissionCoordinator,
    private val capabilityStore: AgentCapabilityStore,
    private val capabilityApprovalGate: CapabilityApprovalGate,
    private val bus: com.mobilebot.bus.MessageBus,
    private val llmConfigurator: LlmConfigurator,
    private val sessionKeyProvider: CurrentSessionKeyProvider,
    private val planManager: PlanManager,
    private val workspaceContext: WorkspaceContextManager,
) {

    @Volatile
    private var skillSnapshot: SkillSnapshot? = null

    suspend fun processUserMessage(
        chatId: String,
        text: String,
        emit: suspend (RuntimeEvent) -> Unit = { publishToBus(chatId, it) },
    ) {
        val sessionKey = "$CHANNEL:$chatId"
        sessionKeyProvider.set(sessionKey, chatId)
        llmConfigurator.beforeRequest()

        // --- A. Entry check: detect pending plan ---
        val pending = planManager.getPending(chatId)
        if (pending != null) {
            handlePlanResponse(chatId, sessionKey, text, pending, emit)
            return
        }

        val history = sessions.appendUserMessage(sessionKey, text)
        memoryFiles.appendHistoryLine("user: $text")

        emit(RuntimeEvent.StateChanged("THINKING"))

        val snapshot = ensureSkillSnapshot()
        val memoryDigest = buildMemoryDigest(sessionKey)
        val persistentMemories = buildPersistentMemoryDigest(text)
        val activePrompt = skillsLoader.activePrompt()

        val systemPrompt = SystemPromptBuilder.build(
            skillRegistry = skillRegistry,
            memoryDigest = memoryDigest,
            activePrompt = activePrompt,
            persistentMemories = persistentMemories,
        )

        val toolDefs = toolRegistry.definitionsForLlm()

        val messages = mutableListOf<LlmMessage>()
        messages.add(LlmMessage(role = "system", content = systemPrompt))

        for (msg in history.takeLast(MAX_HISTORY)) {
            messages.add(msg.toLlmMessage())
        }
        if (messages.none { it.role == "user" && it.content == text }) {
            messages.add(LlmMessage(role = "user", content = text))
        }

        workspaceContext.onNewUserRequest(text)
        runAgentLoop(chatId, sessionKey, messages, toolDefs, emit)
    }

    private suspend fun runAgentLoop(
        chatId: String,
        sessionKey: String,
        messages: MutableList<LlmMessage>,
        toolDefs: List<com.mobilebot.model.ToolDefinition>,
        emit: suspend (RuntimeEvent) -> Unit,
    ) {
        var turnsRemaining = MAX_TURNS
        while (turnsRemaining-- > 0) {
            val response = llm.chat(
                messages = messages,
                tools = toolDefs,
                model = null,
                maxTokens = MAX_TOKENS,
            )

            if (response.toolCalls.isEmpty()) {
                val reply = response.content?.trim()
                    ?: "I'm not sure how to help with that."
                sessions.appendAssistantMessage(sessionKey, reply)
                memoryFiles.appendHistoryLine("assistant: $reply")
                workspaceContext.onAgentResponse(reply)

                // --- C. Plan step tracking on text response ---
                if (planManager.isExecuting(chatId)) {
                    val stepId = planManager.currentStepId(chatId)
                    if (stepId != null) {
                        val stepText = planManager.currentSnapshot(chatId)
                            ?.items?.firstOrNull { it.id == stepId }?.text ?: stepId
                        planManager.updateStepStatus(chatId, stepId, TodoStatus.COMPLETED)
                        planManager.advanceToNextStep(chatId)
                        workspaceContext.onPlanStepCompleted(
                            planSnapshot = planManager.currentSnapshot(chatId),
                            stepText = stepText,
                        )
                        emitPlanProgress(chatId, stepId, emit)
                    }
                }

                emit(RuntimeEvent.StateChanged("RESPONDING"))
                emit(RuntimeEvent.AssistantMessage(reply))
                return
            }

            val assistantMsg = LlmMessage(
                role = "assistant",
                content = response.content.orEmpty(),
                toolCalls = response.toolCalls,
            )
            messages.add(assistantMsg)
            sessions.appendAssistantMessage(
                sessionKey = sessionKey,
                content = assistantMsg.content,
                toolCalls = assistantMsg.toolCalls?.let { serializeToolCalls(it) }
            )

            for (tc in response.toolCalls) {
                emit(RuntimeEvent.ToolStarted(tc.name, tc.id))
                emit(RuntimeEvent.StateChanged("EXECUTING_TOOL"))

                val tool = toolRegistry.get(tc.name)
                if (tool == null) {
                    val error = "Error: unknown tool '${tc.name}'"
                    messages.add(LlmMessage(role = "tool", content = error, toolCallId = tc.id, name = tc.name))
                    sessions.appendToolMessage(sessionKey, error, tc.id, tc.name)
                    emit(RuntimeEvent.ToolFinished(tc.name, tc.id, success = false, summary = error))
                    continue
                }

                val ungrantedCaps = tool.requiredCapabilities.filter { !capabilityStore.isGranted(it) }
                if (ungrantedCaps.isNotEmpty()) {
                    val capNames = AgentCapability.displayNamesFor(ungrantedCaps.toSet())
                        .ifEmpty { ungrantedCaps.toList() }
                    val choice = capabilityApprovalGate.requestApproval(
                        CapabilityApprovalRequest(capNames, tc.name),
                    )
                    when (choice) {
                        CapabilityApprovalResult.ALWAYS,
                        CapabilityApprovalResult.WHILE_USING_APP ->
                            ungrantedCaps.forEach { capabilityStore.grant(it) }
                        CapabilityApprovalResult.ASK_EVERY_TIME -> Unit
                        CapabilityApprovalResult.DENY -> {
                            val denied = "User denied permission for: ${capNames.joinToString(", ")}."
                            messages.add(LlmMessage(role = "tool", content = denied, toolCallId = tc.id, name = tc.name))
                            sessions.appendToolMessage(sessionKey, denied, tc.id, tc.name)
                            emit(RuntimeEvent.ToolFinished(tc.name, tc.id, success = false, summary = denied))
                            continue
                        }
                    }
                }

                if (!permissionCoordinator.ensureRuntimePermissionsForCapabilities(tool.requiredCapabilities)) {
                    val denied = "Required Android permission was not granted for ${tc.name}."
                    messages.add(LlmMessage(role = "tool", content = denied, toolCallId = tc.id, name = tc.name))
                    sessions.appendToolMessage(sessionKey, denied, tc.id, tc.name)
                    emit(RuntimeEvent.ToolFinished(tc.name, tc.id, success = false, summary = denied))
                    continue
                }

                if (!toolGate.ensure(sessionKey, tool, tc.argumentsJson)) {
                    val denied = "User denied tool execution."
                    messages.add(LlmMessage(role = "tool", content = denied, toolCallId = tc.id, name = tc.name))
                    sessions.appendToolMessage(sessionKey, denied, tc.id, tc.name)
                    emit(RuntimeEvent.ToolFinished(tc.name, tc.id, success = false, summary = denied))
                    continue
                }

                val result = toolRegistry.execute(tc.name, tc.argumentsJson)
                val body = when {
                    !result.ok -> "Error: ${result.message}"
                    !result.dataJson.isNullOrBlank() -> result.message + "\n" + result.dataJson
                    else -> result.message
                }

                messages.add(LlmMessage(role = "tool", content = body, toolCallId = tc.id, name = tc.name))
                sessions.appendToolMessage(sessionKey, body, tc.id, tc.name)
                emit(RuntimeEvent.ToolFinished(tc.name, tc.id, success = result.ok, summary = body.take(200)))

                Log.d(TAG, "Tool ${tc.name} -> ${if (result.ok) "OK" else "FAIL"}: ${body.take(200)}")
                workspaceContext.onToolResult(tc.name, result.message)

                // --- B. Post-tool-call check: break on create_plan ---
                if (tc.name == CreatePlanTool.NAME && result.ok) {
                    val plan = planManager.getPending(chatId)
                    if (plan != null) {
                        // Save message history for seamless re-entry after approval
                        planManager.storePlan(
                            chatId = chatId,
                            title = plan.snapshot.title,
                            steps = plan.snapshot.items.map {
                                com.mobilebot.domain.todo.PlanTodo(it.id, it.text)
                            },
                            messages = messages,
                        )
                        emitPlanForReview(chatId, plan, emit)
                        return
                    }
                }
            }

            emit(RuntimeEvent.StateChanged("OBSERVING"))
        }

        val fallback = "Stopped: too many tool call rounds. Please try a simpler request."
        sessions.appendAssistantMessage(sessionKey, fallback)
        memoryFiles.appendHistoryLine("assistant: $fallback")
        workspaceContext.onAgentResponse(fallback)
        emit(RuntimeEvent.StateChanged("FAILED"))
        emit(RuntimeEvent.AssistantMessage(fallback))
    }

    // --- D. Plan helper methods ---

    private suspend fun handlePlanResponse(
        chatId: String,
        sessionKey: String,
        text: String,
        pending: PendingPlan,
        emit: suspend (RuntimeEvent) -> Unit,
    ) {
        when (text.trim().lowercase()) {
            "approve_plan" -> {
                sessions.appendUserMessage(sessionKey, text)
                planManager.approve(chatId)

                val restored = pending.savedMessages.toMutableList()
                restored.add(LlmMessage(
                    role = "user",
                    content = "Plan approved. Execute the plan step by step. " +
                        "After completing each step's actions, briefly report what was done before moving to the next step.",
                ))

                emit(RuntimeEvent.StateChanged("THINKING"))

                val toolDefs = toolRegistry.definitionsForLlm()
                workspaceContext.resetContext(
                    "Executing plan: ${pending.snapshot.title}. " +
                        pending.snapshot.items.joinToString(" | ") { it.text }
                )
                runAgentLoop(chatId, sessionKey, restored, toolDefs, emit)
            }
            "edit_plan" -> {
                planManager.reject(chatId)
                sessions.appendUserMessage(sessionKey, text)
                val prompt = "Please tell me what changes you'd like to the plan."
                sessions.appendAssistantMessage(sessionKey, prompt)
                emit(RuntimeEvent.AssistantMessage(prompt))
            }
            "reject_plan" -> {
                planManager.reject(chatId)
                sessions.appendUserMessage(sessionKey, text)
                val msg = "Plan cancelled."
                sessions.appendAssistantMessage(sessionKey, msg)
                emit(RuntimeEvent.AssistantMessage(msg))
            }
            else -> {
                // Free-form edit: treat as edit request
                planManager.reject(chatId)
                sessions.appendUserMessage(sessionKey, text)
                memoryFiles.appendHistoryLine("user: $text")
                workspaceContext.onNewUserRequest(text)

                emit(RuntimeEvent.StateChanged("THINKING"))

                val history = sessions.getMessages(sessionKey)
                val memoryDigest = buildMemoryDigest(sessionKey)
                val persistentMemories = buildPersistentMemoryDigest(text)
                val activePrompt = skillsLoader.activePrompt()
                val systemPrompt = SystemPromptBuilder.build(
                    skillRegistry = skillRegistry,
                    memoryDigest = memoryDigest,
                    activePrompt = activePrompt,
                    persistentMemories = persistentMemories,
                )
                val toolDefs = toolRegistry.definitionsForLlm()
                val messages = mutableListOf<LlmMessage>()
                messages.add(LlmMessage(role = "system", content = systemPrompt))
                for (msg in history.takeLast(MAX_HISTORY)) {
                    messages.add(msg.toLlmMessage())
                }
                runAgentLoop(chatId, sessionKey, messages, toolDefs, emit)
            }
        }
    }

    private suspend fun emitPlanForReview(
        chatId: String,
        pending: PendingPlan,
        emit: suspend (RuntimeEvent) -> Unit,
    ) {
        val hasChinese = pending.snapshot.title.any {
            Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN
        }
        val actions = if (hasChinese) {
            listOf(
                ActionOption("\u6309\u6b64\u8ba1\u5212\u6267\u884c", "approve_plan"),
                ActionOption("\u4fee\u6539\u8ba1\u5212", "edit_plan"),
                ActionOption("\u53d6\u6d88", "reject_plan"),
            )
        } else {
            listOf(
                ActionOption("Execute this plan", "approve_plan"),
                ActionOption("Edit plan", "edit_plan"),
                ActionOption("Cancel", "reject_plan"),
            )
        }
        emit(RuntimeEvent.PlanPending(pending.snapshot, actions))
    }

    private suspend fun emitPlanProgress(
        chatId: String,
        stepId: String,
        emit: suspend (RuntimeEvent) -> Unit,
    ) {
        val snapshot = planManager.currentSnapshot(chatId) ?: return
        emit(RuntimeEvent.PlanStepUpdated(snapshot, stepId, TodoStatus.COMPLETED))
    }

    private fun ensureSkillSnapshot(): SkillSnapshot {
        skillSnapshot?.let { return it }
        val snap = skillRegistry.snapshot()
        skillSnapshot = snap
        return snap
    }

    fun invalidateSkillSnapshot() {
        skillSnapshot = null
        skillRegistry.invalidateSnapshot()
    }

    private suspend fun buildMemoryDigest(sessionKey: String): String =
        withContext(Dispatchers.IO) {
            val sb = StringBuilder()

            val md = memoryFiles.readMemoryMd().trim().take(2500)
            if (md.isNotEmpty()) {
                sb.appendLine("## Workspace context")
                sb.appendLine(md)
                sb.appendLine()
            }

            val history = memoryFiles.readHistoryTail(1500).trim()
            if (history.isNotEmpty()) {
                sb.appendLine("## Recent session history")
                sb.appendLine(history)
            }

            sb.toString().trimEnd()
        }

    private suspend fun buildPersistentMemoryDigest(query: String): String =
        withContext(Dispatchers.IO) {
            val memories = persistentMemory.recallRelevant(query = query, limit = 5)
            if (memories.isEmpty()) return@withContext ""

            buildString {
                appendLine("## Persistent memories (recalled)")
                for (mem in memories) {
                    val tag = mem.type?.let { "[${MemoryType.toLabel(it)}] " } ?: ""
                    val age = MemoryDigestBuilder.ageString(mem.mtimeMs)
                    appendLine()
                    appendLine("### $tag${mem.filename} ($age)")
                    if (mem.description != null) {
                        appendLine("*${mem.description}*")
                    }
                    appendLine(mem.content.trim())
                }
            }
        }

    private suspend fun publishToBus(chatId: String, ev: RuntimeEvent) {
        when (ev) {
            is RuntimeEvent.AssistantMessage ->
                bus.publishOutbound(OutboundMessage(CHANNEL, chatId, ev.text, emptyMap()))
            is RuntimeEvent.ToolFinished ->
                bus.tryPublishOutbound(OutboundMessage(CHANNEL, chatId, ev.summary,
                    mapOf("_runtime" to "tool", "_tool" to ev.toolName, "_ok" to if (ev.success) "1" else "0")))
            is RuntimeEvent.StateChanged ->
                bus.tryPublishOutbound(OutboundMessage(CHANNEL, chatId, ev.state,
                    mapOf("_runtime" to "state", "_state" to ev.state)))
            is RuntimeEvent.ToolStarted ->
                bus.tryPublishOutbound(OutboundMessage(CHANNEL, chatId, "Tool: ${ev.toolName}",
                    mapOf("_runtime" to "tool_start", "_tool" to ev.toolName)))
            is RuntimeEvent.PlanPending -> {
                val todoJson = TodoListCodec.toJson(ev.plan)
                bus.publishOutbound(OutboundMessage(CHANNEL, chatId, todoJson,
                    mapOf("_runtime" to "todo_list")))
                val actionsJson = com.mobilebot.domain.interaction.ActionPromptCodec.toJson(ev.actions)
                bus.publishOutbound(OutboundMessage(CHANNEL, chatId, actionsJson,
                    mapOf("_runtime" to "action_prompt")))
            }
            is RuntimeEvent.PlanStepUpdated -> {
                val todoJson = TodoListCodec.toJson(ev.plan)
                bus.tryPublishOutbound(OutboundMessage(CHANNEL, chatId, todoJson,
                    mapOf("_runtime" to "todo_list")))
            }
            else -> {}
        }
    }

    private fun serializeToolCalls(toolCalls: List<LlmToolCall>): String {
        val arr = JSONArray()
        for (tc in toolCalls) {
            arr.put(JSONObject()
                .put("id", tc.id)
                .put("name", tc.name)
                .put("argumentsJson", tc.argumentsJson))
        }
        return arr.toString()
    }

    companion object {
        const val CHANNEL = "mobile"
        private const val TAG = "ToolCallAgentLoop"
        private const val MAX_TURNS = 15
        private const val MAX_TOKENS = 4096
        private const val MAX_HISTORY = 20

        private fun ChatMessage.toLlmMessage(): LlmMessage {
            val toolCalls = if (!toolCallsJson.isNullOrBlank()) {
                runCatching {
                    val arr = JSONArray(toolCallsJson)
                    val list = mutableListOf<LlmToolCall>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list.add(LlmToolCall(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            argumentsJson = o.getString("argumentsJson")
                        ))
                    }
                    list
                }.getOrNull()
            } else null

            return LlmMessage(
                role = when (role) {
                    ChatRole.System -> "system"
                    ChatRole.User -> "user"
                    ChatRole.Assistant -> "assistant"
                    ChatRole.Tool -> "tool"
                },
                content = content,
                toolCallId = toolCallId,
                name = toolName,
                toolCalls = toolCalls,
            )
        }
    }
}
