package com.mobilebot.domain.testdoubles

import com.mobilebot.bridge.BrowserBridge
import com.mobilebot.bridge.ClipboardBridge
import com.mobilebot.bridge.ContactsBridge
import com.mobilebot.bridge.LocationBridge
import com.mobilebot.bridge.LocationResult
import com.mobilebot.bridge.MapsBridge
import com.mobilebot.bridge.MediaBridge
import com.mobilebot.bridge.NotificationBridge
import com.mobilebot.bridge.NotificationItem
import com.mobilebot.bridge.ShareBridge
import com.mobilebot.bridge.SmsSendResult
import com.mobilebot.bridge.TelephonyBridge
import com.mobilebot.bridge.FileBridge
import com.mobilebot.bridge.WorkspaceFileRead

class RecordingBrowserBridge : BrowserBridge {
    val openedUrls = mutableListOf<String>()
    var returnValue = true

    override fun openUrl(url: String): Boolean {
        openedUrls += url
        return returnValue
    }
}

class RecordingMapsBridge : MapsBridge {
    data class MapCall(val query: String, val mode: String)

    val calls = mutableListOf<MapCall>()
    var returnValue = true

    override fun openMap(query: String, mode: String): Boolean {
        calls += MapCall(query, mode)
        return returnValue
    }
}

class RecordingContactsBridge : ContactsBridge {
    data class SearchCall(val query: String, val limit: Int)

    val searches = mutableListOf<SearchCall>()
    var results: List<String> = emptyList()

    override suspend fun searchContacts(query: String, limit: Int): List<String> {
        searches += SearchCall(query, limit)
        return results
    }
}

class RecordingTelephonyBridge : TelephonyBridge {
    data class SmsCall(val phone: String, val message: String)

    val dialedNumbers = mutableListOf<String>()
    val sentSms = mutableListOf<SmsCall>()
    val composerCalls = mutableListOf<SmsCall>()
    var dialReturnValue = true
    var smsResult = SmsSendResult(success = true, sentDirectly = true)
    var composerReturnValue = true

    override fun dialNumber(phoneNumber: String): Boolean {
        dialedNumbers += phoneNumber
        return dialReturnValue
    }

    override fun openSmsComposer(phoneNumber: String, message: String): Boolean {
        composerCalls += SmsCall(phoneNumber, message)
        return composerReturnValue
    }

    override fun sendSms(phoneNumber: String, message: String): SmsSendResult {
        sentSms += SmsCall(phoneNumber, message)
        return smsResult
    }
}

class RecordingClipboardBridge : ClipboardBridge {
    val copiedTexts = mutableListOf<String>()
    var returnValue = true

    override fun copyToClipboard(text: String): Boolean {
        copiedTexts += text
        return returnValue
    }
}

class RecordingShareBridge : ShareBridge {
    val sharedTexts = mutableListOf<String>()
    var returnValue = true

    override fun shareText(text: String): Boolean {
        sharedTexts += text
        return returnValue
    }
}

class RecordingMediaBridge : MediaBridge {
    var callCount = 0
    var returnMessage = "Camera opened."

    override suspend fun launchStillCamera(): String {
        callCount++
        return returnMessage
    }
}

class RecordingLocationBridge : LocationBridge {
    var coarseResult = LocationResult(39.9042, 116.4074, null)
    var fineResult = LocationResult(39.9042, 116.4074, null)

    override suspend fun getCoarseLocation(): LocationResult = coarseResult

    override suspend fun getFineLocation(): LocationResult = fineResult
}

class RecordingFileBridge : FileBridge {
    data class ReadCall(val path: String, val maxChars: Int)

    val reads = mutableListOf<ReadCall>()
    var fileContent = WorkspaceFileRead(text = "file content here")

    override suspend fun readWorkspaceText(relativePath: String, maxChars: Int): WorkspaceFileRead {
        reads += ReadCall(relativePath, maxChars)
        return fileContent
    }
}

class RecordingNotificationBridge : NotificationBridge {
    var recentItems: List<NotificationItem> = emptyList()

    override suspend fun listRecent(limit: Int): List<NotificationItem> =
        recentItems.take(limit)

    override suspend fun findByPackage(packageName: String, limit: Int): List<NotificationItem> =
        recentItems.filter { it.packageName == packageName }.take(limit)
}
