package com.tbtechs.focusflow.analytics.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class HourSplitterTest {
    private val utc = ZoneId.of("UTC")
    private val minute = 60_000L
    private val hour = 60 * minute

    private fun ms(z: ZoneId, y: Int, mo: Int, d: Int, h: Int, mi: Int = 0): Long =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, z).toInstant().toEpochMilli()

    private fun check(slices: List<HourSlice>, startMs: Long, endMs: Long) {
        assertEquals(endMs - startMs, slices.sumOf { it.ms })
    }

    @Test
    fun singleHourSpanIsOneSlice() {
        val s = ms(utc, 2026, 5, 1, 10, 10)
        val e = ms(utc, 2026, 5, 1, 10, 40)
        val slices = HourSplitter.split(s, e, utc)
        assertEquals(listOf(HourSlice(LocalDate.of(2026, 5, 1), 10, 30 * minute)), slices)
        check(slices, s, e)
    }

    @Test
    fun spanCrossingOneHourBoundaryGivesTwoSlices() {
        val s = ms(utc, 2026, 5, 1, 10, 50)
        val e = ms(utc, 2026, 5, 1, 11, 20)
        val slices = HourSplitter.split(s, e, utc)
        assertEquals(
            listOf(
                HourSlice(LocalDate.of(2026, 5, 1), 10, 10 * minute),
                HourSlice(LocalDate.of(2026, 5, 1), 11, 20 * minute),
            ),
            slices,
        )
        check(slices, s, e)
    }

    @Test
    fun multiHourSpanSplitsEveryHour() {
        val s = ms(utc, 2026, 5, 1, 8, 30)
        val e = ms(utc, 2026, 5, 1, 11, 15)
        val slices = HourSplitter.split(s, e, utc)
        assertEquals(listOf(8, 9, 10, 11), slices.map { it.hour })
        assertEquals(listOf(30 * minute, hour, hour, 15 * minute), slices.map { it.ms })
        check(slices, s, e)
    }

    @Test
    fun spanCrossingMidnightChangesDate() {
        val s = ms(utc, 2026, 5, 1, 23, 30)
        val e = ms(utc, 2026, 5, 2, 0, 30)
        val slices = HourSplitter.split(s, e, utc)
        assertEquals(
            listOf(
                HourSlice(LocalDate.of(2026, 5, 1), 23, 30 * minute),
                HourSlice(LocalDate.of(2026, 5, 2), 0, 30 * minute),
            ),
            slices,
        )
        check(slices, s, e)
    }

    @Test
    fun zeroAndNegativeLengthGiveNothing() {
        val t = ms(utc, 2026, 5, 1, 10, 0)
        assertTrue(HourSplitter.split(t, t, utc).isEmpty())
        assertTrue(HourSplitter.split(t, t - 1L, utc).isEmpty())
    }

    @Test
    fun halfHourZoneSplitsAtLocalBoundary() {
        val kolkata = ZoneId.of("Asia/Kolkata") // UTC+05:30
        val s = ms(kolkata, 2026, 5, 1, 10, 15)
        val e = ms(kolkata, 2026, 5, 1, 11, 15)
        val slices = HourSplitter.split(s, e, kolkata)
        assertEquals(
            listOf(
                HourSlice(LocalDate.of(2026, 5, 1), 10, 45 * minute),
                HourSlice(LocalDate.of(2026, 5, 1), 11, 15 * minute),
            ),
            slices,
        )
        check(slices, s, e)
    }

    @Test
    fun springForwardDayHasNoHourTwo() {
        val ny = ZoneId.of("America/New_York")
        val s = ms(ny, 2026, 3, 8, 0, 0)
        val e = ms(ny, 2026, 3, 8, 6, 0)
        assertEquals(5 * hour, e - s)
        val slices = HourSplitter.split(s, e, ny)
        check(slices, s, e)
        assertTrue("no slice may land in the skipped hour 2", slices.none { it.hour == 2 })
        assertEquals(listOf(0, 1, 3, 4, 5), slices.map { it.hour })
    }

    @Test
    fun fallBackDayCountsTheRepeatedHourTwice() {
        val ny = ZoneId.of("America/New_York")
        val s = ms(ny, 2026, 11, 1, 0, 0)
        val e = ms(ny, 2026, 11, 1, 6, 0)
        assertEquals(7 * hour, e - s)
        val slices = HourSplitter.split(s, e, ny)
        check(slices, s, e)
        assertEquals(2 * hour, slices.filter { it.hour == 1 }.sumOf { it.ms })
        assertEquals(listOf(0, 1, 1, 2, 3, 4, 5), slices.map { it.hour })
    }

    @Test
    fun wholeLocalDayOnDstDaysAddsUpTo23And25Hours() {
        val ny = ZoneId.of("America/New_York")
        fun dayMs(date: LocalDate): Long {
            val s = date.atStartOfDay(ny).toInstant().toEpochMilli()
            val e = date.plusDays(1).atStartOfDay(ny).toInstant().toEpochMilli()
            val slices = HourSplitter.split(s, e, ny)
            check(slices, s, e)
            assertTrue(slices.all { it.date == date })
            return slices.sumOf { it.ms }
        }
        assertEquals(23 * hour, dayMs(LocalDate.of(2026, 3, 8)))
        assertEquals(25 * hour, dayMs(LocalDate.of(2026, 11, 1)))
        assertEquals(24 * hour, dayMs(LocalDate.of(2026, 6, 1)))
    }
}
