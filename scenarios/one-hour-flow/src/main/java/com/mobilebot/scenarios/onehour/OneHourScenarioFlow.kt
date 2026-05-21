package com.mobilebot.scenarios.onehour

import com.mobilebot.scenarios.coldchaindelivery.ColdchainDeliveryTaskSurface
import com.mobilebot.scenarios.familyshopping.FamilyShoppingTaskSurface
import com.mobilebot.scenarios.familyshopping.FamilyShoppingUserTurn
import com.mobilebot.scenarios.familyshopping.FamilyShoppingUserTurnInterpreter
import com.mobilebot.scenarios.healthsupply.HealthSupplyTaskSurface
import com.mobilebot.scenarios.petgrooming.PetGroomingTaskSurface
import com.mobilebot.scenarios.petgrooming.PetGroomingUserTurn
import com.mobilebot.scenarios.petgrooming.PetGroomingUserTurnInterpreter
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
        val callSessionId: String? = null,
        val personaId: String? = null,
    ) : OneHourFlowEffect

    data class ClearSystemLayer(
        val ids: Set<String>,
    ) : OneHourFlowEffect

    data object ClearActiveCall : OneHourFlowEffect
}

class OneHourScenarioFlow {
    private enum class PetCareStage {
        NONE,
        OPEN_SLOT,
        ACCEPTED,
        DRIVER_CONFIRMED,
        REMINDER_FIRED,
        PICKED_UP,
        ARRIVED_AT_PETSMART,
        SERVICE_STARTED,
        SERVICE_PROGRESS,
        RESCHEDULED,
        CANCELLED,
        DECLINED,
    }

    private enum class ShoppingStage {
        NONE,
        CALL_CAPTURED,
        PRIORITY_UPDATED,
        CANDIDATE_FOUND,
        LIST_CLARIFIED,
        DELIVERY_WINDOW_REQUESTED,
        ORDER_REQUESTED,
        ORDER_HELD,
        ORDER_LOCKED,
        CANCELLED,
    }

    private var petCareAccepted = false
    private var petCareExpediteRequested = false
    private var petCareStage = PetCareStage.NONE
    private var shoppingStage = ShoppingStage.NONE
    private var shoppingDeliveryWindow: String? = null
    private val shoppingExcludedItems = linkedSetOf<String>()
    private val shoppingAddedItems = mutableListOf<String>()

    fun markPetCareAccepted() {
        petCareAccepted = true
        petCareStage = PetCareStage.ACCEPTED
    }

    fun markPetCareDeclined() {
        petCareAccepted = false
        petCareExpediteRequested = false
        petCareStage = PetCareStage.DECLINED
    }

    fun isPetCareAccepted(): Boolean = petCareAccepted
    fun isPetCareExpediteRequested(): Boolean = petCareExpediteRequested

    fun updateRuntimeStateFromPlannerCommands(commands: List<ScenarioAgentCommand>) {
        if (commands.any { it.requestsPetCareExpedite() }) {
            petCareExpediteRequested = true
        }
        when {
            commands.any { it.requestsShoppingConfirmPurchase() } ->
                shoppingStage = when (shoppingStage) {
                    ShoppingStage.ORDER_HELD,
                    ShoppingStage.ORDER_LOCKED -> ShoppingStage.ORDER_LOCKED
                    ShoppingStage.CANCELLED -> ShoppingStage.CANCELLED
                    else -> ShoppingStage.ORDER_REQUESTED
                }
            commands.any { it.requestsShoppingCancelPurchase() } -> shoppingStage = ShoppingStage.CANCELLED
            commands.any { it.requestsShoppingDeliveryWindow() } -> shoppingStage = ShoppingStage.DELIVERY_WINDOW_REQUESTED
            commands.any { it.requestsShoppingAddItems() } -> shoppingStage = ShoppingStage.LIST_CLARIFIED
            commands.any { it.closesShoppingTaskWithoutPurchase() } &&
                shoppingStage in setOf(ShoppingStage.NONE, ShoppingStage.CALL_CAPTURED) ->
                shoppingStage = ShoppingStage.CANCELLED
            commands.any { it.opensShoppingTask() } && shoppingStage == ShoppingStage.NONE ->
                shoppingStage = ShoppingStage.CALL_CAPTURED
        }
        when {
            commands.any { it.requestsPetCareReschedule() } -> {
                petCareAccepted = false
                petCareExpediteRequested = false
                petCareStage = PetCareStage.RESCHEDULED
            }
            commands.any { it.requestsPetCareCancel() } -> {
                petCareAccepted = false
                petCareExpediteRequested = false
                petCareStage = PetCareStage.CANCELLED
            }
            commands.any { it.opensPetCareFollowup() } -> {
                petCareAccepted = true
                if (petCareStage == PetCareStage.NONE || petCareStage == PetCareStage.OPEN_SLOT) {
                    petCareStage = PetCareStage.ACCEPTED
                }
            }
            commands.any { it.closesPetCareFollowup() } -> {
                petCareAccepted = false
                petCareExpediteRequested = false
                if (petCareStage !in setOf(PetCareStage.RESCHEDULED, PetCareStage.CANCELLED)) {
                    petCareStage = PetCareStage.DECLINED
                }
            }
        }
    }

    fun acceptPetCareSlot(label: String): OneHourFlowEffect.UpdateTask {
        petCareAccepted = true
        petCareExpediteRequested = false
        petCareStage = PetCareStage.ACCEPTED
        return OneHourFlowEffect.UpdateTask(
            update = PetGroomingTaskSurface.acceptOpenSlot(label),
            activate = true,
        )
    }

    fun keepOriginalPetCareSlot(label: String): OneHourFlowEffect.UpdateTask {
        petCareStage = PetCareStage.DECLINED
        return OneHourFlowEffect.UpdateTask(
            update = PetGroomingTaskSurface.keepOriginalSlot(label),
            activate = true,
        )
    }

    fun acceptPetCareSlotCommands(label: String): List<ScenarioAgentCommand> {
        petCareAccepted = true
        petCareExpediteRequested = false
        petCareStage = PetCareStage.ACCEPTED
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
        petCareExpediteRequested = false
        petCareStage = PetCareStage.DECLINED
        return listOf(ScenarioAgentCommand.UpdateTask(PetGroomingTaskSurface.keepOriginalSlot(label)))
    }

