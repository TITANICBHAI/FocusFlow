package com.tbtechs.focusflow.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskTimeDisplayTest {
    @Test
    fun showsSecondsForTheFinalMinute() {
        val display = taskTimeDisplay(endTimeMs = 149_000L, nowMs = 100_000L)

        assertFalse(display.hasEnded)
        assertEquals(49, display.secondsRemaining)
        assertNull(display.overdueMinutes)
    }

    @Test
    fun roundsUpPartialCountdownSeconds() {
        val display = taskTimeDisplay(endTimeMs = 100_001L, nowMs = 100_000L)

        assertEquals(1, display.secondsRemaining)
    }

    @Test
    fun labelsTheFirstMinuteAfterTheEndAsJustEnded() {
        val exactlyAtEnd = taskTimeDisplay(endTimeMs = 100_000L, nowMs = 100_000L)
        val fiftyNineSecondsLate = taskTimeDisplay(endTimeMs = 100_000L, nowMs = 159_999L)

        assertTrue(exactlyAtEnd.hasEnded)
        assertTrue(exactlyAtEnd.justEnded)
        assertNull(exactlyAtEnd.overdueMinutes)
        assertTrue(fiftyNineSecondsLate.justEnded)
    }

    @Test
    fun reportsWholeOverdueMinutesAfterTheGraceMinute() {
        val display = taskTimeDisplay(endTimeMs = 100_000L, nowMs = 160_000L)

        assertTrue(display.hasEnded)
        assertFalse(display.justEnded)
        assertEquals(1L, display.overdueMinutes)
    }
}
