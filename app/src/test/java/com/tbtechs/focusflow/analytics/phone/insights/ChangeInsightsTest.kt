package com.tbtechs.focusflow.analytics.phone.insights

import com.tbtechs.focusflow.analytics.phone.PhoneDayStatus
import com.tbtechs.focusflow.analytics.phone.PhoneTestFixtures
import com.tbtechs.focusflow.analytics.phone.TrendDirection
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.HOUR
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.MIN
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.ago
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.appSpan
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.blocked
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.complete
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.completeSpan
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.focusSession
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.gen
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.hourly
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.input
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.ofKind
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.single
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeInsightsTest {
    private val allDays = completeSpan(28, 1)
    private fun app(from: Int, to: Int, pkg: String, min: Int) = appSpan(from, to, pkg, min, pkg.uppercase())
    private val names = mapOf("x" to "X", "y" to "Y", "z" to "Z")

    private fun surge(recentMin: Int, priorMin: Int, days: List<com.tbtechs.focusflow.analytics.phone.PhoneDay> = allDays) =
        gen(input(days, app(7, 1, "x", recentMin) + app(28, 8, "x", priorMin), names = names))

    // ---- SURGE -----------------------------------------------------------------------------------------
    @Test
    fun surgeFiresWithTheExactMessage() {
        val insight = surge(60, 30).single(InsightKind.APP_SURGE)
        assertEquals("surge:x", insight.id)
        assertEquals("X is taking more time", insight.title)
        assertEquals("1h/day over the last 7 days, up from 30m/day (+100%).", insight.message)
        assertEquals(TrendDirection.UP, insight.direction)
        assertEquals(85, insight.priority)
        assertEquals(InsightGroup.CHANGE, insight.group)
    }

    @Test
    fun surgeBoundaryIsExactlyOnePointFiveTimesAndFifteenMinutes() {
        assertEquals(1, surge(60, 40).ofKind(InsightKind.APP_SURGE).size)  // exactly 1.5×, +20 min
        assertEquals(0, surge(59, 40).ofKind(InsightKind.APP_SURGE).size)  // 1.475×
        assertEquals(0, surge(30, 20).ofKind(InsightKind.APP_SURGE).size)  // exactly 1.5× but only +10 min
        assertEquals(1, surge(35, 20).ofKind(InsightKind.APP_SURGE).size)  // 1.75×, +15 min
    }

    @Test
    fun surgeForANewAppUsesTheNewWordingAndNeedsTwentyMinutes() {
        val insight = surge(20, 0).single(InsightKind.APP_SURGE)
        assertEquals("X is new in your routine", insight.title)
        assertEquals("20m/day over the last 7 days; it wasn't used much before.", insight.message)
        assertTrue(surge(19, 0).ofKind(InsightKind.APP_SURGE).isEmpty())
    }

    @Test
    fun surgeNeedsThreeActiveDaysInTheRecentWeek() {
        fun with(activeDays: Int) = gen(
            input(allDays, (1..activeDays).map { PhoneTestFixtures.app(ago(it), "x", 140 * MIN) }, names = names),
        ).ofKind(InsightKind.APP_SURGE)
        assertTrue(with(2).isEmpty())
        assertEquals(1, with(3).size)
    }

    @Test
    fun surgeKeepsOnlyTheTwoLargestIncreases() {
        val apps = app(7, 1, "x", 30) + app(7, 1, "y", 50) + app(7, 1, "z", 40)
        val surges = gen(input(allDays, apps, names = names)).ofKind(InsightKind.APP_SURGE)
        assertEquals(listOf("surge:y", "surge:z"), surges.map { it.id }.sorted())
    }

    @Test
    fun appChangesNeedFiveRecentAndFourteenPriorCompleteDays() {
        fun count(recent: Int, prior: Int): Int {
            val days = span(7, 8 - recent) { complete(it) } + span(28, 29 - prior) { complete(it) }
            return gen(input(days, app(7, 1, "x", 60) + app(28, 8, "x", 30), names = names)).ofKind(InsightKind.APP_SURGE).size
        }
        assertEquals(1, count(5, 14))
        assertEquals(0, count(4, 14))
        assertEquals(0, count(5, 13))
    }

    @Test
    fun missingAppRowsCountAsZeroOnlyOnCompleteDays() {
        // 5 complete recent days + 2 PARTIAL days that carry big rows: the partial rows must be ignored.
        val days = span(7, 3) { PhoneTestFixtures.day(it, PhoneDayStatus.PARTIAL, screenOnMin = 100) } +
            span(2, 1) { complete(it) } + span(28, 8) { complete(it) }
        // only 2 complete recent days → no insight at all
        assertTrue(gen(input(days, app(7, 1, "x", 120), names = names)).ofKind(InsightKind.APP_SURGE).isEmpty())
        // with 5 complete recent days (days 5..1) and rows on every date: average is over the 5 complete dates
        val days2 = span(7, 6) { PhoneTestFixtures.day(it, PhoneDayStatus.PARTIAL, screenOnMin = 100) } +
            span(5, 1) { complete(it) } + span(28, 8) { complete(it) }
        val rows = app(7, 6, "x", 500) + app(5, 3, "x", 60)
        val insight = gen(input(days2, rows, names = names)).single(InsightKind.APP_SURGE)
        assertEquals("X is new in your routine", insight.title)
        assertEquals("36m/day over the last 7 days; it wasn't used much before.", insight.message) // 180 min / 5 days
    }

    // ---- DROP ------------------------------------------------------------------------------------------
    @Test
    fun dropFiresWithTheExactMessage() {
        val insight = surge(20, 60).single(InsightKind.APP_DROP)
        assertEquals("drop:x", insight.id)
        assertEquals("X is down", insight.title)
        assertEquals("20m/day over the last 7 days, down from 1h/day (−67%).", insight.message)
        assertEquals(TrendDirection.DOWN, insight.direction)
        assertEquals(60, insight.priority)
    }

    @Test
    fun dropBoundaryIsHalfTheTimeTwentyMinutesBeforeAndFifteenMinutesLess() {
        assertEquals(1, surge(30, 60).ofKind(InsightKind.APP_DROP).size)  // exactly 0.5×
        assertEquals(0, surge(31, 60).ofKind(InsightKind.APP_DROP).size)
        assertEquals(0, surge(9, 19).ofKind(InsightKind.APP_DROP).size)   // prior under 20 min
        assertEquals(0, surge(10, 20).ofKind(InsightKind.APP_DROP).size)  // 0.5× but only −10 min
        assertEquals(1, surge(5, 20).ofKind(InsightKind.APP_DROP).size)   // −15 min
    }

    @Test
    fun dropKeepsOnlyTheLargestDecrease() {
        val apps = app(28, 8, "x", 40) + app(28, 8, "y", 80) + app(28, 8, "z", 60)
        val drops = gen(input(allDays, apps, names = names)).ofKind(InsightKind.APP_DROP)
        assertEquals(listOf("drop:y"), drops.map { it.id })
    }

    // ---- UNLOCKS ---------------------------------------------------------------------------------------
    private fun unlocks(recent: Int, prior: Int, recentDays: Int = 7, priorDays: Int = 21) =
        gen(input(span(7, 8 - recentDays) { complete(it, 100, recent) } + span(28, 29 - priorDays) { complete(it, 100, prior) }))
            .ofKind(InsightKind.UNLOCKS_CHANGE)

    @Test
    fun unlocksChangeUpFiresWithTheExactMessage() {
        val insight = unlocks(100, 70).single()
        assertEquals("unlocks:2026-11-05", insight.id)
        assertEquals("You're unlocking more often", insight.title)
        assertEquals("100 unlocks a day this week, up from 70 (+43%).", insight.message)
        assertEquals(TrendDirection.UP, insight.direction)
        assertEquals(70, insight.priority)
    }

    @Test
    fun unlocksChangeBoundaries() {
        assertEquals(1, unlocks(50, 40).size)  // exactly 1.25×, +10
        assertEquals(0, unlocks(49, 40).size)
        assertEquals(0, unlocks(38, 30).size)  // 1.27× but only +8
        assertEquals(1, unlocks(75, 100).size) // exactly 0.75×, −25
        assertEquals(0, unlocks(76, 100).size)
        assertEquals(0, unlocks(22, 30).size)  // 0.73× but only −8
        assertEquals(1, unlocks(30, 40).size)  // exactly 0.75×, −10
        assertEquals(0, unlocks(31, 40).size)  // 0.775×
    }

    @Test
    fun unlocksChangeDownMessage() {
        val insight = unlocks(60, 100).single()
        assertEquals("You're unlocking less often", insight.title)
        assertEquals("60 unlocks a day this week, down from 100 (−40%).", insight.message)
        assertEquals(TrendDirection.DOWN, insight.direction)
    }

    @Test
    fun unlocksChangeDoesNotFireWithoutUnlockSignal() {
        assertTrue(unlocks(0, 0).isEmpty())
        assertTrue(unlocks(0, 100).isEmpty()) // recent days without signal do not count
    }

    @Test
    fun unlocksChangeNeedsFiveRecentAndFourteenPriorSignalDays() {
        assertEquals(1, unlocks(100, 70, recentDays = 5, priorDays = 14).size)
        assertEquals(0, unlocks(100, 70, recentDays = 4, priorDays = 14).size)
        assertEquals(0, unlocks(100, 70, recentDays = 5, priorDays = 13).size)
    }

    // ---- LATE NIGHT ------------------------------------------------------------------------------------
    private fun late(recentLate: Int, priorLate: Int, recentDays: Int = 7, priorDays: Int = 21) = gen(
        input(
            span(7, 8 - recentDays) { complete(it, 200, 0, hourly(recentLate, 200 - recentLate)) } +
                span(28, 29 - priorDays) { complete(it, 200, 0, hourly(priorLate, 200 - priorLate)) },
        ),
    ).ofKind(InsightKind.LATE_NIGHT_SHIFT)

    @Test
    fun lateNightUpFiresWithTheExactMessage() {
        val insight = late(60, 20).single()
        assertEquals("latenight:2026-11-05", insight.id)
        assertEquals("More late-night screen time", insight.title)
        assertEquals("1h/day between 10 PM and 5 AM, up from 20m/day.", insight.message)
        assertEquals(TrendDirection.UP, insight.direction)
        assertEquals(75, insight.priority)
    }

    @Test
    fun lateNightUpBoundaries() {
        assertEquals(1, late(150, 100).size) // exactly 1.5× and +50
        assertEquals(0, late(149, 100).size) // 1.49×
        assertEquals(0, late(49, 20).size)   // 2.45× but only +29
        assertEquals(1, late(50, 20).size)   // +30
        assertEquals(1, late(30, 0).size)    // tiny prior, recent 30
        assertEquals(0, late(29, 0).size)
        assertEquals(0, late(30, 4).size)    // tiny prior but only +26
    }

    @Test
    fun lateNightDownBoundaries() {
        val insight = late(40, 80).single()
        assertEquals("Less late-night screen time", insight.title)
        assertEquals("40m/day between 10 PM and 5 AM, down from 1h 20m/day.", insight.message)
        assertEquals(TrendDirection.DOWN, insight.direction)
        assertEquals(50, insight.priority)
        assertEquals(0, late(41, 80).size)
        assertEquals(0, late(10, 39).size) // 0.26× but only −29
        assertEquals(1, late(30, 60).size) // exactly 0.5× and −30
        assertEquals(0, late(30, 59).size) // 0.51×
    }

    @Test
    fun lateNightNeedsTheSameEligibilityAsSurge() {
        assertEquals(1, late(60, 20, recentDays = 5, priorDays = 14).size)
        assertEquals(0, late(60, 20, recentDays = 4, priorDays = 14).size)
        assertEquals(0, late(60, 20, recentDays = 5, priorDays = 13).size)
    }

    @Test
    fun lateNightCountsHoursTenPmToFourFiftyNine() {
        fun hours(vararg h: Int): List<Long> = List(24) { if (it in h) 60 * MIN else 0L }
        fun fires(hour: Int) = gen(
            input(span(7, 1) { complete(it, 60, 0, hours(hour)) } + span(28, 8) { complete(it, 0, 0, hours(12)) }),
        ).ofKind(InsightKind.LATE_NIGHT_SHIFT).isNotEmpty()
        for (h in listOf(22, 23, 0, 1, 2, 3, 4)) assertTrue("hour $h", fires(h))
        for (h in listOf(5, 12, 21)) assertTrue("hour $h", !fires(h))
    }

    // ---- FOCUS TREND -----------------------------------------------------------------------------------
    private fun focusInput(
        nowMin: Int, nowAttempts: Int, beforeMin: Int, beforeAttempts: Int,
        nowSessions: List<Pair<Int, Int>> = emptyList(), beforeSessions: List<Pair<Int, Int>> = emptyList(),
    ): PhoneInsightInput {
        val sessions = if (nowSessions.isNotEmpty()) nowSessions.mapIndexed { i, (min, ov) -> focusSession(ago(2 + i), 9, min, ov) }
        else listOf(focusSession(ago(3), 9, nowMin))
        val prior = if (beforeSessions.isNotEmpty()) beforeSessions.mapIndexed { i, (min, ov) -> focusSession(ago(16 + i), 9, min, ov) }
        else listOf(focusSession(ago(18), 9, beforeMin))
        return input(allDays, focus = sessions + prior, blocked = listOf(blocked(ago(3), "p", nowAttempts), blocked(ago(18), "p", beforeAttempts)))
    }

    @Test
    fun focusTrendDownFiresWithTheExactMessage() {
        val insight = gen(focusInput(120, 2, 120, 6)).single(InsightKind.FOCUS_TREND)
        assertEquals("focus:2026-11-05", insight.id)
        assertEquals("Fewer blocked attempts per focus hour", insight.title)
        assertEquals("1.0 attempts per focus hour over the last 14 days, down from 3.0 (−67%).", insight.message)
        assertEquals(TrendDirection.DOWN, insight.direction)
        assertEquals(58, insight.priority)
        assertEquals(InsightGroup.CHANGE, insight.group)
    }

    @Test
    fun focusTrendUpMessage() {
        val insight = gen(focusInput(60, 6, 60, 3)).single(InsightKind.FOCUS_TREND)
        assertEquals("More blocked attempts per focus hour", insight.title)
        assertEquals("6.0 attempts per focus hour over the last 14 days, up from 3.0 (+100%).", insight.message)
        assertEquals(TrendDirection.UP, insight.direction)
    }

    @Test
    fun focusTrendNeedsAnHourOfFocusOnBothSides() {
        assertEquals(1, gen(focusInput(60, 2, 60, 6)).ofKind(InsightKind.FOCUS_TREND).size)
        assertEquals(0, gen(focusInput(59, 2, 60, 6)).ofKind(InsightKind.FOCUS_TREND).size)
        assertEquals(0, gen(focusInput(60, 2, 59, 6)).ofKind(InsightKind.FOCUS_TREND).size)
    }

    @Test
    fun focusTrendNeedsAtLeastTwentyPercentChange() {
        assertEquals(0, gen(focusInput(60, 119, 60, 100)).ofKind(InsightKind.FOCUS_TREND).size) // +19%
        assertEquals(1, gen(focusInput(60, 120, 60, 100)).ofKind(InsightKind.FOCUS_TREND).size) // +20%
        assertEquals(0, gen(focusInput(60, 81, 60, 100)).ofKind(InsightKind.FOCUS_TREND).size)  // −19%
        assertEquals(1, gen(focusInput(60, 80, 60, 100)).ofKind(InsightKind.FOCUS_TREND).size)  // −20%
    }

    @Test
    fun focusTrendDoesNotFireWhenThePreviousPeriodHadNoAttempts() {
        assertEquals(0, gen(focusInput(60, 5, 60, 0)).ofKind(InsightKind.FOCUS_TREND).size)
    }

    @Test
    fun focusTrendAddsTheHeldRateWhenEachSideHasThreeEndedSessionsAndAFifteenPointGap() {
        val now = listOf(40 to 0, 40 to 0, 40 to 0)
        val before = listOf(40 to 1, 40 to 1, 40 to 0)
        val message = gen(focusInput(0, 2, 0, 6, now, before)).single(InsightKind.FOCUS_TREND).message
        assertTrue(message, message.endsWith(" Sessions without overrides: 100% (was 33%)."))
    }

    @Test
    fun focusTrendHeldRateNeedsAGapOfFifteenPointsAndThreeSessions() {
        fun msg(now: List<Pair<Int, Int>>, before: List<Pair<Int, Int>>) =
            gen(focusInput(0, 2, 0, 6, now, before)).single(InsightKind.FOCUS_TREND).message
        val seven = List(7) { 20 to 0 }
        val sevenOneOverride = List(7) { if (it == 0) 20 to 1 else 20 to 0 }        // 6/7 = 86%
        assertTrue(!msg(seven, sevenOneOverride).contains("overrides"))               // 100 vs 86 → 14 points
        val sixOneOverride = List(6) { if (it == 0) 30 to 1 else 30 to 0 }           // 5/6 = 83%
        assertTrue(msg(List(6) { 30 to 0 }, sixOneOverride).contains("(was 83%)"))   // 100 vs 83 → 17 points
        val two = List(2) { 60 to 0 }
        assertTrue(!msg(two, List(3) { 40 to 1 }).contains("overrides"))             // only two sessions now
    }

    @Test
    fun focusTrendHeldRateGapOfExactlyFifteenPointsIsEnough() {
        val before = List(13) { if (it < 2) 5 to 1 else 5 to 0 } // 11/13 = 84.6% → 85%
        val message = gen(focusInput(0, 2, 0, 6, List(3) { 20 to 0 }, before)).single(InsightKind.FOCUS_TREND).message
        assertTrue(message, message.endsWith(" Sessions without overrides: 100% (was 85%)."))
    }

    @Test
    fun aRunningFocusSessionIsCountedToTheEndOfTheWindow() {
        val running = com.tbtechs.focusflow.analytics.phone.FocusSessionRow(
            ago(5).toString(), InsightFixtures.startMs(ago(5)), null, 0,
        )
        // Started 5 days ago, never ended: covers days 5..1 (5 full days = 120 h) of the last-14 window.
        val result = gen(
            input(allDays, focus = listOf(running, focusSession(ago(20), 9, 60)), blocked = listOf(blocked(ago(3), "p", 120), blocked(ago(20), "p", 30))),
        ).single(InsightKind.FOCUS_TREND)
        assertEquals("1.0 attempts per focus hour over the last 14 days, down from 30.0 (−97%).", result.message)
    }

    @Test
    fun focusSessionsAreClippedToTheWindow() {
        // Session spans the boundary between prior14 and last14: only the part inside each window counts.
        val across = com.tbtechs.focusflow.analytics.phone.FocusSessionRow(
            ago(15).toString(), InsightFixtures.startMs(ago(15)) + 23 * HOUR, InsightFixtures.startMs(ago(14)) + 2 * HOUR, 0,
        ) // 1 h in prior14 (day −15, 23:00–24:00) and 2 h in last14 (day −14, 00:00–02:00)
        val result = gen(input(allDays, focus = listOf(across), blocked = listOf(blocked(ago(14), "p", 4), blocked(ago(15), "p", 3)))).single(InsightKind.FOCUS_TREND)
        assertEquals("2.0 attempts per focus hour over the last 14 days, down from 3.0 (−33%).", result.message)
    }
}
