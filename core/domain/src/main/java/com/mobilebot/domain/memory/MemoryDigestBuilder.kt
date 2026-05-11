package com.mobilebot.domain.memory

/**
 * Builds freshness warnings and memory manifests for system prompt injection.
 */
object MemoryDigestBuilder {

    /**
     * Human-readable age string. Models respond better to "47 days ago" than ISO timestamps.
     */
    fun ageString(mtimeMs: Long): String {
        val days = maxOf(0L, (System.currentTimeMillis() - mtimeMs) / 86_400_000)
        return when {
            days == 0L -> "today"
            days == 1L -> "yesterday"
            else -> "$days days ago"
        }
    }

    /**
     * Freshness warning for memories > 1 day old.
     * Empty string for fresh memories (noise reduction).
     */
    fun freshnessWarning(mtimeMs: Long): String {
        val days = maxOf(0L, (System.currentTimeMillis() - mtimeMs) / 86_400_000)
        if (days <= 1) return ""
        return "\n[WARNING: This memory is $days days old. " +
            "Memories are point-in-time observations, not live state — " +
            "claims about code behavior or file:line citations may be outdated. " +
            "Verify against current code before asserting as fact.]"
    }

    /**
     * Format memory headers as a text manifest for LLM relevance selection.
     * One line per file: [type] filename (timestamp): description
     */
    fun manifest(memories: List<HeadlessMemory>): String = memories.joinToString("\n") { mem ->
        val tag = mem.type?.let { "[${MemoryType.toLabel(it)}] " } ?: ""
        val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
            .format(java.util.Date(mem.mtimeMs))
        mem.description?.let {
            "- $tag${mem.filename} ($ts): $it"
        } ?: "- $tag${mem.filename} ($ts)"
    }
}
