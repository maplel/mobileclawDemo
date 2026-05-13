package com.mobilebot.network

import org.json.JSONObject

internal object ToolCallArgumentNormalizer {
    fun normalize(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return "{}"
        if (isJsonObject(trimmed)) return trimmed

        val repaired = repairMissingClosers(trimmed)
        if (repaired != null && isJsonObject(repaired)) return repaired

        return "{}"
    }

    private fun isJsonObject(value: String): Boolean =
        runCatching {
            JSONObject(value)
            true
        }.getOrDefault(false)

    private fun repairMissingClosers(value: String): String? {
        if (!value.startsWith("{")) return null

        val closers = ArrayDeque<Char>()
        var inString = false
        var escaping = false

        for (ch in value) {
            if (inString) {
                when {
                    escaping -> escaping = false
                    ch == '\\' -> escaping = true
                    ch == '"' -> inString = false
                }
                continue
            }

            when (ch) {
                '"' -> inString = true
                '{' -> closers.addLast('}')
                '[' -> closers.addLast(']')
                '}', ']' -> {
                    if (closers.isEmpty()) return null
                    val expected = closers.removeLast()
                    if (expected != ch) return null
                }
            }
        }

        if (inString || escaping) return null

        val suffix = buildString {
            while (closers.isNotEmpty()) {
                append(closers.removeLast())
            }
        }
        return value + suffix
    }
}
