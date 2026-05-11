package com.mobilebot.bridge.impl

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.mobilebot.bridge.ShareBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidShareBridge
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ShareBridge {
        override fun shareText(text: String): Boolean {
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(chooser)
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: Exception) {
                false
            }
        }
    }
