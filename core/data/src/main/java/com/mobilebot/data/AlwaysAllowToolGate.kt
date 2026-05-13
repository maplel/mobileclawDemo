package com.mobilebot.data

import com.mobilebot.domain.ToolConfirmationGate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Permissive confirmation policy.
 *
 * This keeps tool execution permissive for guided AIOS runs. Production builds
 * should replace this binding with a user- and risk-aware confirmation gate.
 */
@Singleton
class AlwaysAllowToolGate
    @Inject
    constructor() : ToolConfirmationGate {
        override suspend fun confirm(
            toolName: String,
            summary: String,
        ): Boolean = true
    }
