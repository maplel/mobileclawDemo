package com.mobilebot.bridge.impl

import com.mobilebot.bridge.LocationBridge
import com.mobilebot.bridge.LocationResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StubLocationBridge
    @Inject
    constructor() : LocationBridge {
        override suspend fun getCoarseLocation(): LocationResult =
            LocationResult(latitude = null, longitude = null, error = "Location not available")

        override suspend fun getFineLocation(): LocationResult = getCoarseLocation()
    }
