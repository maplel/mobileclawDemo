package com.mobilebot.data

import com.mobilebot.data.settings.UserSettingsRepository
import com.mobilebot.domain.LlmConfigurator
import com.mobilebot.network.OpenAiCompatibleClient
import javax.inject.Inject

class LlmConfiguratorImpl
    @Inject
    constructor(
        private val settings: UserSettingsRepository,
        private val openAi: OpenAiCompatibleClient,
    ) : LlmConfigurator {
        override suspend fun beforeRequest() {
            openAi.apiKey = settings.getApiKey()
            openAi.baseUrl = settings.getBaseUrl()
            openAi.defaultModel = settings.getModel()
        }
    }
