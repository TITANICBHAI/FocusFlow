package com.tbtechs.focusflow.analytics

/** Converts unavailable platform event reads into an explicit unknown result. */
internal object UsageEventReadBoundary {
    fun capture(read: () -> UsageEventRead.Available?): UsageEventRead =
        try {
            read() ?: UsageEventRead.Unknown(
                UsageEventRead.Unknown.Reason.EVENTS_UNAVAILABLE,
            )
        } catch (_: SecurityException) {
            UsageEventRead.Unknown(UsageEventRead.Unknown.Reason.ACCESS_REVOKED)
        }
}
