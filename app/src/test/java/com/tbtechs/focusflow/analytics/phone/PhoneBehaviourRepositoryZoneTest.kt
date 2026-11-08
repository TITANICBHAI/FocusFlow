package com.tbtechs.focusflow.analytics.phone

import com.tbtechs.focusflow.analytics.ANALYTICS_TODAY
import com.tbtechs.focusflow.analytics.ANALYTICS_WEEK
import com.tbtechs.focusflow.analytics.ANALYTICS_YESTERDAY
import com.tbtechs.focusflow.analytics.ForegroundEventType.SCREEN_INTERACTIVE
import com.tbtechs.focusflow.analytics.ForegroundEventType.SCREEN_NON_INTERACTIVE
import com.tbtechs.focusflow.analytics.ForegroundUsageEvent
import com.tbtechs.focusflow.analytics.UsageEventRead
import com.tbtechs.focusflow.analytics.UsageEventsSource
import com.tbtechs.focusflow.analytics.WEEK_MODE_DYNAMIC
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsightInputResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Same repository, but in a half-hour-offset zone (Asia/Kolkata, UTC+05:30) where local and UTC dates disagree for hours. */
class PhoneBehaviourRepositoryZoneTest {
    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val min = 60_000L
    private val today = LocalDate.of(2026, 11, 7)
    private fun d(back: Int) = today.minusDays(back.toLong())

    private fun at(date: LocalDate, h: Int, m: Int = 0): Long =
        ZonedDateTime.of(date.year, date.monthValue, date.dayOfMonth, h, m, 0, 0, kolkata).toInstant().toEpochMilli()

    private class Source(var result: UsageEventRead) : UsageEventsSource {
        override suspend fun readForegroundEvents(startMs: Long, endMs: Long): UsageEventRead = result
    }

    private fun repo(store: FakePhoneBehaviourStore, source: Source, nowMs: Long): PhoneBehaviourRepository {
        val clock = Clock.fixed(Instant.ofEpochMilli(nowMs), kolkata)
        val writer = PhoneBehaviourRollupWriter(store, source, emptySet(), { it }, null, clock, kolkata)
        val sync = BlockedAttemptSync({ "[]" }, store, kolkata)
        return PhoneBehaviourRepository(
            store = store, eventSource = source, writer = writer, blockedSync = sync,
            hasPermission = { true }, categoryOf = { UsageCategory.OTHER }, appNameResolver = { it },
            excludedPackages = emptySet(), clock = clock, zoneId = kolkata, dispatcher = Dispatchers.Unconfined,
        )
    }

    private fun screenSpan(date: LocalDate, fromH: Int, fromM: Int, toH: Int, toM: Int) = listOf(
        ForegroundUsageEvent(SCREEN_INTERACTIVE, at(date, fromH, fromM)),
        ForegroundUsageEvent(SCREEN_NON_INTERACTIVE, at(date, toH, toM)),
    )

    private fun ready(r: PhoneReportResult): PhoneReport {
        assertTrue("expected Ready but was $r", r is PhoneReportResult.Ready)
        return (r as PhoneReportResult.Ready).report
    }

    @Test
    fun yesterdayAndWeekResolveToLocalDates() {
        // 12:00 in Kolkata is 06:30 UTC: same date either way.
        val src = Source(UsageEventRead.Available(screenSpan(d(1), 9, 0, 9, 30), at(d(6), 0)))
        val r = repo(FakePhoneBehaviourStore(), src, at(today, 12))
        assertEquals(PhoneDateRange(d(1), d(1)), ready(runBlocking { r.loadReport(ANALYTICS_YESTERDAY, WEEK_MODE_DYNAMIC, 1) }).range)
        assertEquals(PhoneDateRange(d(6), today), ready(runBlocking { r.loadReport(ANALYTICS_WEEK, WEEK_MODE_DYNAMIC, 1) }).range)
    }

