package com.mobilebot.data.capabilities

import android.content.Context
import com.mobilebot.domain.permissions.AgentCapabilityStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentCapabilityStoreImpl
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : AgentCapabilityStore {
        private val prefs =
            context.getSharedPreferences("agent_capabilities", Context.MODE_PRIVATE)

        override fun isGranted(capabilityId: String): Boolean =
            prefs.getStringSet(KEY, emptySet())?.contains(capabilityId) == true

        override fun grant(capabilityId: String) {
            val current = HashSet(prefs.getStringSet(KEY, emptySet()) ?: emptySet())
            current.add(capabilityId)
            prefs.edit().putStringSet(KEY, current).apply()
        }

        override fun revoke(capabilityId: String) {
            val current = HashSet(prefs.getStringSet(KEY, emptySet()) ?: emptySet())
            current.remove(capabilityId)
            prefs.edit().putStringSet(KEY, current).apply()
        }

        override fun grantedSet(): Set<String> =
            prefs.getStringSet(KEY, emptySet()) ?: emptySet()

        private companion object {
            private const val KEY = "granted_capabilities"
        }
    }
