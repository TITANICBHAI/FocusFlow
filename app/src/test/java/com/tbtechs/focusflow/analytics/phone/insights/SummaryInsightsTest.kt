package com.tbtechs.focusflow.analytics.phone.insights

import com.tbtechs.focusflow.analytics.phone.PhoneDayStatus
import com.tbtechs.focusflow.analytics.phone.PhoneTestFixtures
import com.tbtechs.focusflow.analytics.phone.TrendDirection
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.MIN
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.ago
import com.tbtechs.focusflow.analytics.phone.insights.InsightFixtures.appSpan
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

class SummaryInsightsTest {
    // ---- WEEKLY ----------------------------------------------------------------------------------------
    private fun weeklyDays(recentScreen: Int, beforeScreen: Int, recentUnlocks: Int = 0, beforeUnlocks: Int = 0,
                           recentLate: Int = 0, beforeLate: Int = 0) =
        span(7, 1) { complete(it, recentScreen, recentUnlocks, hourly(recentLate, recentScreen - recentLate)) } +
            span(14, 8) { complete(it, beforeScreen, beforeUnlocks, hourly(beforeLate, beforeScreen - beforeLate)) }

    @Test
    fun weeklySummaryHasEveryLineWhenAllDataExists() {
        val appDays = appSpan(7, 1, "a", 60, "App A") + appSpan(14, 8, "a", 20, "App A") + appSpan(14, 8, "b", 50, "App B")
        val focus = listOf(focusSession(ago(5), 10, 120))
        val blocked = listOf(InsightFixtures.blocked(ago(4), "x", 3))
        val insight = gen(input(weeklyDays(120, 100, 80, 100, 30, 20), appDays, focus = focus, blocked = blocked,
            names = mapOf("a" to "App A", "b" to "App B"))).single(InsightKind.WEEKLY_SUMMARY)
        assertEquals("weekly:2026-11-05", insight.id)
        assertEquals("Last 7 days vs the 7 before", insight.title)
        assertEquals(InsightGroup.SUMMARY, insight.group)
        assertEquals(80, insight.priority)
        assertEquals(TrendDirection.UP, insight.direction)
        assertEquals(
            listOf(
                "Screen time: 2h/day, +20% vs the week before (1h 40m/day).",
                "Unlocks: 80/day (−20%).",
                "Most used: App A (1h/day). It was App B the week before.",
                "Late night (10 PM–5 AM): 30m/day (+50%).",
                "Focus: 2h in focus mode, 3 blocked attempts.",
            ),
            insight.message.split("\n"),
        )
    }

    @Test
    fun weeklySummaryOmitsLinesWithoutData() {
        val insight = gen(input(weeklyDays(120, 100))).single(InsightKind.WEEKLY_SUMMARY)
        assertEquals(listOf("Screen time: 2h/day, +20% vs the week before (1h 40m/day)."), insight.message.split("\n"))
    }

    @Test
    fun weeklyMostUsedAppDoesNotMentionTheOldAppWhenItIsTheSame() {
        val appDays = appSpan(7, 1, "a", 60, "App A") + appSpan(14, 8, "a", 30, "App A")
        val line = gen(input(weeklyDays(120, 100), appDays, names = mapOf("a" to "App A")))
            .single(InsightKind.WEEKLY_SUMMARY).message.split("\n").single { it.startsWith("Most used") }
        assertEquals("Most used: App A (1h/day).", line)
    }

    @Test
    fun weeklyUnlockLineNeedsFourSignalDaysInBothWeeks() {
        val recent = span(7, 1) { complete(it, 100, if (ago(1) == it || ago(2) == it || ago(3) == it) 50 else 0) }
        val before = span(14, 8) { complete(it, 100, 50) }
        val three = gen(input(recent + before)).single(InsightKind.WEEKLY_SUMMARY).message
        assertTrue(three, !three.contains("Unlocks"))
        val recent4 = span(7, 1) { complete(it, 100, if (ago(4) == it || ago(1) == it || ago(2) == it || ago(3) == it) 50 else 0) }
        val four = gen(input(recent4 + before)).single(InsightKind.WEEKLY_SUMMARY).message
        assertTrue(four, four.contains("Unlocks: 50/day (0%)."))
    }

