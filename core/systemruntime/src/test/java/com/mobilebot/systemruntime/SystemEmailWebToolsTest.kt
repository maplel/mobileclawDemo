package com.mobilebot.systemruntime

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemEmailWebToolsTest {
    @Test
    fun sendEmailToolRoutesToRuntimeAction() = runBlocking {
        val runtime = RecordingRuntime()
        val result = SystemSendEmailTool(runtime).execute(
            """{"to":"lee@example.com","subject":"Update","body":"Done"}""",
        )

        assertTrue(result.ok)
        assertEquals("send_email", runtime.action)
        assertEquals("lee@example.com", runtime.params?.optString("to"))
        assertEquals("Update", runtime.params?.optString("subject"))
    }

    @Test
    fun sendEmailToolRejectsMissingRequiredFields() = runBlocking {
        val result = SystemSendEmailTool(RecordingRuntime()).execute("""{"to":"lee@example.com"}""")

        assertFalse(result.ok)
        assertTrue(result.message.contains("subject", ignoreCase = true))
    }

    @Test
    fun queryWebToolRoutesToRuntimeAction() = runBlocking {
        val runtime = RecordingRuntime()
        val result = SystemQueryWebTool(runtime).execute("""{"query":"weather near home"}""")

        assertTrue(result.ok)
        assertEquals("query_web", runtime.action)
        assertEquals("weather near home", runtime.params?.optString("query"))
    }

    private class RecordingRuntime : SystemRuntimeActions {
        var action: String? = null
        var params: JSONObject? = null

        override suspend fun execute(
            action: String,
            params: JSONObject,
        ): SystemRuntimeResult {
            this.action = action
            this.params = params
            return SystemRuntimeResult(true, "ok", mapOf("action" to action))
        }
    }
}
