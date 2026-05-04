package com.mobilebot.bridge

/**
 * In-memory mirror of posted notifications (filled by [android.service.notification.NotificationListenerService]).
 * [addSyntheticForTesting] is for androidTest / manual QA without a live listener.
 */
interface NotificationHistoryStore {
    fun onPosted(
        item: NotificationItem,
        sbnKey: String,
    )

    fun onRemoved(sbnKey: String)

    fun snapshotRecent(limit: Int): List<NotificationItem>

    fun snapshotByPackage(
        packageName: String,
        limit: Int,
    ): List<NotificationItem>

    fun addSyntheticForTesting(item: NotificationItem)
}
