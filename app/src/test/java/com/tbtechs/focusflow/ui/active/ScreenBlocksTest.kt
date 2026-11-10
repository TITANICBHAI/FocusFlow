package com.tbtechs.focusflow.ui.active

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenBlocksTest {
    @Test
    fun nothingOnMeansNotActive() {
        val blocks = ScreenBlocks.of(emptyList(), false, false, false)
        assertFalse(blocks.isActive)
        assertEquals(0, blocks.count)
        assertEquals("Not active", blocks.status)
        assertTrue(blocks.lines.isEmpty())
    }

    @Test
    fun oneCustomRuleIsEnoughToBeActive() {
        val blocks = ScreenBlocks.of(listOf("Reels tab"), false, false, false)
        assertTrue(blocks.isActive)
        assertEquals("1 active", blocks.status)
        assertEquals(listOf("Custom rule: Reels tab"), blocks.lines)
    }

    @Test
    fun switchesAndRulesAreCountedTogetherInDisplayOrder() {
        val blocks = ScreenBlocks.of(listOf("Shorts shelf", "Reels tab"), true, true, true)
        assertEquals(5, blocks.count)
        assertEquals("5 active", blocks.status)
        assertEquals(
            listOf(
                "YouTube Shorts",
                "Instagram Reels",
                "Install and uninstall protection",
                "Custom rule: Shorts shelf",
                "Custom rule: Reels tab",
            ),
            blocks.lines,
        )
    }

    @Test
    fun aBlankRuleNameReadsAsUntitled() {
        val blocks = ScreenBlocks.of(listOf("  ", ""), false, false, false)
        assertEquals(listOf("Custom rule: Untitled rule", "Custom rule: Untitled rule"), blocks.lines)
    }

    @Test
    fun onlyTheSwitchesCountWhenThereAreNoRules() {
        assertTrue(ScreenBlocks.of(emptyList(), true, false, false).isActive)
        assertTrue(ScreenBlocks.of(emptyList(), false, true, false).isActive)
        assertTrue(ScreenBlocks.of(emptyList(), false, false, true).isActive)
    }
}
