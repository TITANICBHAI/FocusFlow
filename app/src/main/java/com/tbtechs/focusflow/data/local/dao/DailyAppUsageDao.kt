package com.tbtechs.focusflow.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tbtechs.focusflow.data.local.entity.DailyAppUsageEntity
import com.tbtechs.focusflow.data.local.entity.parseHourlyMs

data class AppUsageRangeRow(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_name") val appName: String,
    @ColumnInfo(name = "category") val category: String?,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "foreground_ms") val foregroundMs: Long,
    @ColumnInfo(name = "hourly_ms") val hourlyMs: String,
    @ColumnInfo(name = "launch_count") val launchCount: Int,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long,
)

@Dao
abstract class DailyAppUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entity: DailyAppUsageEntity)

    @Query("SELECT * FROM daily_app_usage WHERE date = :date AND package_name = :packageName LIMIT 1")
    abstract suspend fun getForPackageAndDate(date: String, packageName: String): DailyAppUsageEntity?

    @Transaction
    open suspend fun addForegroundTime(
        date: String,
        packageName: String,
        appName: String,
        category: String?,
        hour: Int,
        durationMs: Long,
        lastUsedAt: Long,
    ) {
        if (durationMs <= 0L || hour !in 0..23) return
        val existing = getForPackageAndDate(date, packageName)
        val hourly = existing?.parseHourlyMs() ?: LongArray(24)
        hourly[hour] += durationMs
        upsert(
            existing?.copy(
                appName = appName,
                category = existing.category ?: category,
                foregroundMs = existing.foregroundMs + durationMs,
                hourlyMs = hourly.joinToString(","),
                lastUsedAt = maxOf(existing.lastUsedAt, lastUsedAt),
            ) ?: DailyAppUsageEntity(
                date = date,
                packageName = packageName,
                appName = appName,
                category = category,
                foregroundMs = durationMs,
                hourlyMs = hourly.joinToString(","),
                launchCount = 0,
                lastUsedAt = lastUsedAt,
            ),
        )
    }

    @Transaction
    open suspend fun incrementLaunchCount(
        date: String,
        packageName: String,
        appName: String,
        category: String?,
        lastUsedAt: Long,
    ) {
        val existing = getForPackageAndDate(date, packageName)
        upsert(
            existing?.copy(
                launchCount = existing.launchCount + 1,
                appName = appName,
                category = existing.category ?: category,
                lastUsedAt = maxOf(existing.lastUsedAt, lastUsedAt),
            ) ?: DailyAppUsageEntity(
                date = date,
                packageName = packageName,
                appName = appName,
                category = category,
                foregroundMs = 0L,
                hourlyMs = "",
                launchCount = 1,
                lastUsedAt = lastUsedAt,
            ),
        )
    }

    @Query("""
        SELECT package_name, app_name, category, date, foreground_ms, hourly_ms,
               launch_count, last_used_at
        FROM daily_app_usage
        WHERE date BETWEEN :startDate AND :endDate
        ORDER BY date ASC, foreground_ms DESC
    """)
    abstract suspend fun getForDateRange(startDate: String, endDate: String): List<AppUsageRangeRow>

    @Query("DELETE FROM daily_app_usage WHERE date < :cutoffDate")
    abstract suspend fun deleteOlderThan(cutoffDate: String)

    @Query("SELECT COUNT(DISTINCT date) FROM daily_app_usage")
    abstract suspend fun countDistinctDates(): Int
}