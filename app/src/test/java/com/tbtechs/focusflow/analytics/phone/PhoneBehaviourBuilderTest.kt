package com.tbtechs.focusflow.analytics.phone

import com.tbtechs.focusflow.analytics.phone.PhoneTestFixtures.d
import com.tbtechs.focusflow.analytics.phone.PhoneTestFixtures.day
import com.tbtechs.focusflow.analytics.phone.PhoneTestFixtures.week
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PhoneBehaviourBuilderTest {
    private val utc = ZoneId.of("UTC")
    private val complete = PhoneDayStatus.COMPLETE

    private fun build(days: List<PhoneDay>, names: Map<String, String> = emptyMap(), zone: ZoneId = utc) =
        PhoneBehaviourBuilder.build(week(), days, names, zone)

    private fun at(date: java.time.LocalDate, h: Int, m: Int, zone: ZoneId = utc): Long =
        ZonedDateTime.of(date.year, date.monthValue, date.dayOfMonth, h, m, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun emptyInputIsZeroFilled() {
        val s = build(emptyList())
        assertEquals(0, s.daysWithData)
        assertFalse(s.unlockSignalAvailable)
        assertEquals(SessionBasis.SCREEN, s.sessionBasis)
        assertEquals(List(24) { 0 }, s.unlocksByHour)
        assertEquals(List(24) { 0 }, s.wakesByHour)
        assertEquals(List(5) { 0 }, s.sessionBucketCounts)
        assertEquals(List(5) { 0L }, s.sessionBucketMs)
        assertNull(s.unlocksPerDay)
        assertNull(s.wakesPerDay)
        assertNull(s.avgGapBetweenUnlocksMs)
        assertNull(s.quickCheckSharePercent)
        assertNull(s.firstUnlockMedianMinuteOfDay)
        assertTrue(s.firstApps.isEmpty())
        assertEquals(0L, s.longestSessionMs)
    }

    @Test
    fun totalsAndPerDayAverages() {
        val s = build(listOf(day(d(0), complete, unlocks = 40, wakes = 50), day(d(1), complete, unlocks = 60, wakes = 70)))
        assertEquals(2, s.daysWithData)
        assertEquals(100, s.unlocks)
        assertEquals(120, s.wakes)
        assertEquals(50.0, s.unlocksPerDay!!, 1e-9)
        assertEquals(60.0, s.wakesPerDay!!, 1e-9)
        assertTrue(s.unlockSignalAvailable)
        assertEquals(SessionBasis.UNLOCK, s.sessionBasis)
    }

    @Test
    fun noUnlockSignalGivesScreenBasisAndNoUnlocksPerDayButKeepsWakes() {
        val s = build(listOf(day(d(0), complete, unlocks = 0, wakes = 30), day(d(1), complete, unlocks = 0, wakes = 10)))
        assertFalse(s.unlockSignalAvailable)
        assertEquals(SessionBasis.SCREEN, s.sessionBasis)
        assertNull(s.unlocksPerDay)
        assertEquals(20.0, s.wakesPerDay!!, 1e-9)
    }

    @Test
    fun perDayAveragesUseCompleteDaysOnlyButTotalsUseAll() {
        val s = build(listOf(day(d(0), complete, unlocks = 10), day(d(1), PhoneDayStatus.PARTIAL, unlocks = 90)))
        assertEquals(100, s.unlocks)
        assertEquals(10.0, s.unlocksPerDay!!, 1e-9)
        assertEquals(2, s.daysWithData)
    }

    @Test
    fun liveDayIsTheBaselineWhenThereAreNoCompleteDays() {
        val s = build(listOf(day(d(5), PhoneDayStatus.LIVE, unlocks = 12, wakes = 6)))
        assertEquals(12.0, s.unlocksPerDay!!, 1e-9)
        assertEquals(6.0, s.wakesPerDay!!, 1e-9)
    }

    @Test
    fun onlyPartialDaysGiveNoPerDayAverages() {
        val s = build(listOf(day(d(1), PhoneDayStatus.PARTIAL, unlocks = 12, wakes = 6)))
        assertNull(s.unlocksPerDay)
        assertNull(s.wakesPerDay)
        assertEquals(12, s.unlocks)
    }

    @Test
    fun gapAverageIsSumOverCount() {
        val s = build(
            listOf(
                day(d(0), complete, unlocks = 1, gapSumMs = 600_000L, gapCount = 2),
                day(d(1), complete, unlocks = 1, gapSumMs = 300_000L, gapCount = 1),
            ),
        )
        assertEquals(300_000L, s.avgGapBetweenUnlocksMs)
        assertNull(build(listOf(day(d(0), complete, unlocks = 1, gapSumMs = 5L, gapCount = 0))).avgGapBetweenUnlocksMs)
    }

    @Test
    fun sessionBucketsSumAndQuickCheckShareRounds() {
        val a = day(d(0), complete, unlocks = 1, sessions = 10, bucketCounts = listOf(3, 2, 3, 1, 1), longestMs = 100L)
        val b = day(d(1), complete, unlocks = 1, sessions = 5, bucketCounts = listOf(1, 1, 1, 1, 1), longestMs = 900L)
        val s = build(listOf(a, b))
        assertEquals(15, s.sessions)
        assertEquals(listOf(4, 3, 4, 2, 2), s.sessionBucketCounts)
        assertEquals(7, s.quickChecks)
        assertEquals(47, s.quickCheckSharePercent) // 700 / 15 = 46.67
        assertEquals(900L, s.longestSessionMs)
    }

    @Test
    fun quickCheckShareRoundsHalfUp() {
        // 1 quick of 8 sessions = 12.5 → 13
        val s = build(listOf(day(d(0), complete, unlocks = 1, sessions = 8, bucketCounts = listOf(1, 0, 3, 2, 2))))
        assertEquals(13, s.quickCheckSharePercent)
    }

    @Test
    fun sessionBucketMsAreSummed() {
        val a = day(d(0), complete, unlocks = 1).copy(sessionBucketMs = listOf(1L, 2L, 3L, 4L, 5L))
        val b = day(d(1), complete, unlocks = 1).copy(sessionBucketMs = listOf(10L, 20L, 30L, 40L, 50L))
        assertEquals(listOf(11L, 22L, 33L, 44L, 55L), build(listOf(a, b)).sessionBucketMs)
    }

    @Test
    fun hourlyArraysAreSummedOverAllMeasuredDays() {
        val s = build(
            listOf(
                day(d(0), complete, unlocks = 4, wakes = 2, unlockHour = 7, wakeHour = 7),
                day(d(1), PhoneDayStatus.PARTIAL, unlocks = 6, wakes = 3, unlockHour = 7, wakeHour = 22),
            ),
        )
        assertEquals(10, s.unlocksByHour[7])
        assertEquals(2, s.wakesByHour[7])
        assertEquals(3, s.wakesByHour[22])
    }

    @Test
    fun firstAppsAreSummedSortedAndShared() {
        val a = day(d(0), complete, unlocks = 10, firstApps = mapOf("com.b" to 4, "com.a" to 2), unlocksWithoutApp = 1)
        val b = day(d(1), complete, unlocks = 10, firstApps = mapOf("com.b" to 2, "com.c" to 6), unlocksWithoutApp = 3)
        val s = build(listOf(a, b), mapOf("com.a" to "Alpha", "com.b" to "Beta"))
        assertEquals(listOf("com.b", "com.c", "com.a"), s.firstApps.map { it.packageName })
        val beta = s.firstApps[0]
        assertEquals("Beta", beta.appName)
        assertEquals(6, beta.unlocks)
        assertEquals(30.0, beta.shareOfUnlocksPercent, 1e-9) // 6 of 20
        assertEquals(3.0, beta.perDay, 1e-9) // 2 days with signal
        assertEquals("com.c", s.firstApps[1].appName) // no name → package
        assertEquals(4, s.unlocksWithoutApp)
    }

    @Test
    fun firstAppPerDayCountsOnlyDaysThatHaveAnUnlockSignal() {
        val withSignal = day(d(0), complete, unlocks = 8, firstApps = mapOf("p" to 4))
        val noSignal = day(d(1), complete, unlocks = 0)
        val s = build(listOf(withSignal, noSignal))
        assertEquals(4.0, s.firstApps.single().perDay, 1e-9) // 4 unlocks over the 1 day that had a signal, not 2 days
    }

    @Test
    fun firstAppTiesAreBrokenByName() {
        val a = day(d(0), complete, unlocks = 10, firstApps = mapOf("p1" to 3, "p2" to 3))
        val s = build(listOf(a), mapOf("p1" to "Zed", "p2" to "Abe"))
        assertEquals(listOf("Abe", "Zed"), s.firstApps.map { it.appName })
    }

    @Test
    fun firstAppsAreCappedAtEight() {
        val apps = (1..12).associate { "p$it" to it }
        val s = build(listOf(day(d(0), complete, unlocks = 100, firstApps = apps)))
        assertEquals(8, s.firstApps.size)
        assertEquals("p12", s.firstApps.first().packageName)
        assertEquals("p5", s.firstApps.last().packageName)
    }

    @Test
    fun firstAppSharesAreZeroWithoutUnlocks() {
        val s = build(listOf(day(d(0), complete, unlocks = 0, firstApps = mapOf("p" to 2))))
        assertEquals(0.0, s.firstApps.single().shareOfUnlocksPercent, 1e-9)
    }

    @Test
    fun medianFirstUnlockOddCount() {
        val times = listOf(8 * 60 + 30, 7 * 60, 9 * 60, 6 * 60 + 15, 10 * 60) // sorted: 375, 420, 510, 540, 600
        val days = times.mapIndexed { i, min -> day(d(i), complete, unlocks = 1, firstUnlockAtMs = at(d(i), min / 60, min % 60)) }
        assertEquals(510, build(days).firstUnlockMedianMinuteOfDay)
    }

    @Test
    fun medianFirstUnlockEvenCountRoundsAverageOfMiddleTwo() {
        val times = listOf(360, 420, 481, 540, 600, 660) // middle: 481 + 540 → 510.5 → 511
        val days = times.mapIndexed { i, min -> day(d(i), complete, unlocks = 1, firstUnlockAtMs = at(d(i), min / 60, min % 60)) }
        assertEquals(511, build(days).firstUnlockMedianMinuteOfDay)
    }

    @Test
    fun medianNeedsFiveSamples() {
        val days = (0..3).map { day(d(it), complete, unlocks = 1, firstUnlockAtMs = at(d(it), 7, 0)) }
        assertNull(build(days).firstUnlockMedianMinuteOfDay)
    }

    @Test
    fun medianUsesCompleteDaysOnlyAndTheGivenZone() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        val days = (0..4).map { day(d(it), complete, unlocks = 1, firstUnlockAtMs = at(d(it), 7, 0, kolkata)) } +
            day(d(5), PhoneDayStatus.PARTIAL, unlocks = 1, firstUnlockAtMs = at(d(5), 1, 0, kolkata))
        assertEquals(7 * 60, build(days, zone = kolkata).firstUnlockMedianMinuteOfDay)
        assertEquals(1 * 60 + 30, build(days, zone = utc).firstUnlockMedianMinuteOfDay) // 07:00 IST = 01:30 UTC
    }

    @Test
    fun daysOutsideTheRangeAreIgnored() {
        val s = build(listOf(day(d(0).minusDays(3), complete, unlocks = 99), day(d(1), complete, unlocks = 1)))
        assertEquals(1, s.unlocks)
        assertEquals(1, s.daysWithData)
    }
}
