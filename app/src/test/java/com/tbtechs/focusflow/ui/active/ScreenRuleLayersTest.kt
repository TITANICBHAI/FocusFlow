package com.tbtechs.focusflow.ui.active

import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.enforcement.customrules.CustomScreenRule
import com.tbtechs.focusflow.enforcement.customrules.NodeMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRuleLayersTest {
    private fun rule(id: String, enabled: Boolean) = CustomScreenRule(
        id = id,
        name = "Rule $id",
        packageName = "com.example.app",
        enabled = enabled,
        matchers = listOf(NodeMatcher(viewIdSuffix = "reels_tab")),
    )

    @Test
    fun nothingOnMeansLayerIsNotActive() {
        val layers = ScreenRuleLayers.from(AppSettings(), listOf(rule("a", false)))
        assertFalse(layers.isActive)
        assertEquals(0, layers.activeCount)
        assertEquals(1, layers.customTotal)
    }

    @Test
    fun countsEnabledRulesAndBuiltInProtections() {
        val settings = AppSettings(
            blockYoutubeShortsEnabled = true,
            blockInstagramReelsEnabled = true,
            blockInstallActionsEnabled = true,
            systemGuardEnabled = true,
        )
        val layers = ScreenRuleLayers.from(settings, listOf(rule("a", true), rule("b", false), rule("c", true)))
        assertTrue(layers.isActive)
        assertEquals(6, layers.activeCount)
        assertEquals(2, layers.customEnabled)
    }

    @Test
    fun protectSystemControlsAloneActivatesTheLayer() {
        val layers = ScreenRuleLayers.from(AppSettings(systemGuardEnabled = true), emptyList())
        assertTrue(layers.isActive)
        assertEquals(1, layers.activeCount)
    }

    @Test
    fun aSingleBuiltInProtectionActivatesTheLayer() {
        val layers = ScreenRuleLayers.from(AppSettings(blockInstallActionsEnabled = true), emptyList())
        assertTrue(layers.isActive)
        assertEquals(1, layers.activeCount)
    }
}
