package com.mobilebot.bridge.impl

import com.mobilebot.bridge.ServiceActionDescriptor
import com.mobilebot.bridge.ServiceDescriptor
import com.mobilebot.bridge.ServiceRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StubServiceGatewayTest {

    private lateinit var gateway: StubServiceGateway

    @Before
    fun setUp() {
        gateway = StubServiceGateway()
    }

    @Test
    fun registersAndListsServices() {
        assertEquals(0, gateway.listAvailableServices().size)

        gateway.registerService(createGeicoDescriptor())
        assertEquals(1, gateway.listAvailableServices().size)
        assertEquals("geico", gateway.listAvailableServices()[0].id)
    }

    @Test
    fun callsRegisteredService() = runBlocking {
        gateway.registerService(createGeicoDescriptor())
        val response = gateway.call(ServiceRequest("geico", "getPolicy", mapOf("policyId" to "123")))
        assertTrue(response.ok)
        assertTrue(response.message.contains("Stub response"))
    }

    @Test
    fun rejectsUnknownService() = runBlocking {
        val response = gateway.call(ServiceRequest("unknown", "action"))
        assertFalse(response.ok)
        assertTrue(response.message.contains("Unknown service"))
    }

    @Test
    fun rejectsUnknownAction() = runBlocking {
        gateway.registerService(createGeicoDescriptor())
        val response = gateway.call(ServiceRequest("geico", "unknownAction"))
        assertFalse(response.ok)
        assertTrue(response.message.contains("Unknown action"))
    }

    @Test
    fun multipleServicesCoexist() = runBlocking {
        gateway.registerService(createGeicoDescriptor())
        gateway.registerService(
            ServiceDescriptor(
                id = "aaa",
                name = "AAA",
                category = "automotive",
                baseUrl = "https://api.aaa.com",
                authType = "api_key",
                actions = listOf(
                    ServiceActionDescriptor("requestTow", "POST", "/tow", "Request tow"),
                ),
            ),
        )

        assertEquals(2, gateway.listAvailableServices().size)

        val geicoResp = gateway.call(ServiceRequest("geico", "getPolicy"))
        assertTrue(geicoResp.ok)

        val aaaResp = gateway.call(ServiceRequest("aaa", "requestTow"))
        assertTrue(aaaResp.ok)
    }

    private fun createGeicoDescriptor() = ServiceDescriptor(
        id = "geico",
        name = "Geico Insurance",
        category = "insurance",
        baseUrl = "https://api.geico.com/v1",
        authType = "oauth2",
        actions = listOf(
            ServiceActionDescriptor("getPolicy", "GET", "/policies/{id}", "Get policy"),
            ServiceActionDescriptor("fileClaim", "POST", "/claims", "File claim", requiresUserApproval = true),
        ),
    )
}
