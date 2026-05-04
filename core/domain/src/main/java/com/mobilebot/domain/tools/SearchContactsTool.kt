package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class SearchContactsTool
    @Inject
    constructor(
        private val bridge: DeviceCapabilityBridge,
    ) : Tool {
        override val name: String = "search_contacts"

        override val requiredCapabilities: Set<String> = setOf("contacts.read")

        override val definition =
            ToolDefinition(
                name = name,
                description = "Search device contacts by name or phone fragment.",
                parametersSchema = """{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}""",
            )

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val query = JSONObject(argumentsJson).getString("query").trim()
                if (query.isEmpty()) {
                    return ToolResult(ok = false, message = "Missing query")
                }
                val rows = resolveMatches(query, limit = 15)
                if (rows.isEmpty()) {
                    return ToolResult(
                        ok = true,
                        message = "No contacts matched.",
                        dataJson = "[]",
                    )
                }
                val arr = JSONArray()
                for (row in rows) {
                    val parts = row.split("|", limit = 2)
                    val name = parts.getOrElse(0) { "" }
                    val phone = parts.getOrElse(1) { "" }
                    arr.put(
                        JSONObject()
                            .put("displayName", name)
                            .put("phoneNumber", phone),
                    )
                }
                val dataJson = arr.toString()
                ToolResult(ok = true, message = "Found ${rows.size} contact row(s).", dataJson = dataJson)
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "search_contacts failed")
            }
        }

        private suspend fun resolveMatches(
            rawQuery: String,
            limit: Int,
        ): List<String> {
            val out = LinkedHashSet<String>()
            for (candidate in extractQueryCandidates(rawQuery)) {
                val remaining = limit - out.size
                if (remaining <= 0) break
                val rows = bridge.contacts.searchContacts(candidate, limit = remaining)
                if (rows.isEmpty()) continue
                out.addAll(rows)
                if (out.isNotEmpty()) break
            }
            return out.toList()
        }

        companion object {
            private val directNamePatterns =
                listOf(
                    Regex(
                        """(?:给|替|帮我给|帮忙给|请给)([\p{IsHan}A-Za-z][\p{IsHan}A-Za-z0-9·• ]{0,19}?)(?:发短信|发消息|发个短信|发送短信|打电话|拨打电话|拨号|联系|发信息|短信|消息|电话)""",
                    ),
                    Regex(
                        """(?:发短信|发消息|发个短信|发送短信|打电话|拨打电话|拨号|联系|发信息)(?:给|给到)?([\p{IsHan}A-Za-z][\p{IsHan}A-Za-z0-9·• ]{0,19})""",
                    ),
                    Regex(
                        """(?:找(?:联系人)?|查找(?:联系人)?|搜索(?:联系人)?|联系)([\p{IsHan}A-Za-z][\p{IsHan}A-Za-z0-9·• ]{0,19})""",
                    ),
                )

            internal fun extractQueryCandidates(rawQuery: String): List<String> {
                val query = rawQuery.trim()
                if (query.isEmpty()) return emptyList()

                val out = LinkedHashSet<String>()
                for (pattern in directNamePatterns) {
                    pattern.find(query)?.groupValues?.getOrNull(1)?.let { addCandidate(out, it) }
                }

                Regex("""["“”'‘’]([\p{IsHan}A-Za-z0-9·• ]{1,20})["“”'‘’]""")
                    .findAll(query)
                    .forEach { addCandidate(out, it.groupValues[1]) }

                val digits = query.filter { it.isDigit() }
                if (digits.length >= 3) {
                    out.add(digits)
                }

                if (query.length <= 8) {
                    addCandidate(out, query)
                }

                addCandidate(out, query)
                return out.toList()
            }

            private fun addCandidate(
                out: LinkedHashSet<String>,
                candidate: String?,
            ) {
                val cleaned =
                    candidate
                        ?.trim()
                        ?.trim('"', '\'', '`', '“', '”', '‘', '’', ',', '.', '，', '。', '！', '？', '!', '?')
                        ?.replace(Regex("""\s+"""), " ")
                        ?.trim()
                        .orEmpty()
                if (cleaned.isNotEmpty()) {
                    out.add(cleaned)
                }
            }
        }
    }