    fun userTurnCommands(
        taskId: String?,
        userText: String,
    ): List<ScenarioAgentCommand>? {
        if (petCareStage == PetCareStage.OPEN_SLOT && !petCareAccepted) return null
        val petTurn = PetGroomingUserTurnInterpreter.interpret(
            taskId = taskId,
            userText = userText,
            petCareKnown = petCareStage != PetCareStage.NONE,
        )
        if (petTurn != null) {
            return when (petTurn) {
                PetGroomingUserTurn.ExpediteService ->
                if (petCareAccepted) {
                    petCareExpediteCommands(userText)
                } else {
                    petCareClarificationCommands(userText, "pet_grooming_action")
                }
                PetGroomingUserTurn.AskStatus -> petCareStatusCommands(userText)
                PetGroomingUserTurn.CancelService -> petCareCancelCommands(userText)
                PetGroomingUserTurn.OutOfScope -> petCareOutOfScopeCommands(userText)
                is PetGroomingUserTurn.NeedsClarification ->
                    petCareClarificationCommands(userText, petTurn.reason)
                is PetGroomingUserTurn.RescheduleService ->
                    petCareRescheduleCommands(userText, petTurn.targetWeekOffset, petTurn.unavailableWeekOffsets)
            }
        }

        val shoppingTurn = FamilyShoppingUserTurnInterpreter.interpret(
            taskId = taskId,
            userText = userText,
            shoppingKnown = shoppingStage != ShoppingStage.NONE,
        ) ?: return null
        return when (shoppingTurn) {
            FamilyShoppingUserTurn.AskStatus -> shoppingStatusCommands(userText)
            FamilyShoppingUserTurn.ConfirmPurchase -> shoppingConfirmPurchaseCommands(userText)
            FamilyShoppingUserTurn.CancelPurchase -> shoppingCancelCommands(userText)
            FamilyShoppingUserTurn.OutOfScope -> shoppingOutOfScopeCommands(userText)
            is FamilyShoppingUserTurn.AddItems -> shoppingAddItemsCommands(userText, shoppingTurn.itemText)
            is FamilyShoppingUserTurn.ChangeDeliveryWindow ->
                shoppingDeliveryWindowCommands(userText, shoppingTurn.windowLabel)
            is FamilyShoppingUserTurn.NeedsClarification ->
                shoppingClarificationCommands(userText, shoppingTurn.reason)
            is FamilyShoppingUserTurn.RemoveItems -> shoppingRemoveItemsCommands(userText, shoppingTurn.items)
        }
    }

    fun userIntentCommands(
        taskId: String?,
        intentId: String?,
        userText: String,
    ): List<ScenarioAgentCommand>? {
        return when (intentId) {
            PetGroomingTaskSurface.PURPOSE_EXPEDITE_SERVICE ->
                if (petCareAccepted) petCareExpediteCommands(userText) else null
            else -> null
        }
    }

    fun userDecisionCommands(
        taskId: String?,
        actionKey: String?,
        userText: String,
    ): List<ScenarioAgentCommand>? {
        val cleanText = userText.trim()
        val cleanAction = actionKey?.trim().orEmpty()
        return when (taskId) {
            PetGroomingTaskSurface.TASK_ID -> when {
                cleanAction == PetGroomingTaskSurface.ACTION_ACCEPT_14 ||
                    cleanText in setOf("可以", "同意", "改到 14:00", "改到14:00") ->
                    acceptPetCareSlotCommands(cleanText.ifBlank { "可以" })

                cleanAction == PetGroomingTaskSurface.ACTION_KEEP_17 ||
                    cleanText in setOf("不改了", "不改", "保留原来", "保留 17:00", "保留17:00") ->
                    keepOriginalPetCareSlotCommands(cleanText.ifBlank { "不改了" })

                else -> null
            }
            FamilyShoppingTaskSurface.TASK_ID -> when {
                cleanAction == FamilyShoppingTaskSurface.ACTION_CONFIRM_PURCHASE ||
                    cleanText in setOf("买吧", "买这两样", "下单", "确认") ->
                    shoppingConfirmPurchaseCommands(cleanText.ifBlank { "买这两样" })

                cleanAction == FamilyShoppingTaskSurface.ACTION_SKIP_PURCHASE ||
                    cleanText in setOf("先不买", "不买了", "别买", "取消") ->
                    shoppingCancelCommands(cleanText.ifBlank { "先不买" })

                else -> null
            }
            else -> null
        }
    }

    private fun petCareExpediteCommands(userText: String): List<ScenarioAgentCommand> {
        petCareExpediteRequested = true
        val update = PetGroomingTaskSurface.expediteRequested(userText)
        return listOf(
            ScenarioAgentCommand.UpdateTask(update),
            ScenarioAgentCommand.SendSms(
                taskId = update.taskId,
                to = "PetSmart",
                displayName = "PetSmart",
                message = "麻烦在不影响 Kylin 安全和洗护效果的前提下尽量加快，谢谢。",
                semanticPurpose = PetGroomingTaskSurface.PURPOSE_EXPEDITE_SERVICE,
            ),
            ScenarioAgentCommand.WaitSms(
                taskId = update.taskId,
                contact = "PetSmart",
                reason = "等待 PetSmart 更新加快后的洗护进度",
            ),
        )
    }

