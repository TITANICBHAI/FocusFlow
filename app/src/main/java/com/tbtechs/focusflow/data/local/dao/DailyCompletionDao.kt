package com.tbtechs.focusflow.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tbtechs.focusflow.data.local.entity.DailyCompletionEntity
import kotlinx.coroutines.flow.Flow

// ─── Result projection POJO ───────────────────────────────────────────────────

/**
 * Result row for [DailyCompletionDao.getWeeklyCompletionRates].
 * Mirrors the [WeeklyCompletionRateRow] interface from `database.ts`.
 *
 * [weekStart] is a "YYYY-MM-DD" string representing the Sunday that anchors
 * each calendar week (matching the `date(date, '-N days')` SQLite expression
 * in the query). The JS caller uses `dayjs().startOf('week')` which also
 * returns Sunday by default.
 */
data class WeeklyCompletionRateRow(
    @ColumnInfo(name = "week_start") val weekStart: String,
    @ColumnInfo(name = "completed")  val completed: Int,
    @ColumnInfo(name = "total")      val total: Int,
)

// ─── DAO ─────────────────────────────────────────────────────────────────────

@Dao
interface DailyCompletionDao {

    // ── Reactive fetch-all ────────────────────────────────────────────────────

    /**
     * Reactive stream of all daily completion rows ordered by date ascending.
     * Room re-emits when any row changes (useful for a live streak display).
     * No direct JS counterpart — the JS code reads completions only for
     * analytics; this Flow is added to satisfy the "fetch-all → Flow" rule.
     */
    @Query("SELECT * FROM daily_completions ORDER BY date ASC")
    fun observeAllCompletions(): Flow<List<DailyCompletionEntity>>

    /** Up to 60 newest rows, matching the JavaScript streak query. */
    @Query("SELECT * FROM daily_completions ORDER BY date DESC LIMIT 60")
    suspend fun getRecentCompletionsDesc(): List<DailyCompletionEntity>

    /** Complete completion history used for the profile's all-time best streak. */
    @Query("SELECT * FROM daily_completions ORDER BY date ASC")
    suspend fun getAllCompletionsAsc(): List<DailyCompletionEntity>

    // ── Writes ────────────────────────────────────────────────────────────────

    /**
     * Upserts a single row. Maps to `dbRecordDayCompletion`.
     * `INSERT OR REPLACE` overwrites an existing row for the same [date],
     * matching the JS `INSERT OR REPLACE INTO daily_completions` statement.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletion(completion: DailyCompletionEntity)

    /**
     * Batch upsert. Maps to the inner loop inside `dbBackfillDayCompletions`.
     * Room wraps `@Insert` on a [List] in a single transaction automatically,
     * preserving the `withTransactionAsync` atomicity from the JS version.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletions(completions: List<DailyCompletionEntity>)

    // ── Analytics ─────────────────────────────────────────────────────────────

    /**
     * Aggregates [daily_completions] into calendar weeks (Sunday anchor) for
     * all rows with date ≥ [firstWeekISO]. Maps to `dbGetWeeklyCompletionRates`.
     *
     * [firstWeekISO] is the "YYYY-MM-DD" of the earliest Sunday to include,
     * computed by the caller as:
     * ```kotlin
     * LocalDate.now().with(DayOfWeek.SUNDAY).minusWeeks(numWeeks - 1L).toString()
     * // or: dayjs().startOf('week').subtract(numWeeks-1, 'week') in JS
     * ```
     *
     * The nested SELECT computes each row's calendar-week Sunday using
     * `date(date, '-' || strftime('%w', date) || ' days')` — identical to
     * the JS query. `%w` returns 0 for Sunday, so the expression is a no-op
     * for rows that are already Sundays, making the week anchor stable.
     */
    @Query("""
        SELECT
            date(date, '-' || strftime('%w', date) || ' days') AS week_start,
            SUM(completed) AS completed,
            SUM(total)     AS total
        FROM daily_completions
        WHERE date >= :firstWeekISO
        GROUP BY week_start
        ORDER BY week_start ASC
    """)
    suspend fun getWeeklyCompletionRates(firstWeekISO: String): List<WeeklyCompletionRateRow>
}
