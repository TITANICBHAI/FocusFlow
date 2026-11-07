package com.tbtechs.focusflow.analytics.phone

import com.tbtechs.focusflow.analytics.ForegroundEventType
import com.tbtechs.focusflow.analytics.ForegroundEventType.DEVICE_SHUTDOWN
import com.tbtechs.focusflow.analytics.ForegroundEventType.DEVICE_STARTUP
import com.tbtechs.focusflow.analytics.ForegroundEventType.KEYGUARD_HIDDEN
import com.tbtechs.focusflow.analytics.ForegroundEventType.KEYGUARD_SHOWN
import com.tbtechs.focusflow.analytics.ForegroundEventType.SCREEN_INTERACTIVE
import com.tbtechs.focusflow.analytics.ForegroundEventType.SCREEN_NON_INTERACTIVE
import com.tbtechs.focusflow.analytics.ForegroundSession
import com.tbtechs.focusflow.analytics.ForegroundUsageEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class PhoneEventReducerTest {
    private val utc = ZoneId.of("UTC")
    private val reducer = PhoneEventReducer(utc)
    private val d1 = LocalDate.of(2026, 5, 1)
    private val d2 = LocalDate.of(2026, 5, 2)
    private val minute = 60_000L
    private val hour = 60 * minute

    /** Timestamp on 2026-05-[day] in UTC. */
    private fun t(day: Int, h: Int, m: Int = 0, s: Int = 0): Long =
        ZonedDateTime.of(2026, 5, day, h, m, s, 0, utc).toInstant().toEpochMilli()

    private fun ev(type: ForegroundEventType, day: Int, h: Int, m: Int = 0, s: Int = 0) =
        ForegroundUsageEvent(type, t(day, h, m, s))

    private fun fs(pkg: String, start: Long, end: Long) = ForegroundSession(pkg, start, end, null, true)

    private val farFuture = t(5, 12)

    private fun reduce(
        date: LocalDate,
        events: List<ForegroundUsageEvent>,
        sessions: List<ForegroundSession> = emptyList(),
        now: Long = farFuture,
        status: PhoneDayStatus = PhoneDayStatus.COMPLETE,
    ) = reducer.reduceDay(date, events, sessions, now, status)

    // 1
    @Test
    fun simpleDay() {
        val day = reduce(
            d1,
            listOf(
                ev(SCREEN_INTERACTIVE, 1, 8, 0, 0),
                ev(KEYGUARD_HIDDEN, 1, 8, 0, 3),
                ev(SCREEN_NON_INTERACTIVE, 1, 8, 5, 3),
            ),
        )
        assertEquals("2026-05-01", day.date)
        assertEquals(1, day.wakeCount)
        assertEquals(1, day.wakeHourly[8])
        assertEquals(1, day.unlockCount)
        assertEquals(1, day.unlockHourly[8])
        assertEquals(303_000L, day.screenOnMs)
        assertEquals(303_000L, day.screenOnHourlyMs[8])
        assertEquals(1, day.sessionCount)
        assertEquals(1, day.sessionBucketCounts[3])
        assertEquals(300_000L, day.sessionBucketMs[3])
        assertEquals(300_000L, day.longestSessionMs)
        assertTrue(day.unlockSignal)
        assertEquals(SessionBasis.UNLOCK, day.sessionBasis)
        assertEquals(t(1, 8, 0, 3), day.firstUnlockAtMs)
    }

    // 2
    @Test
    fun repeatedEventsWhileAlreadyInThatStateChangeNothing() {
        val day = reduce(
            d1,
            listOf(
                ev(SCREEN_INTERACTIVE, 1, 8, 0, 0),
                ev(SCREEN_INTERACTIVE, 1, 8, 0, 10),
                ev(KEYGUARD_HIDDEN, 1, 8, 0, 12),
                ev(KEYGUARD_HIDDEN, 1, 8, 0, 20),
                ev(SCREEN_NON_INTERACTIVE, 1, 8, 1, 0),
            ),
        )
        assertEquals(1, day.wakeCount)
        assertEquals(1, day.unlockCount)
        assertEquals(60_000L, day.screenOnMs)
        assertEquals(1, day.sessionCount)
    }

    // 3
    @Test
    fun spanAcrossAnHourBoundarySplitsHourly() {
        val day = reduce(
            d1,
            listOf(ev(SCREEN_INTERACTIVE, 1, 10, 55), ev(SCREEN_NON_INTERACTIVE, 1, 11, 10)),
        )
        assertEquals(5 * minute, day.screenOnHourlyMs[10])
        assertEquals(10 * minute, day.screenOnHourlyMs[11])
        assertEquals(15 * minute, day.screenOnMs)
        assertEquals(day.screenOnMs, day.screenOnHourlyMs.sum())
    }

    // 4
    @Test
    fun screenOnAcrossMidnightIsSplitBetweenDays() {
        val events = listOf(ev(SCREEN_INTERACTIVE, 1, 23, 50), ev(SCREEN_NON_INTERACTIVE, 2, 0, 20))
        val now = t(3, 0)
        val first = reduce(d1, events, now = now)
        assertEquals(10 * minute, first.screenOnMs)
        assertEquals(10 * minute, first.screenOnHourlyMs[23])
        assertEquals(1, first.wakeCount)
        // No unlock events: sessions are screen-on spans; the span belongs to the day it started, full length.
        assertEquals(SessionBasis.SCREEN, first.sessionBasis)
        assertEquals(1, first.sessionCount)
        assertEquals(30 * minute, first.longestSessionMs)
        assertEquals(1, first.sessionBucketCounts[4])

        val second = reduce(d2, events, now = now)
        assertEquals(20 * minute, second.screenOnMs)
        assertEquals(20 * minute, second.screenOnHourlyMs[0])
        assertEquals(0, second.wakeCount)
        assertEquals(0, second.sessionCount)
    }

    // 5
    @Test
    fun unlockedSessionAcrossMidnightBelongsToTheDayItStarted() {
        val events = listOf(
            ev(SCREEN_INTERACTIVE, 1, 23, 50),
            ev(KEYGUARD_HIDDEN, 1, 23, 50),
            ev(SCREEN_NON_INTERACTIVE, 2, 0, 20),
        )
        val now = t(3, 0)
        val first = reduce(d1, events, now = now)
        assertEquals(1, first.unlockCount)
        assertEquals(1, first.sessionCount)
        assertEquals(1, first.sessionBucketCounts[4])
        assertEquals(30 * minute, first.sessionBucketMs[4])
        assertEquals(30 * minute, first.longestSessionMs)

        val second = reduce(d2, events, now = now)
        assertEquals(0, second.sessionCount)
        assertEquals(0, second.unlockCount)
        assertFalse(second.unlockSignal)
    }

    // 6
    @Test
    fun liveDayCountsAnOpenSessionSoFar() {
        val now = t(1, 10, 7)
        val day = reduce(
            d1,
            listOf(
                ev(SCREEN_INTERACTIVE, 1, 10, 0),
                ev(KEYGUARD_HIDDEN, 1, 10, 0),
                // Events after "now" must not be used.
                ev(SCREEN_NON_INTERACTIVE, 1, 10, 30),
            ),
            now = now,
            status = PhoneDayStatus.LIVE,
        )
        assertEquals(PhoneDayStatus.LIVE, day.status)
        assertEquals(1, day.sessionCount)
        assertEquals(7 * minute, day.longestSessionMs)
        assertEquals(1, day.sessionBucketCounts[3])
        assertEquals(7 * minute, day.screenOnMs)
        assertEquals(7 * minute, day.screenOnHourlyMs[10])
    }

    // 7
    @Test
    fun noKeyguardEventsFallBackToScreenSpans() {
        val day = reduce(
            d1,
            listOf(
                ev(SCREEN_INTERACTIVE, 1, 8, 0),
                ev(SCREEN_NON_INTERACTIVE, 1, 8, 1),
                ev(SCREEN_INTERACTIVE, 1, 12, 0),
                ev(SCREEN_NON_INTERACTIVE, 1, 12, 10),
            ),
        )
        assertFalse(day.unlockSignal)
        assertEquals(0, day.unlockCount)
        assertEquals(SessionBasis.SCREEN, day.sessionBasis)
        assertEquals(2, day.sessionCount)
        assertEquals(1, day.sessionBucketCounts[2]) // exactly 60 s
        assertEquals(1, day.sessionBucketCounts[3]) // 10 min
        assertEquals(2, day.wakeCount)
    }

    // 8
    @Test
    fun keyguardShownEndsTheSessionButNotTheScreenSpan() {
        val day = reduce(
            d1,
            listOf(
                ev(SCREEN_INTERACTIVE, 1, 8, 0, 0),
                ev(KEYGUARD_HIDDEN, 1, 8, 0, 0),
                ev(KEYGUARD_SHOWN, 1, 8, 0, 40),
                ev(SCREEN_NON_INTERACTIVE, 1, 8, 1, 40),
            ),
        )
        assertEquals(1, day.sessionCount)
        assertEquals(1, day.sessionBucketCounts[1]) // 40 s
        assertEquals(40_000L, day.longestSessionMs)
        assertEquals(100_000L, day.screenOnMs)
    }

    // 9
    @Test
    fun wakeWithoutUnlockCountsAWakeOnly() {
        val day = reduce(
            d1,
            listOf(
                ev(SCREEN_INTERACTIVE, 1, 8, 0, 0),
                ev(KEYGUARD_HIDDEN, 1, 8, 0, 2),
                ev(KEYGUARD_SHOWN, 1, 8, 1, 0),
                ev(SCREEN_NON_INTERACTIVE, 1, 8, 1, 5),
                ev(SCREEN_INTERACTIVE, 1, 8, 10, 0),
            ),
        )
        assertEquals(2, day.wakeCount)
        assertEquals(1, day.unlockCount)
    }

    // 10
    @Test
    fun gapsLongerThanThreeHoursAreIgnored() {
        fun unlockAt(h: Int) = listOf(
            ev(SCREEN_INTERACTIVE, 1, h, 0, 0),
            ev(KEYGUARD_HIDDEN, 1, h, 0, 1),
            ev(SCREEN_NON_INTERACTIVE, 1, h, 5, 0),
        )
        val day = reduce(d1, unlockAt(8) + unlockAt(9) + unlockAt(14))
        assertEquals(3, day.unlockCount)
        assertEquals(3_600_000L, day.unlockGapSumMs)
        assertEquals(1, day.unlockGapCount)
    }

    // 11
    @Test
    fun earliestStartingForegroundSessionIsTheFirstApp() {
        val u = t(1, 8)
        val day = reduce(
            d1,
            listOf(
                ev(SCREEN_INTERACTIVE, 1, 8),
                ev(KEYGUARD_HIDDEN, 1, 8),
                ev(SCREEN_NON_INTERACTIVE, 1, 8, 5),
            ),
            sessions = listOf(
                fs("pkg.b", u + 8_000L, u + 60_000L),
                fs("pkg.a", u + 3_000L, u + 60_000L),
            ),
        )
        assertEquals(mapOf("pkg.a" to 1), day.firstApps)
        assertEquals(0, day.unlocksWithoutApp)
    }

    // 12
    @Test
    fun sessionStartingTooLateMeansNoApp() {
        val u = t(1, 8)
        val day = reduce(
            d1,
            listOf(ev(SCREEN_INTERACTIVE, 1, 8), ev(KEYGUARD_HIDDEN, 1, 8), ev(SCREEN_NON_INTERACTIVE, 1, 8, 5)),
            sessions = listOf(fs("pkg.a", u + 20_000L, u + 90_000L)),
        )
        assertEquals(1, day.unlocksWithoutApp)
        assertTrue(day.firstApps.isEmpty())
    }

    // 13
    @Test
    fun sessionAlreadyRunningAtUnlockCounts() {
        val u = t(1, 8)
        val day = reduce(
            d1,
            listOf(ev(SCREEN_INTERACTIVE, 1, 8), ev(KEYGUARD_HIDDEN, 1, 8), ev(SCREEN_NON_INTERACTIVE, 1, 8, 5)),
            sessions = listOf(fs("pkg.a", u - 1_000L, u + 60_000L)),
        )
        assertEquals(mapOf("pkg.a" to 1), day.firstApps)
    }

    @Test
    fun sessionThatEndedBeforeTheUnlockWindowIsNotTheFirstApp() {
        val u = t(1, 8)
        val day = reduce(
            d1,
            listOf(ev(SCREEN_INTERACTIVE, 1, 8), ev(KEYGUARD_HIDDEN, 1, 8), ev(SCREEN_NON_INTERACTIVE, 1, 8, 5)),
            sessions = listOf(fs("pkg.a", u - 60_000L, u - 3_000L)),
        )
        assertEquals(1, day.unlocksWithoutApp)
        assertTrue(day.firstApps.isEmpty())
    }

    // 14
    @Test
    fun shutdownClosesTheScreenSpanAndTheUnlockedSession() {
        val day = reduce(
            d1,
            listOf(
                ev(SCREEN_INTERACTIVE, 1, 8, 0, 0),
                ev(KEYGUARD_HIDDEN, 1, 8, 0, 1),
                ev(DEVICE_SHUTDOWN, 1, 8, 10, 0),
                // After the shutdown nothing is on any more.
                ev(DEVICE_STARTUP, 1, 8, 12, 0),
            ),
        )
        assertEquals(10 * minute, day.screenOnMs)
        assertEquals(1, day.sessionCount)
        assertEquals(599_000L, day.longestSessionMs)
    }

    // 15
    @Test
    fun hiddenKeyguardWhileScreenWasOffStartsAScreenSpan() {
        val day = reduce(
            d1,
            listOf(ev(KEYGUARD_HIDDEN, 1, 9, 0, 0), ev(SCREEN_NON_INTERACTIVE, 1, 9, 2, 0)),
        )
        assertEquals(2 * minute, day.screenOnMs)
        assertEquals(2 * minute, day.screenOnHourlyMs[9])
        assertEquals(0, day.wakeCount)
        assertEquals(1, day.unlockCount)
    }

    // 16
    @Test
    fun springForwardDayKeepsHourlyConsistent() {
        val ny = ZoneId.of("America/New_York")
        val date = LocalDate.of(2026, 3, 8)
        fun local(h: Int) = ZonedDateTime.of(2026, 3, 8, h, 0, 0, 0, ny).toInstant().toEpochMilli()
        val day = PhoneEventReducer(ny).reduceDay(
            date,
            listOf(
                ForegroundUsageEvent(SCREEN_INTERACTIVE, local(0)),
                ForegroundUsageEvent(SCREEN_NON_INTERACTIVE, local(6)),
            ),
            emptyList(),
            ZonedDateTime.of(2026, 3, 9, 12, 0, 0, 0, ny).toInstant().toEpochMilli(),
            PhoneDayStatus.COMPLETE,
        )
        assertEquals(5 * hour, day.screenOnMs)
        assertEquals(day.screenOnMs, day.screenOnHourlyMs.sum())
        assertEquals(0L, day.screenOnHourlyMs[2])
        assertEquals(1, day.wakeCount)
    }

    // 17
    @Test
    fun unsortedInputGivesTheSameResultAsSorted() {
        val sorted = listOf(
            ev(SCREEN_INTERACTIVE, 1, 8, 0, 0),
            ev(KEYGUARD_HIDDEN, 1, 8, 0, 3),
            ev(SCREEN_NON_INTERACTIVE, 1, 8, 5, 3),
            ev(SCREEN_INTERACTIVE, 1, 12, 0, 0),
            ev(KEYGUARD_HIDDEN, 1, 12, 0, 2),
            ev(SCREEN_NON_INTERACTIVE, 1, 12, 3, 0),
        )
        val a = reduce(d1, sorted)
        val b = reduce(d1, sorted.reversed())
        val c = reduce(d1, listOf(sorted[3], sorted[0], sorted[5], sorted[1], sorted[4], sorted[2]))
        assertEquals(a, b)
        assertEquals(a, c)
        assertEquals(2, a.unlockCount)
    }

    // 18
    @Test
    fun eventsAfterNowAreIgnored() {
        val day = reduce(
            d1,
            listOf(
                ev(SCREEN_INTERACTIVE, 1, 8, 0, 0),
                ev(KEYGUARD_HIDDEN, 1, 8, 0, 0),
                ev(SCREEN_NON_INTERACTIVE, 1, 8, 5, 0),
                ev(SCREEN_INTERACTIVE, 1, 9, 0, 0),
                ev(KEYGUARD_HIDDEN, 1, 9, 0, 0),
            ),
            now = t(1, 8, 3),
            status = PhoneDayStatus.LIVE,
        )
        assertEquals(1, day.wakeCount)
        assertEquals(1, day.unlockCount)
        assertEquals(3 * minute, day.screenOnMs)
        assertEquals(1, day.sessionCount)
        assertEquals(3 * minute, day.longestSessionMs)
    }

    // 19
    @Test
    fun emptyEventsGiveAnAllZeroDay() {
        assertEquals(PhoneDay.empty("2026-05-01", PhoneDayStatus.COMPLETE), reduce(d1, emptyList()))
    }

    // 20
    @Test
    fun withoutUnlocksTheFirstScreenOnTimeIsTheFirstPickup() {
        val day = reduce(
            d1,
            listOf(
                ev(SCREEN_INTERACTIVE, 1, 12, 0),
                ev(SCREEN_NON_INTERACTIVE, 1, 12, 1),
                ev(SCREEN_INTERACTIVE, 1, 7, 30),
                ev(SCREEN_NON_INTERACTIVE, 1, 7, 31),
            ),
        )
        assertEquals(t(1, 7, 30), day.firstUnlockAtMs)
    }

    @Test
    fun noActivityMeansNoFirstPickup() {
        assertNull(reduce(d1, emptyList()).firstUnlockAtMs)
    }

    // 21
    @Test
    fun listsAlwaysHaveTheFixedSizes() {
        val days = listOf(
            reduce(d1, emptyList()),
            reduce(d1, listOf(ev(SCREEN_INTERACTIVE, 1, 8), ev(KEYGUARD_HIDDEN, 1, 8), ev(SCREEN_NON_INTERACTIVE, 1, 9))),
            reduce(d2, listOf(ev(SCREEN_INTERACTIVE, 1, 23, 50), ev(SCREEN_NON_INTERACTIVE, 2, 0, 20))),
            reduce(d1, emptyList(), now = t(1, 0)), // limit == dayStart
        )
        for (day in days) {
            assertEquals(24, day.screenOnHourlyMs.size)
            assertEquals(24, day.wakeHourly.size)
            assertEquals(24, day.unlockHourly.size)
            assertEquals(PhoneBuckets.COUNT, day.sessionBucketCounts.size)
            assertEquals(PhoneBuckets.COUNT, day.sessionBucketMs.size)
        }
    }

    // --- extra boundary cases (not in the plan's list, all follow §4) ---

    @Test
    fun eventExactlyAtMidnightBelongsToTheNewDay() {
        val events = listOf(ev(SCREEN_INTERACTIVE, 2, 0, 0, 0), ev(SCREEN_NON_INTERACTIVE, 2, 0, 10, 0))
        val now = t(3, 0)
        val first = reduce(d1, events, now = now)
        val second = reduce(d2, events, now = now)
        assertEquals(0, first.wakeCount)
        assertEquals(0L, first.screenOnMs)
        assertEquals(1, second.wakeCount)
        assertEquals(10 * minute, second.screenOnMs)
        assertEquals(1, second.wakeHourly[0])
    }

    @Test
    fun nowBeforeTheDayStartsGivesAnEmptyDay() {
        val day = reduce(d2, listOf(ev(SCREEN_INTERACTIVE, 1, 8)), now = t(1, 12), status = PhoneDayStatus.LIVE)
        assertEquals(PhoneDay.empty("2026-05-02", PhoneDayStatus.LIVE), day)
    }

    @Test
    fun openSessionIsCappedAtSixHoursForPastDaysWithNoLaterEvents() {
        val day = reduce(
            d1,
            listOf(ev(SCREEN_INTERACTIVE, 1, 20, 0), ev(KEYGUARD_HIDDEN, 1, 20, 0)),
            now = t(4, 0),
        )
        // Day 1 only sees until midnight on the clock, but the session keeps its full (capped) length.
        assertEquals(4 * hour, day.screenOnMs)
        assertEquals(1, day.sessionCount)
        assertEquals(6 * hour, day.longestSessionMs)
        assertEquals(1, day.sessionBucketCounts[4])
    }

    @Test
    fun unlockWhileScreenAlreadyOnFromEarlierDayIsStillCounted() {
        val events = listOf(
            ev(SCREEN_INTERACTIVE, 1, 23, 50),
            ev(KEYGUARD_SHOWN, 2, 0, 5), // locked again, screen stays on
            ev(KEYGUARD_HIDDEN, 2, 0, 6),
            ev(SCREEN_NON_INTERACTIVE, 2, 0, 16),
        )
        val day = reduce(d2, events, now = t(3, 0))
        assertEquals(0, day.wakeCount)
        assertEquals(1, day.unlockCount)
        assertEquals(16 * minute, day.screenOnMs)
        assertEquals(10 * minute, day.longestSessionMs)
    }

    @Test
    fun unlockCarriedInFromYesterdayIsNotASessionOfToday() {
        val events = listOf(
            ev(SCREEN_INTERACTIVE, 1, 23, 50),
            ev(KEYGUARD_HIDDEN, 1, 23, 50),
            ev(SCREEN_NON_INTERACTIVE, 2, 0, 20), // ends the carried-over session
            ev(SCREEN_INTERACTIVE, 2, 8, 0),
            ev(KEYGUARD_HIDDEN, 2, 8, 0, 1),
            ev(SCREEN_NON_INTERACTIVE, 2, 8, 5),
        )
        val day = reduce(d2, events, now = t(3, 0))
        assertEquals(1, day.unlockCount)
        assertEquals(SessionBasis.UNLOCK, day.sessionBasis)
        assertEquals(1, day.sessionCount) // only the 08:00 session, not the 23:50 one
        assertEquals(299_000L, day.longestSessionMs)
        assertEquals(25 * minute, day.screenOnMs) // 20 min carried over + 5 min
    }

    @Test
    fun firstAppWindowBoundaries() {
        val u = t(1, 8)
        val unlockEvents = listOf(
            ev(SCREEN_INTERACTIVE, 1, 8), ev(KEYGUARD_HIDDEN, 1, 8), ev(SCREEN_NON_INTERACTIVE, 1, 8, 5),
        )
        fun firstApp(session: ForegroundSession): Map<String, Int> =
            reduce(d1, unlockEvents, sessions = listOf(session)).firstApps

        // Looks back 2 s: a session that ended 1 s before the unlock still counts, one that ended exactly 2 s before does not.
        assertEquals(mapOf("pkg.a" to 1), firstApp(fs("pkg.a", u - 30_000L, u - 1_000L)))
        assertTrue(firstApp(fs("pkg.a", u - 30_000L, u - 2_000L)).isEmpty())
        // Looks ahead 15 s: starting exactly 15 s after counts, 15.001 s after does not.
        assertEquals(mapOf("pkg.a" to 1), firstApp(fs("pkg.a", u + 15_000L, u + 60_000L)))
        assertTrue(firstApp(fs("pkg.a", u + 15_001L, u + 60_000L)).isEmpty())
    }

    @Test
    fun hourlyArraysAddUpToTotalsOnABusyDay() {
        val events = mutableListOf<ForegroundUsageEvent>()
        for (h in 6..21) {
            events += ev(SCREEN_INTERACTIVE, 1, h, 10)
            events += ev(KEYGUARD_HIDDEN, 1, h, 10, 2)
            events += ev(SCREEN_NON_INTERACTIVE, 1, h, 25)
        }
        val day = reduce(d1, events)
        assertEquals(16, day.wakeCount)
        assertEquals(16, day.unlockCount)
        assertEquals(day.wakeCount, day.wakeHourly.sum())
        assertEquals(day.unlockCount, day.unlockHourly.sum())
        assertEquals(day.screenOnMs, day.screenOnHourlyMs.sum())
        assertEquals(day.sessionCount, day.sessionBucketCounts.sum())
        // Each session runs from h:10:02 (unlock) to h:25:00 (screen off) = 898 s, all in bucket 3 (5–20 min).
        assertEquals(16 * 898_000L, day.sessionBucketMs[3])
        assertEquals(16, day.sessionBucketCounts[3])
        assertEquals(16 * 15 * minute, day.screenOnMs)
    }
}
