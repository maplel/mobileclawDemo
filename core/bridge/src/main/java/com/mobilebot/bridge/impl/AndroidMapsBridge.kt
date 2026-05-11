package com.mobilebot.bridge.impl

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mobilebot.bridge.MapsBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidMapsBridge
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : MapsBridge {
        override fun openMap(
            query: String,
            mode: String,
        ): Boolean {
            val q = query.trim()
            if (q.isEmpty()) return false
            val encoded = URLEncoder.encode(q, Charsets.UTF_8.name())
            val primary =
                when (mode.lowercase()) {
                    "navigate" -> Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$encoded"))
                    else -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded"))
                }
            if (launch(primary)) return true
            val web =
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=${URLEncoder.encode(q, Charsets.UTF_8.name())}"),
                )
            return launch(web)
        }

        private fun launch(intent: Intent): Boolean {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: Exception) {
                false
            }
        }
    }
