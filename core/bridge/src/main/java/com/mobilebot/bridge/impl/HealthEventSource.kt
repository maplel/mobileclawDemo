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
 * Monitors health sensors (heart rate, blood oxygen, fall detection)
 * and emits alerts when anomalies are detected.
 */
@Singleton
class HealthEventSource
    @Inject
    constructor() : EventSource {
        override val sourceId: String = "health_monitor"

        private val channel = Channel<ExternalEvent>(Channel.BUFFERED)
        override val events: Flow<ExternalEvent> = channel.receiveAsFlow()

        private var running = false

        override fun start() {
            running = true
            Log.d(TAG, "HealthEventSource started")
        }

        override fun stop() {
            running = false
            Log.d(TAG, "HealthEventSource stopped")
        }

        fun simulateHeartRateAlert(bpm: Int) {
            if (!running) return
            val priority = when {
                bpm > 150 || bpm < 40 -> EventPriority.CRITICAL
                bpm > 120 || bpm < 50 -> EventPriority.HIGH
                else -> EventPriority.NORMAL
            }
            channel.trySend(
                ExternalEvent(
                    type = "health_alert",
                    source = sourceId,
                    payload = mapOf(
                        "metric" to "heart_rate",
                        "value" to bpm,
                        "unit" to "bpm",
                    ),
                    timestamp = System.currentTimeMillis(),
                    priority = priority,
                ),
            )
        }

        fun simulateFallDetected() {
            if (!running) return
            channel.trySend(
                ExternalEvent(
                    type = "fall_detected",
                    source = sourceId,
                    payload = mapOf(
                        "metric" to "fall_detection",
                        "severity" to "potential",
                    ),
                    timestamp = System.currentTimeMillis(),
                    priority = EventPriority.CRITICAL,
                ),
            )
        }

        private companion object {
            private const val TAG = "HealthEventSource"
        }
    }
