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
}
