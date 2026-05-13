package com.mobilebot.data.settings

/** Pure JVM helpers for [UserSettingsRepositoryImpl]; covered by unit tests. */
internal object UserSettingsResolution {
    fun resolvedApiKey(
        stored: String,
        geminiFallback: String,
        zhipuFallback: String,
        minimaxFallback: String = "",
        dashscopeFallback: String = "",
    ): String {
        val s = stored.trim()
        if (s.isNotEmpty()) return s
        val d = dashscopeFallback.trim()
        if (d.isNotEmpty()) return d
        val g = geminiFallback.trim()
        if (g.isNotEmpty()) return g
        val z = zhipuFallback.trim()
        if (z.isNotEmpty()) return z
        return minimaxFallback.trim()
    }

    fun resolvedBaseUrl(
        keyPresent: Boolean,
        stored: String,
    ): String {
        if (!keyPresent) return LlmEndpointDefaults.GEMINI_OPENAI_COMPAT_BASE
        return stored.trim().ifEmpty { LlmEndpointDefaults.GEMINI_OPENAI_COMPAT_BASE }
    }

    fun resolvedModel(
        keyPresent: Boolean,
        stored: String,
    ): String {
        if (!keyPresent) return LlmEndpointDefaults.DEFAULT_GEMINI_MODEL
        val s = stored.trim().ifEmpty { LlmEndpointDefaults.DEFAULT_GEMINI_MODEL }
        if (s.equals("gemini-2.0-flash", ignoreCase = true)) {
            return LlmEndpointDefaults.DEFAULT_GEMINI_MODEL
        }
        // 旧版预设/手填的 glm-4.7 易与计费/资源包不匹配；统一映射为当前默认 Flash
        if (s.equals("glm-4.7", ignoreCase = true)) {
            return LlmEndpointDefaults.DEFAULT_GLM_MODEL
        }
        return s
    }
}
