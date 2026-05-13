package com.mobilebot.domain.memory

data class WorkingMemory(
    val currentGoal: String? = null,
    val constraints: List<String> = emptyList(),
    val selectedEntities: Map<String, String> = emptyMap(),
    val stepObservations: List<String> = emptyList(),
    val lastError: String? = null,
    val pendingApprovalId: String? = null,
    val activeArtifactRefs: List<String> = emptyList(),
)
