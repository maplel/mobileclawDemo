package com.mobilebot.bridge

data class SmsSendResult(
    val success: Boolean,
    /** True when the message was queued via [android.telephony.SmsManager] (no composer). */
    val sentDirectly: Boolean,
)

interface TelephonyBridge {
    /** Opens the dialer with the number prefilled (no call placed without user action). */
    fun dialNumber(phoneNumber: String): Boolean

    /** Opens an SMS composer; user may need to press send. */
    fun openSmsComposer(
        phoneNumber: String,
        message: String,
    ): Boolean

    /**
     * Sends an SMS without UI when [android.Manifest.permission.SEND_SMS] is granted;
     * otherwise opens the composer ([openSmsComposer]).
     */
    fun sendSms(
        phoneNumber: String,
        message: String,
    ): SmsSendResult
}
