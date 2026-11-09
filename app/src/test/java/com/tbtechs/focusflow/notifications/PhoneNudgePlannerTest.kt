package com.tbtechs.focusflow.notifications

import com.tbtechs.focusflow.analytics.phone.TrendDirection
import com.tbtechs.focusflow.analytics.phone.insights.InsightConfidence
import com.tbtechs.focusflow.analytics.phone.insights.InsightGroup
import com.tbtechs.focusflow.analytics.phone.insights.InsightKind
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNudgePlannerTest {
    private fun insight(
        id: String, kind: InsightKind, priority: Int = 50, direction: TrendDirection? = null,
        subject: String? = null, name: String? = subject, hour: Int? = null,
    ) = PhoneInsight(
        id, kind, InsightGroup.CHANGE, "t", "m", emptyList(), direction, InsightConfidence.HIGH, priority,
        subject = subject, subjectName = name, peakHour = hour,
    )

    private val everythingDistracts: (String) -> Boolean = { true }

    private fun plan(vararg items: PhoneInsight, distracting: (String) -> Boolean = everythingDistracts, bed: Int? = null) =
        PhoneNudgePlanner.plan(items.toList(), distracting, bed)

    @Test
    fun noInsightsMeansNoNudges() {
        assertTrue(plan().isEmpty())
    }

    @Test
    fun surgeOfADistractingAppNudgesFifteenMinutesBeforeItsBusiestHour() {
        val nudges = plan(insight("surge:ig", InsightKind.APP_SURGE, 85, TrendDirection.UP, "ig", "Instagram", 21))
        assertEquals(listOf(HabitNudge(
            20 * 60 + 45, "Instagram is taking more of your day",
            "You use it most around 9 pm. Start a short block first?",
        )), nudges)
    }

    @Test
    fun timeOfDayHabitNudgesBeforeTheHour() {
        val nudges = plan(insight("todhabit:yt", InsightKind.TIME_OF_DAY_HABIT, 55, null, "yt", "YouTube", 13))
        assertEquals(listOf(HabitNudge(
            12 * 60 + 45, "Your YouTube hour is coming",
            "You usually open it around 1 pm. Take a break first?",
        )), nudges)
    }

    @Test
    fun appsThatAreNotDistractionsNeverNudge() {
        val items = arrayOf(
            insight("surge:maps", InsightKind.APP_SURGE, 85, TrendDirection.UP, "maps", "Maps", 9),
            insight("todhabit:maps", InsightKind.TIME_OF_DAY_HABIT, 55, null, "maps", "Maps", 9),
        )
        assertTrue(plan(*items, distracting = { false }).isEmpty())
        assertEquals(1, plan(*items, distracting = { it == "maps" }).size) // same minute → one nudge
    }

    @Test
    fun insightsWithoutAnAppOrAUsableHourAreIgnored() {
        val nudges = plan(
            insight("a", InsightKind.APP_SURGE, 85, TrendDirection.UP, subject = null, hour = 10),
            insight("b", InsightKind.APP_SURGE, 80, TrendDirection.UP, "x", "X", hour = null),
            insight("c", InsightKind.APP_SURGE, 80, TrendDirection.UP, "y", "Y", hour = 0), // would be 23:45 the day before
            insight("d", InsightKind.APP_SURGE, 80, TrendDirection.UP, "z", "Z", hour = 24),
            insight("e", InsightKind.APP_SURGE, 80, TrendDirection.UP, "w", "  ", hour = 10),
        )
        assertTrue(nudges.isEmpty())
    }

    @Test
    fun otherKindsNeverNudgeEvenWithAnApp() {
        assertTrue(plan(insight("drop:x", InsightKind.APP_DROP, 60, TrendDirection.DOWN, "x", "X", 10)).isEmpty())
        assertTrue(plan(insight("n", InsightKind.DATA_NOTE, 10, null, "x", "X", 10)).isEmpty())
    }

    @Test
    fun lateNightIncreaseNudgesBeforeBedtimeAndADecreaseDoesNot() {
        val up = insight("latenight:1", InsightKind.LATE_NIGHT_SHIFT, 90, TrendDirection.UP)
        val down = insight("latenight:2", InsightKind.LATE_NIGHT_SHIFT, 40, TrendDirection.DOWN)
        assertEquals(listOf(HabitNudge(
            22 * 60 + 30, "Wind down tonight",
            "Late-night screen time is creeping up. Put the phone down a little earlier?",
        )), plan(up))
        assertEquals(21 * 60 + 30, plan(up, bed = 22 * 60).single().minuteOfDay)
        assertTrue(plan(down).isEmpty())
    }

    @Test
    fun atMostTwoNudgesAndTheLateNightOneWinsASlot() {
        val nudges = plan(
            insight("surge:a", InsightKind.APP_SURGE, 85, TrendDirection.UP, "a", "A", 10),
            insight("surge:b", InsightKind.APP_SURGE, 84, TrendDirection.UP, "b", "B", 14),
            insight("latenight:1", InsightKind.LATE_NIGHT_SHIFT, 70, TrendDirection.UP),
        )
        assertEquals(2, nudges.size)
        assertEquals(listOf(9 * 60 + 45, 22 * 60 + 30), nudges.map { it.minuteOfDay })
    }

    @Test
    fun higherPriorityInsightsAreChosenFirstAndTiesBreakById() {
        val nudges = plan(
            insight("surge:b", InsightKind.APP_SURGE, 85, TrendDirection.UP, "b", "B", 10),
            insight("surge:a", InsightKind.APP_SURGE, 85, TrendDirection.UP, "a", "A", 10),
        )
        assertEquals(1, nudges.size) // same minute: the first by id (a) wins
        assertEquals("A is taking more of your day", nudges.single().title)
    }
}
