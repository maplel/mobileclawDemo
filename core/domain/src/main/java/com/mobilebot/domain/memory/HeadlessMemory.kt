package com.mobilebot.domain.memory

/**
 * Lightweight memory header for scanning — no full content.
 * Used for manifest generation and relevance selection.
 */
data class HeadlessMemory(
    val filename: String,
    val filePath: String,
    val mtimeMs: Long,
    val description: String?,
    val type: MemoryType?,
)
