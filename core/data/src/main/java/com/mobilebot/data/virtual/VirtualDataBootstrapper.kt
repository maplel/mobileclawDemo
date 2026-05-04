package com.mobilebot.data.virtual

import android.util.Log
import com.mobilebot.bridge.virtual.VirtualBridgeManager
import com.mobilebot.bridge.virtual.VirtualMockData
import com.mobilebot.domain.profile.UserProfileStore
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pre-populates [UserProfileStore] with test data when virtual mode is active.
 * Should be called once during application startup, after DI is ready.
 */
@Singleton
class VirtualDataBootstrapper
    @Inject
    constructor(
        private val manager: VirtualBridgeManager,
        private val profileStore: UserProfileStore,
    ) {
        fun bootstrapIfNeeded() {
            if (!manager.hasAnyVirtual()) {
                Log.d(TAG, "All bridges are REAL, skipping virtual data bootstrap")
                return
            }

            Log.i(TAG, "Virtual mode detected, pre-populating UserProfileStore with test data")
            manager.logModes()

            runBlocking {
                for ((category, entries) in VirtualMockData.USER_PROFILE) {
                    for ((key, value) in entries) {
                        profileStore.set(category, key, value)
                    }
                    Log.d(TAG, "Populated category '$category' with ${entries.size} entries")
                }
            }

            Log.i(TAG, "Virtual data bootstrap complete: ${VirtualMockData.USER_PROFILE.size} categories populated")
        }

        private companion object {
            private const val TAG = "VirtualDataBootstrap"
        }
    }
