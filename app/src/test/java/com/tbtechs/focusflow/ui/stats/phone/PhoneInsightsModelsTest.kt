package com.tbtechs.focusflow.ui.stats.phone

import com.tbtechs.focusflow.analytics.phone.TrendDirection
import com.tbtechs.focusflow.analytics.phone.insights.InsightConfidence
import com.tbtechs.focusflow.analytics.phone.insights.InsightGroup
import com.tbtechs.focusflow.analytics.phone.insights.InsightKind
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneInsightsModelsTest {
    private fun ins(id: String, group: InsightGroup, direction: TrendDirection? = null, title: String = "T $id", evidence: List<String> = emptyList()) =
        PhoneInsight(id, InsightKind.RECORD, group, title, "m", evidence, direction, InsightConfidence.MEDIUM, 0)

    @Test fun groupsInFixedOrderAndOmitEmpty() {
        val list = listOf(
            ins("n", InsightGroup.NOTE), ins("r", InsightGroup.RECORD), ins("s", InsightGroup.SUMMARY),
            ins("p", InsightGroup.PATTERN), ins("c", InsightGroup.CHANGE),
        )
        assertEquals(
            listOf(InsightGroup.SUMMARY, InsightGroup.CHANGE, InsightGroup.PATTERN, InsightGroup.RECORD, InsightGroup.NOTE),
            groupInsights(list).map { it.group },
        )
        val sparse = groupInsights(listOf(ins("r", InsightGroup.RECORD), ins("s", InsightGroup.SUMMARY)))
        assertEquals(listOf(InsightGroup.SUMMARY, InsightGroup.RECORD), sparse.map { it.group })
    }

    @Test fun emptyInputGivesNoSections() {
        assertTrue(groupInsights(emptyList()).isEmpty())
    }

    @Test fun orderInsideAGroupIsKept() {
        val list = listOf(ins("b", InsightGroup.PATTERN), ins("x", InsightGroup.RECORD), ins("a", InsightGroup.PATTERN), ins("c", InsightGroup.PATTERN))
        val pattern = groupInsights(list).single { it.group == InsightGroup.PATTERN }
        assertEquals(listOf("b", "a", "c"), pattern.insights.map { it.id })
    }

    @Test fun everyInsightAppearsExactlyOnce() {
        val list = InsightGroup.entries.flatMapIndexed { i, g -> listOf(ins("a$i", g), ins("b$i", g)) }
        assertEquals(list.map { it.id }.sorted(), groupInsights(list).flatMap { it.insights }.map { it.id }.sorted())
    }

    @Test fun evidenceUnchangedAndInOrder() {
        val e = listOf("What: x", "How: y", "Period: z", "Evidence: w")
        assertEquals(e, evidenceLines(ins("a", InsightGroup.SUMMARY, evidence = e)))
        assertEquals(emptyList<String>(), evidenceLines(ins("a", InsightGroup.SUMMARY)))
    }

    @Test fun glyphs() {
        assertEquals("▲", directionGlyph(TrendDirection.UP))
        assertEquals("▼", directionGlyph(TrendDirection.DOWN))
        assertEquals("●", directionGlyph(TrendDirection.FLAT))
        assertEquals("", directionGlyph(null))
    }

    @Test fun buildingMessageText() {
        assertEquals("Insights start once FocusFlow has 3 full days of phone data. Days recorded so far: 0.", buildingMessage(0))
        assertEquals("Insights start once FocusFlow has 3 full days of phone data. Days recorded so far: 2.", buildingMessage(2))
    }

    @Test fun cardTitleHasGlyphOnlyWhenThereIsADirection() {
        assertEquals("▲ T a", insightCardTitle(ins("a", InsightGroup.CHANGE, TrendDirection.UP, "T a")))
        assertEquals("T a", insightCardTitle(ins("a", InsightGroup.CHANGE, null, "T a")))
    }

    @Test fun fixedCopy() {
        assertEquals("Patterns, changes and records from your own phone data.", INSIGHTS_SUBTITLE)
        assertEquals("Nothing unusual to report right now. Check back after a few more days.", INSIGHTS_EMPTY)
        assertEquals("Couldn't analyse your phone habits right now.", INSIGHTS_FAILED)
        assertEquals("Calculated on this phone only. These are observations about your habits, not advice or a diagnosis.", INSIGHTS_FOOTER)
    }
}
