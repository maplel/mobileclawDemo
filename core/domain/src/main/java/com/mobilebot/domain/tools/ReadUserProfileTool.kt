package com.mobilebot.domain.tools

import com.mobilebot.domain.profile.UserProfileStore
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONObject
import javax.inject.Inject

class ReadUserProfileTool
    @Inject
    constructor(
        private val profileStore: UserProfileStore,
    ) : Tool {
        override val name: String = "read_user_profile"

        override val definition =
            ToolDefinition(
                name = name,
                description = "Read user's pre-authorized personal data. " +
                    "Categories: insurance, membership, preferences, emergency_contacts, " +
                    "vehicles, health, trip_plans. Omit key to get all entries in a category.",
                parametersSchema =
                    """{"type":"object","properties":{"category":{"type":"string","description":"Data category"},"key":{"type":"string","description":"Specific key within the category (optional)"}},"required":["category"]}""",
            )

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val args = JSONObject(argumentsJson)
                val category = args.getString("category").trim()
                if (category.isEmpty()) {
                    return ToolResult(ok = false, message = "category is required")
                }

                val key = args.optString("key", "").trim().ifEmpty { null }
                val value = profileStore.get(category, key)

                if (value != null) {
                    ToolResult(
                        ok = true,
                        message = value,
                        dataJson = JSONObject().apply {
                            put("category", category)
                            if (key != null) put("key", key)
                            put("value", value)
                        }.toString(),
                    )
                } else {
                    val hint = if (key != null) "'$key' in '$category'" else "'$category'"
                    ToolResult(ok = true, message = "No data found for $hint")
                }
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "read_user_profile failed")
            }
        }
    }
