package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The weekly standout ledger. The table already exists in the hybrid database;
 * Room manages it here so InsightEngine can preserve weekly deduplication.
 */
@Entity(tableName = "weekly_insights")
data class WeeklyInsightEntity(
    @PrimaryKey
    @ColumnInfo(name = "week_start")
    val weekStart: String,

    @ColumnInfo(name = "insight_id")
    val insightId: String,

    @ColumnInfo(name = "selected_at")
    val selectedAt: String,
)