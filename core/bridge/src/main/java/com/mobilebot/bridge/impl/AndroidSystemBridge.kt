package com.mobilebot.bridge.impl

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.AlarmClock
import android.provider.Settings
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
    }
