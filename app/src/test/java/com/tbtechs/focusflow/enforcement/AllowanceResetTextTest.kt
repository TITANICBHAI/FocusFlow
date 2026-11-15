package com.tbtechs.focusflow.enforcement

import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AllowanceResetTextTest {
    private val zone = TimeZone.getTimeZone("UTC")
    private val clock: (Long) -> String = { ms ->
        val t = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), ZoneId.of("UTC"))
        "%d:%02d".format(t.hour, t.minute)
    }

    private fun at(day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()

    @Test
    fun resetIsWindowStartPlusWindow() {
        assertEquals(1_000L + 3_600_000L, AllowanceResetText.resetAtMs(1_000L, 3_600_000L))
    }

    @Test
    fun unknownWindowGivesNoResetTime() {
        assertEquals(0L, AllowanceResetText.resetAtMs(0L, 3_600_000L))
        assertEquals(0L, AllowanceResetText.resetAtMs(1_000L, 0L))
        assertNull(AllowanceResetText.backText(0L, at(15, 12, 0), zone, Locale.US, clock))
    }

    @Test
    fun sameDayReadsBackAtTime() {
        assertEquals(
            "back at 15:40",
            AllowanceResetText.backText(at(15, 15, 40), at(15, 14, 40), zone, Locale.US, clock),
        )
    }

    @Test
    fun nextDayReadsTomorrow() {
        assertEquals(
            "back tomorrow 8:10",
            AllowanceResetText.backText(at(16, 8, 10), at(15, 23, 30), zone, Locale.US, clock),
        )
    }

    @Test
    fun laterDaysReadTheWeekday() {
        // 18 Nov 2026 is a Wednesday.
        assertEquals(
            "back Wed 8:10",
            AllowanceResetText.backText(at(18, 8, 10), at(15, 12, 0), zone, Locale.US, clock),
        )
    }

    @Test
    fun dayGapUsesCalendarDaysNotTwentyFourHours() {
        assertEquals(1, AllowanceResetText.dayGap(at(16, 0, 5), at(15, 23, 55), zone))
        assertEquals(0, AllowanceResetText.dayGap(at(15, 23, 59), at(15, 0, 1), zone))
    }
}
