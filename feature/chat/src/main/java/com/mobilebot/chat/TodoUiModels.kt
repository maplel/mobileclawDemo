package com.mobilebot.chat

enum class TodoDisplayStatus { PENDING, RUNNING, COMPLETED, FAILED }

data class TodoDisplayItem(
    val id: String,
    val text: String,
    val status: TodoDisplayStatus,
)
