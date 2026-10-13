package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "usage_rollup_app_day",
    primaryKeys = ["date", "package_name"],
    indices = [
        Index(value = ["package_name", "date"], name = "idx_urad_package_date"),
    ],
)
data class UsageRollupAppDayEntity(
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_name") val appName: String,
    @ColumnInfo(name = "category") val category: String?,
    @ColumnInfo(name = "foreground_ms") val foregroundMs: Long,
    @ColumnInfo(name = "hourly_ms") val hourlyMs: String,
    @ColumnInfo(name = "launch_count") val launchCount: Int,
    @ColumnInfo(name = "session_count") val sessionCount: Int,
    @ColumnInfo(name = "first_start_at_ms") val firstStartAtMs: Long?,
    @ColumnInfo(name = "last_used_at_ms") val lastUsedAtMs: Long,
)
