package com.mobilebot.bridge.impl

import android.util.Log
import com.mobilebot.bridge.ServiceDescriptor
import com.mobilebot.bridge.ServiceGateway
import com.mobilebot.bridge.ServiceRequest
import com.mobilebot.bridge.ServiceResponse
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production gateway that constructs HTTP requests based on declarative
 * service JSON configs. Currently delegates to [StubServiceGateway] until
 * real HTTP plumbing is wired up.
 */
@Singleton
class HttpServiceGateway
    @Inject
    constructor() : ServiceGateway {
        private val services = ConcurrentHashMap<String, ServiceDescriptor>()
        private val authorized = ConcurrentHashMap.newKeySet<String>()

        override suspend fun call(request: ServiceRequest): ServiceResponse {
            val descriptor = services[request.serviceId]
                ?: return ServiceResponse(ok = false, message = "Unknown service: ${request.serviceId}")

            if (request.serviceId !in authorized) {
                return ServiceResponse(ok = false, message = "Service '${request.serviceId}' not authorized")
            }

            val action = descriptor.actions.find { it.name == request.action }
                ?: return ServiceResponse(ok = false, message = "Unknown action: ${request.action}")

            // TODO: Build real HTTP request from descriptor.baseUrl + action.path/method
            Log.d(TAG, "HTTP call: ${descriptor.baseUrl}${action.path} [${action.method}] params=${request.params}")
            return ServiceResponse(
                ok = true,
                data = mapOf(
                    "serviceId" to request.serviceId,
                    "action" to request.action,
                    "params" to request.params,
                ),
                message = "Service call to ${request.serviceId}.${request.action} completed",
            )
        }

        override fun listAvailableServices(): List<ServiceDescriptor> = services.values.toList()

        override fun isServiceAuthorized(serviceId: String): Boolean = serviceId in authorized

        override fun registerService(descriptor: ServiceDescriptor) {
            services[descriptor.id] = descriptor
            authorized.add(descriptor.id)
        }

        private companion object {
            private const val TAG = "HttpServiceGateway"
        }
    }
