package com.mobilebot.systemruntime

import com.mobilebot.domain.tools.Tool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceSystemToolModule {
    @Binds
    @IntoSet
    abstract fun bindDeviceSystemTool(tool: DeviceSystemTool): Tool

    @Binds
    @IntoSet
    abstract fun bindSystemSendSmsTool(tool: SystemSendSmsTool): Tool

    @Binds
    @IntoSet
    abstract fun bindSystemWaitForSmsTool(tool: SystemWaitForSmsTool): Tool

    @Binds
    @IntoSet
    abstract fun bindSystemSearchContactsTool(tool: SystemSearchContactsTool): Tool
}
