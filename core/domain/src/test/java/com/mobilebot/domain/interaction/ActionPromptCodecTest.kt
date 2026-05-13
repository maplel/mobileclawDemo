package com.mobilebot.domain.interaction

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionPromptCodecTest {
    @Test
    fun stripsMarkdownFromPresentedOptions() {
        val options =
            ActionPromptCodec.normalizeExplicitOptions(
                listOf(
                    ActionOption("`Keep appointment`", "`Keep appointment`"),
                    ActionOption("**Choose 9:30 AM**", "**Choose 9:30 AM**"),
                    ActionOption("09:30-10:45** (morning), or", "09:30-10:45** (morning), or"),
                ),
            )

        assertEquals(
            listOf(
                ActionOption("Keep appointment", "Keep appointment"),
                ActionOption("Choose 9:30 AM", "Choose 9:30 AM"),
                ActionOption("09:30-10:45 (morning)", "09:30-10:45 (morning)"),
            ),
            options,
        )
    }

    @Test
    fun preservesExplicitLabelsAndValuesWithoutBusinessCompaction() {
        val options =
            ActionPromptCodec.normalizeExplicitOptions(
                listOf(
                    ActionOption("好的 -> proceed with booking the 9:30 appointment", "proceed with booking the 9:30 appointment"),
                    ActionOption("Proceed with booking the 9:30 appointment", "Proceed with booking the 9:30 appointment"),
                ),
            )

        assertEquals(
            listOf(
                ActionOption("好的 -> proceed with booking the 9:30 appointment", "proceed with booking the 9:30 appointment"),
                ActionOption("Proceed with booking the 9:30 appointment", "Proceed with booking the 9:30 appointment"),
            ),
            options,
        )
    }
}
