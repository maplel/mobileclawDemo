package com.mobilebot.data.capabilities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.mobilebot.domain.capabilities.CapabilityProbe
import com.mobilebot.domain.capabilities.CapabilitySnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidRuntimeCapabilityProbe
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : CapabilityProbe {
        override suspend fun probe(): CapabilitySnapshot =
            withContext(Dispatchers.Default) {
                val pm = context.packageManager
                CapabilitySnapshot(
                    smsAvailable =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.SEND_SMS,
                        ) == PackageManager.PERMISSION_GRANTED,
                    contactsAvailable =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_CONTACTS,
                        ) == PackageManager.PERMISSION_GRANTED,
                    locationAvailable =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ) == PackageManager.PERMISSION_GRANTED,
                    browserAvailable = canResolveHttpsBrowser(pm),
                    cameraAvailable =
                        pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) ||
                            pm.hasSystemFeature(PackageManager.FEATURE_CAMERA),
                )
            }

        private fun canResolveHttpsBrowser(pm: PackageManager): Boolean {
            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
            return intent.resolveActivity(pm) != null
        }
    }
