package com.mobilebot.bridge.virtual

import android.util.Log
import com.mobilebot.bridge.NotificationBridge
import com.mobilebot.bridge.NotificationItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VirtualNotificationBridge
    @Inject
    constructor() : NotificationBridge {
        override suspend fun listRecent(limit: Int): List<NotificationItem> {
            Log.d(TAG, "[VIRTUAL] listRecent(limit=$limit)")
            return NOTIFICATIONS.take(limit)
        }

        override suspend fun findByPackage(packageName: String, limit: Int): List<NotificationItem> {
            Log.d(TAG, "[VIRTUAL] findByPackage($packageName, limit=$limit)")
            return NOTIFICATIONS.filter { it.packageName == packageName }.take(limit)
        }

        private companion object {
            private const val TAG = "VirtualNotification"
            private val NOW = System.currentTimeMillis()
            private val NOTIFICATIONS = listOf(
                NotificationItem(
                    id = "vn-1",
                    packageName = "com.tencent.mm",
                    title = "张三",
                    text = "你好，明天下午3点开会，别忘了",
                    postTimeMs = NOW - 300_000,
                ),
                NotificationItem(
                    id = "vn-2",
                    packageName = "com.android.vending",
                    title = "系统更新",
                    text = "Android 安全补丁 2026-04 可用",
                    postTimeMs = NOW - 1_800_000,
                ),
                NotificationItem(
                    id = "vn-3",
                    packageName = "com.google.android.calendar",
                    title = "日历提醒",
                    text = "15:00 项目评审会议",
                    postTimeMs = NOW - 3_600_000,
                ),
                NotificationItem(
                    id = "vn-4",
                    packageName = "com.eg.android.AlipayGphone",
                    title = "支付宝",
                    text = "您的快递已签收",
                    postTimeMs = NOW - 7_200_000,
                ),
            )
        }
    }
