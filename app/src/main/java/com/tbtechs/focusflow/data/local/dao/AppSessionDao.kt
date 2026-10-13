package com.tbtechs.focusflow.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity

data class SessionStatRow(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "session_count") val sessionCount: Int,
    @ColumnInfo(name = "avg_duration_ms") val avgDurationMs: Double,
    @ColumnInfo(name = "min_duration_ms") val minDurationMs: Long,
    @ColumnInfo(name = "max_duration_ms") val maxDurationMs: Long,
    @ColumnInfo(name = "total_ms") val totalMs: Long,
)

data class FirstSessionRow(
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_name") val appName: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
)

@Dao
interface AppSessionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(session: AppSessionEntity): Long

    @Query("""
        SELECT package_name, local_date, COUNT(*) AS session_count,
               AVG(duration_ms) AS avg_duration_ms, MIN(duration_ms) AS min_duration_ms,
               MAX(duration_ms) AS max_duration_ms, SUM(duration_ms) AS total_ms
        FROM app_sessions
        WHERE local_date BETWEEN :startDate AND :endDate AND duration_ms > 0
        GROUP BY package_name, local_date
        ORDER BY local_date ASC
    """)
    suspend fun getSessionStatsByDay(startDate: String, endDate: String): List<SessionStatRow>

    @Query("""
        SELECT local_date, package_name, app_name, MIN(started_at) AS started_at
        FROM app_sessions
        WHERE local_date BETWEEN :startDate AND :endDate
        GROUP BY local_date
        ORDER BY local_date ASC
    """)
    suspend fun getFirstSessionEachDay(startDate: String, endDate: String): List<FirstSessionRow>

    /**
     * Raw closed sessions for one package in a date range. The detection
     * runner calls this only for packages that pass the cheap aggregate
     * shortlist, keeping variance calculations bounded.
     */
    @Query("""
        SELECT * FROM app_sessions
        WHERE package_name = :packageName
          AND local_date BETWEEN :startDate AND :endDate
          AND duration_ms > 0
        ORDER BY started_at ASC
    """)
    suspend fun getSessionsForPackageInRange(
        packageName: String,
        startDate: String,
        endDate: String,
    ): List<AppSessionEntity>

    @Query("""
        SELECT * FROM app_sessions
        WHERE local_date BETWEEN :startDate AND :endDate
          AND duration_ms > 0
        ORDER BY started_at ASC
    """)
    suspend fun getAllSessionsInRange(startDate: String, endDate: String): List<AppSessionEntity>

    @Query("DELETE FROM app_sessions WHERE local_date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)

    @Query("SELECT COUNT(DISTINCT local_date) FROM app_sessions WHERE duration_ms > 0")
    suspend fun countDistinctDaysWithData(): Int
}