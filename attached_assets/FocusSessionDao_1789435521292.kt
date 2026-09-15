package com.tbtechs.focusflow.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tbtechs.focusflow.data.local.entity.FocusSessionEntity

// ─── Result projection POJOs ──────────────────────────────────────────────────

data class SessionOverrideCountRow(
    @ColumnInfo(name = "session_id")     val sessionId: Long,
    @ColumnInfo(name = "task_id")        val taskId: String?,
    @ColumnInfo(name = "started_at")     val startedAt: String,
    @ColumnInfo(name = "ended_at")       val endedAt: String?,
    @ColumnInfo(name = "override_count") val overrideCount: Int,
)

data class EstimationErrorRow(
    @ColumnInfo(name = "task_id")         val taskId: String,
    @ColumnInfo(name = "planned_minutes") val plannedMinutes: Int,
    @ColumnInfo(name = "actual_minutes")  val actualMinutes: Double,
    @ColumnInfo(name = "start_hour")      val startHour: Int?,
)

data class RecentSessionSummaryRow(
    @ColumnInfo(name = "session_id")      val sessionId: Long,
    @ColumnInfo(name = "task_id")         val taskId: String,
    @ColumnInfo(name = "task_title")      val taskTitle: String?,
    @ColumnInfo(name = "started_at")      val startedAt: String,
    @ColumnInfo(name = "ended_at")        val endedAt: String,
    @ColumnInfo(name = "planned_minutes") val plannedMinutes: Int?,
    @ColumnInfo(name = "override_count")  val overrideCount: Int,
)

/**
 * Aggregate projection for [FocusSessionRepository.getLifetimeStats].
 * Maps to the single-row query in `dbGetLifetimeStats` in `database.ts`.
 *
 * [lastSessionAt] is `MAX(started_at) WHERE is_active = 0` — i.e. the most
 * recent *completed* session's start timestamp, or null when no completed
 * session exists. Using `is_active = 0` excludes a currently-running session
 * so that BACK_AGAIN / RESET achievement conditions see the gap *before* the
 * current session, not zero.
 */
data class LifetimeStatsRow(
    @ColumnInfo(name = "completed_tasks")        val completedTasks: Int,
    @ColumnInfo(name = "total_sessions")         val totalSessions: Int,
    @ColumnInfo(name = "clean_sessions")         val cleanSessions: Int,
    @ColumnInfo(name = "total_focus_minutes")    val totalFocusMinutes: Double,
    @ColumnInfo(name = "total_override_attempts") val totalOverrideAttempts: Int,
    @ColumnInfo(name = "last_session_at")        val lastSessionAt: String?,
)

@Dao
interface FocusSessionDao {

    // ── One-shot queries ──────────────────────────────────────────────────────

    @Query("SELECT * FROM focus_sessions WHERE is_active = 1 ORDER BY id DESC LIMIT 1")
    suspend fun getActiveSession(): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions WHERE started_at >= :startOfDay ORDER BY id ASC")
    suspend fun getSessionsFrom(startOfDay: String): List<FocusSessionEntity>

    // ── Lifetime stats aggregate ──────────────────────────────────────────────

