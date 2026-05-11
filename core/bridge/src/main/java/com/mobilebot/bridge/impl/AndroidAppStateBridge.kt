package com.mobilebot.bridge.impl

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.mobilebot.bridge.AppStateBridge
import com.mobilebot.bridge.ConnectivityState
import com.mobilebot.bridge.DeviceContextSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class AndroidAppStateBridge
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AppStateBridge {
        @SuppressLint("MissingPermission")
        override suspend fun snapshot(): DeviceContextSnapshot {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
            val charging =
                bm.isCharging ||
                    run {
                        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                        val st = context.registerReceiver(null, ifilter)
                        val status = st?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                        status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                    }

            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val connectivity =
                runCatching {
                    val nw = cm.activeNetwork ?: return@runCatching ConnectivityState.NONE
                    val caps = cm.getNetworkCapabilities(nw) ?: return@runCatching ConnectivityState.NONE
                    when {
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectivityState.WIFI
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectivityState.CELLULAR
                        else -> ConnectivityState.OTHER
                    }
                }.getOrDefault(ConnectivityState.UNKNOWN)

            val screenOn =
                runCatching {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                    pm.isInteractive
                }.getOrElse { true }

            return DeviceContextSnapshot(
                batteryPct = batteryPct,
                charging = charging,
                connectivity = connectivity,
                foregroundApp = readForegroundPackage(),
                screenOn = screenOn,
                locale = Locale.getDefault().toLanguageTag(),
                timezone = TimeZone.getDefault().id,
            )
        }

        private fun readForegroundPackage(): String? =
            runCatching {
                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                if (usm != null) {
                    val end = System.currentTimeMillis()
                    val begin = end - 60_000L
                    val stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_BEST, begin, end)
                    stats?.maxByOrNull { it.lastTimeUsed }?.packageName
                } else {
                    null
                }
            }.getOrNull()

        override fun observe(): Flow<DeviceContextSnapshot> =
            flow {
                while (coroutineContext.isActive) {
                    emit(snapshot())
                    delay(10_000L)
                }
            }
    }
