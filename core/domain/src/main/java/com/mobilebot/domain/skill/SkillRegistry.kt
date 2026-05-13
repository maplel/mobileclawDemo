package com.mobilebot.domain.skill

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central registry for all skills from three sources: Bundled, Cloud, User.
 * Higher-priority sources override lower-priority ones with the same id.
 */
@Singleton
class SkillRegistry @Inject constructor() {

    private val entries = ConcurrentHashMap<String, SkillEntry>()
    private val versionCounter = AtomicInteger(0)

    @Volatile
    private var cachedSnapshot: SkillSnapshot? = null

    fun register(entry: SkillEntry) {
        val existing = entries[entry.manifest.id]
        if (existing == null || entry.source.priority >= existing.source.priority) {
            entries[entry.manifest.id] = entry
            invalidateSnapshot()
        }
    }

    fun registerAll(newEntries: List<SkillEntry>) {
        newEntries.forEach { register(it) }
    }

    fun unregister(id: String): Boolean {
        val removed = entries.remove(id) != null
        if (removed) invalidateSnapshot()
        return removed
    }

    fun findById(id: String): SkillEntry? = entries[id]

    fun all(): List<SkillEntry> = entries.values.sortedBy { it.manifest.id }

    fun allEligible(): List<SkillEntry> = entries.values
        .filter { it.eligible }
        .sortedBy { it.manifest.id }

    fun allByCategory(category: String): List<SkillEntry> = entries.values
        .filter { it.manifest.category == category && it.eligible }
        .sortedBy { it.manifest.id }

    fun buildCatalogPrompt(): String {
        val eligible = allEligible()
        if (eligible.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("<available_skills>")

        val grouped = eligible.groupBy { it.manifest.category ?: "general" }
        for ((category, skills) in grouped.toSortedMap()) {
            for (entry in skills) {
                if (entry.manifest.disableModelInvocation) continue
                val m = entry.manifest
                sb.appendLine("  <skill category=\"$category\">")
                sb.appendLine("    <name>${m.id}</name>")
                sb.appendLine("    <description>${m.description}</description>")
                m.scenario?.let { scenario ->
                    sb.appendLine("    <scenario id=\"${scenario.scenarioId}\" displayMode=\"${scenario.displayMode.value}\">")
                    if (scenario.systemCapabilities.isNotEmpty()) {
                        sb.appendLine("      <system_capabilities>${scenario.systemCapabilities.joinToString(", ")}</system_capabilities>")
                    }
                    if (scenario.decisionPoints.isNotEmpty()) {
                        sb.appendLine("      <decision_points>${scenario.decisionPoints.joinToString(", ")}</decision_points>")
                    }
                    if (scenario.timelineHints.isNotEmpty()) {
                        sb.appendLine("      <timeline_hints>${scenario.timelineHints.joinToString(", ")}</timeline_hints>")
                    }
                    sb.appendLine("    </scenario>")
                }
                sb.appendLine("  </skill>")
            }
        }

        sb.appendLine("</available_skills>")
        return sb.toString()
    }

    fun snapshot(): SkillSnapshot {
        cachedSnapshot?.let { return it }

        val newSnapshot = SkillSnapshot(
            catalogPrompt = buildCatalogPrompt(),
            eligibleSkillIds = allEligible().map { it.manifest.id },
            version = versionCounter.incrementAndGet(),
        )
        cachedSnapshot = newSnapshot
        return newSnapshot
    }

    fun invalidateSnapshot() {
        cachedSnapshot = null
    }

    fun size(): Int = entries.size
}
