package com.mobilebot

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mobilebot.data.ProcessForegroundBinder
import com.mobilebot.di.SkillBootstrapEntryPoint
import com.mobilebot.service.AgentForegroundService
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MobileBotApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var processForegroundBinder: ProcessForegroundBinder

    override fun onCreate() {
        super.onCreate()
        val entryPoint = EntryPointAccessors
            .fromApplication(this, SkillBootstrapEntryPoint::class.java)
        val loader = entryPoint.skillAssetLoader()
        loader.loadAllSkills()
        loader.loadServiceConfigs()
        entryPoint.systemRuntime().bootstrap()
        entryPoint.virtualDataBootstrapper().bootstrapIfNeeded()
        processForegroundBinder.attach()
        val ch =
            NotificationChannel(
                AgentForegroundService.CHANNEL_ID,
                "MobileBot agent",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()
}
