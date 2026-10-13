package com.tbtechs.focusflow.analytics

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsProcessorRangeTest {
    private val now = ZonedDateTime.of(
        LocalDate.of(2026, 9, 29),
        LocalTime.of(15, 30),
        ZoneId.of("Asia/Kolkata"),
    )

    @Test
    fun dynamicWeekUsesRollingSevenDaysAndEndsAtTheObservedCurrentTime() {
        val range = getAnalyticsRange(
            window = ANALYTICS_WEEK,
            now = now,
            weekStartDay = 1,
            weekMode = WEEK_MODE_DYNAMIC,
        )

        assertEquals(LocalDate.of(2026, 9, 23), range.start.toLocalDate())
        assertEquals(now, range.end)
    }

    @Test
    fun fixedWeekKeepsTheCompleteConfiguredSevenDayWindow() {
        val range = getAnalyticsRange(
            window = ANALYTICS_WEEK,
            now = now,
            weekStartDay = 1,
            weekMode = WEEK_MODE_FIXED,
        )

        assertEquals(LocalDate.of(2026, 9, 28), range.start.toLocalDate())
        assertEquals(LocalDate.of(2026, 10, 4), range.end.toLocalDate())
        assertEquals(LocalTime.MAX, range.end.toLocalTime())
    }

    @Test
    fun fixedWeekUsesTheProfileConfiguredStartDay() {
        val range = getAnalyticsRange(
            window = ANALYTICS_WEEK,
            now = now,
            weekStartDay = 3,
            weekMode = WEEK_MODE_FIXED,
        )

        assertEquals(LocalDate.of(2026, 9, 23), range.start.toLocalDate())
        assertEquals(LocalDate.of(2026, 9, 29), range.end.toLocalDate())
    }
}