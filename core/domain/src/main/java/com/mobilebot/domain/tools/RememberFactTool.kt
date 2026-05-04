package com.mobilebot.domain.tools

import com.mobilebot.domain.memory.MemoryFacade
import com.mobilebot.domain.memory.MemoryFact
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class RememberFactTool
    @Inject
    constructor(
        private val memory: MemoryFacade,
    ) : Tool {
        override val name: String = "remember_fact"

        override val definition =
            ToolDefinition(
                name = name,
                description = "Persist a durable user or domain fact in the local memory database. " +
                    "Use for stable facts the assistant should remember later, such as pet names, " +
                    "last grooming dates, pet food inventory, purchased products, preferences, and IDs.",
                parametersSchema =
                    """{"type":"object","properties":{"namespace":{"type":"string","description":"Fact namespace, e.g. pets, pet_inventory, preferences"},"key":{"type":"string","description":"Stable fact key, e.g. pet.yuanyuan.name or pet.yuanyuan.last_grooming_at"},"value":{"type":"string","description":"Fact value as concise text or JSON"},"confidence":{"type":"number","description":"Confidence from 0 to 1, defaults to 1"}},"required":["namespace","key","value"]}""",
            )

        override val risk = ToolRisk.LOW

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val args = JSONObject(argumentsJson)
                val namespace = args.getString("namespace").trim()
                val key = args.getString("key").trim()
                val value = args.getString("value").trim()
                val confidence = args.optDouble("confidence", 1.0).toFloat().coerceIn(0f, 1f)
                if (namespace.isEmpty() || key.isEmpty() || value.isEmpty()) {
                    return ToolResult(ok = false, message = "namespace, key, and value are required")
                }

                memory.writeFact(
                    MemoryFact(
                        id = "$namespace:$key",
                        namespace = namespace,
                        key = key,
                        value = value,
                        confidence = confidence,
                    ),
                )
                ToolResult(ok = true, message = "Remembered fact: $namespace.$key")
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "remember_fact failed")
            }
        }
    }
