package com.mobilebot.network

import com.mobilebot.model.StreamEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SSE client for nanobot Python [mobile] channel: POST /mobile/v1/chat/stream
 */
@Singleton
class NanobotStreamClient
    @Inject
    constructor() {
        private val json = "application/json; charset=utf-8".toMediaType()

        private val client: OkHttpClient by lazy {
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        fun streamChat(
            baseUrl: String,
            apiKey: String,
            deviceId: String,
            message: String,
        ): Flow<StreamEvent> =
            callbackFlow {
                val root = baseUrl.trimEnd('/')
                val payload =
                    JSONObject()
                        .put("message", message)
                        .put("device_id", deviceId)
                        .toString()
                val req =
                    Request
                        .Builder()
                        .url("$root/mobile/v1/chat/stream")
                        .header("Authorization", "Bearer $apiKey")
                        .post(payload.toRequestBody(json))
                        .build()
                val call = client.newCall(req)
                call.enqueue(
                    object : Callback {
                        override fun onFailure(
                            call: Call,
                            e: IOException,
                        ) {
                            runCatching {
                                trySend(StreamEvent.Error(e.message ?: "network"))
                                close(e)
                            }
                        }

                        override fun onResponse(
                            call: Call,
                            response: Response,
                        ) {
                            try {
                                if (!response.isSuccessful) {
                                    val err = response.body?.string().orEmpty()
                                    runCatching {
                                        trySend(StreamEvent.Error("HTTP ${response.code}: $err"))
                                        close()
                                    }
                                    return
                                }
                                val source = response.body?.source() ?: run {
                                    runCatching { close() }
                                    return
                                }
                                try {
                                    while (!source.exhausted()) {
                                        val line = source.readUtf8Line() ?: break
                                        if (!line.startsWith("data: ")) continue
                                        val data = line.removePrefix("data: ").trim()
                                        val obj = runCatching { JSONObject(data) }.getOrNull() ?: continue
                                        when (obj.optString("type")) {
                                            "delta" ->
                                                trySend(
                                                    StreamEvent.Delta(obj.optString("text")),
                                                )
                                            "progress" ->
                                                trySend(
                                                    StreamEvent.Progress(
                                                        obj.optString("content"),
                                                        obj.optBoolean("tool_hint"),
                                                    ),
                                                )
                                            "stream_end" ->
                                                trySend(
                                                    StreamEvent.StreamEnd(obj.optBoolean("resuming")),
                                                )
                                            "done" ->
                                                trySend(StreamEvent.Done(obj.optString("reply")))
                                            "error" ->
                                                trySend(StreamEvent.Error(obj.optString("error")))
                                        }
                                    }
                                } catch (e: Exception) {
                                    runCatching {
                                        trySend(StreamEvent.Error(e.message ?: "stream"))
                                    }
                                } finally {
                                    response.close()
                                    runCatching { close() }
                                }
                            } catch (t: Throwable) {
                                runCatching {
                                    trySend(StreamEvent.Error(t.message ?: "stream"))
                                    close(t as? Exception ?: IOException(t))
                                }
                            }
                        }
                    },
                )
                awaitClose { call.cancel() }
            }
    }
