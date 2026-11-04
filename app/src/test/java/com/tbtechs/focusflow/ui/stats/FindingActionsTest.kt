package com.tbtechs.focusflow.ui.stats

import com.tbtechs.focusflow.data.local.entity.FindingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FindingActionsTest {
    private fun finding(type: String, pkg: String?, name: String?) = FindingEntity(
        id = "f1",
        detectionType = type,
        subjectPackage = pkg,
        subjectAppName = name,
        state = "detected",
        evidenceFingerprint = "fp",
        evidenceJson = "{}",
        headline = "h",
        body = "b",
        evidenceLine = "e",
        firstDetectedAt = "2026-11-01T00:00:00Z",
        lastUpdatedAt = "2026-11-01T00:00:00Z",
        seenAt = null,
        resolvedAt = null,
        suppressedUntil = null,
    )

    @Test
    fun appFindingsOfferToBlockThatApp() {
        val actions = findingActions(finding("LATE_NIGHT_USE", "com.feed", "Feed"))
        assertEquals(1, actions.size)
        assertEquals("Block Feed now", actions[0].label)
        assertEquals("com.feed", actions[0].packageName)
    }

    @Test
    fun findingsWithoutASingleAppOfferNothing() {
        assertTrue(findingActions(finding("DAY_OF_WEEK_OUTLIER", null, null)).isEmpty())
        assertTrue(findingActions(finding("SUBSTITUTION", null, null)).isEmpty())
        assertTrue(findingActions(finding("LATE_NIGHT_USE", null, "Feed")).isEmpty())
    }

    @Test
    fun timeSinkAndAllowanceFindingsAlsoOfferTheSuggestedLimit() {
        val json = """{"avg_min":120,"suggested_min":90}"""
        val actions = findingActions(finding("HEAVY_DAILY_USE", "com.video", "Video").copy(evidenceJson = json))
        assertEquals(2, actions.size)
        assertEquals(FindingActionKind.BLOCK_NOW, actions[0].kind)
        assertEquals(FindingActionKind.SET_LIMIT, actions[1].kind)
        assertEquals(90, actions[1].minutes)
        assertEquals("Limit Video to 90 min a day", actions[1].label)
    }

    @Test
    fun noLimitIsOfferedForAbsurdOrMissingMinutes() {
        val tiny = finding("ALLOWANCE_SUGGESTION", "com.video", "Video").copy(evidenceJson = """{"suggested_min":2}""")
        assertEquals(1, findingActions(tiny).size)
        assertEquals(1, findingActions(finding("ALLOWANCE_SUGGESTION", "com.video", "Video")).size)
    }

    @Test
    fun entryAppOffersABlockOnlyForFeedApps() {
        val feed = finding("ENTRY_APP", "com.feed", "Feed").copy(evidenceJson = """{"blockable":true}""")
        val chat = finding("ENTRY_APP", "com.chat", "Chat").copy(evidenceJson = """{"blockable":false}""")
        assertEquals(1, findingActions(feed).size)
        assertTrue(findingActions(chat).isEmpty())
    }

    @Test
    fun weekendSpikeOffersTheSuggestedLimit() {
        val json = """{"weekend_avg_min":120,"weekday_avg_min":40,"suggested_min":50}"""
        val actions = findingActions(finding("WEEKEND_SPIKE", "com.video", "Video").copy(evidenceJson = json))
        assertEquals(listOf(FindingActionKind.BLOCK_NOW, FindingActionKind.SET_LIMIT), actions.map { it.kind })
        assertEquals(50, actions[1].minutes)
    }
}
