package com.mobilebot.data.memory

import com.mobilebot.data.db.AppDatabase
import com.mobilebot.data.db.MemoryFactEntity
import com.mobilebot.data.db.SessionSummaryEntity
import com.mobilebot.data.db.ToolEventEntity
import com.mobilebot.data.db.WorkingMemoryEntity
import com.mobilebot.domain.memory.MemoryFacade
import com.mobilebot.domain.memory.MemoryFact
import com.mobilebot.domain.memory.ToolTraceEvent
import com.mobilebot.domain.memory.WorkingMemory
import com.mobilebot.domain.memory.WorkingMemoryUpdate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryFacadeImpl
    @Inject
    constructor(
        db: AppDatabase,
    ) : MemoryFacade {
        private val workingMemoryDao = db.workingMemoryDao()
        private val sessionSummaryDao = db.sessionSummaryDao()
        private val toolEventDao = db.toolEventDao()
        private val memoryFactDao = db.memoryFactDao()

        override suspend fun getWorkingMemory(sessionKey: String): WorkingMemory {
            val json = workingMemoryDao.getJson(sessionKey)
            return WorkingMemoryCodec.decode(json)
        }

        override suspend fun updateWorkingMemory(
            sessionKey: String,
            update: WorkingMemoryUpdate,
        ) {
            val current = getWorkingMemory(sessionKey)
            val next =
                when (update) {
                    is WorkingMemoryUpdate.FullReplace -> update.memory
                    is WorkingMemoryUpdate.SetGoal -> current.copy(currentGoal = update.goal)
                    is WorkingMemoryUpdate.BeginUserTurn ->
                        current.copy(
                            currentGoal = update.goal,
                            stepObservations = emptyList(),
                            lastError = null,
                            pendingApprovalId = null,
                        )
                    is WorkingMemoryUpdate.AppendObservation ->
                        current.copy(
                            stepObservations = current.stepObservations + update.line,
                        )
                    is WorkingMemoryUpdate.SetLastError -> current.copy(lastError = update.error)
                    is WorkingMemoryUpdate.ClearPendingApproval ->
                        current.copy(pendingApprovalId = null)
                }
            val now = System.currentTimeMillis()
            workingMemoryDao.upsert(
                WorkingMemoryEntity(
                    sessionKey = sessionKey,
                    json = WorkingMemoryCodec.encode(next),
                    updatedAt = now,
                ),
            )
        }

        override suspend fun appendToolTrace(
            sessionKey: String,
            event: ToolTraceEvent,
        ) {
            val status =
                when (event.decision.lowercase()) {
                    "success", "allowed" -> "success"
                    "blocked", "denied" -> "blocked"
                    else -> event.decision
                }
            toolEventDao.insert(
                ToolEventEntity(
                    id = UUID.randomUUID().toString(),
                    sessionKey = sessionKey,
                    toolId = event.toolId,
                    argsJson = event.args,
                    resultJson = event.resultPreview,
                    status = status,
                    createdAt = event.startedAtMs,
                ),
            )
        }

        override suspend fun getSessionSummary(sessionKey: String): String? = sessionSummaryDao.getSummary(sessionKey)

        override suspend fun updateSessionSummary(
            sessionKey: String,
            summary: String,
        ) {
            sessionSummaryDao.upsert(
                SessionSummaryEntity(
                    sessionKey = sessionKey,
                    summaryText = summary,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }

        override suspend fun retrieveFacts(
            query: String,
            limit: Int,
        ): List<MemoryFact> {
            val needle = query.trim().take(64).ifEmpty { return emptyList() }
            return memoryFactDao.search(needle, limit).map {
                MemoryFact(
                    id = it.id,
                    namespace = it.namespace,
                    key = it.key,
                    value = it.value,
                    confidence = it.confidence,
                )
            }
        }

        override suspend fun writeFact(fact: MemoryFact) {
            memoryFactDao.upsert(
                MemoryFactEntity(
                    id = fact.id,
                    namespace = fact.namespace,
                    key = fact.key,
                    value = fact.value,
                    embeddingRef = null,
                    confidence = fact.confidence,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }
