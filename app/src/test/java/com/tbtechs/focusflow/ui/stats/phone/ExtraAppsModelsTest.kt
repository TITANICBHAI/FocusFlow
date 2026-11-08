package com.tbtechs.focusflow.ui.stats.phone

import com.tbtechs.focusflow.analytics.phone.AppUsageRow
import com.tbtechs.focusflow.analytics.phone.DayCoverage
import com.tbtechs.focusflow.analytics.phone.DayPoint
import com.tbtechs.focusflow.analytics.phone.PhoneReportFixtures
import com.tbtechs.focusflow.analytics.phone.UsageCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExtraAppsModelsTest {
    private val min = 60_000L
    private fun app(
        name: String, ms: Long = 0, opens: Int = 0, avg: Long = 0, change: Int? = null, share: Double = 0.0,
        daily: List<Long> = emptyList(), periods: List<Long> = List(4) { 0L }, active: Int = 1, first: Int = 0,
    ) = AppUsageRow(
        packageName = "pkg.$name", appName = name, category = UsageCategory.OTHER, totalMs = ms, sharePercent = share,
        opens = opens, avgSessionMs = avg, longestSessionMs = 0, sessionBucketCounts = List(5) { 0 },
        periodMs = periods, dailyMs = daily, activeDays = active, avgPerDayMs = 0, previousAvgPerDayMs = null,
        changePercent = change, firstAfterUnlock = first,
    )

    private fun names(rows: List<AppUsageRow>) = rows.map { it.appName }

    @Test fun sortByTimeDescThenName() {
        val rows = listOf(app("b", ms = 5), app("A", ms = 9), app("a2", ms = 5), app("C", ms = 1))
        assertEquals(listOf("A", "a2", "b", "C"), names(sortApps(rows, AppSort.TIME)))
    }

    @Test fun sortByOpens() {
        val rows = listOf(app("x", opens = 3), app("y", opens = 10), app("z", opens = 3))
        assertEquals(listOf("y", "x", "z"), names(sortApps(rows, AppSort.OPENS)))
    }

    @Test fun sortByAvgSession() {
        val rows = listOf(app("x", avg = 3 * min), app("y", avg = 10 * min), app("w", avg = 3 * min))
        assertEquals(listOf("y", "w", "x"), names(sortApps(rows, AppSort.AVG_SESSION)))
    }

    @Test fun sortByTrendNullsLast() {
        val rows = listOf(app("n1", change = null), app("down", change = -20), app("up", change = 35), app("zero", change = 0), app("n0", change = null))
        assertEquals(listOf("up", "zero", "down", "n0", "n1"), names(sortApps(rows, AppSort.TREND)))
    }

    @Test fun sortTrendTiesByNameIgnoringCase() {
        val rows = listOf(app("beta", change = 10), app("Alpha", change = 10), app("gamma", change = 10))
        assertEquals(listOf("Alpha", "beta", "gamma"), names(sortApps(rows, AppSort.TREND)))
    }

    @Test fun sortDoesNotMutateInput() {
        val rows = listOf(app("b", ms = 1), app("a", ms = 2))
        sortApps(rows, AppSort.TIME)
        assertEquals(listOf("b", "a"), names(rows))
    }

    @Test fun summaryText() {
        val row = app("Chat", ms = 72 * min, opens = 24, avg = 3 * min, share = 17.6)
        assertEquals("1h 12m · 18% · 24 opens · avg 3m", appRowSummary(row))
    }

    @Test fun summarySingularOpen() {
        assertEquals("5m · 5% · 1 open · avg 5m", appRowSummary(app("x", ms = 5 * min, opens = 1, avg = 5 * min, share = 5.2)))
    }

    @Test fun trendTexts() {
        assertEquals("▲ 35% vs previous period", trendText(app("a", change = 35)))
        assertEquals("▼ 12% vs previous period", trendText(app("a", change = -12)))
        assertEquals("● no change vs previous period", trendText(app("a", change = 0)))
        assertNull(trendText(app("a", change = null)))
    }

    @Test fun periodLabelsInOrder() {
        assertEquals(listOf("Morning", "Afternoon", "Evening", "Late night"), periodBarLabels())
    }

    @Test fun periodMinutesRoundToNearest() {
        val row = app("a", periods = listOf(29_999L, 30_000L, 90 * min, -5L))
        assertEquals(listOf(0, 1, 90, 0), periodMinutes(row))
    }

    @Test fun coverageNote() {
        val a = PhoneReportFixtures.emptyReport().apps
        assertEquals("Based on 5 days of app data.", appsCoverageNote(a.copy(appDataDays = 5)))
        assertEquals("Based on 1 day of app data.", appsCoverageNote(a.copy(appDataDays = 1)))
        assertEquals("Based on 0 days of app data.", appsCoverageNote(a.copy(appDataDays = 0)))
    }

    @Test fun detailLines() {
        assertEquals(listOf("Active 4 of 5 days"), appDetailLines(app("a", active = 4, first = 0), 5))
        assertEquals(listOf("Active 1 of 1 days", "First app after 1 unlock"), appDetailLines(app("a", active = 1, first = 1), 1))
        assertEquals("First app after 12 unlocks", appDetailLines(app("a", first = 12), 3)[1])
    }

    private fun day(offset: Int, cov: DayCoverage = DayCoverage.COMPLETE) =
        DayPoint(LocalDate.of(2026, 11, 1).plusDays(offset.toLong()), cov, 0, 0, 0, 0)

    @Test fun dailyPointsAlignedAndLabelled() {
        val days = (0 until 3).map { day(it) }
        val row = app("a", daily = listOf(10L, 20L, 30L))
        val pts = appDailyPoints(row, days)
        assertEquals(listOf(10f, 20f, 30f), pts.map { it.value })
        assertEquals(listOf("S", "M", "T"), pts.map { it.label }) // 1 Nov 2026 is a Sunday
        assertTrue(pts.none { it.highlighted })
    }

    @Test fun dailyPointsKeepLast14WithMatchingValues() {
        val days = (0 until 20).map { day(it) }
        val row = app("a", daily = (0 until 20).map { it * 1000L })
        val pts = appDailyPoints(row, days)
        assertEquals(14, pts.size)
        assertEquals(6000f, pts.first().value)
        assertEquals(19000f, pts.last().value)
        assertEquals("20", pts.last().label) // 20 Nov: last bar of a >7-day chart shows the day of month
    }

    @Test fun dailyPointsDimMissingAndPartial() {
        val days = listOf(day(0, DayCoverage.MISSING), day(1, DayCoverage.PARTIAL), day(2, DayCoverage.LIVE), day(3))
        val pts = appDailyPoints(app("a", daily = listOf(1L, 2L, 3L, 4L)), days)
        assertEquals(listOf(true, true, false, false), pts.map { it.dimmed })
    }

    @Test fun dailyPointsEmptyWhenNoData() {
        assertEquals(emptyList<DayBarPoint>(), appDailyPoints(app("a", daily = emptyList()), listOf(day(0))))
        assertEquals(emptyList<DayBarPoint>(), appDailyPoints(app("a", daily = listOf(1L)), emptyList()))
    }

    @Test fun visibleCount() {
        assertEquals(15, visibleAppCount(40, false))
        assertEquals(30, visibleAppCount(40, true))
        assertEquals(7, visibleAppCount(7, false))
        assertEquals(20, visibleAppCount(20, true))
        assertEquals(0, visibleAppCount(0, true))
    }
}
