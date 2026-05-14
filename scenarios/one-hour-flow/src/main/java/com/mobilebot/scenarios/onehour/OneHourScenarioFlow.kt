package com.mobilebot.scenarios.onehour

import com.mobilebot.scenarios.coldchaindelivery.ColdchainDeliveryTaskSurface
import com.mobilebot.scenarios.familyshopping.FamilyShoppingTaskSurface
import com.mobilebot.scenarios.healthsupply.HealthSupplyTaskSurface
import com.mobilebot.scenarios.petgrooming.PetGroomingTaskSurface
import com.mobilebot.scenarios.runtime.ScenarioAgentCommand
import com.mobilebot.scenarios.runtime.ScenarioLog
import com.mobilebot.scenarios.runtime.ScenarioProgress
import com.mobilebot.scenarios.runtime.ScenarioSurfaceStatus
import com.mobilebot.scenarios.runtime.ScenarioTaskSeed
import com.mobilebot.scenarios.runtime.ScenarioTaskUpdate
import com.mobilebot.systemruntime.CallEndedEvent
import com.mobilebot.systemruntime.IncomingCallEvent
import com.mobilebot.systemruntime.IncomingSmsEvent
import com.mobilebot.systemruntime.ReminderFiredEvent
import com.mobilebot.systemruntime.RuntimeNotificationEvent
import com.mobilebot.systemruntime.SystemRuntimeEvent
import org.json.JSONArray
import org.json.JSONObject

sealed interface OneHourFlowEffect {
    data class CreateTask(
        val seed: ScenarioTaskSeed,
        val activate: Boolean = true,
    ) : OneHourFlowEffect

    data class UpdateTask(
        val update: ScenarioTaskUpdate,
        val activate: Boolean = false,
    ) : OneHourFlowEffect

    data class ShowSystemLayer(
        val id: String,
        val title: String,
        val body: String,
        val actionLabel: String,
        val callTranscriptText: String? = null,
    ) : OneHourFlowEffect

    data class ClearSystemLayer(
        val ids: Set<String>,
    ) : OneHourFlowEffect

    data object ClearActiveCall : OneHourFlowEffect
}

class OneHourScenarioFlow {
    private var petCareAccepted = false

    fun markPetCareAccepted() {
        petCareAccepted = true
    }

    fun markPetCareDeclined() {
        petCareAccepted = false
    }

    fun isPetCareAccepted(): Boolean = petCareAccepted

    fun acceptPetCareSlot(label: String): OneHourFlowEffect.UpdateTask {
        petCareAccepted = true
        return OneHourFlowEffect.UpdateTask(
            update = PetGroomingTaskSurface.acceptOpenSlot(label),
            activate = true,
        )
    }

    fun keepOriginalPetCareSlot(label: String): OneHourFlowEffect.UpdateTask =
        OneHourFlowEffect.UpdateTask(
            update = PetGroomingTaskSurface.keepOriginalSlot(label),
            activate = true,
        )

    fun acceptPetCareSlotCommands(label: String): List<ScenarioAgentCommand> {
        petCareAccepted = true
        val update = PetGroomingTaskSurface.acceptOpenSlot(label).copy(
            logs = listOf(ScenarioLog("用户确认改到 14:00 洗澡和去浮毛。")),
        )
        return listOf(
            ScenarioAgentCommand.UpdateTask(update),
            ScenarioAgentCommand.SendSms(
                taskId = update.taskId,
                to = "PetSmart",
                displayName = "PetSmart",
                message = "好的，14:00 准时到。",
            ),
            ScenarioAgentCommand.SendSms(
                taskId = update.taskId,
                to = "Driver",
                displayName = "老陈",
                message = "老陈，麻烦 13:20 来楼下接 Kylin，14:00 前送到 PetSmart 洗澡和去浮毛。",
            ),
            ScenarioAgentCommand.WaitSms(
                taskId = update.taskId,
                contact = "Driver",
                reason = "等待司机确认 13:20 到楼下接 Kylin",
            ),
        )
    }

