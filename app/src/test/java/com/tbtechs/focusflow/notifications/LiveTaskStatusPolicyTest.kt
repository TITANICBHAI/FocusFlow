package com.tbtechs.focusflow.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTaskStatusPolicyTest {
    @Test
    fun suppressesLiveTaskCardWhileFocusSessionIsActive() {
        assertTrue(
            shouldSuppressLiveTaskStatus(
                focusActive = true,
                breakUntilMs = 0L,
                nowMs = 10_000L,
            ),
        )
    }

    @Test
    fun suppressesLiveTaskCardWhileFocusBreakIsActive() {
        assertTrue(
            shouldSuppressLiveTaskStatus(
                focusActive = false,
                breakUntilMs = 10_001L,
                nowMs = 10_000L,
            ),
        )
    }

    @Test
    fun allowsLiveTaskCardWhenNoFocusSessionOrBreakIsActive() {
        assertFalse(
            shouldSuppressLiveTaskStatus(
                focusActive = false,
                breakUntilMs = 10_000L,
                nowMs = 10_000L,
            ),
        )
        assertFalse(
            shouldSuppressLiveTaskStatus(
                focusActive = false,
                breakUntilMs = 0L,
                nowMs = 10_000L,
            ),
        )
    }
}
