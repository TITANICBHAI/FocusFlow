package com.tbtechs.focusflow.analytics.phone

import org.junit.Assert.assertEquals
import org.junit.Test

class PhonePeriodTest {
    @Test
    fun everyHourMapsPerSection415() {
        for (hour in 0..23) {
            val expected = when (hour) {
                in 5..11 -> PhonePeriod.MORNING
                in 12..16 -> PhonePeriod.AFTERNOON
                in 17..21 -> PhonePeriod.EVENING
                else -> PhonePeriod.LATE_NIGHT // 22, 23 and 0..4
            }
            assertEquals("hour $hour", expected, PhonePeriod.of(hour))
        }
    }

    @Test
    fun boundaryHours() {
        assertEquals(PhonePeriod.LATE_NIGHT, PhonePeriod.of(4))
        assertEquals(PhonePeriod.MORNING, PhonePeriod.of(5))
        assertEquals(PhonePeriod.MORNING, PhonePeriod.of(11))
        assertEquals(PhonePeriod.AFTERNOON, PhonePeriod.of(12))
        assertEquals(PhonePeriod.AFTERNOON, PhonePeriod.of(16))
        assertEquals(PhonePeriod.EVENING, PhonePeriod.of(17))
        assertEquals(PhonePeriod.EVENING, PhonePeriod.of(21))
        assertEquals(PhonePeriod.LATE_NIGHT, PhonePeriod.of(22))
        assertEquals(PhonePeriod.LATE_NIGHT, PhonePeriod.of(0))
    }

    @Test
    fun enumOrderIsMorningAfternoonEveningLateNight() {
        assertEquals(
            listOf(PhonePeriod.MORNING, PhonePeriod.AFTERNOON, PhonePeriod.EVENING, PhonePeriod.LATE_NIGHT),
            PhonePeriod.entries.toList(),
        )
    }

    @Test
    fun onlySocialEntertainmentAndGamesAreDistractions() {
        val distractions = UsageCategory.entries.filter { it.isDistraction }.toSet()
        assertEquals(setOf(UsageCategory.SOCIAL, UsageCategory.ENTERTAINMENT, UsageCategory.GAMES), distractions)
    }
}
