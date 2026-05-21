package com.mobilebot.network

import javax.inject.Inject
import javax.inject.Singleton

data class RoleCallTurn(
    val speaker: String,
    val text: String,
)

data class RoleCallRequest(
    val personaId: String,
    val personaInstruction: String,
    val contactName: String,
    val transcript: List<RoleCallTurn>,
    val latestUserText: String,
    val openingTurn: Boolean = false,
)

data class RoleCallReply(
    val text: String,
)

interface RoleCallModel {
    suspend fun nextReply(request: RoleCallRequest): RoleCallReply
}

interface SpeechRecognizer {
    suspend fun transcribeAudio(
        audioBytes: ByteArray,
        mimeType: String = "audio/wav",
    ): String
}

data class SpeechSynthesisResult(
    val audioUrl: String,
)

interface SpeechSynthesizer {
    suspend fun synthesizeSpeech(
        text: String,
        voice: String = "Cherry",
        languageType: String = "Chinese",
    ): SpeechSynthesisResult
}

@Singleton
class QwenRoleCallModel
    @Inject
    constructor(
        private val llmClient: LlmClient,
    ) : RoleCallModel {
        override suspend fun nextReply(request: RoleCallRequest): RoleCallReply {
            val transcriptText = request.transcript.joinToString("\n") {
                "${it.speaker}: ${it.text}"
            }.ifBlank { "(no prior turns)" }
            val userPrompt = buildString {
                appendLine("contactName: ${request.contactName}")
                appendLine("personaId: ${request.personaId}")
                appendLine("openingTurn: ${request.openingTurn}")
                appendLine("transcript:")
                appendLine(transcriptText)
                if (!request.openingTurn) {
                    appendLine("latestUserText: ${request.latestUserText}")
                }
                appendLine("Reply as the caller only. Keep it under 45 Chinese characters.")
            }
            val response = llmClient.chat(
                messages = listOf(
                    LlmMessage(
                        role = "system",
                        content = request.personaInstruction,
                    ),
                    LlmMessage(
                        role = "user",
                        content = userPrompt,
                    ),
                ),
                tools = null,
                model = QWEN_TURBO,
                maxTokens = 120,
            )
            val text = response.content
                ?.trim()
                ?.trim('"', '“', '”')
                .orEmpty()
            return RoleCallReply(text)
        }

        private companion object {
            const val QWEN_TURBO = "qwen-turbo"
        }
    }
