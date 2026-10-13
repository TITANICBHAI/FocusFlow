package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage_pipeline_state")
data class UsagePipelineStateEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "cutover_date") val cutoverDate: String?,
    @ColumnInfo(name = "pipeline_version") val pipelineVersion: Int,
    @ColumnInfo(name = "shadow_started_on") val shadowStartedOn: String,
) {
    companion object {
        const val SINGLETON_ID = 1
        const val CURRENT_PIPELINE_VERSION = 1
    }
}
