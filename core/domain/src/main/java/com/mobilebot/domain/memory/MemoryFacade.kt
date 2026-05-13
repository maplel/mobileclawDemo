package com.mobilebot.domain.memory

interface MemoryFacade {
    suspend fun getWorkingMemory(sessionKey: String): WorkingMemory

    suspend fun updateWorkingMemory(
        sessionKey: String,
        update: WorkingMemoryUpdate,
    )

    suspend fun appendToolTrace(
        sessionKey: String,
        event: ToolTraceEvent,
    )

    suspend fun getSessionSummary(sessionKey: String): String?

    suspend fun updateSessionSummary(
        sessionKey: String,
        summary: String,
    )

    suspend fun retrieveFacts(
        query: String,
        limit: Int = 5,
    ): List<MemoryFact>

    suspend fun writeFact(fact: MemoryFact)
}