    @Test
    fun weeklyUnlockLineNeedsFourSignalDaysInThePreviousWeekToo() {
        val recent = span(7, 1) { complete(it, 100, 50) }
        fun before(signalDays: Int) = span(14, 8) { complete(it, 100, if (14 - (InsightFixtures.today.toEpochDay() - it.toEpochDay()).toInt() < signalDays) 50 else 0) }
        assertTrue(!gen(input(recent + before(3))).single(InsightKind.WEEKLY_SUMMARY).message.contains("Unlocks"))
        assertTrue(gen(input(recent + before(4))).single(InsightKind.WEEKLY_SUMMARY).message.contains("Unlocks: 50/day (0%)."))
    }

    @Test
    fun weeklyLateNightLineAppearsWhenEitherWeekHasLateNightUse() {
        fun message(recentLate: Int, beforeLate: Int) =
            gen(input(weeklyDays(100, 100, recentLate = recentLate, beforeLate = beforeLate))).single(InsightKind.WEEKLY_SUMMARY).message
        assertTrue(message(0, 0).let { !it.contains("Late night") })
        assertTrue(message(0, 20).contains("Late night (10 PM–5 AM): 0s/day (−100%)."))
        assertTrue(message(20, 0).contains("Late night (10 PM–5 AM): 20m/day."))
    }

    @Test
    fun weeklyFocusLineAppearsWhenEitherWeekHadFocusOrBlockedAttempts() {
        val days = weeklyDays(100, 100)
        fun message(focus: List<com.tbtechs.focusflow.analytics.phone.FocusSessionRow> = emptyList(), blocked: List<com.tbtechs.focusflow.analytics.phone.BlockedDayRow> = emptyList()) =
            gen(input(days, focus = focus, blocked = blocked)).single(InsightKind.WEEKLY_SUMMARY).message
        assertTrue(!message().contains("Focus:"))
        assertTrue(message(focus = listOf(focusSession(ago(10), 9, 60))).contains("Focus: 0s in focus mode, 0 blocked attempts."))
        assertTrue(message(blocked = listOf(InsightFixtures.blocked(ago(10), "x", 2))).contains("Focus: 0s in focus mode, 0 blocked attempts."))
        assertTrue(message(blocked = listOf(InsightFixtures.blocked(ago(2), "x", 1))).contains("Focus: 0s in focus mode, 1 blocked attempt."))
    }

    @Test
    fun weeklyDirectionIsFlatBetweenMinusAndPlusFivePercent() {
        fun dir(recent: Int) = gen(input(weeklyDays(recent, 100))).single(InsightKind.WEEKLY_SUMMARY).direction
        assertEquals(TrendDirection.UP, dir(105))
        assertEquals(TrendDirection.FLAT, dir(104))
        assertEquals(TrendDirection.DOWN, dir(95))
        assertEquals(TrendDirection.FLAT, dir(96))
    }

    @Test
    fun weeklySummaryNeedsFourCompleteDaysInEachWeek() {
        val ok = span(7, 4) { complete(it, 100) } + span(14, 11) { complete(it, 100) }
        assertEquals(1, gen(input(ok)).ofKind(InsightKind.WEEKLY_SUMMARY).size)
        val recentShort = span(7, 5) { complete(it, 100) } + span(14, 11) { complete(it, 100) }
        assertTrue(gen(input(recentShort)).ofKind(InsightKind.WEEKLY_SUMMARY).isEmpty())
        val beforeShort = span(7, 4) { complete(it, 100) } + span(14, 12) { complete(it, 100) }
        assertTrue(gen(input(beforeShort)).ofKind(InsightKind.WEEKLY_SUMMARY).isEmpty())
    }

