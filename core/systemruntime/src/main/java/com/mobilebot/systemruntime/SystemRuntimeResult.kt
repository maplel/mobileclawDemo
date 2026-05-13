package com.mobilebot.systemruntime

data class SystemRuntimeResult(
    val ok: Boolean,
    val message: String,
    val data: Map<String, Any?> = emptyMap(),
)
