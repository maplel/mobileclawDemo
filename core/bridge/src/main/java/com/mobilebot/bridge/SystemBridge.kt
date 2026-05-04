package com.mobilebot.bridge

interface SystemBridge {
    fun openSettings(page: String?): Boolean

    fun setAlarm(hour: Int, minute: Int, label: String?): Boolean

    fun setTimer(lengthSeconds: Int, label: String?): Boolean

    fun setFlashlight(on: Boolean): Boolean

    fun openApp(packageName: String): Boolean

    fun resolveAppPackage(query: String): List<AppInfo>

    fun createCalendarEvent(
        title: String,
        startTime: String,
        endTime: String,
        location: String,
        description: String,
    ): Boolean = false

    fun queryCalendarEvents(startDate: String, endDate: String): String? = null

    fun showNotification(title: String, message: String, priority: String): Boolean = false

    fun openDeepLink(uri: String, fallbackUrl: String): Boolean = false

    fun getDeviceState(): String = "{}"

    fun playMedia(query: String, action: String): Boolean = false

    fun writeSandboxFile(path: String, content: String, append: Boolean): Boolean = false
}

data class AppInfo(val packageName: String, val label: String)
