package com.mobilebot.di

import com.mobilebot.data.skills.SkillAssetLoader
import com.mobilebot.data.virtual.VirtualDataBootstrapper
import com.mobilebot.systemruntime.SystemRuntime
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SkillBootstrapEntryPoint {
    fun skillAssetLoader(): SkillAssetLoader
    fun virtualDataBootstrapper(): VirtualDataBootstrapper
    fun systemRuntime(): SystemRuntime
}
