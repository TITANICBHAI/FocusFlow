package com.tbtechs.focusflow.analytics.phone

import com.tbtechs.focusflow.analytics.phone.PhoneTestFixtures.MIN
import com.tbtechs.focusflow.analytics.phone.PhoneTestFixtures.app
import com.tbtechs.focusflow.analytics.phone.PhoneTestFixtures.d
import com.tbtechs.focusflow.analytics.phone.PhoneTestFixtures.week
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsBuilderTest {
    private fun session(pkg: String, ms: Long, day: Int = 1) = AppSessionRow(pkg, d(day).toString(), 0L, ms)

    private fun build(
        appDays: List<AppDayRow>,
        sessions: List<AppSessionRow> = emptyList(),
        first: Map<String, Int> = emptyMap(),
        previous: PreviousAppPeriod? = null,
        categoryOf: (String) -> UsageCategory = { UsageCategory.OTHER },
        range: PhoneDateRange = week(),
    ) = AppsBuilder.build(AppsInput(range, appDays, sessions, first, previous, categoryOf))

    @Test
    fun emptyInputGivesEmptyListsAndFourZeroPeriods() {
        val s = build(emptyList())
        assertEquals(0L, s.totalMs)
        assertEquals(0, s.appDataDays)
        assertTrue(s.apps.isEmpty())
        assertTrue(s.categories.isEmpty())
        assertEquals(0L, s.othersMs)
        assertEquals(PhonePeriod.entries, s.periods.map { it.period })
        assertTrue(s.periods.all { it.totalMs == 0L && it.sharePercent == 0.0 && it.topApps.isEmpty() })
    }

    @Test
    fun totalsAndSharesAddUp() {
        val s = build(listOf(app(d(1), "a", 60 * MIN), app(d(1), "b", 30 * MIN), app(d(2), "c", 10 * MIN), app(d(2), "a", 20 * MIN)))
        assertEquals(120 * MIN, s.totalMs)
        assertEquals(listOf("a", "b", "c"), s.apps.map { it.packageName })
        assertEquals(80 * MIN, s.apps[0].totalMs)
        assertEquals(100.0, s.apps.sumOf { it.sharePercent }, 1e-9)
        assertEquals(80.0 * 100 / 120, s.apps[0].sharePercent, 1e-9)
    }

    @Test
    fun dailyMsIsAlignedWithTheRangeDates() {
        val s = build(listOf(app(d(1), "a", 5 * MIN), app(d(3), "a", 7 * MIN), app(d(3), "a", 1 * MIN)))
        val row = s.apps.single()
        assertEquals(7, row.dailyMs.size)
        assertEquals(listOf(0L, 5 * MIN, 0L, 8 * MIN, 0L, 0L, 0L), row.dailyMs)
        assertEquals(2, row.activeDays)
        assertEquals(2, s.appDataDays)
        assertEquals(13 * MIN / 2, row.avgPerDayMs)
    }

    @Test
    fun rowsOutsideTheRangeAndZeroTimeAppsAreIgnored() {
        val s = build(listOf(app(d(0).minusDays(1), "old", 99 * MIN), app(d(2), "zero", 0L), app(d(1), "a", MIN)))
        assertEquals(listOf("a"), s.apps.map { it.packageName })
        assertEquals(MIN, s.totalMs)
        assertEquals(1, s.appDataDays)
    }

    @Test
    fun opensIgnoreSessionsUnderOneSecond() {
        val s = build(
            listOf(app(d(1), "a", 10 * MIN)),
            listOf(session("a", 999L), session("a", 1_000L), session("a", 40_000L)),
        )
        val row = s.apps.single()
        assertEquals(2, row.opens)
        assertEquals(20_500L, row.avgSessionMs)
        assertEquals(40_000L, row.longestSessionMs)
    }

    @Test
    fun sessionBucketsFollowPhoneBuckets() {
        val durations = listOf(10_000L, 40_000L, 3 * MIN, 10 * MIN, 30 * MIN)
        val s = build(listOf(app(d(1), "a", 60 * MIN)), durations.map { session("a", it) })
        assertEquals(listOf(1, 1, 1, 1, 1), s.apps.single().sessionBucketCounts)
        assertEquals(30 * MIN, s.apps.single().longestSessionMs)
    }

    @Test
    fun sessionsOutsideTheRangeAreIgnored() {
        val s = build(listOf(app(d(1), "a", MIN)), listOf(session("a", 5_000L, day = 1), AppSessionRow("a", "2020-01-01", 0L, 5_000L)))
        assertEquals(1, s.apps.single().opens)
    }

    @Test
    fun fallsBackToDailySessionCountWhenThereAreNoSessionRows() {
        val s = build(listOf(app(d(1), "a", 30 * MIN, sessions = 4), app(d(2), "a", 30 * MIN, sessions = 2)))
        val row = s.apps.single()
        assertEquals(6, row.opens)
        assertEquals(60 * MIN / 6, row.avgSessionMs)
        assertEquals(0L, row.longestSessionMs)
        assertEquals(List(5) { 0 }, row.sessionBucketCounts)
    }

    @Test
    fun noSessionsAndNoCountsMeansZeroOpens() {
        val row = build(listOf(app(d(1), "a", MIN))).apps.single()
        assertEquals(0, row.opens)
        assertEquals(0L, row.avgSessionMs)
    }

    @Test
    fun sessionRowsWinOverTheDailyCountWhenBothExist() {
        val s = build(listOf(app(d(1), "a", 30 * MIN, sessions = 50)), listOf(session("a", 5_000L)))
        assertEquals(1, s.apps.single().opens)
    }

    @Test
    fun hoursMapToPeriods() {
        // one minute-weight per boundary hour: 4 late | 5 morning ... 11 morning | 12 afternoon ... 16 afternoon | 17 evening ... 21 evening | 22 late
        val weights = mapOf(0 to 1, 4 to 2, 5 to 3, 6 to 4, 11 to 5, 12 to 6, 16 to 7, 17 to 8, 21 to 9, 22 to 10, 23 to 11)
        val hourly = List(24) { (weights[it] ?: 0) * MIN }
        val s = build(listOf(app(d(1), "a", weights.values.sum() * MIN, hourly = hourly)))
        val row = s.apps.single()
        // morning = 3+4+5, afternoon = 6+7, evening = 8+9, late night = 1+2+10+11
        assertEquals(listOf(12 * MIN, 13 * MIN, 17 * MIN, 24 * MIN), row.periodMs)
        assertEquals(24 * MIN, s.periods[PhonePeriod.LATE_NIGHT.ordinal].totalMs)
        assertEquals(12 * MIN, s.periods[PhonePeriod.MORNING.ordinal].totalMs)
        assertEquals(100.0, s.periods.sumOf { it.sharePercent }, 1e-9)
    }

    @Test
    fun periodTopAppsAreTheThreeLargestInThatPeriod() {
        val rows = listOf(
            app(d(1), "a", 10 * MIN, hour = 8), app(d(1), "b", 40 * MIN, hour = 8),
            app(d(1), "c", 30 * MIN, hour = 8), app(d(1), "d", 20 * MIN, hour = 8), app(d(1), "e", 5 * MIN, hour = 20),
        )
        val s = build(rows)
        assertEquals(listOf("b", "c", "d"), s.periods[PhonePeriod.MORNING.ordinal].topApps.map { it.packageName })
        assertEquals(listOf("e"), s.periods[PhonePeriod.EVENING.ordinal].topApps.map { it.packageName })
        assertTrue(s.periods[PhonePeriod.AFTERNOON.ordinal].topApps.isEmpty())
    }

    @Test
    fun trendIsOnlyComputedWithFourPreviousDaysAndFourDataDays() {
        val rows = (1..4).map { app(d(it), "a", 60 * MIN) } // 4 data days, avg 60 min/day
        val prevMs = mapOf("a" to 4 * 30 * MIN) // 30 min/day over 4 days
        val ok = build(rows, previous = PreviousAppPeriod(4, prevMs)).apps.single()
        assertEquals(30 * MIN, ok.previousAvgPerDayMs)
        assertEquals(100, ok.changePercent)

        val threePrev = build(rows, previous = PreviousAppPeriod(3, prevMs)).apps.single()
        assertNull(threePrev.previousAvgPerDayMs)
        assertNull(threePrev.changePercent)

        val threeCurrent = build(rows.take(3), previous = PreviousAppPeriod(4, prevMs)).apps.single()
        assertNull(threeCurrent.previousAvgPerDayMs)
        assertNull(threeCurrent.changePercent)

        assertNull(build(rows, previous = null).apps.single().changePercent)
    }

    @Test
    fun smallPreviousAverageGivesNoPercentButKeepsTheAverage() {
        val rows = (1..4).map { app(d(it), "a", 60 * MIN) }
        val row = build(rows, previous = PreviousAppPeriod(4, mapOf("a" to 4 * 4 * MIN))).apps.single() // 4 min/day
        assertEquals(4 * MIN, row.previousAvgPerDayMs)
        assertNull(row.changePercent)
        val exactlyFive = build(rows, previous = PreviousAppPeriod(4, mapOf("a" to 4 * 5 * MIN))).apps.single()
        assertEquals(1100, exactlyFive.changePercent) // 60 vs 5 min
    }

    @Test
    fun appAbsentFromPreviousPeriodHasZeroPreviousAverageAndNoPercent() {
        val rows = (1..4).map { app(d(it), "new", 60 * MIN) }
        val row = build(rows, previous = PreviousAppPeriod(4, mapOf("other" to 99 * MIN))).apps.single()
        assertEquals(0L, row.previousAvgPerDayMs)
        assertNull(row.changePercent)
    }

    @Test
    fun decreasesGiveNegativePercent() {
        val rows = (1..4).map { app(d(it), "a", 15 * MIN) }
        val row = build(rows, previous = PreviousAppPeriod(4, mapOf("a" to 4 * 30 * MIN))).apps.single()
        assertEquals(-50, row.changePercent)
    }

    @Test
    fun moreThanThirtyAppsGoToOthersButStayInCategories() {
        val rows = (1..32).map { app(d(1), "p%02d".format(it), it * MIN) } // 1..32 minutes
        val s = build(rows, categoryOf = { if (it < "p10") UsageCategory.SOCIAL else UsageCategory.TOOLS })
        assertEquals(30, s.apps.size)
        assertEquals(32 * 33 / 2 * MIN, s.totalMs)
        assertEquals((1 + 2) * MIN, s.othersMs)
        assertEquals(s.totalMs, s.categories.sumOf { it.totalMs })
        assertEquals("p32", s.apps.first().packageName)
        assertEquals(s.totalMs, s.apps.sumOf { it.totalMs } + s.othersMs)
    }

    @Test
    fun equalTimeIsOrderedByAppName() {
        val s = build(listOf(app(d(1), "p1", MIN, name = "Zed"), app(d(1), "p2", MIN, name = "Abe")))
        assertEquals(listOf("Abe", "Zed"), s.apps.map { it.appName })
    }

    @Test
    fun appNameIsTheLatestNonBlankOrThePackage() {
        val s = build(
            listOf(app(d(5), "a", MIN, name = ""), app(d(3), "a", MIN, name = "New"), app(d(1), "a", MIN, name = "Old"), app(d(2), "b", MIN, name = "")),
        )
        assertEquals("New", s.apps.first { it.packageName == "a" }.appName)
        assertEquals("b", s.apps.first { it.packageName == "b" }.appName)
    }

    @Test
    fun categoriesAreGroupedSortedAndZeroRowsOmitted() {
        val cat = mapOf("a" to UsageCategory.SOCIAL, "b" to UsageCategory.SOCIAL, "c" to UsageCategory.TOOLS)
        val s = build(
            listOf(app(d(1), "a", 10 * MIN), app(d(1), "b", 20 * MIN), app(d(1), "c", 40 * MIN)),
            categoryOf = { cat.getValue(it) },
        )
        assertEquals(listOf(UsageCategory.TOOLS, UsageCategory.SOCIAL), s.categories.map { it.category })
        assertEquals(30 * MIN, s.categories[1].totalMs)
        assertEquals(100.0 * 30 / 70, s.categories[1].sharePercent, 1e-9)
    }

    @Test
    fun categoryAndFirstAfterUnlockAreAttached() {
        val s = build(listOf(app(d(1), "a", MIN)), first = mapOf("a" to 12), categoryOf = { UsageCategory.GAMES })
        assertEquals(UsageCategory.GAMES, s.apps.single().category)
        assertEquals(12, s.apps.single().firstAfterUnlock)
        assertEquals(0, build(listOf(app(d(1), "a", MIN))).apps.single().firstAfterUnlock)
    }
}
