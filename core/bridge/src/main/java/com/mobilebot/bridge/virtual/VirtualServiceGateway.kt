package com.mobilebot.bridge.virtual

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.mobilebot.bridge.ServiceDescriptor
import com.mobilebot.bridge.ServiceGateway
import com.mobilebot.bridge.ServiceRequest
import com.mobilebot.bridge.ServiceResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VirtualServiceGateway
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ServiceGateway {
        private val services = ConcurrentHashMap<String, ServiceDescriptor>()

        private val prefs: SharedPreferences by lazy {
            context.getSharedPreferences("virtual_service_data", Context.MODE_PRIVATE)
        }

        private fun loadMapList(key: String): MutableList<MutableMap<String, Any>> {
            val json = prefs.getString(key, null) ?: return mutableListOf()
            val arr = JSONArray(json)
            val result = mutableListOf<MutableMap<String, Any>>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val map = mutableMapOf<String, Any>()
                for (k in obj.keys()) {
                    val v = obj.get(k)
                    when (v) {
                        is Boolean -> map[k] = v
                        is Int -> map[k] = v
                        is Long -> map[k] = v
                        is Double -> map[k] = v
                        is String -> map[k] = v
                        else -> map[k] = v.toString()
                    }
                }
                result.add(map)
            }
            return result
        }

        private fun saveMapList(key: String, list: List<Map<String, Any>>) {
            val arr = JSONArray()
            for (map in list) {
                val obj = JSONObject()
                for ((k, v) in map) {
                    obj.put(k, v)
                }
                arr.put(obj)
            }
            prefs.edit().putString(key, arr.toString()).apply()
        }

        private fun loadAddresses(): MutableList<MutableMap<String, Any>> = loadMapList("saved_addresses")

        private fun saveAddresses(addresses: List<Map<String, Any>>) = saveMapList("saved_addresses", addresses)

        private fun loadPetProfiles(): MutableList<MutableMap<String, Any>> = loadMapList("saved_pet_profiles")

        private fun savePetProfiles(pets: List<Map<String, Any>>) = saveMapList("saved_pet_profiles", pets)

        override suspend fun call(request: ServiceRequest): ServiceResponse {
            val descriptor = services[request.serviceId]
                ?: return ServiceResponse(
                    ok = false,
                    message = "Unknown service: ${request.serviceId}",
                )

            val effectiveAction = normalizeAction(request.serviceId, request.action, request.params)
            val action = descriptor.actions.find { it.name == effectiveAction }
                ?: return ServiceResponse(
                    ok = false,
                    message = "Unknown action '${request.action}' for service '${request.serviceId}'. Available actions: ${descriptor.actions.joinToString { it.name }}",
                )

            val persistedResponse = handlePersistedAction(request.serviceId, effectiveAction, request.params)
            if (persistedResponse != null) {
                Log.d(TAG, "[VIRTUAL] call(${request.serviceId}.$effectiveAction) -> persisted data")
                return ServiceResponse(
                    ok = true,
                    data = persistedResponse + mapOf(
                        "_virtual" to true,
                        "_serviceId" to request.serviceId,
                        "_action" to effectiveAction,
                        "_requestedAction" to request.action,
                    ),
                    message = "Virtual persisted response for ${request.serviceId}.$effectiveAction",
                )
            }

            val mockData = VirtualMockData.lookup(request.serviceId, effectiveAction, request.params)
            if (mockData != null) {
                Log.d(TAG, "[VIRTUAL] call(${request.serviceId}.$effectiveAction) -> mock data with ${mockData.size} fields")
                return ServiceResponse(
                    ok = true,
                    data = mockData + mapOf(
                        "_virtual" to true,
                        "_serviceId" to request.serviceId,
                        "_action" to effectiveAction,
                        "_requestedAction" to request.action,
                    ),
                    message = "Virtual response for ${request.serviceId}.$effectiveAction",
                )
            }

            Log.d(TAG, "[VIRTUAL] call(${request.serviceId}.$effectiveAction) -> no mock data, echoing params")
            return ServiceResponse(
                ok = true,
                data = mapOf(
                    "serviceId" to request.serviceId,
                    "action" to effectiveAction,
                    "requestedAction" to request.action,
                    "actionDescription" to action.description,
                    "params" to request.params,
                    "_virtual" to true,
                ),
                message = "Virtual echo for ${request.serviceId}.$effectiveAction (no specific mock data)",
            )
        }

        @Suppress("UNCHECKED_CAST")
        private fun handlePersistedAction(
            serviceId: String,
            action: String,
            params: Map<String, Any?>,
        ): Map<String, Any>? {
            if (serviceId != "pet_store_mock") return null

            when (action) {
                "list_user_addresses" -> {
                    val addresses = loadAddresses().map { it.toMap() }
                    Log.d(TAG, "[VIRTUAL] list_user_addresses -> ${addresses.size} addresses from persistent storage")
                    return mapOf(
                        "addresses" to addresses,
                        "total" to addresses.size,
                    )
                }
                "save_user_address" -> {
                    val address = params["address"]?.toString()
                        ?: params["pickup_address"]?.toString()
                        ?: params["pickupAddress"]?.toString()
                        ?: ""
                    val label = params["label"]?.toString()
                        ?: params["address_label"]?.toString()
                        ?: "Default pet pickup address"
                    val isDefault = params["is_default"] ?: params["isDefault"] ?: true
                    val addressId = params["address_id"]?.toString()
                        ?: params["addressId"]?.toString()
                        ?: "addr_${System.currentTimeMillis()}"
                    val addresses = loadAddresses()
                    if (isDefault == true) {
                        for (existing in addresses) {
                            existing["is_default"] = false
                        }
                    }
                    val existingIdx = addresses.indexOfFirst { it["address_id"] == addressId }
                    val entry = mutableMapOf<String, Any>(
                        "address_id" to addressId,
                        "label" to label,
                        "address" to address,
                        "is_default" to isDefault,
                    )
                    if (existingIdx >= 0) {
                        addresses[existingIdx] = entry
                    } else {
                        addresses.add(entry)
                    }
                    saveAddresses(addresses)
                    Log.d(TAG, "[VIRTUAL] Saved address: $addressId -> $address (default=$isDefault, total=${addresses.size})")
                    return mapOf(
                        "address_id" to addressId,
                        "status" to "saved",
                        "label" to label,
                        "address" to address,
                        "is_default" to isDefault,
                        "summary" to "Address saved successfully",
                    )
                }
                "list_pet_profiles" -> {
                    val pets = loadPetProfiles().map { it.toMap() }
                    Log.d(TAG, "[VIRTUAL] list_pet_profiles -> ${pets.size} pets from persistent storage")
                    return mapOf(
                        "pets" to pets,
                        "total" to pets.size,
                    )
                }
                "save_pet_profile" -> {
                    val petName = params["pet_name"]?.toString()
                        ?: params["petName"]?.toString()
                        ?: params["name"]?.toString()
                        ?: "default_pet"
                    val petType = params["pet_type"]?.toString()
                        ?: params["petType"]?.toString()
                        ?: "dog"
                    val petId = params["pet_id"]?.toString()
                        ?: params["petId"]?.toString()
                        ?: "pet_${petName.hashCode().toUInt().toString(16)}"
                    val pets = loadPetProfiles()
                    val existingIdx = pets.indexOfFirst { it["pet_id"] == petId }
                    val existing = if (existingIdx >= 0) pets[existingIdx] else null
                    val entry = mutableMapOf<String, Any>(
                        "pet_id" to petId,
                        "name" to petName,
                        "type" to petType,
                    )
                    if (existing != null) {
                        entry.putAll(existing)
                    }
                    for ((key, value) in params) {
                        if (value != null && key !in listOf("pet_id", "petId", "pet_name", "petName", "name", "pet_type", "petType")) {
                            entry[key] = value
                        }
                    }
                    entry["name"] = petName
                    entry["type"] = petType
                    if (existingIdx >= 0) {
                        pets[existingIdx] = entry
                    } else {
                        pets.add(entry)
                    }
                    savePetProfiles(pets)
                    Log.d(TAG, "[VIRTUAL] Saved pet profile: $petId -> $petName ($petType, total=${pets.size})")
                    return mapOf(
                        "pet_id" to petId,
                        "status" to "saved",
                        "pet" to entry.toMap(),
                        "summary" to "Pet profile saved successfully",
                    )
                }
                else -> return null
            }
        }

        override fun listAvailableServices(): List<ServiceDescriptor> =
            services.values.toList()

        override fun isServiceAuthorized(serviceId: String): Boolean =
            services.containsKey(serviceId)

        override fun registerService(descriptor: ServiceDescriptor) {
            services[descriptor.id] = descriptor
            Log.d(TAG, "Registered service: ${descriptor.id} (${descriptor.actions.size} actions)")
        }

        private fun normalizeAction(serviceId: String, action: String, params: Map<String, Any?>): String =
            when (serviceId) {
                "pet_store_mock" -> when (action) {
                    "reserve", "book", "confirm", "confirm_booking" ->
                        if (params.containsKey("product_id") || params.containsKey("productId")) {
                            "create_order"
                        } else {
                            "create_booking"
                        }
                    else -> action
                }
                "pet_transport_mock" -> when (action) {
                    "reserve", "book", "confirm", "confirm_transport" -> "request_transport"
                    else -> action
                }
                else -> action
            }

        private companion object {
            private const val TAG = "VirtualServiceGateway"
        }
    }
