package com.mobilebot.bridge.virtual

import android.util.Log
import com.mobilebot.bridge.ServiceDescriptor
import com.mobilebot.bridge.ServiceGateway
import com.mobilebot.bridge.ServiceRequest
import com.mobilebot.bridge.ServiceResponse
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Virtual [ServiceGateway] that returns realistic mock data
 * per service and per action from [VirtualMockData].
 *
 * Services are still registered via [registerService] (from JSON configs at startup),
 * so action validation works identically to [com.mobilebot.bridge.impl.HttpServiceGateway].
 */
@Singleton
class VirtualServiceGateway
    @Inject
    constructor() : ServiceGateway {
        private val services = ConcurrentHashMap<String, ServiceDescriptor>()

        override suspend fun call(request: ServiceRequest): ServiceResponse {
            val descriptor = services[request.serviceId]
                ?: return ServiceResponse(
                    ok = false,
                    message = "Unknown service: ${request.serviceId}",
                )

            val action = descriptor.actions.find { it.name == request.action }
                ?: return ServiceResponse(
                    ok = false,
                    message = "Unknown action '${request.action}' for service '${request.serviceId}'",
                )

            val mockData = VirtualMockData.lookup(request.serviceId, request.action, request.params)
            if (mockData != null) {
                Log.d(TAG, "[VIRTUAL] call(${request.serviceId}.${request.action}) -> mock data with ${mockData.size} fields")
                return ServiceResponse(
                    ok = true,
                    data = mockData + mapOf(
                        "_virtual" to true,
                        "_serviceId" to request.serviceId,
                        "_action" to request.action,
                    ),
                    message = "Virtual response for ${request.serviceId}.${request.action}",
                )
            }

            Log.d(TAG, "[VIRTUAL] call(${request.serviceId}.${request.action}) -> no mock data, echoing params")
            return ServiceResponse(
                ok = true,
                data = mapOf(
                    "serviceId" to request.serviceId,
                    "action" to request.action,
                    "actionDescription" to action.description,
                    "params" to request.params,
                    "_virtual" to true,
                ),
                message = "Virtual echo for ${request.serviceId}.${request.action} (no specific mock data)",
            )
        }

        override fun listAvailableServices(): List<ServiceDescriptor> =
            services.values.toList()

        override fun isServiceAuthorized(serviceId: String): Boolean =
            services.containsKey(serviceId)

        override fun registerService(descriptor: ServiceDescriptor) {
            services[descriptor.id] = descriptor
            Log.d(TAG, "Registered service: ${descriptor.id} (${descriptor.actions.size} actions)")
        }

        private companion object {
            private const val TAG = "VirtualServiceGateway"
        }
    }
