package com.mobilebot.domain.tools

import com.mobilebot.bridge.LocationResult
import com.mobilebot.bridge.NotificationItem
import com.mobilebot.bridge.SmsSendResult
import com.mobilebot.bridge.WorkspaceFileRead
import com.mobilebot.domain.agent.CurrentSessionKeyProvider
import com.mobilebot.domain.testdoubles.AllCapabilitiesProbe
import com.mobilebot.domain.testdoubles.AlwaysForegroundReader
import com.mobilebot.domain.testdoubles.RecordingDeviceCapabilityBridge
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ToolRegistryIntegrationTest {

    private lateinit var bridge: RecordingDeviceCapabilityBridge
    private lateinit var registry: ToolRegistry

    @Before
    fun setUp() {
        bridge = RecordingDeviceCapabilityBridge()
        val probe = AllCapabilitiesProbe()
        val policyEngine = ToolPolicyEngine(probe, AlwaysForegroundReader(), bridge)
        val sessionKeyProvider = CurrentSessionKeyProvider()
        val tools = setOf<Tool>(
            OpenUrlTool(bridge, sessionKeyProvider),
            OpenMapTool(bridge),
            SearchContactsTool(bridge),
            DialNumberTool(bridge),
            SendSmsTool(bridge),
            GetCurrentLocationTool(bridge),
            OpenCameraTool(bridge, sessionKeyProvider),
            CopyToClipboardTool(bridge),
            ShareTextTool(bridge),
            ReadSandboxFileTool(bridge),
            ListNotificationsTool(bridge),
        )
        registry = ToolRegistry(tools, probe, policyEngine)
    }

    @Test
    fun openUrl_callsBrowserBridgeWithCorrectUrl() = runBlocking {
        val result = registry.execute("open_url", """{"url":"https://www.baidu.com"}""")
        assertTrue(result.ok)
        assertEquals(1, bridge.recordingBrowser.openedUrls.size)
        assertEquals("https://www.baidu.com", bridge.recordingBrowser.openedUrls[0])
    }

    @Test
    fun openUrl_failsWithMissingUrl() = runBlocking {
        val result = registry.execute("open_url", """{"url":""}""")
        assertTrue(!result.ok)
        assertTrue(result.message.contains("Missing"))
    }

    @Test
    fun openMap_callsMapsBridgeWithQueryAndMode() = runBlocking {
        val result = registry.execute("open_map", """{"query":"北京天安门","mode":"search"}""")
        assertTrue(result.ok)
        assertEquals(1, bridge.recordingMaps.calls.size)
        assertEquals("北京天安门", bridge.recordingMaps.calls[0].query)
        assertEquals("search", bridge.recordingMaps.calls[0].mode)
    }

    @Test
    fun openMap_navigateMode() = runBlocking {
        val result = registry.execute("open_map", """{"query":"上海外滩","mode":"navigate"}""")
        assertTrue(result.ok)
        assertEquals("navigate", bridge.recordingMaps.calls[0].mode)
    }

    @Test
    fun searchContacts_returnsFormattedResults() = runBlocking {
        bridge.recordingContacts.results = listOf("张三|13800138000", "张四|13900139000")
        val result = registry.execute("search_contacts", """{"query":"张"}""")
        assertTrue(result.ok)
        assertTrue(result.message.contains("2"))
        val data = result.dataJson!!
        assertTrue(data.contains("张三"))
        assertTrue(data.contains("13800138000"))
    }

    @Test
    fun searchContacts_emptyResult() = runBlocking {
        bridge.recordingContacts.results = emptyList()
        val result = registry.execute("search_contacts", """{"query":"不存在"}""")
        assertTrue(result.ok)
        assertTrue(result.message.contains("No contacts"))
    }

    @Test
    fun dialNumber_callsTelephonyBridge() = runBlocking {
        val result = registry.execute("dial_number", """{"phoneNumber":"10086"}""")
        assertTrue(result.ok)
        assertEquals(1, bridge.recordingTelephony.dialedNumbers.size)
        assertEquals("10086", bridge.recordingTelephony.dialedNumbers[0])
    }

    @Test
    fun sendSms_withPermission_sendsDirectly() = runBlocking {
        bridge.recordingTelephony.smsResult = SmsSendResult(success = true, sentDirectly = true)
        val result = registry.execute("send_sms", """{"phoneNumber":"13800138000","message":"测试消息"}""")
        assertTrue(result.ok)
        assertTrue(result.message.contains("send requested"))
        assertEquals(1, bridge.recordingTelephony.sentSms.size)
        assertEquals("13800138000", bridge.recordingTelephony.sentSms[0].phone)
        assertEquals("测试消息", bridge.recordingTelephony.sentSms[0].message)
    }

    @Test
    fun sendSms_withoutPermission_opensDraft() = runBlocking {
        bridge.recordingTelephony.smsResult = SmsSendResult(success = true, sentDirectly = false)
        val result = registry.execute("send_sms", """{"phoneNumber":"13800138000","message":"你好"}""")
        assertTrue(result.ok)
        assertTrue(result.message.contains("composer") || result.message.contains("SMS"))
    }

    @Test
    fun sendSms_failsWithMissingPhone() = runBlocking {
        val result = registry.execute("send_sms", """{"message":"hello"}""")
        assertTrue(!result.ok)
        assertTrue(result.message.contains("Missing"))
    }

    @Test
    fun getCurrentLocation_returnsCoordinates() = runBlocking {
        bridge.recordingLocation.coarseResult = LocationResult(39.9042, 116.4074, null)
        val result = registry.execute("get_current_location", """{}""")
        assertTrue(result.ok)
        val data = result.dataJson!!
        assertTrue(data.contains("39.9042"))
        assertTrue(data.contains("116.4074"))
    }

    @Test
    fun getCurrentLocation_handlesError() = runBlocking {
        bridge.recordingLocation.coarseResult = LocationResult(null, null, "Permission denied")
        val result = registry.execute("get_current_location", """{}""")
        assertTrue(!result.ok)
        assertTrue(result.message.contains("Permission denied"))
    }

    @Test
    fun openCamera_callsMediaBridge() = runBlocking {
        bridge.recordingMedia.returnMessage = "Camera opened."
        val result = registry.execute("open_camera", """{}""")
        assertTrue(result.ok)
        assertEquals(1, bridge.recordingMedia.callCount)
    }

    @Test
    fun copyToClipboard_callsClipboardBridge() = runBlocking {
        val result = registry.execute("copy_to_clipboard", """{"text":"hello world"}""")
        assertTrue(result.ok)
        assertEquals(1, bridge.recordingClipboard.copiedTexts.size)
        assertEquals("hello world", bridge.recordingClipboard.copiedTexts[0])
    }

    @Test
    fun shareText_callsShareBridge() = runBlocking {
        val result = registry.execute("share_text", """{"text":"今天天气真好"}""")
        assertTrue(result.ok)
        assertEquals(1, bridge.recordingShare.sharedTexts.size)
        assertEquals("今天天气真好", bridge.recordingShare.sharedTexts[0])
    }

    @Test
    fun readSandboxFile_returnsFileContent() = runBlocking {
        bridge.recordingFiles.fileContent = WorkspaceFileRead(text = "shared content", truncated = false)
        val result = registry.execute("read_sandbox_file", """{"path":"shared/incoming.txt"}""")
        assertTrue(result.ok)
        assertTrue(result.message.contains("shared content"))
        assertEquals(1, bridge.recordingFiles.reads.size)
        assertEquals("shared/incoming.txt", bridge.recordingFiles.reads[0].path)
    }

    @Test
    fun readSandboxFile_handlesError() = runBlocking {
        bridge.recordingFiles.fileContent = WorkspaceFileRead(text = "", error = "File not found")
        val result = registry.execute("read_sandbox_file", """{"path":"nonexistent.txt"}""")
        assertTrue(!result.ok)
        assertTrue(result.message.contains("File not found"))
    }

    @Test
    fun listNotifications_returnsItems() = runBlocking {
        bridge.recordingNotifications.recentItems = listOf(
            NotificationItem("1", "com.example", "Title", "Body text", System.currentTimeMillis()),
        )
        val result = registry.execute("list_notifications", """{"limit":10}""")
        assertTrue(result.ok)
        assertTrue(result.message.contains("Title"))
        assertTrue(result.message.contains("Body text"))
    }

    @Test
    fun listNotifications_emptyCache() = runBlocking {
        bridge.recordingNotifications.recentItems = emptyList()
        val result = registry.execute("list_notifications", """{}""")
        assertTrue(result.ok)
        assertTrue(result.message.contains("No cached"))
    }

    @Test
    fun unknownTool_returnsError() = runBlocking {
        val result = registry.execute("nonexistent_tool", """{}""")
        assertTrue(!result.ok)
        assertTrue(result.message.contains("unknown tool"))
    }
}
