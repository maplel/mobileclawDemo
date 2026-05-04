package com.mobilebot.data

import com.mobilebot.domain.ToolConfirmationGate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlwaysAllowToolGate
    @Inject
    constructor() : ToolConfirmationGate {
        override suspend fun confirm(
            toolName: String,
            summary: String,
        ): Boolean = true
    }
