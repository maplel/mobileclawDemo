package com.mobilebot.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mobilebot.data.settings.UserSettingsRepository
import com.mobilebot.domain.AgentLoop
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AgentReminderWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val agent: AgentLoop,
        private val settings: UserSettingsRepository,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            if (settings.getApiKey().isBlank()) return Result.success()
            return try {
                agent.processUserMessage(
                    settings.getDeviceId(),
                    "[Scheduled] Summarize any actionable items from MEMORY in at most 2 sentences.",
                )
                Result.success()
            } catch (_: Exception) {
                Result.retry()
            }
        }
    }
