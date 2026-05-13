package com.mobilebot.network

import android.util.Log
import com.mobilebot.model.StreamEvent
import com.mobilebot.model.ToolDefinition
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class OpenAiCompatibleClient
@Inject
constructor() : LlmClient {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override var defaultModel: String = "gemini-2.5-flash"

    var apiKey: String = ""
    var baseUrl: String = "https://generativelanguage.googleapis.com/v1beta/openai"
    var extraHeaders: Map<String, String> = emptyMap()

    override suspend fun chat(
        messages: List<LlmMessage>,
        tools: List<ToolDefinition>?,
        model: String?,
        maxTokens: Int,
    ): LlmResponse {
        val body =
            buildRequestBody(
                messages = messages,
                tools = tools,
                model = normalizeLegacyGlmModel(model ?: defaultModel),
                maxTokens = maxTokens,
                stream = false,
            )

        var lastException: Exception? = null
        for (attempt in 0 until MAX_RETRIES) {
            try {
                return executeSingleChat(body)
            } catch (e: IOException) {
                lastException = e
                if (!isTransientError(e) || attempt == MAX_RETRIES - 1) throw wrapWithFriendlyMessage(e)
                val delayMs = RETRY_BASE_MS * (1L shl attempt)
                Log.w(TAG, "Transient error on attempt ${attempt + 1}/$MAX_RETRIES, retrying in ${delayMs}ms", e)
                kotlinx.coroutines.delay(delayMs)
            }
        }
        throw wrapWithFriendlyMessage(lastException ?: IOException("request failed"))
    }

    private suspend fun executeSingleChat(body: String): LlmResponse {
        val req = buildPostRequest(body)
        val call = client.newCall(req)

        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }

            fun resumeErr(t: Throwable) {
                if (!cont.isActive) return
                val e = t as? Exception ?: IOException(t.message ?: "request failed", t)
                runCatching { cont.resumeWithException(e) }
            }

            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        resumeErr(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            response.use { resp ->
                                val raw = resp.body?.string().orEmpty()

                                if (!resp.isSuccessful) {
                                    if (cont.isActive) {
                                        runCatching {
                                            cont.resumeWithException(
                                                IOException(formatHttpErrorBody(resp.code, raw)),
                                            )
                                        }
                                    }
                                    return@use
                                }

                                try {
                                    val parsed = parseNonStreamResponse(raw)
                                    if (cont.isActive) {
                                        runCatching { cont.resume(parsed) }
                                    }
                                } catch (e: Exception) {
                                    resumeErr(e)
                                }
                            }
                        } catch (t: Throwable) {
                            resumeErr(t)
                        }
                    }
                },
            )
        }
    }

    private fun isTransientError(e: IOException): Boolean {
        val msg = e.message?.lowercase().orEmpty()
        return msg.contains("unable to resolve host") ||
            msg.contains("no address associated") ||
            msg.contains("failed to connect") ||
            msg.contains("timeout") ||
            msg.contains("connection reset") ||
            msg.contains("connection refused") ||
            e is java.net.UnknownHostException ||
            e is java.net.SocketTimeoutException ||
            e is java.net.ConnectException
    }

    private fun wrapWithFriendlyMessage(e: Exception): IOException {
        val msg = e.message?.lowercase().orEmpty()
        val friendly = when {
            msg.contains("unable to resolve host") || msg.contains("no address associated") ->
                "网络连接失败：无法解析服务器地址。请检查网络连接后重试。\n(DNS resolution failed: ${e.message})"
            msg.contains("timeout") ->
                "网络连接超时：服务器响应时间过长。请稍后重试。\n(Connection timed out: ${e.message})"
            msg.contains("connection refused") || msg.contains("failed to connect") ->
                "无法连接到服务器。请检查网络设置后重试。\n(Connection failed: ${e.message})"
            else -> e.message ?: "网络请求失败"
        }
        return IOException(friendly, e)
    }

    override fun chatStream(
        messages: List<LlmMessage>,
        tools: List<ToolDefinition>?,
        model: String?,
        maxTokens: Int,
    ): Flow<StreamEvent> =
        callbackFlow {
            val body =
                buildRequestBody(
                    messages = messages,
                    tools = tools,
                    model = normalizeLegacyGlmModel(model ?: defaultModel),
                    maxTokens = maxTokens,
                    stream = true,
                )

            val req = buildPostRequest(body)
            val call = client.newCall(req)

            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        runCatching {
                            trySend(StreamEvent.Error(e.message ?: "network error"))
                            close(e)
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            if (!response.isSuccessful) {
                                val err = response.body?.string().orEmpty()
                                runCatching {
                                    trySend(StreamEvent.Error(formatHttpErrorBody(response.code, err)))
                                    close()
                                }
                                return
                            }

                            val source =
                                response.body?.source()
                                    ?: run {
                                        runCatching { close() }
                                        return
                                    }

                            try {
                                while (!source.exhausted()) {
                                    val line = source.readUtf8Line() ?: break
                                    if (!line.startsWith("data: ")) continue

                                    val data = line.removePrefix("data: ").trim()
                                    if (data == "[DONE]") {
                                        trySend(StreamEvent.Done(""))
                                        break
                                    }

                                    parseSseChunk(data)?.let { trySend(it) }
                                }
                            } catch (e: Exception) {
                                runCatching {
                                    trySend(StreamEvent.Error(e.message ?: "stream error"))
                                }
                            } finally {
                                response.close()
                                runCatching { close() }
                            }
                        } catch (t: Throwable) {
                            runCatching {
                                trySend(StreamEvent.Error(t.message ?: "stream error"))
                                close(t as? Exception ?: IOException(t))
                            }
                        }
                    }
                },
            )

            awaitClose { call.cancel() }
        }

    private fun formatHttpErrorBody(code: Int, raw: String): String {
        val trimmed = raw.trim()
        val fromJson =
            runCatching {
                val root = JSONObject(trimmed)
                root.optJSONObject("error")?.optString("message")
                    ?: root.optString("message")
            }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val tail =
            fromJson
                ?: trimmed.take(800).let { t -> if (trimmed.length > 800) "$t…" else t }
        return "HTTP $code: $tail"
    }

    private fun buildPostRequest(body: String): Request {
        val root = baseUrl.trimEnd('/')

        val builder =
            Request.Builder()
                .url("$root/chat/completions")
                .post(body.toRequestBody(jsonMedia))

        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
        }

        extraHeaders.forEach { (k, v) ->
            builder.header(k, v)
        }

        return builder.build()
    }

    private fun normalizeLegacyGlmModel(model: String): String {
        val t = model.trim()
        return if (t.equals("glm-4.7", ignoreCase = true)) "glm-4.7-flash" else t
    }

    private fun isMinimaxOpenAiBaseUrl(): Boolean = baseUrl.contains("minimaxi.com", ignoreCase = true)

    private fun toolParametersJson(schema: String): JSONObject {
        val p = runCatching { JSONObject(schema) }.getOrElse { JSONObject() }
        if (!p.has("type")) p.put("type", "object")
        return p
    }

    private fun buildRequestBody(
        messages: List<LlmMessage>,
        tools: List<ToolDefinition>?,
        model: String,
        maxTokens: Int,
        stream: Boolean,
    ): String {
        val declaredToolNames =
            tools
                ?.mapNotNull { it.name.trim().takeIf { n -> n.isNotEmpty() } }
                ?.toSet()
                .orEmpty()

        val arr = buildMessagesArray(messages, declaredToolNames)

        val root =
            JSONObject()
                .put("model", model)
                .put("messages", arr)
                .put("stream", stream)

        if (maxTokens > 0) {
            root.put("max_tokens", maxTokens)
        }

        if (!tools.isNullOrEmpty()) {
            val tArr = JSONArray()
            for (t in tools) {
                tArr.put(
                    JSONObject()
                        .put("type", "function")
                        .put(
                            "function",
                            JSONObject()
                                .put("name", t.name)
                                .put("description", t.description)
                                .put("parameters", toolParametersJson(t.parametersSchema)),
                        ),
                )
            }
            root.put("tools", tArr)
            root.put("tool_choice", "auto")
        }

        if (isMinimaxOpenAiBaseUrl()) {
            // OpenAI Python SDK: extra_body={"reasoning_split": true}; merged into JSON body for REST.
            root.put("reasoning_split", true)
        }

        val json = root.toString()

        Log.i(
            TAG,
            "chat/completions: model=$model messages=${messages.size} tools=${tools?.size ?: 0} stream=$stream",
        )
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            val preview = if (json.length <= 3000) json else json.take(3000) + "…"
            Log.d(TAG, "request json = $preview")
        }

        return json
    }

    private fun buildMessagesArray(
        messages: List<LlmMessage>,
        declaredToolNames: Set<String>,
    ): JSONArray {
        val result = JSONArray()
        if (messages.isEmpty()) return result

        val systemMsgs = messages.filter { it.role.equals("system", ignoreCase = true) }
        val otherMsgs = messages.filterNot { it.role.equals("system", ignoreCase = true) }

        val systemContent =
            systemMsgs
                .map { it.content.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n\n")

        if (systemContent.isNotEmpty()) {
            result.put(
                JSONObject()
                    .put("role", "system")
                    .put("content", systemContent),
            )
        }

        val toolCallIdToName = mutableMapOf<String, String>()
        for (m in otherMsgs) {
            if (normalizeRole(m.role) != "assistant") continue
            for (tc in m.toolCalls.orEmpty()) {
                val tid = tc.id.trim()
                val fnName = tc.name.trim()
                if (tid.isNotEmpty() && fnName.isNotEmpty()) {
                    toolCallIdToName[tid] = fnName
                }
            }
        }

        fun lookupToolName(
            toolCallId: String,
            explicitName: String?,
        ): String {
            toolCallIdToName[toolCallId]?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            explicitName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            if (declaredToolNames.size == 1) return declaredToolNames.first()
            return FALLBACK_TOOL_FUNCTION_NAME
        }

        for (m in otherMsgs) {
            val role = normalizeRole(m.role) ?: continue
            val trimmed = m.content.trim()

            when (role) {
                "user" -> {
                    if (trimmed.isEmpty()) continue
                    result.put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", trimmed),
                    )
                }

                "assistant" -> {
                    val o = JSONObject().put("role", "assistant")
                    val toolCalls = m.toolCalls.orEmpty()

                    if (toolCalls.isNotEmpty()) {
                        val tcArr = JSONArray()

                        for (tc in toolCalls) {
                            val tid = tc.id.trim()
                            val fnName = tc.name.trim()
                            if (tid.isEmpty() || fnName.isEmpty()) {
                                Log.w(TAG, "drop malformed assistant tool_call: id='${tc.id}' name='${tc.name}'")
                                continue
                            }

                            tcArr.put(
                                JSONObject()
                                    .put("id", tid)
                                    .put("type", "function")
                                    .put(
                                        "function",
                                        JSONObject()
                                            .put("name", fnName)
                                            .put(
                                                "arguments",
                                                ToolCallArgumentNormalizer.normalize(tc.argumentsJson),
                                            ),
                                    ),
                            )
                        }

                        if (tcArr.length() == 0) {
                            if (trimmed.isEmpty()) continue
                            o.put("content", trimmed)
                        } else {
                            o.put("tool_calls", tcArr)
                            if (trimmed.isNotEmpty()) {
                                o.put("content", trimmed)
                            }
                        }

                        if (!o.has("tool_calls") && !o.has("content")) continue
                        result.put(o)
                    } else {
                        if (trimmed.isEmpty()) continue
                        o.put("content", trimmed)
                        result.put(o)
                    }
                }

                "tool" -> {
                    val rawId = m.toolCallId?.trim().orEmpty()
                    if (rawId.isEmpty()) {
                        Log.w(TAG, "drop tool message without tool_call_id")
                        continue
                    }

                    val nameForApi = lookupToolName(rawId, m.name).trim()
                    if (nameForApi.isEmpty()) {
                        Log.w(TAG, "drop tool message without resolvable name, id=$rawId")
                        continue
                    }

                    val contentOut = trimmed.ifEmpty { "(empty tool result)" }

                    result.put(
                        JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", rawId)
                            .put("name", nameForApi)
                            .put("content", contentOut),
                    )
                }

                "system" -> {
                    if (trimmed.isEmpty()) continue
                    result.put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", trimmed),
                    )
                }
            }
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            for (i in 0 until result.length()) {
                Log.d(TAG, "normalized[$i] = ${result.getJSONObject(i)}")
            }
        }

        return result
    }

    private fun parseToolCallsFromMessage(msg: JSONObject): List<LlmToolCall> {
        if (!msg.has("tool_calls")) return emptyList()
        val arr = msg.optJSONArray("tool_calls") ?: return emptyList()
        val out = mutableListOf<LlmToolCall>()

        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            var id =
                item.optString("id", "").trim().ifEmpty {
                    item.optString("tool_call_id", "").trim()
                }
            if (id.isEmpty()) id = "gen_$i"

            val fn = item.optJSONObject("function") ?: item.optJSONObject("function_call")
            var name = fn?.optString("name", "")?.trim().orEmpty()
            if (name.isEmpty()) name = fn?.optString("function_name", "")?.trim().orEmpty()
            if (name.isEmpty()) name = item.optString("name", "").trim()
            if (name.isEmpty()) continue

            val argumentsJson =
                if (fn != null) {
                    ToolCallArgumentNormalizer.normalize(extractFunctionArgumentsJson(fn))
                } else {
                    ToolCallArgumentNormalizer.normalize(item.optString("arguments", ""))
                }

            out.add(
                LlmToolCall(
                    id = id.trim(),
                    name = name.trim(),
                    argumentsJson = argumentsJson,
                ),
            )
        }

        return out
    }

    private fun extractFunctionArgumentsJson(fn: JSONObject): String {
        if (!fn.has("arguments") || fn.isNull("arguments")) {
            return "{}"
        }
        return when (val a = fn.get("arguments")) {
            is String -> a.trim().ifEmpty { "{}" }
            else -> a.toString()
        }
    }

    private fun normalizeRole(raw: String?): String? {
        val role = raw?.trim()?.lowercase() ?: return null
        return when (role) {
            "system" -> "system"
            "user", "human" -> "user"
            "assistant", "bot", "model" -> "assistant"
            "tool" -> "tool"
            else -> null
        }
    }

    private fun parseNonStreamResponse(raw: String): LlmResponse {
        val root = JSONObject(raw)

        if (root.has("error")) {
            val err = root.optJSONObject("error")
            val msg = err?.optString("message")?.takeIf { it.isNotEmpty() } ?: raw.take(500)
            throw IOException("API error: $msg")
        }

        if (!root.has("choices") || root.getJSONArray("choices").length() == 0) {
            throw IOException("Unexpected response (no choices): ${raw.take(400)}")
        }

        val choice = root.getJSONArray("choices").getJSONObject(0)
        val msg =
            choice.optJSONObject("message")
                ?: throw IOException("Unexpected response (no message): ${raw.take(400)}")

        val rawContent: String? =
            if (msg.has("content") && !msg.isNull("content")) {
                msg.getString("content")
            } else {
                null
            }

        val contentForHistory = mergeMinimaxReasoningIntoContent(msg, rawContent)

        val toolCalls = parseToolCallsFromMessage(msg)

        return LlmResponse(
            content = contentForHistory,
            toolCalls = toolCalls,
            finishReason = if (choice.has("finish_reason")) choice.getString("finish_reason") else null,
        )
    }

    /** MiniMax: when reasoning_split is true, thinking may appear only in reasoning_details; keep both for multi-turn / tools. */
    private fun extractReasoningDetailsText(msg: JSONObject): String {
        val arr = msg.optJSONArray("reasoning_details") ?: return ""
        val parts = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val t = o.optString("text", "").trim()
            if (t.isNotEmpty()) parts.add(t)
        }
        return parts.joinToString("\n").trim()
    }

    private fun mergeMinimaxReasoningIntoContent(
        msg: JSONObject,
        content: String?,
    ): String? {
        val reasoning = extractReasoningDetailsText(msg)
        val c = content?.trim().orEmpty()
        return when {
            reasoning.isEmpty() -> if (c.isEmpty()) null else c
            c.isEmpty() -> reasoning
            else -> "$reasoning\n\n$c"
        }
    }

    private fun parseSseChunk(data: String): StreamEvent? {
        val root = JSONObject(data)
        val choices = root.optJSONArray("choices") ?: return null
        if (choices.length() == 0) return null

        val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return null

        if (delta.has("content")) {
            val piece = delta.optString("content", "")
            if (piece.isNotEmpty()) return StreamEvent.Delta(piece)
        }

        if (delta.has("tool_calls")) {
            return StreamEvent.Progress("tool_calls…", toolHint = true)
        }

        return null
    }

    companion object {
        private const val TAG = "OpenAiCompatReq"
        private const val FALLBACK_TOOL_FUNCTION_NAME = "unknown_tool"
        private const val MAX_RETRIES = 3
        private const val RETRY_BASE_MS = 1000L
    }
}
