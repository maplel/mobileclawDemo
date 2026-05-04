package com.mobilebot.bridge.impl

import android.util.Log
import com.mobilebot.bridge.EventPriority
import com.mobilebot.bridge.EventSource
import com.mobilebot.bridge.ExternalEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors calendar events and emits reminders, schedule conflicts,
 * and upcoming appointment alerts.
 */
@Singleton
class CalendarEventSource
    @Inject
    constructor() : EventSource {
        override val sourceId: String = "google_calendar"

        private val channel = Channel<ExternalEvent>(Channel.BUFFERED)
        override val events: Flow<ExternalEvent> = channel.receiveAsFlow()

        private var running = false

        override fun start() {
            running = true
            Log.d(TAG, "CalendarEventSource started")
        }

        override fun stop() {
            running = false
            Log.d(TAG, "CalendarEventSource stopped")
        }

        fun simulateReminder(
            title: String,
            minutesBefore: Int,
            location: String? = null,
        ) {
            if (!running) return
            channel.trySend(
                ExternalEvent(
                    type = "calendar_reminder",
                    source = sourceId,
                    payload = buildMap {
                        put("title", title)
                        put("minutesBefore", minutesBefore)
                        if (location != null) put("location", location)
                    },
                    timestamp = System.currentTimeMillis(),
                    priority = if (minutesBefore <= 5) EventPriority.HIGH else EventPriority.NORMAL,
                ),
            )
        }

        private companion object {
            private const val TAG = "CalendarEventSource"
        }
    }
