package com.mobilebot.bridge.impl

import com.mobilebot.bridge.NotificationHistoryStore
import com.mobilebot.bridge.NotificationItem
import java.util.ArrayDeque
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock

@Singleton
class InMemoryNotificationHistoryStore
    @Inject
    constructor() : NotificationHistoryStore {
        private val lock = ReentrantLock()
        private val maxEntries = 200
        private val entries = ArrayDeque<Entry>()

        private data class Entry(
            val key: String,
            val item: NotificationItem,
        )

        override fun onPosted(
            item: NotificationItem,
            sbnKey: String,
        ) {
            lock.withLock {
                entries.removeIf { it.key == sbnKey }
                while (entries.size >= maxEntries) entries.removeLast()
                entries.addFirst(Entry(sbnKey, item))
            }
        }

        override fun onRemoved(sbnKey: String) {
            lock.withLock {
                entries.removeIf { it.key == sbnKey }
            }
        }

        override fun snapshotRecent(limit: Int): List<NotificationItem> {
            lock.withLock {
                return entries.take(limit.coerceAtLeast(0)).map { it.item }
            }
        }

        override fun snapshotByPackage(
            packageName: String,
            limit: Int,
        ): List<NotificationItem> {
            lock.withLock {
                return entries
                    .asSequence()
                    .filter { it.item.packageName == packageName }
                    .take(limit.coerceAtLeast(0))
                    .map { it.item }
                    .toList()
            }
        }

        override fun addSyntheticForTesting(item: NotificationItem) {
            val key = "test:${item.id}:${item.postTimeMs}"
            onPosted(item, key)
        }
    }
