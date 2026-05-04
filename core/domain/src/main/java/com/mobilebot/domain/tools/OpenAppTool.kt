package com.mobilebot.domain.tools

import com.mobilebot.bridge.DeviceCapabilityBridge
import com.mobilebot.model.ToolDefinition
import com.mobilebot.model.ToolResult
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class OpenAppTool
    @Inject
    constructor(
        private val bridge: DeviceCapabilityBridge,
    ) : Tool {
        override val name: String = "open_app"

        override val definition =
            ToolDefinition(
                name = name,
                description = "Launch an installed app by name. The tool fuzzy-matches the name. 按名称打开已安装的应用。",
                parametersSchema =
                    """{"type":"object","properties":{"name":{"type":"string","description":"App name or keyword (e.g. '微信', 'Chrome')"}},"required":["name"]}""",
            )

        override suspend fun execute(argumentsJson: String): ToolResult {
            return try {
                val query = JSONObject(argumentsJson).getString("name").trim()
                if (query.isEmpty()) {
                    return ToolResult(ok = false, message = "Missing app name.")
                }
                val matches = bridge.system.resolveAppPackage(query)
                when {
                    matches.isEmpty() ->
                        ToolResult(ok = false, message = "No installed app found matching \"$query\".")
                    matches.size == 1 -> {
                        val app = matches.first()
                        val ok = bridge.system.openApp(app.packageName)
                        if (ok) {
                            ToolResult(ok = true, message = "Opened ${app.label}.")
                        } else {
                            ToolResult(ok = false, message = "Found ${app.label} but could not launch it.")
                        }
                    }
                    else -> {
                        val arr = JSONArray()
                        matches.forEach { m ->
                            arr.put(JSONObject().put("name", m.label).put("package", m.packageName))
                        }
                        ToolResult(
                            ok = false,
                            message = "Multiple apps match \"$query\". Ask the user which one.",
                            dataJson = arr.toString(),
                        )
                    }
                }
            } catch (e: Exception) {
                ToolResult(ok = false, message = e.message ?: "open_app failed")
            }
        }
    }
