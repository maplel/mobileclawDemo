package com.mobilebot.bridge.impl

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.util.Log
import com.mobilebot.bridge.AppInfo
import com.mobilebot.bridge.SystemBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSystemBridge
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SystemBridge {
        override fun openSettings(page: String?): Boolean {
            val action =
                when (page?.lowercase()?.trim()) {
                    null, "", "general" -> Settings.ACTION_SETTINGS
                    "wifi", "wlan" -> Settings.ACTION_WIFI_SETTINGS
                    "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                    "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
                    "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
                    "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                    "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
                    "apps", "applications" -> Settings.ACTION_APPLICATION_SETTINGS
                    "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                    "security" -> Settings.ACTION_SECURITY_SETTINGS
                    "date", "time" -> Settings.ACTION_DATE_SETTINGS
                    "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                    "about" -> Settings.ACTION_DEVICE_INFO_SETTINGS
                    "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
                    "notification_access" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                    "wireless", "network" -> Settings.ACTION_WIRELESS_SETTINGS
                    "input", "keyboard" -> Settings.ACTION_INPUT_METHOD_SETTINGS
                    else -> Settings.ACTION_SETTINGS
                }
            return fireIntent(Intent(action))
        }

        override fun setAlarm(hour: Int, minute: Int, label: String?): Boolean {
            val intent =
                Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
                    putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    if (!label.isNullOrBlank()) {
                        putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    }
                }
            return fireIntent(intent)
        }

        override fun setTimer(lengthSeconds: Int, label: String?): Boolean {
            if (lengthSeconds <= 0) return false
            val intent =
                Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, lengthSeconds)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    if (!label.isNullOrBlank()) {
                        putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    }
                }
            return fireIntent(intent)
        }

        override fun setFlashlight(on: Boolean): Boolean =
            try {
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val backCamera =
                    cm.cameraIdList.firstOrNull { id ->
                        val chars = cm.getCameraCharacteristics(id)
                        val facing = chars.get(CameraCharacteristics.LENS_FACING)
                        facing == CameraCharacteristics.LENS_FACING_BACK &&
                            chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    }
                if (backCamera != null) {
                    cm.setTorchMode(backCamera, on)
                    true
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }

        override fun openApp(packageName: String): Boolean {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return fireIntent(intent)
        }

        override fun resolveAppPackage(query: String): List<AppInfo> {
            if (query.isBlank()) return emptyList()
            val pm = context.packageManager
            val lower = query.lowercase().trim()
            return pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { app ->
                    pm.getLaunchIntentForPackage(app.packageName) != null &&
                        (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                }
                .mapNotNull { app ->
                    val label = pm.getApplicationLabel(app).toString()
                    if (label.lowercase().contains(lower) || app.packageName.lowercase().contains(lower)) {
                        AppInfo(packageName = app.packageName, label = label)
                    } else {
                        null
                    }
                }
                .take(10)
        }

        override fun createCalendarEvent(
            title: String,
            startTime: String,
            endTime: String,
            location: String,
            description: String,
        ): Boolean {
            return try {
                val values = ContentValues().apply {
                    put(CalendarContract.Events.DTSTART, parseIsoToMillis(startTime))
                    put(CalendarContract.Events.DTEND, parseIsoToMillis(endTime))
                    put(CalendarContract.Events.TITLE, title)
                    put(CalendarContract.Events.DESCRIPTION, description)
                    put(CalendarContract.Events.EVENT_LOCATION, location)
                    put(CalendarContract.Events.CALENDAR_ID, getDefaultCalendarId())
                    put(CalendarContract.Events.EVENT_TIMEZONE, "Asia/Shanghai")
                }
                val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                if (uri != null) {
                    Log.d(TAG, "Calendar event created: $title at $startTime (uri=$uri)")
                    true
                } else {
                    Log.w(TAG, "Failed to insert calendar event: contentResolver returned null")
                    false
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "No calendar permission: ${e.message}")
                false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create calendar event: ${e.message}")
                false
            }
        }

        override fun queryCalendarEvents(startDate: String, endDate: String): String? {
            return try {
                val startMillis = parseDateToMillis(startDate)
                val endMillis = parseDateToMillis(endDate) + 86_400_000L
                val projection = arrayOf(
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.EVENT_LOCATION,
                    CalendarContract.Events.DESCRIPTION,
                )
                val cursor = context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?",
                    arrayOf(startMillis.toString(), endMillis.toString()),
                    "${CalendarContract.Events.DTSTART} ASC",
                )
                cursor?.use {
                    val events = org.json.JSONArray()
                    while (it.moveToNext()) {
                        val event = org.json.JSONObject().apply {
                            put("title", it.getString(0) ?: "")
                            put("startTime", it.getString(1) ?: "")
                            put("endTime", it.getString(2) ?: "")
                            put("location", it.getString(3) ?: "")
                            put("description", it.getString(4) ?: "")
                        }
                        events.put(event)
                    }
                    if (events.length() == 0) null else events.toString()
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "No calendar permission: ${e.message}")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query calendar events: ${e.message}")
                null
            }
        }

        private fun getDefaultCalendarId(): Long {
            val projection = arrayOf(CalendarContract.Calendars._ID)
            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null, null, null,
            )
            cursor?.use {
                if (it.moveToFirst()) return it.getLong(0)
            }
            return 1L
        }

        private fun parseIsoToMillis(iso: String): Long {
            val formats = listOf(
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.US),
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US),
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US),
            )
            for (fmt in formats) {
                try {
                    return fmt.parse(iso)?.time ?: continue
                } catch (_: Exception) { continue }
            }
            return System.currentTimeMillis()
        }

        private fun parseDateToMillis(date: String): Long {
            return try {
                val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                fmt.parse(date)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }

        private fun fireIntent(intent: Intent): Boolean =
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: Exception) {
                false
            }

        private companion object {
            private const val TAG = "AndroidSystemBridge"
        }
    }
