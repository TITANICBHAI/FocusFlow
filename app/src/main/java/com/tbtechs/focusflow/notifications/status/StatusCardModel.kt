package com.tbtechs.focusflow.notifications.status

import java.time.ZoneId
import java.util.Locale

enum class StatusCardMode {
    IDLE,
    FOCUS,
    BREAK,
}

enum class StatusCardPriority {
    MIN,
    LOW,
}

enum class StatusCardTapTarget {
    HOME,
    PERMISSIONS,
}

enum class StatusCardAction(
    val label: String,
    val identityToken: String,
    val requestCode: Int,
    val minutes: Int? = null,
) {
    DONE("✓ Done", "complete", 3001),
    EXTEND_15("+15m", "extend-15", 3002, minutes = 15),
    EXTEND_30("+30m", "extend-30", 3003, minutes = 30),
    SKIP("Skip", "skip", 3004),
}

data class StatusCardClock(
    val wallClockMs: Long,
    val elapsedRealtimeMs: Long,
    val zoneId: ZoneId,
    val locale: Locale,
)

data class StatusCardModel(
    val mode: StatusCardMode,
    val title: String,
    val text: String,
    val subText: String?,
    val chronometerBaseMs: Long,
    val chronometerCountDown: Boolean,
    val progressPercent: Int?,
    val actions: List<StatusCardAction>,
    val priority: StatusCardPriority,
    val tapTarget: StatusCardTapTarget = StatusCardTapTarget.HOME,
    val taskId: String? = null,
    val usesChronometer: Boolean = true,
    val showWhen: Boolean = true,
    val ongoing: Boolean = true,
    val onlyAlertOnce: Boolean = true,
)
