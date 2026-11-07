package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Durable per-day count of blocked-app open attempts, derived from the capped temptation log.
 * Unlike the usage rollups this is a counter, so it is also written for today.
 */
@Entity(tableName = "blocked_attempt_day", primaryKeys = ["date", "package_name"])
data class BlockedAttemptDayEntity(
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_name") val appName: String,
    @ColumnInfo(name = "attempts") val attempts: Int,
    @ColumnInfo(name = "hourly_attempts") val hourlyAttempts: String,
)
