package com.mobilebot.domain.tools

import com.mobilebot.domain.ToolConfirmationGate
import com.mobilebot.domain.repository.ApprovalRepository
import javax.inject.Inject

class ToolPermissionGate
    @Inject
    constructor(
        private val confirmation: ToolConfirmationGate,
        private val approvals: ApprovalRepository,
    ) {
        suspend fun ensure(
            sessionKey: String,
            tool: Tool,
            argsSummary: String,
        ): Boolean {
            if (!tool.executionPolicy.requiresUserApproval) return true
            if (approvals.findApproved(sessionKey, tool.name, argsSummary)) return true
            val ok = confirmation.confirm(tool.name, argsSummary)
            if (ok) {
                approvals.recordApproval(sessionKey, tool.name, argsSummary)
            }
            return ok
        }
    }
