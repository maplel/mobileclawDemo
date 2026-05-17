package com.mobilebot.systemruntime

import java.time.LocalDateTime

data class SystemRuntimeScriptEvent(
    val id: String,
    val time: String,
    val type: String,
    val source: String,
    val title: String,
    val body: String,
    val scenarioId: String,
)

// 系统事件只描述已经发生的外部事实
sealed interface SystemRuntimeEvent {
    val id: String
    val occurredAt: LocalDateTime
    val source: String
    val title: String
    val body: String
}

data class IncomingSmsEvent(
    override val id: String,
    override val occurredAt: LocalDateTime,
    override val source: String,
    override val title: String,
    override val body: String,
    val from: String,
) : SystemRuntimeEvent

data class IncomingCallEvent(
    override val id: String,
    override val occurredAt: LocalDateTime,
    override val source: String,
    override val title: String,
    override val body: String,
    val contact: String,
) : SystemRuntimeEvent

data class CallEndedEvent(
    override val id: String,
    override val occurredAt: LocalDateTime,
    override val source: String,
    override val title: String,
    override val body: String,
    val contact: String,
    val audioRef: String,
) : SystemRuntimeEvent

data class ReminderFiredEvent(
    override val id: String,
    override val occurredAt: LocalDateTime,
    override val source: String,
    override val title: String,
    override val body: String,
    val reminderId: String,
) : SystemRuntimeEvent

data class AlarmFiredEvent(
    override val id: String,
    override val occurredAt: LocalDateTime,
    override val source: String,
    override val title: String,
    override val body: String,
    val alarmId: String,
) : SystemRuntimeEvent

data class RuntimeNotificationEvent(
    override val id: String,
    override val occurredAt: LocalDateTime,
    override val source: String,
    override val title: String,
    override val body: String,
) : SystemRuntimeEvent

data class DeviceStateEvent(
    override val id: String,
    override val occurredAt: LocalDateTime,
    override val source: String,
    override val title: String,
    override val body: String,
) : SystemRuntimeEvent

data class IncomingEmailEvent(
    override val id: String,
    override val occurredAt: LocalDateTime,
    override val source: String,
    override val title: String,
    override val body: String,
    val from: String,
    val subject: String,
) : SystemRuntimeEvent

data class EmailSentEvent(
    override val id: String,
    override val occurredAt: LocalDateTime,
    override val source: String,
    override val title: String,
    override val body: String,
    val to: String,
    val subject: String,
) : SystemRuntimeEvent

data class WebQueryResultEvent(
    override val id: String,
    override val occurredAt: LocalDateTime,
    override val source: String,
    override val title: String,
    override val body: String,
    val query: String,
    val url: String,
) : SystemRuntimeEvent
