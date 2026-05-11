package com.mobilebot

import android.content.Context
import android.content.Intent
import android.util.Log
import com.mobilebot.domain.ForegroundController
import com.mobilebot.service.AgentForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundControllerImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ForegroundController {
        private val refCount = AtomicInteger(0)

        override fun onAgentStart() {
            val prev = refCount.getAndIncrement()
            if (prev == 0) {
                try {
                    AgentForegroundService.start(context)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not start agent foreground service (chat can still run)", e)
                }
            }
        }

        override fun onAgentStop() {
            val now = refCount.decrementAndGet()
            if (now <= 0) {
                refCount.set(0)
                try {
                    context.stopService(Intent(context, AgentForegroundService::class.java))
                } catch (e: Exception) {
                    Log.w(TAG, "Could not stop agent foreground service", e)
                }
            }
        }

        private companion object {
            private const val TAG = "MobileBot"
        }
    }
