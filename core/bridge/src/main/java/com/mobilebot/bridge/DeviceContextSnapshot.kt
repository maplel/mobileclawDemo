package com.mobilebot.bridge

data class DeviceContextSnapshot(
    val batteryPct: Int? = null,
    val charging: Boolean? = null,
    val connectivity: ConnectivityState = ConnectivityState.UNKNOWN,
    val foregroundApp: String? = null,
    val screenOn: Boolean = true,
    val locale: String = "",
    val timezone: String = "",
)

enum class ConnectivityState {
    NONE,
    WIFI,
    CELLULAR,
    OTHER,
    UNKNOWN,
}
