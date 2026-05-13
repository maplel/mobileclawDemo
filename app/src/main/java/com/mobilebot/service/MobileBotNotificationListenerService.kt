package com.mobilebot.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.mobilebot.bridge.NotificationHistoryStore
import com.mobilebot.bridge.NotificationItem
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Mirrors active notifications into [NotificationHistoryStore] for [com.mobilebot.bridge.NotificationBridge].
 * User must enable MobileBot under **Settings → Apps → Special access → Notification access**.
 */
@AndroidEntryPoint
class MobileBotNotificationListenerService : NotificationListenerService() {
    @Inject
    lateinit var notificationHistoryStore: NotificationHistoryStore

    override fun onCreate() {
        super.onCreate()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        runCatching {
            activeNotifications?.forEach { onPosted(it) }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        onPosted(sbn)
    }

    private fun onPosted(sbn: StatusBarNotification) {
        val item = sbn.toItem()
        notificationHistoryStore.onPosted(item, sbn.key)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        notificationHistoryStore.onRemoved(sbn.key)
    }

    private fun StatusBarNotification.toItem(): NotificationItem {
        val extras = notification.extras
        val title =
            extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text =
            extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                .ifEmpty {
                    extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
                }
                .ifEmpty {
                    extras?.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString().orEmpty()
                }
        return NotificationItem(
            id = "${packageName}_${postTime}_${id}",
            packageName = packageName,
            title = title,
            text = text,
            postTimeMs = postTime,
        )
    }
}
