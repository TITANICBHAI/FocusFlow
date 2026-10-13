package com.tbtechs.focusflow.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageEventReadBoundaryTest {
    @Test
    fun nullPlatformReadIsUnknownRatherThanEmptyUsage() {
        assertEquals(
            UsageEventRead.Unknown(UsageEventRead.Unknown.Reason.EVENTS_UNAVAILABLE),
            UsageEventReadBoundary.capture { null },
        )
    }

    @Test
    fun revokedUsageAccessIsUnknownRatherThanEmptyUsage() {
        assertEquals(
            UsageEventRead.Unknown(UsageEventRead.Unknown.Reason.ACCESS_REVOKED),
            UsageEventReadBoundary.capture { throw SecurityException("usage access revoked") },
        )
    }

    @Test
    fun availablePlatformReadPreservesEventsAndCoverage() {
        val events = listOf(ForegroundUsageEvent(ForegroundEventType.ACTIVITY_RESUMED, 10L, "app.a"))
        assertEquals(
            UsageEventRead.Available(events, earliestEventAtMs = 5L),
            UsageEventReadBoundary.capture {
                UsageEventRead.Available(events, earliestEventAtMs = 5L)
            },
        )
    }
}
