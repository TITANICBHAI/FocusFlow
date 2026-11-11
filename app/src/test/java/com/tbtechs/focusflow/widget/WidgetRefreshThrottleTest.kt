package com.tbtechs.focusflow.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRefreshThrottleTest {
    private val never = Long.MIN_VALUE

    @Test
    fun firstRefreshIsAlwaysHandled() {
        assertTrue(FocusFlowWidget.shouldHandleRefresh(never, 0L))
        assertTrue(FocusFlowWidget.shouldHandleRefresh(never, 5_000L))
    }

    @Test
    fun burstsInsideTheMinimumIntervalAreIgnored() {
        assertFalse(FocusFlowWidget.shouldHandleRefresh(10_000L, 10_500L))
        assertFalse(FocusFlowWidget.shouldHandleRefresh(10_000L, 11_999L))
    }

    @Test
    fun refreshesAtOrAfterTheIntervalAreHandled() {
        assertTrue(FocusFlowWidget.shouldHandleRefresh(10_000L, 12_000L))
        // The app's own alarm tick is scheduled at least 15 s ahead, so it is never throttled.
        assertTrue(FocusFlowWidget.shouldHandleRefresh(10_000L, 25_000L))
    }
}
