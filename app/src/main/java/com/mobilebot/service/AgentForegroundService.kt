package com.mobilebot.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mobilebot.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AgentForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        return try {
            val notification =
                NotificationCompat
                    .Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setContentTitle(getString(R.string.agent_notification_title))
                    .setContentText(getString(R.string.agent_notification_text))
                    .setOngoing(true)
                    .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFY_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(NOTIFY_ID, notification)
            }
            START_STICKY
        } catch (e: Throwable) {
            // startForeground can throw on some OEMs / API levels; do not crash the whole app.
            Log.e(TAG, "startForeground failed", e)
            stopSelf()
            START_NOT_STICKY
        }
    }

    companion object {
        private const val TAG = "AgentFgService"
        const val CHANNEL_ID = "mobilebot_agent"
        private const val NOTIFY_ID = 77001

        fun start(context: Context) {
            val i = Intent(context, AgentForegroundService::class.java)
            ContextCompat.startForegroundService(context, i)
        }
    }
}
