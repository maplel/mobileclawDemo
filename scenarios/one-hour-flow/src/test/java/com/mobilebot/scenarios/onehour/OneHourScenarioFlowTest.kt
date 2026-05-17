package com.mobilebot.scenarios.onehour

import com.mobilebot.scenarios.runtime.ScenarioSurfaceStatus
import com.mobilebot.scenarios.runtime.ScenarioAgentCommand
import com.mobilebot.scenarios.familyshopping.FamilyShoppingTaskSurface
import com.mobilebot.systemruntime.CallEndedEvent
import com.mobilebot.systemruntime.IncomingCallEvent
import com.mobilebot.systemruntime.IncomingSmsEvent
import com.mobilebot.systemruntime.ReminderFiredEvent
import com.mobilebot.systemruntime.RuntimeNotificationEvent
import com.mobilebot.systemruntime.SystemRuntimeEvent
import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class OneHourScenarioFlowTest {
    private val now: LocalDateTime = LocalDateTime.of(2027, 4, 25, 13, 5)

    @Test
    fun petSmartOpenSlotCreatesBlockedPetTaskWithDecision() {
        val effects = OneHourScenarioFlow().handle(
            sms(
                id = "petsmart-open-slot",
                source = "PetSmart",
                body = "14:00 客人计划有变，现在可以安排 Kylin 洗澡和去浮毛。",
            ),
        )

        val createTask = effects.single() as OneHourFlowEffect.CreateTask

        assertEquals("pet-grooming-live", createTask.seed.taskId)
        assertEquals(ScenarioSurfaceStatus.BLOCKED, createTask.seed.status)
        assertEquals(listOf("可以", "不改了"), createTask.seed.decision?.actions?.map { it.label })
        assertTrue(createTask.seed.logs.first().text.contains("PetSmart"))
    }

    @Test
    fun driverAndReminderEventsAreSuppressedUntilPetSlotAccepted() {
        val flow = OneHourScenarioFlow()

        assertTrue(
            flow.handle(
                sms(
                    id = "driver-1320-confirm",
                    source = "Driver",
                    body = "好的，我 13:20 到楼下等 Kylin。",
                ),
            ).isEmpty(),
        )
        assertTrue(
            flow.handle(
                reminder(
                    id = "kylin-downstairs-reminder",
                    title = "送 Kylin 下楼",
                    body = "司机老陈即将到楼下。",
                ),
            ).isEmpty(),
        )
    }

    @Test
    fun acceptedPetSlotAllowsDriverReplyAndReminderEffects() {
        val flow = OneHourScenarioFlow()
        val accept = flow.acceptPetCareSlot("可以")

        val driverEffects = flow.handle(
            sms(
                id = "driver-1320-confirm",
                source = "Driver",
                body = "好的，我 13:20 到楼下等 Kylin。",
            ),
        )
        val reminderEffects = flow.handle(
            reminder(
                id = "kylin-downstairs-reminder",
                title = "送 Kylin 下楼",
                body = "司机老陈即将到楼下。",
            ),
        )

        assertEquals("pet-grooming-live", accept.update.taskId)
        assertTrue(driverEffects.single() is OneHourFlowEffect.UpdateTask)
        assertTrue(reminderEffects.any { it is OneHourFlowEffect.ShowSystemLayer })
        assertTrue(reminderEffects.any { it is OneHourFlowEffect.UpdateTask })
    }

    @Test
    fun plannerDriverSideEffectUnlocksPetFollowupEvents() {
        val flow = OneHourScenarioFlow()
        flow.updateRuntimeStateFromPlannerCommands(
            listOf(
                ScenarioAgentCommand.SendSms(
                    taskId = "pet-grooming-live",
                    to = "Driver",
                    message = "13:20 来接 Kylin。",
                ),
            ),
        )

        val driverEffects = flow.handle(
            sms(
                id = "driver-1320-confirm",
                source = "Driver",
                body = "好的，我 13:20 到楼下等 Kylin。",
            ),
        )

        assertTrue(driverEffects.single() is OneHourFlowEffect.UpdateTask)
    }

    @Test
    fun acceptedPetSlotCommandReferencesIncludeSmsAndListener() {
        val commands = OneHourScenarioFlow().acceptPetCareSlotCommands("可以")
        val update = commands.first() as ScenarioAgentCommand.UpdateTask

        assertEquals(listOf("PS", "DR"), update.update.participants?.map { it.label })
        assertEquals(
            listOf("system_update", "PetSmart", "Driver", "Driver"),
            commands.map {
                when (it) {
                    is ScenarioAgentCommand.UpdateTask -> "system_update"
                    is ScenarioAgentCommand.SendSms -> it.to
                    is ScenarioAgentCommand.WaitSms -> it.contact
                    else -> it.taskId
                }
            },
        )
    }

    @Test
    fun driverConfirmationReferenceCommandsIncludeReminderCreation() {
        val flow = OneHourScenarioFlow()
        flow.acceptPetCareSlot("可以")
        val event = sms(
            id = "driver-1320-confirm",
            source = "Driver",
            body = "好的，我 13:20 到楼下等 Kylin。",
        )
        val commands = OneHourScenarioFlow.commandReferences(event, flow.handle(event))
        val reminder = commands.single { it is ScenarioAgentCommand.CreateReminder } as ScenarioAgentCommand.CreateReminder

        assertTrue(commands.any { it is ScenarioAgentCommand.UpdateTask })
        assertEquals("pet-grooming-live", reminder.taskId)
        assertEquals("2027-04-25T13:20:00", reminder.scheduledFor)
    }

    @Test
    fun incomingCallReferenceCommandsCanBeEmptyForPlannerNoop() {
        val event = IncomingCallEvent(
            id = "ella-call",
            occurredAt = now.withHour(13).withMinute(9),
            source = "Ella",
            title = "Ella 来电",
            body = "Ella 来电，通话中提到家庭采购。",
            contact = "Ella",
        )
        val effects = OneHourScenarioFlow().handle(event)
        val commands = OneHourScenarioFlow.commandReferences(event, effects)

        assertTrue(effects.single() is OneHourFlowEffect.ShowSystemLayer)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun userDecisionPlannerPolicyDoesNotEmbedClarificationReferenceText() {
        val policyJson = OneHourScenarioFlow.userDecisionPlannerPolicyJson(
            userText = "狗死了",
            taskId = "pet-grooming-live",
            displayedActions = listOf("可以" to "accept", "不改了" to "decline"),
        )
        val instruction = OneHourScenarioPolicy.userDecisionInstruction(
            userText = "狗死了",
            plannerPolicyJson = policyJson,
        )

        assertTrue(instruction.contains("plannerPolicy"))
        assertTrue(instruction.contains("对象不存在"))
        assertTrue(instruction.contains("更新任务为停止/完成"))
        assertFalse(instruction.contains("你是想改到 14:00，还是保留原来的 17:00？"))
        assertFalse(instruction.contains("referenceCommands"))
        val policy = JSONObject(policyJson)
        assertEquals("PetSmart", policy.getJSONArray("authorizedSms").getJSONObject(0).getString("to"))
        assertEquals("Driver", policy.getJSONArray("authorizedSms").getJSONObject(1).getString("to"))
        assertEquals(
            listOf("PS", "DR"),
            labels(policy.getJSONObject("participantPolicy").getJSONArray("sideEffectTargetParticipants")),
        )
        assertEquals(listOf("PS", "DR"), labels(policy.getJSONArray("requiredParticipants")))
        val protocol = policy.getJSONObject("petSlotAcceptanceProtocol")
        assertEquals("13:20", protocol.getString("driverPickupTime"))
        assertTrue(protocol.getJSONArray("requiredCommandOrder").toString().contains("wait_sms"))
        assertTrue(protocol.getJSONArray("rules").toString().contains("13:45"))
        assertEquals("可以", policy.getJSONArray("visibleDecisionActions").getJSONObject(0).getString("label"))
    }

    @Test
    fun systemEventPlannerPolicyAuthorizesScenarioSideEffectsWithoutReferences() {
        val event = sms(
            id = "driver-1320-confirm",
            source = "Driver",
            body = "好的，我 13:20 到楼下等 Kylin。",
        )
        val policy = JSONObject(OneHourScenarioFlow.plannerPolicyJson(event))

        assertEquals("system_event", policy.getString("turn"))
        assertFalse(policy.has("eventId"))
        assertEquals("pet-grooming-live", policy.getJSONArray("taskIds").getString(0))
        assertEquals(
            "2027-04-25T13:20:00",
            policy.getJSONArray("authorizedReminders").getJSONObject(0).getString("scheduledFor"),
        )
        val participantPolicy = policy.getJSONObject("participantPolicy")
        assertEquals(listOf("PS", "DR"), labels(policy.getJSONArray("requiredParticipants")))
        assertEquals(listOf("DR"), labels(participantPolicy.getJSONArray("currentFactParticipants")))
        assertEquals(
            listOf("PS"),
            labels(
                participantPolicy
                    .getJSONArray("knownParticipantsByTask")
                    .getJSONObject(0)
                    .getJSONArray("baselineParticipants"),
            ),
        )
        assertFalse(policy.toString().contains("司机老陈即将到楼下。"))
    }

    @Test
    fun propertyRoutePlannerPolicyAuthorizesDriverSmsProtocol() {
        val event = sms(
            id = "property-parking-notice",
            source = "物业管家",
            body = "B2 西侧临停区 13:30 后检修，建议司机走东门。",
        )
        val policy = JSONObject(OneHourScenarioFlow.plannerPolicyJson(event))

        assertFalse(policy.has("eventId"))
        assertEquals("pet-grooming-live", policy.getJSONArray("taskIds").getString(0))
        assertEquals("Driver", policy.getJSONArray("authorizedSms").getJSONObject(0).getString("to"))
        assertEquals(
            listOf("PS", "物", "DR"),
            labels(policy.getJSONArray("requiredParticipants")),
        )
        val protocol = policy.getJSONObject("petRouteUpdateProtocol")
        assertTrue(protocol.getJSONArray("requiredCommandOrder").toString().contains("send_sms to Driver"))
        assertTrue(protocol.getJSONArray("rules").toString().contains("same turn"))
        assertFalse(policy.toString().contains("property-parking-notice"))
    }

    @Test
    fun plannerPolicyAuthorizesTaskWithoutReferenceCommands() {
        val event = sms(
            id = "petsmart-open-slot",
            source = "PetSmart",
            body = "14:00 客人计划有变，现在可以安排 Kylin 洗澡和去浮毛。",
        )
        val policy = JSONObject(OneHourScenarioFlow.plannerPolicyJson(event))

        assertEquals("pet-grooming-live", policy.getJSONArray("taskIds").getString(0))
        assertEquals("可以", policy.getJSONArray("visibleDecisionActions").getJSONObject(0).getString("label"))
        assertTrue(policy.getJSONObject("decisionPolicy").getBoolean("visibleDecisionActionsRequired"))
        assertEquals(0, policy.getJSONArray("authorizedSms").length())
        assertEquals(0, policy.getJSONArray("authorizedReminders").length())
        val participantPolicy = policy.getJSONObject("participantPolicy")
        assertEquals(listOf("PS"), labels(policy.getJSONArray("requiredParticipants")))
        assertEquals(listOf("PS"), labels(participantPolicy.getJSONArray("currentFactParticipants")))
        assertEquals(
            listOf("PS"),
            labels(
                participantPolicy
                    .getJSONArray("knownParticipantsByTask")
                    .getJSONObject(0)
                    .getJSONArray("baselineParticipants"),
            ),
        )
    }

    @Test
    fun callEndedPlannerPolicyIncludesObservedTranscriptWithoutScriptId() {
        val event = CallEndedEvent(
            id = "ella-call-ended",
            occurredAt = now.withHour(13).withMinute(11),
            source = "Ella",
            title = "Ella 通话结束",
            body = "通话结束，音频可用于提取家庭采购待办。",
            contact = "Ella",
            audioRef = "ella-call-ended",
        )
        val policy = JSONObject(OneHourScenarioFlow.plannerPolicyJson(event))

        assertFalse(policy.has("eventId"))
        assertEquals("family-shopping-live", policy.getJSONArray("taskIds").getString(0))
        assertTrue(policy.getString("emptyCommands").contains("avoid_empty"))
        assertTrue(policy.getString("taskPlanningGoal").contains("family shopping"))
        assertTrue(policy.getString("currentObservedContext").contains("低脂牛奶"))
        assertTrue(policy.getString("currentObservedContext").contains("常用洗衣液"))
        assertFalse(policy.toString().contains("ella-call-ended"))
    }

    @Test
    fun nonLayerScriptEventsHavePlannerTaskAuthorization() {
        val missing = scriptEvents()
            .map { it.toRuntimeEvent() }
            .filterNot { it.id == "ella-call" }
            .filter { OneHourScenarioFlow.authorizedTaskIdsForEvent(it).isEmpty() }
            .map { it.id }

        assertTrue("Script events missing planner task authorization: $missing", missing.isEmpty())
    }

    @Test
    fun unclearPetSlotReplyCreatesBlockedClarificationCommand() {
        val command = OneHourScenarioFlow()
            .openSlotClarificationCommands("都可以")
            .single() as ScenarioAgentCommand.UpdateTask

        assertEquals("pet-grooming-live", command.update.taskId)
        assertEquals(ScenarioSurfaceStatus.BLOCKED, command.update.status)
        assertEquals("等待用户决策", command.update.progress.detail)
        assertEquals(listOf("可以", "不改了"), command.update.decision?.actions?.map { it.label })
        assertTrue(command.update.conversations.any { it.text == "都可以" })
        assertTrue(command.update.conversations.any { it.text.contains("14:00") && it.text.contains("17:00") })
    }

    @Test
    fun keepingOriginalPetSlotDoesNotUnlockDriverOrReminderEvents() {
        val flow = OneHourScenarioFlow()
        val keepOriginal = flow.keepOriginalPetCareSlot("不改了")

        assertEquals(ScenarioSurfaceStatus.DONE, keepOriginal.update.status)
        assertTrue(
            flow.handle(
                sms(
                    id = "driver-1320-confirm",
                    source = "Driver",
                    body = "好的，我 13:20 到楼下等 Kylin。",
                ),
            ).isEmpty(),
        )
        assertTrue(
            flow.handle(
                reminder(
                    id = "kylin-downstairs-reminder",
                    title = "送 Kylin 下楼",
                    body = "司机老陈即将到楼下。",
                ),
            ).isEmpty(),
        )
    }

    @Test
    fun allScriptEventsHaveExplicitFlowCoverage() {
        val scriptEventIds = scriptEvents().map { it.getString("id") }.toSet()

        assertTrue(
            "Script has unregistered events: ${scriptEventIds - OneHourScenarioFlow.supportedEventIds}",
            OneHourScenarioFlow.supportedEventIds.containsAll(scriptEventIds),
        )
        assertTrue(
            "Flow declares events that are not in script: ${OneHourScenarioFlow.supportedEventIds - scriptEventIds}",
            scriptEventIds.containsAll(OneHourScenarioFlow.supportedEventIds),
        )
    }

    @Test
    fun acceptedPetSlotRoutesEveryScriptEventToAnEffect() {
        val flow = OneHourScenarioFlow()
        flow.acceptPetCareSlot("可以")

        val emptyRoutes = scriptEvents()
            .map { it.getString("id") to flow.handle(it.toRuntimeEvent()) }
            .filter { (_, effects) -> effects.isEmpty() }
            .map { (id, _) -> id }

        assertTrue("Accepted route dropped events: $emptyRoutes", emptyRoutes.isEmpty())
    }

    @Test
    fun keepingOriginalPetSlotSuppressesPetFollowupEventsOnly() {
        val flow = OneHourScenarioFlow()
        flow.keepOriginalPetCareSlot("不改了")

        val routedPetEvents = scriptEvents()
            .filter { it.getString("id") in OneHourScenarioFlow.petAcceptanceRequiredEventIds }
            .map { it.getString("id") to flow.handle(it.toRuntimeEvent()) }
            .filter { (_, effects) -> effects.isNotEmpty() }
            .map { (id, _) -> id }

        assertTrue("Original-slot branch still routed pet followups: $routedPetEvents", routedPetEvents.isEmpty())
    }

    @Test
    fun incomingCallShowsLayerAndCallEndCreatesFamilyTask() {
        val flow = OneHourScenarioFlow()

        val callLayer = flow.handle(
            IncomingCallEvent(
                id = "ella-call",
                occurredAt = now.withHour(13).withMinute(9),
                source = "Ella",
                title = "Ella 来电",
                body = "Ella 来电，通话中提到家庭采购。",
                contact = "Ella",
            ),
        ).single() as OneHourFlowEffect.ShowSystemLayer
        val endedEffects = flow.handle(
            CallEndedEvent(
                id = "ella-call-ended",
                occurredAt = now.withHour(13).withMinute(11),
                source = "Ella",
                title = "Ella 通话结束",
                body = "通话结束，音频可用于提取家庭采购待办。",
                contact = "Ella",
                audioRef = "ella-call-ended",
            ),
        )

        assertEquals("接听", callLayer.actionLabel)
        assertTrue(callLayer.callTranscriptText.orEmpty().contains("低脂牛奶"))
        assertTrue(callLayer.callTranscriptText.orEmpty().contains("常用洗衣液"))
        assertTrue(endedEffects.any { it is OneHourFlowEffect.ClearActiveCall })
        assertTrue(endedEffects.any { it is OneHourFlowEffect.ClearSystemLayer })
        val familyTask = endedEffects.single { it is OneHourFlowEffect.CreateTask } as OneHourFlowEffect.CreateTask
        assertEquals(
            "family-shopping-live",
            familyTask.seed.taskId,
        )
        assertTrue(familyTask.seed.conversations.any { it.text.contains("低脂牛奶") && it.text.contains("水果") })
        assertTrue(familyTask.seed.logs.any { it.text.contains("家庭采购") && it.text.contains("水果可选") })
    }

    @Test
    fun ellaCallTranscriptMatchesScenarioContextAsset() {
        val transcript = FamilyShoppingTaskSurface.transcriptForAudioRef("ella-call-ended")
            ?: error("Missing Ella call transcript")
        val assetTranscript = agentContext()
            .getJSONArray("callTranscripts")
            .getJSONObject(0)

        assertEquals(assetTranscript.getString("audioRef"), transcript.audioRef)
        assertEquals(assetTranscript.getString("transcript"), transcript.transcript)
        assertEquals(
            (0 until assetTranscript.getJSONArray("tasks").getJSONObject(0).getJSONArray("items").length())
                .map { assetTranscript.getJSONArray("tasks").getJSONObject(0).getJSONArray("items").getString(it) },
            transcript.items,
        )
    }

    @Test
    fun denseHourNotificationsRouteToIndependentTasks() {
        val flow = OneHourScenarioFlow()
        val health = flow.handle(notification("pharmacy-restock", "药房到货提醒", "常用补给有货。"))
        val coldchain = flow.handle(notification("courier-coldchain-arriving", "冷链即将到达", "预计 13:32 到。"))
        val market = flow.handle(notification("market-order-locked", "超市订单锁定", "低脂牛奶和洗衣液已锁定。"))

        assertEquals("health-supply-live", (health.single() as OneHourFlowEffect.CreateTask).seed.taskId)
        assertEquals("coldchain-delivery-live", (coldchain.single() as OneHourFlowEffect.CreateTask).seed.taskId)
        assertEquals("family-shopping-live", (market.single() as OneHourFlowEffect.UpdateTask).update.taskId)
    }

    private fun sms(
        id: String,
        source: String,
        body: String,
    ): IncomingSmsEvent =
        IncomingSmsEvent(
            id = id,
            occurredAt = now,
            source = source,
            title = "$source 短信",
            body = body,
            from = source,
        )

    private fun reminder(
        id: String,
        title: String,
        body: String,
    ): ReminderFiredEvent =
        ReminderFiredEvent(
            id = id,
            occurredAt = now.withHour(13).withMinute(20),
            source = "system",
            title = title,
            body = body,
            reminderId = id,
        )

    private fun notification(
        id: String,
        title: String,
        body: String,
    ): RuntimeNotificationEvent =
        RuntimeNotificationEvent(
            id = id,
            occurredAt = now,
            source = "system",
            title = title,
            body = body,
        )

    private fun scriptEvents(): List<JSONObject> {
        val file = listOf(
            File("core/data/src/main/assets/skills/md/one-hour-aio/SYSTEM_RUNTIME.json"),
            File("../core/data/src/main/assets/skills/md/one-hour-aio/SYSTEM_RUNTIME.json"),
            File("../../core/data/src/main/assets/skills/md/one-hour-aio/SYSTEM_RUNTIME.json"),
        ).firstOrNull { it.isFile } ?: error("one-hour runtime script not found")
        val content = file.readText().trimStart('\uFEFF').trim()
        val events = JSONObject(content).getJSONArray("scenarioEvents")
        return (0 until events.length()).map { events.getJSONObject(it) }
    }

    private fun agentContext(): JSONObject {
        val file = listOf(
            File("core/data/src/main/assets/skills/md/one-hour-aio/AGENT_CONTEXT.json"),
            File("../core/data/src/main/assets/skills/md/one-hour-aio/AGENT_CONTEXT.json"),
            File("../../core/data/src/main/assets/skills/md/one-hour-aio/AGENT_CONTEXT.json"),
        ).firstOrNull { it.isFile } ?: error("one-hour agent context not found")
        return JSONObject(file.readText().trimStart('\uFEFF').trim())
    }

    private fun labels(array: JSONArray): List<String> =
        (0 until array.length()).map { array.getJSONObject(it).getString("label") }

    private fun JSONObject.toRuntimeEvent(): SystemRuntimeEvent {
        val id = getString("id")
        val source = getString("source")
        val title = getString("title")
        val body = getString("body")
        val timeParts = getString("time").split(":").map { it.toInt() }
        val occurredAt = now.withHour(timeParts[0]).withMinute(timeParts[1])

        return when (getString("type")) {
            "incoming_sms" -> IncomingSmsEvent(id, occurredAt, source, title, body, source)
            "incoming_call" -> IncomingCallEvent(id, occurredAt, source, title, body, source)
            "call_ended" -> CallEndedEvent(id, occurredAt, source, title, body, source, id)
            "reminder_fired" -> ReminderFiredEvent(id, occurredAt, source, title, body, id)
            "notification" -> RuntimeNotificationEvent(id, occurredAt, source, title, body)
            else -> error("Unsupported event type: ${getString("type")}")
        }
    }
}
