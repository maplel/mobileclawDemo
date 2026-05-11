package com.mobilebot.domain

fun interface LlmConfigurator {
    suspend fun beforeRequest()
}
