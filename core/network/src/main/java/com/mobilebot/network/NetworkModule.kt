package com.mobilebot.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideLlmClient(impl: OpenAiCompatibleClient): LlmClient = impl

    @Provides
    @Singleton
    fun provideRoleCallModel(impl: QwenRoleCallModel): RoleCallModel = impl

    @Provides
    @Singleton
    fun provideSpeechRecognizer(impl: DashScopeSpeechRecognizer): SpeechRecognizer = impl

    @Provides
    @Singleton
    fun provideSpeechSynthesizer(impl: DashScopeSpeechSynthesizer): SpeechSynthesizer = impl
}
