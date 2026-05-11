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
 * Monitors location-based geofence triggers (arriving at airport,
 * entering school zone, reaching destination, etc.)
 */
@Singleton
class GeofenceEventSource
    @Inject
    constructor() : EventSource {
        override val sourceId: String = "geofence"

        private val channel = Channel<ExternalEvent>(Channel.BUFFERED)
        override val events: Flow<ExternalEvent> = channel.receiveAsFlow()

        private var running = false

        override fun start() {
            running = true
            Log.d(TAG, "GeofenceEventSource started")
        }

        override fun stop() {
            running = false
            Log.d(TAG, "GeofenceEventSource stopped")
        }

        fun simulateEnter(
            fenceId: String,
            locationType: String,
            name: String,
            latitude: Double,
            longitude: Double,
        ) {
            if (!running) return
            channel.trySend(
                ExternalEvent(
                    type = "geofence_enter",
                    source = sourceId,
                    payload = mapOf(
                        "fenceId" to fenceId,
                        "locationType" to locationType,
                        "name" to name,
                        "latitude" to latitude,
                        "longitude" to longitude,
                        "transition" to "enter",
                    ),
                    timestamp = System.currentTimeMillis(),
                    priority = EventPriority.NORMAL,
                ),
            )
        }

        fun simulateExit(fenceId: String, locationType: String) {
            if (!running) return
            channel.trySend(
                ExternalEvent(
                    type = "geofence_exit",
                    source = sourceId,
                    payload = mapOf(
                        "fenceId" to fenceId,
                        "locationType" to locationType,
                        "transition" to "exit",
                    ),
                    timestamp = System.currentTimeMillis(),
                    priority = EventPriority.LOW,
                ),
            )
        }

        private companion object {
            private const val TAG = "GeofenceEventSource"
        }
    }
