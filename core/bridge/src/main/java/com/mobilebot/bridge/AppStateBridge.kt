package com.mobilebot.bridge

import kotlinx.coroutines.flow.Flow

interface AppStateBridge {
    suspend fun snapshot(): DeviceContextSnapshot

    fun observe(): Flow<DeviceContextSnapshot>
}
