package com.mobilebot.bridge.impl

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import com.mobilebot.bridge.MediaBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidMediaBridge
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : MediaBridge {
        override suspend fun launchStillCamera(): String =
            withContext(Dispatchers.Main) {
                val implicit =
                    listOf(
                        Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
                        Intent(MediaStore.ACTION_IMAGE_CAPTURE),
                    )
                for (intent in implicit) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(intent)
                        return@withContext "Camera app opened."
                    } catch (_: ActivityNotFoundException) {
                        continue
                    } catch (e: SecurityException) {
                        return@withContext e.message ?: "security"
                    }
                }

                for (pkg in KNOWN_CAMERA_PACKAGES) {
                    val launch =
                        try {
                            context.packageManager.getLaunchIntentForPackage(pkg)
                        } catch (_: Exception) {
                            null
                        } ?: continue
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(launch)
                        return@withContext "Camera app opened."
                    } catch (_: ActivityNotFoundException) {
                        continue
                    } catch (e: Exception) {
                        return@withContext e.message ?: "launch failed"
                    }
                }

                "Could not open a camera app."
            }

        private companion object {
            val KNOWN_CAMERA_PACKAGES =
                listOf(
                    "com.android.camera2",
                    "com.google.android.GoogleCamera",
                    "com.android.camera",
                )
        }
    }
