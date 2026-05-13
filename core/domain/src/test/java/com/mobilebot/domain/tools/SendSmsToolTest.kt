package com.mobilebot.domain.tools

import com.mobilebot.bridge.AppStateBridge
import com.mobilebot.bridge.AccessibilityActionBridge
import com.mobilebot.bridge.BrowserBridge
import com.mobilebot.bridge.ClipboardBridge
import com.mobilebot.bridge.ContactsBridge
import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.bridge.DeviceContextSnapshot
import com.mobilebot.bridge.FileBridge
import com.mobilebot.bridge.LocationBridge
import com.mobilebot.bridge.MapsBridge
import com.mobilebot.bridge.MediaBridge
import com.mobilebot.bridge.NotificationBridge
import com.mobilebot.bridge.NotificationItem
import com.mobilebot.bridge.ServiceGateway
import com.mobilebot.bridge.ShareBridge
import com.mobilebot.bridge.SmsSendResult
import com.mobilebot.bridge.TelephonyBridge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendSmsToolTest {
    @Test
    fun directSendUsesNonDeliveryConfirmedMessage() = runBlocking {
        val bridge = TestDeviceBridge(StubTelephonyBridge(SmsSendResult(success = true, sentDirectly = true)))
        val tool = SendSmsTool(bridge)

        val result = tool.execute("""{"phoneNumber":"13800138000","message":"测试"}""")

        assertTrue(result.ok)
        assertEquals(
            "SMS send requested. Android accepted the request, but delivery is not confirmed yet.",
            result.message,
        )
    }

    @Test
    fun composerFallbackReportsComposerOpened() = runBlocking {
        val bridge = TestDeviceBridge(StubTelephonyBridge(SmsSendResult(success = true, sentDirectly = false)))
        val tool = SendSmsTool(bridge)

        val result = tool.execute("""{"phoneNumber":"13800138000","message":"测试"}""")

        assertTrue(result.ok)
        assertEquals(
            "SMS composer opened. Review the draft and tap send to finish.",
            result.message,
        )
    }

    @Test
    fun missingMessageIsRejected() = runBlocking {
        val bridge = TestDeviceBridge(StubTelephonyBridge(SmsSendResult(success = true, sentDirectly = true)))
        val tool = SendSmsTool(bridge)

        val result = tool.execute("""{"phoneNumber":"13800138000","message":""}""")

        assertFalse(result.ok)
    }
}

private class StubTelephonyBridge(
    private val response: SmsSendResult,
) : TelephonyBridge {
    override fun dialNumber(phoneNumber: String): Boolean = true

    override fun openSmsComposer(
        phoneNumber: String,
        message: String,
    ): Boolean = true

    override fun sendSms(
        phoneNumber: String,
        message: String,
    ): SmsSendResult = response
}

private class TestDeviceBridge(
    override val telephony: TelephonyBridge,
) : DeviceCapabilityBridge {
    override val files: FileBridge
        get() = unsupported()
    override val notifications: NotificationBridge
        get() =
            object : NotificationBridge {
                override suspend fun listRecent(limit: Int): List<NotificationItem> = emptyList()

                override suspend fun findByPackage(
                    packageName: String,
                    limit: Int,
                ): List<NotificationItem> = emptyList()
            }
    override val accessibility: AccessibilityActionBridge
        get() = unsupported()
    override val appState: AppStateBridge
        get() =
            object : AppStateBridge {
                override suspend fun snapshot(): DeviceContextSnapshot = DeviceContextSnapshot()

                override fun observe(): Flow<DeviceContextSnapshot> = flowOf(DeviceContextSnapshot())
            }
    override val contacts: ContactsBridge
        get() = unsupported()
    override val location: LocationBridge
        get() = unsupported()
    override val media: MediaBridge
        get() = unsupported()
    override val browser: BrowserBridge
        get() = unsupported()
    override val maps: MapsBridge
        get() = unsupported()
    override val clipboard: ClipboardBridge
        get() = unsupported()
    override val share: ShareBridge
        get() = unsupported()
    override val system: com.mobilebot.bridge.SystemBridge
        get() = unsupported()
    override val services: ServiceGateway
        get() = unsupported()

    @Suppress("UNCHECKED_CAST")
    private fun <T> unsupported(): T = throw UnsupportedOperationException()
}
