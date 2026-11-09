package com.tbtechs.focusflow.ui.customrules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesGuideDataTest {
    private val limits = RulesGuideLimits(
        maxRules = 30,
        maxMatchers = 5,
        maxFieldLength = 200,
        maxImportChars = 65_536,
        blockedPackages = setOf("com.android.settings", "com.android.systemui"),
    )
    private val prompt = customRulesAgentPrompt(limits)

    @Test
    fun promptQuotesTheLimitsItWasGiven() {
        assertTrue(prompt.contains("1 to 5 objects"))
        assertTrue(prompt.contains("at most 200 characters"))
        assertTrue(prompt.contains("At most 30 rules per file and 65536 characters"))
        assertTrue(prompt.contains("com.android.settings, com.android.systemui"))
    }

    @Test
    fun promptCarriesTheExactImportFormat() {
        assertTrue(prompt.contains("\"format\": \"focusflow.screenrules\""))
        assertTrue(prompt.contains("\"version\": 1"))
        assertTrue(prompt.contains("\"rules\""))
        assertTrue(prompt.contains("\"matchMode\": \"ALL\""))
        listOf("viewIdSuffix", "className", "textContains", "descContains").forEach {
            assertTrue(it, prompt.contains("`$it`"))
        }
    }

    @Test
    fun promptHasNoLeftoverTemplateMarkers() {
        assertFalse(prompt.contains("PLACEHOLDER"))
        assertFalse(prompt.contains("\${"))
        assertTrue(prompt.contains("## My request"))
    }

    @Test
    fun promptForbidsInventingAndTellsTheAssistantToRefuseBroadMatchers() {
        assertTrue(prompt.contains("Never invent ids, text or package names"))
        assertTrue(prompt.contains("only `className` is refused as too broad"))
        assertTrue(prompt.contains("Do not add `id` or `enabled`"))
    }

    @Test
    fun exampleIsBalancedAndUsesAFictionalPackage() {
        val e = RULES_AGENT_EXAMPLE
        assertTrue(e.count { it == '{' } == e.count { it == '}' })
        assertTrue(e.count { it == '[' } == e.count { it == ']' })
        assertTrue(e.contains("com.example."))
    }

    @Test
    fun guideListsAreNotEmptyAndStepsAreNumbered() {
        assertTrue(RULES_GUIDE_STEPS_YOU.isNotEmpty() && RULES_GUIDE_GOOD_ELEMENTS.isNotEmpty())
        RULES_GUIDE_STEPS_YOU.forEachIndexed { i, s -> assertTrue(s.title.startsWith("${i + 1}.")) }
        RULES_GUIDE_STEPS_AGENT.forEachIndexed { i, s -> assertTrue(s.title.startsWith("${i + 1}.")) }
        assertTrue(RULES_GUIDE_BETA_NOTE.contains("Beta"))
    }
}