    private fun petCareRescheduleCommands(
        userText: String,
        targetWeekOffset: Int,
        unavailableWeekOffsets: List<Int>,
    ): List<ScenarioAgentCommand> {
        if (!petCareAccepted) {
            return petCareClarificationCommands(userText, "pet_grooming_action")
        }
        if (!petCareStage.allowsDirectScheduleChange()) {
            return listOf(
                ScenarioAgentCommand.UpdateTask(
                    PetGroomingTaskSurface.rescheduleConflict(userText, petCareStage.stageText()),
                ),
            )
        }
        val targetLabel = PetGroomingUserTurnInterpreter.weekLabel(targetWeekOffset)
        val unavailableText = unavailableWeekOffsets
            .map { PetGroomingUserTurnInterpreter.weekLabel(it) }
            .takeIf { it.isNotEmpty() }
            ?.joinToString("、", prefix = "，避开 ", postfix = " 没空的时间")
            .orEmpty()
        val update = PetGroomingTaskSurface.rescheduleRequested(
            userText = userText,
            targetWeekOffset = targetWeekOffset,
            unavailableWeekOffsets = unavailableWeekOffsets,
        )
        petCareAccepted = false
        petCareExpediteRequested = false
        petCareStage = PetCareStage.RESCHEDULED
        return listOf(
            ScenarioAgentCommand.UpdateTask(update),
            ScenarioAgentCommand.SendSms(
                taskId = update.taskId,
                to = "PetSmart",
                displayName = "PetSmart",
                message = "您好，Kylin 今天 14:00 的洗澡和去浮毛需要改期，麻烦取消当前安排并改到$targetLabel$unavailableText，谢谢。",
                semanticPurpose = PetGroomingTaskSurface.PURPOSE_RESCHEDULE_SERVICE,
            ),
            ScenarioAgentCommand.SendSms(
                taskId = update.taskId,
                to = "Driver",
                displayName = "老陈",
                message = "老陈，Kylin 今天 14:00 的 PetSmart 行程改期了，13:20 不用来接。",
                semanticPurpose = PetGroomingTaskSurface.PURPOSE_RESCHEDULE_SERVICE,
            ),
            ScenarioAgentCommand.WaitSms(
                taskId = update.taskId,
                contact = "PetSmart",
                reason = "等待 PetSmart 确认新的洗护档期",
            ),
        )
    }

    private fun petCareCancelCommands(userText: String): List<ScenarioAgentCommand> {
        if (!petCareAccepted || !petCareStage.allowsDirectScheduleChange()) {
            return petCareClarificationCommands(userText, "pet_grooming_action")
        }
        val update = PetGroomingTaskSurface.cancelRequested(userText, petCareStage.stageText())
        petCareAccepted = false
        petCareExpediteRequested = false
        petCareStage = PetCareStage.CANCELLED
        return listOf(
            ScenarioAgentCommand.UpdateTask(update),
            ScenarioAgentCommand.SendSms(
                taskId = update.taskId,
                to = "PetSmart",
                displayName = "PetSmart",
                message = "您好，Kylin 今天 14:00 的洗澡和去浮毛取消了，谢谢。",
                semanticPurpose = PetGroomingTaskSurface.PURPOSE_CANCEL_SERVICE,
            ),
            ScenarioAgentCommand.SendSms(
                taskId = update.taskId,
                to = "Driver",
                displayName = "老陈",
                message = "老陈，Kylin 今天 14:00 的 PetSmart 行程取消了，13:20 不用来接。",
                semanticPurpose = PetGroomingTaskSurface.PURPOSE_CANCEL_SERVICE,
            ),
        )
    }

    private fun petCareStatusCommands(userText: String): List<ScenarioAgentCommand> =
        listOf(
            ScenarioAgentCommand.UpdateTask(
                PetGroomingTaskSurface.statusAnswer(
                    userText = userText,
                    stageText = petCareStage.stageText(),
                    etaText = petCareStage.etaText(petCareExpediteRequested),
                    completed = petCareStage.progressCompleted(),
                ),
            ),
        )

    private fun petCareClarificationCommands(
        userText: String,
        reason: String,
    ): List<ScenarioAgentCommand> =
        listOf(ScenarioAgentCommand.UpdateTask(PetGroomingTaskSurface.clarificationNeeded(userText, reason)))

    private fun petCareOutOfScopeCommands(userText: String): List<ScenarioAgentCommand> =
        listOf(ScenarioAgentCommand.UpdateTask(PetGroomingTaskSurface.outOfScope(userText)))

    private fun shoppingConfirmPurchaseCommands(userText: String): List<ScenarioAgentCommand> {
        if (shoppingStage == ShoppingStage.CANCELLED) {
            return shoppingClarificationCommands(userText, "family_shopping_action")
        }
        val confirmFromHold = shoppingStage == ShoppingStage.ORDER_HELD
        val update =
            if (confirmFromHold) {
                shoppingStage = ShoppingStage.ORDER_LOCKED
                FamilyShoppingTaskSurface.purchaseConfirmedFromHold(
                    userText = userText,
                    excludedItems = shoppingExcludedItems,
                    addedItems = shoppingAddedItems,
                    deliveryWindow = shoppingDeliveryWindow,
                )
            } else {
                shoppingStage = ShoppingStage.ORDER_REQUESTED
                FamilyShoppingTaskSurface.purchaseRequested(
                    userText = userText,
                    excludedItems = shoppingExcludedItems,
                    addedItems = shoppingAddedItems,
                    deliveryWindow = shoppingDeliveryWindow,
                )
            }
        val commands = mutableListOf<ScenarioAgentCommand>(
            ScenarioAgentCommand.UpdateTask(update),
            ScenarioAgentCommand.SendSms(
                taskId = update.taskId,
                to = "Ole",
                displayName = "Ole",
                message = shoppingPurchaseMessage(),
                semanticPurpose = FamilyShoppingTaskSurface.PURPOSE_CONFIRM_PURCHASE,
            ),
        )
        if (!confirmFromHold) {
            commands += ScenarioAgentCommand.WaitSms(
                taskId = update.taskId,
                contact = "Ole",
                reason = "等待 Ole 确认家庭采购库存和配送",
            )
        }
        return commands
    }

    private fun shoppingCancelCommands(userText: String): List<ScenarioAgentCommand> {
        shoppingStage = ShoppingStage.CANCELLED
        return listOf(
            ScenarioAgentCommand.UpdateTask(FamilyShoppingTaskSurface.purchaseSkipped(userText)),
            ScenarioAgentCommand.SendSms(
                taskId = FamilyShoppingTaskSurface.TASK_ID,
                to = "Ole",
                displayName = "Ole",
                message = "家庭采购先不下单了，麻烦释放低脂牛奶和常用洗衣液的候选库存。",
                semanticPurpose = FamilyShoppingTaskSurface.PURPOSE_CANCEL_PURCHASE,
            ),
        )
    }

