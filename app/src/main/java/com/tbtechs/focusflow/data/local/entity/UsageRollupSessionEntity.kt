package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "usage_rollup_session",
    primaryKeys = ["package_name", "started_at_ms"],
    indices = [
        Index(value = ["local_date", "package_name"], name = "idx_urs_date_package"),
    ],
)
data class UsageRollupSessionEntity(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "started_at_ms") val startedAtMs: Long,
    @ColumnInfo(name = "ended_at_ms") val endedAtMs: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "local_date") val localDate: String,
)
