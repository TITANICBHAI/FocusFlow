package com.tbtechs.focusflow.analytics.phone.insights

import com.tbtechs.focusflow.analytics.phone.PhoneDay
import com.tbtechs.focusflow.analytics.phone.PhoneDayStatus
import com.tbtechs.focusflow.analytics.phone.PhoneTestFixtures
import com.tbtechs.focusflow.analytics.phone.TrendDirection
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.MIN
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.ago
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.complete
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.gen
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.input
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.ofKind
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.single
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternInsightsTest {
    private val names = mapOf("a" to "App A", "b" to "App B", "c" to "App C")

    // ---- FIRST APP -------------------------------------------------------------------------------------
    /** Ten complete days with 10 unlocks each (U = 100); `firstApps[i]` is day i's first-app map. */
    private fun firstAppDays(firstApps: List<Map<String, Int>>): List<PhoneDay> =
        firstApps.mapIndexed { i, map -> PhoneTestFixtures.day(ago(10 - i), unlocks = 10, screenOnMin = 100, firstApps = map) }

    private fun firstApp(vararg perDay: Map<String, Int>) =
        gen(input(firstAppDays(perDay.toList()), names = names)).ofKind(InsightKind.FIRST_APP_PATTERN)

    @Test
    fun firstAppPatternFiresWithAnOptionalSecondApp() {
        val days = List(10) { mapOf("a" to 4, "b" to 2) } // a = 40%, b = 20% of 100 unlocks
        val insight = firstApp(*days.toTypedArray()).single()
        assertEquals("firstapp:a", insight.id)
        assertEquals("You often reach for App A first", insight.title)
        assertEquals("After 40% of unlocks, App A was the first app you opened (last 14 days). App B follows at 20%.", insight.message)
        assertEquals(65, insight.priority)
        assertEquals(InsightGroup.PATTERN, insight.group)
        assertEquals(null, insight.direction)
    }

    @Test
    fun firstAppSecondAppIsMentionedOnlyFromFifteenPercent() {
        val without = firstApp(*List(10) { mapOf("a" to 4, "b" to 1) }.toTypedArray()).single().message // b = 10%
        assertTrue(without, !without.contains("follows"))
        val atBoundary = List(10) { if (it < 5) mapOf("a" to 4, "b" to 3) else mapOf("a" to 4) } // b = 15%
        assertTrue(firstApp(*atBoundary.toTypedArray()).single().message.endsWith("App B follows at 15%."))
        val below = List(10) { if (it < 7) mapOf("a" to 4, "b" to 2) else mapOf("a" to 4) } // b = 14%
        assertTrue(!firstApp(*below.toTypedArray()).single().message.contains("follows"))
    }

    @Test
    fun firstAppShareBoundaryIsTwentyFivePercent() {
        val exactly = List(10) { if (it < 5) mapOf("a" to 5) else emptyMap() }               // 25 of 100
        assertEquals(1, firstApp(*exactly.toTypedArray()).size)
        val under = List(10) { if (it < 4) mapOf("a" to 5) else if (it == 4) mapOf("a" to 4) else emptyMap() } // 24 of 100
        assertEquals(0, firstApp(*under.toTypedArray()).size)
    }

    @Test
    fun firstAppNeedsThirtyUnlocks() {
        fun with(unlocksPerDay: Int, days: Int) = gen(
            input(span(days, 1) { PhoneTestFixtures.day(it, unlocks = unlocksPerDay, screenOnMin = 10, firstApps = mapOf("a" to unlocksPerDay)) }, names = names),
        ).ofKind(InsightKind.FIRST_APP_PATTERN)
        assertEquals(1, with(10, 3).size)  // 30 unlocks
        assertEquals(0, with(29, 1).size)  // 29 unlocks
    }

    @Test
    fun firstAppIgnoresDaysOutsideTheLastFourteen() {
        val days = span(20, 15) { PhoneTestFixtures.day(it, unlocks = 50, screenOnMin = 10, firstApps = mapOf("a" to 50)) } +
            span(3, 1) { PhoneTestFixtures.day(it, unlocks = 10, screenOnMin = 10, firstApps = emptyMap()) }
        assertTrue(gen(input(days)).ofKind(InsightKind.FIRST_APP_PATTERN).isEmpty())
    }

    // ---- CHECKING --------------------------------------------------------------------------------------
    private fun checking(daysCount: Int, sessions: Int, buckets: List<Int>, unlocks: Int = 5) = gen(
        input(span(daysCount, 1) { PhoneTestFixtures.day(it, unlocks = unlocks, screenOnMin = 60, sessions = sessions, bucketCounts = buckets) }),
    ).ofKind(InsightKind.CHECKING_HABIT)

    @Test
    fun checkingHabitFiresWithTheExactMessage() {
        val insight = checking(5, 20, listOf(6, 5, 5, 3, 1)).single() // 11 quick of 20 per day = 55%
        assertEquals("checking:2026-11-05", insight.id)
        assertEquals("Most unlocks are quick checks", insight.title)
        assertEquals("55% of your phone sessions last under a minute — about 11 quick checks a day.", insight.message)
        assertEquals(64, insight.priority)
    }

    @Test
    fun checkingHabitBoundaries() {
        assertEquals(1, checking(5, 20, listOf(5, 5, 6, 3, 1)).size)  // exactly 50% and 10 a day
        assertEquals(0, checking(5, 20, listOf(5, 4, 6, 4, 1)).size)  // 45%, 9 a day
        assertEquals(0, checking(5, 24, listOf(6, 5, 8, 4, 1)).size)  // 11 a day but 46%
        assertEquals(0, checking(5, 22, listOf(5, 4, 8, 4, 1)).size)  // 41% and 9 a day
        assertEquals(1, checking(5, 10, listOf(5, 5, 0, 0, 0)).size)  // exactly 50 sessions
        assertEquals(0, checking(4, 12, listOf(6, 6, 0, 0, 0)).size)  // 48 sessions
    }

    @Test
    fun checkingHabitNeedsUnlockSignal() {
        assertTrue(checking(5, 20, listOf(10, 5, 3, 1, 1), unlocks = 0).isEmpty())
    }

    @Test
    fun checkingHabitPerDayUsesOnlyDaysWithSignal() {
        // 3 signal days with 20 sessions / 15 quick each (45 quick → 15/day … need N ≥ 50) + 2 no-signal days ignored.
        val days = span(5, 3) { PhoneTestFixtures.day(it, unlocks = 0, screenOnMin = 60, sessions = 40, bucketCounts = listOf(40, 0, 0, 0, 0)) } +
            span(2, 1) { PhoneTestFixtures.day(it, unlocks = 9, screenOnMin = 60, sessions = 30, bucketCounts = listOf(15, 5, 5, 3, 2)) }
        val insight = gen(input(days)).single(InsightKind.CHECKING_HABIT)
        assertEquals("67% of your phone sessions last under a minute — about 20 quick checks a day.", insight.message)
    }

    // ---- TIME OF DAY -----------------------------------------------------------------------------------
    private fun tod(vararg rows: com.tbtechs.focusflow.analytics.phone.AppDayRow) =
        gen(input(span(14, 1) { complete(it) }, rows.toList(), names = names)).ofKind(InsightKind.TIME_OF_DAY_HABIT)

    private fun hourRow(date: java.time.LocalDate, pkg: String, vararg hourMin: Pair<Int, Int>): com.tbtechs.focusflow.analytics.phone.AppDayRow {
        val hourly = List(24) { h -> hourMin.filter { it.first == h }.sumOf { it.second * MIN } }
        return PhoneTestFixtures.app(date, pkg, hourly.sum(), pkg, hourly = hourly)
    }

    @Test
    fun timeOfDayHabitFiresWithTheExactMessage() {
        val insight = tod(hourRow(ago(1), "a", 23 to 90)).single()
        assertEquals("todhabit:a", insight.id)
        assertEquals("App A is mostly a late night app", insight.title)
        assertEquals("100% of your App A time is in the late night (10 PM–5 AM).", insight.message)
        assertEquals(55, insight.priority)
    }

    @Test
    fun timeOfDayHabitNamesEachPeriodAndItsHours() {
        fun msg(hour: Int) = tod(hourRow(ago(1), "a", hour to 90)).single().message
        assertEquals("100% of your App A time is in the morning (5 AM–noon).", msg(8))
        assertEquals("100% of your App A time is in the afternoon (noon–5 PM).", msg(14))
        assertEquals("100% of your App A time is in the evening (5–10 PM).", msg(19))
        assertEquals("100% of your App A time is in the late night (10 PM–5 AM).", msg(2))
    }

    @Test
    fun timeOfDayHabitShareBoundaryIsFiftyFivePercent() {
        assertEquals(1, tod(hourRow(ago(1), "a", 8 to 55, 23 to 45)).size)
        assertEquals(0, tod(hourRow(ago(1), "a", 8 to 54, 23 to 46)).size)
        assertEquals(0, tod(hourRow(ago(1), "a", 8 to 50, 23 to 50)).size)
    }

    @Test
    fun timeOfDayHabitNeedsSixtyMinutesInTheLastFourteenDays() {
        assertEquals(1, tod(hourRow(ago(1), "a", 8 to 60)).size)
        assertEquals(0, tod(hourRow(ago(1), "a", 8 to 59)).size)
        assertEquals(1, tod(hourRow(ago(1), "a", 8 to 30), hourRow(ago(2), "a", 8 to 30)).size) // totals across days
    }

    @Test
    fun timeOfDayHabitOnlyConsidersTheTopThreeAppsAndReportsTwo() {
        val rows = listOf(
            hourRow(ago(1), "a", 8 to 400), hourRow(ago(1), "b", 8 to 300), hourRow(ago(1), "c", 8 to 200),
            hourRow(ago(1), "d", 8 to 100),
        )
        val result = tod(*rows.toTypedArray())
        assertEquals(listOf("todhabit:a", "todhabit:b"), result.map { it.id }.sorted())
    }

    @Test
    fun timeOfDayHabitSkipsAppsOutsideTheTopThreeEvenWhenEarlierOnesDoNotQualify() {
        val rows = listOf(
            hourRow(ago(1), "a", 8 to 200, 23 to 200), hourRow(ago(1), "b", 8 to 150, 23 to 150),
            hourRow(ago(1), "c", 8 to 100), hourRow(ago(1), "d", 8 to 90),
        )
        assertEquals(listOf("todhabit:c"), tod(*rows.toTypedArray()).map { it.id })
    }

    @Test
    fun timeOfDayHabitIgnoresDaysThatAreNotComplete() {
        val days = span(14, 2) { complete(it) } + PhoneTestFixtures.day(ago(1), PhoneDayStatus.PARTIAL, screenOnMin = 10)
        assertTrue(gen(input(days, listOf(hourRow(ago(1), "a", 8 to 90)))).ofKind(InsightKind.TIME_OF_DAY_HABIT).isEmpty())
    }

    // ---- CONSISTENCY -----------------------------------------------------------------------------------
    /** `count` complete days back from yesterday alternating mean−d / mean+d (population cv = d / mean). */
    private fun alternating(mean: Int, d: Int, count: Int = 10, from: Int = 1, extra: (Int, PhoneDay) -> PhoneDay = { _, day -> day }): List<PhoneDay> =
        (0 until count).map { i -> extra(i, complete(ago(from + i), if (i % 2 == 0) mean - d else mean + d)) }

    private fun consistency(days: List<PhoneDay>) = gen(input(days)).ofKind(InsightKind.CONSISTENCY)

    @Test
    fun consistencyLabelsAtTheBoundaries() {
        fun label(d: Int) = consistency(alternating(100, d)).single().title
        assertEquals("Your phone use is steady", label(24))       // 0.24
        assertEquals("Your phone use is variable", label(25))     // 0.25
        assertEquals("Your phone use is variable", label(49))     // 0.49
        assertEquals("Your phone use is irregular", label(50))    // 0.50
    }

    @Test
    fun consistencyMessageAndMetadata() {
        val insight = consistency(alternating(100, 25)).single()
        assertEquals("consistency:2026-11-05", insight.id)
        assertEquals("Daily screen time varies by about 25% around your 1h 40m average.", insight.message)
        assertEquals(50, insight.priority)
        assertEquals(null, insight.direction)
    }

    @Test
    fun consistencyNeedsTenCompleteDaysInTheLastFourteen() {
        assertEquals(1, consistency(alternating(100, 25, count = 10)).size)
        assertEquals(0, consistency(alternating(100, 25, count = 9)).size)
        // days older than 14 do not count
        assertEquals(0, consistency(alternating(100, 25, count = 9) + complete(ago(20), 100)).size)
    }

    @Test
    fun consistencyAddsTheTrendLineWhenThePreviousFourteenDaysDifferByAtLeastPointOne() {
        val now = alternating(100, 50)                       // cv 0.5
        val steadierBefore = alternating(100, 25, from = 15) // cv 0.25 → now is less steady
        val less = consistency(now + steadierBefore).single()
        assertTrue(less.message, less.message.endsWith(" It's less steady than the 14 days before."))
        assertEquals(TrendDirection.UP, less.direction)
        val more = consistency(alternating(100, 25) + alternating(100, 50, from = 15)).single()
        assertTrue(more.message, more.message.endsWith(" It's steadier than the 14 days before."))
        assertEquals(TrendDirection.DOWN, more.direction)
    }

    @Test
    fun consistencyTrendLineBoundaries() {
        val exactly = consistency(alternating(100, 50) + alternating(100, 40, from = 15)).single() // 0.5 vs 0.4 → 0.1
        assertTrue(exactly.message.contains("less steady"))
        val under = consistency(alternating(100, 50) + alternating(100, 41, from = 15)).single()   // 0.09
        assertTrue(!under.message.contains("steady than"))
        assertEquals(null, under.direction)
        val fewDays = consistency(alternating(100, 50) + alternating(100, 25, count = 9, from = 15)).single()
        assertTrue(!fewDays.message.contains("steady than"))
    }

    private fun withFirstUnlock(minutes: (Int) -> Int) = alternating(100, 25) { i, day ->
        day.copy(firstUnlockAtMs = InsightFixtures.startMs(java.time.LocalDate.parse(day.date)) + minutes(i) * MIN)
    }

    @Test
    fun consistencyAddsTheFirstUnlockLineWhenItIsRegular() {
        val message = consistency(withFirstUnlock { 8 * 60 }).single().message
        assertTrue(message, message.endsWith(" Your first unlock is usually around 8:00 AM (±0 min)."))
        val spread = consistency(withFirstUnlock { if (it % 2 == 0) 8 * 60 - 30 else 8 * 60 + 30 }).single().message
        assertTrue(spread, spread.endsWith("(±30 min)."))
    }

    @Test
    fun consistencyFirstUnlockLineBoundaries() {
        // stdev exactly 45 min (8:00 ± 45) is still "regular"; 60 min is omitted; 120 min says it varies a lot.
        assertTrue(consistency(withFirstUnlock { if (it % 2 == 0) 8 * 60 - 45 else 8 * 60 + 45 }).single().message.contains("±45 min"))
        assertTrue(!consistency(withFirstUnlock { if (it % 2 == 0) 8 * 60 - 60 else 8 * 60 + 60 }).single().message.contains("first unlock"))
        assertTrue(consistency(withFirstUnlock { if (it % 2 == 0) 8 * 60 - 120 else 8 * 60 + 120 }).single().message.endsWith(" Your first unlock time varies a lot from day to day."))
        assertTrue(!consistency(withFirstUnlock { if (it % 2 == 0) 8 * 60 - 119 else 8 * 60 + 119 }).single().message.contains("varies a lot"))
    }

    @Test
    fun consistencyFirstUnlockTimeIsTheMedianNotTheMean() {
        val message = consistency(withFirstUnlock { if (it == 0) 8 * 60 + 100 else 8 * 60 }).single().message
        assertTrue(message, message.endsWith(" Your first unlock is usually around 8:00 AM (±30 min)."))
    }

    @Test
    fun consistencyFirstUnlockLineNeedsTenDaysWithAFirstUnlock() {
        val days = alternating(100, 25) { i, day ->
            if (i == 0) day else day.copy(firstUnlockAtMs = InsightFixtures.startMs(java.time.LocalDate.parse(day.date)) + 480 * MIN)
        }
        assertTrue(!consistency(days).single().message.contains("first unlock"))
    }
}
