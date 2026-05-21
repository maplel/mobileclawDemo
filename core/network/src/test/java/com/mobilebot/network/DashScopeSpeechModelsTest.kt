package com.mobilebot.network

import org.junit.Assert.assertEquals
import org.junit.Test

class DashScopeSpeechModelsTest {
    @Test
    fun `normalizes ambient labels when speech content is present`() {
        assertEquals("不买不买。", normalizeRecognizedSpeechText("环境音：不买不买。"))
        assertEquals("先不买", normalizeRecognizedSpeechText("[background noise]: 先不买"))
    }

    @Test
    fun `drops pure non speech labels`() {
        assertEquals("", normalizeRecognizedSpeechText("环境音"))
        assertEquals("", normalizeRecognizedSpeechText("noise"))
    }

    @Test
    fun `drops leaked asr instruction text`() {
        assertEquals(
            "",
            normalizeRecognizedSpeechText(
                "这是一段手机通话里用户本人按住说话的一轮语音。只输出用户实际说出的文字。不要解释、不要补充上下文。",
            ),
        )
    }
}
