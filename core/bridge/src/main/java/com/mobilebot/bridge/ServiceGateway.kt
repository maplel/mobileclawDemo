package com.mobilebot.bridge

data class ServiceRequest(
    val serviceId: String,
    val action: String,
    val params: Map<String, Any> = emptyMap(),
)

data class ServiceResponse(
    val ok: Boolean,
    val data: Map<String, Any> = emptyMap(),
    val message: String = "",
)

data class ServiceActionDescriptor(
    val name: String,
    val method: String,
    val path: String,
    val description: String,
    val requiresUserApproval: Boolean = false,
)

data class ServiceDescriptor(
    val id: String,
    val name: String,
    val category: String,
    val baseUrl: String,
    val authType: String,
    val actions: List<ServiceActionDescriptor>,
)

interface ServiceGateway {
    suspend fun call(request: ServiceRequest): ServiceResponse
    fun listAvailableServices(): List<ServiceDescriptor>
    fun isServiceAuthorized(serviceId: String): Boolean
    fun registerService(descriptor: ServiceDescriptor)
}
