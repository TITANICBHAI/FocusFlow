package com.tbtechs.focusflow.enforcement

import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnPolicyBoundaryDstTest {
    private val newYork = TimeZone.getTimeZone("America/New_York")

    private fun utc(value: String): Long = Instant.parse(value).toEpochMilli()

    private fun window(days: List<Int>, start: Int, end: Int) =
        VpnScheduleWindow(
            packages = listOf("com.example.app"),
            daysOfWeek = days,
            startMinuteOfDay = start,
            endMinuteOfDay = end,
            enabled = true,
            vpnEnabled = true,
        )

    @Test
    fun springForwardGapBoundaryFiresAtTheClockJump() {
        val boundary = VpnPolicyBoundaryPolicy.nextBoundaryMs(
            activeStandalone = false,
            standaloneUntilMs = 0L,
            windows = listOf(window(listOf(Calendar.SUNDAY), 150, 9 * 60)),
            nowMs = utc("2026-03-08T05:00:00Z"),
            timeZone = newYork,
        )

        assertEquals(utc("2026-03-08T07:00:00Z"), boundary)
        assertTrue(
            GreyoutWindowMath.isActive(
                daysOfWeek = listOf(Calendar.SUNDAY),
                startMinuteOfDay = 150,
                endMinuteOfDay = 9 * 60,
                atMs = requireNotNull(boundary),
                timeZone = newYork,
            ),
        )
    }

    @Test
    fun repeatedEndIncludesBothOccurrences() {
        val saturdayWindow = window(listOf(Calendar.SATURDAY), 20 * 60, 90)

        assertEquals(
            utc("2026-11-01T05:30:00Z"),
            GreyoutWindowMath.nextBoundaryAfter(
                daysOfWeek = saturdayWindow.daysOfWeek,
                startMinuteOfDay = saturdayWindow.startMinuteOfDay,
                endMinuteOfDay = saturdayWindow.endMinuteOfDay,
                afterMs = utc("2026-11-01T01:00:00Z"),
                timeZone = newYork,
            ),
        )
        assertEquals(
            utc("2026-11-01T06:30:00Z"),
            GreyoutWindowMath.nextBoundaryAfter(
                daysOfWeek = saturdayWindow.daysOfWeek,
                startMinuteOfDay = saturdayWindow.startMinuteOfDay,
                endMinuteOfDay = saturdayWindow.endMinuteOfDay,
                afterMs = utc("2026-11-01T06:10:00Z"),
                timeZone = newYork,
            ),
        )
    }

    @Test
    fun repeatedStartIncludesItsSecondOccurrence() {
        val sundayWindow = window(listOf(Calendar.SUNDAY), 90, 150)

        assertEquals(
            utc("2026-11-01T06:30:00Z"),
            GreyoutWindowMath.nextBoundaryAfter(
                daysOfWeek = sundayWindow.daysOfWeek,
                startMinuteOfDay = sundayWindow.startMinuteOfDay,
                endMinuteOfDay = sundayWindow.endMinuteOfDay,
                afterMs = utc("2026-11-01T06:10:00Z"),
                timeZone = newYork,
            ),
        )
    }

    @Test
    fun fallBackTransitionIsScheduledForPolicyResynchronization() {
        val saturdayWindow = window(listOf(Calendar.SATURDAY), 20 * 60, 90)
        val boundary = VpnPolicyBoundaryPolicy.nextBoundaryMs(
            activeStandalone = false,
            standaloneUntilMs = 0L,
            windows = listOf(saturdayWindow),
            nowMs = utc("2026-11-01T05:45:00Z"),
            timeZone = newYork,
        )

        assertEquals(utc("2026-11-01T06:00:00Z"), boundary)
        assertTrue(
            GreyoutWindowMath.isActive(
                daysOfWeek = saturdayWindow.daysOfWeek,
                startMinuteOfDay = saturdayWindow.startMinuteOfDay,
                endMinuteOfDay = saturdayWindow.endMinuteOfDay,
                atMs = requireNotNull(boundary),
                timeZone = newYork,
            ),
        )
    }

    @Test
    fun newYorkSecondRepeatedEndIsTheNextBoundaryAfterItBeginsAgain() {
        val saturdayWindow = window(listOf(Calendar.SATURDAY), 20 * 60, 90)
        val boundary = VpnPolicyBoundaryPolicy.nextBoundaryMs(
            activeStandalone = false,
            standaloneUntilMs = 0L,
            windows = listOf(saturdayWindow),
            nowMs = utc("2026-11-01T06:10:00Z"),
            timeZone = newYork,
        )

        assertEquals(utc("2026-11-01T06:30:00Z"), boundary)
    }

    @Test
    fun lordHoweHalfHourTransitionIsIncludedAsANextBoundary() {
        val zoneId = ZoneId.of("Australia/Lord_Howe")
        val transition = requireNotNull(
            zoneId.rules.nextTransition(Instant.parse("2026-01-01T00:00:00Z")),
        )
        assertEquals(
            30 * 60,
            transition.offsetBefore.totalSeconds - transition.offsetAfter.totalSeconds,
        )

        val beforeTransition = transition.instant.minusSeconds(10 * 60)
        val localBefore = beforeTransition.atZone(zoneId)
        val calendarDay = localBefore.dayOfWeek.value % 7 + 1
        val boundary = VpnPolicyBoundaryPolicy.nextBoundaryMs(
            activeStandalone = false,
            standaloneUntilMs = 0L,
            windows = listOf(window(listOf(calendarDay), 0, 4 * 60)),
            nowMs = beforeTransition.toEpochMilli(),
            timeZone = TimeZone.getTimeZone(zoneId),
        )

        assertEquals(transition.instant.toEpochMilli(), boundary)
    }
}
