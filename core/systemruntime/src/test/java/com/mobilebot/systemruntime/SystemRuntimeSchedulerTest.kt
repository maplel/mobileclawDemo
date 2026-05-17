package com.mobilebot.systemruntime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class SystemRuntimeSchedulerTest {
    @Test
    fun deliversDueEventsOnceInTimeOrder() {
        val scheduler = SystemRuntimeScheduler()
        val base = LocalDateTime.of(2027, 4, 25, 13, 0)
        scheduler.replaceScenarioEvents(
            "one_hour_aio",
            listOf(
                scheduled("one_hour_aio", "second", base.plusMinutes(10)),
                scheduled("one_hour_aio", "first", base.plusMinutes(5)),
            ),
        )

        assertTrue(scheduler.dueEvents("one_hour_aio", base.plusMinutes(4)).isEmpty())

        val first = scheduler.dueEvents("one_hour_aio", base.plusMinutes(5))
        assertEquals(listOf("first"), first.map { it.id })

        val remaining = scheduler.dueEvents("one_hour_aio", base.plusMinutes(20))
        assertEquals(listOf("second"), remaining.map { it.id })
        assertTrue(scheduler.dueEvents("one_hour_aio", base.plusMinutes(20)).isEmpty())
    }

    @Test
    fun heldEventsAreNotDeliveredUntilGateIsReleased() {
        val scheduler = SystemRuntimeScheduler()
        val triggerAt = LocalDateTime.of(2027, 4, 25, 13, 5)
        scheduler.replaceScenarioEvents(
            "one_hour_aio",
            listOf(scheduled("one_hour_aio", "driver-1320-confirm", triggerAt)),
        )

        assertTrue(
            scheduler.dueEvents(
                scenarioId = "one_hour_aio",
                now = triggerAt.plusMinutes(1),
                heldEventIds = setOf("driver-1320-confirm"),
            ).isEmpty(),
        )

        val released = scheduler.dueEvents("one_hour_aio", triggerAt.plusMinutes(1))
        assertEquals(listOf("driver-1320-confirm"), released.map { it.id })
    }

    @Test
    fun nextEventSkipsHeldEvents() {
        val scheduler = SystemRuntimeScheduler()
        val base = LocalDateTime.of(2027, 4, 25, 13, 0)
        scheduler.replaceScenarioEvents(
            "one_hour_aio",
            listOf(
                scheduled("one_hour_aio", "held", base.plusMinutes(5)),
                scheduled("one_hour_aio", "open", base.plusMinutes(7)),
            ),
        )

        val next = scheduler.nextEvent(
            scenarioId = "one_hour_aio",
            now = base,
            heldEventIds = setOf("held"),
        )

        assertEquals("open", next?.event?.id)
    }

    private fun scheduled(
        scenarioId: String,
        id: String,
        at: LocalDateTime,
    ): SystemRuntimeScheduledEvent =
        SystemRuntimeScheduledEvent(
            scenarioId = scenarioId,
            triggerAt = at,
            event = RuntimeNotificationEvent(
                id = id,
                occurredAt = at,
                source = "System",
                title = id,
                body = id,
            ),
        )
}
