package com.mobilebot.bridge.impl

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mobilebot.bridge.BrowserBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidBrowserBridge
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BrowserBridge {
        override fun openUrl(url: String): Boolean {
            val normalized =
                url.trim().let { u ->
                    if (u.startsWith("http://", ignoreCase = true) || u.startsWith("https://", ignoreCase = true)) {
                        u
                    } else {
                        "https://$u"
                    }
                }
            return try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: Exception) {
                false
            }
        }
    }
