package com.mobilebot.domain.repository

interface ApprovalRepository {
    suspend fun findApproved(
        sessionKey: String,
        toolId: String,
        argsJson: String,
    ): Boolean

    suspend fun recordApproval(
        sessionKey: String,
        toolId: String,
        argsJson: String,
    )
}
