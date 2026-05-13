package com.mobilebot.domain.interaction

import org.json.JSONArray
import org.json.JSONTokener
import org.json.JSONObject

data class ActionOption(
    val label: String,
    val value: String,
)

object ActionPromptCodec {
    const val MESSAGE_TOOL_NAME = "action_prompt"

    fun resolveOptions(
        question: String,
        explicitOptions: List<ActionOption> = emptyList(),
    ): List<ActionOption> {
        val normalizedExplicit = normalize(explicitOptions)
        if (normalizedExplicit.isNotEmpty()) return normalizedExplicit
        return inferBinaryOptions(question)
    }

    fun toJson(options: List<ActionOption>): String =
        JSONArray().apply {
            for (option in normalize(options)) {
                put(
                    JSONObject()
                        .put("label", option.label)
                        .put("value", option.value),
                )
            }
        }.toString()

    fun parseJson(json: String?): List<ActionOption> {
        if (json.isNullOrBlank()) return emptyList()
        val direct = parseRoot(json)
        if (direct.isNotEmpty()) return direct
        val extracted = extractFirstJsonBlock(json)
        if (!extracted.isNullOrBlank()) {
            return parseRoot(extracted)
        }
        return emptyList()
    }

    fun matchesPresentedOption(
        reply: String,
        options: List<ActionOption>,
    ): Boolean {
        val normalizedReply = cleanOptionText(reply)
        if (normalizedReply.isEmpty()) return false
        return normalize(options).any { option ->
            normalizedReply.equals(option.label, ignoreCase = true) ||
                normalizedReply.equals(option.value, ignoreCase = true)
        }
    }

    fun normalizeExplicitOptions(options: List<ActionOption>): List<ActionOption> = normalize(options)

    fun cleanOptionText(value: String): String =
        value
            .trim()
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")
            .replace(Regex("""\s*,?\s*or\s*$""", RegexOption.IGNORE_CASE), "")
            .trim { char ->
                char.isWhitespace() ||
                    char == '`' ||
                    char == '*' ||
                    char == '_' ||
                    char == '"' ||
                    char == '\''
            }.trim()

    private fun parseRoot(raw: String): List<ActionOption> =
        runCatching {
            when (val root = JSONTokener(raw).nextValue()) {
                is JSONArray -> parseArray(root)
                is JSONObject -> {
                    val arr =
                        root.optJSONArray("options")
                            ?: root.optJSONArray("choices")
                            ?: root.optJSONArray("actions")
                            ?: JSONArray()
                    parseArray(arr)
                }
                else -> emptyList()
            }
        }.getOrDefault(emptyList()).let(::normalize)

    private fun parseArray(arr: JSONArray): List<ActionOption> =
        buildList {
            for (i in 0 until arr.length()) {
                when (val item = arr.opt(i)) {
                    is JSONObject -> {
                        val label =
                            item.optString("label")
                                .ifBlank { item.optString("text") }
                                .ifBlank { item.optString("title") }
                                .ifBlank { item.optString("name") }
                                .ifBlank { item.optString("value") }
                                .trim()
                        val value =
                            item.optString("value")
                                .ifBlank { label }
                                .trim()
                        if (label.isNotEmpty() && value.isNotEmpty()) {
                            add(ActionOption(label = label, value = value))
                        }
                    }
                    is String -> {
                        val value = item.trim()
                        if (value.isNotEmpty()) add(ActionOption(label = value, value = value))
                    }
                }
            }
        }

    private fun normalize(options: List<ActionOption>): List<ActionOption> =
        options
            .mapNotNull { option ->
                val rawLabel = cleanOptionText(option.label)
                val label = rawLabel
                val cleanedValue = cleanOptionText(option.value)
                val value = cleanedValue.ifBlank { label }
                if (label.isEmpty() || value.isEmpty()) null else ActionOption(label = label, value = value)
            }.distinctBy { "${it.label.lowercase()}|${it.value.lowercase()}" }

    private fun inferBinaryOptions(question: String): List<ActionOption> {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (!looksBinaryQuestion(trimmed)) return emptyList()

        val chinese = containsChinese(trimmed)
        return when {
            containsAny(trimmed.lowercase(), listOf("sms", "send")) || containsAny(trimmed, listOf("\u77ed\u4fe1", "\u53d1\u9001")) ->
                listOf(
                    ActionOption(if (chinese) "\u53d1\u9001" else "Send", "yes"),
                    ActionOption(if (chinese) "\u53d6\u6d88" else "Cancel", "no"),
                )
            containsAny(trimmed.lowercase(), listOf("permission", "allow", "access")) || containsAny(trimmed, listOf("\u6743\u9650", "\u5141\u8bb8", "\u8bbf\u95ee")) ->
                listOf(
                    ActionOption(if (chinese) "\u5141\u8bb8" else "Allow", "yes"),
                    ActionOption(if (chinese) "\u62d2\u7edd" else "Deny", "no"),
                )
            containsAny(trimmed.lowercase(), listOf("continue", "proceed")) || containsAny(trimmed, listOf("\u7ee7\u7eed", "\u6267\u884c")) ->
                listOf(
                    ActionOption(if (chinese) "\u7ee7\u7eed" else "Continue", "yes"),
                    ActionOption(if (chinese) "\u53d6\u6d88" else "Cancel", "no"),
                )
            else ->
                listOf(
                    ActionOption(if (chinese) "\u662f" else "Yes", "yes"),
                    ActionOption(if (chinese) "\u5426" else "No", "no"),
                )
        }
    }

    private fun looksBinaryQuestion(question: String): Boolean {
        val lower = question.lowercase()
        return (lower.contains("yes") && lower.contains("no")) ||
            question.contains("\u662f\u5426") ||
            question.contains("\u8981\u4e0d\u8981") ||
            question.contains("\u662f\u4e0d\u662f") ||
            question.contains("\u786e\u8ba4") ||
            question.contains("\u7ee7\u7eed\u5417") ||
            question.contains("\u53d1\u9001\u5417") ||
            question.contains("\u5141\u8bb8\u5417") ||
            (lower.contains("confirm") && (lower.contains("cancel") || lower.contains("send") || lower.contains("proceed"))) ||
            (lower.contains("allow") && (lower.contains("deny") || lower.contains("cancel"))) ||
            (lower.contains("continue") && lower.contains("?")) ||
            lower.contains("reply with 'yes'") ||
            lower.contains("reply with \"yes\"")
    }

    private fun containsChinese(text: String): Boolean =
        text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }

    private fun containsAny(text: String, keywords: List<String>): Boolean =
        keywords.any { text.contains(it) }

    private fun extractFirstJsonBlock(raw: String): String? {
        val trimmed = raw.trim()
        val objectStart = trimmed.indexOf('{')
        if (objectStart >= 0) {
            balancedSlice(trimmed, objectStart, '{', '}')?.let { return it }
        }
        val arrayStart = trimmed.indexOf('[')
        if (arrayStart >= 0) {
            balancedSlice(trimmed, arrayStart, '[', ']')?.let { return it }
        }
        return null
    }

    private fun balancedSlice(
        source: String,
        start: Int,
        open: Char,
        close: Char,
    ): String? {
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until source.length) {
            val c = source[i]
            if (escape) {
                escape = false
                continue
            }
            if (inString) {
                when (c) {
                    '\\' -> escape = true
                    '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return source.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
