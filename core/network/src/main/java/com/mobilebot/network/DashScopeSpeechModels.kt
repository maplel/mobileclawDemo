package com.mobilebot.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class DashScopeSpeechRecognizer
    @Inject
    constructor(
        private val openAiClient: OpenAiCompatibleClient,
    ) : SpeechRecognizer {
        override suspend fun transcribeAudio(
            audioBytes: ByteArray,
            mimeType: String,
        ): String {
            val apiKey = openAiClient.apiKey.trim()
            if (apiKey.isBlank()) throw IOException("DashScope API key is empty.")
            val base64 = Base64.getEncoder().encodeToString(audioBytes)
            val body = JSONObject()
                .put("model", QWEN_ASR_MODEL)
                .put(
                    "messages",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("role", "system")
                                .put(
                                    "content",
                                    JSONArray().put(
                                        JSONObject()
                                            .put("text", ASR_CONTEXT_BIAS),
                                    ),
                                ),
                        )
                        .put(
                        JSONObject()
                            .put("role", "user")
                            .put(
                                "content",
                                JSONArray()
                                    .put(
                                        JSONObject()
                                            .put("type", "input_audio")
                                            .put(
                                                "input_audio",
                                                JSONObject()
                                                    .put("data", "data:$mimeType;base64,$base64"),
                                            ),
                                    )
                            ),
                    ),
                )
                .put(
                    "asr_options",
                    JSONObject()
                        .put("language", "zh")
                        .put("enable_itn", false),
                )
                .put("stream", false)
                .toString()

            val raw = executeJsonPost(
                url = "${compatibleBaseUrl()}/chat/completions",
                apiKey = apiKey,
                body = body,
            )
            return parseAsrText(raw)
        }

        private fun compatibleBaseUrl(): String {
            val base = openAiClient.baseUrl.trimEnd('/')
            return when {
                base.contains("dashscope-intl.aliyuncs.com", ignoreCase = true) ->
                    "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"
                base.contains("dashscope.aliyuncs.com", ignoreCase = true) ->
                    "https://dashscope.aliyuncs.com/compatible-mode/v1"
                else -> "https://dashscope.aliyuncs.com/compatible-mode/v1"
            }
        }

        private companion object {
            const val QWEN_ASR_MODEL = "qwen3-asr-flash"
            const val ASR_CONTEXT_BIAS =
                "这是一段手机通话里用户本人按住说话的一轮语音。只输出用户实际说出的文字。不要解释、不要补充上下文、不要追加场景关键词，也不要把清晰人声标注为环境音、背景音或噪声。"
        }
    }

@Singleton
class DashScopeSpeechSynthesizer
    @Inject
    constructor(
        private val openAiClient: OpenAiCompatibleClient,
    ) : SpeechSynthesizer {
        override suspend fun synthesizeSpeech(
            text: String,
            voice: String,
            languageType: String,
        ): SpeechSynthesisResult {
            val cleanText = text.trim()
            if (cleanText.isBlank()) throw IOException("TTS text is empty.")
            val apiKey = openAiClient.apiKey.trim()
            if (apiKey.isBlank()) throw IOException("DashScope API key is empty.")
            val body = JSONObject()
                .put("model", QWEN_TTS_MODEL)
                .put(
                    "input",
                    JSONObject()
                        .put("text", cleanText)
                        .put("voice", voice)
                        .put("language_type", languageType),
                )
                .toString()

            val raw = executeJsonPost(
                url = "${multimodalBaseUrl()}/api/v1/services/aigc/multimodal-generation/generation",
                apiKey = apiKey,
                body = body,
            )
            val audioUrl = parseTtsAudioUrl(raw)
            return SpeechSynthesisResult(audioUrl)
        }

        private fun multimodalBaseUrl(): String {
            val base = openAiClient.baseUrl
            return if (base.contains("dashscope-intl.aliyuncs.com", ignoreCase = true)) {
                "https://dashscope-intl.aliyuncs.com"
            } else {
                "https://dashscope.aliyuncs.com"
            }
        }

        private companion object {
            const val QWEN_TTS_MODEL = "qwen3-tts-flash"
        }
    }

private val speechJsonMedia = "application/json; charset=utf-8".toMediaType()

private val speechHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
}

