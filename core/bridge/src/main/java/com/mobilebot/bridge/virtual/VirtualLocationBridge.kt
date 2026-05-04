package com.mobilebot.bridge.virtual

import android.util.Log
import com.mobilebot.bridge.LocationBridge
import com.mobilebot.bridge.LocationResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VirtualLocationBridge
    @Inject
    constructor() : LocationBridge {
        override suspend fun getCoarseLocation(): LocationResult {
            Log.d(TAG, "[VIRTUAL] getCoarseLocation() -> Shanghai Bund")
            return LocationResult(
                latitude = 31.2304,
                longitude = 121.4737,
                error = null,
            )
        }

        override suspend fun getFineLocation(): LocationResult {
            Log.d(TAG, "[VIRTUAL] getFineLocation() -> Shanghai Bund")
            return LocationResult(
                latitude = 31.23039,
                longitude = 121.47370,
                error = null,
            )
        }

        private companion object {
            private const val TAG = "VirtualLocation"
        }
    }
