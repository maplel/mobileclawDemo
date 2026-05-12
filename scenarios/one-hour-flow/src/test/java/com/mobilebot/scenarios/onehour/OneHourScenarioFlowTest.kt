package com.mobilebot.scenarios.onehour

import com.mobilebot.scenarios.runtime.ScenarioSurfaceStatus
import com.mobilebot.systemruntime.CallEndedEvent
import com.mobilebot.systemruntime.IncomingCallEvent
import com.mobilebot.systemruntime.IncomingSmsEvent
import com.mobilebot.systemruntime.ReminderFiredEvent
import com.mobilebot.systemruntime.RuntimeNotificationEvent
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertTrue(endedEffects.any { it is OneHourFlowEffect.ClearActiveCall })
        assertTrue(endedEffects.any { it is OneHourFlowEffect.ClearSystemLayer })
        assertEquals(
            "family-shopping-live",
            (endedEffects.single { it is OneHourFlowEffect.CreateTask } as OneHourFlowEffect.CreateTask).seed.taskId,
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
}
