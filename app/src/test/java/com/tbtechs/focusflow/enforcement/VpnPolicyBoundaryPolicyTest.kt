package com.tbtechs.focusflow.enforcement

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnPolicyBoundaryPolicyTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun weekdayScheduleIsActiveInsideItsHalfOpenWindowOnly() {
        val weekdays = listOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
        )

        assertTrue(isActive(weekdays, 9 * 60, 18 * 60, at(Calendar.MONDAY, 9, 0)))
        assertTrue(isActive(weekdays, 9 * 60, 18 * 60, at(Calendar.MONDAY, 17, 59)))
        assertFalse(isActive(weekdays, 9 * 60, 18 * 60, at(Calendar.MONDAY, 18, 0)))
        assertFalse(isActive(weekdays, 9 * 60, 18 * 60, at(Calendar.SATURDAY, 10, 0)))
    }

    @Test
    fun overnightWindowsUseTheirStartDayAcrossSaturdayAndSunday() {
        val saturdayWindow = listOf(Calendar.SATURDAY)
        assertTrue(
            isActive(
                saturdayWindow,
                22 * 60,
                6 * 60,
                at(Calendar.SUNDAY, 1, 0),
            ),
        )
        assertFalse(
            isActive(
                saturdayWindow,
                22 * 60,
                6 * 60,
                at(Calendar.SUNDAY, 6, 0),
            ),
        )

        val sundayWindow = listOf(Calendar.SUNDAY)
        assertTrue(
            isActive(
                sundayWindow,
                22 * 60,
                6 * 60,
                at(Calendar.MONDAY, 5, 59),
            ),
        )
        assertFalse(
            isActive(
                sundayWindow,
                22 * 60,
                6 * 60,
                at(Calendar.MONDAY, 6, 0),
            ),
        )
    }

    @Test
    fun scheduleTargetsIncludeOnlyEnabledVpnWindowsThatAreActiveNow() {
        val windows = listOf(
            VpnScheduleWindow(
                packages = listOf("com.example.z", "com.example.a"),
                daysOfWeek = listOf(Calendar.MONDAY),
                startMinuteOfDay = 9 * 60,
                endMinuteOfDay = 18 * 60,
                vpnEnabled = true,
            ),
            VpnScheduleWindow(
                packages = listOf("com.example.disabled"),
                daysOfWeek = listOf(Calendar.MONDAY),
                startMinuteOfDay = 9 * 60,
                endMinuteOfDay = 18 * 60,
                enabled = false,
                vpnEnabled = true,
            ),
            VpnScheduleWindow(
                packages = listOf("com.example.vpn-off"),
                daysOfWeek = listOf(Calendar.MONDAY),
                startMinuteOfDay = 9 * 60,
                endMinuteOfDay = 18 * 60,
                vpnEnabled = false,
            ),
            VpnScheduleWindow(
                packages = listOf("com.example.outside"),
                daysOfWeek = listOf(Calendar.TUESDAY),
                startMinuteOfDay = 9 * 60,
                endMinuteOfDay = 18 * 60,
                vpnEnabled = true,
            ),
        )

        assertEquals(
            listOf("com.example.a", "com.example.z"),
            VpnPolicyBoundaryPolicy.scheduleTargets(
                windows = windows,
                nowMs = at(Calendar.MONDAY, 10, 0),
                timeZone = utc,
            ),
        )
    }

    @Test
    fun nextScheduleBoundaryFindsStartAndEndAndWrapsToNextWeek() {
        val window = VpnScheduleWindow(
            packages = listOf("com.example.app"),
            daysOfWeek = listOf(
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.WEDNESDAY,
                Calendar.THURSDAY,
                Calendar.FRIDAY,
            ),
            startMinuteOfDay = 9 * 60,
            endMinuteOfDay = 18 * 60,
            vpnEnabled = true,
        )

        assertEquals(
            at(Calendar.MONDAY, 9, 0),
            GreyoutWindowMath.nextBoundaryAfter(
                window.daysOfWeek,
                window.startMinuteOfDay,
                window.endMinuteOfDay,
                afterMs = at(Calendar.MONDAY, 8, 30),
                timeZone = utc,
            ),
        )
        assertEquals(
            at(Calendar.MONDAY, 18, 0),
            GreyoutWindowMath.nextBoundaryAfter(
                window.daysOfWeek,
                window.startMinuteOfDay,
                window.endMinuteOfDay,
                afterMs = at(Calendar.MONDAY, 10, 0),
                timeZone = utc,
            ),
        )
        assertEquals(
            at(Calendar.MONDAY, 9, 0, weekOffset = 1),
            GreyoutWindowMath.nextBoundaryAfter(
                window.daysOfWeek,
                window.startMinuteOfDay,
                window.endMinuteOfDay,
                afterMs = at(Calendar.FRIDAY, 19, 0),
                timeZone = utc,
            ),
        )
    }

    @Test
    fun legacy2400EndRemainsActiveUntilMidnightAndSchedulesMidnightBoundary() {
        val monday = listOf(Calendar.MONDAY)

        assertTrue(isActive(monday, 9 * 60, 24 * 60, at(Calendar.MONDAY, 23, 59)))
        assertFalse(isActive(monday, 9 * 60, 24 * 60, at(Calendar.TUESDAY, 0, 0)))
        assertEquals(
            at(Calendar.TUESDAY, 0, 0),
            GreyoutWindowMath.nextBoundaryAfter(
                daysOfWeek = monday,
                startMinuteOfDay = 9 * 60,
                endMinuteOfDay = 24 * 60,
                afterMs = at(Calendar.MONDAY, 23, 30),
                timeZone = utc,
            ),
        )
    }

    @Test
    fun standaloneVpnTargetsExpireAtTheScheduledBoundaryWithoutFurtherInput() {
        val expiry = at(Calendar.MONDAY, 9, 0)

        assertEquals(
            listOf("com.example.app"),
            VpnPolicyBoundaryPolicy.standaloneTargets(
                packages = listOf("com.example.app"),
                active = true,
                untilMs = expiry,
                nowMs = expiry - 1,
            ),
        )
        assertEquals(
            expiry,
            VpnPolicyBoundaryPolicy.nextBoundaryMs(
                activeStandalone = true,
                standaloneUntilMs = expiry,
                windows = emptyList(),
                nowMs = expiry - 1,
                timeZone = utc,
            ),
        )
        assertEquals(
            emptyList<String>(),
            VpnPolicyBoundaryPolicy.standaloneTargets(
                packages = listOf("com.example.app"),
                active = true,
                untilMs = expiry,
                nowMs = expiry,
            ),
        )
        assertNull(
            VpnPolicyBoundaryPolicy.nextBoundaryMs(
                activeStandalone = true,
                standaloneUntilMs = expiry,
                windows = emptyList(),
                nowMs = expiry,
                timeZone = utc,
            ),
        )
    }

    @Test
    fun nextBoundaryPolicyChoosesTheEarlierStandaloneOrScheduleTransition() {
        val schedule = VpnScheduleWindow(
            packages = listOf("com.example.app"),
            daysOfWeek = listOf(Calendar.MONDAY),
            startMinuteOfDay = 9 * 60,
            endMinuteOfDay = 18 * 60,
            vpnEnabled = true,
        )

        assertEquals(
            at(Calendar.MONDAY, 8, 45),
            VpnPolicyBoundaryPolicy.nextBoundaryMs(
                activeStandalone = true,
                standaloneUntilMs = at(Calendar.MONDAY, 8, 45),
                windows = listOf(schedule),
                nowMs = at(Calendar.MONDAY, 8, 0),
                timeZone = utc,
            ),
        )
        assertEquals(
            at(Calendar.MONDAY, 9, 0),
            VpnPolicyBoundaryPolicy.nextBoundaryMs(
                activeStandalone = false,
                standaloneUntilMs = 0L,
                windows = listOf(schedule),
                nowMs = at(Calendar.MONDAY, 8, 0),
                timeZone = utc,
            ),
        )
    }

    private fun isActive(
        days: List<Int>,
        startMinute: Int,
        endMinute: Int,
        atMs: Long,
    ): Boolean =
        GreyoutWindowMath.isActive(
            daysOfWeek = days,
            startMinuteOfDay = startMinute,
            endMinuteOfDay = endMinute,
            atMs = atMs,
            timeZone = utc,
        )

    private fun at(
        dayOfWeek: Int,
        hour: Int,
        minute: Int,
        weekOffset: Int = 0,
    ): Long {
        val calendar = Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.OCTOBER, 5 + weekOffset * 7, 0, 0, 0)
            while (get(Calendar.DAY_OF_WEEK) != dayOfWeek) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        return calendar.timeInMillis
    }
}