    /**
     * Single-row aggregate that maps to the body of `dbGetLifetimeStats` in
     * `database.ts`. The streak is computed separately in Kotlin
     * ([FocusSessionRepository.getLifetimeStats]) using daily_completions rows,
     * exactly as `dbGetStreak()` does in the JS codebase.
     *
     * [nowISO] is passed to bound the `clean_sessions` correlated subquery's
     * `COALESCE(ended_at, ?)` — active sessions are treated as ending "now"
     * for the purpose of the override-overlap test, matching the JS behaviour.
     */
    @Query("""
        SELECT
            (SELECT COUNT(*) FROM tasks WHERE status = 'completed')
                AS completed_tasks,
            (SELECT COUNT(*) FROM focus_sessions)
                AS total_sessions,
            (SELECT COUNT(*) FROM focus_sessions s
              WHERE NOT EXISTS (
                SELECT 1 FROM focus_overrides o
                 WHERE o.task_id = s.task_id
                   AND o.overridden_at >= s.started_at
                   AND o.overridden_at <= COALESCE(s.ended_at, :nowISO)
              ))
                AS clean_sessions,
            COALESCE((
                SELECT SUM(
                    CASE WHEN s.ended_at IS NULL THEN 0.0
                    ELSE MAX(0.0,
                        (julianday(s.ended_at) - julianday(s.started_at)) * 1440.0)
                    END
                ) FROM focus_sessions s
            ), 0.0)
                AS total_focus_minutes,
            (SELECT COUNT(*) FROM focus_overrides)
                AS total_override_attempts,
            (SELECT MAX(started_at) FROM focus_sessions WHERE is_active = 0)
                AS last_session_at
    """)
    suspend fun getLifetimeStatsAggregate(nowISO: String): LifetimeStatsRow?

    // ── Writes ────────────────────────────────────────────────────────────────

    @Insert
    suspend fun insertSession(session: FocusSessionEntity): Long

    @Query("""
        UPDATE focus_sessions
        SET is_active = 0, ended_at = :endedAt
        WHERE task_id = :taskId AND is_active = 1
    """)
    suspend fun endSession(taskId: String, endedAt: String): Int

    // ── Analytics ─────────────────────────────────────────────────────────────

    @Query("""
        SELECT
            s.id            AS session_id,
            s.task_id,
            t.title         AS task_title,
            s.started_at,
            s.ended_at,
            t.duration_minutes AS planned_minutes,
            COUNT(o.id)     AS override_count
        FROM focus_sessions s
        LEFT JOIN tasks t ON t.id = s.task_id
        LEFT JOIN focus_overrides o
            ON o.task_id = s.task_id
           AND o.overridden_at >= s.started_at
           AND o.overridden_at <= s.ended_at
        WHERE s.is_active = 0
          AND s.ended_at IS NOT NULL
          AND s.ended_at >= :cutoff
          AND s.ended_at <= :now
        GROUP BY s.id, s.task_id, t.title, s.started_at, s.ended_at, t.duration_minutes
        ORDER BY s.ended_at DESC, s.id DESC
        LIMIT 1
    """)
    suspend fun getRecentCompletedSession(cutoff: String, now: String): RecentSessionSummaryRow?

    @Query("""
        SELECT
            s.id            AS session_id,
            s.task_id,
            s.started_at,
            s.ended_at,
            COUNT(o.id)     AS override_count
        FROM focus_sessions s
        LEFT JOIN focus_overrides o
            ON o.task_id = s.task_id
           AND o.overridden_at >= s.started_at
           AND o.overridden_at <= COALESCE(s.ended_at, :endISO)
           AND o.overridden_at >= :startISO
           AND o.overridden_at <  :endISO
        WHERE s.started_at < :endISO
          AND (s.ended_at IS NULL OR s.ended_at > :startISO)
        GROUP BY s.id, s.task_id, s.started_at, s.ended_at
        ORDER BY s.started_at ASC
    """)
    suspend fun getSessionsWithOverrideCount(
        startISO: String,
        endISO: String,
    ): List<SessionOverrideCountRow>

    @Query("""
        SELECT
            t.id            AS task_id,
            t.duration_minutes AS planned_minutes,
            (julianday(s.ended_at) - julianday(s.started_at)) * 1440.0 AS actual_minutes,
            CAST(strftime('%H', datetime(s.started_at, 'localtime')) AS INTEGER) AS start_hour
        FROM focus_sessions s
        INNER JOIN tasks t ON t.id = s.task_id
        WHERE t.status = 'completed'
          AND s.ended_at IS NOT NULL
          AND t.duration_minutes > 0
          AND s.ended_at > s.started_at
          AND s.started_at < :endISO
          AND s.ended_at   > :startISO
        ORDER BY s.started_at ASC
    """)
    suspend fun getEstimationErrors(
        startISO: String,
        endISO: String,
    ): List<EstimationErrorRow>
}
