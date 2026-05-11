package com.mobilebot.data.settings

/**
 * Optional API keys from app [BuildConfig] (e.g. [local.properties] `GEMINI_API_KEY`, `ZHIPU_API_KEY`).
 * Never log return values.
 */
interface DevLlmSecrets {
    fun geminiApiKeyFromLocalBuild(): String

    fun zhipuApiKeyFromLocalBuild(): String

    fun minimaxApiKeyFromLocalBuild(): String

    fun dashscopeApiKeyFromLocalBuild(): String
}
