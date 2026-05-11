package com.mobilebot.bridge.impl

import com.mobilebot.bridge.AccessibilityActionBridge
import com.mobilebot.bridge.UiAction
import com.mobilebot.bridge.UiActionResult
import com.mobilebot.bridge.UiTreeSnapshot
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StubAccessibilityBridge
    @Inject
    constructor() : AccessibilityActionBridge {
        override suspend fun snapshot(): UiTreeSnapshot =
            UiTreeSnapshot(
                packageName = null,
                serializedSummary = "Accessibility service not connected. Enable MobileBot accessibility to capture UI trees.",
            )

        override suspend fun perform(action: UiAction): UiActionResult =
            UiActionResult.Failure("Accessibility control unavailable (service not enabled).")
    }
