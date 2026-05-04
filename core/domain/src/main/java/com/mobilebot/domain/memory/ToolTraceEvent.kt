package com.mobilebot.domain.memory

data class ToolTraceEvent(
    val toolId: String,
    val args: String,
    val startedAtMs: Long,
    val finishedAtMs: Long?,
    val decision: String,
    val resultPreview: String?,
)
