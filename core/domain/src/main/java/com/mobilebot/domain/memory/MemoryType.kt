package com.mobilebot.domain.memory

val MEMORY_TYPES = listOf("user", "feedback", "project", "reference")

enum class MemoryType {
    User,
    Feedback,
    Project,
    Reference;

    companion object {
        fun fromLabel(label: String?): MemoryType? {
            val lbl = label?.lowercase() ?: return null
            return when (lbl) {
                "user" -> User
                "feedback" -> Feedback
                "project" -> Project
                "reference" -> Reference
                else -> null
            }
        }

        fun toLabel(type: MemoryType): String = when (type) {
            User -> "user"
            Feedback -> "feedback"
            Project -> "project"
            Reference -> "reference"
        }
    }
}

fun parseMemoryType(raw: String?): MemoryType? = raw?.let { MemoryType.fromLabel(it) }
