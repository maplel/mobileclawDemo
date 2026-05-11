package com.mobilebot.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilebot.bus.MessageBus
import com.mobilebot.data.settings.UserSettingsRepository
import com.mobilebot.domain.AgentLoop
import com.mobilebot.domain.ForegroundController
import com.mobilebot.domain.interaction.ActionPromptCodec
import com.mobilebot.domain.todo.TodoListCodec
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
        private val decisionIntentNormalizer: ScenarioDecisionIntentNormalizer,
    ) : ViewModel() {
        private var currentChatId: String? = null
        private var eventCounter = 0
        private var continuationCount = 0
        private var scenarioClock = INITIAL_SCENARIO_CLOCK
        private var deferredRetriggerInProgress = false
        private var latestScenarioDecisionIntent: ScenarioDecisionIntent? = null
        private var pendingSelectedActionLabel: String? = null
        private var lastGroomingPaymentAmount: String? = null
        private val groomingMilestones = mutableSetOf<GroomingMilestone>()

        private val scenario = AgentScenarioConfig(
            scenarioId = "pet-grooming",
            title = "Kylin Grooming Assistant",
            skillName = "pet-grooming",
            expectedSignals = listOf(
                "User profile, places, and preferences are available.",
                "Service messages can be sent and received.",
                "Home, route, and salon locations are ready.",
                "Driver and salon contacts are available.",
                "Progress updates can be delivered quietly.",
            ),
            triggerText = """
                Start the `pet-grooming` scenario skill as the scheduled Saturday precheck for Kylin's recurring grooming.
                Current scenario clock: Saturday 2027-04-25 for precheck, with grooming expected on Sunday 2027-04-26. Do not invent another date. Use 2027-04-26 as the payment and accounting date for this run.
                All user-facing assistant messages, action candidates, plan titles, plan steps, SMS bodies, reminders, notifications, payment descriptions, and accounting descriptions must be Chinese. Proper nouns such as PetSmart, Driver, Kylin, CNY, and NT may stay as written. Do not write English prose on user-facing surfaces.
                Invoke `use_skill` with skill_name `pet-grooming`, begin at the weekly precheck decision point, then load user memory, create a concise plan, resolve Y's preferred grooming shop PetSmart through device_system service_call with serviceId `pet_salon_search` and query `PetSmart`, consider another shop only if PetSmart cannot satisfy the requested timing or service scope, use system_send_sms and system_wait_for_sms for SMS conversations, and use device_system for remaining phone and OS capabilities.
                After Y keeps the appointment, call device_system service_call with serviceId `pet_salon_search` and action `get_pet_shop_detail` before sending any SMS to PetSmart. Use the service result for shop identity, address, contact details, service items, and published prices. Kylin is an extra-large Bernese Mountain Dog; use the published service price for Kylin's selected size and service scope as the expected fee for payment/accounting. Do not use full grooming/styling price, small-dog pricing, or pickup coordination fees unless the selected scope changes. Confirm available times, final service scope, and booking status by SMS with PetSmart. Never ask PetSmart for final price in the normal path, and do not include prices, totals, fee details, or CNY in normal PetSmart SMS.
                A PetSmart message that a time is available is not the final booking confirmation. After Y chooses the 9:00 option, the next operational step is only: send PetSmart a confirmation SMS for that slot, then call system_wait_for_sms for PetSmart's booking confirmation. Do not call system_send_sms for Driver until PetSmart's inbound booking-confirmed SMS exists in history.
                Do not resolve or message Driver until PetSmart has confirmed the final selected booking slot by inbound SMS. Driver is Y's private driver: for a 9:00 PetSmart appointment, coordinate Driver to pick Kylin up from Y's home at 8:30 and deliver him to PetSmart by 9:00; for an accepted afternoon bath-only slot after 17:00, coordinate Driver to pick Kylin up at 16:30 and deliver him to PetSmart by 17:00. Do not include a predicted grooming finish time, return pickup time, or home-arrival instruction in this first Driver SMS. After Driver confirms the first pickup plan, do not send Driver another SMS asking for future milestone reports; the next listener must be system_wait_for_sms from Driver for Driver's delivery-to-PetSmart update. Do not wait on PetSmart for arrival or progress until Driver has reported Kylin was delivered to PetSmart. Ask Driver to pick up from PetSmart only after PetSmart says Kylin is finished, ready, or gives a revised pickup time after a delay.
                After PetSmart confirms the booking, contact Driver directly without asking Y again. Driver SMS is addressed to Driver; start it with `司机您好` or `您好`, never `Y您好` or similar Y-facing greetings.
                After sending the first Driver SMS, first wait for Driver's home-pickup confirmation using that SMS listener. A reply such as "收到，我8:30来接 Kylin" satisfies only pickup confirmation, not delivery. After Driver confirms the pickup plan, create a long_reminder for the selected next-day departure time (04/26 08:30 for a 9:00 appointment, or 04/26 16:30 for an afternoon 17:00 appointment) with a Chinese title like `麒麟出发洗澡`; this is reminder creation and must not be treated as actual departure. After that, call a second system_wait_for_sms from Driver with context "Driver delivery-to-PetSmart update" and no old watchId. Only an inbound Driver message that says Kylin was delivered, arrived, 到店, 送到, or 送达 satisfies delivery-to-PetSmart.
                After Y answers a declared decision point, continue through routine downstream actions without asking again: booking confirmation, driver home pickup coordination, reminders, Driver delivery-to-PetSmart monitoring, PetSmart progress/finish monitoring, Driver return coordination, home confirmation, payment, accounting, and final summary. Pause only at declared decision points or real blockers, and finish only after Kylin is confirmed home, payment is complete, and the expense is recorded. Do not ask Y whether to create routine reminders. For home confirmation, send Driver a short SMS asking him to confirm once Kylin is home, then wait on that returned SMS listener. Do not pay before an inbound Driver SMS explicitly confirms home arrival. PetSmart progress and finish updates must be listened from PetSmart, not delegated to Driver.
                If Y selects a concrete grooming time or service option, treat that as confirmation to proceed with that option. Do not ask for a second confirmation of the same choice.
                Do not end with a promise to monitor later while the workflow is still open. If the next step is monitoring, immediately call system_wait_for_sms for the next expected PetSmart or Driver signal.
            """.trimIndent(),
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
                delay(AUTO_TRIGGER_DELAY_MS)
                if (!_frame.value.hasStarted && !_frame.value.busy) {
                    startScenario(showUserBubble = false)
                }
            }
            viewModelScope.launch {
                while (true) {
                    delay(SCENARIO_CLOCK_TICK_MS)
                    tickScenarioClock()
                }
            }
        }

        fun startScenario() {
            startScenario(showUserBubble = true)
        }

        private fun startScenario(showUserBubble: Boolean) {
            if (_frame.value.busy) return
            if (_frame.value.hasStarted && _frame.value.error == null && _frame.value.finalSummary == null) return
            val runChatId = "run-${scenario.scenarioId}-${UUID.randomUUID().toString().take(8)}"
            currentChatId = runChatId
            eventCounter = 0
            continuationCount = 0
            latestScenarioDecisionIntent = null
            pendingSelectedActionLabel = null
            lastGroomingPaymentAmount = null
            groomingMilestones.clear()
            val triggerText = buildScenarioTriggerText(scenarioClock)
            val baseFrame = AgentExperienceFrame.initial(scenario).withClock(scenarioClock)
            val openingItems =
                if (showUserBubble) {
                    baseFrame.conversationItems + AgentConversationItem(
                        id = nextId("conversation"),
                        role = AgentConversationRole.USER,
                        text = "OK",
                    )
                } else {
                    baseFrame.conversationItems
                }
            _frame.value = baseFrame.copy(
                statusLabel = "Starting",
                busy = true,
                hasStarted = true,
                conversationItems = openingItems,
                taskLogs = listOf(
                    AgentTaskLog(
                        id = nextId("task"),
                        timeText = blueprintTimeText(scenarioClock),
                        text = "创建麒麟日常洗护任务。",
                    ),
                ),
                progressLine = AgentProgressLine(
                    label = "Starting",
                    detail = "Preparing the coordination flow",
                ),
                timeline = listOf(
                    AgentTimelineEvent(
                        id = nextId("run"),
                        title = "Workflow started",
                        detail = "The agent is coordinating Kylin's grooming flow.",
                        status = AgentTimelineStatus.RUNNING,
                    ),
                ),
                debugTrace = listOf("user -> ${triggerText.lineSequence().first()}"),
            )

            viewModelScope.launch {
                runAgentTurn(runChatId, triggerText)
            }
        }

        fun chooseDecision(action: ActionButton) {
            val chatId = currentChatId ?: return
            if (_frame.value.busy) return
            continueWithNormalizedDecision(
                chatId = chatId,
                displayText = action.label,
                rawText = action.value,
                selectedActionValue = action.value,
            )
        }

        fun submitDecisionText(text: String) {
            val value = text.trim()
            if (value.isBlank()) return
            val chatId = currentChatId ?: return
            if (_frame.value.busy) return
            continueWithNormalizedDecision(
                chatId = chatId,
                displayText = value,
                rawText = value,
                selectedActionValue = null,
            )
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
                    ScenarioDecisionInput(
                        scenarioId = scenario.scenarioId,
                        promptText = prompt?.text.orEmpty(),
                        presentedActions = prompt?.actions.orEmpty(),
                        displayText = displayText,
                        rawText = rawText,
                    ),
                )
                latestScenarioDecisionIntent = normalized.intent
                _frame.update {
                    it.copy(
                        statusLabel = "Resuming",
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
                runAgentTurn(chatId, normalized.agentText)
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
                        timeline = it.timeline + AgentTimelineEvent(
                            id = nextId("error"),
                            title = "Agent error",
                            detail = e.message ?: e.javaClass.simpleName,
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

        private fun buildScenarioTriggerText(clock: LocalDateTime): String {
            val precheckDate = clock.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val groomingDate = clock.toLocalDate().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
            val dayName = scenarioDayName(clock, full = true)
            val groomingDayName = scenarioDayName(clock.plusDays(1), full = true)
            val time = clock.toLocalTime().format(CLOCK_TIME_FORMATTER)
            return """
                Start the `pet-grooming` scenario skill as the scheduled Saturday precheck for Kylin's recurring grooming.
                Current scenario clock: $dayName $precheckDate $time for precheck, with grooming expected on $groomingDayName $groomingDate. Do not invent another date. Use $groomingDate as the payment and accounting date for this run.
                All user-facing assistant messages, action candidates, plan titles, plan steps, SMS bodies, reminders, notifications, payment descriptions, and accounting descriptions must be Chinese. Proper nouns such as PetSmart, Driver, Kylin, CNY, and NT may stay as written. Do not write English prose on user-facing surfaces.
                Invoke `use_skill` with skill_name `pet-grooming`, begin at the weekly precheck decision point, then load user memory, create a concise plan, resolve Y's preferred grooming shop PetSmart through device_system service_call with serviceId `pet_salon_search` and query `PetSmart`, consider another shop only if PetSmart cannot satisfy the requested timing or service scope, use system_send_sms and system_wait_for_sms for SMS conversations, and use device_system for remaining phone and OS capabilities.
                After Y keeps the appointment, call device_system service_call with serviceId `pet_salon_search` and action `get_pet_shop_detail` before sending any SMS to PetSmart. Use the service result for shop identity, address, contact details, service items, and published prices. Kylin is an extra-large Bernese Mountain Dog; use the published service price for Kylin's selected size and service scope as the expected fee for payment/accounting. Do not use full grooming/styling price, small-dog pricing, or pickup coordination fees unless the selected scope changes. Confirm available times, final service scope, and booking status by SMS with PetSmart. Never ask PetSmart for final price in the normal path, and do not include prices, totals, fee details, or CNY in normal PetSmart SMS.
                A PetSmart message that a time is available is not the final booking confirmation. After Y chooses the 9:00 option, the next operational step is only: send PetSmart a confirmation SMS for that slot, then call system_wait_for_sms for PetSmart's booking confirmation. Do not call system_send_sms for Driver until PetSmart's inbound booking-confirmed SMS exists in history.
                Do not resolve or message Driver until PetSmart has confirmed the final selected booking slot by inbound SMS. Driver is Y's private driver: for a 9:00 PetSmart appointment, coordinate Driver to pick Kylin up from Y's home at 8:30 and deliver him to PetSmart by 9:00; for an accepted afternoon bath-only slot after 17:00, coordinate Driver to pick Kylin up at 16:30 and deliver him to PetSmart by 17:00. Do not include a predicted grooming finish time, return pickup time, or home-arrival instruction in this first Driver SMS. After Driver confirms the first pickup plan, do not send Driver another SMS asking for future milestone reports; the next listener must be system_wait_for_sms from Driver for Driver's delivery-to-PetSmart update. Do not wait on PetSmart for arrival or progress until Driver has reported Kylin was delivered to PetSmart. Ask Driver to pick up from PetSmart only after PetSmart says Kylin is finished, ready, or gives a revised pickup time after a delay.
                After PetSmart confirms the booking, contact Driver directly without asking Y again. Driver SMS is addressed to Driver; start it with `司机您好` or `您好`, never `Y您好` or similar Y-facing greetings.
                After sending the first Driver SMS, first wait for Driver's home-pickup confirmation using that SMS listener. A reply such as "收到，我8:30来接 Kylin" satisfies only pickup confirmation, not delivery. After Driver confirms the pickup plan, create a long_reminder for the selected next-day departure time (04/26 08:30 for a 9:00 appointment, or 04/26 16:30 for an afternoon 17:00 appointment) with a Chinese title like `麒麟出发洗澡`; this is reminder creation and must not be treated as actual departure. After that, call a second system_wait_for_sms from Driver with context "Driver delivery-to-PetSmart update" and no old watchId. Only an inbound Driver message that says Kylin was delivered, arrived, 到店, 送到, or 送达 satisfies delivery-to-PetSmart.
                After Y answers a declared decision point, continue through routine downstream actions without asking again: booking confirmation, driver home pickup coordination, reminders, Driver delivery-to-PetSmart monitoring, PetSmart progress/finish monitoring, Driver return coordination, home confirmation, payment, accounting, and final summary. Pause only at declared decision points or real blockers, and finish only after Kylin is confirmed home, payment is complete, and the expense is recorded. Do not ask Y whether to create routine reminders. For home confirmation, send Driver a short SMS asking him to confirm once Kylin is home, then wait on that returned SMS listener. Do not pay before an inbound Driver SMS explicitly confirms home arrival. PetSmart progress and finish updates must be listened from PetSmart, not delegated to Driver.
                If Y selects a concrete grooming time or service option, treat that as confirmation to proceed with that option. Do not ask for a second confirmation of the same choice.
                Do not end with a promise to monitor later while the workflow is still open. If the next step is monitoring, immediately call system_wait_for_sms for the next expected PetSmart or Driver signal.
                Do not send user-facing prose about loading memory, creating a plan, tool usage, or decision point ids. The first user-facing message should be only the weekly grooming precheck question with short action candidates.
            """.trimIndent()
        }

        private fun continuationPromptFor(frame: AgentExperienceFrame): String? {
            if (frame.scenario.scenarioId != "pet-grooming") return null
            if (frame.decisionPrompt != null || frame.error != null) return null
            if (frame.finalSummary.isNullOrBlank()) return null
            if (groomingDeferred(frame)) return null
            if (groomingClosureSatisfied(frame)) return null
            return GROOMING_CONTINUATION_PROMPT
        }

        private fun groomingDeferred(frame: AgentExperienceFrame): Boolean {
            if (frame.scenario.scenarioId != "pet-grooming") return false
            return latestScenarioDecisionIntent == ScenarioDecisionIntent.PetGroomingDeferCurrentWeek
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
                latestScenarioDecisionIntent = null
                pendingSelectedActionLabel = null
                lastGroomingPaymentAmount = null
                groomingMilestones.clear()
                _frame.value = AgentExperienceFrame.initial(scenario).withClock(scenarioClock)
                deferredRetriggerInProgress = false
                delay(AUTO_TRIGGER_DELAY_MS)
                if (!_frame.value.hasStarted && !_frame.value.busy) {
                    startScenario(showUserBubble = false)
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
                clockDateText = "${clock.toLocalDate().format(CLOCK_DATE_FORMATTER)} ${scenarioDayName(clock, full = false)}",
            )

        private fun scenarioDayName(
            clock: LocalDateTime,
            full: Boolean,
        ): String {
            val dayOffset = Duration.between(INITIAL_SCENARIO_CLOCK.toLocalDate().atStartOfDay(), clock.toLocalDate().atStartOfDay())
                .toDays()
                .let { Math.floorMod(it, SCENARIO_DAY_NAMES.size.toLong()) }
                .toInt()
            return if (full) SCENARIO_DAY_NAMES[dayOffset].first else SCENARIO_DAY_NAMES[dayOffset].second
        }

        private fun groomingClosureSatisfied(frame: AgentExperienceFrame): Boolean {
            if (frame.scenario.scenarioId != "pet-grooming") return false
            return groomingMilestones.containsAll(
                setOf(
                    GroomingMilestone.HOME_CONFIRMED,
                    GroomingMilestone.PAYMENT_COMPLETED,
                    GroomingMilestone.EXPENSE_RECORDED,
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
                        base.copy(
                            statusLabel = "Executing",
                            progressLine = AgentProgressLine(
                                label = "Executing",
                                detail = toolProgressDetail(tool),
                                completed = completedStageCount(base),
                                total = totalStageCount(base),
                            ),
                            timeline = base.timeline + event,
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
                                completed = completedStageCount(base),
                                total = totalStageCount(base),
                            ),
                            timeline = base.timeline + AgentTimelineEvent(
                                id = nextId("decision"),
                                title = "Decision point",
                                detail = displayText,
                                status = AgentTimelineStatus.BLOCKED,
                            ),
                        )
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
                            val decisionPrompt = inferDecisionPrompt(content)
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
                                    isTransientGroomingNarration(content)
                                val visibleBase = base.withPendingSelectedAction()
                                visibleBase.copy(
                                    finalSummary = content,
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
            if (scenario.scenarioId == "pet-grooming" && isTransientGroomingNarration(content)) return null
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
                    isTransientGroomingNarration(content) -> null
                scenario.scenarioId == "pet-grooming" &&
                    isRoutineReminderQuestion(content) -> null
                scenario.scenarioId == "pet-grooming" &&
                    lower.contains("petsmart") &&
                    content.contains("最终价格") -> "已向 PetSmart 发送短信，确认周日可选时段和服务内容。"
                groomingCompletionText(lower) -> compactGroomingCompletionText(content)
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

        private fun isTransientGroomingNarration(text: String): Boolean =
            text.contains("正在发送") ||
                text.contains("正在等待") ||
                text.contains("等待 PetSmart 回复") ||
                text.contains("稍后将等待") ||
                text.contains("稍后将自动处理") ||
                text.contains("已向 PetSmart 发送") ||
                text.contains("发送预约咨询短信") ||
                text.contains("详细信息已加载") ||
                (text.contains("Driver 已识别") && text.contains("PetSmart")) ||
                (text.contains("已确认：") && text.contains("PetSmart")) ||
                text.contains("现在向 PetSmart 发送短信") ||
                text.contains("是否现在就联系司机") ||
                text.contains("接下来将联系您的私人司机") ||
                text.contains("已启动监听") ||
                text.contains("仍在监听") ||
                text.contains("当前状态为") ||
                text.contains("按流程优先级") ||
                text.contains("首个缺失") ||
                text.contains("实际属于") ||
                text.contains("下一步：")

        private fun offersDeferralAsOption(text: String): Boolean =
            text.contains("`改天再说`") ||
                text.lineSequence().any { line ->
                    val trimmed = line.trimStart()
                    (trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.startsWith("•")) &&
                        trimmed.contains("改天")
                }

        private fun groomingCompletionText(text: String): Boolean =
            !text.contains("pending") &&
                !text.contains("home confirmation pending") &&
                !text.contains("到家确认待") &&
                !text.contains("等待司机确认") &&
                (
                    text.contains("all steps complete") ||
                        text.contains("paid and accounted") ||
                        text.contains("payment completed") ||
                        text.contains("closed loop") ||
                        text.contains("流程闭环") ||
                        text.contains("全流程完成") ||
                        (text.contains("支付") && (text.contains("记账") || text.contains("账务")))
                ) &&
                (text.contains("到家") || text.contains("home")) &&
                (text.contains("kylin") || text.contains("麒麟")) &&
                (text.contains("petsmart") || text.contains("pet smart"))

        private fun isRoutineReminderQuestion(text: String): Boolean {
            val lower = text.lowercase()
            return scenario.scenarioId == "pet-grooming" &&
                (text.contains("需要我") || lower.contains("do you want me")) &&
                (text.contains("提醒") || lower.contains("reminder")) &&
                !looksLikeDecisionRequest(text)
        }

        private fun compactGroomingCompletionText(text: String): String {
            val amount = lastGroomingPaymentAmount
                ?: Regex("""(?:¥|￥)\s?\d+(?:\.\d+)?|(?:cny|rmb|yuan)\s*\d+(?:\.\d+)?|\d+(?:\.\d+)?\s*(?:yuan|rmb|cny|元)""", RegexOption.IGNORE_CASE)
                    .find(text)
                    ?.value
                    ?.replace(Regex("""\s+"""), "")
                    ?.let(::normalizeAmountText)
            val feeText = amount?.let { "洗护费用 $it" } ?: "洗护费用"
            return "麒麟已到家，$feeText 已支付并完成记账。"
        }

        private fun normalizeAmountText(value: String): String {
            val number = Regex("""\d+(?:\.\d+)?""").find(value)?.value ?: return value
            return "${number}元"
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
            _frame.update { it.copy(systemNotification = null) }
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
                    val body = reminder?.optString("body").orEmpty()
                    val scheduledFor = reminder?.optString("scheduledFor").orEmpty().ifBlank { "按计划时间" }
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
            scenarioClockForToolResult(tool.orEmpty(), content)
                ?.let(::blueprintTimeText)
                ?: blueprintTimeText(scenarioClock)

        private fun blueprintTimeText(clock: LocalDateTime): String =
            clock.format(BLUEPRINT_TIME_FORMATTER)

        private fun blueprintTimeFor(eventIndex: Int): String =
            BLUEPRINT_TIMES.getOrElse(eventIndex) {
                val minute = 30 + eventIndex
                if (minute < 60) "04/25 18:${minute.toString().padStart(2, '0')}" else "04/26 20:01"
            }

        private fun serviceTaskLogText(data: JSONObject?): String {
            val serviceId = data?.optString("serviceId").orEmpty()
            val action = data?.optString("action").orEmpty()
            return when {
                serviceId == "pet_salon_search" -> {
                    val shopName = firstNamedEntity(data).ifBlank { "PetSmart" }
                    "添加 $shopName 到参与方。"
                }
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
                contact.equals("PetSmart", ignoreCase = true) -> "PetSmart"
                contact.equals("Driver", ignoreCase = true) -> "Driver"
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
            val body = source.optString("body").ifBlank { title }
            val timeText = source.optString("scheduledFor")
                .ifBlank { source.optString("time") }
                .ifBlank { "Now" }
            return AgentSystemNotification(
                id = nextId("notice"),
                title = title,
                timeText = timeText,
                body = body,
            )
        }

        private fun AgentExperienceFrame.withScenarioEventClock(
            tool: String,
            content: String,
        ): AgentExperienceFrame {
            if (scenario.scenarioId != "pet-grooming") return this
            val eventClock = scenarioClockForToolResult(tool, content)
            if (eventClock != null && eventClock.isAfter(scenarioClock)) {
                scenarioClock = eventClock
            }
            return withClock(scenarioClock)
        }

        private fun tickScenarioClock() {
            if (deferredRetriggerInProgress) return
            scenarioClock = scenarioClock.plusMinutes(1)
            _frame.update { it.withClock(scenarioClock) }
        }

        private fun scenarioClockForToolResult(
            tool: String,
            content: String,
        ): LocalDateTime? {
            if (tool !in setOf("device_system", "system_search_contacts", "system_send_sms", "system_wait_for_sms")) {
                return null
            }
            val message = content.substringBefore('\n').trim()
            val data = parseToolData(content)
            val sms = data?.optJSONObject("sms")
            val smsBody = sms?.optString("message").orEmpty()
            val smsEventType = sms?.optString("eventType").orEmpty()
            val contact = sms?.optString("displayName").orEmpty()
                .ifBlank { sms?.optString("from").orEmpty() }
                .ifBlank { sms?.optString("to").orEmpty() }
            val serviceId = data?.optString("serviceId").orEmpty()
            val serviceAction = data?.optString("action").orEmpty()

            return when {
                message.startsWith("Service response", ignoreCase = true) && serviceId == "pet_salon_search" ->
                    if (serviceAction.contains("detail", ignoreCase = true)) {
                        LocalDateTime.of(2027, 4, 25, 13, 1)
                    } else {
                        LocalDateTime.of(2027, 4, 25, 13, 1)
                    }
                message.startsWith("Contacts returned", ignoreCase = true) -> {
                    val names = contactNamesFromData(data)
                    if (names.contains("Driver")) {
                        LocalDateTime.of(2027, 4, 25, 13, 5)
                    } else {
                        LocalDateTime.of(2027, 4, 25, 13, 3)
                    }
                }
                message.startsWith("Payment completed", ignoreCase = true) ->
                    LocalDateTime.of(2027, 4, 26, 20, 0)
                message.startsWith("Expense recorded", ignoreCase = true) ->
                    LocalDateTime.of(2027, 4, 26, 20, 1)
                message.startsWith("Long reminder created", ignoreCase = true) ||
                    message.startsWith("Reminder created", ignoreCase = true) ->
                    null
                message.startsWith("Notification posted", ignoreCase = true) ->
                    scenarioClockForNotification(data)
                message.startsWith("SMS sent", ignoreCase = true) ->
                    scenarioClockForOutboundSms(contact, smsBody)
                message.startsWith("Inbound SMS received", ignoreCase = true) ->
                    scenarioClockForInboundSms(contact, smsBody, smsEventType)
                else -> null
            }
        }

        private fun scenarioClockForOutboundSms(
            contact: String,
            body: String,
        ): LocalDateTime? =
            when {
                contact.contains("PetSmart", ignoreCase = true) &&
                    (body.contains("确认预约") || body.contains("确认约") || body.contains("确认周日") || body.contains("确认本周日")) ->
                    LocalDateTime.of(2027, 4, 25, 13, 4)
                contact.contains("PetSmart", ignoreCase = true) && body.contains("9:00") ->
                    LocalDateTime.of(2027, 4, 25, 13, 2)
                contact.contains("PetSmart", ignoreCase = true) && (body.contains("下午") || body.contains("5点")) ->
                    LocalDateTime.of(2027, 4, 25, 13, 4)
                contact.contains("Driver", ignoreCase = true) && body.contains("8:30") ->
                    LocalDateTime.of(2027, 4, 25, 13, 5)
                contact.contains("Driver", ignoreCase = true) &&
                    (body.contains("16:30") || body.contains("17:00") || body.contains("下午")) ->
                    LocalDateTime.of(2027, 4, 25, 13, 5)
                contact.contains("Driver", ignoreCase = true) && (body.contains("19:20") || body.contains("七点二十")) ->
                    LocalDateTime.of(2027, 4, 26, 19, 20)
                contact.contains("Driver", ignoreCase = true) && (body.contains("到家") || body.contains("home", ignoreCase = true)) ->
                    LocalDateTime.of(2027, 4, 26, 20, 0)
                else -> null
            }

        private fun scenarioClockForInboundSms(
            contact: String,
            body: String,
            eventType: String,
        ): LocalDateTime? =
            when {
                eventType == "petsmart_availability_options" ->
                    LocalDateTime.of(2027, 4, 25, 13, 3)
                eventType == "petsmart_booking_confirmed" ->
                    LocalDateTime.of(2027, 4, 25, 13, 4)
                eventType == "petsmart_arrival_confirmed" ->
                    selectedAppointmentArrivalClock()
                eventType == "petsmart_delayed_pickup" ->
                    selectedAppointmentDelayClock()
                eventType == "petsmart_grooming_finished" ->
                    LocalDateTime.of(2027, 4, 26, 19, 20)
                eventType == "driver_home_pickup_confirmed" ->
                    selectedDriverPickupClock(body)
                eventType == "driver_delivered_to_petsmart" ->
                    selectedAppointmentArrivalClock()
                eventType == "driver_return_pickup_confirmed" ->
                    LocalDateTime.of(2027, 4, 26, 19, 20)
                eventType == "driver_return_eta" ->
                    LocalDateTime.of(2027, 4, 26, 19, 35)
                eventType == "driver_home_arrival" ->
                    LocalDateTime.of(2027, 4, 26, 20, 0)
                contact.contains("PetSmart", ignoreCase = true) &&
                    (body.contains("上午九点") || body.contains("上午9点") || body.contains("9点")) ->
                    LocalDateTime.of(2027, 4, 25, 13, 3)
                contact.contains("PetSmart", ignoreCase = true) &&
                    (body.contains("预约已确认") || body.contains("已确认")) ->
                    LocalDateTime.of(2027, 4, 25, 13, 4)
                contact.contains("Driver", ignoreCase = true) &&
                    (body.contains("8:30") || body.contains("来接")) ->
                    selectedDriverPickupClock(body)
                contact.contains("Driver", ignoreCase = true) &&
                    (body.contains("送到了") || body.contains("已送到") || body.contains("到店")) ->
                    selectedAppointmentArrivalClock()
                contact.contains("PetSmart", ignoreCase = true) &&
                    (body.contains("19:20") || body.contains("七点二十") || body.contains("晚一点")) ->
                    LocalDateTime.of(2027, 4, 26, 16, 30)
                contact.contains("Driver", ignoreCase = true) &&
                    (body.contains("19:20") || body.contains("去 PetSmart 接") || body.contains("去PetSmart接")) ->
                    LocalDateTime.of(2027, 4, 26, 19, 20)
                contact.contains("Driver", ignoreCase = true) &&
                    (body.contains("已经到家") || body.contains("到家了") || body.contains("已到家")) ->
                    LocalDateTime.of(2027, 4, 26, 20, 0)
                else -> null
            }

        private fun scenarioClockForNotification(data: JSONObject?): LocalDateTime? {
            val notification = data?.optJSONObject("notification") ?: return null
            val text = "${notification.optString("title")} ${notification.optString("body")}"
            return when {
                text.contains("16:30") || text.contains("下午4:30") ->
                    LocalDateTime.of(2027, 4, 26, 16, 30)
                text.contains("8:30") || text.contains("08:30") ->
                    LocalDateTime.of(2027, 4, 26, 8, 30)
                text.contains("20:00") || text.contains("8:00") ->
                    LocalDateTime.of(2027, 4, 26, 20, 0)
                else -> null
            }
        }

        private fun selectedDriverPickupClock(body: String): LocalDateTime =
            when {
                body.contains("16:30") || body.contains("17:00") || body.contains("下午") || selectedAppointmentIsAfternoon() ->
                    LocalDateTime.of(2027, 4, 26, 16, 30)
                else ->
                    LocalDateTime.of(2027, 4, 26, 8, 30)
            }

        private fun selectedAppointmentArrivalClock(): LocalDateTime =
            if (selectedAppointmentIsAfternoon()) {
                LocalDateTime.of(2027, 4, 26, 17, 0)
            } else {
                LocalDateTime.of(2027, 4, 26, 9, 0)
            }

        private fun selectedAppointmentDelayClock(): LocalDateTime =
            if (selectedAppointmentIsAfternoon()) {
                LocalDateTime.of(2027, 4, 26, 18, 30)
            } else {
                LocalDateTime.of(2027, 4, 26, 16, 30)
            }

        private fun selectedAppointmentIsAfternoon(): Boolean =
            latestScenarioDecisionIntent == ScenarioDecisionIntent.PetGroomingBookAfternoonBathOnly

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
            val smsEventType = data.optJSONObject("sms")?.optString("eventType").orEmpty()
            when (smsEventType) {
                "driver_home_arrival" -> groomingMilestones += GroomingMilestone.HOME_CONFIRMED
            }

            val paymentStatus = data.optJSONObject("payment")?.optString("status").orEmpty()
            if (paymentStatus == "completed") {
                groomingMilestones += GroomingMilestone.PAYMENT_COMPLETED
                lastGroomingPaymentAmount = data.optJSONObject("payment")
                    ?.optString("amount")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::normalizeAmountText)
                    ?: lastGroomingPaymentAmount
            }

            val expenseStatus = data.optJSONObject("expense")?.optString("status").orEmpty()
            if (expenseStatus == "recorded") {
                groomingMilestones += GroomingMilestone.EXPENSE_RECORDED
                lastGroomingPaymentAmount = data.optJSONObject("expense")
                    ?.optString("amount")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::normalizeAmountText)
                    ?: lastGroomingPaymentAmount
            }
        }

        private fun parseActionButtons(json: String?, promptText: String): List<ActionButton> {
            val explicit =
                try {
                    ActionPromptCodec.parseJson(json).map {
                        ActionButton(label = compactScenarioActionLabel(it.label, it.value), value = it.value)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse action buttons", e)
                    emptyList()
                }
            if (explicit.isNotEmpty()) return explicit
            val scenarioActions = inferScenarioActions(promptText)
            if (scenarioActions.isNotEmpty()) return scenarioActions
            val inferred = ActionPromptCodec.resolveOptions(promptText).map {
                ActionButton(label = compactScenarioActionLabel(it.label, it.value), value = it.value)
            }
            return inferred.ifEmpty {
                listOf(
                    ActionButton("Continue", "Continue"),
                    ActionButton("Modify plan", "Modify plan"),
                    ActionButton("Rewrite", "Rewrite plan"),
                    ActionButton("Cancel", "Cancel"),
                )
            }
        }

        private fun inferScenarioActions(promptText: String): List<ActionButton> {
            if (scenario.scenarioId != "pet-grooming") return emptyList()
            val lower = promptText.lowercase()
            if (isAfternoonBathOnlyTradeoff(promptText)) {
                return listOf(
                    ActionButton("约下午5点", "USER_INTENT:pet_grooming.book_afternoon_bath_only"),
                    ActionButton("约9点", "USER_INTENT:pet_grooming.book_0900"),
                    ActionButton("换一家", "USER_INTENT:pet_grooming.find_alternative_shop"),
                )
            }
            if (isGroomingTimeTradeoff(promptText)) {
                return listOf(
                    ActionButton("约9点", "USER_INTENT:pet_grooming.book_0900"),
                    ActionButton("问下午", "USER_INTENT:pet_grooming.ask_afternoon"),
                    ActionButton("换一家", "USER_INTENT:pet_grooming.find_alternative_shop"),
                )
            }
            val asksToKeepGrooming =
                (lower.contains("grooming") || promptText.contains("美容") || promptText.contains("洗澡") || promptText.contains("洗护")) &&
                    (lower.contains("appointment") || lower.contains("sunday") || lower.contains("周日")) &&
                    (
                        lower.contains("regular") ||
                            lower.contains("weekly") ||
                            lower.contains("keep kylin") ||
                            lower.contains("proceed") ||
                            lower.contains("defer") ||
                            promptText.contains("按计划") ||
                            promptText.contains("请选择") ||
                            promptText.contains("继续吗") ||
                            lower.contains("照常") ||
                            lower.contains("改天")
                    )
            if (!asksToKeepGrooming) return emptyList()
            return listOf(
                ActionButton("好的", "好的"),
                ActionButton("改天再说", "改天再说"),
            )
        }

        private fun isGroomingTimeTradeoff(text: String): Boolean {
            val lower = text.lowercase()
            val hasPetSmart = lower.contains("petsmart") || text.contains("宠物店")
            val hasMorning = lower.contains("9:00") || lower.contains("9am") || text.contains("上午九点") || text.contains("上午9点")
            val hasAfternoon = lower.contains("afternoon") || lower.contains("5 pm") || lower.contains("17:00") ||
                text.contains("下午") || text.contains("五点") || text.contains("5点")
            val asksChoice = lower.contains("would you like") || lower.contains("which option") ||
                lower.contains("tradeoff") || text.contains("要约") || text.contains("选择")
            return hasPetSmart && hasMorning && hasAfternoon && asksChoice
        }

        private fun isAfternoonBathOnlyTradeoff(text: String): Boolean {
            val lower = text.lowercase()
            val hasPetSmart = lower.contains("petsmart") || text.contains("宠物店")
            val hasAfternoon = lower.contains("afternoon") || lower.contains("17:00") || lower.contains("5 pm") ||
                text.contains("下午") || text.contains("五点") || text.contains("5点")
            val hasBathOnly = lower.contains("bath-only") || lower.contains("bath only") ||
                text.contains("只洗澡") || text.contains("不能除毛") || text.contains("不含除毛")
            return hasPetSmart && hasAfternoon && hasBathOnly
        }

        private fun compactScenarioActionLabel(
            label: String,
            value: String,
        ): String {
            if (scenario.scenarioId != "pet-grooming") return label
            val combined = "$label $value"
            val lower = combined.lowercase()
            val timeLabel = Regex("""\b\d{1,2}(?::\d{2})?\s*(?:am|pm)?\b""", RegexOption.IGNORE_CASE)
                .find(combined)
                ?.value
                ?.replace(Regex("""\s+"""), " ")
                ?.trim()
            return when {
                combined.contains("好的") ->
                    "好的"
                lower.contains("defer") || lower.contains("later") || lower.contains("next week") || combined.contains("改天") ->
                    "改天再说"
                lower.contains("book_afternoon_bath_only") || combined.contains("下午5点") || combined.contains("下午五点") ->
                    "约下午5点"
                lower.contains("book_0900") ->
                    "约9点"
                lower.contains("book") || lower.contains("booking") || lower.contains("appointment") ->
                    timeLabel?.let { "约${normalizeTimeLabel(it)}" } ?: "预约"
                lower.contains("modify") || lower.contains("change") || combined.contains("修改") ->
                    "修改计划"
                lower.contains("afternoon") || combined.contains("下午") ->
                    "问下午"
                lower.contains("another shop") || lower.contains("other shop") || combined.contains("换一家") ->
                    "换一家"
                lower.contains("cancel") || combined.contains("取消") ->
                    "取消"
                else -> label.substringBefore("（")
                    .substringBefore("(")
                    .substringBefore("->")
                    .substringBefore("=>")
                    .substringBefore("→")
                    .trim()
                    .ifBlank { label }
            }.let { it.take(24).trim() }
        }

        private fun normalizeTimeLabel(value: String): String =
            value
                .replace(Regex(""":00\b"""), "点")
                .replace(Regex("""\s*(am|AM)\b"""), "")
                .replace(Regex("""\s*(pm|PM)\b"""), "")
                .trim()

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
            if (scenario.scenarioId == "pet-grooming" && isAfternoonBathOnlyTradeoff(text)) {
                return "PetSmart说下午5点后可以，只够洗澡，不含除毛。要改约下午5点吗？"
            }
            if (scenario.scenarioId == "pet-grooming" && isGroomingTimeTradeoff(text)) {
                return "PetSmart说明天上午9点可以洗澡和除毛，下午5点后只够洗澡。要约9点吗？"
            }
            if (scenario.scenarioId == "pet-grooming" && inferScenarioActions(text).isNotEmpty()) {
                return "明天周日了，还是照常给麒麟约洗澡么？"
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
            if (scenario.scenarioId == "pet-grooming" && isNonActionGroomingFact(value)) return ""
            val timeRange = Regex("""^\d{1,2}:\d{2}\s*[–-]\s*\d{1,2}:\d{2}""").find(value)?.value
            return timeRange ?: value
        }

        private fun isNonActionGroomingFact(value: String): Boolean {
            val lower = value.lowercase()
            return lower.contains("booking secured") ||
                lower.contains("medium dog") ||
                lower.contains("basic bath") ||
                lower.contains("de-shedding care") ||
                lower.contains("time changed")
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
            val merged = values.fold(existing) { rows, value ->
                if (rows.lastOrNull()?.text == value.text || rows.any { it.text == value.text && it.timeText == value.timeText }) {
                    rows
                } else {
                    rows + value
                }
            }
            return merged
                .mapIndexed { index, row -> index to row }
                .sortedWith(
                    compareBy<Pair<Int, AgentTaskLog>> { (_, row) -> row.timeText ?: "99/99 99:99" }
                        .thenBy { (index, _) -> index },
                )
                .map { (_, row) -> row }
                .takeLast(MAX_TASK_LOGS)
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

        private enum class GroomingMilestone {
            HOME_CONFIRMED,
            PAYMENT_COMPLETED,
            EXPENSE_RECORDED,
        }

        private companion object {
            private const val TAG = "AgentExperienceViewModel"
            private const val MAX_TRACE_LINES = 80
            private const val MAX_SIGNALS = 12
            private const val MAX_PARTICIPANTS = 5
            private const val MAX_CONVERSATION_ITEMS = 40
            private const val MAX_TASK_LOGS = 80
            private const val MAX_VISIBLE_ASSISTANT_UPDATE_CHARS = 500
            private const val MAX_AUTO_CONTINUATIONS = 14
            private const val AUTO_TRIGGER_DELAY_MS = 5_000L
            private const val SCENARIO_CLOCK_TICK_MS = 60_000L
            private const val CLOCK_ADVANCE_STEPS = 30
            private const val CLOCK_ADVANCE_STEP_MS = 1_000L
            private const val TOOL_ROUND_LIMIT_PREFIX = "Stopped: too many tool call rounds"
            private val INITIAL_SCENARIO_CLOCK: LocalDateTime = LocalDateTime.of(2027, 4, 25, 13, 0)
            private val CLOCK_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
            private val CLOCK_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US)
            private val BLUEPRINT_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.US)
            private val SCENARIO_DAY_NAMES = listOf(
                "Saturday" to "Sat",
                "Sunday" to "Sun",
                "Monday" to "Mon",
                "Tuesday" to "Tue",
                "Wednesday" to "Wed",
                "Thursday" to "Thu",
                "Friday" to "Fri",
            )
            private val GROOMING_CONTINUATION_PROMPT = """
                Continue the pet-grooming workflow from the next missing operational milestone. The previous assistant answer stopped before closure.
                Do not summarize, audit, or explain the history in prose. Start by calling the next needed tool.
                All user-facing assistant messages, action candidates, plan titles, plan steps, SMS bodies, reminders, notifications, payment descriptions, and accounting descriptions must be Chinese. Proper nouns such as PetSmart, Driver, Kylin, CNY, and NT may stay as written. Do not write English prose on user-facing surfaces.
                Continue in this order, choosing the first missing milestone only: PetSmart booking confirmation; Driver home-pickup confirmation; Driver delivery-to-PetSmart update from Driver; PetSmart arrival/progress/finish update; Driver pickup-from-PetSmart and return update; Driver home confirmation; payment; accounting; one short final status.
                If Y selected the 9:00 option and PetSmart has not yet replied with a booking-confirmed SMS, the next tool must be a PetSmart confirmation SMS or a PetSmart wait. Do not message Driver in that state.
                Driver is Y's private driver. For a 9:00 PetSmart appointment, the first Driver leg is 8:30 home pickup, then arrival at PetSmart by 9:00. For an accepted afternoon bath-only slot after 17:00, the first Driver leg is 16:30 home pickup, then arrival at PetSmart by 17:00. PetSmart progress, delay, revised pickup time, and finish must come from PetSmart. The second Driver leg is PetSmart to home only after PetSmart reports Kylin is finished, ready, or gives a revised pickup time after a delay.
                The first Driver SMS must only cover the selected appointment's home pickup and PetSmart delivery. Do not include a predicted grooming finish time, return pickup time, or home-arrival instruction in that first Driver SMS.
                After PetSmart confirms the selected slot, contact Driver directly without asking Y again. Driver SMS is addressed to Driver; start it with `司机您好` or `您好`, never `Y您好` or similar user-facing greetings.
                After sending the first Driver SMS, first wait for Driver's home-pickup confirmation using that SMS listener. A reply such as "收到，我8:30来接 Kylin" satisfies only pickup confirmation, not delivery. After Driver confirms the pickup plan, create a long_reminder for the selected next-day departure time (04/26 08:30 for a 9:00 appointment, or 04/26 16:30 for an afternoon 17:00 appointment) with a Chinese title like `麒麟出发洗澡`; this is reminder creation and must not be treated as actual departure. After that, call a second system_wait_for_sms from Driver with context "Driver delivery-to-PetSmart update" and no old watchId. Only an inbound Driver message that says Kylin was delivered, arrived, 到店, 送到, or 送达 satisfies delivery-to-PetSmart.
                After Driver confirms the first pickup plan, do not send Driver another SMS asking for future milestone reports. For Driver delivery-to-PetSmart update, call system_wait_for_sms with Driver as the sender. Do not wait on PetSmart for arrival or progress until Driver has reported Kylin was delivered to PetSmart.
                For the normal selected scope, use the pet salon search service's published price for Kylin's extra-large Bernese Mountain Dog size and selected bath/de-shedding scope. Do not use small-dog pricing, full grooming/styling pricing, or pickup coordination fees unless Y or PetSmart explicitly changes the scope.
                Do not include prices, totals, fee details, or CNY in normal outbound PetSmart SMS. PetSmart SMS should only confirm time, service scope, and booking status unless there is an abnormal price issue.
                For payment and accounting date, use 2027-04-26 for this run. Do not use the phone's real current year.
                If payment is already completed but no expense has been recorded, the next tool must be device_system with action "accounting" for PetSmart, the same amount used for payment from the published service result, date 2027-04-26, and a Chinese description for Kylin's grooming.
                A Driver promise to return Kylin later is not home confirmation. Do not call system_wait_for_sms for home confirmation until after Driver has been told to bring Kylin home or has reported Kylin is on the way home.
                When home confirmation is the next missing milestone, send Driver a short SMS asking him to reply once Kylin is home, then call system_wait_for_sms with the returned watchId. Do not pay or account until that inbound Driver SMS explicitly says Kylin is home.
                Do not ask Y whether to create routine reminders, and do not stop at a routine reminder question. Create routine reminders autonomously when useful, then continue to the next SMS signal.
                If payment or accounting already happened before Driver home confirmation, still wait for Driver home confirmation before final status.
                If a message was sent and no matching reply was received, call system_wait_for_sms for that contact.
                Ask Y only for a declared decision point, a material time/service tradeoff, safety issue, unusual fee, failed payment, or unclear instruction.
            """.trimIndent()
            private val BLUEPRINT_TIMES = listOf(
                "04/25 18:30",
                "04/25 18:31",
                "04/25 18:32",
                "04/25 18:33",
                "04/25 18:35",
                "04/25 18:36",
                "04/25 18:37",
                "04/25 18:38",
                "04/25 18:39",
                "04/25 18:40",
                "04/26 16:10",
                "04/26 16:58",
                "04/26 17:00",
                "04/26 17:04",
                "04/26 17:05",
                "04/26 17:06",
                "04/26 17:07",
                "04/26 19:20",
                "04/26 19:21",
                "04/26 19:22",
                "04/26 19:23",
                "04/26 20:00",
                "04/26 20:01",
            )
        }
    }
