package com.mobilebot.bridge

data class LocationResult(
    val latitude: Double?,
    val longitude: Double?,
    val error: String?,
)

interface LocationBridge {
    suspend fun getCoarseLocation(): LocationResult

    suspend fun getFineLocation(): LocationResult
}
