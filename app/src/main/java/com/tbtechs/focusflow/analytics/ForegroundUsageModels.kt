package com.tbtechs.focusflow.analytics

enum class ForegroundEventType {
    ACTIVITY_RESUMED,
    ACTIVITY_PAUSED,
    ACTIVITY_STOPPED,
    SCREEN_NON_INTERACTIVE,
    SCREEN_INTERACTIVE,
    KEYGUARD_SHOWN,
    KEYGUARD_HIDDEN,
    DEVICE_SHUTDOWN,
    DEVICE_STARTUP,
}

data class ForegroundUsageEvent(
    val type: ForegroundEventType,
    val timestampMs: Long,
    val packageName: String? = null,
    val activityClassName: String? = null,
)

data class ForegroundSession(
    val packageName: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val activityClassName: String?,
    val isNewOpen: Boolean,
    val isInferredTail: Boolean = false,
) {
    val durationMs: Long get() = endedAtMs - startedAtMs
}

sealed interface UsageEventRead {
    data class Available(
        val events: List<ForegroundUsageEvent>,
        val earliestEventAtMs: Long?,
    ) : UsageEventRead

    data class Unknown(val reason: Reason) : UsageEventRead {
        enum class Reason {
            PERMISSION_MISSING,
            EVENTS_UNAVAILABLE,
            ACCESS_REVOKED,
        }
    }
}

fun interface UsageEventsSource {
    suspend fun readForegroundEvents(startMs: Long, endMs: Long): UsageEventRead
}

data class AppUsageDay(
    val date: String,
    val packageName: String,
    val foregroundMs: Long,
    val hourlyMs: List<Long>,
    val launchCount: Int,
    val sessionCount: Int,
    val firstStartAtMs: Long?,
    val lastUsedAtMs: Long,
)
