package com.mobilebot.domain.agent

import android.util.Log
import com.mobilebot.domain.LlmConfigurator
import com.mobilebot.domain.SkillsLoader
import com.mobilebot.domain.interaction.ActionOption
import com.mobilebot.domain.interaction.ActionPromptCodec
import com.mobilebot.domain.memory.MemoryFacade
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
import com.mobilebot.domain.tools.SkillTool
import com.mobilebot.domain.tools.ToolPermissionGate
import com.mobilebot.domain.tools.ToolRegistry
import com.mobilebot.model.ChatMessage
import com.mobilebot.model.ChatRole
import com.mobilebot.model.OutboundMessage
import com.mobilebot.model.ToolDefinition
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
    private val memory: MemoryFacade,
    private val skillsLoader: SkillsLoader,
    private val permissionCoordinator: AgentPermissionCoordinator,
    private val capabilityStore: AgentCapabilityStore,
    private val capabilityApprovalGate: CapabilityApprovalGate,
    private val bus: com.mobilebot.bus.MessageBus,
    private val llmConfigurator: LlmConfigurator,
    private val sessionKeyProvider: CurrentSessionKeyProvider,
    private val planManager: PlanManager,
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
        val activePrompt = skillsLoader.activePrompt()

        val systemPrompt = SystemPromptBuilder.build(
            skillRegistry = skillRegistry,
            memoryDigest = memoryDigest,
            activePrompt = activePrompt,
        )

        val toolDefs = toolDefinitionsForHistory(history)

        val messages = mutableListOf<LlmMessage>()
        messages.add(LlmMessage(role = "system", content = systemPrompt))

        for (msg in history.takeLast(MAX_HISTORY)) {
            msg.toLlmMessageOrNull()?.let { messages.add(it) }
        }
        if (messages.none { it.role == "user" && it.content == text }) {
            messages.add(LlmMessage(role = "user", content = text))
        }

        runAgentLoop(chatId, sessionKey, messages, toolDefs, emit)
    }

    private suspend fun runAgentLoop(
        chatId: String,
        sessionKey: String,
        messages: MutableList<LlmMessage>,
        initialToolDefs: List<ToolDefinition>,
        emit: suspend (RuntimeEvent) -> Unit,
    ) {
        var toolDefs = initialToolDefs
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

                // --- C. Plan step tracking on text response ---
                if (planManager.isExecuting(chatId)) {
                    val stepId = planManager.currentStepId(chatId)
                    if (stepId != null) {
                        planManager.updateStepStatus(chatId, stepId, TodoStatus.COMPLETED)
                        planManager.advanceToNextStep(chatId)
                        emitPlanProgress(chatId, stepId, emit)
                    }
                }

                emit(RuntimeEvent.StateChanged("RESPONDING"))
                emit(RuntimeEvent.AssistantMessage(reply))
                return
            }

            messages.add(LlmMessage(
                role = "assistant",
                content = response.content.orEmpty(),
                toolCalls = response.toolCalls,
            ))
            val assistantUpdate = response.content?.trim().orEmpty()
            sessions.appendAssistantMessage(
                sessionKey = sessionKey,
                content = assistantUpdate,
                toolCalls = encodeToolCalls(response.toolCalls),
            )
            if (assistantUpdate.isNotBlank()) {
                emit(RuntimeEvent.AssistantUpdate(assistantUpdate))
            }

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
                emit(RuntimeEvent.ToolFinished(tc.name, tc.id, success = result.ok, summary = body.take(200), detail = body))

                Log.d(TAG, "Tool ${tc.name} -> ${if (result.ok) "OK" else "FAIL"}: ${body.take(200)}")

                if (tc.name == SkillTool.NAME && result.ok) {
                    val allowedTools = decodeAllowedToolsFromSkillResult(result.dataJson)
                    if (allowedTools.isNotEmpty()) {
                        toolDefs = toolRegistry.definitionsForSkill(allowedTools)
                    }
                }

                decisionPromptFromToolData(result.dataJson)?.let { prompt ->
                    emitActionPrompt(sessionKey, prompt, emit)
                    return
                }

                // --- B. Post-tool-call check: expose create_plan without blocking execution ---
                if (tc.name == CreatePlanTool.NAME && result.ok) {
                    val plan = planManager.getPending(chatId)
                    if (plan != null) {
                        planManager.storePlan(
                            chatId = chatId,
                            title = plan.snapshot.title,
                            steps = plan.snapshot.items.map {
                                com.mobilebot.domain.todo.PlanTodo(it.id, it.text)
                            },
                            messages = messages,
                        )
                        planManager.approve(chatId)
                        emitPlanForExecution(chatId, sessionKey, emit)
                    }
                }
            }

            emit(RuntimeEvent.StateChanged("OBSERVING"))
        }

        val fallback = "Stopped: too many tool call rounds. Please try a simpler request."
        sessions.appendAssistantMessage(sessionKey, fallback)
        memoryFiles.appendHistoryLine("assistant: $fallback")
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

                val toolDefs = toolDefinitionsForHistory(sessions.getMessages(sessionKey))
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

                emit(RuntimeEvent.StateChanged("THINKING"))

                val history = sessions.getMessages(sessionKey)
                val memoryDigest = buildMemoryDigest(sessionKey)
                val activePrompt = skillsLoader.activePrompt()
                val systemPrompt = SystemPromptBuilder.build(
                    skillRegistry = skillRegistry,
                    memoryDigest = memoryDigest,
                    activePrompt = activePrompt,
                )
                val toolDefs = toolDefinitionsForHistory(history)
                val messages = mutableListOf<LlmMessage>()
                messages.add(LlmMessage(role = "system", content = systemPrompt))
                for (msg in history.takeLast(MAX_HISTORY)) {
                    msg.toLlmMessageOrNull()?.let { messages.add(it) }
                }
                runAgentLoop(chatId, sessionKey, messages, toolDefs, emit)
            }
        }
    }

    private suspend fun emitPlanForExecution(
        chatId: String,
        sessionKey: String,
        emit: suspend (RuntimeEvent) -> Unit,
    ) {
        val snapshot = planManager.currentSnapshot(chatId) ?: return
        val todoJson = TodoListCodec.toJson(snapshot)
        sessions.appendAssistantMessage(
            sessionKey = sessionKey,
            content = todoJson,
            toolName = TodoListCodec.MESSAGE_TOOL_NAME,
            toolCalls = todoJson,
        )
        val runningStepId = snapshot.items.firstOrNull { it.status == TodoStatus.RUNNING }?.id
            ?: snapshot.items.firstOrNull()?.id
            ?: ""
        emit(RuntimeEvent.PlanStepUpdated(snapshot, runningStepId, TodoStatus.RUNNING))
    }

    private suspend fun emitPlanForReview(
        chatId: String,
        sessionKey: String,
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
        val promptText = buildPlanReviewPrompt(pending)
        sessions.appendAssistantMessage(
            sessionKey = sessionKey,
            content = TodoListCodec.toJson(pending.snapshot),
            toolName = TodoListCodec.MESSAGE_TOOL_NAME,
            toolCalls = TodoListCodec.toJson(pending.snapshot),
        )
        sessions.appendAssistantMessage(
            sessionKey = sessionKey,
            content = promptText,
            toolName = ActionPromptCodec.MESSAGE_TOOL_NAME,
            toolCalls = ActionPromptCodec.toJson(actions),
        )
        emit(RuntimeEvent.PlanPending(pending.snapshot, actions))
    }

    private suspend fun emitActionPrompt(
        sessionKey: String,
        prompt: ToolDecisionPrompt,
        emit: suspend (RuntimeEvent) -> Unit,
    ) {
        val actions = prompt.actions.ifEmpty {
            ActionPromptCodec.resolveOptions(prompt.promptText)
        }
        sessions.appendAssistantMessage(
            sessionKey = sessionKey,
            content = prompt.promptText,
            toolName = ActionPromptCodec.MESSAGE_TOOL_NAME,
            toolCalls = ActionPromptCodec.toJson(actions),
        )
        emit(RuntimeEvent.ActionPromptRequired(prompt.promptText, actions))
    }

    private fun buildPlanReviewPrompt(pending: PendingPlan): String {
        val title = pending.snapshot.title.trim().ifBlank { "Plan" }
        val hasChinese = title.any {
            Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN
        }
        return if (hasChinese) "请确认是否执行计划：$title" else "Review this plan: $title"
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

    private fun toolDefinitionsForHistory(history: List<ChatMessage>): List<ToolDefinition> {
        val allowedTools = activeSkillAllowedTools(history)
        return if (allowedTools.isNotEmpty()) {
            toolRegistry.definitionsForSkill(allowedTools)
        } else {
            toolRegistry.definitionsForLlm()
        }
    }

    private fun activeSkillAllowedTools(history: List<ChatMessage>): Set<String> =
        history.asReversed().firstNotNullOfOrNull { msg ->
            if (msg.role == ChatRole.Tool && msg.toolName == SkillTool.NAME) {
                decodeAllowedToolsFromSkillToolMessage(msg.content).takeIf { it.isNotEmpty() }
            } else {
                null
            }
        }.orEmpty()

    fun invalidateSkillSnapshot() {
        skillSnapshot = null
        skillRegistry.invalidateSnapshot()
    }

    private suspend fun buildMemoryDigest(sessionKey: String): String =
        withContext(Dispatchers.IO) {
            val md = memoryFiles.readMemoryMd().trim().take(2500)
            val sum = memory.getSessionSummary(sessionKey)?.trim()?.take(1500).orEmpty()
            buildString {
                if (md.isNotEmpty()) {
                    appendLine("## Long-term memory excerpt")
                    appendLine(md)
                }
                if (sum.isNotEmpty()) {
                    appendLine("## Session summary")
                    appendLine(sum)
                }
            }
        }

    private suspend fun publishToBus(chatId: String, ev: RuntimeEvent) {
        when (ev) {
            is RuntimeEvent.AssistantMessage ->
                bus.publishOutbound(OutboundMessage(CHANNEL, chatId, ev.text, emptyMap()))
            is RuntimeEvent.AssistantUpdate ->
                bus.publishOutbound(OutboundMessage(CHANNEL, chatId, ev.text,
                    mapOf("_runtime" to "assistant_update")))
            is RuntimeEvent.ToolFinished ->
                bus.tryPublishOutbound(OutboundMessage(CHANNEL, chatId, ev.detail,
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
                    mapOf("_runtime" to "todo_list", "_todo_payload" to todoJson)))
                val actionsJson = com.mobilebot.domain.interaction.ActionPromptCodec.toJson(ev.actions)
                val promptText = if (ev.actions.any { it.label.any { ch -> Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.HAN } }) {
                    "请选择下一步"
                } else {
                    "Choose the next step"
                }
                bus.publishOutbound(OutboundMessage(CHANNEL, chatId, promptText,
                    mapOf("_runtime" to "action_prompt", "_actions" to actionsJson)))
            }
            is RuntimeEvent.ActionPromptRequired -> {
                val actionsJson = ActionPromptCodec.toJson(ev.actions)
                bus.publishOutbound(OutboundMessage(CHANNEL, chatId, ev.promptText,
                    mapOf("_runtime" to "action_prompt", "_actions" to actionsJson)))
            }
            is RuntimeEvent.PlanStepUpdated -> {
                val todoJson = TodoListCodec.toJson(ev.plan)
                bus.tryPublishOutbound(OutboundMessage(CHANNEL, chatId, todoJson,
                    mapOf("_runtime" to "todo_list", "_todo_payload" to todoJson)))
            }
            else -> {}
        }
    }

    companion object {
        const val CHANNEL = "mobile"
        private const val TAG = "ToolCallAgentLoop"
        private const val MAX_TURNS = 30
        private const val MAX_TOKENS = 4096
        private const val MAX_HISTORY = 20

        private data class ToolDecisionPrompt(
            val promptText: String,
            val actions: List<ActionOption>,
        )

        private fun decisionPromptFromToolData(dataJson: String?): ToolDecisionPrompt? {
            if (dataJson.isNullOrBlank()) return null
            return runCatching {
                val root = JSONObject(dataJson)
                val promptObj = root.optJSONObject("decisionPrompt")
                    ?: root.optJSONObject("userDecision")
                    ?: return@runCatching null
                val promptText = promptObj.optString("prompt")
                    .ifBlank { promptObj.optString("text") }
                    .trim()
                if (promptText.isBlank()) return@runCatching null
                val actions = ActionPromptCodec.parseJson(
                    promptObj.optJSONArray("actions")?.toString()
                        ?: promptObj.optJSONArray("options")?.toString(),
                )
                ToolDecisionPrompt(promptText, actions)
            }.getOrNull()
        }

        private fun ChatMessage.toLlmMessageOrNull(): LlmMessage? {
            if (role == ChatRole.Assistant &&
                (toolName == TodoListCodec.MESSAGE_TOOL_NAME || toolName == ActionPromptCodec.MESSAGE_TOOL_NAME)
            ) {
                return null
            }
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
            toolCalls = if (role == ChatRole.Assistant) decodeToolCalls(toolCallsJson) else null,
        )
        }

        private fun encodeToolCalls(toolCalls: List<LlmToolCall>): String =
            JSONArray().apply {
                toolCalls.forEach { tc ->
                    put(
                        JSONObject()
                            .put("id", tc.id)
                            .put("name", tc.name)
                            .put("argumentsJson", tc.argumentsJson),
                    )
                }
            }.toString()

        private fun decodeToolCalls(raw: String?): List<LlmToolCall>? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val id = o.optString("id", "").trim()
                        val name = o.optString("name", "").trim()
                        if (id.isEmpty() || name.isEmpty()) continue
                        val args = o.optString("argumentsJson", "").ifBlank {
                            o.optString("arguments", "{}")
                        }
                        add(LlmToolCall(id = id, name = name, argumentsJson = args.ifBlank { "{}" }))
                    }
                }.takeIf { it.isNotEmpty() }
            }.getOrNull()
        }

        private fun decodeAllowedToolsFromSkillResult(raw: String?): Set<String> {
            if (raw.isNullOrBlank()) return emptySet()
            return runCatching {
                val obj = JSONObject(raw)
                val arr = obj.optJSONArray("allowedTools") ?: return@runCatching emptySet()
                buildSet {
                    for (i in 0 until arr.length()) {
                        val name = arr.optString(i, "").trim()
                        if (name.isNotEmpty()) add(name)
                    }
                    if (obj.optBoolean("allowSkillSwitching", false)) add(SkillTool.NAME)
                }
            }.getOrElse { emptySet() }
        }

        private fun decodeAllowedToolsFromSkillToolMessage(content: String): Set<String> {
            val metadata = content
                .lineSequence()
                .map { it.trim() }
                .lastOrNull { it.startsWith("{") && it.contains("\"allowedTools\"") }
                ?: return emptySet()
            return decodeAllowedToolsFromSkillResult(metadata)
        }
    }
}
