package com.mobilebot.domain.memory

/**
 * Full memory entry: scanned file content with metadata.
 */
data class MemoryEntry(
    val filePath: String,
    val filename: String,
    val mtimeMs: Long,
    val description: String?,
    val type: MemoryType?,
    val content: String,
)
