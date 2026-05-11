package com.mobilebot.data.capabilities

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat
import com.mobilebot.domain.tools.DeviceCapabilityProbe
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Declares whether tools are **eligible to be offered** on this build (planner + registry).
 * Also provides device checks for skill eligibility (permissions, connectivity, apps, API level).
 */
@Singleton
class AndroidCapabilityProbe @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceCapabilityProbe {

    override fun hasCapabilities(capabilities: Set<String>): Boolean {
        if (capabilities.isEmpty()) return true
        return capabilities.all { cap ->
            when (cap) {
                "files.workspace" -> true
                "media.camera" -> true
                "notifications.read" -> true
                "accessibility.control" -> false
                "browser.view" -> true
                "maps.external" -> true
                "clipboard.write" -> true
                "telephony.dial" -> true
                "messaging.sms" -> true
                "messaging.sms.send" -> true
                "calendar.read" -> true
                "share.generic" -> true
                "location.coarse" -> true
                "contacts.read" -> true
                else -> false
            }
        }
    }

    override fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context, permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun isConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    override fun meetsMinApi(minApi: Int): Boolean {
        return Build.VERSION.SDK_INT >= minApi
    }

    override fun isWithinTimeRange(timeSpec: String): Boolean {
        val parts = timeSpec.split("-")
        if (parts.size != 2) return true
        val startParts = parts[0].trim().split(":")
        val endParts = parts[1].trim().split(":")
        if (startParts.size != 2 || endParts.size != 2) return true

        val startMinutes = (startParts[0].toIntOrNull() ?: 0) * 60 + (startParts[1].toIntOrNull() ?: 0)
        val endMinutes = (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0)

        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        return if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes..endMinutes
        } else {
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }
    }
}
