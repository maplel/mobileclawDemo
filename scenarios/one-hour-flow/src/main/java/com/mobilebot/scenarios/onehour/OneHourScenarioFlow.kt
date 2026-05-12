package com.mobilebot.scenarios.onehour

import com.mobilebot.scenarios.coldchaindelivery.ColdchainDeliveryTaskSurface
import com.mobilebot.scenarios.familyshopping.FamilyShoppingTaskSurface
import com.mobilebot.scenarios.healthsupply.HealthSupplyTaskSurface
import com.mobilebot.scenarios.petgrooming.PetGroomingTaskSurface
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
            "driver-kylin-picked-up" -> ifPetAccepted(PetGroomingTaskSurface.driverPickedUpKylin(event.body))
            "driver-arrived-petsmart" -> ifPetAccepted(PetGroomingTaskSurface.driverArrivedPetSmart(event.body))
            "petsmart-service-started" -> ifPetAccepted(PetGroomingTaskSurface.serviceStarted(event.body))
            "ella-shopping-clarify" -> listOf(OneHourFlowEffect.UpdateTask(FamilyShoppingTaskSurface.clarifiedList(event.body)))
            "petsmart-service-progress" -> ifPetAccepted(PetGroomingTaskSurface.serviceProgress(event.body))
            else -> emptyList()
        }

    private fun handleRuntimeNotification(event: RuntimeNotificationEvent): List<OneHourFlowEffect> =
        when (event.id) {
            "property-parking-notice" -> listOf(OneHourFlowEffect.UpdateTask(PetGroomingTaskSurface.propertyParkingNotice(event.body)))
            "pharmacy-restock" -> listOf(OneHourFlowEffect.CreateTask(HealthSupplyTaskSurface.pharmacyRestock(event.body)))
            "market-delivery-window" -> listOf(OneHourFlowEffect.UpdateTask(FamilyShoppingTaskSurface.marketDeliveryCandidate(event.body)))
            "courier-coldchain-arriving" -> listOf(OneHourFlowEffect.CreateTask(ColdchainDeliveryTaskSurface.arriving(event.body)))
            "courier-coldchain-delivered" -> listOf(OneHourFlowEffect.UpdateTask(ColdchainDeliveryTaskSurface.delivered(event.body)))
            "health-supply-candidate" -> listOf(OneHourFlowEffect.UpdateTask(HealthSupplyTaskSurface.deliveryCandidate(event.body)))
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
        listOf(
            OneHourFlowEffect.ShowSystemLayer(
                id = event.id,
                title = event.title,
                body = event.body,
                actionLabel = "OK",
            ),
            OneHourFlowEffect.UpdateTask(PetGroomingTaskSurface.departureReminderFired()),
        )

    private fun ifPetAccepted(update: ScenarioTaskUpdate): List<OneHourFlowEffect> =
        if (petCareAccepted) listOf(OneHourFlowEffect.UpdateTask(update)) else emptyList()
}
