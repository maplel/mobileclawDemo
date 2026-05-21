package com.mobilebot.systemruntime

import com.mobilebot.domain.tools.CallTranscript
import com.mobilebot.domain.tools.CallTranscriptTask
import javax.inject.Inject
import javax.inject.Singleton

enum class VoiceCallPhase {
    RINGING,
    CONNECTED,
    LISTENING,
    THINKING,
    SPEAKING,
    ENDED,
}

enum class VoiceCallSpeaker {
    USER,
    AGENT,
}

data class VoiceCallTurn(
    val speaker: VoiceCallSpeaker,
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis(),
)

data class VoiceCallSession(
    val id: String,
    val contact: String,
    val personaId: String,
    val startedAtMillis: Long,
    val phase: VoiceCallPhase,
    val turns: List<VoiceCallTurn> = emptyList(),
) {
    val audioRef: String = runtimeAudioRef(id)

    companion object {
        fun runtimeAudioRef(sessionId: String): String = "runtime-call:$sessionId"
    }
}

@Singleton
class VoiceCallSessionRuntime
    @Inject
    constructor() {
        private val sessions = linkedMapOf<String, VoiceCallSession>()
        private val transcripts = linkedMapOf<String, CallTranscript>()

        @Synchronized
        fun startSession(
            sessionId: String,
            contact: String,
            personaId: String,
        ): VoiceCallSession {
            val cleanId = sessionId.ifBlank { "call-${System.currentTimeMillis()}" }
            val session = VoiceCallSession(
                id = cleanId,
                contact = contact.ifBlank { "Caller" },
                personaId = personaId.ifBlank { contact.ifBlank { "caller" }.lowercase() },
                startedAtMillis = System.currentTimeMillis(),
                phase = VoiceCallPhase.CONNECTED,
            )
            sessions[cleanId] = session
            return session
        }

        @Synchronized
        fun setPhase(
            sessionId: String,
            phase: VoiceCallPhase,
        ): VoiceCallSession? =
            sessions[sessionId]?.copy(phase = phase)?.also { sessions[sessionId] = it }

        @Synchronized
        fun appendTurn(
            sessionId: String,
            speaker: VoiceCallSpeaker,
            text: String,
        ): VoiceCallSession? {
            val cleanText = text.trim()
            if (cleanText.isBlank()) return sessions[sessionId]
            val current = sessions[sessionId] ?: return null
            val updated = current.copy(
                phase = when (speaker) {
                    VoiceCallSpeaker.USER -> VoiceCallPhase.THINKING
                    VoiceCallSpeaker.AGENT -> VoiceCallPhase.LISTENING
                },
                turns = current.turns + VoiceCallTurn(speaker, cleanText),
            )
            sessions[sessionId] = updated
            return updated
        }

        @Synchronized
        fun currentSession(sessionId: String): VoiceCallSession? = sessions[sessionId]

        @Synchronized
        fun finishSession(sessionId: String): CallTranscript? {
            val current = sessions[sessionId] ?: return null
            val ended = current.copy(phase = VoiceCallPhase.ENDED)
            sessions[sessionId] = ended
            val transcript = ended.toTranscript()
            transcripts[transcript.audioRef] = transcript
            return transcript
        }

        @Synchronized
        fun findTranscript(
            audioRef: String,
            contact: String,
        ): CallTranscript? {
            val ref = audioRef.trim()
            if (ref.isBlank()) return null
            val transcript = transcripts[ref] ?: return null
            val cleanContact = contact.trim()
            return if (cleanContact.isBlank() || transcript.contact.equals(cleanContact, ignoreCase = true)) {
                transcript
            } else {
                null
            }
        }

        private fun VoiceCallSession.toTranscript(): CallTranscript {
            val lines = turns.joinToString("\n") { turn ->
                val speaker = when (turn.speaker) {
                    VoiceCallSpeaker.USER -> "用户"
                    VoiceCallSpeaker.AGENT -> contact
                }
                "$speaker：${turn.text}"
            }
            return CallTranscript(
                audioRef = audioRef,
                contact = contact,
                durationSeconds = estimatedDurationSeconds(),
                transcript = lines,
                tasks = extractTasks(lines),
            )
        }

        private fun VoiceCallSession.estimatedDurationSeconds(): Int {
            val elapsed = ((System.currentTimeMillis() - startedAtMillis) / 1000L).toInt()
            return elapsed.coerceAtLeast(turns.size * 6)
        }

        private fun extractTasks(transcript: String): List<CallTranscriptTask> {
            if (hasUserDeclinedShopping(transcript)) return emptyList()
            val items = buildList {
                if (transcript.contains("低脂牛奶")) add("低脂牛奶")
                if (transcript.contains("洗衣液")) add("常用洗衣液")
                if (transcript.contains("水果")) add("水果")
            }.distinct()
            if (items.isEmpty()) return emptyList()
            return listOf(
                CallTranscriptTask(
                    title = "家庭采购",
                    priority = "normal",
                    items = items,
                ),
            )
        }

        private fun hasUserDeclinedShopping(transcript: String): Boolean =
            transcript.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("用户：") || it.startsWith("你：") || it.startsWith("User:", ignoreCase = true) }
                .any { line ->
                    val lower = line.lowercase()
                    listOf(
                        "不买不买",
                        "先不买",
                        "不买了",
                        "别买了",
                        "不用买了",
                        "不用下单",
                        "不下单",
                        "取消采购",
                        "先别买",
                        "不要买了",
                        "no need to buy",
                        "don't buy",
                        "do not buy",
                        "skip purchase",
                    ).any { lower.contains(it.lowercase()) } ||
                        Regex("""(^|[：:\s，,。])不买(?:不买)?(?:[。！!，,]|\s|$)""").containsMatchIn(line)
                }
    }
