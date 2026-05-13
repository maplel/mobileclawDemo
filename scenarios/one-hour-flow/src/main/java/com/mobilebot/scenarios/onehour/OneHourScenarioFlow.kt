package com.mobilebot.scenarios.onehour

import com.mobilebot.scenarios.coldchaindelivery.ColdchainDeliveryTaskSurface
import com.mobilebot.scenarios.familyshopping.FamilyShoppingTaskSurface
import com.mobilebot.scenarios.healthsupply.HealthSupplyTaskSurface
import com.mobilebot.scenarios.petgrooming.PetGroomingTaskSurface
import com.mobilebot.scenarios.runtime.ScenarioAgentCommand
import com.mobilebot.scenarios.runtime.ScenarioLog
import com.mobilebot.scenarios.runtime.ScenarioTaskSeed
import com.mobilebot.scenarios.runtime.ScenarioTaskUpdate
import com.mobilebot.systemruntime.CallEndedEvent
import com.mobilebot.systemruntime.IncomingCallEvent
import com.mobilebot.systemruntime.IncomingSmsEvent
import com.mobilebot.systemruntime.ReminderFiredEvent
import com.mobilebot.systemruntime.RuntimeNotificationEvent
import com.mobilebot.systemruntime.SystemRuntimeEvent

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
            ),
        )

    private fun handleCallEnded(event: CallEndedEvent): List<OneHourFlowEffect> =
        listOf(
            OneHourFlowEffect.ClearActiveCall,
            OneHourFlowEffect.ClearSystemLayer(setOf(event.id, event.id.removeSuffix("-ended"))),
            OneHourFlowEffect.CreateTask(FamilyShoppingTaskSurface.fromEllaCall()),
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
