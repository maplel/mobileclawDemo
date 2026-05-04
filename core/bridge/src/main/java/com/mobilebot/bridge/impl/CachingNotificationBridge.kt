package com.mobilebot.bridge.impl

import com.mobilebot.bridge.NotificationBridge
import com.mobilebot.bridge.NotificationHistoryStore
import com.mobilebot.bridge.NotificationItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachingNotificationBridge
    @Inject
    constructor(
        private val store: NotificationHistoryStore,
    ) : NotificationBridge {
        override suspend fun listRecent(limit: Int): List<NotificationItem> =
            withContext(Dispatchers.Default) {
                store.snapshotRecent(limit)
            }

        override suspend fun findByPackage(
            packageName: String,
            limit: Int,
        ): List<NotificationItem> =
            withContext(Dispatchers.Default) {
                store.snapshotByPackage(packageName, limit)
            }
    }