    fun keepOriginalPetCareSlotCommands(label: String): List<ScenarioAgentCommand> {
        petCareAccepted = false
        return listOf(ScenarioAgentCommand.UpdateTask(PetGroomingTaskSurface.keepOriginalSlot(label)))
    }

    fun openSlotClarificationCommands(userText: String): List<ScenarioAgentCommand> {
        val (conversations, decision) = PetGroomingTaskSurface.openSlotClarification(userText)
        val update = ScenarioTaskUpdate(
            taskId = PetGroomingTaskSurface.TASK_ID,
            subtitle = "PetSmart 14:00 空档待确认",
            status = ScenarioSurfaceStatus.BLOCKED,
            conversations = conversations,
            progress = ScenarioProgress(
                label = "等待",
                detail = "等待用户决策",
                completed = 0,
                total = 7,
            ),
            decision = decision,
        )
        return listOf(ScenarioAgentCommand.UpdateTask(update))
    }

    // 系统事件只描述外部事实，这里把事实分发给对应场景处理器。
    fun handle(event: SystemRuntimeEvent): List<OneHourFlowEffect> =
        when (event) {
            is IncomingSmsEvent -> handleIncomingSms(event)
            is RuntimeNotificationEvent -> handleRuntimeNotification(event)
            is IncomingCallEvent -> handleIncomingCall(event)
            is CallEndedEvent -> handleCallEnded(event)
            is ReminderFiredEvent -> handleReminder(event)
            else -> emptyList()
        }

    private fun handleIncomingSms(event: IncomingSmsEvent): List<OneHourFlowEffect> =
        when (event.id) {
            "petsmart-open-slot" -> listOf(OneHourFlowEffect.CreateTask(PetGroomingTaskSurface.openSlotSeed(event.body)))
            "driver-1320-confirm" -> ifPetAccepted(PetGroomingTaskSurface.driverPickupConfirmation(event.body))
            "ella-shopping-followup" -> listOf(OneHourFlowEffect.UpdateTask(FamilyShoppingTaskSurface.priorityFollowup(event.body)))
            "property-courier-help" -> listOf(OneHourFlowEffect.UpdateTask(ColdchainDeliveryTaskSurface.propertyHelp(event.body)))
            "property-coldchain-secured" -> listOf(OneHourFlowEffect.UpdateTask(ColdchainDeliveryTaskSurface.propertyConfirmed(event.body)))
            "driver-kylin-picked-up" -> ifPetAccepted(PetGroomingTaskSurface.driverPickedUpKylin(event.body))
            "driver-arrived-petsmart" -> ifPetAccepted(PetGroomingTaskSurface.driverArrivedPetSmart(event.body))
            "petsmart-service-started" -> ifPetAccepted(PetGroomingTaskSurface.serviceStarted(event.body))
            "ella-shopping-clarify" -> listOf(OneHourFlowEffect.UpdateTask(FamilyShoppingTaskSurface.clarifiedList(event.body)))
            "petsmart-service-progress" -> ifPetAccepted(PetGroomingTaskSurface.serviceProgress(event.body))
            else -> emptyList()
        }

    private fun handleRuntimeNotification(event: RuntimeNotificationEvent): List<OneHourFlowEffect> =
        when (event.id) {
            "property-parking-notice" -> ifPetAccepted(PetGroomingTaskSurface.propertyParkingNotice(event.body))
            "pharmacy-restock" -> listOf(OneHourFlowEffect.CreateTask(HealthSupplyTaskSurface.pharmacyRestock(event.body)))
            "market-delivery-window" -> listOf(OneHourFlowEffect.UpdateTask(FamilyShoppingTaskSurface.marketDeliveryCandidate(event.body)))
            "courier-coldchain-arriving" -> listOf(OneHourFlowEffect.CreateTask(ColdchainDeliveryTaskSurface.arriving(event.body)))
            "courier-coldchain-delivered" -> listOf(OneHourFlowEffect.UpdateTask(ColdchainDeliveryTaskSurface.delivered(event.body)))
            "health-supply-candidate" -> listOf(OneHourFlowEffect.UpdateTask(HealthSupplyTaskSurface.deliveryCandidate(event.body)))
            "market-order-locked" -> listOf(OneHourFlowEffect.UpdateTask(FamilyShoppingTaskSurface.orderLocked(event.body)))
            "health-supply-held" -> listOf(OneHourFlowEffect.UpdateTask(HealthSupplyTaskSurface.deliveryHeld(event.body)))
            else -> emptyList()
        }

