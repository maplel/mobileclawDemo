package com.mobilebot.data

import com.mobilebot.data.approval.ApprovalRepositoryImpl
import com.mobilebot.data.capabilities.AgentCapabilityStoreImpl
import com.mobilebot.data.capabilities.AndroidCapabilityProbe
import com.mobilebot.data.capabilities.AndroidRuntimeCapabilityProbe
import com.mobilebot.data.context.AssetCallTranscriptRepository
import com.mobilebot.data.memory.MemoryFacadeImpl
import com.mobilebot.data.memory.MemoryFileRepositoryImpl
import com.mobilebot.data.memory.PersistentMemoryManagerImpl
import com.mobilebot.data.profile.UserProfileStoreImpl
import com.mobilebot.data.session.SessionRepositoryImpl
import com.mobilebot.data.settings.UserSettingsRepository
import com.mobilebot.data.settings.UserSettingsRepositoryImpl
import com.mobilebot.data.skills.CloudSkillSyncerImpl
import com.mobilebot.data.skills.SkillContentLoaderImpl
import com.mobilebot.data.skills.SkillsLoaderImpl
import com.mobilebot.domain.LlmConfigurator
import com.mobilebot.domain.skill.CloudSkillSyncer
import com.mobilebot.domain.skill.SkillContentLoader
import com.mobilebot.domain.permissions.AgentCapabilityStore
import com.mobilebot.domain.permissions.CapabilityApprovalGate
import com.mobilebot.domain.permissions.DefaultCapabilityApprovalGate
import com.mobilebot.domain.SkillsLoader
import com.mobilebot.domain.ToolConfirmationGate
import com.mobilebot.domain.memory.MemoryFacade
import com.mobilebot.domain.memory.PersistentMemoryManager
import com.mobilebot.domain.profile.UserProfileStore
import com.mobilebot.domain.repository.ApprovalRepository
import com.mobilebot.domain.repository.MemoryFileRepository
import com.mobilebot.domain.capabilities.CapabilityProbe
import com.mobilebot.domain.tools.DeviceCapabilityProbe
import com.mobilebot.domain.tools.ForegroundStateReader
import com.mobilebot.domain.tools.CallTranscriptRepository
import com.mobilebot.domain.repository.SessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindModule {
    @Binds
    @Singleton
    abstract fun bindUserSettings(impl: UserSettingsRepositoryImpl): UserSettingsRepository

    @Binds
    @Singleton
    abstract fun bindMemoryFiles(impl: MemoryFileRepositoryImpl): MemoryFileRepository

    @Binds
    @Singleton
    abstract fun bindMemoryFacade(impl: MemoryFacadeImpl): MemoryFacade

    @Binds
    @Singleton
    abstract fun bindPersistentMemoryManager(impl: PersistentMemoryManagerImpl): PersistentMemoryManager

    @Binds
    @Singleton
    abstract fun bindCapabilityProbe(impl: AndroidCapabilityProbe): DeviceCapabilityProbe

    @Binds
    @Singleton
    abstract fun bindRuntimeCapabilityProbe(impl: AndroidRuntimeCapabilityProbe): CapabilityProbe

    @Binds
    @Singleton
    abstract fun bindForegroundReader(impl: ForegroundStateReaderImpl): ForegroundStateReader

    @Binds
    @Singleton
    abstract fun bindApprovals(impl: ApprovalRepositoryImpl): ApprovalRepository

    @Binds
    @Singleton
    abstract fun bindSessions(impl: SessionRepositoryImpl): SessionRepository

    @Binds
    @Singleton
    abstract fun bindLlmConfigurator(impl: LlmConfiguratorImpl): LlmConfigurator

    @Binds
    @Singleton
    abstract fun bindSkills(impl: SkillsLoaderImpl): SkillsLoader

    @Binds
    @Singleton
    abstract fun bindToolGate(impl: AlwaysAllowToolGate): ToolConfirmationGate

    @Binds
    @Singleton
    abstract fun bindUserProfileStore(impl: UserProfileStoreImpl): UserProfileStore

    @Binds
    @Singleton
    abstract fun bindAgentCapabilityStore(impl: AgentCapabilityStoreImpl): AgentCapabilityStore

    @Binds
    @Singleton
    abstract fun bindCapabilityApprovalGate(impl: DefaultCapabilityApprovalGate): CapabilityApprovalGate

    @Binds
    @Singleton
    abstract fun bindSkillContentLoader(impl: SkillContentLoaderImpl): SkillContentLoader

    @Binds
    @Singleton
    abstract fun bindCloudSkillSyncer(impl: CloudSkillSyncerImpl): CloudSkillSyncer

    @Binds
    @Singleton
    abstract fun bindCallTranscriptRepository(impl: AssetCallTranscriptRepository): CallTranscriptRepository
}
