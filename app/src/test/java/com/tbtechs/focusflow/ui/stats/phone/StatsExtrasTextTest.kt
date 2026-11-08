package com.tbtechs.focusflow.ui.stats.phone

import com.tbtechs.focusflow.analytics.phone.AppUsageRow
import com.tbtechs.focusflow.analytics.phone.AverageBasis
import com.tbtechs.focusflow.analytics.phone.DayCoverage
import com.tbtechs.focusflow.analytics.phone.DayPoint
import com.tbtechs.focusflow.analytics.phone.PhoneDateRange
import com.tbtechs.focusflow.analytics.phone.PhoneReportFixtures
import com.tbtechs.focusflow.analytics.phone.UsageCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StatsExtrasTextTest {
    private val min = 60_000L
    private val hour = 60 * min
    private val base = PhoneReportFixtures.emptyReport()
    private val monday = LocalDate.of(2026, 11, 2)

    private fun day(offset: Int, ms: Long, cov: DayCoverage = DayCoverage.COMPLETE) =
        DayPoint(monday.plusDays(offset.toLong()), cov, ms, 0, 0, 0)

    // ---- weekdayAverages ----
    @Test fun averagesAreMondayFirstAndUseCompleteDaysOnly() {
        val days = listOf(
            day(0, 2 * hour), day(7, 4 * hour),              // two Mondays
            day(1, hour, DayCoverage.PARTIAL),               // a partial Tuesday is ignored
            day(2, 90 * min), day(3, 0, DayCoverage.MISSING),
            day(4, 10 * hour, DayCoverage.LIVE),
        )
        val avgs = weekdayAverages(days)
        assertEquals(DayOfWeek.entries.toList(), avgs.map { it.day })
        assertEquals(WeekdayAverage(DayOfWeek.MONDAY, 3 * hour, 2), avgs[0])
        assertEquals(WeekdayAverage(DayOfWeek.TUESDAY, null, 0), avgs[1])
        assertEquals(WeekdayAverage(DayOfWeek.WEDNESDAY, 90 * min, 1), avgs[2])
        assertEquals(WeekdayAverage(DayOfWeek.THURSDAY, null, 0), avgs[3])
        assertEquals(WeekdayAverage(DayOfWeek.FRIDAY, null, 0), avgs[4])
    }

    @Test fun averageUsesIntegerDivision() {
        val avgs = weekdayAverages(listOf(day(0, 10), day(7, 11)))
        assertEquals(10L, avgs[0].avgMs)
    }

    @Test fun emptyInputGivesSevenEmptyWeekdays() {
        val avgs = weekdayAverages(emptyList())
        assertEquals(7, avgs.size)
        assertTrue(avgs.all { it.avgMs == null && it.days == 0 })
    }

    @Test fun chartNeedsFourteenCompleteDays() {
        fun complete(n: Int) = (0 until n).map { day(it, hour) }
        assertFalse(showWeekdayChart(complete(13)))
        assertTrue(showWeekdayChart(complete(14)))
        assertFalse(showWeekdayChart(complete(13) + day(20, hour, DayCoverage.PARTIAL) + day(21, hour, DayCoverage.LIVE)))
    }

    // ---- highest / bars / caption ----
    @Test fun highestWeekdayAndTieGoesToTheEarlierDay() {
        val avgs = weekdayAverages(listOf(day(0, hour), day(2, 3 * hour), day(5, 3 * hour)))
        assertEquals(DayOfWeek.WEDNESDAY, highestWeekday(avgs)!!.day)
        assertNull(highestWeekday(weekdayAverages(emptyList())))
    }

    @Test fun zeroAverageStillCountsAsMeasured() {
        val avgs = weekdayAverages(listOf(day(0, 0)))
        assertEquals(DayOfWeek.MONDAY, highestWeekday(avgs)!!.day)
    }

    @Test fun barPoints() {
        val avgs = weekdayAverages(listOf(day(0, hour), day(7, hour), day(5, 5 * hour)))
        val pts = weekdayBarPoints(avgs)
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), pts.map { it.label })
        assertEquals(listOf(hour.toFloat(), 0f, 0f, 0f, 0f, (5 * hour).toFloat(), 0f), pts.map { it.value })
        // Monday has 2 samples (full strength); Saturday has 1 (dimmed); the rest have none (dimmed).
        assertEquals(listOf(false, true, true, true, true, true, true), pts.map { it.dimmed })
        assertEquals(listOf(false, false, false, false, false, true, false), pts.map { it.highlighted })
    }

    @Test fun captionNamesTheHighestWeekday() {
        val avgs = weekdayAverages(listOf(day(5, 245 * min), day(0, hour)))
        assertEquals("Highest on Saturdays · 4h 5m on average", weekdayCaption(avgs))
        assertNull(weekdayCaption(weekdayAverages(emptyList())))
    }

    // ---- partOfDayRows ----
    private fun hourly(vararg at: Pair<Int, Long>): List<Long> = MutableList(24) { 0L }.also { l -> at.forEach { (h, v) -> l[h] += v } }

    @Test fun hourBoundariesLandInTheRightPart() {
        fun partOf(h: Int): String = partOfDayRows(hourly(h to hour), "today").single { it.trailing == "100%" }.title.substringBefore(" ·")
        assertEquals("Late night", partOf(4))
        assertEquals("Morning", partOf(5))
        assertEquals("Morning", partOf(11))
        assertEquals("Afternoon", partOf(12))
        assertEquals("Afternoon", partOf(16))
        assertEquals("Evening", partOf(17))
        assertEquals("Evening", partOf(21))
        assertEquals("Late night", partOf(22))
        assertEquals("Late night", partOf(0))
        assertEquals("Late night", partOf(23))
    }

    @Test fun rowsHaveSharesRangesAndSuffix() {
        val rows = partOfDayRows(hourly(8 to hour, 13 to 2 * hour, 20 to hour), "week")
        assertEquals(4, rows.size)
        assertEquals(TopAppRow("Morning · 5 AM – 12 PM", "1h a day", 0.25f, "25%"), rows[0])
        assertEquals(TopAppRow("Afternoon · 12 – 5 PM", "2h a day", 0.5f, "50%"), rows[1])
        assertEquals(TopAppRow("Evening · 5 – 10 PM", "1h a day", 0.25f, "25%"), rows[2])
        assertEquals(TopAppRow("Late night · 10 PM – 5 AM", "0s a day", 0f, "0%"), rows[3])
    }

    @Test fun singleDayHasNoSuffix() {
        assertEquals("1h", partOfDayRows(hourly(8 to hour), "yesterday")[0].subtitle)
    }

    @Test fun noScreenTimeGivesNoRows() {
        assertTrue(partOfDayRows(List(24) { 0L }, "week").isEmpty())
        assertTrue(partOfDayRows(emptyList(), "week").isEmpty())
        assertTrue(partOfDayRows(hourly(3 to -5L), "week").isEmpty())
    }

    @Test fun aNegativeHourDoesNotSkewTheShares() {
        val rows = partOfDayRows(hourly(3 to -30 * min, 8 to hour), "today")
        assertEquals("100%", rows[0].trailing)
        assertEquals("0%", rows[3].trailing)
    }

    @Test fun extraHoursBeyond24AreIgnored() {
        val rows = partOfDayRows(hourly(8 to hour) + listOf(hour, hour), "today")
        assertEquals("100%", rows[0].trailing)
    }

    // ---- windowPhrase ----
    @Test fun phrases() {
        assertEquals("today", windowPhrase("today"))
        assertEquals("yesterday", windowPhrase("yesterday"))
        assertEquals("this week", windowPhrase("week"))
        assertEquals("over 3 months", windowPhrase("three_months"))
        assertEquals("all time", windowPhrase("all_time"))
        assertEquals("", windowPhrase("other"))
    }

    // ---- shareSummaryText ----
    private fun app(name: String, ms: Long) = AppUsageRow(
        packageName = "p.$name", appName = name, category = UsageCategory.OTHER, totalMs = ms, sharePercent = 0.0,
        opens = 1, avgSessionMs = 0, longestSessionMs = 0, sessionBucketCounts = List(5) { 0 }, periodMs = List(4) { 0L },
        dailyMs = emptyList(), activeDays = 1, avgPerDayMs = 0, previousAvgPerDayMs = null, changePercent = null, firstAfterUnlock = 0,
    )

    @Test fun fullSummary() {
        val report = base.copy(
            window = "week",
            range = PhoneDateRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7)),
            screenTime = base.screenTime.copy(averageBasis = AverageBasis.COMPLETE_DAYS, averageDays = 6, avgScreenOnPerDayMs = 72 * min, peakHour = 21),
            behaviour = base.behaviour.copy(unlockSignalAvailable = true, unlocksPerDay = 47.6),
            apps = base.apps.copy(apps = listOf(app("Chat", 62 * min), app("Maps", 45 * min), app("Mail", 30 * min), app("Notes", 20 * min))),
        )
        assertEquals(
            listOf(
                "My phone this week (Sun, Nov 1 – Sat, Nov 7)",
                "Screen time: 1h 12m a day on average · 6 days",
                "Phone: 48 unlocks a day",
                "Most used: Chat (1h 2m), Maps (45m), Mail (30m)",
                "Busiest around 9 PM · average per day",
                "Shared from FocusFlow",
            ).joinToString("\n"),
            shareSummaryText(report, "week"),
        )
    }

    @Test fun singleDaySummaryUsesOneDate() {
        val report = base.copy(
            window = "today",
            range = PhoneDateRange(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 7)),
            screenTime = base.screenTime.copy(totalScreenOnMs = 90 * min),
        )
        val lines = shareSummaryText(report, "today").lines()
        assertEquals("My phone today (Sat, Nov 7)", lines[0])
        assertEquals("Screen time: 1h 30m screen on today so far", lines[1])
    }

    @Test fun emptyDataSummaryHasOnlyHeaderAndFooter() {
        val report = base.copy(window = "week", range = PhoneDateRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7)))
        assertEquals(
            "My phone this week (Sun, Nov 1 – Sat, Nov 7)\nShared from FocusFlow",
            shareSummaryText(report, "week"),
        )
    }

    @Test fun appsWithNoTimeAreLeftOut() {
        val report = base.copy(window = "week", apps = base.apps.copy(apps = listOf(app("Idle", 0))))
        assertFalse(shareSummaryText(report, "week").contains("Most used"))
    }
}
