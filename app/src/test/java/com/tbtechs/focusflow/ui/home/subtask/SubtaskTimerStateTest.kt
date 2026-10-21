package com.tbtechs.focusflow.ui.home.subtask

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtaskTimerStateTest {
    @Test
    fun countdownUsesExplicitTicksAndFinishesAtZero() {
        var state = SubtaskTimerState().start(SubtaskTimerMode.REST, seconds = 2)
        assertEquals(2, state.remainingSeconds)
        assertEquals("Rest timer started.", state.announcement)

        val firstTick = state.tick()
        state = firstTick.state
        assertFalse(firstTick.finished)
        assertEquals(1, state.remainingSeconds)
        assertTrue(state.isRunning)

        val finalTick = state.tick()
        state = finalTick.state
        assertTrue(finalTick.finished)
        assertFalse(state.isRunning)
        assertEquals(0, state.remainingSeconds)
        assertEquals("Rest timer finished.", state.announcement)

        assertFalse(state.tick().finished)
    }

    @Test
    fun stoppingDurationTimerCancelsRemainingTicks() {
        val running = SubtaskTimerState().start(SubtaskTimerMode.DURATION, seconds = 30)

        val stopped = running.stop()

        assertFalse(stopped.isRunning)
        assertEquals(0, stopped.remainingSeconds)
        assertEquals("Duration timer stopped.", stopped.announcement)
        assertFalse(stopped.tick().finished)
    }
}
