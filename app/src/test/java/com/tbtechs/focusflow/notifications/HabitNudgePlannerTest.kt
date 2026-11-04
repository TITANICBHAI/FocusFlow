package com.tbtechs.focusflow.notifications

import com.tbtechs.focusflow.data.local.entity.FindingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitNudgePlannerTest {
    private fun finding(
        type: String,
        name: String?,
        state: String = "detected",
        json: String = "{}",
    ) = FindingEntity(
        id = "$type-$state",
        detectionType = type,
        subjectPackage = name?.lowercase(),
        subjectAppName = name,
        state = state,
        evidenceFingerprint = "fp",
        evidenceJson = json,
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
    fun lateNightNudgeComesHalfAnHourBeforeBedtime() {
        val nudges = HabitNudgePlanner.plan(listOf(finding("LATE_NIGHT_USE", "Feed")), 22 * 60)
        assertEquals(1, nudges.size)
        assertEquals(21 * 60 + 30, nudges[0].minuteOfDay)
        assertTrue(nudges[0].text.contains("Feed"))
    }

    @Test
    fun lateNightNudgeNeverLandsInsideTheLateNightWindow() {
        assertEquals(22 * 60 + 30, HabitNudgePlanner.lateNightMinute(null))
        assertEquals(22 * 60 + 30, HabitNudgePlanner.lateNightMinute(60))
        assertEquals(22 * 60 + 30, HabitNudgePlanner.lateNightMinute(23 * 60 + 30))
        assertEquals(21 * 60, HabitNudgePlanner.lateNightMinute(21 * 60 + 30))
    }

    @Test
    fun heavyUseNudgeIsTimedBeforeThePeakHourAndNamesTheLimit() {
        val json = """{"avg_min":120,"suggested_min":90,"peak_hour":20}"""
        val nudges = HabitNudgePlanner.plan(listOf(finding("HEAVY_DAILY_USE", "Video", json = json)), null)
        assertEquals(1, nudges.size)
        assertEquals(20 * 60 - 15, nudges[0].minuteOfDay)
        assertTrue(nudges[0].text.contains("90 min"))
        assertTrue(nudges[0].text.contains("8 pm"))
    }

    @Test
    fun heavyUseWithoutAKnownPeakHourProducesNoNudge() {
        val json = """{"avg_min":120,"suggested_min":90}"""
        assertTrue(HabitNudgePlanner.plan(listOf(finding("HEAVY_DAILY_USE", "Video", json = json)), null).isEmpty())
    }

    @Test
    fun dismissedOrResolvedFindingsProduceNothing() {
        val list = listOf(
            finding("LATE_NIGHT_USE", "Feed", state = "intentional"),
            finding("LATE_NIGHT_USE", "Feed", state = "resolved"),
        )
        assertTrue(HabitNudgePlanner.plan(list, 22 * 60).isEmpty())
    }

    @Test
    fun nudgesCloserThanHalfAnHourAreNotBothSent() {
        val json = """{"suggested_min":60,"peak_hour":22}"""
        val list = listOf(
            finding("LATE_NIGHT_USE", "Feed"),
            finding("HEAVY_DAILY_USE", "Video", json = json),
        )
        assertEquals(1, HabitNudgePlanner.plan(list, 22 * 60).size)
    }

    @Test
    fun hardestHourNudgeComesFifteenMinutesBeforeThePeak() {
        val json = """{"attempts":12,"total":20,"days":6,"peak_hour":23}"""
        val nudges = HabitNudgePlanner.plan(listOf(finding("TEMPTATION_PEAK", "Feed", json = json)), null)
        assertEquals(1, nudges.size)
        assertEquals(23 * 60 - 15, nudges[0].minuteOfDay)
        assertTrue(nudges[0].text.contains("Feed"))
        assertTrue(nudges[0].text.contains("11 pm"))
    }

    @Test
    fun whenThereAreMoreNudgesThanSlotsThePriorityOrderDecidesNotTheClock() {
        val list = listOf(
            finding("HEAVY_DAILY_USE", "Video", json = """{"suggested_min":60,"peak_hour":9}"""),
            finding("TEMPTATION_PEAK", "Feed", json = """{"peak_hour":15}"""),
            finding("LATE_NIGHT_USE", "Feed"),
        )
        val nudges = HabitNudgePlanner.plan(list, 22 * 60)
        assertEquals(2, nudges.size)
        // late-night (21:30) and hardest hour (14:45) win; the 08:45 time-sink nudge is dropped.
        assertEquals(listOf(14 * 60 + 45, 21 * 60 + 30), nudges.map { it.minuteOfDay })
    }

    @Test
    fun entryAppNudgeIsOnlyForFeedAppsWithAKnownPeakHour() {
        val feed = finding("ENTRY_APP", "Feed", json = """{"blockable":true,"peak_hour":21}""")
        val nudges = HabitNudgePlanner.plan(listOf(feed), null)
        assertEquals(1, nudges.size)
        assertEquals(21 * 60 - 15, nudges[0].minuteOfDay)
        assertTrue(nudges[0].text.contains("9 pm"))

        val chat = finding("ENTRY_APP", "Chat", json = """{"blockable":false,"peak_hour":21}""")
        assertTrue(HabitNudgePlanner.plan(listOf(chat), null).isEmpty())
        val noPeak = finding("ENTRY_APP", "Feed", json = """{"blockable":true}""")
        assertTrue(HabitNudgePlanner.plan(listOf(noPeak), null).isEmpty())
    }
}
