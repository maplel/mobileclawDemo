package com.mobilebot.domain.service

import android.util.Log
import com.mobilebot.bridge.ServiceDescriptor
import com.mobilebot.bridge.ServiceGateway
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level manager for service registration, authorization, and discovery.
 * Wraps [ServiceGateway] with lifecycle and authorization management.
 */
@Singleton
class ServiceManager
    @Inject
    constructor(
        private val gateway: ServiceGateway,
    ) {
        fun installService(descriptor: ServiceDescriptor): Boolean {
            val existing = gateway.listAvailableServices().find { it.id == descriptor.id }
            if (existing != null) {
                Log.d(TAG, "Updating service: ${descriptor.id}")
            }
            gateway.registerService(descriptor)
            Log.d(TAG, "Installed service: ${descriptor.id} (${descriptor.name})")
            return true
        }

        fun uninstallService(serviceId: String): Boolean {
            val existing = gateway.listAvailableServices().find { it.id == serviceId }
            if (existing == null) {
                Log.d(TAG, "Service '$serviceId' not found")
                return false
            }
            Log.d(TAG, "Uninstalled service: $serviceId")
            return true
        }

        fun listServices(): List<ServiceInfo> =
            gateway.listAvailableServices().map { desc ->
                ServiceInfo(
                    id = desc.id,
                    name = desc.name,
                    category = desc.category,
                    authorized = gateway.isServiceAuthorized(desc.id),
                    actionCount = desc.actions.size,
                    actions = desc.actions.map { it.name },
                )
            }

        fun getServiceInfo(serviceId: String): ServiceInfo? =
            gateway.listAvailableServices().find { it.id == serviceId }?.let { desc ->
                ServiceInfo(
                    id = desc.id,
                    name = desc.name,
                    category = desc.category,
                    authorized = gateway.isServiceAuthorized(desc.id),
                    actionCount = desc.actions.size,
                    actions = desc.actions.map { it.name },
                )
            }

        fun isAuthorized(serviceId: String): Boolean =
            gateway.isServiceAuthorized(serviceId)

        private companion object {
            private const val TAG = "ServiceManager"
        }
    }

data class ServiceInfo(
    val id: String,
    val name: String,
    val category: String,
    val authorized: Boolean,
    val actionCount: Int,
    val actions: List<String>,
)
