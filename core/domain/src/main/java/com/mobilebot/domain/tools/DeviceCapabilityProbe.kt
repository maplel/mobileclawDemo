package com.mobilebot.domain.tools

/**
 * Queries device/runtime capabilities for tool and skill eligibility checks.
 */
interface DeviceCapabilityProbe {
    fun hasCapabilities(capabilities: Set<String>): Boolean

    fun hasPermission(permission: String): Boolean = true

    fun isConnected(): Boolean = true

    fun isAppInstalled(packageName: String): Boolean = true

    fun meetsMinApi(minApi: Int): Boolean = true

    fun isWithinTimeRange(timeSpec: String): Boolean = true
}
