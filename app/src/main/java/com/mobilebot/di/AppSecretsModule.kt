package com.mobilebot.di

import com.mobilebot.BuildConfig
import com.mobilebot.data.settings.DevLlmSecrets
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppSecretsModule {
    @Provides
    @Singleton
    fun provideDevLlmSecrets(): DevLlmSecrets =
        object : DevLlmSecrets {
            override fun geminiApiKeyFromLocalBuild(): String = BuildConfig.GEMINI_API_KEY

            override fun zhipuApiKeyFromLocalBuild(): String = BuildConfig.ZHIPU_API_KEY

            override fun minimaxApiKeyFromLocalBuild(): String = BuildConfig.MINIMAX_API_KEY

            override fun dashscopeApiKeyFromLocalBuild(): String = BuildConfig.DASHSCOPE_API_KEY
        }
}
