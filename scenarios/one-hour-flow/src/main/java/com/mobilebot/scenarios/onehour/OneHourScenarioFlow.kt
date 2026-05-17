package com.mobilebot.scenarios.onehour

import com.mobilebot.scenarios.coldchaindelivery.ColdchainDeliveryTaskSurface
import com.mobilebot.scenarios.familyshopping.FamilyShoppingTaskSurface
import com.mobilebot.scenarios.healthsupply.HealthSupplyTaskSurface
import com.mobilebot.scenarios.petgrooming.PetGroomingTaskSurface
import com.mobilebot.scenarios.runtime.ScenarioAgentCommand
import com.mobilebot.scenarios.runtime.ScenarioCommandAuthorization
import com.mobilebot.scenarios.runtime.ScenarioLog
import com.mobilebot.scenarios.runtime.ScenarioParticipant
import com.mobilebot.scenarios.runtime.ScenarioProgress
import com.mobilebot.scenarios.runtime.ScenarioReminderAuthorization
import com.mobilebot.scenarios.runtime.ScenarioSmsAuthorization
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

    fun updateRuntimeStateFromPlannerCommands(commands: List<ScenarioAgentCommand>) {
        when {
            commands.any { it.opensPetCareFollowup() } -> petCareAccepted = true
            commands.any { it.closesPetCareFollowup() } -> petCareAccepted = false
        }
    }

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

    fun systemLayerEffects(event: SystemRuntimeEvent): List<OneHourFlowEffect> =
        when (event) {
            is IncomingCallEvent -> handleIncomingCall(event)
            is CallEndedEvent -> listOf(
                OneHourFlowEffect.ClearActiveCall,
                OneHourFlowEffect.ClearSystemLayer(setOf(event.id, event.id.removeSuffix("-ended"))),
            )
            is ReminderFiredEvent -> if (petCareAccepted) {
                listOf(
                    OneHourFlowEffect.ShowSystemLayer(
                        id = event.id,
                        title = event.title,
                        body = event.body,
                        actionLabel = "OK",
                    ),
                )
            } else {
                emptyList()
            }
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

    private fun ScenarioAgentCommand.opensPetCareFollowup(): Boolean =
        when (this) {
            is ScenarioAgentCommand.SendSms ->
                taskId == PetGroomingTaskSurface.TASK_ID && to.equals("Driver", ignoreCase = true)
            is ScenarioAgentCommand.WaitSms ->
                taskId == PetGroomingTaskSurface.TASK_ID && contact.equals("Driver", ignoreCase = true)
            is ScenarioAgentCommand.CreateReminder -> taskId == PetGroomingTaskSurface.TASK_ID
            else -> false
        }

    private fun ScenarioAgentCommand.closesPetCareFollowup(): Boolean =
        when (this) {
            is ScenarioAgentCommand.CompleteTask -> taskId == PetGroomingTaskSurface.TASK_ID
            is ScenarioAgentCommand.CreateTask ->
                seed.taskId == PetGroomingTaskSurface.TASK_ID && seed.status == ScenarioSurfaceStatus.DONE
            is ScenarioAgentCommand.UpdateTask ->
                update.taskId == PetGroomingTaskSurface.TASK_ID && update.status == ScenarioSurfaceStatus.DONE
            else -> false
        }

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
        ): String {
            val authorization = commandAuthorizationForEvent(event)
            return basePlannerPolicy(
                authorization = authorization,
                decisionActions = decisionActionsForEvent(event),
                currentFactParticipants = participantsForCurrentFact(event),
            )
                .put("turn", "system_event")
                .put("emptyCommands", emptyCommandPolicy(authorization))
                .put("taskPlanningGoal", taskPlanningGoal(authorization.taskIds))
                .apply {
                    currentObservedContext(event)?.let { put("currentObservedContext", it) }
                    petRouteUpdateProtocol(event)?.let { put("petRouteUpdateProtocol", it) }
                }
                .put(
                    "rules",
                    JSONArray(
                        listOf(
                            "Treat eventFact as already observed system state.",
                            "Use only the observed event fact and current task state to decide whether commands are needed.",
                            "Return empty commands only when plannerPolicy.emptyCommands allows it or the observed fact is unrelated to authorized tasks.",
                            "plannerPolicy is runtime authorization, not a deterministic answer key.",
                            "Write concise task updates that fit the observed fact.",
                            "Every plannerPolicy.requiredParticipants item matching the task must appear in participants or participantsToAdd in your command.",
                            "If you output participants on update_task, it is a full replacement list; include still-involved currentTask/allTasks participants plus newly observed participants.",
                            "When plannerPolicy provides a protocol block, follow its command order and key constraints for the current observed fact.",
                            "When plannerPolicy.decisionPolicy.visibleDecisionActionsRequired is true, keep the task BLOCKED and include decision actions exactly in the task command.",
                        ),
                    ),
                )
                .toString()
        }

        fun userDecisionPlannerPolicyJson(
            userText: String,
            taskId: String?,
            displayedActions: List<Pair<String, String>>,
        ): String =
            basePlannerPolicy(
                authorization = commandAuthorizationForUserDecision(taskId),
                decisionActions = displayedActions,
            )
                .put("turn", "user_decision")
                .put("userText", userText)
                .apply {
                    petSlotAcceptanceProtocol(taskId)?.let { put("petSlotAcceptanceProtocol", it) }
                }
                .put(
                    "rules",
                    JSONArray(
                        listOf(
                            "Interpret the user's latest text first; do not force it into the existing buttons.",
                            "If the user clearly accepts 14:00, continue the pet grooming coordination.",
                            "For 14:00 acceptance, follow plannerPolicy.petSlotAcceptanceProtocol when present.",
                            "If the user clearly keeps the original slot, finish this change request without Driver or reminder side effects.",
                            "If the user changes the task premise or says the request no longer needs action, stop or complete this task and clear decision actions.",
                            "If the reply is truly unclear and still about scheduling, ask one concise clarification question.",
                            "Do not rely on local deterministic wording; write the smallest command batch that matches the user's intent.",
                        ),
                    ),
                )
                .toString()

        private fun petSlotAcceptanceProtocol(taskId: String?): JSONObject? =
            if (taskId == PetGroomingTaskSurface.TASK_ID) {
                JSONObject()
                    .put("appliesWhen", "user clearly accepts the visible PetSmart 14:00 slot")
                    .put("driverPickupTime", "13:20")
                    .put("appointmentTime", "14:00")
                    .put(
                        "requiredCommandOrder",
                        JSONArray(
                            listOf(
                                "update_task: status RUNNING, participants include PetSmart and Driver",
                                "send_sms to PetSmart: confirm Kylin 14:00 bath and de-shedding slot",
                                "send_sms to Driver: ask for 13:20 home pickup and arrival at PetSmart before 14:00",
                                "wait_sms from Driver: wait for pickup/departure confirmation",
                            ),
                        ),
                    )
                    .put(
                        "rules",
                        JSONArray(
                            listOf(
                                "Do not invent a later pickup time such as 13:45.",
                                "Do not omit wait_sms after sending the Driver coordination SMS.",
                                "Use the contact displayName Driver in participant metadata.",
                            ),
                        ),
                    )
            } else {
                null
            }

        private fun petRouteUpdateProtocol(event: SystemRuntimeEvent): JSONObject? =
            if (event.id == "property-parking-notice") {
                JSONObject()
                    .put("appliesWhen", "observed property parking or route notice affects Driver's pickup or dropoff route")
                    .put("authorizedTarget", "Driver")
                    .put(
                        "requiredCommandOrder",
                        JSONArray(
                            listOf(
                                "update_task: preserve PetSmart and Driver, add property-service, summarize the parking or route fact",
                                "send_sms to Driver: pass the observed route or parking change that affects pickup or dropoff",
                            ),
                        ),
                    )
                    .put(
                        "rules",
                        JSONArray(
                            listOf(
                                "If the observed property notice changes where Driver should enter, exit, wait, or park, send the authorized Driver SMS in the same turn.",
                                "Do not notify PetSmart for a building parking route notice unless PetSmart is explicitly authorized.",
                                "Keep the message grounded in the observed fact; do not invent extra traffic or timing details.",
                            ),
                        ),
                    )
            } else {
                null
            }

        private fun basePlannerPolicy(
            authorization: ScenarioCommandAuthorization,
            decisionActions: List<Pair<String, String>> = emptyList(),
            currentFactParticipants: List<ScenarioParticipant> = emptyList(),
        ): JSONObject {
            val taskIds = authorization.taskIds.filter { it.isNotBlank() }.distinct()
            val smsTargets = authorization.sms
                .map { JSONObject().put("taskId", it.taskId).put("to", it.to) }
            val reminders = authorization.reminders
                .map { JSONObject().put("taskId", it.taskId).put("scheduledFor", it.scheduledFor) }
            val participantPolicy = participantPolicy(
                taskIds = taskIds,
                currentFactParticipants = currentFactParticipants,
                authorization = authorization,
            )

            return JSONObject()
                .put("mode", "llm_planner_runtime_policy")
                .put("taskIds", JSONArray(taskIds))
                .put("allowedStatuses", JSONArray(listOf("RUNNING", "BLOCKED", "DONE")))
                .put(
                    "visibleDecisionActions",
                    JSONArray(decisionActions.distinctBy { it.second }.map {
                        JSONObject().put("label", it.first).put("key", it.second)
                    }),
                )
                .put("authorizedSms", JSONArray(smsTargets))
                .put("authorizedReminders", JSONArray(reminders))
                .put("decisionPolicy", decisionPolicy(decisionActions))
                .put("requiredParticipants", participantPolicy.getJSONArray("requiredParticipants"))
                .put("participantPolicy", participantPolicy)
                .put(
                    "sideEffectRule",
                    "SMS and reminders are allowed only when listed in authorizedSms or authorizedReminders; otherwise update task state only.",
                )
        }

        private fun decisionPolicy(decisionActions: List<Pair<String, String>>): JSONObject =
            JSONObject()
                .put("visibleDecisionActionsRequired", decisionActions.isNotEmpty())
                .put(
                    "rules",
                    JSONArray(
                        listOf(
                            "If visibleDecisionActions is non-empty, the task command must include decision.text and decision.actions using exactly those visible labels and keys.",
                            "For an observed scheduling option that needs user confirmation, set task status to BLOCKED until the user chooses.",
                            "Do not drop visibleDecisionActions when creating or updating the task surface.",
                            "Only omit decision when the observed fact is already resolved and does not require a user choice.",
                        ),
                    ),
                )

        private fun participantPolicy(
            taskIds: List<String>,
            currentFactParticipants: List<ScenarioParticipant>,
            authorization: ScenarioCommandAuthorization,
        ): JSONObject {
            val currentFacts = currentFactParticipants.distinctBy { it.id }
            val sideEffectTargetParticipants = authorization.sms
                .mapNotNull { sms ->
                    participantForSource(sms.to)?.let { participant -> sms.taskId to participant }
                }
                .distinctBy { (taskId, participant) -> "$taskId:${participant.id}" }
            val requiredParticipants = (
                taskIds.flatMap { taskId -> baselineParticipantsForTask(taskId).map { taskId to it } } +
                    currentFacts.flatMap { participant -> taskIds.map { taskId -> taskId to participant } } +
                    sideEffectTargetParticipants
                )
                .distinctBy { (taskId, participant) -> "$taskId:${participant.id}" }

            return JSONObject()
                .put(
                    "requiredParticipants",
                    JSONArray(
                        requiredParticipants.map { (taskId, participant) ->
                            participantToPolicyJson(participant)
                                .put("taskId", taskId)
                                .put("requiredIn", "participants_or_participantsToAdd")
                        },
                    ),
                )
                .put(
                    "knownParticipantsByTask",
                    JSONArray(
                        taskIds.map { taskId ->
                            JSONObject()
                                .put("taskId", taskId)
                                .put(
                                    "baselineParticipants",
                                    JSONArray(baselineParticipantsForTask(taskId).map(::participantToPolicyJson)),
                                )
                                .put(
                                    "knownParticipants",
                                    JSONArray(knownParticipantsForTask(taskId).map(::participantToPolicyJson)),
                                )
                        },
                    ),
                )
                .put("currentFactParticipants", JSONArray(currentFacts.map(::participantToPolicyJson)))
                .put(
                    "sideEffectTargetParticipants",
                    JSONArray(
                        sideEffectTargetParticipants.map { (taskId, participant) ->
                            participantToPolicyJson(participant).put("taskId", taskId)
                        },
                    ),
                )
                .put(
                    "rules",
                    JSONArray(
                        listOf(
                            "For create_task, include participants for the task baseline and for observed currentFactParticipants that belong to the task.",
                            "For update_task, either add newly involved actors via participantsToAdd, or provide participants as the complete current participant list for that task.",
                            "participants and participantsToAdd must be arrays of objects with id, label, displayName, and role; never use string ids.",
                            "When providing participants, preserve still-involved participants already shown in currentTask/allTasks; do not drop Driver/PetSmart/Ella just because the latest fact mentions another actor.",
                            "Every requiredParticipants item matching the task must appear in participants or participantsToAdd in the same command.",
                            "When sending SMS to an authorized target, include that target as a participant in the same turn if newly involved.",
                            "knownParticipantsByTask is controlled vocabulary and guidance; do not add every known participant before they are involved.",
                            "If the observed fact introduces a real participant not listed here, emit it explicitly instead of dropping it.",
                        ),
                    ),
                )
        }

        private fun baselineParticipantsForTask(taskId: String): List<ScenarioParticipant> =
            when (taskId) {
                PetGroomingTaskSurface.TASK_ID -> listOf(PETSMART)
                FamilyShoppingTaskSurface.TASK_ID -> listOf(ELLA)
                ColdchainDeliveryTaskSurface.TASK_ID -> listOf(COURIER)
                HealthSupplyTaskSurface.TASK_ID -> listOf(PHARMACY)
                else -> emptyList()
            }

        private fun knownParticipantsForTask(taskId: String): List<ScenarioParticipant> =
            when (taskId) {
                PetGroomingTaskSurface.TASK_ID -> listOf(PETSMART, DRIVER, PROPERTY)
                FamilyShoppingTaskSurface.TASK_ID -> listOf(ELLA, OLE)
                ColdchainDeliveryTaskSurface.TASK_ID -> listOf(COURIER, PROPERTY)
                HealthSupplyTaskSurface.TASK_ID -> listOf(PHARMACY)
                else -> emptyList()
            }

        private fun participantsForCurrentFact(event: SystemRuntimeEvent): List<ScenarioParticipant> =
            listOfNotNull(participantForSource(event.source))

        private fun participantToPolicyJson(participant: ScenarioParticipant): JSONObject =
            JSONObject()
                .put("id", participant.id)
                .put("label", participant.label)
                .put("displayName", participant.displayName)
                .put("role", participant.role)

        private fun emptyCommandPolicy(authorization: ScenarioCommandAuthorization): String =
            if (authorization.taskIds.isEmpty()) {
                "allowed_for_system_layer_only"
            } else {
                "avoid_empty_when_observed_fact_matches_authorized_task"
            }

        private fun taskPlanningGoal(taskIds: Set<String>): String =
            when {
                FamilyShoppingTaskSurface.TASK_ID in taskIds ->
                    "Manage the family shopping task from the observed family call, family SMS, or market delivery fact. Create the task if it does not exist; otherwise update the same task."
                ColdchainDeliveryTaskSurface.TASK_ID in taskIds ->
                    "Manage the coldchain delivery task from the observed courier or property fact. Create the task if it does not exist; otherwise update the same task."
                HealthSupplyTaskSurface.TASK_ID in taskIds ->
                    "Manage the routine health supply task from the observed pharmacy or delivery fact. Create the task if it does not exist; otherwise update the same task."
                PetGroomingTaskSurface.TASK_ID in taskIds ->
                    "Manage the pet grooming coordination task from the observed shop, driver, property, or reminder fact."
                else -> "No task command is required unless the observed fact clearly belongs to an authorized task."
            }

        private fun currentObservedContext(event: SystemRuntimeEvent): String? =
            when (event) {
                is CallEndedEvent -> FamilyShoppingTaskSurface.transcriptForAudioRef(event.audioRef)
                    ?.let { "Call transcript from ${event.contact}: ${it.transcript}" }
                else -> null
            }

        fun commandAuthorizationForEvent(event: SystemRuntimeEvent): ScenarioCommandAuthorization =
            ScenarioCommandAuthorization(
                taskIds = authorizedTaskIdsForEvent(event),
                sms = smsAuthorizationsForEvent(event),
                reminders = when (event.id) {
                    "driver-1320-confirm" -> setOf(
                        ScenarioReminderAuthorization(
                            taskId = PetGroomingTaskSurface.TASK_ID,
                            scheduledFor = "2027-04-25T13:20:00",
                        ),
                    )
                    else -> emptySet()
                },
            )

        private fun smsAuthorizationsForEvent(event: SystemRuntimeEvent): Set<ScenarioSmsAuthorization> =
            when (event.id) {
                "property-parking-notice" -> setOf(
                    ScenarioSmsAuthorization(PetGroomingTaskSurface.TASK_ID, "Driver"),
                )
                else -> emptySet()
            }

        fun commandAuthorizationForUserDecision(taskId: String?): ScenarioCommandAuthorization {
            val cleanTaskId = taskId?.takeIf { it.isNotBlank() }
                ?: return ScenarioCommandAuthorization()
            return ScenarioCommandAuthorization(
                taskIds = setOf(cleanTaskId),
                sms = if (cleanTaskId == PetGroomingTaskSurface.TASK_ID) {
                    setOf(
                        ScenarioSmsAuthorization(PetGroomingTaskSurface.TASK_ID, "PetSmart"),
                        ScenarioSmsAuthorization(PetGroomingTaskSurface.TASK_ID, "Driver"),
                    )
                } else {
                    emptySet()
                },
            )
        }

        private fun decisionActionsForEvent(event: SystemRuntimeEvent): List<Pair<String, String>> =
            when (event.id) {
                "petsmart-open-slot" -> PetGroomingTaskSurface.openSlotSeed(event.body)
                    .decision
                    ?.actions
                    .orEmpty()
                    .map { it.label to it.key }
                else -> emptyList()
            }

        fun taskIdFromEffects(effects: List<OneHourFlowEffect>): String? =
            effects.firstNotNullOfOrNull { effect ->
                when (effect) {
                    is OneHourFlowEffect.CreateTask -> effect.seed.taskId
                    is OneHourFlowEffect.UpdateTask -> effect.update.taskId
                    else -> null
                }
            }

        fun authorizedTaskIdsForEvent(event: SystemRuntimeEvent): Set<String> =
            when (event.id) {
                "petsmart-open-slot",
                "driver-1320-confirm",
                "property-parking-notice",
                "kylin-downstairs-reminder",
                "driver-kylin-picked-up",
                "driver-arrived-petsmart",
                "petsmart-service-started",
                "petsmart-service-progress",
                -> setOf(PetGroomingTaskSurface.TASK_ID)

                "ella-call-ended",
                "ella-shopping-followup",
                "market-delivery-window",
                "ella-shopping-clarify",
                "market-order-locked",
                -> setOf(FamilyShoppingTaskSurface.TASK_ID)

                "courier-coldchain-arriving",
                "courier-coldchain-delivered",
                "property-courier-help",
                "property-coldchain-secured",
                -> setOf(ColdchainDeliveryTaskSurface.TASK_ID)

                "pharmacy-restock",
                "health-supply-candidate",
                "health-supply-held",
                -> setOf(HealthSupplyTaskSurface.TASK_ID)

                else -> emptySet()
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

        private fun participantForSource(source: String): ScenarioParticipant? =
            when {
                source.equals("PetSmart", ignoreCase = true) -> PETSMART
                source.equals("Driver", ignoreCase = true) ||
                    source.contains("司机") ||
                    source.contains("老陈") -> DRIVER
                source.equals("Ella", ignoreCase = true) -> ELLA
                source.equals("Ole", ignoreCase = true) -> OLE
                source.contains("顺丰冷链") -> COURIER
                source.contains("物业") -> PROPERTY
                source.contains("美团买药") -> PHARMACY
                else -> null
            }

        private val PETSMART = ScenarioParticipant(
            id = "petsmart",
            label = "PS",
            displayName = "PetSmart",
            role = "grooming_shop",
        )

        private val DRIVER = ScenarioParticipant(
            id = "driver",
            label = "DR",
            displayName = "Driver",
            role = "private_driver",
        )

        private val ELLA = ScenarioParticipant(
            id = "ella",
            label = "E",
            displayName = "Ella",
            role = "family",
        )

        private val OLE = ScenarioParticipant(
            id = "ole",
            label = "O",
            displayName = "Ole",
            role = "market",
        )

        private val COURIER = ScenarioParticipant(
            id = "courier-coldchain",
            label = "顺",
            displayName = "顺丰冷链",
            role = "delivery_service",
        )

        private val PROPERTY = ScenarioParticipant(
            id = "property-service",
            label = "物",
            displayName = "物业管家",
            role = "property_service",
        )

        private val PHARMACY = ScenarioParticipant(
            id = "pharmacy-service",
            label = "药",
            displayName = "美团买药",
            role = "pharmacy_service",
        )
    }
}
