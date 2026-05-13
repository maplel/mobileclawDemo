package com.mobilebot.domain.tools

import com.mobilebot.bridge.ConnectivityState
import com.mobilebot.bridge.DeviceContextSnapshot
import com.mobilebot.domain.testdoubles.AllCapabilitiesProbe
import com.mobilebot.domain.testdoubles.AlwaysForegroundReader
import com.mobilebot.domain.testdoubles.RecordingDeviceCapabilityBridge
import com.mobilebot.domain.testdoubles.NeverForegroundReader
import com.mobilebot.domain.testdoubles.NoCapabilitiesProbe
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPolicyEngineTest {

    private val defaultDefinition = ToolDefinition(
        name = "test_tool",
        description = "A test tool",
        parametersSchema = """{"type":"object"}""",
    )

    private fun makeTool(
        capabilities: Set<String> = emptySet(),
        policy: ToolExecutionPolicy = ToolExecutionPolicy(),
    ): Tool = object : Tool {
        override val name = "test_tool"
        override val definition = defaultDefinition
        override val requiredCapabilities = capabilities
        override val executionPolicy = policy
        override suspend fun execute(argumentsJson: String) = ToolResult(ok = true, message = "ok")
    }

    @Test
    fun blocksToolWhenCapabilityMissing() = runBlocking {
        val bridge = RecordingDeviceCapabilityBridge()
        val engine = ToolPolicyEngine(NoCapabilitiesProbe(), AlwaysForegroundReader(), bridge)

        val tool = makeTool(capabilities = setOf("browser.view"))
        val decision = engine.check(tool)

        assertFalse(decision.allowed)
        assertTrue(decision.reason!!.contains("capability"))
    }

    @Test
    fun blocksToolWhenForegroundRequiredButBackground() = runBlocking {
        val bridge = RecordingDeviceCapabilityBridge()
        val engine = ToolPolicyEngine(AllCapabilitiesProbe(), NeverForegroundReader(), bridge)

        val tool = makeTool(policy = ToolExecutionPolicy(requiresForeground = true))
        val decision = engine.check(tool)

        assertFalse(decision.allowed)
        assertTrue(decision.reason!!.contains("foreground"))
    }

    @Test
    fun blocksToolWhenConnectivityRequiredButOffline() = runBlocking {
        val bridge = RecordingDeviceCapabilityBridge()
        bridge.stubAppState.snapshotValue = DeviceContextSnapshot(connectivity = ConnectivityState.NONE)
        val engine = ToolPolicyEngine(AllCapabilitiesProbe(), AlwaysForegroundReader(), bridge)

        val tool = makeTool(policy = ToolExecutionPolicy(requiresConnectivity = true))
        val decision = engine.check(tool)

        assertFalse(decision.allowed)
        assertTrue(decision.reason!!.contains("network") || decision.reason!!.contains("connectivity"))
    }

    @Test
    fun allowsToolWhenAllConditionsMet() = runBlocking {
        val bridge = RecordingDeviceCapabilityBridge()
        bridge.stubAppState.snapshotValue = DeviceContextSnapshot(connectivity = ConnectivityState.WIFI)
        val engine = ToolPolicyEngine(AllCapabilitiesProbe(), AlwaysForegroundReader(), bridge)

        val tool = makeTool(
            capabilities = setOf("browser.view"),
            policy = ToolExecutionPolicy(requiresForeground = true, requiresConnectivity = true),
        )
        val decision = engine.check(tool)

        assertTrue(decision.allowed)
    }

    @Test
    fun allowsToolWithNoRequirements() = runBlocking {
        val bridge = RecordingDeviceCapabilityBridge()
        val engine = ToolPolicyEngine(AllCapabilitiesProbe(), AlwaysForegroundReader(), bridge)

        val tool = makeTool()
        val decision = engine.check(tool)

        assertTrue(decision.allowed)
    }
}
