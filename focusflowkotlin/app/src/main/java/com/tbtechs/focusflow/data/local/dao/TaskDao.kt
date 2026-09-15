package com.tbtechs.focusflow.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tbtechs.focusflow.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

// ─── Result projection POJOs ──────────────────────────────────────────────────

/**
 * Minimal task projection used by [FocusSessionRepository.backfillDayCompletions].
 * Mirrors the `SELECT start_time, status FROM tasks` projection in
 * `dbBackfillDayCompletions` — only the two columns needed for the calculation.
 */
data class TaskStartStatusRow(
    @ColumnInfo(name = "start_time") val startTime: String,
    @ColumnInfo(name = "status")     val status: String,
)

/**
 * Result POJO for [getTasksByHourOfDay], mirroring the [TasksByHourRow]
 * interface returned by `dbGetTasksByHourOfDay` in `database.ts`.
 */
data class TasksByHourRow(
    @ColumnInfo(name = "hour")      val hour: Int,
    @ColumnInfo(name = "total")     val total: Int,
    @ColumnInfo(name = "completed") val completed: Int,
)

// ─── DAO ─────────────────────────────────────────────────────────────────────

@Dao
interface TaskDao {

    // ── Reactive fetch-all ────────────────────────────────────────────────────

    /**
     * Reactive stream of all tasks ordered by start time.
     * Maps to `dbGetAllTasks` — the source of truth for [TaskViewModel.tasks].
     * Room re-emits whenever any task row changes.
     */
    @Query("SELECT * FROM tasks ORDER BY start_time ASC")
    fun observeAllTasks(): Flow<List<TaskEntity>>

    // ── One-shot lookups ──────────────────────────────────────────────────────

    /**
     * Tasks that ended within [cutoff, now) but are still unresolved
     * (status ≠ 'completed' or 'skipped'). Maps to `dbGetRecentUnresolvedTasks`.
     *
     * @param cutoff ISO timestamp for 24 h ago.
     * @param now    ISO timestamp for the current moment.
     */
    @Query("""
        SELECT * FROM tasks
        WHERE end_time >= :cutoff
          AND end_time < :now
          AND status NOT IN ('completed', 'skipped')
        ORDER BY end_time DESC
    """)
    suspend fun getRecentUnresolvedTasks(cutoff: String, now: String): List<TaskEntity>

    /**
     * Tasks whose local-calendar date falls in [[start], [end]] inclusive.
     * Maps to `dbGetTasksInDateRange`. Both arguments are "YYYY-MM-DD" strings
     * in the device's local timezone — callers must convert before passing.
     *
     * The `datetime(start_time, 'localtime')` modifier converts stored UTC
     * timestamps to device-local time before the date comparison, matching
     * the JS implementation exactly.
     */
    @Query("""
        SELECT * FROM tasks
        WHERE date(datetime(start_time, 'localtime')) BETWEEN :start AND :end
        ORDER BY start_time ASC
    """)
    suspend fun getTasksInDateRange(start: String, end: String): List<TaskEntity>

    /**
     * Tasks scheduled on a specific local-calendar day.
     * Maps to `dbGetTasksForDate`. [localDate] is "YYYY-MM-DD" in device timezone.
     */
    @Query("""
        SELECT * FROM tasks
        WHERE date(datetime(start_time, 'localtime')) = :localDate
        ORDER BY start_time ASC
    """)
    suspend fun getTasksForDate(localDate: String): List<TaskEntity>

    /**
     * Minimal start_time/status projection for tasks starting on or after [cutoff].
     * Used exclusively by [FocusSessionRepository.backfillDayCompletions] to
     * rebuild the daily_completions ledger without loading full task rows.
     */
    @Query("SELECT start_time, status FROM tasks WHERE start_time >= :cutoff")
    suspend fun getTaskStartStatusFrom(cutoff: String): List<TaskStartStatusRow>

    // ── Writes ────────────────────────────────────────────────────────────────

    /**
     * Maps to `dbInsertTask` — `INSERT OR IGNORE` semantics: silently skips
     * duplicate IDs rather than throwing, matching the JS `INSERT OR IGNORE`.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTask(task: TaskEntity)

    /**
     * Maps to `dbUpdateTask` — updates a single row by primary key.
     * Room generates `UPDATE tasks SET … WHERE id = ?`.
     */
    @Update
    suspend fun updateTask(task: TaskEntity)

    /**
     * Maps to `dbUpdateTasksBatch`. Room wraps `@Update` on a [List] in a
     * single SQLite transaction automatically, preserving the atomicity
     * guarantee from `database.withTransactionAsync` in the JS version.
     */
    @Update
    suspend fun updateTasks(tasks: List<TaskEntity>)

    /** Maps to `dbDeleteTask`. */
    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: String)

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    // ── Analytics ─────────────────────────────────────────────────────────────

    /**
     * Maps to `dbGetTasksByHourOfDay`. Counts tasks (and the completed subset)
     * by local hour of day within [[startISO], [endISO]).
     * Used by the Stats screen; not exposed through [TaskRepository] yet —
     * a future StatsRepository or Track D will call this directly.
     */
    @Query("""
        SELECT
            CAST(strftime('%H', datetime(start_time, 'localtime')) AS INTEGER) AS hour,
            COUNT(*) AS total,
            SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END) AS completed
        FROM tasks
        WHERE start_time >= :startISO AND start_time < :endISO
        GROUP BY hour
        ORDER BY hour ASC
    """)
    suspend fun getTasksByHourOfDay(startISO: String, endISO: String): List<TasksByHourRow>
}
