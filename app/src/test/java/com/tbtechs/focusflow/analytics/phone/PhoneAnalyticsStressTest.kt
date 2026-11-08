package com.tbtechs.focusflow.analytics.phone

import com.tbtechs.focusflow.analytics.ForegroundEventType.ACTIVITY_PAUSED
import com.tbtechs.focusflow.analytics.ForegroundEventType.ACTIVITY_RESUMED
import com.tbtechs.focusflow.analytics.ForegroundEventType.KEYGUARD_HIDDEN
import com.tbtechs.focusflow.analytics.ForegroundEventType.SCREEN_INTERACTIVE
import com.tbtechs.focusflow.analytics.ForegroundEventType.SCREEN_NON_INTERACTIVE
import com.tbtechs.focusflow.analytics.ForegroundSpanTracker
import com.tbtechs.focusflow.analytics.ForegroundUsageEvent
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsightEngine
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsightInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Random

/** Large, awkward inputs: nothing may throw, hang, or produce numbers that disagree with each other. */
class PhoneAnalyticsStressTest {
    private val utc = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 7)
    private val min = 60_000L
    private val hour = 60 * min
    private fun ago(n: Int): LocalDate = today.minusDays(n.toLong())
    private val pkgs = (1..20).map { "com.stress.app$it" }

    private fun dayStart(date: LocalDate) = date.atStartOfDay(utc).toInstant().toEpochMilli()

    private class Data(
        val range: PhoneDateRange,
        val days: List<PhoneDay>,
        val appDays: List<AppDayRow>,
        val sessions: List<AppSessionRow>,
        val blocked: List<BlockedDayRow>,
        val focus: List<FocusSessionRow>,
    )

    /** 90 days × 20 apps × 300 app sessions a day, built from a fixed seed. */
    private fun bigData(): Data {
        val rnd = Random(42)
        val range = PhoneDateRange(ago(89), today)
        val days = mutableListOf<PhoneDay>()
        val appDays = mutableListOf<AppDayRow>()
        val sessions = mutableListOf<AppSessionRow>()
        val blocked = mutableListOf<BlockedDayRow>()
        val focus = mutableListOf<FocusSessionRow>()
        for (back in 89 downTo 0) {
            val date = ago(back)
            val key = date.toString()
            val status = if (back == 0) PhoneDayStatus.LIVE else PhoneDayStatus.COMPLETE
            days += PhoneTestFixtures.day(
                date, status, screenOnMin = 120 + rnd.nextInt(200), unlocks = 20 + rnd.nextInt(80), wakes = 30 + rnd.nextInt(60),
                sessions = 40 + rnd.nextInt(40), bucketCounts = List(PhoneBuckets.COUNT) { rnd.nextInt(30) },
                longestMs = (10 + rnd.nextInt(60)) * min, gapSumMs = 600 * min, gapCount = 40,
                firstUnlockAtMs = dayStart(date) + (6 + rnd.nextInt(4)) * hour,
                firstApps = pkgs.take(5).associateWith { 1 + rnd.nextInt(10) }, unlocksWithoutApp = rnd.nextInt(10),
            )
            for (pkg in pkgs) {
                appDays += PhoneTestFixtures.app(date, pkg, (5 + rnd.nextInt(40)) * min, name = pkg.substringAfterLast('.'), hour = rnd.nextInt(24), sessions = 15)
            }
            repeat(300) {
                sessions += AppSessionRow(pkgs[rnd.nextInt(pkgs.size)], key, dayStart(date) + rnd.nextInt(80_000) * 1000L, (5 + rnd.nextInt(600)) * 1000L)
            }
            pkgs.take(4).forEach {
                val attempts = rnd.nextInt(6)
                blocked += BlockedDayRow(key, it, it, attempts, List(24) { h -> if (h == 10) attempts else 0 })
            }
            if (rnd.nextInt(3) == 0) {
                val start = dayStart(date) + 9 * hour
                focus += FocusSessionRow(key, start, start + (20 + rnd.nextInt(60)) * min, rnd.nextInt(2))
            }
        }
        return Data(range, days, appDays, sessions, blocked, focus)
    }

    private fun categoryOf(pkg: String) = if (pkg.hashCode() % 2 == 0) UsageCategory.SOCIAL else UsageCategory.OTHER

    @Test
    fun ninetyDaysOfDataBuildsEverySectionAndTheInsightsInTime() {
        val data = bigData()
        val started = System.nanoTime()

        val screen = ScreenTimeBuilder.build(data.range, data.days, data.appDays)
        val behaviour = PhoneBehaviourBuilder.build(data.range, data.days, pkgs.associateWith { it }, utc)
        val apps = AppsBuilder.build(AppsInput(data.range, data.appDays, data.sessions, pkgs.take(5).associateWith { 3 }, null, ::categoryOf))
        val distraction = DistractionBuilder.build(apps, data.appDays, pkgs.take(4).associateWith { 5 }, ::categoryOf, null)
        val focus = FocusBlockingBuilder.build(data.range, today, dayStart(today) + 12 * hour, utc, data.focus, data.blocked, data.focus, data.blocked)
        val insights = PhoneInsightEngine().generate(
            PhoneInsightInput(
                today = today, days = data.days, appDays = data.appDays, appSessions = data.sessions, blockedDays = data.blocked,
                focusSessions = data.focus, appNames = pkgs.associateWith { it }, categoryOf = ::categoryOf, zone = utc,
            ),
        )

        val seconds = (System.nanoTime() - started) / 1e9
        assertTrue("took $seconds s", seconds < 20.0)
        assertEquals(90, screen.days.size)
        assertEquals(24, screen.screenOnByHourAvgMs.size)
        assertEquals(24, behaviour.unlocksByHour.size)
        assertTrue(apps.apps.size <= 30)
        assertTrue(distraction.distractionMs > 0)
        assertTrue(focus.sessions > 0)
        assertTrue(insights.size <= 14)
        assertEquals(insights.map { it.id }.size, insights.map { it.id }.toSet().size)
    }

    @Test
    fun theNumbersOnDifferentCardsAgree() {
        val data = bigData()
        val screen = ScreenTimeBuilder.build(data.range, data.days, data.appDays)
        val apps = AppsBuilder.build(AppsInput(data.range, data.appDays, data.sessions, emptyMap(), null, ::categoryOf))
        val focus = FocusBlockingBuilder.build(data.range, today, dayStart(today) + 12 * hour, utc, data.focus, data.blocked, data.focus, data.blocked)

        // Daily bars add up to the headline total.
        assertEquals(screen.totalScreenOnMs, screen.days.sumOf { it.screenOnMs })
        // The hour-of-day average and the per-day average describe the same time (integer division loses < 1 ms per hour).
        val perDay = screen.avgScreenOnPerDayMs!!
        val hourSum = screen.screenOnByHourAvgMs.sum()
        assertTrue("hours $hourSum vs day $perDay", kotlin.math.abs(hourSum - perDay) <= 24)
        // Every app's time is accounted for once: the listed apps plus the tail equal the total.
        assertEquals(apps.totalMs, apps.apps.sumOf { it.totalMs } + apps.othersMs)
        assertEquals(data.appDays.sumOf { it.foregroundMs }, apps.totalMs)
        // Category shares add up to about 100%.
        val categoryShare = apps.categories.sumOf { it.sharePercent }
        assertTrue("categories $categoryShare", kotlin.math.abs(categoryShare - 100.0) < 0.5)
        // Blocked attempts: the headline equals the sum over days and over apps.
        assertEquals(data.blocked.sumOf { it.attempts }, focus.blockedAttempts)
        assertEquals(focus.blockedAttempts, focus.blockedApps.sumOf { it.attempts })
        assertEquals(focus.blockedAttempts, focus.blockedByHour.sum())
    }

    @Test
    fun twentyThousandEventsInOneDayReduceQuickly() {
        val date = ago(1)
        val base = dayStart(date)
        val events = mutableListOf<ForegroundUsageEvent>()
        for (g in 0 until 4000) {
            val pkg = pkgs[g % pkgs.size]
            val t = base + g * 21_000L
            events += ForegroundUsageEvent(SCREEN_INTERACTIVE, t)
            events += ForegroundUsageEvent(KEYGUARD_HIDDEN, t + 1_000)
            events += ForegroundUsageEvent(ACTIVITY_RESUMED, t + 2_000, pkg, "$pkg.Main")
            events += ForegroundUsageEvent(ACTIVITY_PAUSED, t + 12_000, pkg, "$pkg.Main")
            events += ForegroundUsageEvent(SCREEN_NON_INTERACTIVE, t + 13_000)
        }
        assertEquals(20_000, events.size)
        val now = base + 25 * hour
        val started = System.nanoTime()
        val sessions = ForegroundSpanTracker(excludedPackages = emptySet()).sessions(events, base, base + 24 * hour, now)
        val day = PhoneEventReducer(utc).reduceDay(date, events, sessions, now, PhoneDayStatus.COMPLETE)
        val seconds = (System.nanoTime() - started) / 1e9
        assertTrue("took $seconds s", seconds < 10.0)
        assertTrue(day.screenOnMs in 1..24 * hour)
        assertEquals(day.screenOnMs, day.screenOnHourlyMs.sum())
        assertTrue(day.unlockCount > 0)
        assertEquals(day.unlockCount, day.unlockHourly.sum())
        assertEquals(day.wakeCount, day.wakeHourly.sum())
    }

    @Test
    fun emptyAndAllZeroInputsNeverThrowOrProduceNaN() {
        val range = PhoneDateRange(ago(6), today)
        val zeroDays = range.dates().map { PhoneTestFixtures.day(it, PhoneDayStatus.COMPLETE) }
        val zeroApps = range.dates().flatMap { d -> pkgs.take(3).map { PhoneTestFixtures.app(d, it, 0L) } }

        val screen = ScreenTimeBuilder.build(range, zeroDays, zeroApps)
        val behaviour = PhoneBehaviourBuilder.build(range, zeroDays, emptyMap(), utc)
        val apps = AppsBuilder.build(AppsInput(range, zeroApps, emptyList(), emptyMap(), null, ::categoryOf))
        val distraction = DistractionBuilder.build(apps, zeroApps, emptyMap(), ::categoryOf, null)
        val focus = FocusBlockingBuilder.build(range, today, dayStart(today) + 12 * hour, utc, emptyList(), emptyList(), emptyList(), emptyList())

        assertEquals(0L, screen.totalScreenOnMs)
        assertEquals(0, behaviour.unlocks)
        assertEquals(0L, apps.totalMs)
        assertTrue(apps.apps.isEmpty())
        assertEquals(0L, distraction.distractionMs)
        assertEquals(0, focus.blockedAttempts)
        assertTrue(apps.categories.all { !it.sharePercent.isNaN() })
        assertTrue(focus.blockedApps.isEmpty())
    }

    @Test
    fun everyBarMathFunctionSurvivesHugeAndBrokenValues() {
        val nasty = listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -5.0, 0.0, Long.MAX_VALUE.toDouble())
        val fractions = com.tbtechs.focusflow.ui.stats.phone.barFractions(nasty)
        assertTrue(fractions.all { !it.isNaN() && it in 0f..1f })
        assertEquals(24, com.tbtechs.focusflow.ui.stats.phone.barFractions(List(24) { Long.MAX_VALUE }).size)
    }
}
