package com.mobilebot.bridge.impl

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.mobilebot.bridge.SmsSendResult
import com.mobilebot.bridge.TelephonyBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidTelephonyBridge
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : TelephonyBridge {
        override fun dialNumber(phoneNumber: String): Boolean {
            val cleaned = phoneNumber.trim()
            if (cleaned.isEmpty()) return false
            val uri = Uri.parse("tel:${Uri.encode(cleaned)}")
            val intent = Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return tryLaunch(intent)
        }

        override fun openSmsComposer(
            phoneNumber: String,
            message: String,
        ): Boolean {
            val num = phoneNumber.trim()
            if (num.isEmpty()) return false
            val uri = Uri.parse("smsto:${Uri.encode(num)}")
            val intent =
                Intent(Intent.ACTION_SENDTO, uri).apply {
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            return tryLaunch(intent)
        }

        override fun sendSms(
            phoneNumber: String,
            message: String,
        ): SmsSendResult {
            val num = phoneNumber.trim()
            if (num.isEmpty()) return SmsSendResult(success = false, sentDirectly = false)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                return try {
                    val sm = obtainSmsManager()
                    sm?.sendTextMessage(num, null, message, null, null)
                    SmsSendResult(success = true, sentDirectly = true)
                } catch (_: SecurityException) {
                    val ok = openSmsComposer(num, message)
                    SmsSendResult(success = ok, sentDirectly = false)
                } catch (_: Exception) {
                    val ok = openSmsComposer(num, message)
                    SmsSendResult(success = ok, sentDirectly = false)
                }
            }
            val ok = openSmsComposer(num, message)
            return SmsSendResult(success = ok, sentDirectly = false)
        }

        private fun obtainSmsManager(): SmsManager? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

        private fun tryLaunch(intent: Intent): Boolean =
            try {
                context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: Exception) {
                false
            }
    }