    private fun handleIncomingCall(event: IncomingCallEvent): List<OneHourFlowEffect> =
        listOf(
            OneHourFlowEffect.ShowSystemLayer(
                id = event.id,
                title = "${event.source} 来电",
                body = "正在接入通话转写。",
                actionLabel = "接听",
                callTranscriptText = FamilyShoppingTaskSurface.transcriptForIncomingCall(event.id)?.transcript,
            ),
        )

    private fun handleCallEnded(event: CallEndedEvent): List<OneHourFlowEffect> =
        listOf(
            OneHourFlowEffect.ClearActiveCall,
            OneHourFlowEffect.ClearSystemLayer(setOf(event.id, event.id.removeSuffix("-ended"))),
            OneHourFlowEffect.CreateTask(FamilyShoppingTaskSurface.fromEllaCall(event.audioRef)),
        )

    private fun handleReminder(event: ReminderFiredEvent): List<OneHourFlowEffect> =
        if (petCareAccepted) {
            listOf(
                OneHourFlowEffect.ShowSystemLayer(
                    id = event.id,
                    title = event.title,
                    body = event.body,
                    actionLabel = "OK",
                ),
                OneHourFlowEffect.UpdateTask(PetGroomingTaskSurface.departureReminderFired()),
            )
        } else {
            emptyList()
        }

    private fun ifPetAccepted(update: ScenarioTaskUpdate): List<OneHourFlowEffect> =
        if (petCareAccepted) listOf(OneHourFlowEffect.UpdateTask(update)) else emptyList()