    @Test
    fun weeklySummaryIgnoresDaysThatAreNotComplete() {
        val partial = span(7, 4) { PhoneTestFixtures.day(it, PhoneDayStatus.PARTIAL, screenOnMin = 100) } + span(14, 8) { complete(it, 100) }
        assertTrue(gen(input(partial)).ofKind(InsightKind.WEEKLY_SUMMARY).isEmpty())
    }

    // ---- MONTHLY ---------------------------------------------------------------------------------------
    @Test
    fun monthlySummaryComparesTheLastThirtyDaysWithTheThirtyBefore() {
        val days = span(30, 1) { complete(it, 90, 50) } + span(60, 31) { complete(it, 60, 40) }
        val appDays = appSpan(30, 1, "a", 40, "App A") + appSpan(30, 1, "b", 30, "App B") + appSpan(30, 1, "c", 20, "App C") +
            appSpan(30, 1, "d", 10, "App D")
        val insight = gen(input(days, appDays, names = mapOf("a" to "App A", "b" to "App B", "c" to "App C", "d" to "App D")))
            .single(InsightKind.MONTHLY_SUMMARY)
        assertEquals("monthly:2026-11-05", insight.id)
        assertEquals("Last 30 days vs the 30 before", insight.title)
        assertEquals(45, insight.priority)
        assertEquals(TrendDirection.UP, insight.direction)
        assertEquals(
            listOf("Screen time: 1h 30m/day, +50% vs the month before.", "Unlocks: 50/day (+25%).", "Top apps: App A, App B, App C."),
            insight.message.split("\n"),
        )
    }

    @Test
    fun monthlySummaryNeedsTwentyAndTenCompleteDays() {
        fun count(month: Int, before: Int) =
            gen(input(span(30, 31 - month) { complete(it) } + span(60, 61 - before) { complete(it) })).ofKind(InsightKind.MONTHLY_SUMMARY).size
        assertEquals(1, count(20, 10))
        assertEquals(0, count(19, 10))
        assertEquals(0, count(20, 9))
    }

    @Test
    fun monthlyUnlockLineNeedsFourSignalDaysInBothMonths() {
        fun message(monthSignal: Int, beforeSignal: Int): String {
            val month = span(30, 1) { complete(it, 90, if (30 - (InsightFixtures.today.toEpochDay() - it.toEpochDay()).toInt() < monthSignal) 50 else 0) }
            val before = span(60, 31) { complete(it, 60, if (60 - (InsightFixtures.today.toEpochDay() - it.toEpochDay()).toInt() < beforeSignal) 40 else 0) }
            return gen(input(month + before)).single(InsightKind.MONTHLY_SUMMARY).message
        }
        assertTrue(message(4, 4).contains("Unlocks: 50/day (+25%)."))
        assertTrue(!message(3, 4).contains("Unlocks"))
        assertTrue(!message(4, 3).contains("Unlocks"))
    }

    @Test
    fun monthlyTopAppsListsFewerThanThreeWhenThatIsAllThereIs() {
        val days = span(30, 1) { complete(it) } + span(60, 31) { complete(it) }
        val message = gen(input(days, appSpan(30, 1, "a", 10, "Only App"), names = mapOf("a" to "Only App"))).single(InsightKind.MONTHLY_SUMMARY).message
        assertTrue(message, message.endsWith("Top apps: Only App."))
    }

    // ---- UNUSUAL DAY -----------------------------------------------------------------------------------
    private fun unusual(median: Int, yesterday: Int, baselineDays: Int = 14): List<PhoneInsight> =
        gen(input(span(15, 16 - baselineDays) { complete(it, median) } + complete(ago(1), yesterday))).ofKind(InsightKind.UNUSUAL_DAY)

