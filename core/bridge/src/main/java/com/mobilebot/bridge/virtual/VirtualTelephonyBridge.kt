package com.mobilebot.bridge.virtual

import android.util.Log
import com.mobilebot.bridge.SmsSendResult
import com.mobilebot.bridge.TelephonyBridge
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VirtualTelephonyBridge
    @Inject
    constructor() : TelephonyBridge {
        override fun dialNumber(phoneNumber: String): Boolean {
            Log.d(TAG, "[VIRTUAL] dialNumber($phoneNumber)")
            return true
        }

        override fun openSmsComposer(phoneNumber: String, message: String): Boolean {
            Log.d(TAG, "[VIRTUAL] openSmsComposer($phoneNumber, ${message.take(40)}...)")
            return true
        }

        override fun sendSms(phoneNumber: String, message: String): SmsSendResult {
            Log.d(TAG, "[VIRTUAL] sendSms($phoneNumber, ${message.take(40)}...)")
            return SmsSendResult(success = true, sentDirectly = true)
        }

        private companion object {
            private const val TAG = "VirtualTelephony"
        }
    }
