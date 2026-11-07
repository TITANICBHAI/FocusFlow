package com.tbtechs.focusflow.analytics.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class FocusBlockingBuilderTest {
    private val utc = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 7)
    private val min = 60_000L
    private val hour = 60 * min
    private val now = at(today, 12, 0)

    private fun at(date: LocalDate, h: Int, m: Int = 0, zone: ZoneId = utc): Long =
        ZonedDateTime.of(date.year, date.monthValue, date.dayOfMonth, h, m, 0, 0, zone).toInstant().toEpochMilli()

    private fun sess(startMs: Long, endMs: Long?, overrides: Int = 0) =
        FocusSessionRow(date = "", startedAtMs = startMs, endedAtMs = endMs, overrideCount = overrides)

    /** An ended session of exactly [lengthMs] starting at [h]:00 on [date]. */
    private fun ended(date: LocalDate, h: Int, lengthMs: Long, overrides: Int = 0) =
        sess(at(date, h), at(date, h) + lengthMs, overrides)

    private fun blocked(date: LocalDate, pkg: String, attempts: Int, hour: Int = 10, name: String = pkg) =
        BlockedDayRow(date.toString(), pkg, name, attempts, List(24) { if (it == hour) attempts else 0 })

    private fun build(
        range: PhoneDateRange = PhoneDateRange(today.minusDays(6), today),
        sessions: List<FocusSessionRow> = emptyList(),
        blocked: List<BlockedDayRow> = emptyList(),
        trendSessions: List<FocusSessionRow> = emptyList(),
        trendBlocked: List<BlockedDayRow> = emptyList(),
        nowMs: Long = now,
        zone: ZoneId = utc,
    ) = FocusBlockingBuilder.build(range, today, nowMs, zone, sessions, blocked, trendSessions, trendBlocked)

    @Test
    fun emptyInput() {
        val s = build()
        assertEquals(0L, s.focusMs)
        assertEquals(0, s.sessions)
        assertEquals(List(5) { 0 }, s.durationBucketCounts)
        assertEquals(0, s.blockedAttempts)
        assertTrue(s.blockedApps.isEmpty())
        assertEquals(List(24) { 0 }, s.blockedByHour)
        assertNull(s.blockedPeakHour)
        assertNull(s.attemptsPerFocusHour)
        assertNull(s.trend)
        assertEquals(8, s.weeks.size)
        assertEquals(today.minusDays(55), s.weeks.first().blockStart)
        assertEquals(today.minusDays(6), s.weeks.last().blockStart)
    }

    @Test
    fun overlapIsClippedAtBothRangeEdges() {
        val range = PhoneDateRange(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 5))
        val s = build(
            range = range,
            sessions = listOf(
                sess(at(LocalDate.of(2026, 11, 2), 23), at(LocalDate.of(2026, 11, 3), 1)),  // 1 h inside
                sess(at(LocalDate.of(2026, 11, 5), 23), at(LocalDate.of(2026, 11, 6), 2)),  // 1 h inside
                sess(at(LocalDate.of(2026, 11, 1), 8), at(LocalDate.of(2026, 11, 1), 9)),   // outside
                sess(at(LocalDate.of(2026, 11, 4), 8), at(LocalDate.of(2026, 11, 4), 9)),   // 1 h inside
            ),
        )
        assertEquals(3 * hour, s.focusMs)
        assertEquals(3, s.sessions)
        assertEquals(3, s.heldSessions)
    }

    @Test
    fun sessionTouchingTheRangeEdgeCountsAsOutside() {
        val range = PhoneDateRange(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3))
        val s = build(range = range, sessions = listOf(sess(at(LocalDate.of(2026, 11, 2), 22), at(LocalDate.of(2026, 11, 3), 0))))
        assertEquals(0, s.sessions)
        assertEquals(0L, s.focusMs)
    }

    @Test
    fun aRunningSessionCountsTowardFocusTimeOnlyAndEndsAtNow() {
        val s = build(sessions = listOf(sess(at(today, 10), null)))
        assertEquals(2 * hour, s.focusMs)
        assertEquals(1, s.sessions)
        assertEquals(0, s.heldSessions)
        assertEquals(0, s.interruptedSessions)
        assertEquals(List(5) { 0 }, s.durationBucketCounts)
    }

    @Test
    fun rangeEndIsClippedToNow() {
        // ended after "now" can't happen in real data, but the clip must still hold
        val s = build(sessions = listOf(sess(at(today, 11), at(today, 15))))
        assertEquals(hour, s.focusMs)
    }

    @Test
    fun heldAndInterruptedAreCountedFromEndedSessionsOnly() {
        val s = build(
            sessions = listOf(
                ended(today.minusDays(1), 9, hour, overrides = 0),
                ended(today.minusDays(2), 9, hour, overrides = 0),
                ended(today.minusDays(3), 9, hour, overrides = 2),
                sess(at(today, 9), null, overrides = 5),
            ),
        )
        assertEquals(4, s.sessions)
        assertEquals(2, s.heldSessions)
        assertEquals(1, s.interruptedSessions)
    }

    @Test
    fun durationBucketBoundaries() {
        val lengths = listOf(
            15 * min - 1_000L to 0, 15 * min to 1, 30 * min - 1_000L to 1, 30 * min to 2,
            60 * min - 1_000L to 2, 60 * min to 3, 120 * min - 1_000L to 3, 120 * min to 4, 300 * min to 4,
        )
        for ((length, bucket) in lengths) {
            val counts = build(sessions = listOf(ended(today.minusDays(1), 1, length))).durationBucketCounts
            assertEquals("length $length", List(5) { if (it == bucket) 1 else 0 }, counts)
        }
    }

    @Test
    fun durationUsesTheFullSessionLengthNotTheClippedOverlap() {
        val range = PhoneDateRange(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3))
        val s = build(range = range, sessions = listOf(sess(at(LocalDate.of(2026, 11, 2), 23), at(LocalDate.of(2026, 11, 3), 0, 30)))) // 90 min, 30 min inside
        assertEquals(30 * min, s.focusMs)
        assertEquals(listOf(0, 0, 0, 1, 0), s.durationBucketCounts) // the 90-minute bucket, not the 30-minute one
    }

    @Test
    fun blockedAppsTopEightSharesHoursAndPeak() {
        val rows = (1..10).map { blocked(today.minusDays(1), "p$it", it, hour = 20) } +
            blocked(today.minusDays(2), "p10", 5, hour = 7)
        val s = build(blocked = rows)
        assertEquals(60, s.blockedAttempts)
        assertEquals(8, s.blockedApps.size)
        assertEquals("p10", s.blockedApps[0].packageName)
        assertEquals(15, s.blockedApps[0].attempts)
        assertEquals(25.0, s.blockedApps[0].sharePercent, 1e-9)
        assertEquals("p3", s.blockedApps.last().packageName)
        assertEquals(55, s.blockedByHour[20])
        assertEquals(5, s.blockedByHour[7])
        assertEquals(20, s.blockedPeakHour)
    }

    @Test
    fun blockedTiesAreOrderedByNameAndPeakTiesGoToTheEarliestHour() {
        val s = build(blocked = listOf(blocked(today, "p1", 3, hour = 15, name = "Zed"), blocked(today, "p2", 3, hour = 6, name = "Abe")))
        assertEquals(listOf("Abe", "Zed"), s.blockedApps.map { it.appName })
        assertEquals(6, s.blockedPeakHour)
    }

    @Test
    fun blockedAppNameFallsBackToThePackageAndKeepsAnEarlierNameIfLaterIsBlank() {
        val s = build(blocked = listOf(blocked(today.minusDays(2), "p1", 1, name = "Named"), blocked(today.minusDays(1), "p1", 1, name = ""), blocked(today, "p2", 1, name = "")))
        val byPkg = s.blockedApps.associateBy { it.packageName }
        assertEquals("Named", byPkg.getValue("p1").appName)
        assertEquals(2, byPkg.getValue("p1").attempts)
        assertEquals("p2", byPkg.getValue("p2").appName)
    }

    @Test
    fun attemptsPerFocusHourNeedsThirtyMinutes() {
        val b = listOf(blocked(today.minusDays(1), "p", 6))
        assertNull(build(sessions = listOf(ended(today.minusDays(1), 9, 30 * min - 1)), blocked = b).attemptsPerFocusHour)
        assertEquals(12.0, build(sessions = listOf(ended(today.minusDays(1), 9, 30 * min)), blocked = b).attemptsPerFocusHour!!, 1e-9)
        assertEquals(3.0, build(sessions = listOf(ended(today.minusDays(1), 9, 2 * hour)), blocked = b).attemptsPerFocusHour!!, 1e-9)
    }

    @Test
    fun weeksAreEightBlocksOldestFirstWithExactBoundaries() {
        val s = build(
            trendBlocked = listOf(
                blocked(today, "p", 1),                    // block 0 (last day)
                blocked(today.minusDays(6), "p", 2),       // block 0 (first day)
                blocked(today.minusDays(7), "p", 4),       // block 1 (last day)
                blocked(today.minusDays(13), "p", 8),      // block 1 (first day)
                blocked(today.minusDays(14), "p", 16),     // block 2
                blocked(today.minusDays(56), "p", 99),     // before block 7
            ),
        )
        assertEquals(8, s.weeks.size)
        assertEquals((7 downTo 0).map { today.minusDays(7L * it + 6) }, s.weeks.map { it.blockStart })
        assertEquals(3, s.weeks[7].attempts)   // block 0
        assertEquals(12, s.weeks[6].attempts)  // block 1
        assertEquals(16, s.weeks[5].attempts)  // block 2
        assertEquals(0, s.weeks[0].attempts)
    }

    @Test
    fun weekPointsUseStartDateForSessionCountsAndOverlapForFocusTime() {
        val blockStart = today.minusDays(6)
        val spanning = sess(at(blockStart.minusDays(1), 23), at(blockStart, 3)) // starts in block 1, 3 h into block 0
        val s = build(
            trendSessions = listOf(
                ended(today, 1, hour, overrides = 0),
                ended(today.minusDays(3), 1, hour, overrides = 1),
                sess(at(today, 11), null),   // running: not an "ended" session
                spanning,
            ),
        )
        val block0 = s.weeks[7]
        assertEquals(2, block0.endedSessions)
        assertEquals(1, block0.heldSessions)
        assertEquals(hour + hour + hour + 3 * hour, block0.focusMs) // 1h + 1h + running 1h + 3h overlap
        val block1 = s.weeks[6]
        assertEquals(1, block1.endedSessions)
        assertEquals(1, block1.heldSessions)
        assertEquals(hour, block1.focusMs)
    }

    // ---- trend ----

    /** 4 h of focus in each of "now" (blocks 0+1) and "before" (blocks 2+3), with the given attempt counts. */
    private fun trend(nowAttempts: Int, beforeAttempts: Int, focusNow: Long = 4 * hour, focusBefore: Long = 4 * hour): FocusTrend? {
        val sessions = listOf(ended(today.minusDays(2), 1, focusNow), ended(today.minusDays(20), 1, focusBefore))
        val rows = listOfNotNull(
            if (nowAttempts > 0) blocked(today.minusDays(2), "p", nowAttempts) else null,
            if (beforeAttempts > 0) blocked(today.minusDays(20), "p", beforeAttempts) else null,
        )
        return build(trendSessions = sessions, trendBlocked = rows).trend
    }

    @Test
    fun trendNeedsOneHourOnBothSides() {
        assertNull(trend(10, 10, focusNow = 59 * min))
        assertNull(trend(10, 10, focusBefore = 59 * min))
        assertNotNull(trend(10, 10, focusNow = hour, focusBefore = hour))
    }

    @Test
    fun trendDirectionBoundaries() {
        // before: 100 attempts in 4 h = 25/h
        assertEquals(TrendDirection.FLAT, trend(119, 100)!!.direction)   // +19%
        assertEquals(TrendDirection.UP, trend(120, 100)!!.direction)     // +20%
        assertEquals(TrendDirection.FLAT, trend(81, 100)!!.direction)    // −19%
        assertEquals(TrendDirection.DOWN, trend(80, 100)!!.direction)    // −20%
        assertEquals(TrendDirection.FLAT, trend(100, 100)!!.direction)
    }

    @Test
    fun trendValuesAreRatesAndPercent() {
        val t = trend(120, 100)!!
        assertEquals(30.0, t.attemptsPerFocusHourNow, 1e-9)
        assertEquals(25.0, t.attemptsPerFocusHourBefore, 1e-9)
        assertEquals(20, t.changePercent)
        assertEquals(-20, trend(80, 100)!!.changePercent)
    }

    @Test
    fun trendWithNoAttemptsBefore() {
        assertEquals(FocusTrend(TrendDirection.FLAT, 0.0, 0.0, 0), trend(0, 0))
        assertNull(trend(5, 0))
    }

    @Test
    fun trendIgnoresTheMainWindowArguments() {
        val s = build(
            sessions = listOf(ended(today.minusDays(2), 1, 4 * hour), ended(today.minusDays(20), 1, 4 * hour)),
            blocked = listOf(blocked(today.minusDays(2), "p", 50)),
        )
        assertNull(s.trend)
    }

    @Test
    fun trendWindowsAreBlocksZeroToOneAndTwoToThree() {
        // Focus and attempts only in block 4 (days 28..34 ago) must not count as "before".
        val s = build(
            trendSessions = listOf(ended(today.minusDays(2), 1, 2 * hour), ended(today.minusDays(30), 1, 2 * hour)),
            trendBlocked = listOf(blocked(today.minusDays(30), "p", 40)),
        )
        assertNull(s.trend)
        // Day 14 ago (first day of block 2) and day 27 ago (last day of block 3) both count as "before".
        // "Now" is blocks 0 and 1: day 2 ago (block 0, no attempts) and day 9 ago (block 1, 4 attempts).
        val t = build(
            trendSessions = listOf(
                ended(today.minusDays(2), 1, hour), ended(today.minusDays(9), 1, hour),
                ended(today.minusDays(14), 1, hour), ended(today.minusDays(27), 1, hour),
            ),
            trendBlocked = listOf(blocked(today.minusDays(9), "p", 4), blocked(today.minusDays(14), "p", 1), blocked(today.minusDays(27), "p", 5)),
        ).trend
        assertEquals(2.0, t!!.attemptsPerFocusHourNow, 1e-9)    // 4 attempts / 2 h
        assertEquals(3.0, t.attemptsPerFocusHourBefore, 1e-9)   // (1 + 5) attempts / 2 h
    }

    @Test
    fun zoneChangesWhichDayASessionStartsOn() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        // 20:00 UTC on 6 Nov is 01:30 IST on 7 Nov → block 0 in Kolkata; both are in block 0 here, so test a block edge:
        val edgeDay = today.minusDays(6) // first day of block 0
        val startUtc = at(edgeDay.minusDays(1), 20) // 23:xx UTC of the day before the block, which is 01:30 IST on edgeDay
        val inKolkata = build(trendSessions = listOf(sess(startUtc, startUtc + hour)), zone = kolkata).weeks[7]
        val inUtc = build(trendSessions = listOf(sess(startUtc, startUtc + hour)), zone = utc).weeks[6]
        assertEquals(1, inKolkata.endedSessions)
        assertEquals(1, inUtc.endedSessions)
    }
}
