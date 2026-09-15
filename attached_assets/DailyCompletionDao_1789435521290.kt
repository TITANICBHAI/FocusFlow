package com.tbtechs.focusflow.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tbtechs.focusflow.data.local.entity.DailyCompletionEntity
import kotlinx.coroutines.flow.Flow

data class WeeklyCompletionRateRow(
    @ColumnInfo(name = "week_start") val weekStart: String,
    @ColumnInfo(name = "completed")  val completed: Int,
    @ColumnInfo(name = "total")      val total: Int,
)

@Dao
interface DailyCompletionDao {

    @Query("SELECT * FROM daily_completions ORDER BY date ASC")
    fun observeAllCompletions(): Flow<List<DailyCompletionEntity>>

    /**
     * Returns up to 60 rows ordered newest-first for streak computation.
     * 60 days is the same cap used by `dbGetStreak` in `database.ts`
     * (`LIMIT 60`) — enough headroom beyond the 21-day THREE_WEEKS badge.
     * Called by [FocusSessionRepository.computeStreak].
     */
    @Query("SELECT * FROM daily_completions ORDER BY date DESC LIMIT 60")
    suspend fun getRecentCompletionsDesc(): List<DailyCompletionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletion(completion: DailyCompletionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletions(completions: List<DailyCompletionEntity>)

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
