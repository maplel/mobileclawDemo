package com.mobilebot.bridge.virtual

import com.mobilebot.bridge.ServiceActionDescriptor
import com.mobilebot.bridge.ServiceDescriptor
import com.mobilebot.bridge.ServiceRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualServiceGatewayPetAliasTest {
    @Test
    fun mapsPetStoreConfirmAliasToCreateBooking() = runBlocking {
        val gateway = VirtualServiceGateway()
        gateway.registerService(petStoreDescriptor())

        val response = gateway.call(
            ServiceRequest(
                serviceId = "pet_store_mock",
                action = "confirm",
                params = mapOf("pet_name" to "元宝"),
            ),
        )

        assertTrue(response.ok)
        assertEquals("create_booking", response.data["_action"])
        assertEquals("confirm", response.data["_requestedAction"])
    }

    @Test
    fun mapsPetTransportConfirmAliasToRequestTransport() = runBlocking {
        val gateway = VirtualServiceGateway()
        gateway.registerService(petTransportDescriptor())

        val response = gateway.call(
            ServiceRequest(
                serviceId = "pet_transport_mock",
                action = "confirm",
                params = mapOf("pickup_address" to "深圳市南山区"),
            ),
        )

        assertTrue(response.ok)
        assertEquals("request_transport", response.data["_action"])
        assertEquals("confirm", response.data["_requestedAction"])
    }

    private fun petStoreDescriptor() = ServiceDescriptor(
        id = "pet_store_mock",
        name = "Mock Pet Store Service",
        category = "life-service",
        baseUrl = "https://mock.petstore.local/api/v1",
        authType = "none",
        actions = listOf(
            ServiceActionDescriptor("create_booking", "POST", "/bookings", "Create a grooming booking"),
            ServiceActionDescriptor("create_order", "POST", "/orders", "Create a product order"),
        ),
    )

    private fun petTransportDescriptor() = ServiceDescriptor(
        id = "pet_transport_mock",
        name = "Mock Pet Transport Service",
        category = "life-service",
        baseUrl = "https://mock.pettransport.local/api/v1",
        authType = "none",
        actions = listOf(
            ServiceActionDescriptor("request_transport", "POST", "/transports", "Request pet transport"),
        ),
    )
}
