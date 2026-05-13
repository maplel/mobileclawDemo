package com.mobilebot.domain.memory

data class MemoryFact(
    val id: String,
    val namespace: String,
    val key: String,
    val value: String,
    val confidence: Float = 1f,
)