    private fun shoppingRemoveItemsCommands(
        userText: String,
        items: List<String>,
    ): List<ScenarioAgentCommand> {
        shoppingExcludedItems += items
        shoppingStage = ShoppingStage.LIST_CLARIFIED
        if (shoppingExcludedItems.containsAll(listOf("低脂牛奶", "常用洗衣液", "水果"))) {
            return shoppingClarificationCommands(userText, "empty_cart")
        }
        return listOf(
            ScenarioAgentCommand.UpdateTask(
                FamilyShoppingTaskSurface.removeItems(
                    userText = userText,
                    removedItems = items,
                    excludedItems = shoppingExcludedItems,
                    addedItems = shoppingAddedItems,
                ),
            ),
        )
    }

    private fun shoppingAddItemsCommands(
        userText: String,
        itemText: String,
    ): List<ScenarioAgentCommand> {
        shoppingAddedItems += itemText
        shoppingStage = ShoppingStage.LIST_CLARIFIED
        val update = FamilyShoppingTaskSurface.addItems(
            userText = userText,
            addedNow = itemText,
            excludedItems = shoppingExcludedItems,
            addedItems = shoppingAddedItems,
        )
        return listOf(
            ScenarioAgentCommand.UpdateTask(update),
            ScenarioAgentCommand.SendSms(
                taskId = update.taskId,
                to = "Ole",
                displayName = "Ole",
                message = "家庭采购里麻烦一起确认 $itemText 的库存和配送时间。",
                semanticPurpose = FamilyShoppingTaskSurface.PURPOSE_ADD_ITEMS,
            ),
            ScenarioAgentCommand.WaitSms(
                taskId = update.taskId,
                contact = "Ole",
                reason = "等待 Ole 确认追加采购项",
            ),
        )
    }

    private fun shoppingDeliveryWindowCommands(
        userText: String,
        deliveryWindow: String,
    ): List<ScenarioAgentCommand> {
        shoppingDeliveryWindow = deliveryWindow
        shoppingStage = ShoppingStage.DELIVERY_WINDOW_REQUESTED
        val update = FamilyShoppingTaskSurface.deliveryWindowRequested(userText, deliveryWindow)
        return listOf(
            ScenarioAgentCommand.UpdateTask(update),
            ScenarioAgentCommand.SendSms(
                taskId = update.taskId,
                to = "Ole",
                displayName = "Ole",
                message = "家庭采购请按 $deliveryWindow 配送来确认，确认前先不要自动下单。",
                semanticPurpose = FamilyShoppingTaskSurface.PURPOSE_DELIVERY_WINDOW,
            ),
            ScenarioAgentCommand.WaitSms(
                taskId = update.taskId,
                contact = "Ole",
                reason = "等待 Ole 确认新的家庭采购配送窗口",
            ),
        )
    }

    private fun shoppingStatusCommands(userText: String): List<ScenarioAgentCommand> =
        listOf(
            ScenarioAgentCommand.UpdateTask(
                FamilyShoppingTaskSurface.statusAnswer(
                    userText = userText,
                    stageText = shoppingStage.stageText(),
                    excludedItems = shoppingExcludedItems,
                    addedItems = shoppingAddedItems,
                    deliveryWindow = shoppingDeliveryWindow,
                    completed = shoppingStage.progressCompleted(),
                    status = shoppingStage.surfaceStatus(),
                ),
            ),
        )

    private fun shoppingClarificationCommands(
        userText: String,
        reason: String,
    ): List<ScenarioAgentCommand> =
        listOf(ScenarioAgentCommand.UpdateTask(FamilyShoppingTaskSurface.clarificationNeeded(userText, reason)))

    private fun shoppingOutOfScopeCommands(userText: String): List<ScenarioAgentCommand> =
        listOf(ScenarioAgentCommand.UpdateTask(FamilyShoppingTaskSurface.outOfScope(userText)))

    private fun shoppingPurchaseMessage(): String {
        val baseItems = listOf("低脂牛奶", "常用洗衣液", "水果可选")
            .filterNot { item -> shoppingExcludedItems.any { excluded -> item.contains(excluded) || excluded.contains(item) } }
        val itemText = (baseItems + shoppingAddedItems).distinct().joinToString("、")
        val deliveryText = shoppingDeliveryWindow?.let { "，配送时间按 $it" }.orEmpty()
        return "请帮忙锁定家庭采购：$itemText$deliveryText，谢谢。"
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

    fun shouldSuppressPlannerForEvent(event: SystemRuntimeEvent): Boolean =
        shoppingStage == ShoppingStage.CANCELLED &&
            event.id in familyShoppingFollowupEventIds

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
            "petsmart-open-slot" -> listOf(
                OneHourFlowEffect.CreateTask(PetGroomingTaskSurface.openSlotSeed(event.body)),
            ).also { petCareStage = PetCareStage.OPEN_SLOT }
            "driver-1320-confirm" -> ifPetAccepted(
                PetGroomingTaskSurface.driverPickupConfirmation(event.body),
                PetCareStage.DRIVER_CONFIRMED,
            )
            "ella-shopping-followup" -> ifShoppingActive(
                FamilyShoppingTaskSurface.priorityFollowup(event.body),
                ShoppingStage.PRIORITY_UPDATED,
            )
            "property-courier-help" -> listOf(OneHourFlowEffect.UpdateTask(ColdchainDeliveryTaskSurface.propertyHelp(event.body)))
            "property-coldchain-secured" -> listOf(OneHourFlowEffect.UpdateTask(ColdchainDeliveryTaskSurface.propertyConfirmed(event.body)))
            "driver-kylin-picked-up" -> ifPetAccepted(
                PetGroomingTaskSurface.driverPickedUpKylin(event.body),
                PetCareStage.PICKED_UP,
            )
            "driver-arrived-petsmart" -> ifPetAccepted(
                PetGroomingTaskSurface.driverArrivedPetSmart(event.body),
                PetCareStage.ARRIVED_AT_PETSMART,
            )
            "petsmart-service-started" -> ifPetAccepted(
                PetGroomingTaskSurface.serviceStarted(event.body, petCareExpediteRequested),
                PetCareStage.SERVICE_STARTED,
            )
            "ella-shopping-clarify" -> ifShoppingActive(
                FamilyShoppingTaskSurface.clarifiedList(event.body),
                ShoppingStage.LIST_CLARIFIED,
            )
            "petsmart-service-progress" -> ifPetAccepted(
                PetGroomingTaskSurface.serviceProgress(event.body, petCareExpediteRequested),
                PetCareStage.SERVICE_PROGRESS,
            )
            else -> emptyList()
        }

