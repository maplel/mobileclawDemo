package com.mobilebot.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mobilebot.data.settings.UserSettingsRepository
import com.mobilebot.domain.AgentLoop
import com.mobilebot.domain.agent.AgentInput
import com.mobilebot.domain.agent.AgentInputSource
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
                agent.processInput(
                    AgentInput(
                        chatId = settings.getDeviceId(),
                        source = AgentInputSource.HEARTBEAT,
                        text = "Review actionable items from boot, user, and memory context in at most 2 sentences.",
                        memoryHint = "Use available memory context and tools before surfacing reminders.",
                    ),
                )
                Result.success()
            } catch (_: Exception) {
                Result.retry()
            }
        }
    }
