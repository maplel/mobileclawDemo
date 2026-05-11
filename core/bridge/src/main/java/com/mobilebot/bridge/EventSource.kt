package com.mobilebot.bridge

import kotlinx.coroutines.flow.Flow

enum class EventPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL,
}

data class ExternalEvent(
    val type: String,
    val source: String,
    val payload: Map<String, Any>,
    val timestamp: Long,
    val priority: EventPriority,
)

interface EventSource {
    val sourceId: String
    fun start()
    fun stop()
    val events: Flow<ExternalEvent>
}
