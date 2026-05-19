package com.mobilebot.chat

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilebot.bus.MessageBus
import com.mobilebot.data.settings.UserSettingsRepository
import com.mobilebot.domain.AgentLoop
import com.mobilebot.domain.ForegroundController
import com.mobilebot.domain.LlmConfigurator
import com.mobilebot.domain.agent.AgentDecisionAction
import com.mobilebot.domain.agent.AgentSessionInput
import com.mobilebot.domain.agent.AgentSessionRoute
import com.mobilebot.domain.agent.AgentDecisionInput
import com.mobilebot.domain.agent.AgentDecisionIntent
import com.mobilebot.domain.agent.AgentDecisionIntentNormalizer
import com.mobilebot.domain.agent.ScenarioCommandGuard
import com.mobilebot.domain.agent.ScenarioAgentTurnInput
import com.mobilebot.domain.agent.ScenarioAgentTurnRunner
import com.mobilebot.domain.interaction.ActionPromptCodec
import com.mobilebot.domain.repository.MemoryFileRepository
import com.mobilebot.domain.todo.TodoListCodec
import com.mobilebot.domain.tools.ToolRegistry
import com.mobilebot.network.RoleCallModel
import com.mobilebot.network.RoleCallRequest
import com.mobilebot.network.RoleCallTurn
import com.mobilebot.scenarios.onehour.OneHourFlowEffect
import com.mobilebot.scenarios.onehour.OneHourScenarioPolicy
import com.mobilebot.scenarios.onehour.OneHourScenarioFlow
import com.mobilebot.scenarios.onehour.OneHourScenarioRunTracker
import com.mobilebot.scenarios.runtime.ScenarioAgentCommand
import com.mobilebot.scenarios.runtime.ScenarioAction
import com.mobilebot.scenarios.runtime.ScenarioConversation
import com.mobilebot.scenarios.runtime.ScenarioCommandAuthorization
import com.mobilebot.scenarios.runtime.ScenarioDecision
import com.mobilebot.scenarios.runtime.ScenarioLog
import com.mobilebot.scenarios.runtime.ScenarioParticipant
import com.mobilebot.scenarios.runtime.ScenarioProgress
import com.mobilebot.scenarios.runtime.ScenarioSurfaceRole
import com.mobilebot.scenarios.runtime.ScenarioSurfaceStatus
import com.mobilebot.scenarios.runtime.ScenarioTaskSeed
import com.mobilebot.scenarios.runtime.ScenarioTaskUpdate
import com.mobilebot.scenarios.runtime.ScenarioTimeline
import com.mobilebot.systemruntime.AlarmFiredEvent
import com.mobilebot.systemruntime.CallEndedEvent
import com.mobilebot.systemruntime.EmailSentEvent
import com.mobilebot.systemruntime.IncomingEmailEvent
import com.mobilebot.systemruntime.IncomingCallEvent
import com.mobilebot.systemruntime.IncomingSmsEvent
import com.mobilebot.systemruntime.ReminderFiredEvent
import com.mobilebot.systemruntime.SystemRuntime
import com.mobilebot.systemruntime.SystemRuntimeEvent
import com.mobilebot.systemruntime.VoiceCallSession
import com.mobilebot.systemruntime.VoiceCallSessionRuntime
import com.mobilebot.systemruntime.VoiceCallSpeaker
import com.mobilebot.systemruntime.WebQueryResultEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDateTime
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
        private val llmConfigurator: LlmConfigurator,
        private val decisionIntentNormalizer: AgentDecisionIntentNormalizer,
        private val systemRuntime: SystemRuntime,
        private val voiceCallSessionRuntime: VoiceCallSessionRuntime,
        private val roleCallModel: RoleCallModel,
        private val scenarioAgentTurnRunner: ScenarioAgentTurnRunner,
        private val toolRegistry: ToolRegistry,
        private val memoryFiles: MemoryFileRepository,
    ) : ViewModel() {
        private var currentChatId: String? = null
        private var eventCounter = 0
        private var continuationCount = 0
        private var scenarioClock = INITIAL_SCENARIO_CLOCK
        private var deferredRetriggerInProgress = false
        private var latestAgentDecisionIntent: AgentDecisionIntent? = null
        private var pendingSelectedActionLabel: String? = null
        private var awaitingInitialPrecheckDecision = false
        private var activeServiceDate = INITIAL_SCENARIO_CLOCK.toLocalDate().plusDays(1)
        private var clockMode = ScenarioClockMode.Live
        private var liveClockAnchorMs = SystemClock.elapsedRealtime()
        private var fastClockJob: Job? = null
        private var taskSortCounter = 0L
        private val taskStates = linkedMapOf<String, AgentTaskState>()
        private val taskSessionIds = mutableMapOf<String, String>()
        private val pinnedTaskIds = linkedSetOf<String>()
        private val scenarioRunTracker = OneHourScenarioRunTracker()
        private val oneHourFlow = OneHourScenarioFlow()
        private val scenarioSpec = OneHourScenarioPolicy.config()
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
            systemRuntime.scheduleScenarioEvents(ONE_HOUR_SCENARIO_ID, scenarioClock)
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
            accelerateClockUntilNextEvent(allowWhilePaused = false)
        }

        private fun accelerateClockUntilNextEvent(allowWhilePaused: Boolean) {
            if (deferredRetriggerInProgress) return
            if (!allowWhilePaused && shouldPauseLiveClock()) return
            if (fastClockJob?.isActive == true) return
            val target = nextDeliverableTimelineEvent() ?: return
            clockMode = ScenarioClockMode.FastUntilNextEvent
            _frame.update { it.copy(clockMode = clockMode) }
            fastClockJob = viewModelScope.launch {
                while (scenarioClock.isBefore(target) && clockMode == ScenarioClockMode.FastUntilNextEvent) {
                    delay(CLOCK_LOOP_INTERVAL_MS)
                    val nextClock = scenarioClock.plusMinutes(1)
                    scenarioClock = if (nextClock.isAfter(target)) target else nextClock
                    _frame.update { it.withClock(scenarioClock) }
                    // 快进每过一分钟就检查一次，触发下一个系统事件后立即停下。
                    handleDueTimelineEvents()
                    if (clockMode != ScenarioClockMode.FastUntilNextEvent) {
                        return@launch
                    }
                }
                handleDueTimelineEvents()
                if (clockMode == ScenarioClockMode.FastUntilNextEvent) {
                    setClockModeLive()
                }
            }
        }

        fun markScreenReady() {
            if (clockMode == ScenarioClockMode.Live) {
                resetLiveClockAnchor()
            }
        }

        fun startScenario() {
            if (_frame.value.busy) return
            if (_frame.value.hasStarted && _frame.value.error == null && _frame.value.finalSummary == null) return
            val runChatId = "run-${scenario.scenarioId}-${UUID.randomUUID().toString().take(8)}"
            currentChatId = runChatId
            activeServiceDate = scenarioClock.toLocalDate().plusDays(1)
            eventCounter = 0
            continuationCount = 0
            latestAgentDecisionIntent = null
            pendingSelectedActionLabel = null
            awaitingInitialPrecheckDecision = true
            resetLiveClockAnchor()
            taskSessionIds.clear()
            systemRuntime.scheduleScenarioEvents(ONE_HOUR_SCENARIO_ID, scenarioClock)
            scenarioRunTracker.clear()
            val baseFrame = AgentExperienceFrame.initial(scenario).withClock(scenarioClock)
            val precheckDecision = OneHourScenarioPolicy.precheckDecision()
            val precheckPrompt = DecisionPrompt(
                text = precheckDecision.text,
                actions = precheckDecision.actions.map { ActionButton(it.label, it.key) },
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
                val sessionId = sessionIdForTask(activeTaskId)
                AgentSessionRoute(
                    sessionId = sessionId,
                    taskId = activeTaskId,
                )
            }

        private fun sessionIdForTask(taskId: String?): String {
            val sessionId = if (taskId == null) {
                currentChatId ?: "run-${scenario.scenarioId}-${UUID.randomUUID().toString().take(8)}"
            } else {
                // 每个任务拥有独立 Agent 上下文，避免多任务互相污染。
                taskSessionIds.getOrPut(taskId) {
                    "task-$taskId-${UUID.randomUUID().toString().take(8)}"
                }
            }
            currentChatId = sessionId
            return sessionId
        }

        fun selectTask(taskId: String) {
            val task = taskStates[taskId] ?: return
            taskStates[_frame.value.activeTaskId]?.let {
                taskStates[it.id] = _frame.value.captureTaskState(it)
            }
            // 查看任务不改变任务活跃顺序，只有真实事件更新才刷新排序。
            currentChatId = taskSessionIds[taskId]
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
                oneHourFlow.userTurnCommands(
                    taskId = _frame.value.activeTaskId,
                    userText = displayText,
                )?.let { commands ->
                    _frame.update {
                        it.copy(
                            debugTrace = appendTrace(
                                it.debugTrace,
                                "scenario user turn -> local runtime",
                            ),
                        )
                    }
                    applyScenarioAgentCommands(commands, blueprintTimeText(scenarioClock))
                    pendingSelectedActionLabel = null
                    handleDueTimelineEvents()
                    finishScenarioDecisionTurn(selectedActionValue, displayText)
                    return@launch
                }
                val normalized = decisionIntentNormalizer.normalize(
                    AgentDecisionInput(
                        contextId = scenario.scenarioId,
                        promptText = prompt?.text.orEmpty(),
                        presentedActions = prompt?.actions.orEmpty().map { it.toAgentDecisionAction() },
                        candidateIntents = OneHourScenarioPolicy.decisionIntents(scenario.scenarioId),
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
                            OneHourScenarioPolicy.isKeepCurrentWeek(normalized.intent)
                        ) {
                            AgentTaskLog(
                                id = nextId("task"),
                                timeText = blueprintTimeText(scenarioClock),
                                text = OneHourScenarioPolicy.initialTaskLogText(),
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
                oneHourFlow.userIntentCommands(
                    taskId = _frame.value.activeTaskId,
                    intentId = normalized.intent.id,
                    userText = displayText,
                )?.let { commands ->
                    applyScenarioAgentCommands(commands, blueprintTimeText(scenarioClock))
                    pendingSelectedActionLabel = null
                    handleDueTimelineEvents()
                    finishScenarioDecisionTurn(selectedActionValue, displayText)
                    return@launch
                }
                if (
                    initialPrecheckDecision &&
                    OneHourScenarioPolicy.isDeferCurrentWeek(normalized.intent)
                ) {
                    completeDeferredScenarioRun()
                    return@launch
                }
                val agentText =
                    if (initialPrecheckDecision) {
                        """
                            ${OneHourScenarioPolicy.triggerText(scenarioClock)}

                            ${OneHourScenarioPolicy.initialDecisionInstruction(normalized.agentText)}
                        """.trimIndent()
                    } else {
                        normalized.agentText
                    }
                runAgentTurn(chatId, agentText)
            }
        }

        private fun completeDeferredScenarioRun() {
            val message = OneHourScenarioPolicy.deferredCompletionMessage()
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
                currentChatId = chatId
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
                                error = OneHourScenarioPolicy.workflowStoppedError(),
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
                                detail = OneHourScenarioPolicy.nextMilestoneDetail(),
                                completed = completedStageCount(it),
                                total = totalStageCount(it),
                            ),
                            timeline = it.timeline + AgentTimelineEvent(
                                id = nextId("guard"),
                                title = "Workflow continuing",
                                detail = OneHourScenarioPolicy.closureRequiredDetail(),
                                status = AgentTimelineStatus.RUNNING,
                            ),
                            debugTrace = appendTrace(it.debugTrace, OneHourScenarioPolicy.continuationTrace()),
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
            if (!OneHourScenarioPolicy.matches(frame.scenario.scenarioId)) return null
            if (frame.decisionPrompt != null || frame.error != null) return null
            if (frame.finalSummary.isNullOrBlank()) return null
            if (scenarioDeferred(frame)) return null
            if (scenarioClosureSatisfied(frame)) return null
            return scenarioContinuationPrompt()
        }

        private fun scenarioContinuationPrompt(): String =
            OneHourScenarioPolicy.continuationPrompt(
                date = activeServiceDate,
                useAlternativeService = selectedAppointmentIsAlternative(),
            )

        private fun scenarioDeferred(frame: AgentExperienceFrame): Boolean {
            if (!OneHourScenarioPolicy.matches(frame.scenario.scenarioId)) return false
            return OneHourScenarioPolicy.isDeferCurrentWeek(latestAgentDecisionIntent)
        }

        private fun shouldScheduleDeferredRetrigger(frame: AgentExperienceFrame): Boolean =
            OneHourScenarioPolicy.matches(frame.scenario.scenarioId) &&
                frame.finalSummary != null &&
                frame.decisionPrompt == null &&
                frame.error == null &&
                scenarioDeferred(frame)

        private fun scheduleDeferredRetrigger() {
            if (deferredRetriggerInProgress) return
            deferredRetriggerInProgress = true
            viewModelScope.launch {
                val nextClock = scenarioClock.plusDays(7).withHour(13).withMinute(0).withSecond(0).withNano(0)
                advanceClockTo(nextClock)
                scenarioClock = nextClock
                resetLiveClockAnchor()
                latestAgentDecisionIntent = null
                pendingSelectedActionLabel = null
                scenarioRunTracker.clear()
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
                clockDateText = "${clock.toLocalDate().format(CLOCK_DATE_FORMATTER)} ${scenarioDayLabel(clock)}",
                clockMode = clockMode,
            )

        private fun scenarioDayLabel(clock: LocalDateTime): String {
            val offset = Duration.between(
                INITIAL_SCENARIO_CLOCK.toLocalDate().atStartOfDay(),
                clock.toLocalDate().atStartOfDay(),
            ).toDays()
            return SCENARIO_DAY_LABELS[Math.floorMod(offset, SCENARIO_DAY_LABELS.size.toLong()).toInt()]
        }

        private fun scenarioClosureSatisfied(frame: AgentExperienceFrame): Boolean {
            if (!OneHourScenarioPolicy.matches(frame.scenario.scenarioId)) return false
            return scenarioRunTracker.closureSatisfied()
        }

        private fun handleAgentMessage(content: String, metadata: Map<String, String>) {
            val runtime = metadata["_runtime"].orEmpty()
            val tool = metadata["_tool"].orEmpty()
            if (runtime == "tool" && metadata["_ok"] == "1") {
                recordScenarioMilestones(tool, content)
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
                        if (shouldSuppressResolvedScenarioPrompt(content)) {
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
                                if (shouldSuppressResolvedScenarioPrompt(content)) null else inferDecisionPrompt(content)
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
                                    OneHourScenarioPolicy.matches(scenario.scenarioId) &&
                                    displayText == null &&
                                    OneHourScenarioPolicy.isTransientNarration(content)
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
            if (OneHourScenarioPolicy.matches(scenario.scenarioId) && OneHourScenarioPolicy.isTransientNarration(content)) return null
            if (
                lower.contains("system_wait_for_sms") ||
                lower.contains("device_system") ||
                    lower.contains("device system") ||
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
                OneHourScenarioPolicy.matches(scenario.scenarioId) &&
                    OneHourScenarioPolicy.isTransientNarration(content) -> null
                OneHourScenarioPolicy.matches(scenario.scenarioId) &&
                    isRoutineReminderQuestion(content) -> null
                OneHourScenarioPolicy.matches(scenario.scenarioId) &&
                    OneHourScenarioPolicy.isServiceContact(content) &&
                    content.contains("最终价格") -> "已向服务方发送短信，确认可选时段和服务内容。"
                OneHourScenarioPolicy.matches(scenario.scenarioId) &&
                    OneHourScenarioPolicy.isCompletionText(content) ->
                    OneHourScenarioPolicy.compactCompletionText(content, scenarioRunTracker.paymentAmount)
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
            return OneHourScenarioPolicy.matches(scenario.scenarioId) &&
                OneHourScenarioPolicy.isRoutineReminderQuestion(
                    text = text,
                    looksLikeDecisionRequest = looksLikeDecisionRequest(text),
                )
        }

        private fun normalizeAmountText(value: String): String {
            return OneHourScenarioPolicy.normalizeAmountText(value)
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
            var callSessionId: String? = null
            _frame.update { frame ->
                val notification = frame.systemNotification
                val call = if (notification != null && notification.actionLabel.trim() == "接听") {
                    val sessionId = notification.callSessionId ?: notification.id
                    callSessionId = sessionId
                    val caller = notification.title.removeSuffix(" 来电")
                    val session = voiceCallSessionRuntime.startSession(
                        sessionId = sessionId,
                        contact = caller,
                        personaId = notification.personaId ?: caller.lowercase(Locale.US),
                    )
                    AgentActiveCall(
                        id = session.id,
                        caller = session.contact,
                        startedTimeText = notification.timeText,
                        statusText = "来电接入中",
                        transcriptText = "通话已接通，等待对方开口。",
                        personaId = session.personaId,
                        turns = emptyList(),
                        inputEnabled = false,
                    )
                } else {
                    frame.activeCall
                }
                frame.copy(
                    systemNotification = null,
                    activeCall = call,
                )
            }
            callSessionId?.let { sessionId ->
                viewModelScope.launch {
                    generateRoleCallOpeningTurn(sessionId)
                }
            }
        }

        fun submitCallUserTurn(text: String) {
            val cleanText = text.trim()
            val call = _frame.value.activeCall ?: return
            if (cleanText.isBlank() || !call.inputEnabled) return
            val session = voiceCallSessionRuntime.appendTurn(call.id, VoiceCallSpeaker.USER, cleanText) ?: return
            _frame.updateActiveCall(session, "对方思考中", inputEnabled = false)
            viewModelScope.launch {
                val reply = generateRoleCallReply(session, cleanText, openingTurn = false)
                val updated = voiceCallSessionRuntime.appendTurn(call.id, VoiceCallSpeaker.AGENT, reply)
                    ?: voiceCallSessionRuntime.currentSession(call.id)
                    ?: return@launch
                _frame.updateActiveCall(updated, "等待你回应", inputEnabled = true)
            }
        }

        fun hangUpActiveCall() {
            val call = _frame.value.activeCall ?: return
            val transcript = voiceCallSessionRuntime.finishSession(call.id)
            _frame.update { frame ->
                frame.copy(
                    activeCall = null,
                    progressLine = frame.progressLine.copy(
                        label = "${call.caller} 通话结束",
                        detail = "整理通话转写",
                    ),
                )
            }
            val eventId = callEndedEventId(call.id)
            systemRuntime.markScenarioEventDelivered(ONE_HOUR_SCENARIO_ID, eventId)
            viewModelScope.launch {
                systemRuntime.publishEvent(
                    CallEndedEvent(
                        id = eventId,
                        occurredAt = scenarioClock,
                        source = call.caller,
                        title = "${call.caller} 通话结束",
                        body = "通话结束，音频可用于提取家庭采购待办。",
                        contact = call.caller,
                        audioRef = transcript?.audioRef ?: VoiceCallSession.runtimeAudioRef(call.id),
                        callSessionId = call.id,
                    ),
                )
            }
        }

        private suspend fun generateRoleCallOpeningTurn(sessionId: String) {
            val session = voiceCallSessionRuntime.currentSession(sessionId) ?: return
            val reply = generateRoleCallReply(session, latestUserText = "", openingTurn = true)
            val updated = voiceCallSessionRuntime.appendTurn(sessionId, VoiceCallSpeaker.AGENT, reply)
                ?: voiceCallSessionRuntime.currentSession(sessionId)
                ?: return
            _frame.updateActiveCall(updated, "等待你回应", inputEnabled = true)
        }

        private suspend fun generateRoleCallReply(
            session: VoiceCallSession,
            latestUserText: String,
            openingTurn: Boolean,
        ): String {
            val hasApiKey = settings.getApiKey().isNotBlank()
            if (!hasApiKey) {
                return OneHourScenarioPolicy.fallbackRoleCallReply(
                    openingTurn = openingTurn,
                    userTurns = session.turns.count { it.speaker == VoiceCallSpeaker.USER },
                )
            }
            return runCatching {
                llmConfigurator.beforeRequest()
                withContext(Dispatchers.IO) {
                    withTimeoutOrNull(CALL_ROLE_MODEL_TIMEOUT_MS) {
                        roleCallModel.nextReply(
                            RoleCallRequest(
                                personaId = session.personaId,
                                personaInstruction = OneHourScenarioPolicy.ellaRoleCallInstruction(),
                                contactName = session.contact,
                                transcript = session.turns.map {
                                    RoleCallTurn(
                                        speaker = when (it.speaker) {
                                            VoiceCallSpeaker.USER -> "用户"
                                            VoiceCallSpeaker.AGENT -> session.contact
                                        },
                                        text = it.text,
                                    )
                                },
                                latestUserText = latestUserText,
                                openingTurn = openingTurn,
                            ),
                        ).text
                    }
                }
            }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.takeIf { OneHourScenarioPolicy.isValidEllaRoleCallReply(it, openingTurn) }
                ?: OneHourScenarioPolicy.fallbackRoleCallReply(
                    openingTurn = openingTurn,
                    userTurns = session.turns.count { it.speaker == VoiceCallSpeaker.USER },
                )
        }

        private fun MutableStateFlow<AgentExperienceFrame>.updateActiveCall(
            session: VoiceCallSession,
            statusText: String,
            inputEnabled: Boolean,
        ) {
            update { frame ->
                val current = frame.activeCall
                if (current == null || current.id != session.id) {
                    frame
                } else {
                    frame.copy(
                        activeCall = current.copy(
                            statusText = statusText,
                            transcriptText = session.toDisplayTranscript(),
                            turns = session.turns.map {
                                AgentCallTurn(
                                    speaker = when (it.speaker) {
                                        VoiceCallSpeaker.USER -> "你"
                                        VoiceCallSpeaker.AGENT -> session.contact
                                    },
                                    text = it.text,
                                )
                            },
                            inputEnabled = inputEnabled,
                        ),
                    )
                }
            }
        }

        private fun VoiceCallSession.toDisplayTranscript(): String =
            turns.joinToString("\n") { turn ->
                val speaker = when (turn.speaker) {
                    VoiceCallSpeaker.USER -> "你"
                    VoiceCallSpeaker.AGENT -> contact
                }
                "$speaker：${turn.text}"
            }.ifBlank { "通话已接通，等待对话开始。" }

        private fun callEndedEventId(sessionId: String): String =
            if (sessionId.endsWith("-call")) {
                "$sessionId-ended"
            } else {
                "$sessionId-ended"
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
                "use_skill" -> "流程已选择" to "加载协调规则。"
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
                "use_skill" -> "协调规则已启用。"
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
                content.contains("\"key\":\"memory\"", ignoreCase = true) -> "服务偏好已加载。"
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
            OneHourScenarioPolicy.serviceTaskLogText(serviceId, action, firstNamedEntity(data))?.let { return it }
            return when {
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
                if (participant.role == "service_provider") {
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
                OneHourScenarioPolicy.participantRoleForContact(trimmed) == "private_driver" -> "private_driver"
                OneHourScenarioPolicy.participantRoleForContact(trimmed) == "service_provider" -> "service_provider"
                else -> "service"
            }
            return AgentParticipant(
                id = participantId(trimmed, role),
                label = participantLabel(trimmed),
                displayName = trimmed,
                role = role,
            )
        }

        private fun participantId(name: String, role: String): String =
            when (role) {
                "private_driver" -> "driver"
                else -> "$role-${name.lowercase().replace(Regex("""\s+"""), "-")}"
            }

        private fun participantLabel(name: String): String {
            val label = OneHourScenarioPolicy.labelForContact(name)
            if (label != "?") return label
            val letters = name.filter { it.isLetterOrDigit() }
            return if (letters.isBlank()) name.take(1) else letters.take(2).uppercase()
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
            val party = OneHourScenarioPolicy.displayContactName(contact).takeIf {
                it != contact || OneHourScenarioPolicy.participantRoleForContact(contact) == "private_driver"
            } ?: return emptyList()
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

        private fun displayReminderBody(raw: String): String {
            return OneHourScenarioPolicy.displayReminderBody(raw)
        }

        private fun applyScenarioTaskUpdate(
            update: ScenarioTaskUpdate,
            timeText: String,
            activate: Boolean = false,
        ) {
            updateTaskState(update.taskId, activate = activate) { task ->
                val baseParticipants = update.participants
                    ?.map { it.toAgentParticipant() }
                    ?.canonicalParticipants()
                    ?: task.participants
                val withAdded = update.participantsToAdd.fold(baseParticipants) { participants, participant ->
                    participants.withParticipant(participant.toAgentParticipant())
                }
                val updatedParticipants = if (update.participantsToRemove.isEmpty()) {
                    withAdded
                } else {
                    withAdded.filterNot { it.id in update.participantsToRemove }
                }
                task.copy(
                    status = update.status.toAgentStatus(),
                    updatedTimeText = timeText,
                    subtitle = update.subtitle,
                    conversationItems = appendConversationItems(
                        task.conversationItems,
                        update.conversations.map { it.toConversationItem() },
                    ),
                    taskLogs = appendTaskLogs(task.taskLogs, update.logs.toTaskLogs(timeText)),
                    participants = updatedParticipants,
                    progressLine = update.progress.toProgressLine(),
                    decisionPrompt = update.decision?.toDecisionPrompt(),
                    activeActionValue = update.activeActionValue,
                    selectedAction = if (update.decision == null) task.selectedAction else null,
                    timeline = task.timeline + update.timeline.map { it.toTimelineEvent() },
                    finalSummary = update.finalSummary ?: task.finalSummary,
                )
            }
        }

        private fun ScenarioTaskSeed.toTaskState(timeText: String): AgentTaskState =
            AgentTaskState(
                id = taskId,
                title = title,
                subtitle = subtitle,
                status = status.toAgentStatus(),
                updatedTimeText = timeText,
                conversationItems = conversations.map { it.toConversationItem() },
                taskLogs = logs.toTaskLogs(timeText),
                participants = participants.map { it.toAgentParticipant() },
                progressLine = progress.toProgressLine(),
                timeline = timeline.map { it.toTimelineEvent() },
                decisionPrompt = decision?.toDecisionPrompt(),
            )

        private fun ScenarioSurfaceStatus.toAgentStatus(): AgentTimelineStatus =
            when (this) {
                ScenarioSurfaceStatus.RUNNING -> AgentTimelineStatus.RUNNING
                ScenarioSurfaceStatus.DONE -> AgentTimelineStatus.DONE
                ScenarioSurfaceStatus.BLOCKED -> AgentTimelineStatus.BLOCKED
            }

        private fun ScenarioConversation.toConversationItem(): AgentConversationItem =
            AgentConversationItem(
                id = nextId("conversation"),
                role = when (role) {
                    ScenarioSurfaceRole.AGENT -> AgentConversationRole.AGENT
                    ScenarioSurfaceRole.USER -> AgentConversationRole.USER
                },
                text = text,
            )

        private fun List<ScenarioLog>.toTaskLogs(timeText: String): List<AgentTaskLog> =
            map {
                AgentTaskLog(
                    id = nextId("task"),
                    timeText = timeText,
                    text = it.text,
                )
            }

        private fun ScenarioParticipant.toAgentParticipant(): AgentParticipant =
            AgentParticipant(
                id = id,
                label = label,
                displayName = displayName,
                role = role,
            )

        private fun List<AgentParticipant>.canonicalParticipants(): List<AgentParticipant> =
            fold(emptyList<AgentParticipant>()) { parties, participant ->
                parties.withCanonicalParticipant(participant)
            }.takeLast(MAX_PARTICIPANTS)

        private fun List<AgentParticipant>.withCanonicalParticipant(participant: AgentParticipant): List<AgentParticipant> =
            filterNot { it.isEquivalentParticipant(participant) } + participant

        private fun AgentParticipant.isEquivalentParticipant(other: AgentParticipant): Boolean =
            id == other.id ||
                displayName.equals(other.displayName, ignoreCase = true) ||
                (role == other.role && label == other.label)

        private fun ScenarioProgress.toProgressLine(): AgentProgressLine =
            AgentProgressLine(
                label = label,
                detail = detail,
                completed = completed,
                total = total,
            )

        private fun AgentProgressLine.toScenarioProgress(): ScenarioProgress =
            ScenarioProgress(
                label = label,
                detail = detail,
                completed = completed,
                total = total,
            )

        private fun ScenarioDecision.toDecisionPrompt(): DecisionPrompt =
            DecisionPrompt(
                text = text,
                actions = actions.map {
                    ActionButton(
                        label = it.label,
                        value = "$SCRIPTED_ACTION_PREFIX${it.key}",
                    )
                },
            )

        private fun ScenarioTimeline.toTimelineEvent(): AgentTimelineEvent =
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

        private suspend fun tickScenarioClock() {
            if (deferredRetriggerInProgress) return
            if (clockMode == ScenarioClockMode.FastUntilNextEvent) {
                // 快进由单独 Job 执行，避免普通时钟循环并发推进。
                return
            }
            if (shouldPauseLiveClock()) {
                resetLiveClockAnchor()
                return
            }
            val elapsedMinutes = (SystemClock.elapsedRealtime() - liveClockAnchorMs) / SCENARIO_CLOCK_TICK_MS
            if (elapsedMinutes > 0L) {
                liveClockAnchorMs += elapsedMinutes * SCENARIO_CLOCK_TICK_MS
                scenarioClock = scenarioClock.plusMinutes(elapsedMinutes)
                _frame.update { it.withClock(scenarioClock) }
                handleDueTimelineEvents()
            }
        }

        private suspend fun handleDueTimelineEvents() {
            val delivered = systemRuntime.advanceScenarioClock(
                scenarioId = ONE_HOUR_SCENARIO_ID,
                now = scenarioClock,
                heldEventIds = heldRuntimeEventIds(),
            )
            if (delivered.isNotEmpty() && clockMode == ScenarioClockMode.FastUntilNextEvent) {
                setClockModeLive()
            }
        }

        private fun nextDeliverableTimelineEvent(): LocalDateTime? =
            systemRuntime.nextScheduledScenarioEvent(
                scenarioId = ONE_HOUR_SCENARIO_ID,
                now = scenarioClock,
                heldEventIds = heldRuntimeEventIds(),
            )?.triggerAt

        private fun setClockModeLive() {
            clockMode = ScenarioClockMode.Live
            resetLiveClockAnchor()
            _frame.update { it.copy(clockMode = clockMode) }
        }

        private fun resetLiveClockAnchor() {
            liveClockAnchorMs = SystemClock.elapsedRealtime()
        }

        private fun shouldPauseLiveClock(): Boolean =
            _frame.value.busy ||
                _frame.value.decisionPrompt != null ||
                _frame.value.systemNotification != null ||
                _frame.value.activeCall != null

        private fun heldRuntimeEventIds(): Set<String> =
            buildSet {
                add("ella-call-ended")
                if (!oneHourFlow.isPetCareAccepted()) {
                    addAll(OneHourScenarioFlow.petAcceptanceRequiredEventIds)
                }
            }

        private fun handleSystemRuntimeEvent(event: SystemRuntimeEvent) {
            val timeText = blueprintTimeText(event.occurredAt)
            val localEffects = oneHourFlow.handle(event)
            _frame.update {
                it.copy(
                    recentSystemEvents = appendSystemEvent(it.recentSystemEvents, event),
                    debugTrace = appendTrace(it.debugTrace, "system event -> ${event.id}"),
                )
            }
            applyOneHourFlowEffects(localEffects, timeText)
            viewModelScope.launch {
                runScenarioAgentForSystemEvent(event, localEffects)
            }
        }

        private suspend fun runScenarioAgentForSystemEvent(
            event: SystemRuntimeEvent,
            localEffects: List<OneHourFlowEffect>,
        ) {
            val authorization = OneHourScenarioFlow.commandAuthorizationForEvent(event)
            if (shouldSkipScenarioPlanner(authorization, localEffects)) return
            val taskId = authorization.taskIds.firstOrNull { taskStates.containsKey(it) }
                ?: authorization.taskIds.firstOrNull()
                ?: _frame.value.activeTaskId
            val sessionId = sessionIdForTask(taskId ?: event.id)
            val timeText = blueprintTimeText(event.occurredAt)
            recordObservedSystemEventLog(event, timeText, authorization.taskIds)
            if (authorization.taskIds.isNotEmpty()) {
                _frame.update {
                    it.copy(
                        busy = true,
                        statusLabel = "Understanding",
                        progressLine = AgentProgressLine(
                            label = event.toRuntimeProgressDetail(),
                            detail = "解读中",
                            completed = completedStageCount(it),
                            total = totalStageCount(it),
                        ),
                    )
                }
            }
            val hasApiKey = settings.getApiKey().isNotBlank()
            val result = if (!hasApiKey) {
                null
            } else {
                runScenarioPlanner(
                    ScenarioAgentTurnInput(
                        sessionId = sessionId,
                        scenarioId = scenario.scenarioId,
                        skillName = scenario.skillName,
                        turnType = "system_event",
                        taskId = taskId,
                        eventFact = event.toAgentFact(),
                        currentTaskSnapshot = taskSnapshotFor(taskId),
                        allTaskSnapshots = allTaskSnapshotsForPlanner(),
                        recentToolResults = recentToolResultsForPlanner(),
                        memoryDigest = memoryDigestForScenario(),
                        skillInstruction = OneHourScenarioPolicy.orchestrationInstruction(
                            plannerPolicyJson = OneHourScenarioFlow.plannerPolicyJson(event),
                        ),
                    ),
                )
            }
            if (result?.isOk == true) {
                val commands = result.commands.withoutLocallyHandledTaskSurfaceCommands(localEffects)
                if (commands.isEmpty() && authorization.taskIds.isNotEmpty()) {
                    recordScenarioAgentDiagnostic("system ${event.id}", "LLM 未返回命令，未执行本地业务结果。")
                    return
                }
                val guardError = ScenarioCommandGuard.validate(
                    commands = commands,
                    knownTaskIds = taskStates.keys,
                    authorization = authorization,
                )
                if (guardError == null) {
                    applyScenarioAgentCommands(commands.withSystemEventLog(event), timeText)
                    _frame.update { it.copy(busy = false, activeActionValue = null) }
                } else {
                    recordScenarioAgentDiagnostic("system ${event.id}", guardError)
                }
            } else {
                val reason = result?.error ?: if (hasApiKey) {
                    "LLM 编排超时，未执行本地业务结果。"
                } else {
                    "未配置 API Key，未执行本地业务结果。"
                }
                recordScenarioAgentDiagnostic("system ${event.id}", reason)
            }
        }

        private fun shouldSkipScenarioPlanner(
            authorization: ScenarioCommandAuthorization,
            localEffects: List<OneHourFlowEffect>,
        ): Boolean {
            if (authorization.taskIds.isEmpty()) return true
            if (localEffects.isEmpty()) return false
            return authorization.sms.isEmpty() && authorization.reminders.isEmpty()
        }

        private suspend fun runScenarioPlanner(input: ScenarioAgentTurnInput) =
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(SCENARIO_PLANNER_TIMEOUT_MS) {
                    scenarioAgentTurnRunner.run(input)
            }
        }

        private fun recordObservedSystemEventLog(
            event: SystemRuntimeEvent,
            timeText: String,
            taskIds: Set<String>,
        ) {
            val eventLog = ScenarioLog(event.toBlueprintLogText())
            taskIds
                .filter { taskStates.containsKey(it) }
                .forEach { taskId ->
                    updateTaskState(taskId, activate = _frame.value.activeTaskId == taskId) { task ->
                        task.copy(
                            taskLogs = appendTaskLogs(task.taskLogs, listOf(eventLog).toTaskLogs(timeText)),
                        )
                    }
                }
        }

        private fun SystemRuntimeEvent.toAgentFact(): String =
            buildString {
                appendLine("type: ${this@toAgentFact::class.simpleName}")
                appendLine("time: ${blueprintTimeText(occurredAt)}")
                appendLine("source: $source")
                appendLine("title: $title")
                appendLine("body: $body")
                when (val event = this@toAgentFact) {
                    is IncomingCallEvent -> {
                        appendLine("contact: ${event.contact}")
                        event.callSessionId?.let { appendLine("callSessionId: $it") }
                    }
                    is CallEndedEvent -> {
                        appendLine("contact: ${event.contact}")
                        appendLine("audioRef: ${event.audioRef}")
                        event.callSessionId?.let { appendLine("callSessionId: $it") }
                    }
                    else -> Unit
                }
            }

        private fun List<ScenarioAgentCommand>.withSystemEventLog(event: SystemRuntimeEvent): List<ScenarioAgentCommand> {
            val eventLog = ScenarioLog(event.toBlueprintLogText())
            val loggedTaskIds = mutableSetOf<String>()
            return map { command ->
                when (command) {
                    is ScenarioAgentCommand.CreateTask -> {
                        if (!loggedTaskIds.add(command.seed.taskId)) {
                            command
                        } else {
                            command.copy(seed = command.seed.copy(logs = command.seed.logs.withEventLog(eventLog, event)))
                        }
                    }
                    is ScenarioAgentCommand.UpdateTask -> {
                        if (!loggedTaskIds.add(command.update.taskId)) {
                            command
                        } else {
                            command.copy(update = command.update.copy(logs = command.update.logs.withEventLog(eventLog, event)))
                        }
                    }
                    else -> command
                }
            }
        }

        private fun List<ScenarioAgentCommand>.withoutLocallyHandledTaskSurfaceCommands(
            localEffects: List<OneHourFlowEffect>,
        ): List<ScenarioAgentCommand> {
            val localTaskIds = localEffects.mapNotNull { effect ->
                when (effect) {
                    is OneHourFlowEffect.CreateTask -> effect.seed.taskId
                    is OneHourFlowEffect.UpdateTask -> effect.update.taskId
                    else -> null
                }
            }.toSet()
            if (localTaskIds.isEmpty()) return this
            return filterNot { command ->
                command.taskId in localTaskIds &&
                    when (command) {
                        is ScenarioAgentCommand.CreateTask,
                        is ScenarioAgentCommand.UpdateTask,
                        is ScenarioAgentCommand.AskUser,
                        is ScenarioAgentCommand.CompleteTask -> true
                        is ScenarioAgentCommand.SendSms,
                        is ScenarioAgentCommand.WaitSms,
                        is ScenarioAgentCommand.CreateReminder,
                        is ScenarioAgentCommand.SwitchTask -> false
                    }
            }
        }

        private fun List<ScenarioLog>.withEventLog(
            eventLog: ScenarioLog,
            event: SystemRuntimeEvent,
        ): List<ScenarioLog> {
            val alreadyLogged = any { log ->
                log.text == eventLog.text || (event.body.isNotBlank() && log.text.contains(event.body))
            }
            if (alreadyLogged) return this
            val remainingLogs = if (firstOrNull()?.isUnattributedEventSummary() == true) drop(1) else this
            return listOf(eventLog) + remainingLogs
        }

        private fun ScenarioLog.isUnattributedEventSummary(): Boolean {
            val value = text.trim()
            if (value.isBlank()) return false
            val operationPrefixes = listOf(
                "收到",
                "发送",
                "开始",
                "创建",
                "触发",
                "记录",
                "支付",
                "添加",
                "移除",
                "完成",
                "更新",
                "联系",
                "监听",
                "回复",
                "取消",
            )
            return operationPrefixes.none { value.startsWith(it) }
        }

        private fun SystemRuntimeEvent.toBlueprintLogText(): String {
            val detail = body.ifBlank { title }
            val sourceText = source.ifBlank { "系统" }
            return when (this) {
                is IncomingSmsEvent -> "收到 $sourceText 短信：$detail"
                is IncomingCallEvent -> "收到 $sourceText 来电：$detail"
                is CallEndedEvent -> "$sourceText 通话结束：$detail"
                is ReminderFiredEvent -> "触发提醒：$detail"
                is AlarmFiredEvent -> "触发闹钟：$detail"
                is IncomingEmailEvent -> "收到 $sourceText 邮件：$detail"
                is EmailSentEvent -> "已发送邮件给 ${to}：$detail"
                is WebQueryResultEvent -> "网页查询结果：$detail"
                else -> "收到 $sourceText 通知：$detail"
            }
        }

        private fun SystemRuntimeEvent.toRuntimeProgressDetail(): String {
            val sourceText = source.ifBlank { "系统" }
            return when (this) {
                is IncomingSmsEvent -> "收到 $sourceText 短信"
                is IncomingCallEvent -> "收到 $sourceText 来电"
                is CallEndedEvent -> "$sourceText 通话结束"
                is ReminderFiredEvent -> "触发提醒"
                is AlarmFiredEvent -> "触发闹钟"
                is IncomingEmailEvent -> "收到 $sourceText 邮件"
                is EmailSentEvent -> "已发送邮件给 ${to}"
                is WebQueryResultEvent -> "收到网页查询结果"
                else -> "收到 $sourceText 通知"
            }
        }

        private suspend fun memoryDigestForScenario(): String =
            withContext(Dispatchers.IO) {
                memoryFiles.readMemoryMd().trim().take(2200)
            }

        private fun taskSnapshotFor(taskId: String?): String {
            val task = taskId?.let { taskStates[it] } ?: return ""
            return buildString {
                appendLine("id: ${task.id}")
                appendLine("title: ${task.title}")
                appendLine("subtitle: ${task.subtitle}")
                appendLine("status: ${task.status}")
                appendLine("participants: ${task.participants.toPlannerDigest()}")
                appendLine("progress: ${task.progressLine.label} ${task.progressLine.detail}")
                appendLine("latestLogs:")
                task.taskLogs.takeLast(8).forEach { appendLine("- ${it.timeText} ${it.text}") }
                appendLine("latestConversation:")
                task.conversationItems.takeLast(6).forEach { appendLine("- ${it.role}: ${it.text}") }
            }
        }

        private fun allTaskSnapshotsForPlanner(): String {
            if (taskStates.isEmpty()) return ""
            return taskStates.values.joinToString("\n\n") { task ->
                buildString {
                    appendLine("id: ${task.id}")
                    appendLine("title: ${task.title}")
                    appendLine("subtitle: ${task.subtitle}")
                    appendLine("status: ${task.status}")
                    appendLine("participants: ${task.participants.toPlannerDigest()}")
                    appendLine("progress: ${task.progressLine.label} ${task.progressLine.detail}")
                    task.taskLogs.lastOrNull()?.let { appendLine("latestLog: ${it.timeText} ${it.text}") }
                }.trim()
            }
        }

        private fun List<AgentParticipant>.toPlannerDigest(): String =
            if (isEmpty()) {
                "(none)"
            } else {
                joinToString(", ") { participant ->
                    "${participant.id}/${participant.label}/${participant.displayName}/${participant.role}"
                }
            }

        private fun recentToolResultsForPlanner(): String =
            taskStates.values
                .flatMap { task -> task.taskLogs.takeLast(4).map { "${task.id}: ${it.timeText} ${it.text}" } }
                .takeLast(10)
                .joinToString("\n")

        private fun recordScenarioAgentDiagnostic(
            turn: String,
            reason: String,
        ) {
            _frame.update {
                val activeTask = taskStates[it.activeTaskId]
                val plannerLabel = it.statusLabel in setOf("Thinking", "理解中")
                it.copy(
                    busy = false,
                    activeActionValue = null,
                    statusLabel = activeTask?.status?.let(::statusLabelFor)
                        ?: if (plannerLabel) "Running" else it.statusLabel,
                    progressLine = activeTask?.progressLine
                        ?: if (it.progressLine.label in setOf("Thinking", "理解中")) {
                            it.progressLine.copy(label = "Running", detail = "")
                        } else {
                            it.progressLine
                        },
                    debugTrace = appendTrace(
                        it.debugTrace,
                        "planner diagnostic [$turn] -> ${reason.take(220)}",
                    ),
                )
            }
        }

        private fun applyScenarioAgentCommands(
            commands: List<ScenarioAgentCommand>,
            timeText: String,
        ) {
            oneHourFlow.updateRuntimeStateFromPlannerCommands(commands)
            val sideEffects = mutableListOf<suspend () -> Unit>()
            commands.forEach { command ->
                when (command) {
                    is ScenarioAgentCommand.CreateTask -> upsertTask(command.seed.toTaskState(timeText))
                    is ScenarioAgentCommand.UpdateTask -> applyScenarioTaskUpdate(command.update, timeText, activate = true)
                    is ScenarioAgentCommand.AskUser -> applyScenarioTaskUpdate(
                        ScenarioTaskUpdate(
                            taskId = command.taskId,
                            subtitle = taskStates[command.taskId]?.subtitle.orEmpty(),
                            status = ScenarioSurfaceStatus.BLOCKED,
                            conversations = emptyList(),
                            progress = taskStates[command.taskId]?.progressLine?.toScenarioProgress()
                                ?: ScenarioProgress("等待", "等待用户决策", 0, 1),
                            decision = command.decision,
                        ),
                        timeText,
                        activate = true,
                    )
                    is ScenarioAgentCommand.SendSms -> {
                        sideEffects += { executeScenarioSmsCommand(command, timeText) }
                    }
                    is ScenarioAgentCommand.WaitSms -> {
                        sideEffects += {
                            appendCommandLog(
                                command.taskId,
                                timeText,
                                "开始监听 ${command.contact} 的短信：${command.reason}",
                            )
                        }
                    }
                    is ScenarioAgentCommand.CreateReminder -> {
                        sideEffects += { executeScenarioReminderCommand(command, timeText) }
                    }
                    is ScenarioAgentCommand.SwitchTask -> selectTask(command.taskId)
                    is ScenarioAgentCommand.CompleteTask -> applyScenarioTaskUpdate(
                        ScenarioTaskUpdate(
                            taskId = command.taskId,
                            subtitle = command.summary,
                            status = ScenarioSurfaceStatus.DONE,
                            conversations = listOf(ScenarioConversation(ScenarioSurfaceRole.AGENT, command.summary)),
                            logs = listOf(ScenarioLog(command.summary)),
                            progress = taskStates[command.taskId]?.progressLine?.let {
                                ScenarioProgress("完成", command.summary, it.total, it.total)
                            } ?: ScenarioProgress("完成", command.summary, 1, 1),
                            finalSummary = command.summary,
                        ),
                        timeText,
                        activate = true,
                    )
                }
            }
            if (sideEffects.isNotEmpty()) {
                viewModelScope.launch {
                    sideEffects.forEach { it() }
                }
            }
        }

        private suspend fun executeScenarioSmsCommand(
            command: ScenarioAgentCommand.SendSms,
            timeText: String,
        ) {
            val result = withContext(Dispatchers.IO) {
                toolRegistry.execute(
                    "system_send_sms",
                    JSONObject()
                        .put("scenarioId", ONE_HOUR_SCENARIO_ID)
                        .put("to", command.to)
                        .put("message", command.message)
                        .put("semanticPurpose", command.semanticPurpose)
                        .toString(),
                )
            }
            val body = if (result.dataJson.isNullOrBlank()) {
                result.message
            } else {
                "${result.message}\n${result.dataJson}"
            }
            val logs = listOfNotNull(
                taskLogFromToolResult(
                    tool = "system_send_sms",
                    content = body,
                    ok = result.ok,
                    eventIndex = taskStates[command.taskId]?.taskLogs?.size ?: 0,
                ),
            )
            if (logs.isNotEmpty()) {
                updateTaskState(command.taskId, activate = _frame.value.activeTaskId == command.taskId) { task ->
                    task.copy(
                        taskLogs = appendTaskLogs(task.taskLogs, logs),
                    )
                }
            }
        }

        private suspend fun executeScenarioReminderCommand(
            command: ScenarioAgentCommand.CreateReminder,
            timeText: String,
        ) {
            val result = withContext(Dispatchers.IO) {
                toolRegistry.execute(
                    "device_system",
                    JSONObject()
                        .put("action", "long_reminder")
                        .put(
                            "params",
                            JSONObject()
                                .put("title", command.title)
                                .put("body", command.body)
                                .put("scheduledFor", command.scheduledFor),
                        )
                        .toString(),
                )
            }
            val body = if (result.dataJson.isNullOrBlank()) {
                result.message
            } else {
                "${result.message}\n${result.dataJson}"
            }
            val log = taskLogFromToolResult(
                tool = "device_system",
                content = body,
                ok = result.ok,
                eventIndex = taskStates[command.taskId]?.taskLogs?.size ?: 0,
            ) ?: AgentTaskLog(
                id = nextId("task"),
                timeText = timeText,
                text = "创建提醒：${command.scheduledFor} ${command.title}",
            )
            updateTaskState(command.taskId, activate = _frame.value.activeTaskId == command.taskId) { task ->
                task.copy(taskLogs = appendTaskLogs(task.taskLogs, listOf(log)))
            }
        }

        private fun appendCommandLog(
            taskId: String,
            timeText: String,
            text: String,
        ) {
            val log = AgentTaskLog(nextId("task"), timeText, text)
            updateTaskState(taskId, activate = _frame.value.activeTaskId == taskId) { task ->
                task.copy(taskLogs = appendTaskLogs(task.taskLogs, listOf(log)))
            }
        }

        private fun handleLocalScenarioDecision(
            displayText: String,
            rawText: String,
            selectedActionValue: String?,
        ) {
            val prompt = _frame.value.decisionPrompt ?: return
            val presentedActions = prompt.actions.map { it.toAgentDecisionAction() }
            val userConversation = if (selectedActionValue == null) {
                AgentConversationItem(
                    id = nextId("conversation"),
                    role = AgentConversationRole.USER,
                    text = displayText,
                )
            } else {
                null
            }
            _frame.value.activeTaskId?.let { taskId ->
                taskStates[taskId]?.let { task ->
                    taskStates[taskId] = task.copy(
                        conversationItems = appendConversationItems(
                            task.conversationItems,
                            listOfNotNull(userConversation),
                        ),
                        selectedAction = null,
                    )
                }
            }
            _frame.update {
                it.copy(
                    statusLabel = "理解中",
                    busy = true,
                    decisionPrompt = if (selectedActionValue == null) null else it.decisionPrompt,
                    activeActionValue = selectedActionValue,
                    conversationItems = appendConversationItems(
                        it.conversationItems,
                        listOfNotNull(userConversation),
                    ),
                    selectedAction = null,
                    progressLine = it.progressLine.copy(
                        label = "理解中",
                        detail = displayText,
                    ),
                    debugTrace = appendTrace(it.debugTrace, "local decision -> ${rawText.take(160)}"),
                )
            }
            viewModelScope.launch {
                runScenarioAgentForDecision(
                    displayText = displayText,
                    rawText = rawText,
                    selectedActionValue = selectedActionValue,
                    presentedActions = presentedActions,
                )
            }
        }

        private suspend fun runScenarioAgentForDecision(
            displayText: String,
            rawText: String,
            selectedActionValue: String?,
            presentedActions: List<AgentDecisionAction>,
        ) {
            val taskId = _frame.value.activeTaskId
            val authorization = OneHourScenarioFlow.commandAuthorizationForUserDecision(taskId)
            val sessionId = sessionIdForTask(taskId)
            val timeText = blueprintTimeText(scenarioClock)
            oneHourFlow.userDecisionCommands(
                taskId = taskId,
                actionKey = selectedActionValue?.removePrefix(SCRIPTED_ACTION_PREFIX),
                userText = displayText,
            )?.let { commands ->
                applyScenarioAgentCommands(commands, timeText)
                pendingSelectedActionLabel = null
                handleDueTimelineEvents()
                finishScenarioDecisionTurn(selectedActionValue, displayText)
                return
            }
            val hasApiKey = settings.getApiKey().isNotBlank()
            val result = if (!hasApiKey) {
                null
            } else {
                runScenarioPlanner(
                    ScenarioAgentTurnInput(
                        sessionId = sessionId,
                        scenarioId = scenario.scenarioId,
                        skillName = scenario.skillName,
                        turnType = "user_decision",
                        taskId = taskId,
                        userInput = displayText,
                        presentedActions = presentedActions,
                        currentTaskSnapshot = taskSnapshotFor(taskId),
                        allTaskSnapshots = allTaskSnapshotsForPlanner(),
                        recentToolResults = recentToolResultsForPlanner(),
                        memoryDigest = memoryDigestForScenario(),
                        skillInstruction = OneHourScenarioPolicy.userDecisionInstruction(
                            userText = displayText,
                            plannerPolicyJson = OneHourScenarioFlow.userDecisionPlannerPolicyJson(
                                userText = displayText,
                                taskId = taskId,
                                displayedActions = presentedActions.map { it.label to it.value },
                            ),
                        ),
                    ),
                )
            }
            if (result?.isOk == true) {
                if (result.commands.isEmpty()) {
                    recordScenarioAgentDiagnostic("user ${rawText.take(80)}", "LLM 未返回命令，未执行本地业务结果。")
                } else {
                    val guardError = ScenarioCommandGuard.validate(
                        commands = result.commands,
                        knownTaskIds = taskStates.keys,
                        authorization = authorization,
                    )
                    if (guardError == null) {
                        applyScenarioAgentCommands(result.commands, timeText)
                    } else {
                        recordScenarioAgentDiagnostic("user ${rawText.take(80)}", guardError)
                    }
                }
                pendingSelectedActionLabel = null
                handleDueTimelineEvents()
                finishScenarioDecisionTurn(selectedActionValue, displayText)
            } else {
                val reason = result?.error ?: if (hasApiKey) {
                    "LLM 编排超时，未执行本地业务结果。"
                } else {
                    "未配置 API Key，未执行本地业务结果。"
                }
                recordScenarioAgentDiagnostic("user ${rawText.take(80)}", reason)
                pendingSelectedActionLabel = null
                handleDueTimelineEvents()
                finishScenarioDecisionTurn(selectedActionValue, displayText)
            }
        }

        private fun finishScenarioDecisionTurn(
            selectedActionValue: String?,
            selectedActionLabel: String,
        ) {
            val resolvedAction = selectedActionValue?.let { ActionButton(selectedActionLabel, it) }
            _frame.update { frame ->
                val shouldRetainAction = resolvedAction != null && frame.decisionPrompt == null
                frame.copy(
                    busy = false,
                    activeActionValue = if (shouldRetainAction) resolvedAction.value else null,
                    selectedAction = if (shouldRetainAction) resolvedAction else null,
                )
            }
            _frame.value.activeTaskId?.let { taskId ->
                taskStates[taskId]?.let { task ->
                    taskStates[taskId] = _frame.value.captureTaskState(task)
                }
            }
        }

        private fun applyOneHourFlowEffects(
            effects: List<OneHourFlowEffect>,
            timeText: String,
        ) {
            effects.forEach { applyOneHourFlowEffect(it, timeText) }
        }

        private fun applyOneHourFlowEffect(
            effect: OneHourFlowEffect,
            timeText: String,
        ) {
            when (effect) {
                is OneHourFlowEffect.CreateTask -> upsertTask(effect.seed.toTaskState(timeText))
                is OneHourFlowEffect.UpdateTask -> applyScenarioTaskUpdate(
                    update = effect.update,
                    timeText = timeText,
                    activate = effect.activate,
                )
                is OneHourFlowEffect.ShowSystemLayer -> showSystemLayer(effect, timeText)
                is OneHourFlowEffect.ClearSystemLayer -> clearSystemLayer(effect.ids)
                OneHourFlowEffect.ClearActiveCall -> _frame.update { it.copy(activeCall = null) }
            }
        }

        private fun showSystemLayer(
            effect: OneHourFlowEffect.ShowSystemLayer,
            timeText: String,
        ) {
            _frame.update {
                it.copy(
                    systemNotification = AgentSystemNotification(
                        id = effect.id,
                        title = effect.title,
                        timeText = timeText,
                        body = effect.body,
                        actionLabel = effect.actionLabel,
                        callTranscriptText = effect.callTranscriptText,
                        callSessionId = effect.callSessionId,
                        personaId = effect.personaId,
                    ),
                )
            }
        }

        private fun clearSystemLayer(ids: Set<String>) {
            _frame.update {
                it.copy(
                    systemNotification = if (it.systemNotification?.id in ids) null else it.systemNotification,
                )
            }
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
                selectedAction = selectedAction,
                finalSummary = finalSummary,
                error = error,
            )

        private fun AgentTaskState.applyToFrame(frame: AgentExperienceFrame): AgentExperienceFrame =
            frame.copy(
                activeTaskId = id,
                activeTaskTitle = title,
                activeTaskSubtitle = subtitle,
                taskCards = taskCardsFor(activeId = id),
                statusLabel = statusLabelFor(status),
                hasStarted = true,
                conversationItems = conversationItems,
                taskLogs = taskLogs,
                participants = participants,
                progressLine = progressLine,
                timeline = timeline,
                stageCards = stageCards,
                decisionPrompt = decisionPrompt,
                activeActionValue = activeActionValue,
                selectedAction = selectedAction,
                finalSummary = finalSummary,
                error = error,
            )

        private fun statusLabelFor(status: AgentTimelineStatus): String =
            when (status) {
                AgentTimelineStatus.BLOCKED -> "Waiting for decision"
                AgentTimelineStatus.DONE -> "Complete"
                AgentTimelineStatus.FAILED -> "Error"
                else -> "Running"
            }

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
            withCanonicalParticipant(participant).takeLast(MAX_PARTICIPANTS)

        private fun appendSystemEvent(
            existing: List<AgentSystemEvent>,
            event: SystemRuntimeEvent,
        ): List<AgentSystemEvent> =
            (
                existing + AgentSystemEvent(
                    id = event.id,
                    timeText = blueprintTimeText(event.occurredAt),
                    source = event.source,
                    eventTypeText = event.toDisplayEventTypeText(),
                    title = event.title,
                    body = event.body,
                )
            ).takeLast(MAX_SYSTEM_EVENTS)

        private fun SystemRuntimeEvent.toDisplayEventTypeText(): String =
            when (this) {
                is IncomingSmsEvent -> "短信"
                is IncomingCallEvent -> "来电"
                is CallEndedEvent -> "通话"
                is ReminderFiredEvent -> "提醒"
                is AlarmFiredEvent -> "闹钟"
                is IncomingEmailEvent -> "邮件"
                is EmailSentEvent -> "邮件"
                is WebQueryResultEvent -> "网页结果"
                else -> "通知"
            }

        private fun selectedAppointmentIsAfternoon(): Boolean =
            OneHourScenarioPolicy.isAfternoonBathOnly(latestAgentDecisionIntent)

        private fun selectedAppointmentIsAlternative(): Boolean =
            OneHourScenarioPolicy.isAlternativeService(latestAgentDecisionIntent)

        private fun parseToolData(content: String): JSONObject? {
            val json = content.substringAfter('\n', missingDelimiterValue = "").trim()
            if (json.isBlank()) return null
            return runCatching { JSONObject(json) }.getOrNull()
        }

        private fun recordScenarioMilestones(
            tool: String,
            content: String,
        ) {
            if (!OneHourScenarioPolicy.matches(scenario.scenarioId)) return
            if (!isSystemRuntimeTool(tool)) return
            val data = parseToolData(content) ?: return
            scenarioRunTracker.recordSystemRuntimeData(data)
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
            if (!OneHourScenarioPolicy.matches(scenario.scenarioId)) return emptyList()
            return OneHourScenarioPolicy.actionCandidates(promptText)
                .map { ActionButton(label = it.label, value = it.value) }
        }

        private fun shouldSuppressResolvedScenarioPrompt(text: String): Boolean {
            if (!OneHourScenarioPolicy.matches(scenario.scenarioId)) return false
            return OneHourScenarioPolicy.shouldSuppressResolvedPrompt(text, latestAgentDecisionIntent)
        }

        private fun compactActionLabel(
            label: String,
            value: String,
        ): String {
            if (!OneHourScenarioPolicy.matches(scenario.scenarioId)) return label
            return OneHourScenarioPolicy.compactActionLabel(label, value)
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
            if (OneHourScenarioPolicy.matches(scenario.scenarioId)) {
                return OneHourScenarioPolicy.compactDecisionPromptText(text) ?: text
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
            if (OneHourScenarioPolicy.matches(scenario.scenarioId) && OneHourScenarioPolicy.isNonActionFact(value)) return ""
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

        private fun appendConversationItems(
            existing: List<AgentConversationItem>,
            values: List<AgentConversationItem>,
        ): List<AgentConversationItem> {
            if (values.isEmpty()) return existing
            val merged = values.fold(existing) { items, value ->
                if (items.any { it.role == value.role && it.text == value.text }) {
                    items
                } else {
                    items + value
                }
            }
            return merged.takeLast(MAX_CONVERSATION_ITEMS)
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
            val merged = values.fold(existing) { logs, value ->
                if (logs.any { it.isSameTaskLog(value) }) {
                    logs
                } else {
                    logs + value
                }
            }
            return merged.takeLast(MAX_TASK_LOGS)
        }

        private fun AgentTaskLog.isSameTaskLog(other: AgentTaskLog): Boolean {
            if (timeText != other.timeText) return false
            if (text == other.text) return true
            val observedPayload = text.observedFactPayload() ?: return false
            return observedPayload == other.text.observedFactPayload()
        }

        private fun String.observedFactPayload(): String? {
            val value = trim()
            val isObservedFact =
                value.startsWith("收到") ||
                    value.startsWith("触发提醒") ||
                    value.contains("通话结束")
            if (!isObservedFact) return null
            return value.substringAfter('：', missingDelimiterValue = "")
                .trim()
                .takeIf { it.length >= 4 }
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
            private const val SCENARIO_PLANNER_TIMEOUT_MS = 45_000L
            private const val CALL_ROLE_MODEL_TIMEOUT_MS = 20_000L
            private const val CLOCK_LOOP_INTERVAL_MS = 1_000L
            private const val CLOCK_ADVANCE_STEPS = 30
            private const val CLOCK_ADVANCE_STEP_MS = 1_000L
            private const val TOOL_ROUND_LIMIT_PREFIX = "Stopped: too many tool call rounds"
            private const val SCRIPTED_ACTION_PREFIX = "MULTI:"
            private const val ONE_HOUR_SCENARIO_ID = "one_hour_aio"
            private val INITIAL_SCENARIO_CLOCK: LocalDateTime = LocalDateTime.of(2027, 4, 25, 13, 0)
            private val SCENARIO_DAY_LABELS = listOf("Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri")
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
