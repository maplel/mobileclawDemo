package com.mobilebot.data.context

import android.content.Context
import com.mobilebot.domain.tools.CallTranscript
import com.mobilebot.domain.tools.CallTranscriptRepository
import com.mobilebot.domain.tools.CallTranscriptTask
import com.mobilebot.systemruntime.VoiceCallSessionRuntime
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class AssetCallTranscriptRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val voiceCallSessionRuntime: VoiceCallSessionRuntime,
    ) : CallTranscriptRepository {
        private val transcripts: List<CallTranscript> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            loadTranscripts()
        }

        override suspend fun findTranscript(
            audioRef: String,
            contact: String,
        ): CallTranscript? {
            val ref = audioRef.trim()
            if (ref.isBlank()) return null
            val normalizedContact = contact.trim()
            voiceCallSessionRuntime.findTranscript(ref, normalizedContact)?.let { return it }
            return transcripts.firstOrNull {
                it.audioRef == ref &&
                    (normalizedContact.isBlank() || it.contact.equals(normalizedContact, ignoreCase = true))
            } ?: transcripts.firstOrNull { it.audioRef == ref }
        }

        private fun loadTranscripts(): List<CallTranscript> {
            val skillDirs = runCatching { context.assets.list(SKILLS_ROOT).orEmpty().toList() }.getOrDefault(emptyList())
            return skillDirs.flatMap { dir ->
                val path = "$SKILLS_ROOT/$dir/$CONTEXT_FILE"
                val raw = runCatching {
                    context.assets.open(path).bufferedReader().use { it.readText() }
                }.getOrNull() ?: return@flatMap emptyList()
                parseTranscripts(raw)
            }
        }

        private fun parseTranscripts(raw: String): List<CallTranscript> {
            val root = JSONObject(raw.trimStart('\uFEFF'))
            val items = root.optJSONArray("callTranscripts") ?: return emptyList()
            return (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                val audioRef = item.optString("audioRef").trim()
                if (audioRef.isBlank()) return@mapNotNull null
                CallTranscript(
                    audioRef = audioRef,
                    contact = item.optString("contact").trim(),
                    durationSeconds = item.optInt("durationSeconds", 0),
                    transcript = item.optString("transcript").trim(),
                    tasks = parseTasks(item.optJSONArray("tasks")),
                )
            }
        }

        private fun parseTasks(items: JSONArray?): List<CallTranscriptTask> {
            if (items == null) return emptyList()
            return (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                CallTranscriptTask(
                    title = item.optString("title").trim(),
                    priority = item.optString("priority", "normal").trim(),
                    items = item.optJSONArray("items").toStringList(),
                )
            }
        }

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { index -> optString(index).trim().takeIf { it.isNotEmpty() } }
        }

        private companion object {
            const val SKILLS_ROOT = "skills/md"
            const val CONTEXT_FILE = "AGENT_CONTEXT.json"
        }
    }
