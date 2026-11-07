package com.tbtechs.focusflow.analytics.phone

import com.tbtechs.focusflow.analytics.ANALYTICS_ALL_TIME
import com.tbtechs.focusflow.analytics.ANALYTICS_THREE_MONTHS
import com.tbtechs.focusflow.analytics.ANALYTICS_TODAY
import com.tbtechs.focusflow.analytics.ANALYTICS_WEEK
import com.tbtechs.focusflow.analytics.ANALYTICS_YESTERDAY
import com.tbtechs.focusflow.analytics.ForegroundEventType.ACTIVITY_PAUSED
import com.tbtechs.focusflow.analytics.ForegroundEventType.ACTIVITY_RESUMED
import com.tbtechs.focusflow.analytics.ForegroundEventType.KEYGUARD_HIDDEN
import com.tbtechs.focusflow.analytics.ForegroundEventType.SCREEN_INTERACTIVE
import com.tbtechs.focusflow.analytics.ForegroundEventType.SCREEN_NON_INTERACTIVE
import com.tbtechs.focusflow.analytics.ForegroundUsageEvent
import com.tbtechs.focusflow.analytics.UsageEventRead
import com.tbtechs.focusflow.analytics.UsageEventsSource
import com.tbtechs.focusflow.analytics.WEEK_MODE_DYNAMIC
import com.tbtechs.focusflow.analytics.WEEK_MODE_FIXED
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsightInputResult
import com.tbtechs.focusflow.data.local.dao.SessionOverrideCountRow
import com.tbtechs.focusflow.data.local.entity.UsageRollupAppDayEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class PhoneBehaviourRepositoryTest {
    private val utc = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 7) // a Saturday
    private val min = 60_000L

    private fun at(date: LocalDate, h: Int, m: Int = 0, s: Int = 0): Long =
        ZonedDateTime.of(date.year, date.monthValue, date.dayOfMonth, h, m, s, 0, utc).toInstant().toEpochMilli()

    private val now = at(today, 12, 0)
    private fun d(back: Int) = today.minusDays(back.toLong())

    private fun usageOn(date: LocalDate, pkg: String = "com.app.a") = listOf(
        ForegroundUsageEvent(SCREEN_INTERACTIVE, at(date, 9, 0, 0)),
        ForegroundUsageEvent(KEYGUARD_HIDDEN, at(date, 9, 0, 3)),
        ForegroundUsageEvent(ACTIVITY_RESUMED, at(date, 9, 0, 5), pkg, "$pkg.Main"),
        ForegroundUsageEvent(ACTIVITY_PAUSED, at(date, 9, 10, 0), pkg, "$pkg.Main"),
        ForegroundUsageEvent(SCREEN_NON_INTERACTIVE, at(date, 9, 10, 1)),
    )

    private class Source(var result: UsageEventRead) : UsageEventsSource {
        var calls = 0
        override suspend fun readForegroundEvents(startMs: Long, endMs: Long): UsageEventRead { calls++; return result }
    }

    private class ThrowingStore(
        private val inner: FakePhoneBehaviourStore,
        private val failEarliest: Boolean = false,
        private val failPrune: Boolean = false,
    ) : PhoneBehaviourStore by inner {
        override suspend fun earliestDate(): String? = if (failEarliest) error("db down") else inner.earliestDate()
        override suspend fun prune(cutoffDate: String) { if (failPrune) error("prune failed") else inner.prune(cutoffDate) }
    }

    private class Env(
        val store: FakePhoneBehaviourStore = FakePhoneBehaviourStore(),
        val source: Source = Source(UsageEventRead.Available(emptyList(), null)),
        var permission: Boolean = true,
        var temptationLog: String = "[]",
    )

    private fun repo(
        env: Env,
        store: PhoneBehaviourStore = env.store,
        excluded: Set<String> = emptySet(),
        hasPermission: suspend () -> Boolean = { env.permission },
    ): PhoneBehaviourRepository {
        val clock = Clock.fixed(Instant.ofEpochMilli(now), utc)
        val writer = PhoneBehaviourRollupWriter(store, env.source, excluded, { it }, null, clock, utc)
        val sync = BlockedAttemptSync({ env.temptationLog }, store, utc)
        return PhoneBehaviourRepository(
            store = store, eventSource = env.source, writer = writer, blockedSync = sync,
            hasPermission = hasPermission, categoryOf = { UsageCategory.OTHER }, appNameResolver = { it },
            excludedPackages = excluded, clock = clock, zoneId = utc, dispatcher = Dispatchers.Unconfined,
        )
    }

    private fun completeRow(date: LocalDate, screenMin: Int = 60, unlocks: Int = 0) =
        PhoneDay.empty(date.toString(), PhoneDayStatus.COMPLETE)
            .copy(screenOnMs = screenMin * min, unlockCount = unlocks, unlockSignal = unlocks > 0)
            .toEntity(now - 2 * 24 * 60 * min, null)

    private fun appRow(date: LocalDate, pkg: String, ms: Long, sessions: Int = 0) = UsageRollupAppDayEntity(
        date = date.toString(), packageName = pkg, appName = pkg, category = null, foregroundMs = ms,
        hourlyMs = List(24) { if (it == 12) ms else 0L }.joinToString(","), launchCount = 0,
        sessionCount = sessions, firstStartAtMs = null, lastUsedAtMs = 0L,
    )

    private fun ready(r: PhoneReportResult): PhoneReport {
        assertTrue("expected Ready but was $r", r is PhoneReportResult.Ready)
        return (r as PhoneReportResult.Ready).report
    }

    private fun load(env: Env, window: String = ANALYTICS_WEEK, mode: String = WEEK_MODE_DYNAMIC, r: PhoneBehaviourRepository = repo(env)) =
        runBlocking { r.loadReport(window, mode, 1) }

    // 1
    @Test
    fun noPermissionReturnsPermissionNeededWithoutTouchingAnything() {
        val env = Env(permission = false)
        assertEquals(PhoneReportResult.PermissionNeeded, load(env))
        assertEquals(PhoneInsightInputResult.PermissionNeeded, runBlocking { repo(env).loadInsightInput() })
        assertEquals(0, env.source.calls)
        assertEquals(0, env.store.replaceCalls)
        assertTrue(env.store.pruneCutoffs.isEmpty())
        assertTrue(env.store.mergeCalls.isEmpty())
    }

    // 2
    @Test
    fun noDataAnywhereIsNoData() {
        assertEquals(PhoneReportResult.NoData, load(Env()))
    }

    // 3
    @Test
    fun yesterdayIsOneDateFromTheStoredRow() {
        val env = Env().also { it.store.days += completeRow(d(1), screenMin = 45, unlocks = 12) }
        val report = ready(load(env, ANALYTICS_YESTERDAY))
        assertEquals(PhoneDateRange(d(1), d(1)), report.range)
        assertEquals(1, report.screenTime.days.size)
        assertEquals(45 * min, report.screenTime.totalScreenOnMs)
        assertEquals(12, report.behaviour.unlocks)
        assertEquals(ANALYTICS_YESTERDAY, report.window)
    }

    // 4
    @Test
    fun dynamicWeekHasSevenDaysWithTodayLiveAndIncludedInTotals() {
        val env = Env(source = Source(UsageEventRead.Available(usageOn(today), at(d(6), 0, 0))))
        for (back in 1..6) env.store.days += completeRow(d(back), screenMin = 60)
        val report = ready(load(env))
        assertEquals(PhoneDateRange(d(6), today), report.range)
        val days = report.screenTime.days
        assertEquals(7, days.size)
        assertEquals(DayCoverage.LIVE, days.last().coverage)
        assertTrue(days.last().screenOnMs > 0)
        assertEquals(6 * 60 * min + days.last().screenOnMs, report.screenTime.totalScreenOnMs)
        assertEquals(1, report.coverage.liveDays)
        assertEquals(6, report.coverage.completeDays)
        assertEquals(0, report.coverage.missingDays)
    }

    @Test
    fun todayWindowIsLiveOnly() {
        val env = Env(source = Source(UsageEventRead.Available(usageOn(today), at(d(3), 0, 0))))
        val report = ready(load(env, ANALYTICS_TODAY))
        assertEquals(PhoneDateRange(today, today), report.range)
        assertEquals(DayCoverage.LIVE, report.screenTime.days.single().coverage)
        assertEquals(1, report.behaviour.unlocks)
    }

    @Test
    fun liveAppRowsAndSessionsFeedTheAppsSection() {
        val env = Env(source = Source(UsageEventRead.Available(usageOn(today, "com.app.a") + usageOn(today, "com.launcher").map {
            it.copy(timestampMs = it.timestampMs + 3_600_000L)
        }, at(d(3), 0, 0))))
        val report = ready(load(env, ANALYTICS_TODAY, r = repo(env, excluded = setOf("com.launcher"))))
        val apps = report.apps.apps
        assertEquals(listOf("com.app.a"), apps.map { it.packageName })
        assertEquals(595_000L, apps.single().totalMs)
        assertEquals(1, apps.single().opens)
    }

    @Test
    fun aSessionStartedBeforeMidnightCountsTimeTodayButIsNotAnOpenToday() {
        val y = d(1)
        val events = listOf(
            ForegroundUsageEvent(SCREEN_INTERACTIVE, at(y, 23, 49)),
            ForegroundUsageEvent(KEYGUARD_HIDDEN, at(y, 23, 49, 2)),
            ForegroundUsageEvent(ACTIVITY_RESUMED, at(y, 23, 50), "com.night", "com.night.Main"),
            ForegroundUsageEvent(ACTIVITY_PAUSED, at(today, 0, 10), "com.night", "com.night.Main"),
        ) + usageOn(today, "com.day")
        val env = Env(source = Source(UsageEventRead.Available(events, at(d(3), 0, 0))))
        val apps = ready(load(env, ANALYTICS_TODAY)).apps.apps.associateBy { it.packageName }
        assertEquals(10 * min, apps.getValue("com.night").totalMs)
        assertEquals(0, apps.getValue("com.night").opens)
        assertEquals(1, apps.getValue("com.day").opens)
    }

    // 5
    @Test
    fun allTimeStartsAtTheEarliestStoredDate() {
        val env = Env().also {
            it.store.usageRollupDates += "2026-10-20"
            it.store.days += completeRow(LocalDate.of(2026, 10, 25))
        }
        val report = ready(load(env, ANALYTICS_ALL_TIME))
        assertEquals(PhoneDateRange(LocalDate.of(2026, 10, 20), d(0)), report.range)
        assertEquals(19, report.screenTime.days.size)
        assertEquals(1, report.coverage.completeDays)
    }

    @Test
    fun threeMonthsIsNinetyDaysEndingToday() {
        val env = Env().also { it.store.days += completeRow(d(5)) }
        assertEquals(PhoneDateRange(d(89), today), ready(load(env, ANALYTICS_THREE_MONTHS)).range)
    }

    // 6
    @Test
    fun resolveRangeClampsAFixedWeekToToday() {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), utc)
        val r = resolveRange(ANALYTICS_WEEK, WEEK_MODE_FIXED, 1, zdt, null)
        assertEquals(PhoneDateRange(LocalDate.of(2026, 11, 2), today), r)
        // week starting Sunday
        assertEquals(LocalDate.of(2026, 11, 1), resolveRange(ANALYTICS_WEEK, WEEK_MODE_FIXED, 0, zdt, null).start)
    }

    @Test
    fun resolveRangeAllTimeUsesEarliestAndNeverStartsAfterToday() {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), utc)
        assertEquals(PhoneDateRange(LocalDate.of(2026, 9, 1), today), resolveRange(ANALYTICS_ALL_TIME, WEEK_MODE_DYNAMIC, 1, zdt, LocalDate.of(2026, 9, 1)))
        assertEquals(PhoneDateRange(today, today), resolveRange(ANALYTICS_ALL_TIME, WEEK_MODE_DYNAMIC, 1, zdt, null))
        assertEquals(PhoneDateRange(today, today), resolveRange(ANALYTICS_ALL_TIME, WEEK_MODE_DYNAMIC, 1, zdt, today.plusDays(3)))
    }

    @Test
    fun resolveRangeYesterdayAndDynamicWeek() {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), utc)
        assertEquals(PhoneDateRange(d(1), d(1)), resolveRange(ANALYTICS_YESTERDAY, WEEK_MODE_DYNAMIC, 1, zdt, null))
        assertEquals(PhoneDateRange(d(6), today), resolveRange(ANALYTICS_WEEK, WEEK_MODE_DYNAMIC, 1, zdt, null))
    }

    // 7
    @Test
    fun daysThatExistOnlyAsEventsAreWrittenBeforeTheReportIsBuilt() {
        val env = Env(source = Source(UsageEventRead.Available(usageOn(d(2)) + usageOn(d(1)), at(d(3), 0, 0))))
        val report = ready(load(env))
        val byDate = report.screenTime.days.associateBy { it.date }
        assertEquals(DayCoverage.COMPLETE, byDate.getValue(d(2)).coverage)
        assertEquals(DayCoverage.COMPLETE, byDate.getValue(d(1)).coverage)
        assertTrue(byDate.getValue(d(1)).screenOnMs > 0)
        assertEquals(DayCoverage.MISSING, byDate.getValue(d(5)).coverage)
        assertTrue(env.store.days.any { it.date == d(2).toString() })
    }

    // 8
    @Test
    fun previousPeriodNeedsFourDaysWithAppData() {
        fun changeFor(previousDays: Int): Int? {
            val env = Env()
            for (back in 3..6) env.store.appDayRows += appRow(d(back), "a", 60 * min) // 4 data days in the week
            for (i in 0 until previousDays) env.store.appDayRows += appRow(d(7 + i), "a", 30 * min) // previous period 11-… -7.. -13
            return ready(load(env)).apps.apps.single().changePercent
        }
        assertNull(changeFor(3))
        assertEquals(100, changeFor(4))
        assertEquals(100, changeFor(7))
    }

    @Test
    fun previousPeriodIsOnlyUsedForWeekAndThreeMonths() {
        val env = Env()
        for (back in 3..6) env.store.appDayRows += appRow(d(back), "a", 60 * min)
        for (i in 0 until 4) env.store.appDayRows += appRow(d(7 + i), "a", 30 * min)
        env.store.days += completeRow(d(1))
        env.store.appDayRows += appRow(d(1), "a", 10 * min)
        val yesterday = ready(load(env, ANALYTICS_YESTERDAY)).apps.apps
        assertTrue(yesterday.all { it.changePercent == null })
        assertTrue(ready(load(env, ANALYTICS_ALL_TIME)).apps.apps.all { it.changePercent == null })
    }

    // 9
    @Test
    fun temptationLogEntriesBecomeBlockedAttempts() {
        val env = Env(temptationLog = """[{"pkg":"com.x","appName":"X","timestamp":${at(d(2), 10)}},{"pkg":"com.x","appName":"X","timestamp":${at(d(1), 11)}}]""")
        env.store.days += completeRow(d(1))
        env.store.appDayRows += appRow(d(1), "com.x", 5 * min)
        val report = ready(load(env))
        assertEquals(2, report.focus.blockedAttempts)
        assertEquals("com.x", report.focus.blockedApps.single().packageName)
        assertEquals(2, report.distraction.apps.single().blockedAttempts)
    }

    @Test
    fun focusSessionsAreParsedAndBadRowsSkipped() {
        val env = Env()
        env.store.days += completeRow(d(1))
        env.store.focusRows += SessionOverrideCountRow(1, "t", "2026-11-05T10:00:00Z", "2026-11-05T11:00:00Z", 0)
        env.store.focusRows += SessionOverrideCountRow(2, "t", "not a time", "2026-11-05T11:00:00Z", 0)
        env.store.focusRows += SessionOverrideCountRow(3, "t", "2026-11-04T10:00:00Z", "garbage", 1)
        env.store.focusRows += SessionOverrideCountRow(4, null, "2026-11-06T10:00:00Z", "2026-11-06T10:30:00Z", 2)
        val focus = ready(load(env)).focus
        assertEquals(2, focus.sessions)
        assertEquals(90 * min, focus.focusMs)
        assertEquals(1, focus.heldSessions)
        assertEquals(1, focus.interruptedSessions)
    }

    @Test
    fun aRunningFocusSessionCountsUntilNow() {
        val env = Env()
        env.store.days += completeRow(d(1))
        env.store.focusRows += SessionOverrideCountRow(1, "t", "2026-11-07T10:00:00Z", null, 0)
        val focus = ready(load(env)).focus
        assertEquals(2 * 60 * min, focus.focusMs)
        assertEquals(0, focus.heldSessions)
    }

    // 10
    @Test
    fun aThrowingStoreGivesFailedNotAnException() {
        val env = Env()
        env.store.days += completeRow(d(1))
        val result = load(env, r = repo(env, store = ThrowingStore(env.store, failEarliest = true)))
        assertTrue(result is PhoneReportResult.Failed)
        assertEquals("db down", (result as PhoneReportResult.Failed).message)
        val insights = runBlocking { repo(env, store = ThrowingStore(env.store, failEarliest = true)).loadInsightInput() }
        assertTrue(insights is PhoneInsightInputResult.Ready || insights is PhoneInsightInputResult.Building || insights is PhoneInsightInputResult.Failed)
    }

    @Test
    fun aFailureWhilePersistingIsSwallowedAndTheReportStillBuilds() {
        val env = Env(source = Source(UsageEventRead.Available(usageOn(d(1)), at(d(2), 0, 0))))
        env.store.days += completeRow(d(3), screenMin = 20)
        val report = ready(load(env, r = repo(env, store = ThrowingStore(env.store, failPrune = true))))
        assertEquals(20 * min, report.screenTime.days.single { it.date == d(3) }.screenOnMs)
    }

    @Test
    fun cancellationIsRethrown() {
        val env = Env()
        var cancelled = false
        try {
            runBlocking { repo(env, hasPermission = { throw CancellationException("stop") }).loadReport(ANALYTICS_WEEK, WEEK_MODE_DYNAMIC, 1) }
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        cancelled = false
        try {
            runBlocking { repo(env, hasPermission = { throw CancellationException("stop") }).loadInsightInput() }
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    @Test
    fun lostUsageAccessDuringPersistingIsPermissionNeeded() {
        val env = Env(source = Source(UsageEventRead.Unknown(UsageEventRead.Unknown.Reason.ACCESS_REVOKED)))
        assertEquals(PhoneReportResult.PermissionNeeded, load(env))
        assertEquals(PhoneInsightInputResult.PermissionNeeded, runBlocking { repo(env).loadInsightInput() })
    }

    @Test
    fun otherUnknownReadsFallBackToStoredDataWithoutLiveToday() {
        val env = Env(source = Source(UsageEventRead.Unknown(UsageEventRead.Unknown.Reason.EVENTS_UNAVAILABLE)))
        env.store.days += completeRow(d(1), screenMin = 33)
        val report = ready(load(env))
        assertEquals(33 * min, report.screenTime.totalScreenOnMs)
        assertEquals(0, report.coverage.liveDays)
        assertEquals(DayCoverage.MISSING, report.screenTime.days.last().coverage)
    }

    // 11
    @Test
    fun insightInputIsBuildingBelowThreeCompleteDays() {
        val env = Env()
        env.store.days += completeRow(d(1))
        env.store.days += completeRow(d(2))
        env.store.days += PhoneDay.empty(d(3).toString(), PhoneDayStatus.PARTIAL).toEntity(now, null)
        assertEquals(PhoneInsightInputResult.Building(2), runBlocking { repo(env).loadInsightInput() })
    }

    @Test
    fun insightInputIsReadyWithTheLiveDay() {
        val env = Env(source = Source(UsageEventRead.Available(usageOn(today), at(d(6), 0, 0))))
        for (back in 1..5) env.store.days += completeRow(d(back), unlocks = 3)
        env.store.appDayRows += appRow(d(2), "com.app.a", 10 * min)
        env.store.focusRows += SessionOverrideCountRow(1, "t", "2026-11-05T10:00:00Z", "2026-11-05T11:00:00Z", 0)
        val result = runBlocking { repo(env).loadInsightInput() }
        assertTrue("was $result", result is PhoneInsightInputResult.Ready)
        val input = (result as PhoneInsightInputResult.Ready).input
        assertEquals(today, input.today)
        // d1..d5 stored, d6 written by the pass (earliest-event day), plus live today
        assertEquals(7, input.days.size)
        assertEquals(1, input.days.count { it.status == PhoneDayStatus.LIVE })
        assertEquals(1, input.days.count { it.date == today.toString() })
        assertTrue(input.appDays.any { it.packageName == "com.app.a" && it.date == d(2).toString() })
        assertTrue(input.appDays.any { it.packageName == "com.app.a" && it.date == today.toString() })
        assertEquals(1, input.focusSessions.size)
        assertEquals(utc, input.zone)
    }

    @Test
    fun insightInputCoversNinetyDays() {
        val env = Env()
        for (back in listOf(1, 2, 3, 89, 90)) env.store.days += completeRow(d(back))
        val input = (runBlocking { repo(env).loadInsightInput() } as PhoneInsightInputResult.Ready).input
        assertTrue(input.days.any { it.date == d(89).toString() })
        assertTrue(input.days.none { it.date == d(90).toString() })
    }

    @Test
    fun namesComeFromAppRowsFirstAppsAndBlockedRows() {
        val env = Env(temptationLog = """[{"pkg":"com.b","appName":"Blocked B","timestamp":${at(d(1), 10)}}]""")
        env.store.days += completeRow(d(1))
        env.store.appDayRows += appRow(d(1), "com.a", 5 * min).copy(appName = "App A")
        val report = ready(load(env))
        assertEquals("App A", report.apps.apps.single().appName)
        assertEquals("Blocked B", report.focus.blockedApps.single().appName)
        assertNotNull(report.coverage.appDataSince)
        assertEquals(d(1), report.coverage.appDataSince)
        assertEquals(d(1), report.coverage.phoneDataSince)
    }
}
