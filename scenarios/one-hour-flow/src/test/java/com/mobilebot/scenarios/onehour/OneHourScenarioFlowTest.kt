package com.mobilebot.scenarios.onehour

import com.mobilebot.scenarios.runtime.ScenarioSurfaceStatus
import com.mobilebot.scenarios.runtime.ScenarioAgentCommand
import com.mobilebot.scenarios.familyshopping.FamilyShoppingTaskSurface
import com.mobilebot.scenarios.petgrooming.PetGroomingTaskSurface
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
        assertTrue(createTask.seed.conversations.isEmpty())
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
    fun localUserDecisionCommandsAcceptPetSlotWithoutUserFacingPlannerText() {
        val flow = OneHourScenarioFlow()
        val commands = flow.userDecisionCommands(
            taskId = "pet-grooming-live",
            actionKey = "pet.accept_14",
            userText = "可以",
        ) ?: error("Expected local decision commands")
        val update = commands.first() as ScenarioAgentCommand.UpdateTask

        assertTrue(flow.isPetCareAccepted())
        assertEquals(null, update.update.decision)
        assertTrue(update.update.conversations.isEmpty())
        assertTrue(commands.any { it is ScenarioAgentCommand.SendSms && it.to == "PetSmart" })
        assertTrue(commands.any { it is ScenarioAgentCommand.SendSms && it.to == "Driver" })
        assertTrue(commands.any { it is ScenarioAgentCommand.WaitSms && it.contact == "Driver" })
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
        assertTrue(policy.getString("taskPlanningGoal").contains("Create the task if it does not exist"))
        assertEquals("可以", policy.getJSONArray("visibleDecisionActions").getJSONObject(0).getString("label"))
        assertTrue(policy.getJSONObject("decisionPolicy").getBoolean("visibleDecisionActionsRequired"))
        assertTrue(policy.getJSONObject("decisionPolicy").getJSONArray("rules").toString().contains("AGENT conversation"))
        assertEquals(0, policy.getJSONArray("authorizedSms").length())
        assertEquals(0, policy.getJSONArray("authorizedReminders").length())
        val visibleTextPolicy = policy.getJSONObject("visibleTextPolicy")
        assertEquals(
            "14:00 洗护档期空出来了，需要你确认是否调整 Kylin 的预约",
            visibleTextPolicy.getString("preferredUserVisibleSummary"),
        )
        assertTrue(visibleTextPolicy.getJSONArray("forbiddenVisibleFragments").toString().contains("客人计划有变"))
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
    fun visibleDecisionSystemEventsAreOwnedByPlannerSurface() {
        assertTrue(
            OneHourScenarioFlow.plannerOwnsVisibleDecisionSurface(
                sms(
                    id = "petsmart-open-slot",
                    source = "PetSmart",
                    body = "14:00 客人计划有变，现在可以安排 Kylin 洗澡和去浮毛。",
                ),
            ),
        )
        assertFalse(
            OneHourScenarioFlow.plannerOwnsVisibleDecisionSurface(
                sms(
                    id = "driver-1320-confirm",
                    source = "Driver",
                    body = "好的，我 13:20 到楼下等 Kylin。",
                ),
            ),
        )
    }

    @Test
    fun authorizedSystemTaskSurfacesAreOwnedByPlanner() {
        assertTrue(
            OneHourScenarioFlow.plannerOwnsSystemTaskSurface(
                sms(
                    id = "driver-1320-confirm",
                    source = "Driver",
                    body = "好的，我 13:20 到楼下等 Kylin。",
                ),
            ),
        )
        assertFalse(
            OneHourScenarioFlow.plannerOwnsSystemTaskSurface(
                sms(
                    id = "unknown-sms",
                    source = "Driver",
                    body = "OK",
                ),
            ),
        )
    }

    @Test
    fun callEndedTaskSurfaceIsPlannerOwnedForTranscriptNormalization() {
        assertTrue(
            OneHourScenarioFlow.plannerOwnsSystemTaskSurface(
                CallEndedEvent(
                    id = "ella-call-ended",
                    occurredAt = now.withHour(13).withMinute(11),
                    source = "Ella",
                    title = "Ella 通话结束",
                    body = "通话结束，音频可用于提取家庭采购待办。",
                    contact = "Ella",
                    audioRef = "runtime-call:ella-call",
                    callSessionId = "ella-call",
                    transcript = """
                        Ella：你方便的话，顺路买瓶低脂牛奶和洗衣液吧。
                        用户：不买不买。
                    """.trimIndent(),
                ),
            ),
        )
        assertTrue(
            OneHourScenarioFlow.plannerOwnsSystemTaskSurface(
                CallEndedEvent(
                    id = "ella-call-ended",
                    occurredAt = now.withHour(13).withMinute(11),
                    source = "Ella",
                    title = "Ella 通话结束",
                    body = "通话结束，转写可用于更新当前任务。",
                    contact = "Ella",
                    audioRef = "runtime-call:ella-call",
                    callSessionId = "ella-call",
                    transcript = """
                        Ella：家里低脂牛奶和洗衣液快没了，顺路帮忙买一下。
                        用户：我不想买。
                        Ella：好的，那这次先不买。
                    """.trimIndent(),
                ),
            ),
        )
        assertTrue(
            OneHourScenarioFlow.plannerOwnsSystemTaskSurface(
                CallEndedEvent(
                    id = "ella-call-ended",
                    occurredAt = now.withHour(13).withMinute(11),
                    source = "Ella",
                    title = "Ella 通话结束",
                    body = "通话结束，音频可用于提取家庭采购待办。",
                    contact = "Ella",
                    audioRef = "runtime-call:ella-call",
                    callSessionId = "ella-call",
                    transcript = "Ella：你方便的话，顺路买瓶低脂牛奶和洗衣液吧。",
                ),
            ),
        )
    }

    @Test
    fun healthSupplyTaskSurfaceStaysLocal() {
        val event = notification(
            id = "pharmacy-restock",
            title = "美团买药通知",
            body = "美团买药通知：家中常备的益生菌已补货，支持快速配送。",
        )
        val flow = OneHourScenarioFlow()
        val seed = flow.handle(event).single() as OneHourFlowEffect.CreateTask

        assertFalse(OneHourScenarioFlow.plannerOwnsSystemTaskSurface(event))
        assertTrue(OneHourScenarioFlow.commandAuthorizationForEvent(event).taskIds.isEmpty())
        assertEquals("健康补给", seed.seed.title)
        assertTrue(seed.seed.conversations.single().text.contains("常买的益生菌补货了"))
        assertFalse(seed.seed.conversations.single().text.contains("美团买药通知"))
    }

    @Test
    fun coldchainTaskSurfaceStaysLocal() {
        val event = notification(
            id = "courier-coldchain-arriving",
            title = "冷链即将到达",
            body = "顺丰冷链通知包裹即将送达，需尽快处理。",
        )
        val flow = OneHourScenarioFlow()
        val seed = flow.handle(event).single() as OneHourFlowEffect.CreateTask

        assertFalse(OneHourScenarioFlow.plannerOwnsSystemTaskSurface(event))
        assertTrue(OneHourScenarioFlow.commandAuthorizationForEvent(event).taskIds.isEmpty())
        assertEquals("冷链收货", seed.seed.title)
        assertTrue(seed.seed.conversations.single().text.contains("顺丰冷链预计 13:45 到小区"))
        assertFalse(seed.seed.conversations.single().text.contains("顺丰冷链通知"))
    }

    @Test
    fun petServiceTimingTaskSurfaceStaysLocal() {
        val started = sms(
            id = "petsmart-service-started",
            source = "PetSmart",
            body = "PetSmart 发来 Kylin 到店和开洗确认。",
        )
        val progress = sms(
            id = "petsmart-service-progress",
            source = "PetSmart",
            body = "PetSmart 发来洗护进度更新。",
        )

        assertFalse(OneHourScenarioFlow.plannerOwnsSystemTaskSurface(started))
        assertFalse(OneHourScenarioFlow.plannerOwnsSystemTaskSurface(progress))
        assertTrue(OneHourScenarioFlow.commandAuthorizationForEvent(started).taskIds.isEmpty())
        assertTrue(OneHourScenarioFlow.commandAuthorizationForEvent(progress).taskIds.isEmpty())
    }

    @Test
    fun driverConfirmationPlannerPolicyRequiresPlannerWrittenReminderUpdate() {
        val policy = JSONObject(
            OneHourScenarioFlow.plannerPolicyJson(
                sms(
                    id = "driver-1320-confirm",
                    source = "Driver",
                    body = "OK，13:20 到楼下。",
                ),
            ),
        )
        val protocol = policy.getJSONObject("petDriverConfirmationProtocol")

        assertEquals("2027-04-25T13:20:00", protocol.getString("reminderScheduledFor"))
        assertTrue(protocol.getJSONArray("requiredCommandOrder").toString().contains("create_reminder"))
        assertTrue(protocol.getJSONArray("rules").toString().contains("Do not copy deterministic reference wording"))
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
        assertTrue(policy.getJSONArray("rules").toString().contains("include one concise AGENT conversation or summary"))
        assertTrue(policy.getJSONArray("rules").toString().contains("never expose eventFact.title/body verbatim"))
        assertTrue(policy.getJSONArray("rules").toString().contains("visibleTextPolicy"))
        val visibleTextPolicy = policy.getJSONObject("visibleTextPolicy")
        assertEquals("根据 Ella 电话整理家庭采购状态", visibleTextPolicy.getString("preferredUserVisibleSummary"))
        assertEquals(
            "通话中已确认暂不采购",
            visibleTextPolicy.getJSONObject("conditionalSummaries").getString("declined"),
        )
        val transcriptPolicy = policy.getJSONObject("familyShoppingCallTranscriptPolicy")
        assertTrue(transcriptPolicy.getBoolean("normalizationRequired"))
        assertEquals("purchaseDisposition", transcriptPolicy.getString("normalizeField"))
        assertTrue(transcriptPolicy.getString("declinedMeaning").contains("do not want to buy"))
        val declinedCommand = transcriptPolicy
            .getJSONObject("requiredCommandsByDisposition")
            .getJSONObject("declined")
        assertEquals("DONE", declinedCommand.getString("status"))
        assertEquals("omit", declinedCommand.getString("decision"))
        assertTrue(declinedCommand.getJSONArray("forbidden").toString().contains("BLOCKED"))
        assertTrue(visibleTextPolicy.getJSONArray("forbiddenVisibleFragments").toString().contains("音频可用于提取"))
        assertTrue(policy.getJSONArray("rules").toString().contains("PetSmart 来信"))
        assertTrue(policy.getJSONArray("rules").toString().contains("Ella 通话结束"))
        assertTrue(policy.getJSONArray("rules").toString().contains("familyShoppingCallTranscriptPolicy"))
        assertFalse(policy.toString().contains("ella-call-ended"))
    }

    @Test
    fun runtimeCallEndedPlannerPolicyUsesEllaShoppingContext() {
        val event = CallEndedEvent(
            id = "ella-call-ended",
            occurredAt = now.withHour(13).withMinute(11),
            source = "Ella",
            title = "Ella 通话结束",
            body = "通话结束，音频可用于提取家庭采购待办。",
            contact = "Ella",
            audioRef = "runtime-call:ella-call",
            callSessionId = "ella-call",
        )
        val policy = JSONObject(OneHourScenarioFlow.plannerPolicyJson(event))

        assertTrue(policy.getString("currentObservedContext").contains("低脂牛奶"))
        assertTrue(policy.getString("currentObservedContext").contains("常用洗衣液"))
    }

    @Test
    fun callEndedWithRuntimeTranscriptCreatesTaskFromRuntimeTranscript() {
        val transcriptText = "Ella：麻烦只买低脂牛奶，猫粮不用买，明天上午送也行。"
        val effects = OneHourScenarioFlow().handle(
            CallEndedEvent(
                id = "ella-call-ended",
                occurredAt = now.withHour(13).withMinute(11),
                source = "Ella",
                title = "Ella 通话结束",
                body = "通话结束，音频可用于提取家庭采购待办。",
                contact = "Ella",
                audioRef = "runtime-call:ella-call",
                callSessionId = "ella-call",
                transcript = transcriptText,
            ),
        )
        val familyTask = effects.single { it is OneHourFlowEffect.CreateTask } as OneHourFlowEffect.CreateTask
        val conversationText = familyTask.seed.conversations.joinToString(" ") { it.text }
        val logText = familyTask.seed.logs.joinToString(" ") { it.text }

        assertTrue(conversationText.contains("家庭采购任务"))
        assertFalse(conversationText.contains(transcriptText))
        assertFalse(conversationText.contains("通话转写"))
        assertTrue(logText.contains("低脂牛奶"))
        assertTrue(logText.contains("猫粮"))
        assertTrue(logText.contains("明天上午"))
        assertFalse(conversationText.contains("常用洗衣液"))
        assertFalse(logText.contains("常用洗衣液"))
    }

    @Test
    fun callEndedDoesNotExposeRoleCallTranscriptAsConversationBubble() {
        val transcriptText = """
            Ella：家里要补点东西，低脂牛奶和洗衣液先买，水果顺路再买。
            用户：你在说什么？
            Ella：我刚才说要买低脂牛奶和洗衣液，水果顺路再买。
            用户：我身上钱不够。
            Ella：那我先买牛奶和洗衣液，水果下次再买。
            用户：你买还是我买啊？
            Ella：我买，你别买了。
            用户：好，拜拜。
            Ella：好的，拜拜！
        """.trimIndent()
        val effects = OneHourScenarioFlow().handle(
            CallEndedEvent(
                id = "ella-call-ended",
                occurredAt = now.withHour(13).withMinute(11),
                source = "Ella",
                title = "Ella 通话结束",
                body = "通话结束，音频可用于提取家庭采购待办。",
                contact = "Ella",
                audioRef = "runtime-call:ella-call",
                callSessionId = "ella-call",
                transcript = transcriptText,
            ),
        )
        val familyTask = effects.single { it is OneHourFlowEffect.CreateTask } as OneHourFlowEffect.CreateTask
        val conversationText = familyTask.seed.conversations.joinToString(" ") { it.text }
        val logText = familyTask.seed.logs.joinToString(" ") { it.text }

        assertTrue(conversationText.contains("家庭采购任务"))
        assertFalse(conversationText.contains("通话转写"))
        assertFalse(conversationText.contains("用户："))
        assertFalse(conversationText.contains("Ella："))
        assertFalse(conversationText.contains("我身上钱不够"))
        assertTrue(logText.contains("低脂牛奶"))
        assertTrue(logText.contains("常用洗衣液"))
    }

    @Test
    fun callEndedWithExplicitPurchaseRefusalHasLocalFallbackButPlannerStillOwnsSurface() {
        val flow = OneHourScenarioFlow()
        val transcriptText = """
            Ella：你方便的话，顺路买瓶低脂牛奶和洗衣液吧。
            用户：不买不买。
            Ella：那不买了，辛苦你啦。
        """.trimIndent()
        val event = CallEndedEvent(
            id = "ella-call-ended",
            occurredAt = now.withHour(13).withMinute(11),
            source = "Ella",
            title = "Ella 通话结束",
            body = "通话结束，音频可用于提取家庭采购待办。",
            contact = "Ella",
            audioRef = "runtime-call:ella-call",
            callSessionId = "ella-call",
            transcript = transcriptText,
        )
        val effects = flow.handle(event)
        val familyTask = effects.single { it is OneHourFlowEffect.CreateTask } as OneHourFlowEffect.CreateTask

        assertTrue(OneHourScenarioFlow.plannerOwnsSystemTaskSurface(event))
        assertEquals(ScenarioSurfaceStatus.DONE, familyTask.seed.status)
        assertEquals("通话中已确认暂不采购", familyTask.seed.subtitle)
        assertTrue(familyTask.seed.conversations.single().text.contains("不下单"))
        assertEquals(null, familyTask.seed.decision)
        assertTrue(
            flow.shouldSuppressPlannerForEvent(
                sms("ella-shopping-followup", "Ella", "牛奶和洗衣液优先，水果顺路再买。"),
            ),
        )
        assertTrue(
            flow.shouldSuppressPlannerForEvent(
                notification("market-delivery-window", "超市配送窗口", "低脂牛奶和洗衣液 45 分钟内可送达。"),
            ),
        )
        assertTrue(
            flow.handle(sms("ella-shopping-followup", "Ella", "牛奶和洗衣液优先，水果顺路再买。")).isEmpty(),
        )
        assertTrue(
            flow.handle(notification("market-delivery-window", "超市配送窗口", "低脂牛奶和洗衣液 45 分钟内可送达。")).isEmpty(),
        )
        assertTrue(
            flow.handle(notification("market-order-locked", "超市订单锁定", "低脂牛奶和洗衣液已锁定。")).isEmpty(),
        )
    }

    @Test
    fun callEndedSemanticPurchaseRefusalIsHandledByPlannerNormalization() {
        val flow = OneHourScenarioFlow()
        val event = CallEndedEvent(
            id = "ella-call-ended",
            occurredAt = now.withHour(13).withMinute(11),
            source = "Ella",
            title = "Ella 通话结束",
            body = "通话结束，音频可用于提取家庭采购待办。",
            contact = "Ella",
            audioRef = "runtime-call:ella-call",
            callSessionId = "ella-call",
            transcript = """
                Ella：你方便的话，顺路买瓶低脂牛奶和洗衣液吧。
                用户：我不想买。
                Ella：那不买了，辛苦你啦。
            """.trimIndent(),
        )

        assertTrue(OneHourScenarioFlow.plannerOwnsSystemTaskSurface(event))
        val policy = JSONObject(OneHourScenarioFlow.plannerPolicyJson(event))
        val declinedCommand = policy
            .getJSONObject("familyShoppingCallTranscriptPolicy")
            .getJSONObject("requiredCommandsByDisposition")
            .getJSONObject("declined")

        assertEquals("DONE", declinedCommand.getString("status"))
        assertTrue(
            policy.getJSONObject("familyShoppingCallTranscriptPolicy")
                .getJSONArray("rules")
                .toString()
                .contains("does not want to buy"),
        )

        flow.handle(event)
        flow.updateRuntimeStateFromPlannerCommands(
            listOf(
                ScenarioAgentCommand.UpdateTask(FamilyShoppingTaskSurface.purchaseSkipped("我不想买")),
            ),
        )

        assertTrue(
            flow.shouldSuppressPlannerForEvent(
                sms("ella-shopping-followup", "Ella", "牛奶和洗衣液优先，水果顺路再买。"),
            ),
        )
        assertTrue(
            flow.handle(notification("market-delivery-window", "超市配送窗口", "低脂牛奶和洗衣液 45 分钟内可送达。"))
                .isEmpty(),
        )
    }

    @Test
    fun callEndedPlannerPolicyUsesRuntimeTranscriptWhenProvided() {
        val event = CallEndedEvent(
            id = "ella-call-ended",
            occurredAt = now.withHour(13).withMinute(11),
            source = "Ella",
            title = "Ella 通话结束",
            body = "通话结束，音频可用于提取家庭采购待办。",
            contact = "Ella",
            audioRef = "runtime-call:ella-call",
            callSessionId = "ella-call",
            transcript = "Ella：帮我买鸡蛋和咖啡，今晚送。",
        )
        val context = JSONObject(OneHourScenarioFlow.plannerPolicyJson(event))
            .getString("currentObservedContext")

        assertTrue(context.contains("鸡蛋"))
        assertTrue(context.contains("咖啡"))
        assertTrue(context.contains("今晚"))
        assertFalse(context.contains("常用洗衣液"))
    }

    @Test
    fun ellaRoleCallReplyRejectsOffTopicOpening() {
        assertFalse(
            OneHourScenarioPolicy.isValidRoleCallReply(
                "嗨，最近怎么样？有空一起吃饭吗？",
                openingTurn = true,
            ),
        )
        assertFalse(
            OneHourScenarioPolicy.isValidRoleCallReply(
                "你方便的话，顺路买瓶低脂牛奶和洗衣液吧。",
                openingTurn = true,
            ),
        )
        assertFalse(
            OneHourScenarioPolicy.isValidRoleCallReply(
                "我刚想起来家里牛奶快没了，顺路的话帮补一下。",
                openingTurn = true,
            ),
        )
        assertTrue(
            OneHourScenarioPolicy.isValidRoleCallReply(
                "喂，下午能帮家里买点低脂牛奶吗？",
                openingTurn = true,
            ),
        )
    }

    @Test
    fun ellaRoleCallInstructionDefinesSpouseRequesterBoundary() {
        val instruction = OneHourScenarioPolicy.roleCallInstruction()

        assertTrue(instruction.contains("Ella 是用户的妻子"))
        assertTrue(instruction.contains("不是这次采购的执行方"))
        assertTrue(instruction.contains("用户或用户的 AIOS 帮家里安排或顺路买东西"))
        assertTrue(instruction.contains("直接接受取消"))
        assertTrue(instruction.contains("我先去买"))
        assertTrue(instruction.contains("把执行责任放到 Ella 身上"))
        assertTrue(instruction.contains("不要照抄固定模板"))
        assertTrue(instruction.contains("麻烦你啦"))
    }

    @Test
    fun roleCallUserTranscriptDropsLeakedContextSuffixAfterRefusal() {
        assertEquals(
            "不买不买，先不买，不用买，不要买。",
            OneHourScenarioPolicy.normalizeRoleCallUserTranscript(
                "不买不买，先不买，不用买，不要买，低脂牛奶、洗衣液、Kylin、PetSmart。",
            ),
        )
        assertEquals(
            "低脂牛奶不要买，洗衣液可以买",
            OneHourScenarioPolicy.normalizeRoleCallUserTranscript("低脂牛奶不要买，洗衣液可以买"),
        )
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
    fun petRouteReferenceUpdatesDoNotOwnAgentConversationText() {
        val flow = OneHourScenarioFlow()
        flow.acceptPetCareSlot("可以")

        val updates = listOf(
            sms("driver-1320-confirm", "Driver", "OK，13:20 到楼下。"),
            sms("driver-kylin-picked-up", "Driver", "Kylin 已上车。"),
            sms("driver-arrived-petsmart", "Driver", "已到 PetSmart。"),
        ).map { event ->
            flow.handle(event).single { it is OneHourFlowEffect.UpdateTask } as OneHourFlowEffect.UpdateTask
        }

        assertTrue(updates.all { it.update.conversations.isEmpty() })
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
    fun petGroomingProgressUsesRealisticDurationWindow() {
        val flow = OneHourScenarioFlow()
        flow.acceptPetCareSlot("可以")

        val started = flow.handle(
            sms(
                id = "petsmart-service-started",
                source = "PetSmart",
                body = "PetSmart 发来 Kylin 到店和开洗确认。",
            ),
        ).single() as OneHourFlowEffect.UpdateTask
        val progress = flow.handle(
            sms(
                id = "petsmart-service-progress",
                source = "PetSmart",
                body = "PetSmart 发来洗护进度更新。",
            ),
        ).single() as OneHourFlowEffect.UpdateTask

        val startedText = started.update.conversations.joinToString(" ") { it.text }
        val progressText = progress.update.conversations.joinToString(" ") { it.text }
        val petEventBodies = scriptEvents()
            .filter { it.getString("id") in setOf("petsmart-service-started", "petsmart-service-progress") }
            .joinToString(" ") { it.getString("body") }

        assertTrue(startedText.contains("16:00"))
        assertFalse(startedText.contains("14:45"))
        assertEquals("预计 16:15 左右完成", progress.update.subtitle)
        assertTrue(progressText.contains("16:15"))
        assertFalse(progressText.contains("14:30"))
        assertFalse(progressText.contains("15:00"))
        assertFalse(petEventBodies.contains("16:00"))
        assertFalse(petEventBodies.contains("16:15"))
        assertFalse(petEventBodies.contains("14:45"))
        assertFalse(petEventBodies.contains("14:30"))
        assertFalse(petEventBodies.contains("15:00"))
    }

    @Test
    fun petGroomingExpediteIntentChangesLaterProgressThroughStructuredAction() {
        val flow = OneHourScenarioFlow()
        flow.acceptPetCareSlot("可以")

        val commands = flow.userIntentCommands(
            taskId = "pet-grooming-live",
            intentId = PetGroomingTaskSurface.PURPOSE_EXPEDITE_SERVICE,
            userText = "让宠物店洗快点",
        ) ?: error("Expected expedite commands")
        val sms = commands.single { it is ScenarioAgentCommand.SendSms } as ScenarioAgentCommand.SendSms

        assertEquals("PetSmart", sms.to)
        assertEquals(PetGroomingTaskSurface.PURPOSE_EXPEDITE_SERVICE, sms.semanticPurpose)

        flow.updateRuntimeStateFromPlannerCommands(commands)
        assertTrue(flow.isPetCareExpediteRequested())

        val progress = flow.handle(
            sms(
                id = "petsmart-service-progress",
                source = "PetSmart",
                body = "PetSmart 发来洗护进度更新。",
            ),
        ).single() as OneHourFlowEffect.UpdateTask
        val progressText = progress.update.conversations.joinToString(" ") { it.text }

        assertEquals("预计 16:00 左右完成", progress.update.subtitle)
        assertTrue(progressText.contains("16:00"))
        assertFalse(progressText.contains("16:05"))
        assertFalse(progressText.contains("16:15"))

        val status = flow.userTurnCommands(
            taskId = PetGroomingTaskSurface.TASK_ID,
            userText = "status",
        )?.single() as ScenarioAgentCommand.UpdateTask
        val statusText = status.update.conversations.joinToString(" ") { it.text }

        assertTrue(statusText.contains("16:00"))
        assertFalse(statusText.contains("16:05"))
        assertFalse(statusText.contains("16:15"))
    }

    @Test
    fun petGroomingRescheduleFreeformStopsCurrentRuntimeFollowups() {
        val flow = OneHourScenarioFlow()
        flow.acceptPetCareSlot("可以")

        val commands = flow.userTurnCommands(
            taskId = PetGroomingTaskSurface.TASK_ID,
            userText = "这周和下下周没空，改下下下周",
        ) ?: error("Expected reschedule commands")
        val update = commands.first() as ScenarioAgentCommand.UpdateTask
        val petsmartSms = commands
            .filterIsInstance<ScenarioAgentCommand.SendSms>()
            .single { it.to == "PetSmart" }

        assertEquals(ScenarioSurfaceStatus.DONE, update.update.status)
        assertEquals(PetGroomingTaskSurface.PURPOSE_RESCHEDULE_SERVICE, petsmartSms.semanticPurpose)
        assertTrue(petsmartSms.message.contains("下下下周"))
        assertTrue(petsmartSms.message.contains("这周"))
        assertTrue(petsmartSms.message.contains("下下周"))
        assertFalse(flow.isPetCareAccepted())
        assertTrue(
            flow.handle(
                sms(
                    id = "driver-kylin-picked-up",
                    source = "Driver",
                    body = "老陈发来 Kylin 已上车确认。",
                ),
            ).isEmpty(),
        )
    }

    @Test
    fun petGroomingLateRescheduleCreatesConflictWithoutSms() {
        val flow = OneHourScenarioFlow()
        flow.acceptPetCareSlot("可以")
        flow.handle(
            sms(
                id = "petsmart-service-started",
                source = "PetSmart",
                body = "PetSmart 发来 Kylin 到店和开洗确认。",
            ),
        )

        val commands = flow.userTurnCommands(
            taskId = PetGroomingTaskSurface.TASK_ID,
            userText = "改成下周吧",
        ) ?: error("Expected conflict command")
        val update = commands.single() as ScenarioAgentCommand.UpdateTask
        val text = update.update.conversations.joinToString(" ") { it.text }

        assertTrue(flow.isPetCareAccepted())
        assertEquals(ScenarioSurfaceStatus.RUNNING, update.update.status)
        assertTrue(text.contains("不能直接"))
        assertTrue(commands.none { it is ScenarioAgentCommand.SendSms })
    }

    @Test
    fun petGroomingStatusQuestionUsesScenarioState() {
        val flow = OneHourScenarioFlow()
        flow.acceptPetCareSlot("可以")
        flow.handle(
            sms(
                id = "petsmart-service-started",
                source = "PetSmart",
                body = "PetSmart 发来 Kylin 到店和开洗确认。",
            ),
        )

        val commands = flow.userTurnCommands(
            taskId = PetGroomingTaskSurface.TASK_ID,
            userText = "status",
        ) ?: error("Expected status command")
        val update = commands.single() as ScenarioAgentCommand.UpdateTask
        val text = update.update.conversations.joinToString(" ") { it.text }

        assertEquals(ScenarioSurfaceStatus.RUNNING, update.update.status)
        assertTrue(text.contains("PetSmart"))
        assertTrue(text.contains("16:00"))
        assertTrue(commands.none { it is ScenarioAgentCommand.SendSms })
    }

    @Test
    fun petGroomingAmbiguousRescheduleNeedsClarificationWithoutSms() {
        val flow = OneHourScenarioFlow()
        flow.acceptPetCareSlot("可以")

        val commands = flow.userTurnCommands(
            taskId = PetGroomingTaskSurface.TASK_ID,
            userText = "change time",
        ) ?: error("Expected clarification command")
        val update = commands.single() as ScenarioAgentCommand.UpdateTask

        assertEquals(ScenarioSurfaceStatus.BLOCKED, update.update.status)
        assertTrue(update.update.conversations.joinToString(" ") { it.text }.contains("明确目标时间"))
        assertTrue(commands.none { it is ScenarioAgentCommand.SendSms })
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
        assertEquals("ella-call", callLayer.callSessionId)
        assertEquals("ella", callLayer.personaId)
        assertTrue(callLayer.callTranscriptText.isNullOrBlank())
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
    fun marketDeliveryCandidateRequiresPurchaseConfirmation() {
        val flow = startFamilyShoppingFlow()

        val update = flow.handle(
            notification("market-delivery-window", "超市配送窗口", "低脂牛奶和洗衣液 45 分钟内可送达。"),
        ).single() as OneHourFlowEffect.UpdateTask

        assertEquals(ScenarioSurfaceStatus.BLOCKED, update.update.status)
        assertEquals("等待购买确认", update.update.progress.detail)
        assertEquals(
            listOf("买这两样", "先不买"),
            update.update.decision?.actions?.map { it.label },
        )
        assertTrue(update.update.conversations.joinToString(" ") { it.text }.contains("要我现在锁定"))
    }

    @Test
    fun marketDeliveryPlannerPolicyIncludesVisiblePurchaseDecision() {
        val policy = JSONObject(
            OneHourScenarioFlow.plannerPolicyJson(
                notification("market-delivery-window", "超市配送窗口", "低脂牛奶和洗衣液 45 分钟内可送达。"),
            ),
        )

        assertTrue(policy.getJSONObject("decisionPolicy").getBoolean("visibleDecisionActionsRequired"))
        assertEquals("买这两样", policy.getJSONArray("visibleDecisionActions").getJSONObject(0).getString("label"))
        assertEquals("先不买", policy.getJSONArray("visibleDecisionActions").getJSONObject(1).getString("label"))
        assertEquals(0, policy.getJSONArray("authorizedSms").length())
    }

    @Test
    fun familyShoppingConfirmPurchaseFreeformWaitsForOrderLock() {
        val flow = startFamilyShoppingFlow()
        flow.handle(notification("market-delivery-window", "超市配送窗口", "低脂牛奶和洗衣液 45 分钟内可送达。"))

        val commands = flow.userTurnCommands(
            taskId = FamilyShoppingTaskSurface.TASK_ID,
            userText = "买吧",
        ) ?: error("Expected purchase confirmation commands")
        flow.updateRuntimeStateFromPlannerCommands(commands)
        val update = commands.first() as ScenarioAgentCommand.UpdateTask
        val oleSms = commands.filterIsInstance<ScenarioAgentCommand.SendSms>().single()

        assertEquals(ScenarioSurfaceStatus.RUNNING, update.update.status)
        assertEquals("Ole", oleSms.to)
        assertEquals(FamilyShoppingTaskSurface.PURPOSE_CONFIRM_PURCHASE, oleSms.semanticPurpose)
        assertTrue(commands.any { it is ScenarioAgentCommand.WaitSms && it.contact == "Ole" })

        val locked = flow.handle(
            notification("market-order-locked", "超市订单锁定", "低脂牛奶和洗衣液已锁定，预计 14:12 送达。"),
        ).single() as OneHourFlowEffect.UpdateTask

        assertEquals(ScenarioSurfaceStatus.DONE, locked.update.status)
        assertTrue(locked.update.conversations.joinToString(" ") { it.text }.contains("已锁定"))
    }

    @Test
    fun familyShoppingConfirmPurchaseSurvivesLaterListClarification() {
        val flow = startFamilyShoppingFlow()
        flow.handle(notification("market-delivery-window", "超市配送窗口", "低脂牛奶和洗衣液 45 分钟内可送达。"))

        val commands = flow.userTurnCommands(
            taskId = FamilyShoppingTaskSurface.TASK_ID,
            userText = "买这两样",
        ) ?: error("Expected purchase confirmation commands")
        flow.updateRuntimeStateFromPlannerCommands(commands)

        val clarify = flow.handle(
            sms("ella-shopping-clarify", "Ella", "洗衣液买常用那款就行，猫粮不用买。"),
        ).single() as OneHourFlowEffect.UpdateTask
        val locked = flow.handle(
            notification("market-order-locked", "超市订单锁定", "低脂牛奶和洗衣液已锁定，预计 14:12 送达。"),
        ).single() as OneHourFlowEffect.UpdateTask

        assertEquals("采购清单已收敛", clarify.update.subtitle)
        assertEquals(ScenarioSurfaceStatus.DONE, locked.update.status)
        assertTrue(locked.update.conversations.joinToString(" ") { it.text }.contains("预计 14:12"))
    }

    @Test
    fun familyShoppingUnconfirmedOrderLockBecomesInventoryHold() {
        val flow = startFamilyShoppingFlow()
        flow.handle(notification("market-delivery-window", "超市配送窗口", "低脂牛奶和洗衣液 45 分钟内可送达。"))

        val held = flow.handle(
            notification("market-order-locked", "超市订单锁定", "低脂牛奶和洗衣液已锁定。"),
        ).single() as OneHourFlowEffect.UpdateTask
        val text = held.update.conversations.joinToString(" ") { it.text }

        assertEquals(ScenarioSurfaceStatus.BLOCKED, held.update.status)
        assertTrue(text.contains("不会自动付款"))
        assertEquals(
            listOf("买这两样", "先不买"),
            held.update.decision?.actions?.map { it.label },
        )
    }

    @Test
    fun familyShoppingHoldConfirmationStaysCompletedAfterRuntimeSync() {
        val flow = startFamilyShoppingFlow()
        flow.handle(notification("market-delivery-window", "超市配送窗口", "低脂牛奶和洗衣液 45 分钟内可送达。"))
        flow.handle(notification("market-order-locked", "超市订单锁定", "低脂牛奶和洗衣液已锁定。"))

        val commands = flow.userTurnCommands(
            taskId = FamilyShoppingTaskSurface.TASK_ID,
            userText = "买这两样",
        ) ?: error("Expected held purchase confirmation commands")
        flow.updateRuntimeStateFromPlannerCommands(commands)
        val update = commands.first() as ScenarioAgentCommand.UpdateTask
        val status = flow.userTurnCommands(
            taskId = FamilyShoppingTaskSurface.TASK_ID,
            userText = "状态",
        )?.single() as ScenarioAgentCommand.UpdateTask

        assertEquals(ScenarioSurfaceStatus.DONE, update.update.status)
        assertTrue(commands.none { it is ScenarioAgentCommand.WaitSms })
        assertEquals(ScenarioSurfaceStatus.DONE, status.update.status)
        assertTrue(status.update.conversations.joinToString(" ") { it.text }.contains("家庭采购已下单"))
    }

    @Test
    fun familyShoppingRemoveFruitKeepsPurchaseDecision() {
        val flow = startFamilyShoppingFlow()
        flow.handle(notification("market-delivery-window", "超市配送窗口", "低脂牛奶和洗衣液 45 分钟内可送达。"))

        val update = flow.userTurnCommands(
            taskId = FamilyShoppingTaskSurface.TASK_ID,
            userText = "不要水果",
        )?.single() as ScenarioAgentCommand.UpdateTask
        val text = update.update.conversations.joinToString(" ") { it.text }

        assertEquals(ScenarioSurfaceStatus.RUNNING, update.update.status)
        assertTrue(text.contains("移除 水果"))
        assertTrue(text.contains("低脂牛奶、常用洗衣液"))
        assertEquals(
            listOf("买这两样", "先不买"),
            update.update.decision?.actions?.map { it.label },
        )
    }

    @Test
    fun familyShoppingDeliveryWindowFreeformSendsStructuredRequest() {
        val flow = startFamilyShoppingFlow()
        flow.handle(notification("market-delivery-window", "超市配送窗口", "低脂牛奶和洗衣液 45 分钟内可送达。"))

        val commands = flow.userTurnCommands(
            taskId = FamilyShoppingTaskSurface.TASK_ID,
            userText = "14:30 后送",
        ) ?: error("Expected delivery window commands")
        flow.updateRuntimeStateFromPlannerCommands(commands)
        val update = commands.first() as ScenarioAgentCommand.UpdateTask
        val oleSms = commands.filterIsInstance<ScenarioAgentCommand.SendSms>().single()
        val status = flow.userTurnCommands(
            taskId = FamilyShoppingTaskSurface.TASK_ID,
            userText = "状态",
        )?.single() as ScenarioAgentCommand.UpdateTask

        assertTrue(update.update.conversations.joinToString(" ") { it.text }.contains("14:30"))
        assertEquals(FamilyShoppingTaskSurface.PURPOSE_DELIVERY_WINDOW, oleSms.semanticPurpose)
        assertTrue(commands.any { it is ScenarioAgentCommand.WaitSms && it.contact == "Ole" })
        assertTrue(status.update.conversations.joinToString(" ") { it.text }.contains("配送窗口：14:30"))
    }

    @Test
    fun familyShoppingCancelSuppressesLaterOrderLock() {
        val flow = startFamilyShoppingFlow()
        flow.handle(notification("market-delivery-window", "超市配送窗口", "低脂牛奶和洗衣液 45 分钟内可送达。"))

        val commands = flow.userTurnCommands(
            taskId = FamilyShoppingTaskSurface.TASK_ID,
            userText = "先不买",
        ) ?: error("Expected purchase cancel commands")
        flow.updateRuntimeStateFromPlannerCommands(commands)
        val update = commands.first() as ScenarioAgentCommand.UpdateTask
        val oleSms = commands.filterIsInstance<ScenarioAgentCommand.SendSms>().single()

        assertEquals(ScenarioSurfaceStatus.DONE, update.update.status)
        assertEquals(FamilyShoppingTaskSurface.PURPOSE_CANCEL_PURCHASE, oleSms.semanticPurpose)
        assertTrue(
            flow.handle(
                notification("market-order-locked", "超市订单锁定", "低脂牛奶和洗衣液已锁定。"),
            ).isEmpty(),
        )
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

    private fun startFamilyShoppingFlow(): OneHourScenarioFlow =
        OneHourScenarioFlow().also { flow ->
            flow.handle(
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
