package com.mobilebot.domain.tools

data class PolicyDecision(
    val allowed: Boolean,
    val reason: String? = null,
)
