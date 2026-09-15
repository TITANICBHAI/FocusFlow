package com.tbtechs.focusflow.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tbtechs.focusflow.data.local.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

// ─── Result projection POJOs ──────────────────────────────────────────────────

/**
 * Mirrors the [SessionOverrideCountRow] interface from `database.ts`.
 * Returned by [FocusSessionDao.getSessionsWithOverrideCount].
 *
 * [taskId] is nullable because some legacy focus sessions may have been
 * started without a task association (the schema allows any TEXT value;
 * the join with tasks can return null if the task was deleted).
 */
data class SessionOverrideCountRow(
    @ColumnInfo(name = "session_id")     val sessionId: Long,
    @ColumnInfo(name = "task_id")        val taskId: String?,
    @ColumnInfo(name = "started_at")     val startedAt: String,
    @ColumnInfo(name = "ended_at")       val endedAt: String?,
    @ColumnInfo(name = "override_count") val overrideCount: Int,
)

/**
 * Mirrors the [EstimationErrorRow] interface from `database.ts`.
 * Returned by [FocusSessionDao.getEstimationErrors].
 *
 * [startHour] can be null for rows where `datetime()` returns NULL
 * (theoretically impossible for well-formed ISO strings but typed nullable
 * to match the `number | null` in TypeScript and avoid a silent crash).
 */
data class EstimationErrorRow(
    @ColumnInfo(name = "task_id")         val taskId: String,
    @ColumnInfo(name = "planned_minutes") val plannedMinutes: Int,
    @ColumnInfo(name = "actual_minutes")  val actualMinutes: Double,
    @ColumnInfo(name = "start_hour")      val startHour: Int?,
)

/**
 * Mirrors the [RecentFocusSessionSummary] interface from `database.ts`.
 * Returned by [FocusSessionDao.getRecentCompletedSession].
 *
 * [taskTitle] and [plannedMinutes] are nullable: the JOIN is a LEFT JOIN,
 * so a session whose task was deleted still appears with null task fields.
 */
data class RecentSessionSummaryRow(
    @ColumnInfo(name = "session_id")      val sessionId: Long,
    @ColumnInfo(name = "task_id")         val taskId: String,
    @ColumnInfo(name = "task_title")      val taskTitle: String?,
    @ColumnInfo(name = "started_at")      val startedAt: String,
    @ColumnInfo(name = "ended_at")        val endedAt: String,
    @ColumnInfo(name = "planned_minutes") val plannedMinutes: Int?,
    @ColumnInfo(name = "override_count")  val overrideCount: Int,
)

/** Aggregate projection used by the lifetime stats and achievement engine. */
data class LifetimeStatsRow(
    @ColumnInfo(name = "completed_tasks")         val completedTasks: Int,
    @ColumnInfo(name = "total_sessions")          val totalSessions: Int,
    @ColumnInfo(name = "clean_sessions")          val cleanSessions: Int,
    @ColumnInfo(name = "total_focus_minutes")     val totalFocusMinutes: Double,
    @ColumnInfo(name = "total_override_attempts") val totalOverrideAttempts: Int,
    @ColumnInfo(name = "last_session_at")         val lastSessionAt: String?,
)

// ─── DAO ─────────────────────────────────────────────────────────────────────

@Dao
interface FocusSessionDao {

    // ── One-shot queries ──────────────────────────────────────────────────────

    /**
     * Returns the single active session, or null if none is running.
     * Maps to `dbGetActiveFocusSession`.
     *
     * `ORDER BY id DESC LIMIT 1` matches the JS query exactly — guards against
     * the pathological case of two rows with `is_active = 1` (should never
     * happen under normal operation, but is_active is not a UNIQUE constraint
     * in the schema, so the defensive ordering is retained).
     */
    @Query("SELECT * FROM focus_sessions WHERE is_active = 1 ORDER BY id DESC LIMIT 1")
    suspend fun getActiveSession(): FocusSessionEntity?

    /** Reactive counterpart used by the UI after boot recovery or service writes. */
    @Query("SELECT * FROM focus_sessions WHERE is_active = 1 ORDER BY id DESC LIMIT 1")
    fun observeActiveSession(): Flow<FocusSessionEntity?>

