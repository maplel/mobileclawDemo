package com.mobilebot.domain.tools

import com.mobilebot.bridge.ServiceActionDescriptor
import com.mobilebot.bridge.ServiceDescriptor
import com.mobilebot.bridge.ServiceGateway
import com.mobilebot.bridge.ServiceRequest
import com.mobilebot.bridge.ServiceResponse
import com.mobilebot.bridge.DeviceCapabilityBridge
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CallServiceToolTest {

    private lateinit var gateway: InMemoryGateway
    private lateinit var tool: CallServiceTool

    @Before
    fun setUp() {
        gateway = InMemoryGateway()
        tool = CallServiceTool(MinimalBridge(gateway))
    }

    @Test
    fun toolNameIsCallService() {
        assertTrue(tool.name == "call_service")
    }

    @Test
    fun rejectsEmptyServiceId() = runBlocking {
        val result = tool.execute("""{"serviceId":"","action":"getPolicy"}""")
        assertFalse(result.ok)
    }

    @Test
    fun rejectsUnknownService() = runBlocking {
        val result = tool.execute("""{"serviceId":"nonexistent","action":"getPolicy"}""")
        assertFalse(result.ok)
    }

    @Test
    fun callsRegisteredServiceSuccessfully() = runBlocking {
        gateway.addService(
            ServiceDescriptor(
                id = "geico",
                name = "Geico",
                category = "insurance",
                baseUrl = "https://api.geico.com",
                authType = "oauth2",
                actions = listOf(
                    ServiceActionDescriptor("getPolicy", "GET", "/policies", "Get policy"),
                ),
            ),
        )
        val result = tool.execute("""{"serviceId":"geico","action":"getPolicy"}""")
        assertTrue("Call should succeed: ${result.message}", result.ok)
    }

    @Test
    fun rejectsUnknownAction() = runBlocking {
        gateway.addService(
            ServiceDescriptor(
                id = "geico",
                name = "Geico",
                category = "insurance",
                baseUrl = "https://api.geico.com",
                authType = "oauth2",
                actions = listOf(
                    ServiceActionDescriptor("getPolicy", "GET", "/policies", "Get policy"),
                ),
            ),
        )
        val result = tool.execute("""{"serviceId":"geico","action":"nonexistent"}""")
        assertFalse(result.ok)
    }
}

private class InMemoryGateway : ServiceGateway {
    private val services = mutableMapOf<String, ServiceDescriptor>()

    fun addService(desc: ServiceDescriptor) {
        services[desc.id] = desc
    }

    override suspend fun call(request: ServiceRequest): ServiceResponse {
        val desc = services[request.serviceId]
            ?: return ServiceResponse(ok = false, message = "Unknown service: ${request.serviceId}")
        val action = desc.actions.find { it.name == request.action }
            ?: return ServiceResponse(ok = false, message = "Unknown action: ${request.action}")
        return ServiceResponse(ok = true, message = "OK", data = mapOf("action" to action.name))
    }

    override fun listAvailableServices() = services.values.toList()
    override fun isServiceAuthorized(serviceId: String) = serviceId in services
    override fun registerService(descriptor: ServiceDescriptor) {
        services[descriptor.id] = descriptor
    }
}

private class MinimalBridge(private val gw: ServiceGateway) : DeviceCapabilityBridge {
    override val files get() = throw UnsupportedOperationException()
    override val notifications get() = throw UnsupportedOperationException()
    override val accessibility get() = throw UnsupportedOperationException()
    override val appState get() = throw UnsupportedOperationException()
    override val contacts get() = throw UnsupportedOperationException()
    override val location get() = throw UnsupportedOperationException()
    override val media get() = throw UnsupportedOperationException()
    override val browser get() = throw UnsupportedOperationException()
    override val maps get() = throw UnsupportedOperationException()
    override val clipboard get() = throw UnsupportedOperationException()
    override val share get() = throw UnsupportedOperationException()
    override val telephony get() = throw UnsupportedOperationException()
    override val system get() = throw UnsupportedOperationException()
    override val services: ServiceGateway = gw
}