    @Test
    fun unusualDayFiresAtExactlyOnePointFourTimesAndFortyFiveMinutes() {
        val insight = unusual(150, 210).single()
        assertEquals("unusual:2026-11-05", insight.id)
        assertEquals("Yesterday was heavier than usual", insight.title)
        assertEquals("Screen time was 3h 30m, 40% above your usual 2h 30m (typical of the last 14 days).", insight.message)
        assertEquals(TrendDirection.UP, insight.direction)
        assertEquals(90, insight.priority)
        assertEquals(InsightGroup.CHANGE, insight.group)
    }

    @Test
    fun unusualDayDoesNotFireJustBelowTheRatioOrTheMinutes() {
        assertTrue(unusual(150, 209).isEmpty())   // 1.393× (ratio just under)
        assertTrue(unusual(100, 140).isEmpty())   // 1.4× but only +40 min
        assertTrue(unusual(300, 345).isEmpty())   // +45 min but 1.15×
        assertEquals(1, unusual(100, 145).size)   // 1.45× and +45 min fires
    }

    @Test
    fun unusualDayDownFiresAtPointSixTimes() {
        val insight = unusual(150, 90).single()
        assertEquals("Yesterday was lighter than usual", insight.title)
        assertEquals("Screen time was 1h 30m, 40% below your usual 2h 30m (typical of the last 14 days).", insight.message)
        assertEquals(TrendDirection.DOWN, insight.direction)
        assertEquals(55, insight.priority)
        assertTrue(unusual(150, 91).isEmpty())
        assertTrue(unusual(60, 30).isEmpty()) // 0.5× but only −30 min
    }

    @Test
    fun unusualDayNeedsSevenBaselineDaysAndACompleteYesterday() {
        assertEquals(1, unusual(150, 210, baselineDays = 7).size)
        assertTrue(unusual(150, 210, baselineDays = 6).isEmpty())
        val partialYesterday = span(15, 2) { complete(it, 150) } + PhoneTestFixtures.day(ago(1), PhoneDayStatus.PARTIAL, screenOnMin = 400)
        assertTrue(gen(input(partialYesterday)).ofKind(InsightKind.UNUSUAL_DAY).isEmpty())
    }

    @Test
    fun unusualDayIsSkippedWhenTheUsualDayHasNoScreenTime() {
        assertTrue(unusual(0, 120).isEmpty())
    }

    @Test
    fun unusualDayUsesTheMedianNotTheMean() {
        // 13 days of 100 min and one of 1000 min: median 100, mean ~164. Yesterday = 150 is 1.5× the median.
        val baseline = span(15, 3) { complete(it, 100) } + complete(ago(2), 1000)
        val insight = gen(input(baseline + complete(ago(1), 150))).ofKind(InsightKind.UNUSUAL_DAY).single()
        assertEquals("Screen time was 2h 30m, 50% above your usual 1h 40m (typical of the last 14 days).", insight.message)
    }

    @Test
    fun confidenceIsHighWithTwiceTheMinimumSample() {
        assertEquals(InsightConfidence.HIGH, unusual(150, 210, baselineDays = 14).single().confidence)
        assertEquals(InsightConfidence.MEDIUM, unusual(150, 210, baselineDays = 7).single().confidence)
    }

    @Test
    fun confidenceIsHighAtExactlyTwiceTheMinimum() {
        // weekly: minimum is 4 complete days; 4 + 4 = 8 days used is exactly 2× the minimum.
        val days = span(7, 4) { complete(it) } + span(14, 11) { complete(it) }
        assertEquals(InsightConfidence.HIGH, gen(input(days)).single(InsightKind.WEEKLY_SUMMARY).confidence)
    }

    @Test
    fun everyInsightCarriesTheFourEvidenceLines() {
        val insight = unusual(150, 210).single()
        assertEquals(listOf("What: ", "How: ", "Period: ", "Evidence: "), insight.evidence.map { it.substringBefore(":") + ": " })
        assertEquals("Evidence: 14 complete days", insight.evidence[3])
        assertEquals("How: Compared with the median of your previous 14 complete days", insight.evidence[1])
    }
}
