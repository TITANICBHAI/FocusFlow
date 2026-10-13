package com.tbtechs.focusflow.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ForegroundSpanTrackerTest {
    private val tracker = ForegroundSpanTracker(excludedPackages = emptySet())

    @Test
    fun nextResumeClosesPreviousPackageWhenPauseIsMissing() {
        val sessions = read(
            resumed(100, "app.a", "A"),
            resumed(250, "app.b", "B"),
            paused(400, "app.b", "B"),
        )
        assertEquals(listOf("app.a" to (100L to 250L), "app.b" to (250L to 400L)), sessions.map {
            it.packageName to (it.startedAtMs to it.endedAtMs)
        })
    }

    @Test
    fun stoppedEventClosesOnlyTheMatchingActivity() {
        val sessions = read(
            resumed(100, "app.a", "ActivityA"),
            resumed(200, "app.a", "ActivityB"),
            stopped(250, "app.a", "ActivityA"),
            stopped(300, "app.a", "ActivityB"),
        )
        assertEquals(listOf(100L to 300L), sessions.map { it.startedAtMs to it.endedAtMs })
    }

    @Test
    fun pausedAndStopOnlySessionsAreClosed() {
        val paused = read(resumed(100, "app.a", "A"), paused(180, "app.a", "A"))
        val stopped = read(resumed(100, "app.a", "A"), stopped(180, "app.a", "A"))
        assertEquals(80L, paused.single().durationMs)
        assertEquals(80L, stopped.single().durationMs)
    }

    @Test
    fun screenOffKeyguardAndShutdownCloseTheOpenSpan() {
        val closingEvents = listOf(
            ForegroundEventType.SCREEN_NON_INTERACTIVE,
            ForegroundEventType.KEYGUARD_SHOWN,
            ForegroundEventType.DEVICE_SHUTDOWN,
        )
        closingEvents.forEach { closeType ->
            val sessions = read(
                resumed(100, "app.a", "A"),
                ForegroundUsageEvent(closeType, 175),
            )
            assertEquals(closeType.name, 75L, sessions.single().durationMs)
        }
    }

    @Test
    fun startupClosesStaleSpanAndFollowingResumeStartsANewOne() {
        val sessions = read(
            resumed(100, "app.a", "A"),
            ForegroundUsageEvent(ForegroundEventType.DEVICE_STARTUP, 200),
            resumed(300, "app.b", "B"),
            paused(400, "app.b", "B"),
        )
        assertEquals(listOf(100L to 200L, 300L to 400L), sessions.map {
            it.startedAtMs to it.endedAtMs
        })
    }

    @Test
    fun duplicateAndOutOfOrderResumesProduceNonOverlappingSessions() {
        val sessions = read(
            resumed(300, "app.b", "B"),
            resumed(100, "app.a", "A"),
            resumed(100, "app.a", "A"),
            paused(250, "app.a", "A"),
            paused(400, "app.b", "B"),
        )
        assertEquals(listOf(100L to 250L, 300L to 400L), sessions.map {
            it.startedAtMs to it.endedAtMs
        })
        assertTrue(sessions.zipWithNext().all { (left, right) -> left.endedAtMs <= right.startedAtMs })
    }

    @Test
    fun windowLookbackRetainsOriginalSessionIdentityButClipsDailyTime() {
        val sessions = read(
            resumed(0, "app.a", "A"),
            paused(20, "app.a", "A"),
            windowStart = 10,
            windowEnd = 30,
            now = 30,
        )
        assertEquals(0L, sessions.single().startedAtMs)
        val row = UsageCalendarAggregator.aggregate(
            sessions,
            rangeStartMs = 10,
            rangeEndMs = 30,
            zoneId = ZoneId.of("UTC"),
        ).single()
        assertEquals(10L, row.foregroundMs)
    }

    @Test
    fun midnightSessionHasOneStartDateAndClippedDailyTime() {
        val zone = ZoneId.of("UTC")
        val start = LocalDate.parse("2026-10-01").atTime(23, 50).atZone(zone)
            .toInstant().toEpochMilli()
        val end = LocalDate.parse("2026-10-02").atTime(0, 20).atZone(zone)
            .toInstant().toEpochMilli()
        val sessions = read(
            resumed(start, "app.a", "A"),
            paused(end, "app.a", "A"),
            windowStart = start - 6 * HOUR,
            windowEnd = end + 1,
            now = end + 1,
        )
        assertEquals(1, sessions.size)
        assertEquals(30 * MINUTE, sessions.single().durationMs)
        val days = UsageCalendarAggregator.aggregate(
            sessions,
            start - 6 * HOUR,
            end + 1,
            zone,
        ).associateBy { it.date }
        assertEquals(10 * MINUTE, days.getValue("2026-10-01").foregroundMs)
        assertEquals(20 * MINUTE, days.getValue("2026-10-02").foregroundMs)
        assertEquals(1, days.getValue("2026-10-01").sessionCount)
        assertEquals(0, days.getValue("2026-10-02").sessionCount)
        assertEquals(1, days.getValue("2026-10-01").launchCount)
        assertFalse(days.getValue("2026-10-02").hourlyMs.drop(1).any { it != 0L })
        assertEquals(20 * MINUTE, days.getValue("2026-10-02").hourlyMs[0])
    }

    @Test
    fun dstDaysUseCalendarBoundariesForTwentyThreeAndTwentyFiveHourDays() {
        val zone = ZoneId.of("America/New_York")
        val dates = listOf(
            LocalDate.parse("2026-03-08") to 23L,
            LocalDate.parse("2026-11-01") to 25L,
        )
        dates.forEach { (date, expectedHours) ->
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val sessions = read(
                resumed(start, "app.a", "A"),
                paused(end, "app.a", "A"),
                windowStart = start,
                windowEnd = end,
                now = end,
            )
            val row = UsageCalendarAggregator.aggregate(sessions, start, end, zone).single()
            assertEquals(expectedHours * HOUR, row.foregroundMs)
            assertEquals(date.toString(), row.date)
        }
    }

    @Test
    fun inferredTailIsCappedButMeasuredSessionsAreNot() {
        val open = read(resumed(100, "app.a", "A"), now = 100 + 5 * HOUR)
        val closed = read(
            resumed(100, "app.a", "A"),
            paused(100 + 5 * HOUR, "app.a", "A"),
            now = 100 + 6 * HOUR,
        )
        assertEquals(4 * HOUR, open.single().durationMs)
        assertEquals(5 * HOUR, closed.single().durationMs)
    }

    @Test
    fun excludedPackagesAreFilteredAfterTheyCanCloseThePreviousSpan() {
        val localTracker = ForegroundSpanTracker()
        val sessions = localTracker.sessions(
            listOf(
                resumed(100, "app.a", "A"),
                resumed(200, "com.android.systemui", "SystemUI"),
                paused(300, "com.android.systemui", "SystemUI"),
            ),
            windowStartMs = 0,
            windowEndMs = 500,
            nowMs = 500,
        )
        assertEquals(listOf("app.a"), sessions.map { it.packageName })
        assertEquals(100L, sessions.single().durationMs)
    }

    private fun read(
        vararg events: ForegroundUsageEvent,
        windowStart: Long = 0,
        windowEnd: Long = 10 * HOUR,
        now: Long = windowEnd,
    ) = tracker.sessions(events.toList(), windowStart, windowEnd, now)

    private fun resumed(at: Long, pkg: String, activity: String) =
        ForegroundUsageEvent(ForegroundEventType.ACTIVITY_RESUMED, at, pkg, activity)

    private fun paused(at: Long, pkg: String, activity: String) =
        ForegroundUsageEvent(ForegroundEventType.ACTIVITY_PAUSED, at, pkg, activity)

    private fun stopped(at: Long, pkg: String, activity: String) =
        ForegroundUsageEvent(ForegroundEventType.ACTIVITY_STOPPED, at, pkg, activity)

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
