package com.mobilebot.bridge.impl

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.mobilebot.bridge.ClipboardBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidClipboardBridge
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ClipboardBridge {
        override fun copyToClipboard(text: String): Boolean {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
            return try {
                cm.setPrimaryClip(ClipData.newPlainText("mobilebot", text))
                true
            } catch (_: Exception) {
                false
            }
        }
    }