    companion object {
        fun commandReferences(effects: List<OneHourFlowEffect>): List<ScenarioAgentCommand> =
            effects.mapNotNull { effect ->
                when (effect) {
                    is OneHourFlowEffect.CreateTask -> ScenarioAgentCommand.CreateTask(effect.seed)
                    is OneHourFlowEffect.UpdateTask -> ScenarioAgentCommand.UpdateTask(effect.update)
                    else -> null
                }
            }

        fun commandReferences(
            event: SystemRuntimeEvent,
            effects: List<OneHourFlowEffect>,
        ): List<ScenarioAgentCommand> {
            val commands = commandReferences(effects)
            return when (event.id) {
                "driver-1320-confirm" -> commands + ScenarioAgentCommand.CreateReminder(
                    taskId = PetGroomingTaskSurface.TASK_ID,
                    title = "送 Kylin 下楼",
                    body = "司机老陈即将到楼下。",
                    scheduledFor = "2027-04-25T13:20:00",
                )
                else -> commands
            }
        }

        fun plannerPolicyJson(
            event: SystemRuntimeEvent,
            referenceCommands: List<ScenarioAgentCommand>,
        ): String =
            basePlannerPolicy(referenceCommands)
                .put("turn", "system_event")
                .put("eventId", event.id)
                .put(
                    "rules",
                    JSONArray(
                        listOf(
                            "Treat eventFact as already observed system state.",
                            "Use current task state and timeline queue to decide whether commands are needed.",
                            "Return empty commands when the event only needs system-layer display.",
                            "Do not copy fallback wording; write concise task updates that fit the observed fact.",
                        ),
                    ),
                )
                .toString()

        fun userDecisionPlannerPolicyJson(
            userText: String,
            referenceCommands: List<ScenarioAgentCommand>,
        ): String =
            basePlannerPolicy(referenceCommands)
                .put("turn", "user_decision")
                .put("userText", userText)
                .put(
                    "rules",
                    JSONArray(
                        listOf(
                            "Interpret the user's latest text first; do not force it into the existing buttons.",
                            "If the user clearly accepts 14:00, continue the pet grooming coordination.",
                            "If the user clearly keeps the original slot, finish this change request without Driver or reminder side effects.",
                            "If the user reports the pet is unavailable, deceased, or no longer needs grooming, stop this grooming task, clear decision actions, and do not ask the 14:00/17:00 clarification again.",
                            "If the reply is truly unclear and still about scheduling, ask one concise clarification question.",
                            "Do not copy fallback wording; write the smallest command batch that matches the user's intent.",
                        ),
                    ),
                )
                .toString()

        private fun basePlannerPolicy(referenceCommands: List<ScenarioAgentCommand>): JSONObject {
            val taskIds = referenceCommands.map { it.taskId }.filter { it.isNotBlank() }.distinct()
            val decisionActions = referenceCommands.flatMap { command ->
                when (command) {
                    is ScenarioAgentCommand.CreateTask -> command.seed.decision?.actions.orEmpty()
                    is ScenarioAgentCommand.UpdateTask -> command.update.decision?.actions.orEmpty()
                    is ScenarioAgentCommand.AskUser -> command.decision.actions
                    else -> emptyList()
                }
            }.distinctBy { it.key }
            val smsTargets = referenceCommands.filterIsInstance<ScenarioAgentCommand.SendSms>()
                .map { JSONObject().put("taskId", it.taskId).put("to", it.to) }
            val reminders = referenceCommands.filterIsInstance<ScenarioAgentCommand.CreateReminder>()
                .map { JSONObject().put("taskId", it.taskId).put("scheduledFor", it.scheduledFor) }

            return JSONObject()
                .put("mode", "planner_policy_not_reference_answer")
                .put("fallback", "local deterministic commands exist only for failure fallback and guard authorization")
                .put("taskIds", JSONArray(taskIds))
                .put("allowedStatuses", JSONArray(listOf("RUNNING", "BLOCKED", "DONE")))
                .put(
                    "existingDecisionActionKeys",
                    JSONArray(decisionActions.map {
                        JSONObject().put("key", it.key)
                    }),
                )
                .put("authorizedSms", JSONArray(smsTargets))
                .put("authorizedReminders", JSONArray(reminders))
                .put(
                    "sideEffectRule",
                    "SMS and reminders are allowed only when listed in authorizedSms or authorizedReminders; otherwise update task state only.",
                )
        }

        fun taskIdFromEffects(effects: List<OneHourFlowEffect>): String? =
            effects.firstNotNullOfOrNull { effect ->
                when (effect) {
                    is OneHourFlowEffect.CreateTask -> effect.seed.taskId
                    is OneHourFlowEffect.UpdateTask -> effect.update.taskId
                    else -> null
                }
            }

        val supportedEventIds: Set<String> = setOf(
            "petsmart-open-slot",
            "driver-1320-confirm",
            "ella-call",
            "ella-call-ended",
            "property-parking-notice",
            "ella-shopping-followup",
            "kylin-downstairs-reminder",
            "driver-kylin-picked-up",
            "pharmacy-restock",
            "market-delivery-window",
            "courier-coldchain-arriving",
            "driver-arrived-petsmart",
            "petsmart-service-started",
            "courier-coldchain-delivered",
            "ella-shopping-clarify",
            "property-courier-help",
            "property-coldchain-secured",
            "petsmart-service-progress",
            "health-supply-candidate",
            "market-order-locked",
            "health-supply-held",
        )

        val petAcceptanceRequiredEventIds: Set<String> = setOf(
            "driver-1320-confirm",
            "property-parking-notice",
            "kylin-downstairs-reminder",
            "driver-kylin-picked-up",
            "driver-arrived-petsmart",
            "petsmart-service-started",
            "petsmart-service-progress",
        )
    }
}
