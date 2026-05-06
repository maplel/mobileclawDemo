package com.mobilebot.domain.tools

import com.mobilebot.domain.memory.MemoryFacade
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class RecallFactsTool
    @Inject
    constructor(
        private val memory: MemoryFacade,
    ) : Tool {
        override val name: String = "recall_facts"

        override val definition =
            ToolDefinition(
                name = name,
                description = "Search durable facts previously stored in the local memory database. " +
                    "Use before asking the user for information that may already be known, such as pet names, " +
                    "last grooming dates, pet food inventory, preferences, and prior purchases.",
                parametersSchema =
                    """{"type":"object","properties":{"query":{"type":"string","description":"Search text, e.g. pet, dog_food, 元宝, last_grooming"},"limit":{"type":"integer","description":"Maximum facts to return, default 5"}},"required":["query"]}""",
            )

        override val risk = ToolRisk.LOW

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val args = JSONObject(argumentsJson)
                val query = args.getString("query").trim()
                val limit = args.optInt("limit", 5).coerceIn(1, 20)
                if (query.isEmpty()) {
                    return ToolResult(ok = false, message = "query is required")
                }

                val facts = memory.retrieveFacts(query, limit)
                val json = JSONArray()
                for (fact in facts) {
                    json.put(
                        JSONObject()
                            .put("namespace", fact.namespace)
                            .put("key", fact.key)
                            .put("value", fact.value)
                            .put("confidence", fact.confidence),
                    )
                }

                ToolResult(
                    ok = true,
                    message = if (facts.isEmpty()) "No remembered facts found." else "Remembered facts found.",
                    dataJson = json.toString(),
                )
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "recall_facts failed")
            }
        }
    }
