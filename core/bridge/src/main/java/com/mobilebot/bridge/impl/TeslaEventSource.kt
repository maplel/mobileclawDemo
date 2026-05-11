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
 * Listens for Tesla vehicle collision events via BLE or Fleet API webhooks.
 * In production, this would maintain a BLE connection to the vehicle and
 * subscribe to collision/airbag signals. Currently exposes a [simulateCollision]
 * method for testing.
 */
@Singleton
class TeslaEventSource
    @Inject
    constructor() : EventSource {
        override val sourceId: String = "tesla_ble"

        private val channel = Channel<ExternalEvent>(Channel.BUFFERED)
        override val events: Flow<ExternalEvent> = channel.receiveAsFlow()

        private var running = false

        override fun start() {
            running = true
            Log.d(TAG, "TeslaEventSource started — listening for vehicle events")
        }

        override fun stop() {
            running = false
            Log.d(TAG, "TeslaEventSource stopped")
        }

        /**
         * Simulate a collision event for testing. In production, this would
         * be triggered by BLE data from the vehicle's crash sensors.
         */
        fun simulateCollision(
            latitude: Double = 45.7833,
            longitude: Double = -109.9500,
            impactSeverity: String = "moderate",
            airbagDeployed: Boolean = false,
            speedAtImpact: Int = 35,
        ) {
            if (!running) {
                Log.w(TAG, "TeslaEventSource not running, ignoring collision simulation")
                return
            }
            val event = ExternalEvent(
                type = "vehicle_collision",
                source = sourceId,
                payload = mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "impactSeverity" to impactSeverity,
                    "airbagDeployed" to airbagDeployed,
                    "speedAtImpact" to speedAtImpact,
                    "vehicleId" to "TESLA_MODEL_Y_001",
                    "timestamp" to System.currentTimeMillis(),
                ),
                timestamp = System.currentTimeMillis(),
                priority = if (airbagDeployed) EventPriority.CRITICAL else EventPriority.HIGH,
            )
            channel.trySend(event)
            Log.d(TAG, "Collision event emitted: severity=$impactSeverity, airbag=$airbagDeployed")
        }

        private companion object {
            private const val TAG = "TeslaEventSource"
        }
    }
