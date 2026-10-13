package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/** One row per app per local calendar day, written from live foreground events. */
@Entity(
    tableName = "daily_app_usage",
    primaryKeys = ["date", "package_name"],
    indices = [Index(value = ["package_name", "date"], name = "idx_dau_package_date")],
)
data class DailyAppUsageEntity(
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_name") val appName: String,
    @ColumnInfo(name = "category") val category: String?,
    @ColumnInfo(name = "foreground_ms", defaultValue = "0") val foregroundMs: Long,
    @ColumnInfo(name = "hourly_ms", defaultValue = "") val hourlyMs: String,
    @ColumnInfo(name = "launch_count", defaultValue = "0") val launchCount: Int,
    @ColumnInfo(name = "last_used_at", defaultValue = "0") val lastUsedAt: Long,
)

fun DailyAppUsageEntity.parseHourlyMs(): LongArray {
    if (hourlyMs.isBlank()) return LongArray(24)
    val parts = hourlyMs.split(",")
    return LongArray(24) { index -> parts.getOrNull(index)?.toLongOrNull() ?: 0L }
}