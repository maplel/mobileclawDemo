package com.mobilebot.bridge

data class UiTreeSnapshot(
    val packageName: String?,
    val serializedSummary: String,
)

sealed class UiAction {
    data class Tap(val nodeId: String) : UiAction()

    data class Scroll(
        val nodeId: String,
        val direction: ScrollDirection,
    ) : UiAction()

    enum class ScrollDirection {
        UP,
        DOWN,
    }
}

sealed class UiActionResult {
    data object Success : UiActionResult()

    data class Failure(val reason: String) : UiActionResult()
}

interface AccessibilityActionBridge {
    suspend fun snapshot(): UiTreeSnapshot

    suspend fun perform(action: UiAction): UiActionResult
}
