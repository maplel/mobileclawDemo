package com.mobilebot.domain.skill

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks skill invocation and tool execution metrics for observability.
 */
@Singleton
class SkillObservability @Inject constructor() {

    private val skillInvocations = ConcurrentHashMap<String, AtomicLong>()
    private val skillSuccesses = ConcurrentHashMap<String, AtomicLong>()
    private val skillFailures = ConcurrentHashMap<String, AtomicLong>()
    private val skillLatencies = ConcurrentHashMap<String, MutableList<Long>>()

    fun recordSkillInvocation(skillId: String) {
        skillInvocations.getOrPut(skillId) { AtomicLong(0) }.incrementAndGet()
    }

    fun recordSkillSuccess(skillId: String, latencyMs: Long) {
        skillSuccesses.getOrPut(skillId) { AtomicLong(0) }.incrementAndGet()
        skillLatencies.getOrPut(skillId) { mutableListOf() }.let { list ->
            synchronized(list) { list.add(latencyMs) }
        }
    }

    fun recordSkillFailure(skillId: String) {
        skillFailures.getOrPut(skillId) { AtomicLong(0) }.incrementAndGet()
    }

    fun getStats(): List<SkillStats> {
        val allIds = (skillInvocations.keys + skillSuccesses.keys + skillFailures.keys).distinct()
        return allIds.map { id ->
            val invocations = skillInvocations[id]?.get() ?: 0
            val successes = skillSuccesses[id]?.get() ?: 0
            val failures = skillFailures[id]?.get() ?: 0
            val avgLatency = skillLatencies[id]?.let { list ->
                synchronized(list) { if (list.isNotEmpty()) list.average().toLong() else 0L }
            } ?: 0L

            SkillStats(
                skillId = id,
                totalInvocations = invocations,
                successes = successes,
                failures = failures,
                hitRate = if (invocations > 0) successes.toDouble() / invocations else 0.0,
                avgLatencyMs = avgLatency,
            )
        }.sortedByDescending { it.totalInvocations }
    }

    fun reset() {
        skillInvocations.clear()
        skillSuccesses.clear()
        skillFailures.clear()
        skillLatencies.clear()
    }
}

data class SkillStats(
    val skillId: String,
    val totalInvocations: Long,
    val successes: Long,
    val failures: Long,
    val hitRate: Double,
    val avgLatencyMs: Long,
)
