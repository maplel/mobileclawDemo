package com.mobilebot.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeartbeatScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun applyHeartbeat(enable: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enable) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val req =
                PeriodicWorkRequestBuilder<AgentReminderWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    ).build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        companion object {
            const val WORK_NAME = "mobilebot_heartbeat"
        }
    }
