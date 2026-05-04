package com.mobilebot.bridge.impl

import com.mobilebot.bridge.ServiceDescriptor
import com.mobilebot.bridge.ServiceGateway
import com.mobilebot.bridge.ServiceRequest
import com.mobilebot.bridge.ServiceResponse
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory stub for development / testing.
 * Returns a synthetic response echoing the request parameters.
 */
@Singleton
class StubServiceGateway
    @Inject
    constructor() : ServiceGateway {
        private val services = ConcurrentHashMap<String, ServiceDescriptor>()
        private val authorized = ConcurrentHashMap<String, Boolean>()

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
            return ServiceResponse(
                ok = true,
                data = mapOf(
                    "serviceId" to request.serviceId,
                    "action" to request.action,
                    "actionDescription" to action.description,
                    "params" to request.params,
                    "stub" to true,
                ),
                message = "Stub response for ${request.serviceId}.${request.action}",
            )
        }

        override fun listAvailableServices(): List<ServiceDescriptor> = services.values.toList()

        override fun isServiceAuthorized(serviceId: String): Boolean =
            authorized[serviceId] ?: true

        override fun registerService(descriptor: ServiceDescriptor) {
            services[descriptor.id] = descriptor
        }

        fun authorizeService(serviceId: String, allow: Boolean) {
            authorized[serviceId] = allow
        }
    }
