package com.mobilebot.bridge.virtual

import com.mobilebot.bridge.ServiceActionDescriptor
import com.mobilebot.bridge.ServiceDescriptor
import com.mobilebot.bridge.ServiceRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VirtualServiceGatewayPersistenceTest {

    private lateinit var gateway: VirtualServiceGateway

    @Before
    fun setUp() {
        gateway = VirtualServiceGateway()
        gateway.registerService(
            ServiceDescriptor(
                id = "pet_store_mock",
                name = "Mock Pet Store",
                category = "life-service",
                baseUrl = "https://mock.petstore.local",
                authType = "none",
                actions = listOf(
                    ServiceActionDescriptor("list_user_addresses", "GET", "/user/addresses", "List addresses"),
                    ServiceActionDescriptor("save_user_address", "POST", "/user/addresses", "Save address"),
                    ServiceActionDescriptor("list_pet_profiles", "GET", "/pets", "List pets"),
                    ServiceActionDescriptor("save_pet_profile", "POST", "/pets", "Save pet"),
                    ServiceActionDescriptor("list_locations", "GET", "/locations", "List locations"),
                ),
            ),
        )
    }

    @Test
    fun listUserAddressesReturnsEmptyInitially() = runBlocking {
        val response = gateway.call(ServiceRequest("pet_store_mock", "list_user_addresses"))
        assertTrue(response.ok)
        @Suppress("UNCHECKED_CAST")
        val addresses = response.data?.get("addresses") as List<*>
        assertEquals(0, addresses.size)
    }

    @Test
    fun saveAndListUserAddresses() = runBlocking {
        gateway.call(ServiceRequest("pet_store_mock", "save_user_address", mapOf(
            "address" to "深圳市南山区科苑南路2666号",
            "label" to "家",
            "is_default" to true,
        )))

        val response = gateway.call(ServiceRequest("pet_store_mock", "list_user_addresses"))
        assertTrue(response.ok)
        @Suppress("UNCHECKED_CAST")
        val addresses = response.data?.get("addresses") as List<Map<String, Any>>
        assertEquals(1, addresses.size)
        assertEquals("深圳市南山区科苑南路2666号", addresses[0]["address"])
        assertEquals("家", addresses[0]["label"])
        assertEquals(true, addresses[0]["is_default"])
    }

    @Test
    fun saveMultipleAddressesAndDefaultToggle() = runBlocking {
        gateway.call(ServiceRequest("pet_store_mock", "save_user_address", mapOf(
            "address" to "地址A",
            "label" to "家",
            "is_default" to true,
        )))
        gateway.call(ServiceRequest("pet_store_mock", "save_user_address", mapOf(
            "address" to "地址B",
            "label" to "公司",
            "is_default" to true,
        )))

        val response = gateway.call(ServiceRequest("pet_store_mock", "list_user_addresses"))
        @Suppress("UNCHECKED_CAST")
        val addresses = response.data?.get("addresses") as List<Map<String, Any>>
        assertEquals(2, addresses.size)
        val defaults = addresses.filter { it["is_default"] == true }
        assertEquals(1, defaults.size)
        assertEquals("地址B", defaults[0]["address"])
    }

    @Test
    fun listPetProfilesReturnsEmptyInitially() = runBlocking {
        val response = gateway.call(ServiceRequest("pet_store_mock", "list_pet_profiles"))
        assertTrue(response.ok)
        @Suppress("UNCHECKED_CAST")
        val pets = response.data?.get("pets") as List<*>
        assertEquals(0, pets.size)
    }

    @Test
    fun saveAndListPetProfiles() = runBlocking {
        gateway.call(ServiceRequest("pet_store_mock", "save_pet_profile", mapOf(
            "pet_name" to "元宝",
            "pet_type" to "dog",
            "breed" to "金毛",
            "size" to "中型犬",
        )))

        val response = gateway.call(ServiceRequest("pet_store_mock", "list_pet_profiles"))
        assertTrue(response.ok)
        @Suppress("UNCHECKED_CAST")
        val pets = response.data?.get("pets") as List<Map<String, Any>>
        assertEquals(1, pets.size)
        assertEquals("元宝", pets[0]["name"])
        assertEquals("dog", pets[0]["type"])
        assertEquals("金毛", pets[0]["breed"])
        assertEquals("中型犬", pets[0]["size"])
    }

    @Test
    fun saveMultiplePetProfiles() = runBlocking {
        gateway.call(ServiceRequest("pet_store_mock", "save_pet_profile", mapOf(
            "pet_name" to "元宝",
            "pet_type" to "dog",
        )))
        gateway.call(ServiceRequest("pet_store_mock", "save_pet_profile", mapOf(
            "pet_name" to "咪咪",
            "pet_type" to "cat",
        )))

        val response = gateway.call(ServiceRequest("pet_store_mock", "list_pet_profiles"))
        @Suppress("UNCHECKED_CAST")
        val pets = response.data?.get("pets") as List<Map<String, Any>>
        assertEquals(2, pets.size)
        val names = pets.map { it["name"].toString() }.sorted()
        assertTrue(names.contains("元宝"))
        assertTrue(names.contains("咪咪"))
    }

    @Test
    fun savePetProfileMergesExisting() = runBlocking {
        val saveResp = gateway.call(ServiceRequest("pet_store_mock", "save_pet_profile", mapOf(
            "pet_name" to "元宝",
            "pet_type" to "dog",
        )))
        val petId = saveResp.data?.get("pet_id").toString()

        gateway.call(ServiceRequest("pet_store_mock", "save_pet_profile", mapOf(
            "pet_id" to petId,
            "pet_name" to "元宝",
            "pet_type" to "dog",
            "breed" to "金毛",
        )))

        val response = gateway.call(ServiceRequest("pet_store_mock", "list_pet_profiles"))
        @Suppress("UNCHECKED_CAST")
        val pets = response.data?.get("pets") as List<Map<String, Any>>
        assertEquals(1, pets.size)
        assertEquals("元宝", pets[0]["name"])
        assertEquals("金毛", pets[0]["breed"])
    }

    @Test
    fun persistedActionsDoNotAffectOtherActions() = runBlocking {
        val response = gateway.call(ServiceRequest("pet_store_mock", "list_locations"))
        assertTrue(response.ok)
        assertTrue((response.data?.get("locations") as? List<*>)?.isNotEmpty() == true)
    }
}
