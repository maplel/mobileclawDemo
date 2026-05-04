package com.mobilebot.bridge

data class NotificationItem(
    val id: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTimeMs: Long,
)

interface NotificationBridge {
    suspend fun listRecent(limit: Int = 20): List<NotificationItem>

    suspend fun findByPackage(
        packageName: String,
        limit: Int = 20,
    ): List<NotificationItem>
}
