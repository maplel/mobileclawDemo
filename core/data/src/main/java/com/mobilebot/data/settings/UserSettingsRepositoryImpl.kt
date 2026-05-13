package com.mobilebot.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSettingsRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val devSecrets: DevLlmSecrets,
    ) : UserSettingsRepository {
        private val masterKey =
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

        private val prefs: SharedPreferences =
            EncryptedSharedPreferences.create(
                context,
                "mobilebot_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

        override val settingsFlow: Flow<Unit> =
            callbackFlow {
                val listener =
                    SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                        trySend(Unit)
                    }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

        override suspend fun getApiKey(): String =
            UserSettingsResolution.resolvedApiKey(
                prefs.getString(KEY_API, "").orEmpty(),
                devSecrets.geminiApiKeyFromLocalBuild(),
                devSecrets.zhipuApiKeyFromLocalBuild(),
                devSecrets.minimaxApiKeyFromLocalBuild(),
                devSecrets.dashscopeApiKeyFromLocalBuild(),
            )

        override suspend fun getApiKeyStoredOnly(): String = prefs.getString(KEY_API, "").orEmpty()

        override suspend fun getBaseUrl(): String =
            UserSettingsResolution.resolvedBaseUrl(
                prefs.contains(KEY_BASE),
                prefs.getString(KEY_BASE, "").orEmpty(),
            )

        override suspend fun getModel(): String {
            val keyPresent = prefs.contains(KEY_MODEL)
            val stored = prefs.getString(KEY_MODEL, "").orEmpty()
            val resolved = UserSettingsResolution.resolvedModel(keyPresent, stored)
            // 把旧版 glm-4.7 一次性写回，避免 UI/日志仍显示旧 id、或未走到 resolved 的构建
            if (keyPresent && stored.trim().equals("glm-4.7", ignoreCase = true)) {
                prefs.edit().putString(KEY_MODEL, resolved).apply()
            }
            return resolved
        }

        override suspend fun getDeviceId(): String =
            prefs.getString(KEY_DEVICE, "android-device-1").orEmpty()

        override suspend fun getProviderId(): String =
            prefs.getString(KEY_PROVIDER, "").orEmpty()

        override suspend fun setApiKey(value: String) {
            prefs.edit().putString(KEY_API, value).apply()
        }

        override suspend fun setBaseUrl(value: String) {
            prefs.edit().putString(KEY_BASE, value).apply()
        }

        override suspend fun setModel(value: String) {
            prefs.edit().putString(KEY_MODEL, value).apply()
        }

        override suspend fun setProviderId(value: String) {
            prefs.edit().putString(KEY_PROVIDER, value).apply()
        }

        override suspend fun setDeviceId(value: String) {
            prefs.edit().putString(KEY_DEVICE, value).apply()
        }

        override suspend fun getHeartbeatEnabled(): Boolean = prefs.getBoolean(KEY_HEARTBEAT, false)

        override suspend fun setHeartbeatEnabled(value: Boolean) {
            prefs.edit().putBoolean(KEY_HEARTBEAT, value).apply()
        }

        companion object {
            private const val KEY_API = "api_key"
            private const val KEY_BASE = "base_url"
            private const val KEY_MODEL = "model"
            private const val KEY_PROVIDER = "provider_id"
            private const val KEY_DEVICE = "device_id"
            private const val KEY_HEARTBEAT = "heartbeat_enabled"
        }
    }
