package com.mobilebot.bridge.virtual

import com.mobilebot.bridge.AccessibilityActionBridge
import com.mobilebot.bridge.AppStateBridge
import com.mobilebot.bridge.BrowserBridge
import com.mobilebot.bridge.ClipboardBridge
import com.mobilebot.bridge.ConnectivityState
import com.mobilebot.bridge.ContactsBridge
import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.bridge.DeviceContextSnapshot
import com.mobilebot.bridge.FileBridge
import com.mobilebot.bridge.LocationBridge
import com.mobilebot.bridge.MapsBridge
import com.mobilebot.bridge.MediaBridge
import com.mobilebot.bridge.NotificationBridge
import com.mobilebot.bridge.ServiceGateway
import com.mobilebot.bridge.ShareBridge
import com.mobilebot.bridge.SystemBridge
import com.mobilebot.bridge.TelephonyBridge
import com.mobilebot.bridge.UiAction
import com.mobilebot.bridge.UiActionResult
import com.mobilebot.bridge.UiTreeSnapshot
import com.mobilebot.bridge.AppInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fully virtual [DeviceCapabilityBridge] composed of virtual bridge implementations.
 * Used when all bridges are set to virtual mode, or as the virtual half of
 * [SwitchableDeviceCapabilityBridge].
 */
@Singleton
class VirtualDeviceCapabilityBridge
    @Inject
    constructor(
        virtualTelephony: VirtualTelephonyBridge,
        virtualContacts: VirtualContactsBridge,
        virtualLocation: VirtualLocationBridge,
        virtualNotifications: VirtualNotificationBridge,
        virtualFiles: VirtualFileBridge,
        virtualServices: VirtualServiceGateway,
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
    ) : DeviceCapabilityBridge {
        val virtualSystemBridge = VirtualSystemBridgeWithCalendar(context)
        override val files: FileBridge = virtualFiles
        override val notifications: NotificationBridge = virtualNotifications
        override val accessibility: AccessibilityActionBridge = VirtualAccessibilityBridge()
        override val appState: AppStateBridge = VirtualAppStateBridge()
        override val contacts: ContactsBridge = virtualContacts
        override val location: LocationBridge = virtualLocation
        override val media: MediaBridge = VirtualMediaBridge()
        override val browser: BrowserBridge = VirtualBrowserBridge()
        override val maps: MapsBridge = VirtualMapsBridge()
        override val clipboard: ClipboardBridge = VirtualClipboardBridge()
        override val share: ShareBridge = VirtualShareBridge()
        override val telephony: TelephonyBridge = virtualTelephony
        override val system: SystemBridge = virtualSystemBridge
        override val services: ServiceGateway = virtualServices
    }

private class VirtualAccessibilityBridge : AccessibilityActionBridge {
    override suspend fun snapshot(): UiTreeSnapshot =
        UiTreeSnapshot(packageName = "com.mobilebot", serializedSummary = "[VIRTUAL] UI snapshot")

    override suspend fun perform(action: UiAction): UiActionResult =
        UiActionResult.Success
}

private class VirtualAppStateBridge : AppStateBridge {
    private val state = DeviceContextSnapshot(
        batteryPct = 85,
        charging = false,
        connectivity = ConnectivityState.WIFI,
        foregroundApp = "com.mobilebot",
        screenOn = true,
        locale = "zh-CN",
        timezone = "Asia/Shanghai",
    )

    override suspend fun snapshot(): DeviceContextSnapshot = state
    override fun observe(): Flow<DeviceContextSnapshot> = MutableStateFlow(state)
}

private class VirtualMediaBridge : MediaBridge {
    override suspend fun launchStillCamera(): String = "[VIRTUAL] Camera launched successfully"
}

private class VirtualBrowserBridge : BrowserBridge {
    override fun openUrl(url: String): Boolean = true
}

private class VirtualMapsBridge : MapsBridge {
    override fun openMap(query: String, mode: String): Boolean = true
}

private class VirtualClipboardBridge : ClipboardBridge {
    override fun copyToClipboard(text: String): Boolean = true
}

private class VirtualShareBridge : ShareBridge {
    override fun shareText(text: String): Boolean = true
}

class VirtualSystemBridgeWithCalendar(
    private val context: android.content.Context? = null,
) : SystemBridge {
    private val calendarEvents = mutableListOf<Map<String, String>>()

    private val prefs: android.content.SharedPreferences? by lazy {
        context?.getSharedPreferences("virtual_calendar_events", android.content.Context.MODE_PRIVATE)
    }

    init {
        loadCalendarFromDisk()
    }

    private fun loadCalendarFromDisk() {
        val sp = prefs ?: return
        val json = sp.getString("calendar_events", null) ?: return
        try {
            val arr = org.json.JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                calendarEvents.add(
                    mapOf(
                        "title" to (obj.optString("title", "")),
                        "startTime" to (obj.optString("startTime", "")),
                        "endTime" to (obj.optString("endTime", "")),
                        "location" to (obj.optString("location", "")),
                        "description" to (obj.optString("description", "")),
                    ),
                )
            }
        } catch (_: Exception) {}
    }

    private fun saveCalendarToDisk() {
        val sp = prefs ?: return
        val arr = org.json.JSONArray()
        for (event in calendarEvents) {
            val obj = org.json.JSONObject()
            for ((k, v) in event) {
                obj.put(k, v)
            }
            arr.put(obj)
        }
        sp.edit().putString("calendar_events", arr.toString()).apply()
    }

    override fun openSettings(page: String?): Boolean = true
    override fun setAlarm(hour: Int, minute: Int, label: String?): Boolean = true
    override fun setTimer(lengthSeconds: Int, label: String?): Boolean = true
    override fun setFlashlight(on: Boolean): Boolean = true
    override fun openApp(packageName: String): Boolean = true
    override fun resolveAppPackage(query: String): List<AppInfo> = listOf(
        AppInfo(packageName = "com.virtual.app", label = query),
    )

    override fun createCalendarEvent(
        title: String,
        startTime: String,
        endTime: String,
        location: String,
        description: String,
    ): Boolean {
        calendarEvents.add(
            mapOf(
                "title" to title,
                "startTime" to startTime,
                "endTime" to endTime,
                "location" to location,
                "description" to description,
            ),
        )
        saveCalendarToDisk()
        return true
    }

    override fun queryCalendarEvents(startDate: String, endDate: String): String? {
        val matching = calendarEvents.filter { event ->
            val eventDate = event["startTime"]?.substring(0, 10) ?: ""
            eventDate >= startDate && eventDate <= endDate
        }
        if (matching.isEmpty()) return null
        return org.json.JSONArray(matching.map { org.json.JSONObject(it) }).toString()
    }
}
