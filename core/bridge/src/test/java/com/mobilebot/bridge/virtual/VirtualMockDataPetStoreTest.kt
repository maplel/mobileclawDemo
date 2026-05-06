package com.mobilebot.bridge.virtual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualMockDataPetStoreTest {
    @Test
    fun petStoreMockReturnsAllCoreActionResponses() {
        val actions = listOf(
            "list_locations",
            "list_services",
            "list_products",
            "query_inventory",
            "list_promotions",
            "get_store_calendar",
            "query_availability",
            "create_booking",
            "modify_booking",
            "cancel_booking",
            "create_order",
            "modify_order",
            "cancel_order",
            "get_status",
        )

        for (action in actions) {
            assertNotNull("Expected mock response for $action", VirtualMockData.lookup("pet_store_mock", action))
        }
    }

    @Test
    fun queryAvailabilityExcludesBlockedSlots() {
        val response = VirtualMockData.lookup(
            "pet_store_mock",
            "query_availability",
            mapOf(
                "store_id" to "pet_store_nanshan_001",
                "service_id" to "dog_bath_teeth",
                "date" to "2026-05-04",
            ),
        )

        requireNotNull(response)
        @Suppress("UNCHECKED_CAST")
        val slots = response["availableSlots"] as List<Map<String, Any>>
        val starts = slots.map { it["start"].toString() }
        assertFalse(starts.any { it.contains("12:30") || it.contains("16:00") || it.contains("18:00") })
    }

    @Test
    fun petTransportMockSupportsLifecycleActions() {
        val estimate = VirtualMockData.lookup("pet_transport_mock", "estimate_transport")
        val request = VirtualMockData.lookup("pet_transport_mock", "request_transport")
        val modify = VirtualMockData.lookup("pet_transport_mock", "modify_transport")
        val cancel = VirtualMockData.lookup("pet_transport_mock", "cancel_transport")
        val status = VirtualMockData.lookup("pet_transport_mock", "get_transport_status")

        assertEquals(36, estimate?.get("estimatedFee"))
        assertEquals("requested", request?.get("status"))
        assertEquals("modified", modify?.get("status"))
        assertEquals("cancelled", cancel?.get("status"))
        assertTrue(status?.get("estimatedArrivalMinutes") is Int)
    }
}
