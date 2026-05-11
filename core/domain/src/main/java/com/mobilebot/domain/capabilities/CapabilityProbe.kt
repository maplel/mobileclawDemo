package com.mobilebot.domain.capabilities

fun interface CapabilityProbe {
    suspend fun probe(): CapabilitySnapshot
}