private suspend fun executeJsonPost(
    url: String,
    apiKey: String,
    body: String,
): String {
    val request = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $apiKey")
        .post(body.toRequestBody(speechJsonMedia))
        .build()
    val call = speechHttpClient.newCall(request)
    return suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    response.use { resp ->
                        val raw = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            if (cont.isActive) {
                                cont.resumeWithException(IOException("HTTP ${resp.code}: ${raw.take(800)}"))
                            }
                            return@use
                        }
                        if (cont.isActive) cont.resume(raw)
                    }
                }
            },
        )
    }
}

private fun parseAsrText(raw: String): String {
    val root = JSONObject(raw)
    val choices = root.optJSONArray("choices")
    val content = choices
        ?.optJSONObject(0)
        ?.optJSONObject("message")
        ?.opt("content")
    return normalizeRecognizedSpeechText(
        extractText(content)
        ?: root.optJSONObject("output")?.optString("text")?.trim()?.takeIf { it.isNotEmpty() }
        ?: root.optString("text").trim().takeIf { it.isNotEmpty() }
        ?: throw IOException("ASR response did not include transcription text."),
    )
}

internal fun normalizeRecognizedSpeechText(raw: String): String {
    var value = raw.trim().trim('"', '\'', '`', '“', '”', '‘', '’')
    if (value.isBlank()) return ""
    if (value.isAsrInstructionLeak()) return ""

    value = value
        .replace(
            Regex(
                pattern = """^\s*(?:\[?\s*(?:环境音|背景音|噪声|杂音|ambient sound|background noise|noise|non[- ]?speech)\s*\]?\s*[:：,，.。\-]*)+""",
                option = RegexOption.IGNORE_CASE,
            ),
            "",
        )
        .replace(Regex("""^\s*(?:转写|识别结果|用户|你)\s*[:：]\s*"""), "")
        .trim()
        .trim('"', '\'', '`', '“', '”', '‘', '’')

    if (value.isAsrInstructionLeak()) return ""

    val compact = value
        .lowercase(Locale.US)
        .replace(Regex("""[\s\p{Punct}。！？：，、“”‘’【】\[\]（）()]+"""), "")
    val nonSpeechLabels = setOf(
        "环境音",
        "背景音",
        "噪声",
        "杂音",
        "无有效语音",
        "没有听清",
        "没听清",
        "nospeech",
        "noise",
        "backgroundnoise",
        "ambientsound",
        "nonspeech",
    )
    return if (compact in nonSpeechLabels) "" else value
}

private fun String.isAsrInstructionLeak(): Boolean {
    val value = trim()
    if (value.isBlank()) return false
    val lower = value.lowercase(Locale.US)
    return (
        value.startsWith("这是一段手机通话里用户本人按住说话的一轮语音") ||
            lower.startsWith("this is a mobile phone call") ||
            lower.startsWith("this is a voice message")
    ) && (
        value.contains("只输出用户实际说出的文字") ||
            value.contains("不要解释") ||
            lower.contains("only output")
    )
}

private fun extractText(value: Any?): String? =
    when (value) {
        is String -> value.trim().takeIf { it.isNotEmpty() }
        is JSONArray -> {
            for (i in 0 until value.length()) {
                extractText(value.opt(i))?.let { return it }
            }
            null
        }
        is JSONObject -> {
            value.optString("text").trim().takeIf { it.isNotEmpty() }
                ?: value.optString("transcript").trim().takeIf { it.isNotEmpty() }
                ?: value.optString("content").trim().takeIf { it.isNotEmpty() }
        }
        else -> null
    }

private fun parseTtsAudioUrl(raw: String): String {
    val root = JSONObject(raw)
    return root.optJSONObject("output")
        ?.optJSONObject("audio")
        ?.optString("url")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: findAudioUrl(root)
        ?: throw IOException("TTS response did not include an audio URL.")
}

private fun findAudioUrl(value: Any?): String? {
    return when (value) {
        is JSONObject -> {
            val direct = value.optString("url").trim().takeIf { it.startsWith("http", ignoreCase = true) }
            if (direct != null) return direct
            val keys = value.keys()
            while (keys.hasNext()) {
                findAudioUrl(value.opt(keys.next()))?.let { return it }
            }
            null
        }
        is JSONArray -> {
            for (i in 0 until value.length()) {
                findAudioUrl(value.opt(i))?.let { return it }
            }
            null
        }
        else -> null
    }
}
