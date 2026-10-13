package com.tbtechs.focusflow.ui.common

/**
 * Shared task-end boundary state used by Focus and Schedule.
 *
 * The first minute after a task ends is intentionally distinct from a
 * whole-minute overdue label, and sub-minute countdowns retain seconds.
 */
internal data class TaskTimeDisplay(
    val hasEnded: Boolean,
    val justEnded: Boolean,
    val secondsRemaining: Int?,
    val overdueMinutes: Long?,
)

internal fun taskTimeDisplay(
    endTimeMs: Long,
    nowMs: Long,
): TaskTimeDisplay {
    val remainingMs = endTimeMs - nowMs
    return when {
        remainingMs > 0L && remainingMs < 60_000L -> {
            val seconds = ((remainingMs + 999L) / 1_000L)
                .coerceAtLeast(1L)
                .toInt()
            TaskTimeDisplay(
                hasEnded = false,
                justEnded = false,
                secondsRemaining = seconds,
                overdueMinutes = null,
            )
        }

        remainingMs <= 0L && remainingMs > -60_000L -> TaskTimeDisplay(
            hasEnded = true,
            justEnded = true,
            secondsRemaining = null,
            overdueMinutes = null,
        )

        remainingMs <= -60_000L -> TaskTimeDisplay(
            hasEnded = true,
            justEnded = false,
            secondsRemaining = null,
            overdueMinutes = (-remainingMs / 60_000L).coerceAtLeast(1L),
        )

        else -> TaskTimeDisplay(
            hasEnded = false,
            justEnded = false,
            secondsRemaining = null,
            overdueMinutes = null,
        )
    }
}
