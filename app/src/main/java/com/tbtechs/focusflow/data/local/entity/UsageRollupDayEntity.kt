package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage_rollup_day")
data class UsageRollupDayEntity(
    @PrimaryKey @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "coverage_start_ms") val coverageStartMs: Long?,
    @ColumnInfo(name = "coverage_end_ms") val coverageEndMs: Long?,
    @ColumnInfo(name = "pipeline_version") val pipelineVersion: Int,
    @ColumnInfo(name = "computed_at_ms") val computedAtMs: Long,
    @ColumnInfo(name = "total_foreground_ms") val totalForegroundMs: Long,
) {
    companion object {
        const val PARTIAL = "PARTIAL"
        const val COMPLETE = "COMPLETE"
    }
}
