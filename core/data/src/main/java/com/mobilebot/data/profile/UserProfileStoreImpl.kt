package com.mobilebot.data.profile

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mobilebot.domain.profile.UserProfileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted local storage for user profile data, organized by category.
 *
 * Categories include: insurance, membership, preferences, emergency_contacts,
 * vehicles, health, trip_plans.
 */
@Singleton
class UserProfileStoreImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : UserProfileStore {
        private val prefs: SharedPreferences by lazy {
            try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (_: Exception) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }

        override suspend fun get(category: String, key: String?): String? {
            val categoryLower = category.lowercase()
            return if (key != null) {
                val keyLower = key.lowercase()
                prefs.getString(storageKey(categoryLower, keyLower), null)
                    ?: run {
                        val prefix = "$categoryLower:"
                        val matching = prefs.all.filter {
                            it.key.lowercase().startsWith(prefix) &&
                                it.key.lowercase().removePrefix(prefix).startsWith(keyLower)
                        }
                        if (matching.isEmpty()) return null
                        matching.entries.joinToString("\n") {
                            "${it.key.removePrefix("$category:")}: ${it.value}"
                        }
                    }
            } else {
                val prefix = "$categoryLower:"
                val entries = prefs.all.filter { it.key.lowercase().startsWith(prefix) }
                if (entries.isEmpty()) return null
                entries.entries.joinToString("\n") {
                    val originalPrefix = it.key.indexOf(':').let { idx ->
                        if (idx >= 0) it.key.substring(idx + 1) else it.key
                    }
                    "$originalPrefix: ${it.value}"
                }
            }
        }

        override suspend fun set(category: String, key: String, value: String) {
            prefs.edit().putString(storageKey(category, key), value).apply()
        }

        override fun listCategories(): List<String> =
            prefs.all.keys
                .mapNotNull { it.split(":").firstOrNull() }
                .distinct()
                .sorted()

        private fun storageKey(category: String, key: String) = "$category:$key"

        private companion object {
            private const val PREFS_NAME = "user_profile_store"
        }
    }
