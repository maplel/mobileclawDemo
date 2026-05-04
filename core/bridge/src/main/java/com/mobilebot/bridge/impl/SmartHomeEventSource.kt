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
 * Monitors smart home devices (leak sensors, smoke detectors, security cameras)
 * and emits alerts when issues are detected.
 */
@Singleton
class SmartHomeEventSource
    @Inject
    constructor() : EventSource {
        override val sourceId: String = "smart_home"

        private val channel = Channel<ExternalEvent>(Channel.BUFFERED)
        override val events: Flow<ExternalEvent> = channel.receiveAsFlow()

        private var running = false

        override fun start() {
            running = true
            Log.d(TAG, "SmartHomeEventSource started")
        }

        override fun stop() {
            running = false
            Log.d(TAG, "SmartHomeEventSource stopped")
        }

        fun simulateAlert(
            alertType: String,
            device: String,
            room: String,
        ) {
            if (!running) return
            val priority = when (alertType) {
                "smoke", "fire", "gas_leak" -> EventPriority.CRITICAL
                "water_leak", "intrusion" -> EventPriority.HIGH
                "door_open", "motion" -> EventPriority.NORMAL
                else -> EventPriority.LOW
            }
            channel.trySend(
                ExternalEvent(
                    type = "smart_home_alert",
                    source = sourceId,
                    payload = mapOf(
                        "alertType" to alertType,
                        "device" to device,
                        "room" to room,
                    ),
                    timestamp = System.currentTimeMillis(),
                    priority = priority,
                ),
            )
        }

        private companion object {
            private const val TAG = "SmartHomeEventSource"
        }
    }
