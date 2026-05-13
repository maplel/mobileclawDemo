package com.mobilebot.data.settings

import kotlinx.coroutines.flow.Flow

interface UserSettingsRepository {
    val settingsFlow: Flow<Unit>

    suspend fun getApiKey(): String

    /** Value saved in Settings only (no [local.properties] fallback); for UI display. */
    suspend fun getApiKeyStoredOnly(): String

    suspend fun getBaseUrl(): String

    suspend fun getModel(): String

    suspend fun getProviderId(): String

    suspend fun getDeviceId(): String

    suspend fun setApiKey(value: String)

    suspend fun setBaseUrl(value: String)

    suspend fun setModel(value: String)

    suspend fun setProviderId(value: String)

    suspend fun setDeviceId(value: String)

    suspend fun getHeartbeatEnabled(): Boolean

    suspend fun setHeartbeatEnabled(value: Boolean)
}
