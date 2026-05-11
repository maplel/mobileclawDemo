package com.mobilebot.data.approval

import com.mobilebot.data.db.AppDatabase
import com.mobilebot.data.db.ToolApprovalEntity
import com.mobilebot.domain.repository.ApprovalRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApprovalRepositoryImpl
    @Inject
    constructor(
        db: AppDatabase,
    ) : ApprovalRepository {
        private val dao = db.toolApprovalDao()

        override suspend fun findApproved(
            sessionKey: String,
            toolId: String,
            argsJson: String,
        ): Boolean {
            val h = argsJson.hashCode()
            return dao.countApproved(sessionKey, toolId, h) > 0
        }

        override suspend fun recordApproval(
            sessionKey: String,
            toolId: String,
            argsJson: String,
        ) {
            val h = argsJson.hashCode()
            dao.insert(
                ToolApprovalEntity(
                    id = UUID.randomUUID().toString(),
                    sessionKey = sessionKey,
                    toolId = toolId,
                    argsJson = argsJson,
                    argsHash = h,
                    approvedAt = System.currentTimeMillis(),
                ),
            )
        }
    }
