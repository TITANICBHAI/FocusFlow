package com.tbtechs.focusflow.analytics.phone

import com.tbtechs.focusflow.analytics.phone.PhoneTestFixtures.MIN
import com.tbtechs.focusflow.analytics.phone.PhoneTestFixtures.app
import com.tbtechs.focusflow.analytics.phone.PhoneTestFixtures.d
import com.tbtechs.focusflow.analytics.phone.PhoneTestFixtures.day
import com.tbtechs.focusflow.analytics.phone.PhoneTestFixtures.week
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenTimeBuilderTest {
    private val complete = PhoneDayStatus.COMPLETE
    private val partial = PhoneDayStatus.PARTIAL
    private val live = PhoneDayStatus.LIVE

    private fun build(days: List<PhoneDay>, apps: List<AppDayRow> = emptyList(), range: PhoneDateRange = week()) =
        ScreenTimeBuilder.build(range, days, apps)

    @Test
    fun daysAreOnePerDateOldestFirstWithCoverage() {
        val s = build(listOf(day(d(1), complete, 60), day(d(2), partial, 30), day(d(3), live, 10)))
        assertEquals((0..6).map { d(it) }, s.days.map { it.date })
        assertEquals(DayCoverage.MISSING, s.days[0].coverage)
        assertEquals(DayCoverage.COMPLETE, s.days[1].coverage)
        assertEquals(DayCoverage.PARTIAL, s.days[2].coverage)
        assertEquals(DayCoverage.LIVE, s.days[3].coverage)
    }

    @Test
    fun missingDaysAreExcludedFromScreenTotalButKeepAppMs() {
        val s = build(
            listOf(day(d(1), complete, 60)),
            listOf(app(d(1), "a", 10 * MIN), app(d(4), "a", 20 * MIN)), // d(4) has no phone day
        )
        assertEquals(60 * MIN, s.totalScreenOnMs)
        assertEquals(30 * MIN, s.totalAppMs)
        assertEquals(20 * MIN, s.days[4].appMs)
        assertEquals(0L, s.days[4].screenOnMs)
        assertEquals(DayCoverage.MISSING, s.days[4].coverage)
    }

    @Test
    fun partialCountsInTotalButNotInAverage() {
        val s = build(listOf(day(d(0), complete, 100), day(d(1), partial, 500)))
        assertEquals(600 * MIN, s.totalScreenOnMs)
        assertEquals(AverageBasis.COMPLETE_DAYS, s.averageBasis)
        assertEquals(1, s.averageDays)
        assertEquals(100 * MIN, s.avgScreenOnPerDayMs)
    }

    @Test
    fun onlyLiveDayGivesTodaySoFar() {
        val s = build(listOf(day(d(5), live, 40), day(d(4), partial, 90)))
        assertEquals(AverageBasis.TODAY_SO_FAR, s.averageBasis)
        assertEquals(1, s.averageDays)
        assertEquals(40 * MIN, s.avgScreenOnPerDayMs)
        assertNull(s.weekday)
        assertNull(s.highest)
    }

    @Test
    fun onlyPartialOrMissingGivesNoAverages() {
        val s = build(listOf(day(d(2), partial, 90)), listOf(app(d(2), "a", 5 * MIN)))
        assertEquals(AverageBasis.NONE, s.averageBasis)
        assertEquals(0, s.averageDays)
        assertNull(s.avgScreenOnPerDayMs)
        assertNull(s.avgAppPerDayMs)
        assertEquals(List(24) { 0L }, s.screenOnByHourAvgMs)
        assertNull(s.peakHour)
        assertEquals(90 * MIN, s.totalScreenOnMs)
    }

    @Test
    fun emptyInputIsAllZeroAndNone() {
        val s = build(emptyList())
        assertEquals(0L, s.totalScreenOnMs)
        assertEquals(0L, s.totalAppMs)
        assertEquals(AverageBasis.NONE, s.averageBasis)
        assertEquals(7, s.days.size)
        assertEquals(24, s.screenOnByHourAvgMs.size)
    }

    @Test
    fun averageUsesIntegerDivision() {
        val s = build(listOf(day(d(0), complete, 0).copy(screenOnMs = 10), day(d(1), complete, 0).copy(screenOnMs = 5)))
        assertEquals(7L, s.avgScreenOnPerDayMs)
    }

    @Test
    fun weekdayWeekendGating() {
        // Mon, Tue + Sat (d(5)) → both present
        val ok = build(listOf(day(d(0), complete, 100), day(d(1), complete, 200), day(d(5), complete, 400)))
        assertEquals(DayGroupAverage(150 * MIN, 2), ok.weekday)
        assertEquals(DayGroupAverage(400 * MIN, 1), ok.weekend)
        // no weekend day → both null
        val noWeekend = build(listOf(day(d(0), complete, 100), day(d(1), complete, 200)))
        assertNull(noWeekend.weekday)
        assertNull(noWeekend.weekend)
        // one weekday + one weekend → both null
        val oneWeekday = build(listOf(day(d(0), complete, 100), day(d(5), complete, 400)))
        assertNull(oneWeekday.weekday)
        assertNull(oneWeekday.weekend)
    }

    @Test
    fun weekendIsSaturdayAndSundayOnly() {
        val s = build(listOf(day(d(4), complete, 10), day(d(3), complete, 10), day(d(6), complete, 300))) // Fri, Thu, Sun
        assertEquals(2, s.weekday?.days)
        assertEquals(1, s.weekend?.days)
    }

    @Test
    fun weekdayGroupIgnoresPartialDays() {
        val s = build(
            listOf(day(d(0), complete, 100), day(d(1), complete, 100), day(d(2), partial, 900), day(d(5), complete, 50)),
        )
        assertEquals(100 * MIN, s.weekday?.avgMs)
        assertEquals(2, s.weekday?.days)
    }

    @Test
    fun highestAndLowestNeedThreeCompleteDays() {
        val two = build(listOf(day(d(0), complete, 10), day(d(1), complete, 20)))
        assertNull(two.highest)
        assertNull(two.lowest)
        val three = build(listOf(day(d(0), complete, 10), day(d(1), complete, 30), day(d(2), complete, 20)))
        assertEquals(d(1), three.highest?.date)
        assertEquals(d(0), three.lowest?.date)
    }

    @Test
    fun highestAndLowestTiesGoToTheLaterDate() {
        val s = build(
            listOf(
                day(d(0), complete, 50), day(d(1), complete, 50), day(d(2), complete, 10),
                day(d(3), complete, 10), day(d(4), complete, 30),
            ),
        )
        assertEquals(d(1), s.highest?.date)
        assertEquals(d(3), s.lowest?.date)
    }

    @Test
    fun highestAndLowestIgnorePartialDays() {
        val s = build(
            listOf(day(d(0), complete, 10), day(d(1), complete, 20), day(d(2), complete, 30), day(d(3), partial, 999), day(d(4), partial, 0)),
        )
        assertEquals(d(2), s.highest?.date)
        assertEquals(d(0), s.lowest?.date)
    }

    @Test
    fun hourlyAverageAndPeak() {
        val a = day(d(0), complete, 60, screenHour = 8)
        val b = day(d(1), complete, 20, screenHour = 8)
        val c = day(d(2), complete, 40, screenHour = 21)
        val s = build(listOf(a, b, c))
        assertEquals(80 * MIN / 3, s.screenOnByHourAvgMs[8])
        assertEquals(40 * MIN / 3, s.screenOnByHourAvgMs[21])
        assertEquals(8, s.peakHour)
    }

    @Test
    fun peakHourTieGoesToEarliestHour() {
        val s = build(listOf(day(d(0), complete, 60, screenHour = 15), day(d(1), complete, 60, screenHour = 6)))
        assertEquals(6, s.peakHour)
    }

    @Test
    fun allZeroHoursHaveNoPeak() {
        val s = build(listOf(day(d(0), complete, 0), day(d(1), complete, 0)))
        assertEquals(List(24) { 0L }, s.screenOnByHourAvgMs)
        assertNull(s.peakHour)
    }

    @Test
    fun hourlyAverageUsesBaselineDaysOnly() {
        val s = build(listOf(day(d(0), complete, 60, screenHour = 8), day(d(1), partial, 600, screenHour = 8)))
        assertEquals(60 * MIN, s.screenOnByHourAvgMs[8])
    }

    @Test
    fun avgAppIsNullWithoutAppRowsAndComputedWithThem() {
        val none = build(listOf(day(d(0), complete, 60)))
        assertNull(none.avgAppPerDayMs)
        val some = build(
            listOf(day(d(0), complete, 60), day(d(1), complete, 60)),
            listOf(app(d(0), "a", 30 * MIN), app(d(0), "b", 10 * MIN), app(d(1), "a", 20 * MIN)),
        )
        assertEquals(30 * MIN, some.avgAppPerDayMs)
    }

    @Test
    fun avgAppIsNullWhenEveryBaselineDayHasNoAppTime() {
        val s = build(
            listOf(day(d(0), complete, 60), day(d(1), partial, 60)),
            listOf(app(d(1), "a", 99 * MIN)), // only the PARTIAL day has app time
        )
        assertNull(s.avgAppPerDayMs)
        assertNotNull(s.avgScreenOnPerDayMs)
    }

    @Test
    fun phoneDaysOutsideTheRangeAreIgnored() {
        val s = build(listOf(day(d(0).minusDays(1), complete, 999), day(d(1), complete, 10)))
        assertEquals(10 * MIN, s.totalScreenOnMs)
        assertEquals(1, s.averageDays)
    }

    @Test
    fun unlocksAndWakesComeFromTheDay() {
        val s = build(listOf(day(d(1), complete, 10, unlocks = 7, wakes = 9)))
        assertEquals(7, s.days[1].unlocks)
        assertEquals(9, s.days[1].wakes)
    }
}
