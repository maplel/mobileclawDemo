package com.mobilebot.systemruntime

import org.json.JSONObject

interface SystemRuntimeActions {
    suspend fun execute(
        action: String,
        params: JSONObject,
    ): SystemRuntimeResult
}
