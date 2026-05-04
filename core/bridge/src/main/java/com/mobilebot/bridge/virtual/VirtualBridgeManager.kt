package com.mobilebot.bridge.virtual

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VirtualBridgeManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        enum class BridgeMode { REAL, VIRTUAL }

        private val modes: Map<String, BridgeMode> = loadConfig()

        fun isVirtual(bridgeName: String): Boolean =
            modes[bridgeName] == BridgeMode.VIRTUAL

        fun hasAnyVirtual(): Boolean = modes.values.any { it == BridgeMode.VIRTUAL }

        fun allModes(): Map<String, BridgeMode> = modes

        fun logModes() {
            Log.i(TAG, "=== VirtualBridgeManager config ===")
            for ((name, mode) in modes.entries.sortedBy { it.key }) {
                Log.i(TAG, "  $name -> $mode")
            }
            val virtualCount = modes.count { it.value == BridgeMode.VIRTUAL }
            Log.i(TAG, "  Total: ${modes.size} bridges, $virtualCount virtual")
            Log.i(TAG, "===================================")
        }

        private fun loadConfig(): Map<String, BridgeMode> {
            return try {
                val text = context.assets.open(CONFIG_FILE).bufferedReader().use { it.readText() }
                val root = JSONObject(text)
                val bridges = root.getJSONObject("bridges")
                buildMap {
                    for (key in bridges.keys()) {
                        val value = bridges.getString(key).lowercase()
                        put(key, if (value == "virtual") BridgeMode.VIRTUAL else BridgeMode.REAL)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load $CONFIG_FILE, defaulting all bridges to REAL", e)
                emptyMap()
            }
        }

        companion object {
            private const val TAG = "VirtualBridgeManager"
            private const val CONFIG_FILE = "virtual_bridge_config.json"
        }
    }
