package com.mobilebot.domain.tools

import com.mobilebot.domain.memory.PersistentMemoryManager
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

/**
 * Tool for the LLM to explicitly query stored memories.
 *
 * This complements automatic memory injection — the LLM can use this to
 * recall specific details it doesn't see in the system prompt.
 */
class RecallMemoriesTool
    @Inject
    constructor(
        private val persistentMemory: PersistentMemoryManager,
    ) : Tool {
        override val name: String = "recall_memories"

        override val definition =
            ToolDefinition(
                name = name,
                description = "Search stored memories by relevance to a query. " +
                    "Use this to recall specific information the user has previously saved, " +
                    "such as preferences, project context, or reference details. " +
                    "Related memories are also automatically injected into each turn.",
                parametersSchema =
                    """{"type":"object","properties":{"query":{"type":"string","description":"What to search for in memories"},"limit":{"type":"integer","description":"Maximum number of memories to return (default 5)","default":5}},"required":["query"]}""",
            )

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val args = JSONObject(argumentsJson)
                val query = args.getString("query").trim()
                if (query.isEmpty()) {
                    return ToolResult(ok = false, message = "query is required")
                }
                val limit = args.optInt("limit", 5).coerceIn(1, 20)

                val memories = persistentMemory.recallRelevant(query = query, limit = limit)

                if (memories.isEmpty()) {
                    return ToolResult(ok = true, message = "No memories found matching '$query'.")
                }

                val resultJson = JSONObject().apply {
                    put("query", query)
                    val arr = org.json.JSONArray()
                    for (mem in memories) {
                        arr.put(JSONObject().apply {
                            put("filename", mem.filename)
                            put("type", mem.type?.let { com.mobilebot.domain.memory.MemoryType.toLabel(it) })
                            put("description", mem.description)
                            put("content", mem.content)
                        })
                    }
                    put("memories", arr)
                }

                val summary = memories.joinToString("\n") { mem ->
                    val tag = mem.type?.let { "[${com.mobilebot.domain.memory.MemoryType.toLabel(it)}] " } ?: ""
                    "- $tag${mem.description ?: mem.filename}: ${mem.content.take(200)}"
                }

                ToolResult(
                    ok = true,
                    message = "Found ${memories.size} memory/memories:\n$summary",
                    dataJson = resultJson.toString(),
                )
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "recall_memories failed")
            }
        }
    }