    /**
     * Returns all sessions that started on or after [startOfDay] (ISO timestamp).
     * Used by [FocusSessionRepository.getTodayFocusMinutes] to load the raw rows;
     * the 6-hour-per-session cap and summation are applied in the repository
     * because they require arithmetic not expressible in plain SQLite.
     *
     * Maps to the `SELECT started_at, ended_at FROM focus_sessions WHERE
     * started_at >= ?` query inside `dbGetTodayFocusMinutes`.
     */
    @Query("SELECT * FROM focus_sessions WHERE started_at >= :startOfDay ORDER BY id ASC")
    suspend fun getSessionsFrom(startOfDay: String): List<FocusSessionEntity>

    /**
     * Reads bounded lifetime aggregates. Streak calculation remains in the
     * repository because it depends on local-calendar date arithmetic.
     */
    @Query("""
        SELECT
            (SELECT COUNT(*) FROM tasks WHERE status = 'completed') AS completed_tasks,
            (SELECT COUNT(*) FROM focus_sessions) AS total_sessions,
            (SELECT COUNT(*) FROM focus_sessions s
              WHERE NOT EXISTS (
                SELECT 1 FROM focus_overrides o
                 WHERE o.task_id = s.task_id
                   AND o.overridden_at >= s.started_at
                   AND o.overridden_at <= COALESCE(s.ended_at, :nowISO)
              )) AS clean_sessions,
            COALESCE((
                SELECT SUM(
                    CASE WHEN s.ended_at IS NULL THEN 0.0
                    ELSE MAX(0.0,
                        (julianday(s.ended_at) - julianday(s.started_at)) * 1440.0)
                    END
                ) FROM focus_sessions s
            ), 0.0) AS total_focus_minutes,
            (SELECT COUNT(*) FROM focus_overrides) AS total_override_attempts,
            (SELECT MAX(started_at) FROM focus_sessions WHERE is_active = 0) AS last_session_at
    """)
    suspend fun getLifetimeStatsAggregate(nowISO: String): LifetimeStatsRow?

    // ── Writes ────────────────────────────────────────────────────────────────

    /**
     * Maps to `dbStartFocusSession`. Returns the auto-generated row id.
     * Callers should pass `id = 0` (the default) so Room triggers AUTOINCREMENT.
     */
    @Insert
    suspend fun insertSession(session: FocusSessionEntity): Long

    /**
     * Maps to `dbEndFocusSession`. Clears the active flag and stamps the end
     * timestamp for the session belonging to [taskId]. Returns the number of
     * affected rows (0 if no active session for that task).
     */
    @Query("""
        UPDATE focus_sessions
        SET is_active = 0, ended_at = :endedAt
        WHERE task_id = :taskId AND is_active = 1
    """)
    suspend fun endSession(taskId: String, endedAt: String): Int

    // ── Analytics ─────────────────────────────────────────────────────────────

    /**
     * Returns the most recently completed focus session that ended within
     * [cutoff, now], including a count of temptation overrides that occurred
     * during it. Maps to `dbGetRecentCompletedFocusSession`.
     *
     * The LEFT JOIN on tasks means [RecentSessionSummaryRow.taskTitle] and
     * [RecentSessionSummaryRow.plannedMinutes] can be null when the task has
     * been deleted after the session ended.
     *
     * Callers (via [FocusSessionRepository.getRecentCompletedSession]) are
     * responsible for clamping [RecentSessionSummaryRow.overrideCount] to ≥ 0
     * and supplying a fallback title, matching the JS implementation.
     */
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
        LEFT JOIN tasks t
            ON t.id = s.task_id
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

    /**
     * Returns sessions that overlap [[startISO], [endISO]] with the count of
     * override events that happened during each session's lifetime within that
     * window. Maps to `dbGetSessionsWithOverrideCount`.
     *
     * Used by the analytics engine to correlate session discipline with
     * scheduled task data. Called from [FocusSessionRepository].
     */
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

    /**
     * Compares the scheduled task duration with the actual session duration
     * for all completed sessions within [[startISO], [endISO]].
     * Maps to `dbGetEstimationErrors`.
     *
     * `actual_minutes` is a REAL (Double) computed as julianday difference × 1440.
     * Sessions without an end timestamp are excluded (their actual duration is
     * not final), matching the JS `s.ended_at IS NOT NULL` guard.
     */
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
