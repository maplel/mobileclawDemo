package com.mobilebot.systemruntime

import java.time.LocalDateTime

data class SystemRuntimeScheduledEvent(
    val scenarioId: String,
    val triggerAt: LocalDateTime,
    val event: SystemRuntimeEvent,
)

class SystemRuntimeScheduler {
    private val scheduled = linkedMapOf<String, SystemRuntimeScheduledEvent>()
    private val delivered = linkedSetOf<String>()

    @Synchronized
    fun replaceScenarioEvents(
        scenarioId: String,
        events: List<SystemRuntimeScheduledEvent>,
    ) {
        scheduled.keys
            .filter { it.startsWith("$scenarioId:") }
            .toList()
            .forEach { scheduled.remove(it) }
        delivered.removeAll { it.startsWith("$scenarioId:") }
        events.forEach(::schedule)
    }

    @Synchronized
    fun schedule(event: SystemRuntimeScheduledEvent) {
        scheduled[keyFor(event)] = event
    }

    @Synchronized
    fun dueEvents(
        scenarioId: String,
        now: LocalDateTime,
        heldEventIds: Set<String> = emptySet(),
    ): List<SystemRuntimeEvent> {
        val due = scheduled.values
            .asSequence()
            .filter { it.scenarioId == scenarioId }
            .filter { keyFor(it) !in delivered }
            .filter { it.event.id !in heldEventIds }
            .filter { !it.triggerAt.isAfter(now) }
            .sortedBy { it.triggerAt }
            .toList()
        due.forEach { delivered += keyFor(it) }
        return due.map { it.event }
    }

    @Synchronized
    fun nextEvent(
        scenarioId: String,
        now: LocalDateTime,
        heldEventIds: Set<String> = emptySet(),
    ): SystemRuntimeScheduledEvent? =
        scheduled.values
            .asSequence()
            .filter { it.scenarioId == scenarioId }
            .filter { keyFor(it) !in delivered }
            .filter { it.event.id !in heldEventIds }
            .filter { it.triggerAt.isAfter(now) || it.triggerAt == now }
            .minByOrNull { it.triggerAt }

    @Synchronized
    fun clearScenario(scenarioId: String) {
        scheduled.keys
            .filter { it.startsWith("$scenarioId:") }
            .toList()
            .forEach { scheduled.remove(it) }
        delivered.removeAll { it.startsWith("$scenarioId:") }
    }

    private fun keyFor(event: SystemRuntimeScheduledEvent): String =
        "${event.scenarioId}:${event.event.id}"
}
