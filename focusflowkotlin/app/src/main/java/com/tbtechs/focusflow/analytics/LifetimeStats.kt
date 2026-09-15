package com.tbtechs.focusflow.analytics

/**
 * All-time aggregates consumed by the achievement engine.
 *
 * [lastSessionAt] is the start time of the most recent completed session. It
 * deliberately excludes an active session so return-after-a-break achievements
 * measure the gap before the session that is being started.
 */
data class LifetimeStats(
    val completedTasks: Int,
    val totalSessions: Int,
    val cleanSessions: Int,
    val totalFocusMinutes: Double,
    val totalOverrideAttempts: Int,
    val currentStreakDays: Int,
    val lastSessionAt: String?,
)