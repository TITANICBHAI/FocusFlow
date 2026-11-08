package com.tbtechs.focusflow.ui.stats.phone

import com.tbtechs.focusflow.analytics.phone.AppUsageRow
import com.tbtechs.focusflow.analytics.phone.AverageBasis
import com.tbtechs.focusflow.analytics.phone.DayGroupAverage
import com.tbtechs.focusflow.analytics.phone.PhoneBehaviourSection
import com.tbtechs.focusflow.analytics.phone.PhoneCoverage
import com.tbtechs.focusflow.analytics.phone.PhoneReportFixtures
import com.tbtechs.focusflow.analytics.phone.ScreenTimeSection
import com.tbtechs.focusflow.analytics.phone.UsageCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsPhoneTextTest {
    private val base = PhoneReportFixtures.emptyReport()
    private val min = 60_000L
    private val hour = 60 * min

    private fun screen(
        total: Long = 0L, app: Long = 0L, basis: AverageBasis = AverageBasis.NONE, days: Int = 0,
        avg: Long? = null, avgApp: Long? = null,
    ): ScreenTimeSection = base.screenTime.copy(
        totalScreenOnMs = total, totalAppMs = app, averageBasis = basis, averageDays = days,
        avgScreenOnPerDayMs = avg, avgAppPerDayMs = avgApp,
    )

    private fun behaviour(
        signal: Boolean = true, unlocks: Int = 0, perDay: Double? = null, wakes: Int = 0, wakesPerDay: Double? = null,
        gap: Long? = null, quick: Int? = null,
    ): PhoneBehaviourSection = base.behaviour.copy(
        unlockSignalAvailable = signal, unlocks = unlocks, unlocksPerDay = perDay, wakes = wakes, wakesPerDay = wakesPerDay,
        avgGapBetweenUnlocksMs = gap, quickCheckSharePercent = quick,
    )

    private fun coverage(partial: Int = 0, missing: Int = 0) = PhoneCoverage(3, partial, 0, missing, null, null)

    private fun app(name: String, ms: Long, share: Double, opens: Int = 1) = AppUsageRow(
        packageName = "pkg.$name", appName = name, category = UsageCategory.OTHER, totalMs = ms, sharePercent = share,
        opens = opens, avgSessionMs = 0, longestSessionMs = 0, sessionBucketCounts = List(5) { 0 },
        periodMs = List(4) { 0L }, dailyMs = emptyList(), activeDays = 1, avgPerDayMs = 0, previousAvgPerDayMs = null,
        changePercent = null, firstAfterUnlock = 0,
    )

    // ---- isSingleDay ----
    @Test fun singleDayWindows() {
        assertTrue(isSingleDay("today"))
        assertTrue(isSingleDay("yesterday"))
        assertTrue(!isSingleDay("week"))
        assertTrue(!isSingleDay("all"))
    }

    // ---- screenTimeHero ----
    @Test fun heroToday() {
        assertEquals(HeroText("1h 12m", "screen on today so far"), screenTimeHero(screen(total = 72 * min), "today"))
    }

    @Test fun heroYesterday() {
        assertEquals(HeroText("3h", "screen on yesterday"), screenTimeHero(screen(total = 3 * hour), "yesterday"))
    }

    @Test fun heroSingleDayUsesTotalNotAverage() {
        val s = screen(total = 10 * min, basis = AverageBasis.COMPLETE_DAYS, days = 5, avg = 5 * hour)
        assertEquals("10m", screenTimeHero(s, "today").value)
    }

    @Test fun heroMultiDayCompleteDays() {
        val s = screen(total = 7 * hour, basis = AverageBasis.COMPLETE_DAYS, days = 6, avg = 72 * min)
        assertEquals(HeroText("1h 12m", "a day on average · 6 days"), screenTimeHero(s, "week"))
    }

    @Test fun heroMultiDaySingularDay() {
        val s = screen(basis = AverageBasis.COMPLETE_DAYS, days = 1, avg = hour)
        assertEquals("a day on average · 1 day", screenTimeHero(s, "week").caption)
    }

    @Test fun heroMultiDayTodaySoFar() {
        val s = screen(basis = AverageBasis.TODAY_SO_FAR, days = 1, avg = 20 * min)
        assertEquals(HeroText("20m", "so far today"), screenTimeHero(s, "week"))
    }

    @Test fun heroMultiDayNone() {
        assertEquals(HeroText("—", "not enough complete days yet"), screenTimeHero(screen(), "week"))
    }

    @Test fun heroMultiDayNullAverageIsNoneEvenWithBasis() {
        val s = screen(basis = AverageBasis.COMPLETE_DAYS, days = 3, avg = null)
        assertEquals("—", screenTimeHero(s, "week").value)
    }

    @Test fun heroMultiDayBasisNoneWinsOverStrayAverage() {
        val s = screen(basis = AverageBasis.NONE, days = 0, avg = 2 * hour)
        assertEquals(HeroText("—", "not enough complete days yet"), screenTimeHero(s, "week"))
    }

    // ---- unlocksHero ----
    @Test fun unlocksSingleDayWithSignal() {
        assertEquals(HeroText("63", "unlocks"), unlocksHero(behaviour(unlocks = 63), "today"))
    }

    @Test fun unlocksSingleDayWithSignalZeroStillShown() {
        assertEquals(HeroText("0", "unlocks"), unlocksHero(behaviour(unlocks = 0), "yesterday"))
    }

    @Test fun unlocksMultiDayRounds() {
        assertEquals(HeroText("48", "unlocks a day"), unlocksHero(behaviour(perDay = 47.6), "week"))
        assertEquals(HeroText("47", "unlocks a day"), unlocksHero(behaviour(perDay = 47.4), "week"))
    }

    @Test fun unlocksMultiDayNullPerDay() {
        assertEquals(HeroText("—", "unlocks a day"), unlocksHero(behaviour(perDay = null), "week"))
    }

    @Test fun wakesFallbackSingleDayThreshold() {
        assertNull(unlocksHero(behaviour(signal = false, wakes = 4), "today"))
        assertEquals(HeroText("5", "screen wake-ups"), unlocksHero(behaviour(signal = false, wakes = 5), "today"))
        assertEquals(HeroText("40", "screen wake-ups"), unlocksHero(behaviour(signal = false, wakes = 40), "yesterday"))
    }

    @Test fun wakesFallbackMultiDayThreshold() {
        assertNull(unlocksHero(behaviour(signal = false, wakesPerDay = 0.99), "week"))
        assertNull(unlocksHero(behaviour(signal = false, wakesPerDay = null), "week"))
        assertEquals(HeroText("1", "screen wake-ups a day"), unlocksHero(behaviour(signal = false, wakesPerDay = 1.0), "week"))
        assertEquals(HeroText("12", "screen wake-ups a day"), unlocksHero(behaviour(signal = false, wakesPerDay = 11.6), "week"))
    }

    @Test fun signalPresentIgnoresWakesFallback() {
        assertEquals("unlocks", unlocksHero(behaviour(signal = true, unlocks = 2, wakes = 90), "today")!!.caption)
    }

    // ---- coverageNote ----
    @Test fun coverageNoteSingularPluralAndNull() {
        assertEquals("1 day with partial or no data aren't averaged.", coverageNote(coverage(partial = 1), "week"))
        assertEquals("3 days with partial or no data aren't averaged.", coverageNote(coverage(partial = 1, missing = 2), "month"))
        assertNull(coverageNote(coverage(), "week"))
    }

    @Test fun coverageNoteNullForSingleDay() {
        assertNull(coverageNote(coverage(partial = 1, missing = 4), "today"))
        assertNull(coverageNote(coverage(partial = 1, missing = 4), "yesterday"))
    }

    @Test fun coverageNoteIgnoresLiveAndComplete() {
        assertNull(coverageNote(PhoneCoverage(5, 0, 2, 0, null, null), "week"))
    }

    // ---- inAppsText ----
    @Test fun inAppsSingleDay() {
        assertEquals("In apps: 45m", inAppsText(screen(app = 45 * min), "today"))
        assertNull(inAppsText(screen(app = 0), "today"))
    }

    @Test fun inAppsMultiDay() {
        assertEquals("In apps: 2h a day", inAppsText(screen(avgApp = 2 * hour), "week"))
        assertNull(inAppsText(screen(avgApp = 0L), "week"))
        assertNull(inAppsText(screen(avgApp = null), "week"))
    }

    @Test fun inAppsMultiDayIgnoresTotal() {
        assertNull(inAppsText(screen(app = hour, avgApp = null), "week"))
    }

    // ---- weekdayWeekendNote ----
    @Test fun weekdayWeekendNote() {
        val s = screen().copy(weekday = DayGroupAverage(3 * hour, 5), weekend = DayGroupAverage(5 * hour + 30 * min, 2))
        assertEquals("Weekdays 3h · Weekends 5h 30m a day (5 and 2 days)", weekdayWeekendNote(s))
    }

    @Test fun weekdayWeekendNoteNeedsBoth() {
        assertNull(weekdayWeekendNote(screen().copy(weekday = DayGroupAverage(hour, 5), weekend = null)))
        assertNull(weekdayWeekendNote(screen().copy(weekday = null, weekend = DayGroupAverage(hour, 2))))
    }

    // ---- small stats ----
    @Test fun wakeUpsStat() {
        assertEquals("12", wakeUpsStat(behaviour(wakes = 12), "today"))
        assertEquals("30/day", wakeUpsStat(behaviour(wakesPerDay = 29.6), "week"))
        assertEquals("—", wakeUpsStat(behaviour(wakesPerDay = null), "week"))
    }

    @Test fun gapAndQuickStats() {
        assertEquals("14m", gapStat(behaviour(gap = 14 * min)))
        assertEquals("—", gapStat(behaviour(gap = null)))
        assertEquals("37%", quickChecksStat(behaviour(quick = 37)))
        assertEquals("0%", quickChecksStat(behaviour(quick = 0)))
        assertEquals("—", quickChecksStat(behaviour(quick = null)))
    }

    // ---- captions ----
    @Test fun byDayCaption() {
        assertNull(byDayCaption(2))
        assertNull(byDayCaption(7))
        assertEquals("Last 8 days", byDayCaption(8))
        assertEquals("Last 30 days", byDayCaption(30))
        assertEquals("Last 30 days", byDayCaption(90))
    }

    @Test fun busiestCaption() {
        assertEquals("Busiest around 9 PM", busiestCaption(21, "today"))
        assertEquals("Busiest around 9 PM · average per day", busiestCaption(21, "week"))
        assertNull(busiestCaption(null, "week"))
    }

    @Test fun appsFootnote() {
        assertEquals("Share of time spent inside apps (2h 30m total)", appsFootnote(base.apps.copy(totalMs = 150 * min)))
    }

    // ---- topAppRows ----
    @Test fun topAppRowsFormatting() {
        val apps = base.apps.copy(totalMs = 2 * hour, apps = listOf(app("Chat", 72 * min, 60.0, opens = 1), app("Maps", 48 * min, 40.0, opens = 12)), othersMs = 0)
        val rows = topAppRows(apps)
        assertEquals(2, rows.size)
        assertEquals(TopAppRow("Chat", "1h 12m · 1 open", 0.6f, "60%"), rows[0])
        assertEquals(TopAppRow("Maps", "48m · 12 opens", 0.4f, "40%"), rows[1])
    }

    @Test fun topAppRowsRoundsShare() {
        val apps = base.apps.copy(totalMs = hour, apps = listOf(app("A", 30 * min, 33.4), app("B", 20 * min, 33.5)), othersMs = 0)
        val rows = topAppRows(apps)
        assertEquals("33%", rows[0].trailing)
        assertEquals("34%", rows[1].trailing)
    }

    @Test fun topAppRowsLimitAndOther() {
        val list = (1..8).map { app("App$it", hour, 10.0) } // 80% listed
        val apps = base.apps.copy(totalMs = 10 * hour, apps = list, othersMs = 2 * hour)
        val rows = topAppRows(apps, limit = 6)
        assertEquals(7, rows.size)
        assertEquals("App6", rows[5].title)
        val other = rows.last()
        assertEquals("Other apps", other.title)
        assertNull(other.subtitle)
        assertEquals("40%", other.trailing) // 2 omitted listed apps (20%) + others 2h/10h (20%)
        assertEquals(0.4f, other.fraction, 0.0001f)
    }

    @Test fun topAppRowsDefaultLimitIsSix() {
        val list = (1..7).map { app("App$it", hour, 14.0) }
        val rows = topAppRows(base.apps.copy(totalMs = 7 * hour, apps = list, othersMs = 0))
        assertEquals(7, rows.size) // 6 + "Other apps" (the 7th app's 14%)
        assertEquals("Other apps", rows[6].title)
        assertEquals("14%", rows[6].trailing)
    }

    @Test fun topAppRowsNoOtherWhenNothingOmitted() {
        val apps = base.apps.copy(totalMs = hour, apps = listOf(app("A", hour, 100.0)), othersMs = 0)
        assertEquals(1, topAppRows(apps).size)
    }

    @Test fun topAppRowsOtherThreshold() {
        val total = 1000 * min
        val below = base.apps.copy(totalMs = total, apps = listOf(app("A", 1, 99.625)), othersMs = 3 * min + 45_000L) // 0.375%
        assertEquals(1, topAppRows(below).size)
        val at = base.apps.copy(totalMs = total, apps = listOf(app("A", 1, 99.5)), othersMs = 5 * min) // exactly 0.5%
        val rows = topAppRows(at)
        assertEquals(2, rows.size)
        assertEquals("1%", rows[1].trailing)
        assertEquals(0.005f, rows[1].fraction, 0.0001f)
    }

    @Test fun topAppRowsOtherFromOthersMsOnly() {
        val apps = base.apps.copy(totalMs = 10 * hour, apps = listOf(app("A", 9 * hour, 90.0)), othersMs = hour)
        val rows = topAppRows(apps)
        assertEquals("10%", rows[1].trailing)
    }

    @Test fun topAppRowsZeroTotalNoOthersDivision() {
        val apps = base.apps.copy(totalMs = 0, apps = emptyList(), othersMs = 0)
        assertEquals(emptyList<TopAppRow>(), topAppRows(apps))
    }

    @Test fun topAppRowsFractionsStayInRange() {
        val apps = base.apps.copy(totalMs = hour, apps = listOf(app("A", hour, 100.0000001), app("B", 0, -1.0)), othersMs = 0)
        val rows = topAppRows(apps)
        assertTrue(rows.all { it.fraction in 0f..1f })
    }
}
