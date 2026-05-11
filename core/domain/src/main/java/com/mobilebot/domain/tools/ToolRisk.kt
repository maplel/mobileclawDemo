package com.mobilebot.domain.tools

enum class ToolRisk {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

data class ToolExecutionPolicy(
    val requiresForeground: Boolean = false,
    val requiresConnectivity: Boolean = false,
    val requiresUserApproval: Boolean = false,
    val hasSideEffects: Boolean = false,
    val maxRetries: Int = 0,
    val timeoutMs: Long = 15_000L,
)
