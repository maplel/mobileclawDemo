package com.mobilebot.domain

fun interface ToolConfirmationGate {
    suspend fun confirm(
        toolName: String,
        summary: String,
    ): Boolean
}