    private fun handleRuntimeNotification(event: RuntimeNotificationEvent): List<OneHourFlowEffect> =
        when (event.id) {
            "property-parking-notice" -> ifPetAccepted(PetGroomingTaskSurface.propertyParkingNotice(event.body))
            "pharmacy-restock" -> listOf(OneHourFlowEffect.CreateTask(HealthSupplyTaskSurface.pharmacyRestock(event.body)))
            "market-delivery-window" -> ifShoppingActive(
                FamilyShoppingTaskSurface.marketDeliveryCandidate(event.body),
                ShoppingStage.CANDIDATE_FOUND,
            )
            "courier-coldchain-arriving" -> listOf(OneHourFlowEffect.CreateTask(ColdchainDeliveryTaskSurface.arriving(event.body)))
            "courier-coldchain-delivered" -> listOf(OneHourFlowEffect.UpdateTask(ColdchainDeliveryTaskSurface.delivered(event.body)))
            "health-supply-candidate" -> listOf(OneHourFlowEffect.UpdateTask(HealthSupplyTaskSurface.deliveryCandidate(event.body)))
            "market-order-locked" -> shoppingOrderLockedEffects(event.body)
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
                callSessionId = event.callSessionId ?: event.id,
                personaId = event.personaId ?: event.source.lowercase(),
            ),
        )

    private fun handleCallEnded(event: CallEndedEvent): List<OneHourFlowEffect> {
        val transcript = FamilyShoppingTaskSurface.resolveCallTranscript(
            audioRef = event.audioRef,
            transcriptText = event.transcript,
            contact = event.contact,
        )
        shoppingStage = if (transcript.userDeclinedPurchase) {
            ShoppingStage.CANCELLED
        } else {
            ShoppingStage.CALL_CAPTURED
        }
        shoppingDeliveryWindow = null
        shoppingExcludedItems.clear()
        shoppingAddedItems.clear()
        return listOf(
            OneHourFlowEffect.ClearActiveCall,
            OneHourFlowEffect.ClearSystemLayer(setOf(event.id, event.id.removeSuffix("-ended"))),
            OneHourFlowEffect.CreateTask(
                FamilyShoppingTaskSurface.fromCallTranscript(transcript),
            ),
        )
    }

    private fun handleReminder(event: ReminderFiredEvent): List<OneHourFlowEffect> =
        if (petCareAccepted) {
            petCareStage = PetCareStage.REMINDER_FIRED
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

    private fun ifPetAccepted(
        update: ScenarioTaskUpdate,
        nextStage: PetCareStage? = null,
    ): List<OneHourFlowEffect> =
        if (petCareAccepted) {
            nextStage?.let { petCareStage = it }
            listOf(OneHourFlowEffect.UpdateTask(update))
        } else {
            emptyList()
        }

    private fun ifShoppingActive(
        update: ScenarioTaskUpdate,
        nextStage: ShoppingStage,
    ): List<OneHourFlowEffect> {
        if (shoppingStage == ShoppingStage.CANCELLED) {
            return emptyList()
        }
        if (!shoppingStage.shouldKeepPurchaseStateFor(nextStage)) {
            shoppingStage = nextStage
        }
        return listOf(OneHourFlowEffect.UpdateTask(update))
    }

    private fun ShoppingStage.shouldKeepPurchaseStateFor(nextStage: ShoppingStage): Boolean =
        this in setOf(
            ShoppingStage.ORDER_REQUESTED,
            ShoppingStage.ORDER_HELD,
            ShoppingStage.ORDER_LOCKED,
        ) &&
            nextStage in setOf(
                ShoppingStage.PRIORITY_UPDATED,
                ShoppingStage.CANDIDATE_FOUND,
                ShoppingStage.LIST_CLARIFIED,
            )

    private fun shoppingOrderLockedEffects(messageBody: String): List<OneHourFlowEffect> {
        if (shoppingStage == ShoppingStage.CANCELLED) return emptyList()
        return if (shoppingStage == ShoppingStage.ORDER_REQUESTED) {
            shoppingStage = ShoppingStage.ORDER_LOCKED
            listOf(OneHourFlowEffect.UpdateTask(FamilyShoppingTaskSurface.orderLocked(messageBody)))
        } else {
            shoppingStage = ShoppingStage.ORDER_HELD
            listOf(OneHourFlowEffect.UpdateTask(FamilyShoppingTaskSurface.orderHeld(messageBody)))
        }
    }

    private fun ScenarioAgentCommand.requestsPetCareExpedite(): Boolean =
        this is ScenarioAgentCommand.SendSms &&
            taskId == PetGroomingTaskSurface.TASK_ID &&
            semanticPurpose == PetGroomingTaskSurface.PURPOSE_EXPEDITE_SERVICE

    private fun ScenarioAgentCommand.requestsPetCareReschedule(): Boolean =
        this is ScenarioAgentCommand.SendSms &&
            taskId == PetGroomingTaskSurface.TASK_ID &&
            semanticPurpose == PetGroomingTaskSurface.PURPOSE_RESCHEDULE_SERVICE

    private fun ScenarioAgentCommand.requestsPetCareCancel(): Boolean =
        this is ScenarioAgentCommand.SendSms &&
            taskId == PetGroomingTaskSurface.TASK_ID &&
            semanticPurpose == PetGroomingTaskSurface.PURPOSE_CANCEL_SERVICE

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

    private fun ScenarioAgentCommand.requestsShoppingConfirmPurchase(): Boolean =
        this is ScenarioAgentCommand.SendSms &&
            taskId == FamilyShoppingTaskSurface.TASK_ID &&
            semanticPurpose == FamilyShoppingTaskSurface.PURPOSE_CONFIRM_PURCHASE

    private fun ScenarioAgentCommand.requestsShoppingCancelPurchase(): Boolean =
        this is ScenarioAgentCommand.SendSms &&
            taskId == FamilyShoppingTaskSurface.TASK_ID &&
            semanticPurpose == FamilyShoppingTaskSurface.PURPOSE_CANCEL_PURCHASE

    private fun ScenarioAgentCommand.requestsShoppingDeliveryWindow(): Boolean =
        this is ScenarioAgentCommand.SendSms &&
            taskId == FamilyShoppingTaskSurface.TASK_ID &&
            semanticPurpose == FamilyShoppingTaskSurface.PURPOSE_DELIVERY_WINDOW

    private fun ScenarioAgentCommand.requestsShoppingAddItems(): Boolean =
        this is ScenarioAgentCommand.SendSms &&
            taskId == FamilyShoppingTaskSurface.TASK_ID &&
            semanticPurpose == FamilyShoppingTaskSurface.PURPOSE_ADD_ITEMS

    private fun ScenarioAgentCommand.opensShoppingTask(): Boolean =
        when (this) {
            is ScenarioAgentCommand.CreateTask -> seed.taskId == FamilyShoppingTaskSurface.TASK_ID
            is ScenarioAgentCommand.UpdateTask -> update.taskId == FamilyShoppingTaskSurface.TASK_ID
            else -> false
        }

    private fun ScenarioAgentCommand.closesShoppingTaskWithoutPurchase(): Boolean =
        when (this) {
            is ScenarioAgentCommand.CreateTask ->
                seed.taskId == FamilyShoppingTaskSurface.TASK_ID &&
                    seed.status == ScenarioSurfaceStatus.DONE &&
                    seed.decision == null
            is ScenarioAgentCommand.UpdateTask ->
                update.taskId == FamilyShoppingTaskSurface.TASK_ID &&
                    update.status == ScenarioSurfaceStatus.DONE &&
                    update.decision == null
            is ScenarioAgentCommand.CompleteTask -> taskId == FamilyShoppingTaskSurface.TASK_ID
            else -> false
        }

    private fun PetCareStage.allowsDirectScheduleChange(): Boolean =
        this in setOf(
            PetCareStage.ACCEPTED,
            PetCareStage.DRIVER_CONFIRMED,
            PetCareStage.REMINDER_FIRED,
        )

    private fun PetCareStage.stageText(): String =
        when (this) {
            PetCareStage.NONE -> "还没有 Kylin 洗护任务"
            PetCareStage.OPEN_SLOT -> "PetSmart 14:00 空档待确认"
            PetCareStage.ACCEPTED -> "已改约 14:00，等待司机确认"
            PetCareStage.DRIVER_CONFIRMED -> "司机已确认 13:20 接 Kylin"
            PetCareStage.REMINDER_FIRED -> "已提醒下楼，等待司机接到 Kylin"
            PetCareStage.PICKED_UP -> "已由老陈接上车，正在去 PetSmart"
            PetCareStage.ARRIVED_AT_PETSMART -> "已到 PetSmart，等待开洗"
            PetCareStage.SERVICE_STARTED -> "已在 PetSmart 开始洗澡和去浮毛"
            PetCareStage.SERVICE_PROGRESS -> "PetSmart 洗护进行中"
            PetCareStage.RESCHEDULED -> "已请求改期，当前 14:00 链路停止"
            PetCareStage.CANCELLED -> "已取消当前 14:00 洗护"
            PetCareStage.DECLINED -> "已保留原来的 17:00 只洗澡安排"
        }

    private fun PetCareStage.etaText(expediteRequested: Boolean): String =
        when (this) {
            PetCareStage.SERVICE_STARTED -> "按记忆推算洗澡约 1.5 小时、去浮毛约 30 到 40 分钟，预计 16:00 左右完成。"
            PetCareStage.SERVICE_PROGRESS ->
                if (expediteRequested) {
                    "门店反馈毛量比上次多，但已按你的提醒优先处理，预计仍在 16:00 左右完成。"
                } else {
                    "门店反馈浮毛比上次多，预计 16:15 左右完成。"
                }
            PetCareStage.RESCHEDULED -> "等待 PetSmart 确认新档期。"
            PetCareStage.CANCELLED -> "当前洗护和接送已停止。"
            else -> "后续仍按当前场景 runtime 推进。"
        }

    private fun PetCareStage.progressCompleted(): Int =
        when (this) {
            PetCareStage.NONE,
            PetCareStage.OPEN_SLOT -> 0
            PetCareStage.ACCEPTED -> 2
            PetCareStage.DRIVER_CONFIRMED -> 3
            PetCareStage.REMINDER_FIRED -> 4
            PetCareStage.PICKED_UP,
            PetCareStage.ARRIVED_AT_PETSMART -> 5
            PetCareStage.SERVICE_STARTED,
            PetCareStage.SERVICE_PROGRESS -> 6
            PetCareStage.RESCHEDULED,
            PetCareStage.CANCELLED,
            PetCareStage.DECLINED -> 7
        }

    private fun ShoppingStage.stageText(): String =
        when (this) {
            ShoppingStage.NONE -> "还没有家庭采购任务"
            ShoppingStage.CALL_CAPTURED -> "已从 Ella 通话提取家庭采购待办"
            ShoppingStage.PRIORITY_UPDATED -> "Ella 已确认牛奶和洗衣液优先"
            ShoppingStage.CANDIDATE_FOUND -> "Ole 有货，等待你确认是否购买"
            ShoppingStage.LIST_CLARIFIED -> "采购清单已更新，等待购买确认"
            ShoppingStage.DELIVERY_WINDOW_REQUESTED -> "已请求调整配送时间，等待 Ole 确认"
            ShoppingStage.ORDER_REQUESTED -> "已请求 Ole 锁定订单，等待库存和配送确认"
            ShoppingStage.ORDER_HELD -> "Ole 已保留库存，等待你确认后再付款"
            ShoppingStage.ORDER_LOCKED -> "家庭采购已下单"
            ShoppingStage.CANCELLED -> "家庭采购已停止，未下单"
        }

    private fun ShoppingStage.progressCompleted(): Int =
        when (this) {
            ShoppingStage.NONE -> 0
            ShoppingStage.CALL_CAPTURED -> 1
            ShoppingStage.PRIORITY_UPDATED -> 2
            ShoppingStage.CANDIDATE_FOUND,
            ShoppingStage.ORDER_HELD -> 3
            ShoppingStage.LIST_CLARIFIED,
            ShoppingStage.DELIVERY_WINDOW_REQUESTED,
            ShoppingStage.ORDER_REQUESTED -> 4
            ShoppingStage.ORDER_LOCKED,
            ShoppingStage.CANCELLED -> 5
        }

    private fun ShoppingStage.surfaceStatus(): ScenarioSurfaceStatus =
        when (this) {
            ShoppingStage.CANDIDATE_FOUND,
            ShoppingStage.ORDER_HELD -> ScenarioSurfaceStatus.BLOCKED
            ShoppingStage.ORDER_LOCKED,
            ShoppingStage.CANCELLED -> ScenarioSurfaceStatus.DONE
            else -> ScenarioSurfaceStatus.RUNNING
        }

    companion object {
        val familyShoppingFollowupEventIds: Set<String> = setOf(
            "ella-shopping-followup",
            "market-delivery-window",
            "ella-shopping-clarify",
            "market-order-locked",
        )

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
                    visibleTextPolicyForEvent(event)?.let { put("visibleTextPolicy", it) }
                    familyShoppingCallTranscriptPolicy(event)?.let { put("familyShoppingCallTranscriptPolicy", it) }
                    petRouteUpdateProtocol(event)?.let { put("petRouteUpdateProtocol", it) }
                    petDriverConfirmationProtocol(event)?.let { put("petDriverConfirmationProtocol", it) }
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
                            "When the authorized task does not yet exist, use create_task instead of ask_user or update_task.",
                            "The planner owns all user-visible task surface text for authorized system events; do not copy deterministic runtime reference wording.",
                            "For every create_task or update_task, include one concise AGENT conversation or summary written by the planner for the user, plus logs that describe the interpreted task state; never expose eventFact.title/body verbatim as the visible task text.",
                            "Visible text must be task state or Agent action, not a message broadcast or call transcript availability notice.",
                            "When plannerPolicy.visibleTextPolicy is present, all visible title/subtitle/summary/conversations/logs/decision.text must follow preferredUserVisibleSummary and must avoid every forbiddenVisibleFragment.",
                            "When plannerPolicy.familyShoppingCallTranscriptPolicy is present, first normalize the call transcript into purchaseDisposition and then choose commands from that normalized result.",
                            "Avoid runtime broadcast prefixes such as 'PetSmart 通知：', 'PetSmart 来信：', or 'Ella 通话结束：' on visible task surfaces.",
                            "Good visible text examples: '14:00 洗护档期空出来了，需要你确认是否调整 Kylin 的预约'; '已从 Ella 电话整理出家庭采购待办'.",
                            "Bad visible text examples: 'PetSmart 来信：原定 14:00 的客人取消...'; 'Ella 通话结束：通话结束，音频可用于提取家庭采购待办'.",
                        ),
                    ),
                )
                .toString()
        }

        private fun visibleTextPolicyForEvent(event: SystemRuntimeEvent): JSONObject? =
            when (event.id) {
                "petsmart-open-slot" -> JSONObject()
                    .put("taskSurfaceIntent", "ask_user_to_confirm_pet_grooming_slot_change")
                    .put("preferredUserVisibleSummary", "14:00 洗护档期空出来了，需要你确认是否调整 Kylin 的预约")
                    .put(
                        "forbiddenVisibleFragments",
                        JSONArray(
                            listOf(
                                "PetSmart 来信",
                                "PetSmart 通知",
                                "原本 14:00 的客人计划有变",
                                "原定 14:00 的客人计划有变",
                                "客人计划有变",
                            ),
                        ),
                    )
                "ella-call-ended" -> JSONObject()
                    .put("taskSurfaceIntent", "create_family_shopping_task_from_call_transcript")
                    .put("preferredUserVisibleSummary", "根据 Ella 电话整理家庭采购状态")
                    .put(
                        "conditionalSummaries",
                        JSONObject()
                            .put("accepted", "已从 Ella 电话整理出家庭采购待办")
                            .put("declined", "通话中已确认暂不采购")
                            .put("needs_clarification", "通话内容需要确认采购意图"),
                    )
                    .put(
                        "forbiddenVisibleFragments",
                        JSONArray(
                            listOf(
                                "Ella 通话结束",
                                "通话结束，音频可用于提取",
                                "通话结束，转写可用于更新当前任务",
                            ),
                        ),
                    )
                else -> null
            }

        private fun familyShoppingCallTranscriptPolicy(event: SystemRuntimeEvent): JSONObject? =
            if (event is CallEndedEvent && FamilyShoppingTaskSurface.TASK_ID in authorizedTaskIdsForEvent(event)) {
                JSONObject()
                    .put("normalizationRequired", true)
                    .put("normalizeField", "purchaseDisposition")
                    .put("allowedValues", JSONArray(listOf("accepted", "declined", "needs_clarification")))
                    .put(
                        "acceptedMeaning",
                        "The user authorizes or asks the agent to proceed with family shopping or order coordination.",
                    )
                    .put(
                        "declinedMeaning",
                        "The user refuses, rejects, postpones, cancels, says no need, says they do not want to buy, or otherwise tells Ella not to proceed with this purchase.",
                    )
                    .put(
                        "needsClarificationMeaning",
                        "The transcript is about shopping but does not clearly say whether to buy, skip, or change the list.",
                    )
                    .put(
                        "requiredCommandsByDisposition",
                        JSONObject()
                            .put(
                                "declined",
                                JSONObject()
                                    .put("taskId", FamilyShoppingTaskSurface.TASK_ID)
                                    .put("status", "DONE")
                                    .put("subtitle", "通话中已确认暂不采购")
                                    .put("decision", "omit")
                                    .put("sms", "none")
                                    .put("forbidden", JSONArray(listOf("BLOCKED", "ask_user", "purchase decision actions", "market follow-up"))),
                            )
                            .put(
                                "accepted",
                                JSONObject()
                                    .put("taskId", FamilyShoppingTaskSurface.TASK_ID)
                                    .put("status", "RUNNING")
                                    .put("decision", "omit unless a user choice is still required")
                                    .put("summary", "已从 Ella 电话整理出家庭采购待办"),
                            )
                            .put(
                                "needs_clarification",
                                JSONObject()
                                    .put("taskId", FamilyShoppingTaskSurface.TASK_ID)
                                    .put("status", "BLOCKED")
                                    .put("decision", "include a concise clarification decision if needed"),
                            ),
                    )
                    .put(
                        "rules",
                        JSONArray(
                            listOf(
                                "Do not use keyword enumeration as the source of truth; infer the user's meaning from the full transcript.",
                                "If the transcript says the user does not want to buy, the normalized purchaseDisposition is declined even if shopping items were mentioned earlier.",
                                "For declined, create_task or update_task must complete the family shopping task as DONE and must not keep a purchase confirmation decision.",
                                "For declined, do not convert the transcript into a BLOCKED shopping candidate.",
                            ),
                        ),
                    )
            } else {
                null
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
                            "If you emit any side-effect command such as send_sms or wait_sms, the first command must be update_task with a concise AGENT conversation confirming what you are doing for the user.",
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
                                "update_task: status RUNNING, clear decision, participants include PetSmart and Driver, conversations include one concise AGENT confirmation, logs include the user's 14:00 acceptance",
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
                                "The user-facing confirmation should be newly written for this turn; do not copy deterministic reference wording.",
                            ),
                        ),
                    )
            } else {
                null
            }

        private fun petDriverConfirmationProtocol(event: SystemRuntimeEvent): JSONObject? =
            if (event.id == "driver-1320-confirm") {
                JSONObject()
                    .put("appliesWhen", "Driver confirms the 13:20 pickup")
                    .put("reminderTitle", "送 Kylin 下楼")
                    .put("reminderScheduledFor", "2027-04-25T13:20:00")
                    .put(
                        "requiredCommandOrder",
                        JSONArray(
                            listOf(
                                "update_task: status RUNNING, participants preserve PetSmart and Driver, conversations include one concise AGENT confirmation written for this turn, logs include the Driver reply",
                                "create_reminder: title 送 Kylin 下楼, scheduledFor 2027-04-25T13:20:00",
                            ),
                        ),
                    )
                    .put(
                        "rules",
                        JSONArray(
                            listOf(
                                "Do not send SMS for this event.",
                                "Do not copy deterministic reference wording.",
                                "Keep the reminder time exactly 13:20.",
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
                            "When asking for a visible decision, include one concise AGENT conversation written for this observed fact; do not copy runtime reference wording.",
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
                    "Manage the pet grooming coordination task from the observed shop, driver, property, or reminder fact. Create the task if it does not exist; otherwise update the same task."
                else -> "No task command is required unless the observed fact clearly belongs to an authorized task."
            }

        private fun currentObservedContext(event: SystemRuntimeEvent): String? =
            when (event) {
                is CallEndedEvent -> (
                    event.transcript?.trim()?.takeIf { it.isNotBlank() }?.let {
                        FamilyShoppingTaskSurface.callTranscriptContext(event.contact, it)
                    }
                        ?: FamilyShoppingTaskSurface.transcriptForAudioRef(event.audioRef)
                        ?: event.callSessionId?.let(FamilyShoppingTaskSurface::transcriptForIncomingCall)
                        ?: if (event.contact.equals("Ella", ignoreCase = true)) {
                            FamilyShoppingTaskSurface.transcriptForIncomingCall("ella-call")
                        } else {
                            null
                        }
                    )?.let { "Call transcript from ${event.contact}: ${it.transcript}" }
                else -> null
            }

        fun commandAuthorizationForEvent(event: SystemRuntimeEvent): ScenarioCommandAuthorization {
            if (event.hasAuthoritativeLocalTaskSurface()) return ScenarioCommandAuthorization()
            return ScenarioCommandAuthorization(
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
        }

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
                sms = when (cleanTaskId) {
                    PetGroomingTaskSurface.TASK_ID -> setOf(
                        ScenarioSmsAuthorization(PetGroomingTaskSurface.TASK_ID, "PetSmart"),
                        ScenarioSmsAuthorization(PetGroomingTaskSurface.TASK_ID, "Driver"),
                    )
                    FamilyShoppingTaskSurface.TASK_ID -> setOf(
                        ScenarioSmsAuthorization(FamilyShoppingTaskSurface.TASK_ID, "Ole"),
                    )
                    else -> emptySet()
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
                "market-delivery-window" -> FamilyShoppingTaskSurface.marketDeliveryCandidate(event.body)
                    .decision
                    ?.actions
                    .orEmpty()
                    .map { it.label to it.key }
                else -> emptyList()
            }

        fun plannerOwnsVisibleDecisionSurface(event: SystemRuntimeEvent): Boolean =
            decisionActionsForEvent(event).isNotEmpty()

        fun plannerOwnsSystemTaskSurface(event: SystemRuntimeEvent): Boolean =
            authorizedTaskIdsForEvent(event).isNotEmpty() &&
                !event.hasAuthoritativeLocalTaskSurface()

        private fun SystemRuntimeEvent.hasAuthoritativeLocalTaskSurface(): Boolean =
            when {
                id in setOf(
                    "pharmacy-restock",
                    "health-supply-candidate",
                    "health-supply-held",
                    "courier-coldchain-arriving",
                    "courier-coldchain-delivered",
                    "property-courier-help",
                    "property-coldchain-secured",
                    "petsmart-service-started",
                    "petsmart-service-progress",
                ) -> true
                else -> false
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

        val manuallyCompletedEventIds: Set<String> = setOf(
            "ella-call-ended",
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
