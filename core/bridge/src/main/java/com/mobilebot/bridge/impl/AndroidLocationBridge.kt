package com.mobilebot.bridge.impl

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.mobilebot.bridge.LocationBridge
import com.mobilebot.bridge.LocationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidLocationBridge
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : LocationBridge {
        override suspend fun getCoarseLocation(): LocationResult = withContext(Dispatchers.IO) { readLastKnown() }

        override suspend fun getFineLocation(): LocationResult = withContext(Dispatchers.IO) { readLastKnown() }

        @SuppressLint("MissingPermission")
        private fun readLastKnown(): LocationResult {
            val fineOk =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            val coarseOk =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            if (!fineOk && !coarseOk) {
                return LocationResult(latitude = null, longitude = null, error = "Location permission not granted")
            }
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return LocationResult(null, null, error = "LocationManager unavailable")
            val providers = lm.getProviders(true)
            for (p in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
                if (p in providers) {
                    val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull()
                    if (loc != null) {
                        return LocationResult(latitude = loc.latitude, longitude = loc.longitude, error = null)
                    }
                }
            }
            return LocationResult(latitude = null, longitude = null, error = "No cached location yet")
        }
    }
