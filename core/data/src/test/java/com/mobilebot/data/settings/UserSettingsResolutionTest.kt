package com.mobilebot.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Example unit tests for LLM settings resolution (stored prefs vs [local.properties] fallback).
 * Run: `./gradlew :core:data:testDebugUnitTest`
 */
class UserSettingsResolutionTest {

    @Test
    fun `resolvedApiKey uses stored key when non-blank`() {
        assertEquals(
            "user-key",
            UserSettingsResolution.resolvedApiKey("user-key", "gemini-dev", "zhipu-dev"),
        )
    }

    @Test
    fun `resolvedApiKey falls back to Gemini then Zhipu when stored empty`() {
        assertEquals(
            "gemini-dev",
            UserSettingsResolution.resolvedApiKey("", "gemini-dev", "zhipu-dev"),
        )
        assertEquals(
            "gemini-dev",
            UserSettingsResolution.resolvedApiKey("   ", "gemini-dev", "zhipu-dev"),
        )
        assertEquals(
            "zhipu-dev",
            UserSettingsResolution.resolvedApiKey("", "", "zhipu-dev"),
        )
        assertEquals(
            "",
            UserSettingsResolution.resolvedApiKey("", "", ""),
        )
        assertEquals(
            "minimax-dev",
            UserSettingsResolution.resolvedApiKey("", "", "", "minimax-dev"),
        )
    }

    @Test
    fun `resolvedBaseUrl returns Gemini default when pref key absent`() {
        assertEquals(
            LlmEndpointDefaults.GEMINI_OPENAI_COMPAT_BASE,
            UserSettingsResolution.resolvedBaseUrl(keyPresent = false, stored = ""),
        )
    }

    @Test
    fun `resolvedBaseUrl returns stored value when key present`() {
        assertEquals(
            "https://api.openai.com/v1",
            UserSettingsResolution.resolvedBaseUrl(keyPresent = true, stored = "https://api.openai.com/v1"),
        )
    }

    @Test
    fun `resolvedModel returns default Gemini model when pref key absent`() {
        assertEquals(
            LlmEndpointDefaults.DEFAULT_GEMINI_MODEL,
            UserSettingsResolution.resolvedModel(keyPresent = false, stored = ""),
        )
    }

    @Test
    fun `resolvedModel maps old Gemini default to current default`() {
        assertEquals(
            LlmEndpointDefaults.DEFAULT_GEMINI_MODEL,
            UserSettingsResolution.resolvedModel(keyPresent = true, stored = "gemini-2.0-flash"),
        )
    }

    @Test
    fun `resolvedModel maps legacy glm-4_7 to glm-4_7-flash`() {
        assertEquals(
            LlmEndpointDefaults.DEFAULT_GLM_MODEL,
            UserSettingsResolution.resolvedModel(keyPresent = true, stored = "glm-4.7"),
        )
        assertEquals(
            LlmEndpointDefaults.DEFAULT_GLM_MODEL,
            UserSettingsResolution.resolvedModel(keyPresent = true, stored = "GLM-4.7"),
        )
        assertEquals(
            LlmEndpointDefaults.DEFAULT_GLM_MODEL,
            UserSettingsResolution.resolvedModel(keyPresent = true, stored = "glm-4.7-flash"),
        )
    }
}
