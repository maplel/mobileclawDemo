package com.mobilebot.di

import com.mobilebot.ForegroundControllerImpl
import com.mobilebot.domain.ForegroundController
import com.mobilebot.domain.permissions.AgentPermissionCoordinator
import com.mobilebot.permissions.DefaultAgentPermissionCoordinator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @Singleton
    abstract fun bindForeground(impl: ForegroundControllerImpl): ForegroundController

    @Binds
    @Singleton
    abstract fun bindAgentPermissionCoordinator(
        impl: DefaultAgentPermissionCoordinator,
    ): AgentPermissionCoordinator
}
