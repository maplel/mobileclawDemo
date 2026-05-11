package com.mobilebot.bridge.di

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.bridge.NotificationHistoryStore
import com.mobilebot.bridge.ServiceGateway
import com.mobilebot.bridge.impl.AndroidDeviceCapabilityBridge
import com.mobilebot.bridge.impl.HttpServiceGateway
import com.mobilebot.bridge.impl.InMemoryNotificationHistoryStore
import com.mobilebot.bridge.virtual.SwitchableDeviceCapabilityBridge
import com.mobilebot.bridge.virtual.VirtualBridgeManager
import com.mobilebot.bridge.virtual.VirtualServiceGateway
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BridgeModule {
    @Binds
    @Singleton
    abstract fun bindNotificationHistoryStore(impl: InMemoryNotificationHistoryStore): NotificationHistoryStore

    companion object {
        @Provides
        @Singleton
        fun provideDeviceCapabilityBridge(
            manager: VirtualBridgeManager,
            switchable: SwitchableDeviceCapabilityBridge,
            real: AndroidDeviceCapabilityBridge,
        ): DeviceCapabilityBridge =
            if (manager.hasAnyVirtual()) switchable else real

        @Provides
        @Singleton
        fun provideServiceGateway(
            manager: VirtualBridgeManager,
            real: HttpServiceGateway,
            virtual: VirtualServiceGateway,
        ): ServiceGateway =
            if (manager.isVirtual("services")) virtual else real
    }
}
