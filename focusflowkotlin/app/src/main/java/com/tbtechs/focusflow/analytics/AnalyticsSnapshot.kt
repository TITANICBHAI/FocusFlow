package com.tbtechs.focusflow.analytics

/**
 * The subset of the analytics snapshot required by AchievementEngine.
 *
 * The remaining analytics fields can be added by the AnalyticsProcessor port
 * without changing achievement condition signatures.
 */
data class AnalyticsSnapshot(
    val generatedAt: String,
    val window: String,
    val tasks: TaskMetrics,
    val sessions: SessionMetrics,
    val blocking: BlockingMetrics,
) {
    data class TaskMetrics(
        val completed: Int,
        val byDayOfWeek: Map<Int, DayBucket>,
        val estimationErrorMinutes: List<Double>,
    )

    data class SessionMetrics(
        val cleanCount: Int,
    )

    data class BlockingMetrics(
        val totalAttempts: Int,
        val topAppShare: Double?,
    )

    data class DayBucket(
        val total: Int,
        val completed: Int = 0,
    )
}