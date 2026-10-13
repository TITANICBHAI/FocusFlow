package com.tbtechs.focusflow.notifications.status

import java.time.Instant

object StatusCardMapper {
    private val focusActions = listOf(
        StatusCardAction.DONE,
        StatusCardAction.EXTEND_15,
        StatusCardAction.EXTEND_30,
        StatusCardAction.SKIP,
    )

    fun idle(
        serviceStartMs: Long,
        clock: StatusCardClock,
        needsAttention: Boolean = false,
        healthKnown: Boolean = true,
    ) = StatusCardModel(
        mode = StatusCardMode.IDLE,
        title = "FocusFlow",
        text = when {
            !healthKnown -> "Checking protection status — tap to open"
            needsAttention -> "Needs attention — tap to fix"
            else -> "Monitoring active — tap to open"
        },
        subText = null,
        chronometerBaseMs = clock.elapsedRealtimeMs - (clock.wallClockMs - serviceStartMs),
        chronometerCountDown = false,
        progressPercent = null,
        actions = emptyList(),
        priority = StatusCardPriority.MIN,
        tapTarget = if (healthKnown && needsAttention) {
            StatusCardTapTarget.PERMISSIONS
        } else {
            StatusCardTapTarget.HOME
        },
    )

    fun focus(
        taskId: String,
        taskName: String,
        startTimeMs: Long,
        endTimeMs: Long,
        remainingMs: Long,
        nextName: String?,
        clock: StatusCardClock,
    ): StatusCardModel {
        val endTime = Instant.ofEpochMilli(endTimeMs).atZone(clock.zoneId)
        val hour = endTime.hour
        val hour12 = if (hour % 12 == 0) 12 else hour % 12
        val amPm = if (hour < 12) "AM" else "PM"
        val endLabel = String.format(
            clock.locale,
            "%d:%02d %s",
            hour12,
            endTime.minute,
            amPm,
        )

        val totalMs = if (startTimeMs > 0L) endTimeMs - startTimeMs else remainingMs
        val elapsedMs = (totalMs - remainingMs).coerceAtLeast(0L)
        val progressPercent = if (totalMs > 0L) {
            ((elapsedMs * 100L) / totalMs).toInt().coerceIn(0, 100)
        } else {
            0
        }

        return StatusCardModel(
            mode = StatusCardMode.FOCUS,
            title = "🎯 $taskName",
            text = "ends $endLabel",
            subText = nextName?.let { "Next: $it" },
            chronometerBaseMs = endTimeMs - clock.wallClockMs + clock.elapsedRealtimeMs,
            chronometerCountDown = true,
            progressPercent = progressPercent,
            actions = focusActions,
            priority = StatusCardPriority.LOW,
            taskId = taskId,
        )
    }

    fun breakTime(
        taskName: String,
        breakUntilMs: Long,
        clock: StatusCardClock,
    ) = StatusCardModel(
        mode = StatusCardMode.BREAK,
        title = "☕ Break · $taskName",
        text = "Apps temporarily unlocked",
        subText = "Back to work when the break ends",
        chronometerBaseMs = breakUntilMs - clock.wallClockMs + clock.elapsedRealtimeMs,
        chronometerCountDown = true,
        progressPercent = null,
        actions = emptyList(),
        priority = StatusCardPriority.LOW,
    )
}
