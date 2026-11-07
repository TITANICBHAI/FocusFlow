package com.tbtechs.focusflow.analytics.phone

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PhoneTextTest {
    @Test
    fun durationBoundaries() {
        assertEquals("0s", PhoneText.duration(0L))
        assertEquals("0s", PhoneText.duration(999L))
        assertEquals("59s", PhoneText.duration(59_999L))
        assertEquals("1m", PhoneText.duration(60_000L))
        assertEquals("1h", PhoneText.duration(3_599_999L))
        assertEquals("1h", PhoneText.duration(3_600_000L))
        assertEquals("1h 12m", PhoneText.duration(4_320_000L))
        assertEquals("4h 12m", PhoneText.duration(15_120_000L))
    }

    @Test
    fun durationRoundsToNearestMinuteBelowAnHour() {
        assertEquals("37m", PhoneText.duration(37L * 60_000L + 29_999L))
        assertEquals("38m", PhoneText.duration(37L * 60_000L + 30_000L))
    }

    @Test
    fun durationNegativeIsZero() {
        assertEquals("0s", PhoneText.duration(-5_000L))
    }

    @Test
    fun signedPercent() {
        assertEquals("+35%", PhoneText.signedPercent(35))
        assertEquals("−12%", PhoneText.signedPercent(-12))
        assertEquals("0%", PhoneText.signedPercent(0))
    }

    @Test
    fun percent() {
        assertEquals("35%", PhoneText.percent(35))
    }

    @Test
    fun hourLabels() {
        assertEquals("12 AM", PhoneText.hour(0))
        assertEquals("9 AM", PhoneText.hour(9))
        assertEquals("12 PM", PhoneText.hour(12))
        assertEquals("9 PM", PhoneText.hour(21))
        assertEquals("11 PM", PhoneText.hour(23))
    }

    @Test
    fun clockLabels() {
        assertEquals("8:15 AM", PhoneText.clock(495))
        assertEquals("12:00 AM", PhoneText.clock(0))
        assertEquals("12:30 PM", PhoneText.clock(12 * 60 + 30))
        assertEquals("11:59 PM", PhoneText.clock(23 * 60 + 59))
    }

    @Test
    fun countPluralises() {
        assertEquals("1 unlock", PhoneText.count(1, "unlock"))
        assertEquals("63 unlocks", PhoneText.count(63, "unlock"))
        assertEquals("0 unlocks", PhoneText.count(0, "unlock"))
        assertEquals("2 days", PhoneText.count(2, "day", "days"))
        assertEquals("3 wakes", PhoneText.count(3, "wake-up", "wakes"))
    }

    @Test
    fun weekdayDateIsEnglishShortForm() {
        assertEquals("Sat, Oct 18", PhoneText.weekdayDate(LocalDate.of(2025, 10, 18)))
        assertEquals("Fri, Nov 6", PhoneText.weekdayDate(LocalDate.of(2026, 11, 6)))
    }
}
