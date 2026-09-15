package com.tbtechs.focusflow.analytics

/**
 * Kotlin port of the `LifetimeStats` interface from `src/data/database.ts`.
 *
 * Contains all-time aggregate statistics about the user's FocusFlow usage.
 * Produced by [com.tbtechs.focusflow.data.repository.FocusSessionRepository.getLifetimeStats]
 * and consumed by [AchievementEngine.evaluateAchievements] /
 * [AchievementEngine.syncAchievements].
 *
 * ## Field notes
 * - [totalFocusMinutes] is rounded to 2 decimal places (matching the JS
 *   `Math.round(... * 100) / 100` in `dbGetLifetimeStats`).
 * - [currentStreakDays] is computed in Kotlin using the same consecutive-day
 *   ≥ 50% completion algorithm as `dbGetStreak()` in `database.ts`.
 * - [lastSessionAt] was added to support BACK_AGAIN and RESET achievements.
 *   It is the `started_at` of the most recent *completed* focus session
 *   (`is_active = 0`), or null when no completed session exists. A currently-
 *   active session is deliberately excluded so that the gap before the
 *   current session is what gets measured.
 */
data class LifetimeStats(
    /** Total tasks ever marked `'completed'`. */
    val completedTasks: Int,

    /** Total focus sessions ever started (active + completed). */
    val totalSessions: Int,

    /**
     * Sessions with no override events during their lifetime.
     * A session is "clean" if no [focus_overrides] row's `overridden_at`
     * falls within `[started_at, ended_at]` for that session.
     */
    val cleanSessions: Int,

    /** Sum of all completed session durations, in minutes (max-0 clamped, 2 dp). */
    val totalFocusMinutes: Double,

    /** Total rows in the `focus_overrides` table across all time. */
    val totalOverrideAttempts: Int,

    /** Consecutive days with ≥ 50% task completion, counting backwards from today. */
    val currentStreakDays: Int,

    /**
     * ISO-8601 UTC timestamp of the most recent *completed* session's `started_at`,
     * or null when no completed session exists yet.
     *
     * New field — not present in the original `LifetimeStats` interface.
     * Added to support BACK_AGAIN and RESET achievement conditions.
     */
    val lastSessionAt: String?,
)
