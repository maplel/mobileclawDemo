package com.mobilebot.domain.memory

sealed interface WorkingMemoryUpdate {
    data class FullReplace(val memory: WorkingMemory) : WorkingMemoryUpdate

    data class SetGoal(val goal: String?) : WorkingMemoryUpdate

    /**
     * New user message: set goal and drop prior-turn tool traces so the model is not steered
     * by completed actions (e.g. still calling open_camera after the user only says "hello").
     */
    data class BeginUserTurn(val goal: String?) : WorkingMemoryUpdate

    data class AppendObservation(val line: String) : WorkingMemoryUpdate

    data class SetLastError(val error: String?) : WorkingMemoryUpdate

    data object ClearPendingApproval : WorkingMemoryUpdate
}
