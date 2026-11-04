package com.tbtechs.focusflow.analytics.detection

import java.time.LocalDate

internal enum class UsageDetectorWindow(val days: Long) {
    MORNING_HIJACK(14),
    VARIABLE_REWARD_LOOP(21),
    ESCALATING_CAPTURE(28),
    STREAK_LOCK_IN(14),
    INFINITE_SESSION_DESIGN(21),
    SUBSTITUTION(28),
    ALLOWANCE_SUGGESTION(30),
    LATE_NIGHT_USE(14),
    HEAVY_DAILY_USE(14),
    ENTRY_APP(14),
    QUICK_CHECK_HABIT(14),
    APP_HOPPING(14),
    WEEKEND_SPIKE(28),
}

internal data class CompletedUsageDateRange(
    val start: LocalDate,
    val end: LocalDate,
)

internal object UsageDetectorWindows {
    fun range(
        detector: UsageDetectorWindow,
        today: LocalDate = LocalDate.now(),
    ): CompletedUsageDateRange = CompletedUsageDateRange(
        start = today.minusDays(detector.days),
        end = today.minusDays(1),
    )
}