    @Test
    fun localTodayWinsWhenUtcIsStillYesterday() {
        // 02:00 on 7 Nov in Kolkata is 20:30 on 6 Nov in UTC.
        val src = Source(UsageEventRead.Available(screenSpan(today, 0, 30, 1, 30), at(d(6), 0)))
        val r = repo(FakePhoneBehaviourStore(), src, at(today, 2))
        assertEquals(PhoneDateRange(today, today), ready(runBlocking { r.loadReport(ANALYTICS_TODAY, WEEK_MODE_DYNAMIC, 1) }).range)
        assertEquals(PhoneDateRange(d(1), d(1)), ready(runBlocking { r.loadReport(ANALYTICS_YESTERDAY, WEEK_MODE_DYNAMIC, 1) }).range)
    }

    @Test
    fun screenOnSpansLandInTheLocalHourAndDate() {
        // 00:10–00:20 local on 6 Nov is 18:40–18:50 UTC on 5 Nov; 23:45–23:59 local is 18:15–18:29 UTC on 6 Nov.
        val events = screenSpan(d(1), 0, 10, 0, 20) + screenSpan(d(1), 23, 45, 23, 59)
        val r = repo(FakePhoneBehaviourStore(), Source(UsageEventRead.Available(events, at(d(2), 0))), at(today, 12))
        val report = ready(runBlocking { r.loadReport(ANALYTICS_YESTERDAY, WEEK_MODE_DYNAMIC, 1) })
        assertEquals(24 * min, report.screenTime.totalScreenOnMs)
        val hourly = report.screenTime.screenOnByHourAvgMs
        assertEquals(10 * min, hourly[0])
        assertEquals(14 * min, hourly[23])
        assertEquals(0L, hourly[18]) // the UTC hour must stay empty
        assertEquals(24 * min, hourly.sum())
    }

    @Test
    fun aSpanCrossingLocalMidnightIsSplitBetweenTheTwoLocalDates() {
        val events = listOf(
            ForegroundUsageEvent(SCREEN_INTERACTIVE, at(d(2), 23, 50)),
            ForegroundUsageEvent(SCREEN_NON_INTERACTIVE, at(d(1), 0, 10)),
        )
        val r = repo(FakePhoneBehaviourStore(), Source(UsageEventRead.Available(events, at(d(3), 0))), at(today, 12))
        val report = ready(runBlocking { r.loadReport(ANALYTICS_WEEK, WEEK_MODE_DYNAMIC, 1) })
        val byDate = report.screenTime.days.associateBy { it.date }
        assertEquals(10 * min, byDate.getValue(d(2)).screenOnMs)
        assertEquals(10 * min, byDate.getValue(d(1)).screenOnMs)
    }

    @Test
    fun aDayIsOnlyCompleteWhenEventsCoverItsLocalMidnight() {
        // The first event the OS still has is 03:00 local on 6 Nov: that day is missing its first three hours.
        val events = screenSpan(d(1), 9, 0, 9, 30) + screenSpan(d(0), 8, 0, 8, 10)
        val r = repo(FakePhoneBehaviourStore(), Source(UsageEventRead.Available(events, at(d(1), 3, 0))), at(today, 12))
        val report = ready(runBlocking { r.loadReport(ANALYTICS_YESTERDAY, WEEK_MODE_DYNAMIC, 1) })
        assertEquals(DayCoverage.PARTIAL, report.screenTime.days.single().coverage)
    }

    @Test
    fun insightInputUsesTheLocalToday() {
        // 02:00 on 7 Nov in Kolkata is still 6 Nov in UTC.
        val events = (1..4).flatMap { screenSpan(d(it), 9, 0, 9, 30) }
        val r = repo(FakePhoneBehaviourStore(), Source(UsageEventRead.Available(events, at(d(6), 0))), at(today, 2))
        val result = runBlocking { r.loadInsightInput() }
        assertTrue("expected Ready but was $result", result is PhoneInsightInputResult.Ready)
        assertEquals(today, (result as PhoneInsightInputResult.Ready).input.today)
    }
}
