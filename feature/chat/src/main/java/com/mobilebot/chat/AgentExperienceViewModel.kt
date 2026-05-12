package com.mobilebot.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilebot.bus.MessageBus
import com.mobilebot.data.settings.UserSettingsRepository
import com.mobilebot.domain.AgentLoop
import com.mobilebot.domain.ForegroundController
import com.mobilebot.domain.agent.AgentDecisionAction
import com.mobilebot.domain.agent.AgentSessionInput
import com.mobilebot.domain.agent.AgentSessionRoute
import com.mobilebot.domain.agent.AgentDecisionInput
import com.mobilebot.domain.agent.AgentDecisionIntent
import com.mobilebot.domain.agent.AgentDecisionIntentNormalizer
import com.mobilebot.domain.interaction.ActionPromptCodec
import com.mobilebot.domain.todo.TodoListCodec
import com.mobilebot.scenarios.petgrooming.PetGroomingContacts
import com.mobilebot.scenarios.petgrooming.PetGroomingConversationRules
import com.mobilebot.scenarios.petgrooming.PetGroomingDecisionIntents
import com.mobilebot.scenarios.petgrooming.PetGroomingMilestone
import com.mobilebot.scenarios.petgrooming.PetGroomingMilestoneDetector
import com.mobilebot.scenarios.petgrooming.PetGroomingScenarioSpec
import com.mobilebot.scenarios.petgrooming.PetGroomingSurfaceConversation
import com.mobilebot.scenarios.petgrooming.PetGroomingSurfaceDecision
import com.mobilebot.scenarios.petgrooming.PetGroomingSurfaceLog
import com.mobilebot.scenarios.petgrooming.PetGroomingSurfaceParticipant
import com.mobilebot.scenarios.petgrooming.PetGroomingSurfaceProgress
import com.mobilebot.scenarios.petgrooming.PetGroomingSurfaceRole
import com.mobilebot.scenarios.petgrooming.PetGroomingSurfaceStatus
import com.mobilebot.scenarios.petgrooming.PetGroomingSurfaceTimeline
import com.mobilebot.scenarios.petgrooming.PetGroomingTaskSurface
import com.mobilebot.systemruntime.CallEndedEvent
import com.mobilebot.systemruntime.IncomingCallEvent
import com.mobilebot.systemruntime.IncomingSmsEvent
import com.mobilebot.systemruntime.ReminderFiredEvent
import com.mobilebot.systemruntime.RuntimeNotificationEvent
import com.mobilebot.systemruntime.SystemRuntime
import com.mobilebot.systemruntime.SystemRuntimeEvent
import com.mobilebot.systemruntime.SystemRuntimeScriptEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AgentExperienceViewModel
    @Inject
    constructor(
        private val bus: MessageBus,
        private val agent: AgentLoop,
        private val settings: UserSettingsRepository,
        private val foreground: ForegroundController,
        private val decisionIntentNormalizer: AgentDecisionIntentNormalizer,
        private val systemRuntime: SystemRuntime,
    ) : ViewModel() {
        private var currentChatId: String? = null
        private var eventCounter = 0
        private var continuationCount = 0
        private var scenarioClock = INITIAL_SCENARIO_CLOCK
        private var deferredRetriggerInProgress = false
        private var latestAgentDecisionIntent: AgentDecisionIntent? = null
        private var pendingSelectedActionLabel: String? = null
        private var lastGroomingPaymentAmount: String? = null
        private var awaitingInitialPrecheckDecision = false
        private var activeGroomingDate = INITIAL_SCENARIO_CLOCK.toLocalDate().plusDays(1)
        private var clockMode = ScenarioClockMode.Live
        private var normalClockElapsedMs = 0L
        private var petGroomingAccepted = false
        private var taskSortCounter = 0L
        private val deliveredTimelineEvents = mutableSetOf<String>()
        private val taskStates = linkedMapOf<String, AgentTaskState>()
        private val pinnedTaskIds = linkedSetOf<String>()
        private val groomingMilestones = mutableSetOf<PetGroomingMilestone>()
        private val endedCallNotificationIds = mutableSetOf<String>()
        // 系统事件只负责投放外部事实，具体任务编排由 Agent 处理。
        private val timelineScript: List<ScenarioTimelineEvent> by lazy {
            systemRuntime.scenarioEvents(ONE_HOUR_SCENARIO_ID)
                .mapNotNull { it.toScenarioTimelineEvent(INITIAL_SCENARIO_CLOCK) }
        }

        private val scenarioSpec = PetGroomingScenarioSpec.config()
        private val scenario = AgentScenarioConfig(
            scenarioId = scenarioSpec.scenarioId,
            title = scenarioSpec.title,
            skillName = scenarioSpec.skillName,
            expectedSignals = scenarioSpec.expectedSignals,
            triggerText = scenarioSpec.triggerText,
        )

        private val _frame = MutableStateFlow(AgentExperienceFrame.initial(scenario))
        val frame: StateFlow<AgentExperienceFrame> = _frame.asStateFlow()

        init {
            viewModelScope.launch {
                bus.outbound.collect { msg ->
                    if (msg.channel != AgentLoop.CHANNEL) return@collect
                    if (msg.chatId != currentChatId) return@collect
                    if (msg.metadata["_progress"] == "1") return@collect
                    handleAgentMessage(msg.content, msg.metadata)
                }
            }
            viewModelScope.launch {
                systemRuntime.events.collect { event ->
                    handleSystemRuntimeEvent(event)
                }
            }
            viewModelScope.launch {
                while (true) {
                    delay(CLOCK_LOOP_INTERVAL_MS)
                    tickScenarioClock()
                }
            }
        }

        fun accelerateClockUntilNextEvent() {
            if (deferredRetriggerInProgress) return
            clockMode = ScenarioClockMode.FastUntilNextEvent
            normalClockElapsedMs = 0L
            _frame.update { it.copy(clockMode = clockMode) }
        }

        fun startScenario() {
            if (_frame.value.busy) return
            if (_frame.value.hasStarted && _frame.value.error == null && _frame.value.finalSummary == null) return
            val runChatId = "run-${scenario.scenarioId}-${UUID.randomUUID().toString().take(8)}"
            currentChatId = runChatId
            activeGroomingDate = scenarioClock.toLocalDate().plusDays(1)
            eventCounter = 0
            continuationCount = 0
            latestAgentDecisionIntent = null
            pendingSelectedActionLabel = null
            lastGroomingPaymentAmount = null
            awaitingInitialPrecheckDecision = true
            groomingMilestones.clear()
            val baseFrame = AgentExperienceFrame.initial(scenario).withClock(scenarioClock)
            val precheckPrompt = DecisionPrompt(
                text = "明天周日了，还是照常给麒麟约洗澡么？",
                actions = listOf(
                    ActionButton("好的", "USER_INTENT:pet_grooming.keep_current_week"),
                    ActionButton("改天再说", "USER_INTENT:pet_grooming.defer_current_week"),
                ),
            )
            _frame.value = baseFrame.copy(
                statusLabel = "Waiting for decision",
                busy = false,
                hasStarted = true,
                decisionPrompt = precheckPrompt,
                conversationItems = baseFrame.conversationItems + AgentConversationItem(
                    id = nextId("conversation"),
                    role = AgentConversationRole.AGENT,
                    text = precheckPrompt.text,
                ),
                progressLine = AgentProgressLine(
                    label = "Waiting",
                    detail = "User decision required",
                ),
                timeline = listOf(
                    AgentTimelineEvent(
                        id = nextId("decision"),
                        title = "Decision point",
                        detail = precheckPrompt.text,
                        status = AgentTimelineStatus.BLOCKED,
                    ),
                ),
                debugTrace = listOf("precheck -> ${precheckPrompt.text}"),
            )
        }

        fun chooseDecision(action: ActionButton) {
            handleSessionInput(
                AgentSessionInput.ActionSelected(
                    route = currentSessionRoute(),
                    action = action.toAgentDecisionAction(),
                ),
            )
        }

        fun submitDecisionText(text: String) {
            handleSessionInput(
                AgentSessionInput.TextSubmitted(
                    route = currentSessionRoute(),
                    text = text,
                ),
            )
        }

        private fun handleSessionInput(input: AgentSessionInput) {
            when (input) {
                is AgentSessionInput.ActionSelected -> {
                    if (_frame.value.busy) return
                    val action = input.action
                    if (action.value.startsWith(SCRIPTED_ACTION_PREFIX)) {
                        handleLocalScenarioDecision(
                            displayText = action.label,
                            rawText = action.value,
                            selectedActionValue = action.value,
                        )
                        return
                    }
                    val chatId = input.route.sessionId ?: return
                    continueWithNormalizedDecision(
                        chatId = chatId,
                        displayText = action.label,
                        rawText = action.value,
                        selectedActionValue = action.value,
                    )
                }
                is AgentSessionInput.TextSubmitted -> {
                    val value = input.text.trim()
                    if (value.isBlank() || _frame.value.busy) return
                    if (_frame.value.decisionPrompt?.actions.orEmpty().any { it.value.startsWith(SCRIPTED_ACTION_PREFIX) }) {
                        handleLocalScenarioDecision(
                            displayText = value,
                            rawText = value,
                            selectedActionValue = null,
                        )
                        return
                    }
                    val chatId = input.route.sessionId ?: return
                    continueWithNormalizedDecision(
                        chatId = chatId,
                        displayText = value,
                        rawText = value,
                        selectedActionValue = null,
                    )
                }
            }
        }

        private fun currentSessionRoute(): AgentSessionRoute =
            _frame.value.activeTaskId.let { activeTaskId ->
                AgentSessionRoute(
                    sessionId = currentChatId ?: activeTaskId,
                    taskId = activeTaskId,
                )
            }

        fun selectTask(taskId: String) {
            val task = taskStates[taskId] ?: return
            taskStates[_frame.value.activeTaskId]?.let {
                taskStates[it.id] = _frame.value.captureTaskState(it)
            }
            // 查看任务不改变任务活跃顺序，只有真实事件更新才刷新排序。
            _frame.update { task.applyToFrame(it) }
        }

        fun toggleTaskPinned(taskId: String) {
            if (taskId in pinnedTaskIds) {
                pinnedTaskIds.remove(taskId)
            } else {
                pinnedTaskIds.add(taskId)
            }
            _frame.update { frame ->
                frame.copy(taskCards = taskCardsFor(activeId = frame.activeTaskId))
            }
        }

        private fun continueWithNormalizedDecision(
            chatId: String,
            displayText: String,
            rawText: String,
            selectedActionValue: String?,
        ) {
            val prompt = _frame.value.decisionPrompt
            val delayUserBubble = selectedActionValue != null
            if (delayUserBubble) {
                pendingSelectedActionLabel = displayText
            }
            continuationCount = 0
            _frame.update {
                it.copy(
                    statusLabel = "Understanding",
                    busy = true,
                    decisionPrompt = if (selectedActionValue == null) null else it.decisionPrompt,
                    activeActionValue = selectedActionValue,
                    conversationItems = if (delayUserBubble) {
                        it.conversationItems
                    } else {
                        appendConversation(
                            it.conversationItems,
                            AgentConversationRole.USER,
                            displayText,
                        )
                    },
                    progressLine = AgentProgressLine(
                        label = "Understanding",
                        detail = displayText,
                        completed = completedStageCount(it),
                        total = totalStageCount(it),
                    ),
                    timeline = it.timeline + AgentTimelineEvent(
                        id = nextId("decision"),
                        title = "Decision received",
                        detail = displayText,
                        status = AgentTimelineStatus.DONE,
                    ),
                    debugTrace = appendTrace(it.debugTrace, "user decision -> ${rawText.take(160)}"),
                )
            }
            viewModelScope.launch {
                val normalized = decisionIntentNormalizer.normalize(
                    AgentDecisionInput(
                        contextId = scenario.scenarioId,
                        promptText = prompt?.text.orEmpty(),
                        presentedActions = prompt?.actions.orEmpty().map { it.toAgentDecisionAction() },
                        candidateIntents = PetGroomingDecisionIntents.forScenario(scenario.scenarioId),
                        displayText = displayText,
                        rawText = rawText,
                    ),
                )
                val initialPrecheckDecision = awaitingInitialPrecheckDecision
                awaitingInitialPrecheckDecision = false
                latestAgentDecisionIntent = normalized.intent
                _frame.update {
                    val initialTaskLog =
                        if (initialPrecheckDecision &&
                            normalized.intent == PetGroomingDecisionIntents.KeepCurrentWeek
                        ) {
                            AgentTaskLog(
                                id = nextId("task"),
                                timeText = blueprintTimeText(scenarioClock),
                                text = "创建麒麟日常洗护任务。",
                            )
                        } else {
                            null
                        }
                    it.copy(
                        statusLabel = "Resuming",
                        taskLogs = initialTaskLog?.let { taskLog -> appendTaskLogs(it.taskLogs, listOf(taskLog)) }
                            ?: it.taskLogs,
                        progressLine = it.progressLine.copy(
                            label = "Resuming",
                            detail = displayText,
                        ),
                        debugTrace = appendTrace(
                            it.debugTrace,
                            "normalized intent -> ${normalized.intent.id}${if (normalized.usedFallback) " (fallback)" else ""}",
                        ),
                    )
                }
                if (
                    initialPrecheckDecision &&
                    normalized.intent == PetGroomingDecisionIntents.DeferCurrentWeek
                ) {
                    completeDeferredGroomingRun()
                    return@launch
                }
                val agentText =
                    if (initialPrecheckDecision) {
                        """
                            ${PetGroomingScenarioSpec.triggerText(scenarioClock)}

                            Y already answered the weekly precheck decision. Authoritative decision: ${normalized.agentText}
                            Do not ask the weekly precheck question again. If Y keeps this week, continue booking and coordination from that decision. If Y defers this week, acknowledge briefly and stop this run without contacting PetSmart or Driver.
                        """.trimIndent()
                    } else {
                        normalized.agentText
                    }
                runAgentTurn(chatId, agentText)
            }
        }

        private fun completeDeferredGroomingRun() {
            val message = "好的，那下周再说。"
            _frame.update {
                val visibleBase = it.withPendingSelectedAction()
                visibleBase.copy(
                    busy = false,
                    statusLabel = "完成",
                    decisionPrompt = null,
                    activeActionValue = null,
                    finalSummary = message,
                    conversationItems = appendConversation(
                        visibleBase.conversationItems,
                        AgentConversationRole.AGENT,
                        message,
                    ),
                    progressLine = AgentProgressLine(
                        label = "完成",
                        detail = message,
                        completed = totalStageCount(visibleBase),
                        total = totalStageCount(visibleBase),
                    ),
                    timeline = visibleBase.timeline + AgentTimelineEvent(
                        id = nextId("assistant"),
                        title = "完成",
                        detail = message,
                        status = AgentTimelineStatus.DONE,
                    ),
                    debugTrace = appendTrace(visibleBase.debugTrace, "defer -> scheduled next weekly precheck"),
                )
            }
            if (shouldScheduleDeferredRetrigger(_frame.value)) {
                scheduleDeferredRetrigger()
            }
        }

        private suspend fun runAgentTurn(chatId: String, text: String) {
            try {
                if (settings.getApiKey().isBlank()) {
                    _frame.update {
                        it.withPendingSelectedAction().copy(
                            busy = false,
                            statusLabel = "Needs API key",
                            decisionPrompt = null,
                            activeActionValue = null,
                            progressLine = AgentProgressLine(
                                label = "Needs API key",
                                detail = "Configure the provider key in Settings before running.",
                                completed = completedStageCount(it),
                                total = totalStageCount(it),
                            ),
                            error = "Configure an API key in Settings before running the LLM flow.",
                        )
                    }
                    return
                }
                foreground.onAgentStart()
                withContext(Dispatchers.IO) {
                    agent.processUserMessage(chatId, text)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Agent experience failed", e)
                val detail = e.message ?: e.javaClass.simpleName
                _frame.update {
                    it.withPendingSelectedAction().copy(
                        busy = false,
                        statusLabel = "Error",
                        decisionPrompt = null,
                        activeActionValue = null,
                        progressLine = AgentProgressLine(
                            label = "Needs attention",
                            detail = "The provider response could not be completed.",
                            completed = completedStageCount(it),
                            total = totalStageCount(it),
                        ),
                        error = e.message ?: e.javaClass.simpleName,
                        debugTrace = appendTrace(it.debugTrace, "error -> $detail"),
                        timeline = it.timeline + AgentTimelineEvent(
                            id = nextId("error"),
                            title = "Agent error",
                            detail = detail,
                            status = AgentTimelineStatus.FAILED,
                        ),
                    )
                }
            } finally {
                foreground.onAgentStop()
                val continuationPrompt = continuationPromptFor(_frame.value)
                if (continuationPrompt != null) {
                    if (continuationCount >= MAX_AUTO_CONTINUATIONS) {
                        _frame.update {
                            it.withPendingSelectedAction().copy(
                                busy = false,
                                statusLabel = "Needs attention",
                                finalSummary = null,
                                error = "The grooming workflow stopped before closure.",
                                decisionPrompt = null,
                                activeActionValue = null,
                                progressLine = AgentProgressLine(
                                    label = "Needs attention",
                                    detail = "The workflow needs manual review before continuing.",
                                    completed = completedStageCount(it),
                                    total = totalStageCount(it),
                                ),
                                timeline = it.timeline + AgentTimelineEvent(
                                    id = nextId("guard"),
                                    title = "Workflow paused",
                                    detail = "The run stopped before home confirmation, payment, and accounting were complete.",
                                    status = AgentTimelineStatus.FAILED,
                                ),
                            )
                        }
                        return
                    }
                    continuationCount += 1
                    _frame.update {
                        it.copy(
                            busy = true,
                            statusLabel = "Continuing",
                            finalSummary = null,
                            progressLine = AgentProgressLine(
                                label = "Continuing",
                                detail = "Advancing to the next missing grooming milestone.",
                                completed = completedStageCount(it),
                                total = totalStageCount(it),
                            ),
                            timeline = it.timeline + AgentTimelineEvent(
                                id = nextId("guard"),
                                title = "Workflow continuing",
                                detail = "The grooming flow remains open until home confirmation, payment, and accounting are complete.",
                                status = AgentTimelineStatus.RUNNING,
                            ),
                            debugTrace = appendTrace(it.debugTrace, "continuation -> grooming workflow remains open"),
                        )
                    }
                    runAgentTurn(chatId, continuationPrompt)
                    return
                }
                _frame.update {
                    if (it.decisionPrompt != null) {
                        it.copy(busy = false, statusLabel = "Waiting for decision", activeActionValue = null)
                    } else if (it.error != null) {
                        it.copy(busy = false)
                    } else {
                        it.copy(
                            busy = false,
                            statusLabel = if (it.finalSummary != null) "Complete" else "Ready for next step",
                            progressLine = it.progressLine.copy(
                                label = if (it.finalSummary != null) "Complete" else it.progressLine.label,
                            ),
                        )
                    }
                }
                if (shouldScheduleDeferredRetrigger(_frame.value)) {
                    scheduleDeferredRetrigger()
                }
            }
        }

        private fun continuationPromptFor(frame: AgentExperienceFrame): String? {
            if (frame.scenario.scenarioId != "pet-grooming") return null
            if (frame.decisionPrompt != null || frame.error != null) return null
            if (frame.finalSummary.isNullOrBlank()) return null
            if (groomingDeferred(frame)) return null
            if (groomingClosureSatisfied(frame)) return null
            return groomingContinuationPrompt()
        }

        private fun groomingContinuationPrompt(): String =
            PetGroomingScenarioSpec.continuationPrompt(
                groomingDate = activeGroomingDate,
                selectedShop = selectedGroomingShopName(),
            )

        private fun groomingDeferred(frame: AgentExperienceFrame): Boolean {
            if (frame.scenario.scenarioId != "pet-grooming") return false
            return latestAgentDecisionIntent == PetGroomingDecisionIntents.DeferCurrentWeek
        }

        private fun shouldScheduleDeferredRetrigger(frame: AgentExperienceFrame): Boolean =
            frame.scenario.scenarioId == "pet-grooming" &&
                frame.finalSummary != null &&
                frame.decisionPrompt == null &&
                frame.error == null &&
                groomingDeferred(frame)

        private fun scheduleDeferredRetrigger() {
            if (deferredRetriggerInProgress) return
            deferredRetriggerInProgress = true
            viewModelScope.launch {
                val nextClock = scenarioClock.plusDays(7).withHour(13).withMinute(0).withSecond(0).withNano(0)
                advanceClockTo(nextClock)
                scenarioClock = nextClock
                latestAgentDecisionIntent = null
                pendingSelectedActionLabel = null
                lastGroomingPaymentAmount = null
                groomingMilestones.clear()
                _frame.value = AgentExperienceFrame.initial(scenario).withClock(scenarioClock)
                deferredRetriggerInProgress = false
                delay(AUTO_TRIGGER_DELAY_MS)
                if (!_frame.value.hasStarted && !_frame.value.busy) {
                    startScenario()
                }
            }
        }

        private suspend fun advanceClockTo(target: LocalDateTime) {
            val start = scenarioClock
            val totalMinutes = Duration.between(start, target).toMinutes().coerceAtLeast(0)
            repeat(CLOCK_ADVANCE_STEPS) { index ->
                val elapsedMinutes = totalMinutes * (index + 1) / CLOCK_ADVANCE_STEPS
                val current = start.plusMinutes(elapsedMinutes)
                _frame.update { it.withClock(current) }
                delay(CLOCK_ADVANCE_STEP_MS)
            }
            _frame.update { it.withClock(target) }
        }

        private fun AgentExperienceFrame.withClock(clock: LocalDateTime): AgentExperienceFrame =
            copy(
                clockTimeText = clock.toLocalTime().format(CLOCK_TIME_FORMATTER),
                clockDateText = "${clock.toLocalDate().format(CLOCK_DATE_FORMATTER)} ${clock.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)}",
                clockMode = clockMode,
            )

        private fun groomingClosureSatisfied(frame: AgentExperienceFrame): Boolean {
            if (frame.scenario.scenarioId != "pet-grooming") return false
            return groomingMilestones.containsAll(
                setOf(
                    PetGroomingMilestone.HOME_CONFIRMED,
                    PetGroomingMilestone.PAYMENT_COMPLETED,
                    PetGroomingMilestone.EXPENSE_RECORDED,
                ),
            )
        }

        private fun handleAgentMessage(content: String, metadata: Map<String, String>) {
            val runtime = metadata["_runtime"].orEmpty()
            val tool = metadata["_tool"].orEmpty()
            if (runtime == "tool" && metadata["_ok"] == "1") {
                recordGroomingMilestones(tool, content)
            }
            _frame.update { frame ->
                val debugLine = buildDebugLine(runtime, tool, content)
                val base = frame.copy(debugTrace = appendTrace(frame.debugTrace, debugLine))
                when (runtime) {
                    "state" -> {
                        val state = metadata["_state"].orEmpty().ifBlank { content }
                        val status = if (state == "FAILED") AgentTimelineStatus.FAILED else AgentTimelineStatus.RUNNING
                        base.copy(
                            statusLabel = state.ifBlank { base.statusLabel },
                            progressLine = AgentProgressLine(
                                label = state.ifBlank { "Running" },
                                detail = base.progressLine.detail,
                                completed = completedStageCount(base),
                                total = totalStageCount(base),
                            ),
                            timeline = base.timeline + AgentTimelineEvent(
                                id = nextId("state"),
                                title = "Agent status",
                                detail = state.ifBlank { "State updated" },
                                status = status,
                            ),
                        )
                    }
                    "tool_start" -> {
                        val event = toolStartEvent(tool)
                        val visibleBase = base.withPendingSelectedAction()
                        visibleBase.copy(
                            statusLabel = "Executing",
                            decisionPrompt = null,
                            activeActionValue = null,
                            progressLine = AgentProgressLine(
                                label = "Executing",
                                detail = toolProgressDetail(tool),
                                completed = completedStageCount(visibleBase),
                                total = totalStageCount(visibleBase),
                            ),
                            timeline = visibleBase.timeline + event,
                        )
                    }
                    "tool" -> {
                        val ok = metadata["_ok"] == "1"
                        val event = toolResultEvent(tool, content, ok)
                        val systemTool = isSystemRuntimeTool(tool)
                        val signals =
                            if (systemTool) appendSignal(base.systemSignals, systemSignalFromDeviceResult(content))
                            else base.systemSignals
                        val taskLog = taskLogFromToolResult(tool, content, ok, base.taskLogs.size)
                        val partyLogs = partyTaskLogsFromToolResult(tool, content, ok, base.taskLogs)
                        val logsToAppend = partyLogs + listOfNotNull(taskLog)
                        val notification = if (systemTool && ok) notificationFromDeviceResult(content) else null
                        base.copy(
                            systemSignals = signals,
                            taskLogs = appendTaskLogs(base.taskLogs, logsToAppend),
                            participants = updateParticipantsFromTaskLogs(base.participants, logsToAppend),
                            systemNotification = notification ?: base.systemNotification,
                            progressLine = AgentProgressLine(
                                label = if (ok) "Updated" else "Needs attention",
                                detail = event.detail,
                                completed = completedStageCount(base),
                                total = totalStageCount(base),
                            ),
                            timeline = base.timeline + event,
                        ).withScenarioEventClock(tool, content)
                    }
                    "assistant_update" -> {
                        val displayText = visibleAssistantUpdate(content)
                        if (displayText == null) {
                            base
                        } else {
                            val visibleBase = base.withPendingSelectedAction()
                            visibleBase.copy(
                                statusLabel = "Running",
                                decisionPrompt = null,
                                activeActionValue = null,
                                conversationItems = appendConversation(
                                    visibleBase.conversationItems,
                                    AgentConversationRole.AGENT,
                                    displayText,
                                ),
                                progressLine = AgentProgressLine(
                                    label = "Running",
                                    detail = displayText.lineSequence().firstOrNull().orEmpty().take(120),
                                    completed = completedStageCount(base),
                                    total = totalStageCount(base),
                                ),
                            )
                        }
                    }
                    "todo_list" -> updatePlan(base, metadata["_todo_payload"].orEmpty())
                    "action_prompt" -> {
                        val actions = parseActionButtons(metadata["_actions"], content)
                        val displayText = compactDecisionPromptText(content)
                        val visibleBase = base.withPendingSelectedAction()
                        if (shouldSuppressResolvedGroomingPrompt(content)) {
                            visibleBase.copy(
                                statusLabel = "Running",
                                finalSummary = content,
                                decisionPrompt = null,
                                activeActionValue = null,
                                progressLine = AgentProgressLine(
                                    label = "Running",
                                    detail = "Continuing workflow",
                                    completed = completedStageCount(visibleBase),
                                    total = totalStageCount(visibleBase),
                                ),
                            )
                        } else {
                            visibleBase.copy(
                                statusLabel = "Waiting for decision",
                                decisionPrompt = DecisionPrompt(displayText, actions),
                                activeActionValue = null,
                                conversationItems = appendConversation(
                                    visibleBase.conversationItems,
                                    AgentConversationRole.AGENT,
                                    displayText,
                                ),
                                progressLine = AgentProgressLine(
                                    label = "Waiting",
                                    detail = "User decision required",
                                    completed = completedStageCount(visibleBase),
                                    total = totalStageCount(visibleBase),
                                ),
                                timeline = visibleBase.timeline + AgentTimelineEvent(
                                    id = nextId("decision"),
                                    title = "Decision point",
                                    detail = displayText,
                                    status = AgentTimelineStatus.BLOCKED,
                                ),
                            )
                        }
                    }
                    "error" ->
                        base.withPendingSelectedAction().copy(
                            statusLabel = "Error",
                            error = content,
                            decisionPrompt = null,
                            activeActionValue = null,
                            progressLine = AgentProgressLine(
                                label = "Needs attention",
                                detail = "The provider response could not be completed.",
                                completed = completedStageCount(base),
                                total = totalStageCount(base),
                            ),
                            timeline = base.timeline + AgentTimelineEvent(
                                id = nextId("error"),
                                title = "Agent error",
                                detail = content,
                                status = AgentTimelineStatus.FAILED,
                            ),
                        )
                    else -> {
                        if (content.isBlank()) {
                            base
                        } else if (content.startsWith(TOOL_ROUND_LIMIT_PREFIX)) {
                            base.withPendingSelectedAction().copy(
                                statusLabel = "Failed",
                                error = content,
                                finalSummary = null,
                                decisionPrompt = null,
                                activeActionValue = null,
                                progressLine = AgentProgressLine(
                                    label = "Needs attention",
                                    detail = "The run stopped after too many internal action rounds.",
                                    completed = completedStageCount(base),
                                    total = totalStageCount(base),
                                ),
                                timeline = base.timeline + AgentTimelineEvent(
                                    id = nextId("assistant"),
                                    title = "Agent stopped",
                                    detail = content,
                                    status = AgentTimelineStatus.FAILED,
                                ),
                            )
                        } else {
                            val decisionPrompt =
                                if (shouldSuppressResolvedGroomingPrompt(content)) null else inferDecisionPrompt(content)
                            if (decisionPrompt != null) {
                                val visibleBase = base.withPendingSelectedAction()
                                visibleBase.copy(
                                    statusLabel = "Waiting for decision",
                                    decisionPrompt = decisionPrompt,
                                    activeActionValue = null,
                                    finalSummary = null,
                                    conversationItems = appendConversation(
                                        visibleBase.conversationItems,
                                        AgentConversationRole.AGENT,
                                        decisionPrompt.text,
                                    ),
                                    progressLine = AgentProgressLine(
                                        label = "Waiting",
                                        detail = "User decision required",
                                        completed = completedStageCount(base),
                                        total = totalStageCount(base),
                                    ),
                                    timeline = base.timeline + AgentTimelineEvent(
                                        id = nextId("decision"),
                                        title = "Decision point",
                                        detail = decisionPrompt.text,
                                        status = AgentTimelineStatus.BLOCKED,
                                    ),
                                )
                            } else {
                                val displayText = compactFinalSummary(content)
                                val silentProgress =
                                    scenario.scenarioId == "pet-grooming" &&
                                    displayText == null &&
                                    PetGroomingConversationRules.isTransientNarration(content)
                                val visibleBase = base.withPendingSelectedAction()
                                visibleBase.copy(
                                    finalSummary = displayText ?: content,
                                    decisionPrompt = null,
                                    activeActionValue = null,
                                    conversationItems = if (displayText == null) {
                                        visibleBase.conversationItems
                                    } else {
                                        appendConversation(
                                            visibleBase.conversationItems,
                                            AgentConversationRole.AGENT,
                                            displayText,
                                        )
                                    },
                                    progressLine = AgentProgressLine(
                                        label = if (silentProgress) "Running" else "Complete",
                                        detail = if (silentProgress) {
                                            "Continuing workflow"
                                        } else {
                                            displayText ?: content.lineSequence().firstOrNull().orEmpty().take(120)
                                        },
                                        completed = if (silentProgress) completedStageCount(base) else totalStageCount(base),
                                        total = totalStageCount(base),
                                    ),
                                    timeline = base.timeline + AgentTimelineEvent(
                                        id = nextId("assistant"),
                                        title = if (silentProgress) "Assistant checkpoint" else "Final summary",
                                        detail = content.take(220),
                                        status = if (silentProgress) AgentTimelineStatus.RUNNING else AgentTimelineStatus.DONE,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        private fun visibleAssistantUpdate(content: String): String? {
            if (content.isBlank() || content.length > MAX_VISIBLE_ASSISTANT_UPDATE_CHARS) return null
            val lower = content.lowercase()
            if (scenario.scenarioId == "pet-grooming" && PetGroomingConversationRules.isTransientNarration(content)) return null
            if (
                lower.contains("system_wait_for_sms") ||
                lower.contains("device_system") ||
                    lower.contains("device system") ||
                    lower.contains("pet-grooming workflow") ||
                    lower.contains("workflow requires") ||
                    lower.contains("required closure") ||
                    lower.contains("closure requirements") ||
                    lower.contains("full history") ||
                    lower.contains("next required tool") ||
                    lower.contains("tool call") ||
                    lower.contains("workflow logic") ||
                    lower.contains("decision point") ||
                    lower.contains("defer_current_week") ||
                    lower.contains("load y's relevant memory") ||
                    lower.contains("create a concise plan") ||
                    lower.contains("let's begin kylin's scheduled") ||
                    lower.contains("now i'll check if y wants") ||
                    lower.contains("sending now") ||
                    lower.contains("now waiting") ||
                    lower.contains("no further action needed") ||
                    lower.contains("execute final closure") ||
                    lower.contains("proceeding with payment") ||
                    lower.contains("confirmed as kylin") ||
                    lower.contains("i'll now begin listening") ||
                    lower.contains("the final truth per your memory")
            ) {
                return null
            }
            if (!containsChinese(content)) return null
            return content
        }

        private fun compactFinalSummary(content: String): String? {
            val lower = content.lowercase()
            return when {
                scenario.scenarioId == "pet-grooming" &&
                    PetGroomingConversationRules.isTransientNarration(content) -> null
                scenario.scenarioId == "pet-grooming" &&
                    isRoutineReminderQuestion(content) -> null
                scenario.scenarioId == "pet-grooming" &&
                    lower.contains("petsmart") &&
                    content.contains("最终价格") -> "已向 PetSmart 发送短信，确认周日可选时段和服务内容。"
                scenario.scenarioId == "pet-grooming" &&
                    PetGroomingConversationRules.isCompletionText(content) ->
                    PetGroomingConversationRules.compactCompletionText(content, lastGroomingPaymentAmount)
                (
                    lower.contains("deferred") ||
                        lower.contains("skip this week") ||
                        lower.contains("postponed") ||
                        lower.contains("改天") ||
                        lower.contains("本周先不")
                ) &&
                    !offersDeferralAsOption(content) ->
                    "好的，那下周再说。"
                containsChinese(content) -> content
                else -> null
            }
        }

        private fun offersDeferralAsOption(text: String): Boolean =
            text.contains("`改天再说`") ||
                text.lineSequence().any { line ->
                    val trimmed = line.trimStart()
                    (trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.startsWith("•")) &&
                        trimmed.contains("改天")
                }

        private fun isRoutineReminderQuestion(text: String): Boolean {
            return scenario.scenarioId == "pet-grooming" &&
                PetGroomingConversationRules.isRoutineReminderQuestion(
                    text = text,
                    looksLikeDecisionRequest = looksLikeDecisionRequest(text),
                )
        }

        private fun normalizeAmountText(value: String): String {
            return PetGroomingConversationRules.normalizeAmountText(value)
        }

        private fun containsChinese(text: String): Boolean =
            text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }

        private fun updatePlan(frame: AgentExperienceFrame, payload: String): AgentExperienceFrame {
            val snapshot = TodoListCodec.parseJson(payload) ?: return frame
            val completed = snapshot.items.count { it.status == com.mobilebot.domain.todo.TodoStatus.COMPLETED }
            val total = snapshot.items.size.coerceAtLeast(1)
            return frame.copy(
                statusLabel = "Planning",
                stageCards = snapshot.items.map {
                    AgentStageCard(
                        id = it.id,
                        title = it.text,
                        status = it.status.toAgentStatus(),
                    )
                },
                progressLine = AgentProgressLine(
                    label = "Planning",
                    detail = snapshot.items.firstOrNull { it.status == com.mobilebot.domain.todo.TodoStatus.RUNNING }?.text
                        ?: snapshot.title.ifBlank { "Preparing next stages" },
                    completed = completed,
                    total = total,
                ),
                timeline = frame.timeline + AgentTimelineEvent(
                    id = nextId("plan"),
                    title = "Execution plan ready",
                    detail = snapshot.title.ifBlank { "The agent has prepared the next stages." },
                    status = AgentTimelineStatus.DONE,
                ),
            )
        }

        fun dismissSystemNotification() {
            var shouldAutoAdvanceActiveCall = false
            _frame.update { frame ->
                val notification = frame.systemNotification
                val isIncomingCallAction = notification?.actionLabel == "接听"
                val isExpiredIncomingCall = isIncomingCallAction && notification.id in endedCallNotificationIds
                val call = if (isIncomingCallAction && !isExpiredIncomingCall) {
                    // 接听后短暂展示通话态，再自动推进到系统通话结束事件。
                    shouldAutoAdvanceActiveCall = true
                    AgentActiveCall(
                        id = notification.id,
                        caller = notification.title.removeSuffix(" 来电"),
                        startedTimeText = notification.timeText,
                        statusText = "正在通话",
                        transcriptText = "通话转写中",
                    )
                } else {
                    frame.activeCall
                }
                frame.copy(
                    systemNotification = null,
                    activeCall = if (isExpiredIncomingCall) null else call,
                )
            }
            if (shouldAutoAdvanceActiveCall) {
                viewModelScope.launch {
                    delay(CALL_CONNECTED_DISPLAY_MS)
                    if (_frame.value.activeCall != null) {
                        accelerateClockUntilNextEvent()
                    }
                }
            }
        }

        fun expireSystemNotification(notificationId: String) {
            _frame.update { frame ->
                if (frame.systemNotification?.id == notificationId) {
                    frame.copy(systemNotification = null)
                } else {
                    frame
                }
            }
        }

        private fun toolStartEvent(tool: String): AgentTimelineEvent {
            val (title, detail) = when (tool) {
                "use_skill" -> "流程已选择" to "加载麒麟洗护协调规则。"
                "create_plan" -> "开始规划" to "准备执行阶段。"
                "device_system" -> "检查上下文" to "读取手机上下文和服务状态。"
                "system_search_contacts" -> "检查联系人" to "确认相关参与方。"
                "system_send_sms" -> "发送短信" to "等待预期回复。"
                "system_wait_for_sms" -> "监听短信" to "等待匹配短信。"
                "read_user_profile" -> "读取偏好" to "检查已保存的用户偏好。"
                else -> "继续执行" to "推进当前流程。"
            }
            return AgentTimelineEvent(
                id = nextId("tool"),
                title = title,
                detail = detail,
                status = AgentTimelineStatus.RUNNING,
            )
        }

        private fun toolProgressDetail(tool: String): String =
            when (tool) {
                "use_skill" -> "加载协调规则"
                "create_plan" -> "准备任务计划"
                "device_system" -> "调用手机能力"
                "system_search_contacts" -> "确认联系人"
                "system_send_sms" -> "发送短信并启动监听"
                "system_wait_for_sms" -> "等待匹配短信"
                "read_user_profile" -> "读取已保存偏好"
                else -> "继续推进流程"
            }

        private fun toolResultEvent(
            tool: String,
            content: String,
            ok: Boolean,
        ): AgentTimelineEvent {
            val detail = when (tool) {
                "use_skill" -> "麒麟洗护协调规则已启用。"
                "create_plan" -> "执行阶段已更新。"
                "device_system" -> systemSignalFromDeviceResult(content)
                "system_search_contacts" -> systemSignalFromDeviceResult(content)
                "system_send_sms" -> systemSignalFromDeviceResult(content)
                "system_wait_for_sms" -> systemSignalFromDeviceResult(content)
                "read_user_profile" -> "已检查用户资料。"
                else -> content.substringBefore('\n').take(180).ifBlank { "操作已完成。" }
            }
            return AgentTimelineEvent(
                id = nextId("tool"),
                title = if (ok) actionCompleteTitle(tool) else "需要处理",
                detail = detail,
                status = if (ok) AgentTimelineStatus.DONE else AgentTimelineStatus.FAILED,
            )
        }

        private fun actionCompleteTitle(tool: String): String =
            when (tool) {
                "use_skill" -> "流程就绪"
                "create_plan" -> "计划就绪"
                "device_system" -> "上下文已更新"
                "system_search_contacts" -> "联系人已确认"
                "system_send_sms" -> "短信已发送"
                "system_wait_for_sms" -> "短信已收到"
                "read_user_profile" -> "资料已检查"
                else -> "操作已完成"
            }

        private fun systemSignalFromDeviceResult(content: String): String {
            val message = content.substringBefore('\n').trim()
            return when {
                message.startsWith("Memory returned", ignoreCase = true) -> memorySignal(content)
                message.startsWith("Contacts returned", ignoreCase = true) -> "相关联系人已确认。"
                message.startsWith("SMS sent", ignoreCase = true) -> "短信已发送。"
                message.startsWith("Inbound SMS received", ignoreCase = true) -> "收到新的服务回复。"
                message.startsWith("Location resolved", ignoreCase = true) -> "位置上下文已确认。"
                message.startsWith("Service response", ignoreCase = true) ||
                    message.startsWith("Service gateway response", ignoreCase = true) -> "服务信息已更新。"
                message.startsWith("Service gateway echo", ignoreCase = true) -> "服务请求已记录。"
                message.startsWith("Long reminder created", ignoreCase = true) ||
                    message.startsWith("Reminder created", ignoreCase = true) -> "长提醒已建立。"
                message.startsWith("Notification posted", ignoreCase = true) -> "提醒已设置。"
                message.startsWith("Phone call connected", ignoreCase = true) -> "电话已接通。"
                message.startsWith("Call log returned", ignoreCase = true) -> "通话记录已检查。"
                message.startsWith("Device state returned", ignoreCase = true) -> "设备状态已检查。"
                message.startsWith("Payment completed", ignoreCase = true) -> "支付已完成。"
                message.startsWith("Expense recorded", ignoreCase = true) -> "记账已完成。"
                else -> "上下文已更新。"
            }
        }

        private fun memorySignal(content: String): String =
            when {
                content.contains("\"key\":\"memory\"", ignoreCase = true) -> "麒麟服务偏好已加载。"
                content.contains("\"key\":\"places\"", ignoreCase = true) -> "家庭和常用地点已加载。"
                content.contains("\"key\":\"social\"", ignoreCase = true) -> "可信服务关系已加载。"
                else -> "用户资料已加载。"
            }

        private fun taskLogFromToolResult(
            tool: String,
            content: String,
            ok: Boolean,
            eventIndex: Int,
        ): AgentTaskLog? {
            if (!ok) {
                return AgentTaskLog(
                    id = nextId("task"),
                    timeText = taskLogTimeFor(tool, content, eventIndex),
                    text = "操作需要处理：${content.substringBefore('\n').take(140)}",
                )
            }
            return when (tool) {
                "device_system", "system_search_contacts", "system_send_sms", "system_wait_for_sms" -> deviceTaskLog(
                    tool = tool,
                    content = content,
                    eventIndex = eventIndex,
                )
                else -> null
            }
        }

        private fun deviceTaskLog(
            tool: String,
            content: String,
            eventIndex: Int,
        ): AgentTaskLog? {
            val message = content.substringBefore('\n').trim()
            val data = parseToolData(content)
            val text = when {
                message.startsWith("Memory returned", ignoreCase = true) -> return null
                message.startsWith("Contacts returned", ignoreCase = true) -> return null
                message.startsWith("SMS sent", ignoreCase = true) -> {
                    val sms = data?.optJSONObject("sms")
                    val to = sms?.optString("displayName").orEmpty()
                        .ifBlank { sms?.optString("to").orEmpty() }
                        .ifBlank { data?.optJSONObject("listener")?.optString("contact").orEmpty() }
                        .ifBlank { "联系人" }
                    val body = sms?.optString("message").orEmpty()
                    "发送短信给 $to${body.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()}"
                }
                message.startsWith("Inbound SMS received", ignoreCase = true) -> {
                    val sms = data?.optJSONObject("sms")
                    val from = sms?.optString("displayName").orEmpty()
                        .ifBlank { sms?.optString("from").orEmpty() }
                        .ifBlank { data?.optJSONObject("listener")?.optString("contact").orEmpty() }
                        .ifBlank { "联系人" }
                    val body = sms?.optString("message").orEmpty()
                    "收到 $from 的短信${body.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()}"
                }
                message.startsWith("Notification posted", ignoreCase = true) -> {
                    val notification = data?.optJSONObject("notification")
                    val title = notification?.optString("title").orEmpty().ifBlank { "Reminder" }
                    val body = notification?.optString("body").orEmpty()
                    "设置提醒：$title${body.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()}"
                }
                message.startsWith("Long reminder created", ignoreCase = true) ||
                    message.startsWith("Reminder created", ignoreCase = true) -> {
                    val reminder = data?.optJSONObject("reminder")
                    val title = reminder?.optString("title").orEmpty().ifBlank { "提醒" }
                    val body = displayReminderBody(reminder?.optString("body").orEmpty())
                    val scheduledFor = displayReminderTime(
                        reminder?.optString("scheduledFor").orEmpty().ifBlank { "按计划时间" },
                    )
                    "新建长提醒：$scheduledFor $title${body.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()}"
                }
                message.startsWith("Phone call connected", ignoreCase = true) -> message
                message.startsWith("Call log returned", ignoreCase = true) -> return null
                message.startsWith("Location resolved", ignoreCase = true) -> return null
                message.startsWith("Service response", ignoreCase = true) ||
                    message.startsWith("Service gateway response", ignoreCase = true) -> serviceTaskLogText(data)
                message.startsWith("Service gateway echo", ignoreCase = true) -> "记录服务请求。"
                message.startsWith("Device state returned", ignoreCase = true) -> return null
                message.startsWith("Payment completed", ignoreCase = true) -> {
                    val payment = data?.optJSONObject("payment")
                    val recipient = payment?.optString("recipient").orEmpty().ifBlank { "服务方" }
                    val amount = payment?.optString("amount")
                        .orEmpty()
                        .takeIf { it.isNotBlank() }
                        ?.let(::normalizeAmountText)
                        ?: "已确认金额"
                    "向 $recipient 支付：$amount。"
                }
                message.startsWith("Expense recorded", ignoreCase = true) -> {
                    val expense = data?.optJSONObject("expense")
                    val merchant = expense?.optString("merchant").orEmpty().ifBlank { "服务方" }
                    val amount = expense?.optString("amount")
                        .orEmpty()
                        .takeIf { it.isNotBlank() }
                        ?.let(::normalizeAmountText)
                        ?: "已确认金额"
                    "完成记账：$merchant $amount。"
                }
                else -> return null
            }
            return AgentTaskLog(
                id = nextId("task"),
                timeText = taskLogTimeFor(tool, content, eventIndex),
                text = text,
            )
        }

        private fun taskLogTimeFor(
            tool: String?,
            content: String,
            eventIndex: Int,
        ): String =
            blueprintTimeText(scenarioClock)

        private fun blueprintTimeText(clock: LocalDateTime): String =
            clock.format(BLUEPRINT_TIME_FORMATTER)

        private fun serviceTaskLogText(data: JSONObject?): String {
            val serviceId = data?.optString("serviceId").orEmpty()
            val action = data?.optString("action").orEmpty()
            val actionLower = action.lowercase()
            return when {
                serviceId == "pet_salon_search" && actionLower.contains("detail") -> {
                    val shopName = firstNamedEntity(data).ifBlank { "PetSmart" }
                    "添加 $shopName 到参与方。"
                }
                serviceId == "pet_salon_search" ->
                    "查询附近宠物店。"
                serviceId.isNotBlank() ->
                    "获取 $serviceId 服务信息。"
                else ->
                    "获取服务信息。"
            }
        }

        private fun contactNamesFromData(data: JSONObject?): String {
            val contacts = data?.optJSONArray("contacts") ?: return ""
            return buildList {
                for (index in 0 until contacts.length()) {
                    val contact = contacts.optJSONObject(index) ?: continue
                    val name = contact.optString("displayName")
                        .ifBlank { contact.optString("name") }
                    if (name.isNotBlank()) add(name)
                }
            }.distinct().joinToString("、")
        }

        private fun firstNamedEntity(value: Any?): String =
            when (value) {
                is JSONObject -> {
                    value.optString("displayName")
                        .ifBlank { value.optString("name") }
                        .ifBlank {
                            value.keys().asSequence()
                                .mapNotNull { key -> firstNamedEntity(value.opt(key)).takeIf { it.isNotBlank() } }
                                .firstOrNull()
                                .orEmpty()
                        }
                }
                is org.json.JSONArray -> {
                    (0 until value.length())
                        .asSequence()
                        .mapNotNull { index -> firstNamedEntity(value.opt(index)).takeIf { it.isNotBlank() } }
                        .firstOrNull()
                        .orEmpty()
                }
                else -> ""
            }

        private fun updateParticipantsFromTaskLogs(
            existing: List<AgentParticipant>,
            logs: List<AgentTaskLog>,
        ): List<AgentParticipant> =
            logs.fold(existing) { current, log ->
                val added = participantNamesFromAddLog(log.text)
                if (added.isEmpty()) {
                    val removed = participantNamesFromRemoveLog(log.text)
                    if (removed.isEmpty()) current else removeParticipants(current, removed)
                } else {
                    added.fold(current) { parties, name ->
                        upsertParticipant(parties, participantFromName(name))
                    }
                }
            }

        private fun participantNamesFromAddLog(text: String): List<String> {
            val match = Regex("""添加\s+(.+?)\s+到参与方""").find(text) ?: return emptyList()
            return splitParticipantNames(match.groupValues[1])
        }

        private fun participantNamesFromRemoveLog(text: String): List<String> {
            val direct = Regex("""(?:移除|踢出)\s+(.+?)\s+(?:出|从)?参与方""").find(text)
            val reverse = Regex("""从参与方(?:移除|踢出)\s+(.+?)(?:。|$)""").find(text)
            return splitParticipantNames(direct?.groupValues?.getOrNull(1) ?: reverse?.groupValues?.getOrNull(1).orEmpty())
        }

        private fun splitParticipantNames(raw: String): List<String> =
            raw
                .replace(" and ", "、", ignoreCase = true)
                .replace(",", "、")
                .split("、")
                .map { it.trim().trimEnd('。', '.', ';', '；') }
                .filter { it.isNotBlank() && !it.equals("Y", ignoreCase = true) && it != "Y" }

        private fun upsertParticipant(
            existing: List<AgentParticipant>,
            participant: AgentParticipant,
        ): List<AgentParticipant> {
            val withoutSameRole =
                if (participant.role == "grooming_service") {
                    existing.filterNot { it.role == participant.role }
                } else {
                    existing
                }
            val withoutSameId = withoutSameRole.filterNot { it.id == participant.id }
            return (withoutSameId + participant).takeLast(MAX_PARTICIPANTS)
        }

        private fun removeParticipants(
            existing: List<AgentParticipant>,
            names: List<String>,
        ): List<AgentParticipant> {
            val ids = names.map { participantFromName(it).id }.toSet()
            return existing.filterNot { it.id in ids || it.displayName in names }
        }

        private fun participantFromName(name: String): AgentParticipant {
            val trimmed = name.trim()
            val lower = trimmed.lowercase()
            val role = when {
                lower.contains("driver") || trimmed.contains("司机") -> "private_driver"
                lower.contains("pet") || lower.contains("salon") || trimmed.contains("宠物") || trimmed.contains("洗护") -> "grooming_service"
                else -> "service"
            }
            return AgentParticipant(
                id = "$role-${trimmed.lowercase().replace(Regex("""\s+"""), "-")}",
                label = participantLabel(trimmed),
                displayName = trimmed,
                role = role,
            )
        }

        private fun participantLabel(name: String): String {
            val lower = name.lowercase()
            return when {
                lower.contains("driver") || name.contains("司机") -> "D"
                lower.contains("petsmart") -> "PS"
                else -> {
                    val letters = name.filter { it.isLetterOrDigit() }
                    if (letters.isBlank()) name.take(1) else letters.take(2).uppercase()
                }
            }
        }

        private fun partyTaskLogsFromToolResult(
            tool: String,
            content: String,
            ok: Boolean,
            existing: List<AgentTaskLog>,
        ): List<AgentTaskLog> {
            if (!ok || tool !in setOf("system_send_sms", "system_wait_for_sms")) return emptyList()
            val data = parseToolData(content) ?: return emptyList()
            val sms = data.optJSONObject("sms") ?: return emptyList()
            val contact = sms.optString("displayName").ifBlank {
                sms.optString("to").ifBlank { sms.optString("from") }
            }
            val party = when {
                contact.equals("Driver", ignoreCase = true) -> "Driver"
                PetGroomingContacts.isGroomingShopContact(contact) -> PetGroomingContacts.shopNameIn(contact) ?: contact
                else -> return emptyList()
            }
            if (existing.any { it.text.contains("添加") && it.text.contains(party) && it.text.contains("参与方") }) {
                return emptyList()
            }
            return listOf(
                AgentTaskLog(
                    id = nextId("task"),
                    timeText = taskLogTimeFor(tool, content, existing.size),
                    text = "添加 $party 到参与方。",
                ),
            )
        }

        private fun notificationFromDeviceResult(content: String): AgentSystemNotification? {
            val message = content.substringBefore('\n').trim()
            val data = parseToolData(content) ?: return null
            val source = when {
                message.startsWith("Notification posted", ignoreCase = true) ->
                    data.optJSONObject("notification")
                message.startsWith("Long reminder created", ignoreCase = true) ||
                    message.startsWith("Reminder created", ignoreCase = true) ->
                    data.optJSONObject("reminder")
                else -> null
            } ?: return null
            val title = source.optString("title").ifBlank { "提醒" }
            val body = displayReminderBody(source.optString("body").ifBlank { title })
            val timeText = displayReminderTime(source.optString("scheduledFor")
                .ifBlank { source.optString("time") }
                .ifBlank { "Now" })
            return AgentSystemNotification(
                id = nextId("notice"),
                title = title,
                timeText = timeText,
                body = body,
            )
        }

        private fun displayReminderTime(raw: String): String {
            val value = raw.trim()
            val match = Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})""").find(value)
            return if (match != null) {
                val (_, month, day, hour, minute) = match.destructured
                "$month/$day $hour:$minute"
            } else {
                value
            }
        }

        private fun selectedGroomingShopName(): String =
            PetGroomingContacts.selectedShopName(useAlternative = selectedAppointmentIsAlternative())

        private fun displayReminderBody(raw: String): String {
            return PetGroomingContacts.displayDriverReminderBody(raw)
        }

        private fun applyPetGroomingTaskUpdate(
            update: com.mobilebot.scenarios.petgrooming.PetGroomingTaskUpdate,
            timeText: String,
            activate: Boolean = false,
        ) {
            updateTaskState(PetGroomingTaskSurface.TASK_ID, activate = activate) { task ->
                task.copy(
                    status = update.status.toAgentStatus(),
                    updatedTimeText = timeText,
                    subtitle = update.subtitle,
                    conversationItems = task.conversationItems + update.conversations.map { it.toConversationItem() },
                    taskLogs = appendTaskLogs(task.taskLogs, update.logs.toTaskLogs(timeText)),
                    participants = update.participants?.map { it.toAgentParticipant() } ?: task.participants,
                    progressLine = update.progress.toProgressLine(),
                    decisionPrompt = update.decision?.toDecisionPrompt(),
                    activeActionValue = update.activeActionValue,
                    timeline = task.timeline + update.timeline.map { it.toTimelineEvent() },
                    finalSummary = update.finalSummary ?: task.finalSummary,
                )
            }
        }

        private fun PetGroomingSurfaceStatus.toAgentStatus(): AgentTimelineStatus =
            when (this) {
                PetGroomingSurfaceStatus.RUNNING -> AgentTimelineStatus.RUNNING
                PetGroomingSurfaceStatus.DONE -> AgentTimelineStatus.DONE
                PetGroomingSurfaceStatus.BLOCKED -> AgentTimelineStatus.BLOCKED
            }

        private fun PetGroomingSurfaceConversation.toConversationItem(): AgentConversationItem =
            AgentConversationItem(
                id = nextId("conversation"),
                role = when (role) {
                    PetGroomingSurfaceRole.AGENT -> AgentConversationRole.AGENT
                    PetGroomingSurfaceRole.USER -> AgentConversationRole.USER
                },
                text = text,
            )

        private fun List<PetGroomingSurfaceLog>.toTaskLogs(timeText: String): List<AgentTaskLog> =
            map {
                AgentTaskLog(
                    id = nextId("task"),
                    timeText = timeText,
                    text = it.text,
                )
            }

        private fun PetGroomingSurfaceParticipant.toAgentParticipant(): AgentParticipant =
            AgentParticipant(
                id = id,
                label = label,
                displayName = displayName,
                role = role,
            )

        private fun PetGroomingSurfaceProgress.toProgressLine(): AgentProgressLine =
            AgentProgressLine(
                label = label,
                detail = detail,
                completed = completed,
                total = total,
            )

        private fun PetGroomingSurfaceDecision.toDecisionPrompt(): DecisionPrompt =
            DecisionPrompt(
                text = text,
                actions = actions.map {
                    ActionButton(
                        label = it.label,
                        value = "$SCRIPTED_ACTION_PREFIX${it.key}",
                    )
                },
            )

        private fun PetGroomingSurfaceTimeline.toTimelineEvent(): AgentTimelineEvent =
            AgentTimelineEvent(
                id = nextId("timeline"),
                title = title,
                detail = detail,
                status = status.toAgentStatus(),
            )

        private fun AgentExperienceFrame.withScenarioEventClock(
            tool: String,
            content: String,
        ): AgentExperienceFrame {
            return withClock(scenarioClock)
        }

        private fun tickScenarioClock() {
            if (deferredRetriggerInProgress) return
            if (clockMode == ScenarioClockMode.FastUntilNextEvent) {
                scenarioClock = scenarioClock.plusMinutes(1)
                _frame.update { it.withClock(scenarioClock) }
                handleDueTimelineEvents()
                return
            }
            normalClockElapsedMs += CLOCK_LOOP_INTERVAL_MS
            if (normalClockElapsedMs >= SCENARIO_CLOCK_TICK_MS) {
                normalClockElapsedMs = 0L
                scenarioClock = scenarioClock.plusMinutes(1)
                _frame.update { it.withClock(scenarioClock) }
                handleDueTimelineEvents()
            }
        }

        private fun handleDueTimelineEvents() {
            val due = timelineScript
                .filter { it.id !in deliveredTimelineEvents && !it.triggerAt.isAfter(scenarioClock) }
                .sortedBy { it.triggerAt }
            if (due.isEmpty()) return
            for (event in due) {
                deliveredTimelineEvents += event.id
                viewModelScope.launch {
                    systemRuntime.publishEvent(event.toSystemRuntimeEvent())
                }
            }
            if (clockMode == ScenarioClockMode.FastUntilNextEvent) {
                clockMode = ScenarioClockMode.Live
                normalClockElapsedMs = 0L
                _frame.update { it.copy(clockMode = clockMode) }
            }
        }

        private fun handleSystemRuntimeEvent(event: SystemRuntimeEvent) {
            _frame.update {
                it.copy(
                    recentSystemEvents = appendSystemEvent(it.recentSystemEvents, event),
                    debugTrace = appendTrace(it.debugTrace, "system event -> ${event.id}"),
                )
            }
            when (event) {
                is IncomingSmsEvent -> handleIncomingSmsEvent(event)
                is IncomingCallEvent -> handleIncomingCallEvent(event)
                is CallEndedEvent -> handleCallEndedEvent(event)
                is ReminderFiredEvent -> handleReminderEvent(event)
                is RuntimeNotificationEvent -> handleRuntimeNotificationEvent(event)
                else -> Unit
            }
        }

        private fun handleIncomingSmsEvent(event: IncomingSmsEvent) {
            when (event.id) {
                "petsmart-open-slot" -> createPetGroomingTask(event)
                "driver-1320-confirm" -> handleDriverPickupConfirmation(event)
                "ella-shopping-followup" -> handleEllaShoppingFollowup(event)
                "property-courier-help" -> handlePropertyCourierHelp(event)
                "driver-kylin-picked-up" -> handleDriverPickedUpKylin(event)
                "driver-arrived-petsmart" -> handleDriverArrivedPetSmart(event)
                "petsmart-service-started" -> handlePetSmartServiceStarted(event)
                "ella-shopping-clarify" -> handleEllaShoppingClarify(event)
                "petsmart-service-progress" -> handlePetSmartServiceProgress(event)
            }
        }

        private fun createPetGroomingTask(event: IncomingSmsEvent) {
            val seed = PetGroomingTaskSurface.openSlotSeed(event.body)
            val task = AgentTaskState(
                id = PetGroomingTaskSurface.TASK_ID,
                title = seed.title,
                subtitle = seed.subtitle,
                status = seed.status.toAgentStatus(),
                updatedTimeText = blueprintTimeText(scenarioClock),
                conversationItems = seed.conversations.map { it.toConversationItem() },
                taskLogs = seed.logs.toTaskLogs(blueprintTimeText(scenarioClock)),
                participants = seed.participants.map { it.toAgentParticipant() },
                progressLine = seed.progress.toProgressLine(),
                decisionPrompt = seed.decision?.toDecisionPrompt(),
                timeline = seed.timeline.map { it.toTimelineEvent() },
            )
            upsertTask(task)
        }

        private fun handleLocalScenarioDecision(
            displayText: String,
            rawText: String,
            selectedActionValue: String?,
        ) {
            val prompt = _frame.value.decisionPrompt ?: return
            if (selectedActionValue != null) {
                pendingSelectedActionLabel = displayText
            }
            _frame.update {
                it.copy(
                    statusLabel = "理解中",
                    busy = true,
                    decisionPrompt = if (selectedActionValue == null) null else it.decisionPrompt,
                    activeActionValue = selectedActionValue,
                    conversationItems = it.conversationItems,
                    progressLine = it.progressLine.copy(
                        label = "理解中",
                        detail = displayText,
                    ),
                    debugTrace = appendTrace(it.debugTrace, "local decision -> ${rawText.take(160)}"),
                )
            }
            viewModelScope.launch {
                val normalized = decisionIntentNormalizer.normalize(
                    AgentDecisionInput(
                        contextId = scenario.scenarioId,
                        promptText = prompt.text,
                        presentedActions = prompt.actions.map { it.toAgentDecisionAction() },
                        candidateIntents = PetGroomingDecisionIntents.forScenario(scenario.scenarioId),
                        displayText = displayText,
                        rawText = rawText,
                    ),
                )
                _frame.update {
                    it.copy(
                        debugTrace = appendTrace(
                            it.debugTrace,
                            "local intent -> ${normalized.intent.id}${if (normalized.usedFallback) " (fallback)" else ""}",
                        ),
                    )
                }
                // LLM 只负责识别用户意图，具体执行仍走稳定的场景动作。
                when (normalized.intent) {
                    PetGroomingDecisionIntents.AcceptOpenSlot -> acceptPetSmartOpenSlot(displayText)
                    PetGroomingDecisionIntents.KeepOriginalSlot -> keepOriginalPetSmartSlot(displayText)
                    else -> askOpenSlotClarification(displayText)
                }
            }
        }

        private fun askOpenSlotClarification(userText: String) {
            val (conversations, decision) = PetGroomingTaskSurface.openSlotClarification(userText)
            val prompt = decision.toDecisionPrompt()
            _frame.update {
                it.copy(
                    busy = false,
                    statusLabel = "等待",
                    decisionPrompt = prompt,
                    activeActionValue = null,
                    conversationItems = it.conversationItems + conversations.map { item -> item.toConversationItem() },
                    progressLine = AgentProgressLine(
                        label = "等待",
                        detail = "等待用户决策",
                        completed = it.progressLine.completed,
                        total = it.progressLine.total,
                    ),
                    debugTrace = appendTrace(it.debugTrace, "local decision clarification -> $userText"),
                )
            }
        }

        private fun acceptPetSmartOpenSlot(label: String) {
            pendingSelectedActionLabel = null
            val frame = _frame.value
            val task = taskStates[PetGroomingTaskSurface.TASK_ID] ?: return
            val surface = PetGroomingTaskSurface.acceptOpenSlot(label)
            val updated = task.copy(
                status = surface.status.toAgentStatus(),
                updatedTimeText = blueprintTimeText(scenarioClock),
                subtitle = surface.subtitle,
                conversationItems = frame.conversationItems + surface.conversations.map { it.toConversationItem() },
                taskLogs = appendTaskLogs(frame.taskLogs, surface.logs.toTaskLogs(blueprintTimeText(scenarioClock))),
                participants = surface.participants?.map { it.toAgentParticipant() } ?: task.participants,
                progressLine = surface.progress.toProgressLine(),
                decisionPrompt = surface.decision?.toDecisionPrompt(),
                activeActionValue = surface.activeActionValue,
                timeline = frame.timeline + surface.timeline.map { it.toTimelineEvent() },
                finalSummary = surface.finalSummary,
            )
            upsertTask(updated)
            petGroomingAccepted = true
        }

        private fun keepOriginalPetSmartSlot(label: String) {
            pendingSelectedActionLabel = null
            val frame = _frame.value
            val task = taskStates[PetGroomingTaskSurface.TASK_ID] ?: return
            val surface = PetGroomingTaskSurface.keepOriginalSlot(label)
            val updated = task.copy(
                status = surface.status.toAgentStatus(),
                updatedTimeText = blueprintTimeText(scenarioClock),
                subtitle = surface.subtitle,
                conversationItems = frame.conversationItems + surface.conversations.map { it.toConversationItem() },
                taskLogs = appendTaskLogs(frame.taskLogs, surface.logs.toTaskLogs(blueprintTimeText(scenarioClock))),
                participants = surface.participants?.map { it.toAgentParticipant() } ?: task.participants,
                progressLine = surface.progress.toProgressLine(),
                decisionPrompt = surface.decision?.toDecisionPrompt(),
                activeActionValue = surface.activeActionValue,
                timeline = frame.timeline + surface.timeline.map { it.toTimelineEvent() },
                finalSummary = surface.finalSummary,
            )
            upsertTask(updated)
        }

        private fun handleDriverPickupConfirmation(event: IncomingSmsEvent) {
            if (!petGroomingAccepted) return
            applyPetGroomingTaskUpdate(
                update = PetGroomingTaskSurface.driverPickupConfirmation(event.body),
                timeText = blueprintTimeText(scenarioClock),
            )
        }

        private fun handleEllaShoppingFollowup(event: IncomingSmsEvent) {
            updateTaskState(FAMILY_TASK_ID, activate = false) { task ->
                task.copy(
                    updatedTimeText = blueprintTimeText(event.occurredAt),
                    subtitle = "采购优先级已更新",
                    conversationItems = appendConversation(
                        task.conversationItems,
                        AgentConversationRole.AGENT,
                        "Ella 又补充了采购优先级：低脂牛奶和洗衣液优先，水果顺路再买。我已经同步到任务里。",
                    ),
                    taskLogs = appendTaskLogs(
                        task.taskLogs,
                        listOf(
                            AgentTaskLog(
                                id = nextId("task"),
                                timeText = blueprintTimeText(event.occurredAt),
                                text = "收到 Ella 的短信：${event.body}",
                            ),
                            AgentTaskLog(
                                id = nextId("task"),
                                timeText = blueprintTimeText(event.occurredAt),
                                text = "更新采购优先级：低脂牛奶、洗衣液优先，水果可选。",
                            ),
                        ),
                    ),
                    progressLine = AgentProgressLine(
                        label = "进行中",
                        detail = "匹配采购方案",
                        completed = 2,
                        total = 4,
                    ),
                )
            }
        }

        private fun handleDriverPickedUpKylin(event: IncomingSmsEvent) {
            if (!petGroomingAccepted) return
            applyPetGroomingTaskUpdate(
                update = PetGroomingTaskSurface.driverPickedUpKylin(event.body),
                timeText = blueprintTimeText(event.occurredAt),
            )
        }

        private fun handleRuntimeNotificationEvent(event: RuntimeNotificationEvent) {
            when (event.id) {
                "property-parking-notice" -> handlePropertyParkingNotice(event)
                "pharmacy-restock" -> handlePharmacyRestock(event)
                "market-delivery-window" -> handleMarketDeliveryWindow(event)
                "courier-coldchain-arriving" -> handleCourierColdchainArriving(event)
                "courier-coldchain-delivered" -> handleCourierColdchainDelivered(event)
                "health-supply-candidate" -> handleHealthSupplyCandidate(event)
            }
        }

        private fun handlePropertyParkingNotice(event: RuntimeNotificationEvent) {
            updateTaskState(PET_TASK_ID, activate = false) { task ->
                task.copy(
                    updatedTimeText = blueprintTimeText(event.occurredAt),
                    subtitle = "司机改走东门接送",
                    taskLogs = appendTaskLogs(
                        task.taskLogs,
                        listOf(
                            AgentTaskLog(
                                id = nextId("task"),
                                timeText = blueprintTimeText(event.occurredAt),
                                text = "收到物业通知：${event.body}",
                            ),
                            AgentTaskLog(
                                id = nextId("task"),
                                timeText = blueprintTimeText(event.occurredAt),
                                text = "更新司机路线：优先走东门，避开 B2 西侧临停区。",
                            ),
                        ),
                    ),
                    participants = task.participants.withParticipant(
                        AgentParticipant("property-service", "物", "物业管家", "property_service"),
                    ),
                    progressLine = AgentProgressLine(
                        label = "进行中",
                        detail = "接送路线已更新",
                        completed = task.progressLine.completed,
                        total = task.progressLine.total,
                    ),
                )
            }
        }

        private fun handlePharmacyRestock(event: RuntimeNotificationEvent) {
            val task = AgentTaskState(
                id = HEALTH_TASK_ID,
                title = "健康补给",
                subtitle = "常用品补货候选",
                status = AgentTimelineStatus.RUNNING,
                updatedTimeText = blueprintTimeText(event.occurredAt),
                conversationItems = listOf(
                    AgentConversationItem(
                        id = nextId("conversation"),
                        role = AgentConversationRole.AGENT,
                        text = "常买的益生菌补货了，我先放进健康补给任务里，不打断你。",
                    ),
                ),
                taskLogs = listOf(
                    AgentTaskLog(
                        id = nextId("task"),
                        timeText = blueprintTimeText(event.occurredAt),
                        text = "收到美团买药通知：${event.body}",
                    ),
                    AgentTaskLog(
                        id = nextId("task"),
                        timeText = blueprintTimeText(event.occurredAt),
                        text = "新建健康补给任务：益生菌补货候选。",
                    ),
                ),
                participants = listOf(
                    AgentParticipant("pharmacy-service", "药", "美团买药", "pharmacy_service"),
                ),
                progressLine = AgentProgressLine(
                    label = "进行中",
                    detail = "等待是否合并配送",
                    completed = 1,
                    total = 3,
                ),
            )
            upsertTask(task)
        }

        private fun handleMarketDeliveryWindow(event: RuntimeNotificationEvent) {
            updateTaskState(FAMILY_TASK_ID, activate = false) { task ->
                task.copy(
                    updatedTimeText = blueprintTimeText(event.occurredAt),
                    subtitle = "已找到可配送渠道",
                    conversationItems = appendConversation(
                        task.conversationItems,
                        AgentConversationRole.AGENT,
                        "附近超市有低脂牛奶和常用洗衣液，45 分钟内可送达。我先把它作为家庭采购候选，不打断你。",
                    ),
                    taskLogs = appendTaskLogs(
                        task.taskLogs,
                        listOf(
                            AgentTaskLog(
                                id = nextId("task"),
                                timeText = blueprintTimeText(event.occurredAt),
                                text = "收到 Ole 通知：${event.body}",
                            ),
                            AgentTaskLog(
                                id = nextId("task"),
                                timeText = blueprintTimeText(event.occurredAt),
                                text = "加入采购候选：低脂牛奶、常用洗衣液，预计 45 分钟内送达。",
                            ),
                        ),
                    ),
                    participants = task.participants.withParticipant(
                        AgentParticipant("ole", "O", "Ole", "market"),
                    ),
                    progressLine = AgentProgressLine(
                        label = "进行中",
                        detail = "等待清单确认",
                        completed = 3,
                        total = 4,
                    ),
                )
            }
        }

        private fun handleCourierColdchainArriving(event: RuntimeNotificationEvent) {
            val task = AgentTaskState(
                id = PACKAGE_TASK_ID,
                title = "冷链收货",
                subtitle = "13:45 到达小区",
                status = AgentTimelineStatus.RUNNING,
                updatedTimeText = blueprintTimeText(event.occurredAt),
                conversationItems = listOf(
                    AgentConversationItem(
                        id = nextId("conversation"),
                        role = AgentConversationRole.AGENT,
                        text = "顺丰冷链预计 13:45 到小区，我会跟进是否需要物业代收。",
                    ),
                ),
                taskLogs = listOf(
                    AgentTaskLog(
                        id = nextId("task"),
                        timeText = blueprintTimeText(event.occurredAt),
                        text = "收到顺丰冷链通知：${event.body}",
                    ),
                    AgentTaskLog(
                        id = nextId("task"),
                        timeText = blueprintTimeText(event.occurredAt),
                        text = "新建冷链收货任务，关注是否需要及时入冰柜。",
                    ),
                ),
                participants = listOf(
                    AgentParticipant("courier-coldchain", "顺", "顺丰冷链", "delivery_service"),
                ),
                progressLine = AgentProgressLine(
                    label = "进行中",
                    detail = "等待包裹到达",
                    completed = 1,
                    total = 3,
                ),
            )
            upsertTask(task)
        }

        private fun handleCourierColdchainDelivered(event: RuntimeNotificationEvent) {
            updateTaskState(PACKAGE_TASK_ID, activate = false) { task ->
                task.copy(
                    updatedTimeText = blueprintTimeText(event.occurredAt),
                    subtitle = "已放入前台冰柜",
                    conversationItems = appendConversation(
                        task.conversationItems,
                        AgentConversationRole.AGENT,
                        "冷链包裹已经到前台冰柜了，我继续看物业是否能帮忙保管到你方便再取。",
                    ),
                    taskLogs = appendTaskLogs(
                        task.taskLogs,
                        listOf(
                            AgentTaskLog(
                                id = nextId("task"),
                                timeText = blueprintTimeText(event.occurredAt),
                                text = "收到顺丰冷链通知：${event.body}",
                            ),
                        ),
                    ),
                    progressLine = AgentProgressLine(
                        label = "进行中",
                        detail = "等待物业确认保管",
                        completed = 2,
                        total = 3,
                    ),
                )
            }
        }

        private fun handlePropertyCourierHelp(event: IncomingSmsEvent) {
            updateTaskState(PACKAGE_TASK_ID, activate = false) { task ->
                task.copy(
                    status = AgentTimelineStatus.DONE,
                    updatedTimeText = blueprintTimeText(event.occurredAt),
                    subtitle = "物业已协助保管",
                    conversationItems = appendConversation(
                        task.conversationItems,
                        AgentConversationRole.AGENT,
                        "物业可以先把冷链包裹放进前台冰柜，这条线我已经处理好了。",
                    ),
                    taskLogs = appendTaskLogs(
                        task.taskLogs,
                        listOf(
                            AgentTaskLog(
                                id = nextId("task"),
                                timeText = blueprintTimeText(event.occurredAt),
                                text = "收到物业管家的短信：${event.body}",
                            ),
                            AgentTaskLog(
                                id = nextId("task"),
                                timeText = blueprintTimeText(event.occurredAt),
                                text = "更新状态：冷链包裹由物业前台冰柜保管。",
                            ),
                        ),
                    ),
                    participants = task.participants.withParticipant(
                        AgentParticipant("property-service", "物", "物业管家", "property_service"),
                    ),
                    progressLine = AgentProgressLine(
                        label = "完成",
                        detail = "物业已协助保管",
                        completed = 3,
                        total = 3,
                    ),
                )
            }
        }

        private fun handleDriverArrivedPetSmart(event: IncomingSmsEvent) {
            if (!petGroomingAccepted) return
            applyPetGroomingTaskUpdate(
                update = PetGroomingTaskSurface.driverArrivedPetSmart(event.body),
                timeText = blueprintTimeText(event.occurredAt),
            )
        }

        private fun handlePetSmartServiceStarted(event: IncomingSmsEvent) {
            if (!petGroomingAccepted) return
            applyPetGroomingTaskUpdate(
                update = PetGroomingTaskSurface.serviceStarted(event.body),
                timeText = blueprintTimeText(event.occurredAt),
            )
        }

        private fun handleEllaShoppingClarify(event: IncomingSmsEvent) {
            updateTaskState(FAMILY_TASK_ID, activate = false) { task ->
                task.copy(
                    updatedTimeText = blueprintTimeText(event.occurredAt),
                    subtitle = "采购清单已收敛",
                    conversationItems = appendConversation(
                        task.conversationItems,
                        AgentConversationRole.AGENT,
                        "Ella 又调整了清单：洗衣液买常用款，猫粮不用买。我已经把候选清单收敛好了。",
                    ),
                    taskLogs = appendTaskLogs(
                        task.taskLogs,
                        listOf(
                            AgentTaskLog(
                                id = nextId("task"),
                                timeText = blueprintTimeText(event.occurredAt),
                                text = "收到 Ella 的短信：${event.body}",
                            ),
                            AgentTaskLog(
                                id = nextId("task"),
                                timeText = blueprintTimeText(event.occurredAt),
                                text = "更新采购清单：保留低脂牛奶、常用洗衣液；移除猫粮。",
                            ),
                        ),
                    ),
                    progressLine = AgentProgressLine(
                        label = "进行中",
                        detail = "清单已收敛",
                        completed = 4,
                        total = 4,
                    ),
                )
            }
        }

        private fun handlePetSmartServiceProgress(event: IncomingSmsEvent) {
            if (!petGroomingAccepted) return
            applyPetGroomingTaskUpdate(
                update = PetGroomingTaskSurface.serviceProgress(event.body),
                timeText = blueprintTimeText(event.occurredAt),
            )
        }

        private fun handleHealthSupplyCandidate(event: RuntimeNotificationEvent) {
            updateTaskState(HEALTH_TASK_ID, activate = false) { task ->
                task.copy(
                    updatedTimeText = blueprintTimeText(event.occurredAt),
                    subtitle = "可与维生素合并配送",
                    conversationItems = appendConversation(
                        task.conversationItems,
                        AgentConversationRole.AGENT,
                        "健康补给可以和维生素 D 一起配送，我先保留候选，等你有空再确认是否下单。",
                    ),
                    taskLogs = appendTaskLogs(
                        task.taskLogs,
                        listOf(
                            AgentTaskLog(
                                id = nextId("task"),
                                timeText = blueprintTimeText(event.occurredAt),
                                text = "收到美团买药通知：${event.body}",
                            ),
                            AgentTaskLog(
                                id = nextId("task"),
                                timeText = blueprintTimeText(event.occurredAt),
                                text = "更新健康补给候选：益生菌和维生素 D 可合并配送。",
                            ),
                        ),
                    ),
                    progressLine = AgentProgressLine(
                        label = "等待",
                        detail = "低优先级，稍后再问",
                        completed = 2,
                        total = 3,
                    ),
                )
            }
        }

        private fun handleIncomingCallEvent(event: IncomingCallEvent) {
            _frame.update {
                it.copy(
                    systemNotification = AgentSystemNotification(
                        id = event.id,
                        title = "Ella 来电",
                        timeText = blueprintTimeText(event.occurredAt),
                        body = "正在接入通话转写。",
                        actionLabel = "接听",
                    ),
                )
            }
        }

        private fun handleCallEndedEvent(event: CallEndedEvent) {
            endedCallNotificationIds += event.id.removeSuffix("-ended")
            endedCallNotificationIds += event.id
            val task = AgentTaskState(
                id = FAMILY_TASK_ID,
                title = "家庭采购",
                subtitle = "Ella 电话交代的待办",
                status = AgentTimelineStatus.RUNNING,
                updatedTimeText = blueprintTimeText(scenarioClock),
                conversationItems = listOf(
                    AgentConversationItem(
                        id = nextId("conversation"),
                        role = AgentConversationRole.AGENT,
                        text = "刚才 Ella 电话里提到周末家庭采购，我已经帮你建立任务跟踪，会继续整理需要确认的事项。",
                    ),
                ),
                taskLogs = listOf(
                    AgentTaskLog(
                        id = nextId("task"),
                        timeText = blueprintTimeText(scenarioClock),
                        text = "通话转写完成：识别到 Ella 交代的家庭采购待办。",
                    ),
                    AgentTaskLog(
                        id = nextId("task"),
                        timeText = blueprintTimeText(scenarioClock),
                        text = "新建家庭采购任务。",
                    ),
                ),
                participants = listOf(
                    AgentParticipant("ella", "E", "Ella", "family"),
                ),
                progressLine = AgentProgressLine(
                    label = "进行中",
                    detail = "整理待办事项",
                    completed = 1,
                    total = 4,
                ),
            )
            _frame.update {
                it.copy(
                    activeCall = null,
                    systemNotification = if (it.systemNotification?.id in endedCallNotificationIds) {
                        null
                    } else {
                        it.systemNotification
                    },
                )
            }
            upsertTask(task)
        }

        private fun handleReminderEvent(event: ReminderFiredEvent) {
            _frame.update {
                it.copy(
                    systemNotification = AgentSystemNotification(
                        id = event.id,
                        title = event.title,
                        timeText = blueprintTimeText(event.occurredAt),
                        body = event.body,
                        actionLabel = "OK",
                    ),
                )
            }
            applyPetGroomingTaskUpdate(
                update = PetGroomingTaskSurface.departureReminderFired(),
                timeText = blueprintTimeText(scenarioClock),
            )
        }

        private fun upsertTask(task: AgentTaskState) {
            taskStates[_frame.value.activeTaskId]?.let {
                taskStates[it.id] = _frame.value.captureTaskState(it)
            }
            val updatedTask = task.copy(sortKey = nextTaskSortKey())
            taskStates[updatedTask.id] = updatedTask
            _frame.update { updatedTask.applyToFrame(it) }
        }

        private fun updateTaskState(
            taskId: String,
            activate: Boolean,
            transform: (AgentTaskState) -> AgentTaskState,
        ) {
            val existing = taskStates[taskId] ?: return
            val updated = transform(existing).copy(sortKey = nextTaskSortKey())
            taskStates[taskId] = updated
            if (activate || _frame.value.activeTaskId == taskId) {
                _frame.update { updated.applyToFrame(it) }
            } else {
                _frame.update { frame ->
                    frame.copy(taskCards = taskCardsFor(activeId = frame.activeTaskId))
                }
            }
        }

        private fun AgentExperienceFrame.captureTaskState(previous: AgentTaskState): AgentTaskState =
            previous.copy(
                conversationItems = conversationItems,
                taskLogs = taskLogs,
                participants = participants,
                progressLine = progressLine,
                timeline = timeline,
                stageCards = stageCards,
                decisionPrompt = decisionPrompt,
                activeActionValue = activeActionValue,
                finalSummary = finalSummary,
                error = error,
            )

        private fun AgentTaskState.applyToFrame(frame: AgentExperienceFrame): AgentExperienceFrame =
            frame.copy(
                activeTaskId = id,
                activeTaskTitle = title,
                activeTaskSubtitle = subtitle,
                taskCards = taskCardsFor(activeId = id),
                statusLabel = when (status) {
                    AgentTimelineStatus.BLOCKED -> "Waiting for decision"
                    AgentTimelineStatus.DONE -> "Complete"
                    AgentTimelineStatus.FAILED -> "Error"
                    else -> "Running"
                },
                hasStarted = true,
                conversationItems = conversationItems,
                taskLogs = taskLogs,
                participants = participants,
                progressLine = progressLine,
                timeline = timeline,
                stageCards = stageCards,
                decisionPrompt = decisionPrompt,
                activeActionValue = activeActionValue,
                finalSummary = finalSummary,
                error = error,
            )

        private fun taskCardsFor(activeId: String?): List<AgentTaskCard> =
            taskStates.values
                .map { it.toCard(activeId = activeId) }
                .sortedWith(
                    compareByDescending<AgentTaskCard> { it.isPinned }
                        .thenByDescending { it.sortKey },
                )

        private fun AgentTaskState.toCard(activeId: String?): AgentTaskCard =
            AgentTaskCard(
                id = id,
                title = title,
                subtitle = subtitle,
                status = status,
                updatedTimeText = updatedTimeText,
                sortKey = sortKey,
                isActive = id == activeId,
                isPinned = id in pinnedTaskIds,
            )

        private fun nextTaskSortKey(): Long {
            taskSortCounter += 1
            return taskSortCounter
        }

        private fun List<AgentParticipant>.withParticipant(participant: AgentParticipant): List<AgentParticipant> =
            if (any { it.id == participant.id }) this else this + participant

        private fun appendSystemEvent(
            existing: List<AgentSystemEvent>,
            event: SystemRuntimeEvent,
        ): List<AgentSystemEvent> =
            (
                existing + AgentSystemEvent(
                    id = event.id,
                    timeText = blueprintTimeText(event.occurredAt),
                    source = event.source,
                    title = event.title,
                    body = event.body,
                )
            ).takeLast(MAX_SYSTEM_EVENTS)

        private fun SystemRuntimeScriptEvent.toScenarioTimelineEvent(dayStart: LocalDateTime): ScenarioTimelineEvent? {
            val timeValue = runCatching { LocalTime.parse(time, CLOCK_TIME_FORMATTER) }.getOrNull() ?: return null
            return ScenarioTimelineEvent(
                id = id,
                triggerAt = dayStart.toLocalDate().atTime(timeValue),
                type = type,
                source = source,
                title = title,
                body = body,
            )
        }

        private fun ScenarioTimelineEvent.toSystemRuntimeEvent(): SystemRuntimeEvent =
            when (type) {
                "incoming_sms" -> IncomingSmsEvent(
                    id = id,
                    occurredAt = triggerAt,
                    source = source,
                    title = title,
                    body = body,
                    from = source,
                )
                "incoming_call" -> IncomingCallEvent(
                    id = id,
                    occurredAt = triggerAt,
                    source = source,
                    title = title,
                    body = body,
                    contact = source,
                )
                "call_ended" -> CallEndedEvent(
                    id = id,
                    occurredAt = triggerAt,
                    source = source,
                    title = title,
                    body = body,
                    contact = source,
                    audioRef = id,
                )
                "reminder_fired" -> ReminderFiredEvent(
                    id = id,
                    occurredAt = triggerAt,
                    source = source,
                    title = title,
                    body = body,
                    reminderId = id,
                )
                else -> com.mobilebot.systemruntime.RuntimeNotificationEvent(
                    id = id,
                    occurredAt = triggerAt,
                    source = source,
                    title = title,
                    body = body,
                )
            }

        private fun selectedAppointmentIsAfternoon(): Boolean =
            latestAgentDecisionIntent == PetGroomingDecisionIntents.BookAfternoonBathOnly

        private fun selectedAppointmentIsAlternative(): Boolean =
            latestAgentDecisionIntent == PetGroomingDecisionIntents.FindAlternative

        private fun parseToolData(content: String): JSONObject? {
            val json = content.substringAfter('\n', missingDelimiterValue = "").trim()
            if (json.isBlank()) return null
            return runCatching { JSONObject(json) }.getOrNull()
        }

        private fun recordGroomingMilestones(
            tool: String,
            content: String,
        ) {
            if (scenario.scenarioId != "pet-grooming") return
            if (!isSystemRuntimeTool(tool)) return
            val data = parseToolData(content) ?: return
            val update = PetGroomingMilestoneDetector.fromSystemRuntimeData(data)
            groomingMilestones += update.milestones
            lastGroomingPaymentAmount = update.paymentAmount ?: lastGroomingPaymentAmount
        }

        private fun parseActionButtons(json: String?, promptText: String): List<ActionButton> {
            val explicit =
                try {
                    ActionPromptCodec.parseJson(json).map {
                        ActionButton(label = compactActionLabel(it.label, it.value), value = it.value)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse action buttons", e)
                    emptyList()
                }
            if (explicit.isNotEmpty()) return explicit
            val scenarioActions = inferScenarioActions(promptText)
            if (scenarioActions.isNotEmpty()) return scenarioActions
            val inferred = ActionPromptCodec.resolveOptions(promptText).map {
                ActionButton(label = compactActionLabel(it.label, it.value), value = it.value)
            }
            return inferred.ifEmpty {
                listOf(
                    ActionButton("继续", "USER_INTENT:general.continue"),
                    ActionButton("修改计划", "USER_INTENT:general.modify_plan"),
                    ActionButton("重写", "USER_INTENT:general.rewrite_plan"),
                    ActionButton("取消", "USER_INTENT:general.cancel"),
                )
            }
        }

        private fun inferScenarioActions(promptText: String): List<ActionButton> {
            if (scenario.scenarioId != "pet-grooming") return emptyList()
            return PetGroomingConversationRules.actionCandidates(promptText)
                .map { ActionButton(label = it.label, value = it.value) }
        }

        private fun shouldSuppressResolvedGroomingPrompt(text: String): Boolean {
            if (scenario.scenarioId != "pet-grooming") return false
            return PetGroomingConversationRules.shouldSuppressResolvedPrompt(text, latestAgentDecisionIntent)
        }

        private fun compactActionLabel(
            label: String,
            value: String,
        ): String {
            if (scenario.scenarioId != "pet-grooming") return label
            return PetGroomingConversationRules.compactActionLabel(label, value)
        }

        private fun inferDecisionPrompt(content: String): DecisionPrompt? {
            val text = content.trim()
            val scenarioActions = inferScenarioActions(text)
            if (scenarioActions.isEmpty() && !looksLikeDecisionRequest(text)) return null
            val inlineActions = parseInlineActionButtons(text)
            return DecisionPrompt(
                text = compactDecisionPromptText(text),
                actions = scenarioActions.ifEmpty { inlineActions.ifEmpty { parseActionButtons(null, text) } },
            )
        }

        private fun compactDecisionPromptText(text: String): String {
            if (scenario.scenarioId == "pet-grooming") {
                return PetGroomingConversationRules.compactDecisionPromptText(text) ?: text
            }
            return text
        }

        private fun looksLikeDecisionRequest(text: String): Boolean {
            val lower = text.lowercase()
            if (
                lower.contains("proceed automatically") ||
                lower.contains("i'll proceed automatically") ||
                lower.contains("i’ll proceed automatically")
            ) return false
            return lower.contains("would you like") ||
                lower.contains("which option") ||
                lower.contains("please choose") ||
                lower.contains("need to ask for your confirmation") ||
                lower.contains("need your confirmation") ||
                (lower.contains("proceed") && lower.contains("cancel")) ||
                text.contains("请选择") ||
                text.contains("继续吗") ||
                text.contains("要继续吗") ||
                text.contains("需要你确认") ||
                text.contains("是否继续") ||
                text.contains("是否照常")
        }

        private fun parseInlineActionButtons(text: String): List<ActionButton> =
            text
                .lineSequence()
                .mapNotNull { line ->
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("-") && !trimmed.startsWith("*") && !trimmed.startsWith("•")) {
                        return@mapNotNull null
                    }
                    val label = compactInlineActionLabel(stripLeadingActionIcon(trimmed.drop(1).trim()))
                    if (label.isBlank()) null else ActionButton(label = label, value = label)
                }
                .take(4)
                .toList()

        private fun compactInlineActionLabel(value: String): String {
            if (scenario.scenarioId == "pet-grooming" && PetGroomingConversationRules.isNonActionFact(value)) return ""
            val timeRange = Regex("""^\d{1,2}:\d{2}\s*[–-]\s*\d{1,2}:\d{2}""").find(value)?.value
            return timeRange ?: value
        }

        private fun stripLeadingActionIcon(value: String): String =
            ActionPromptCodec.cleanOptionText(
                value
                    .dropWhile { char ->
                        !char.isLetterOrDigit() &&
                            Character.UnicodeScript.of(char.code) != Character.UnicodeScript.HAN
                    },
            )
                .trim()

        private fun buildDebugLine(runtime: String, tool: String, content: String): String {
            val type = runtime.ifBlank { "assistant" }
            val suffix = if (tool.isNotBlank()) " [$tool]" else ""
            return "$type$suffix -> ${content.take(240)}"
        }

        private fun appendSignal(existing: List<String>, value: String): List<String> =
            (existing.filterNot { it == value } + value).takeLast(MAX_SIGNALS)

        private fun isSystemRuntimeTool(tool: String): Boolean =
            tool == "device_system" ||
                tool == "system_search_contacts" ||
                tool == "system_send_sms" ||
                tool == "system_wait_for_sms"

        private fun appendConversation(
            existing: List<AgentConversationItem>,
            role: AgentConversationRole,
            text: String,
        ): List<AgentConversationItem> {
            if (text.isBlank()) return existing
            return (existing + AgentConversationItem(nextId("conversation"), role, text)).takeLast(MAX_CONVERSATION_ITEMS)
        }

        private fun AgentExperienceFrame.withPendingSelectedAction(): AgentExperienceFrame {
            val label = pendingSelectedActionLabel?.takeIf { it.isNotBlank() } ?: return this
            pendingSelectedActionLabel = null
            return copy(
                conversationItems = appendConversation(
                    conversationItems,
                    AgentConversationRole.USER,
                    label,
                ),
            )
        }

        private fun appendTaskLogs(
            existing: List<AgentTaskLog>,
            values: List<AgentTaskLog>,
        ): List<AgentTaskLog> {
            if (values.isEmpty()) return existing
            return (existing + values).takeLast(MAX_TASK_LOGS)
        }

        private fun appendTrace(existing: List<String>, value: String): List<String> =
            (existing + value).takeLast(MAX_TRACE_LINES)

        private fun completedStageCount(frame: AgentExperienceFrame): Int =
            frame.stageCards.count { it.status == AgentTimelineStatus.DONE }

        private fun totalStageCount(frame: AgentExperienceFrame): Int =
            frame.stageCards.size.coerceAtLeast(1)

        private fun nextId(prefix: String): String {
            eventCounter += 1
            return "$prefix-$eventCounter"
        }

        private companion object {
            private const val TAG = "AgentExperienceViewModel"
            private const val MAX_TRACE_LINES = 80
            private const val MAX_SIGNALS = 12
            private const val MAX_SYSTEM_EVENTS = 14
            private const val MAX_PARTICIPANTS = 5
            private const val MAX_CONVERSATION_ITEMS = 40
            private const val MAX_TASK_LOGS = 80
            private const val MAX_VISIBLE_ASSISTANT_UPDATE_CHARS = 500
            private const val MAX_AUTO_CONTINUATIONS = 14
            private const val AUTO_TRIGGER_DELAY_MS = 5_000L
            private const val SCENARIO_CLOCK_TICK_MS = 60_000L
            private const val CLOCK_LOOP_INTERVAL_MS = 1_000L
            private const val CALL_CONNECTED_DISPLAY_MS = 3_000L
            private const val CLOCK_ADVANCE_STEPS = 30
            private const val CLOCK_ADVANCE_STEP_MS = 1_000L
            private const val TOOL_ROUND_LIMIT_PREFIX = "Stopped: too many tool call rounds"
            private const val SCRIPTED_ACTION_PREFIX = "MULTI:"
            private const val ONE_HOUR_SCENARIO_ID = "one_hour_aio"
            private const val PET_TASK_ID = "pet-grooming-live"
            private const val FAMILY_TASK_ID = "family-shopping-live"
            private const val PACKAGE_TASK_ID = "coldchain-delivery-live"
            private const val HEALTH_TASK_ID = "health-supply-live"
            private val INITIAL_SCENARIO_CLOCK: LocalDateTime = LocalDateTime.of(2027, 4, 25, 13, 0)
            private val CLOCK_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
            private val CLOCK_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US)
            private val BLUEPRINT_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.US)
        }
    }

private fun ActionButton.toAgentDecisionAction(): AgentDecisionAction =
    AgentDecisionAction(
        label = label,
        value = value,
    )
