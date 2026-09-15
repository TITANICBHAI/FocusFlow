package com.tbtechs.focusflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tbtechs.focusflow.data.local.entity.WeeklyInsightEntity

@Dao
interface WeeklyInsightDao {
    @Query("SELECT insight_id FROM weekly_insights ORDER BY week_start DESC LIMIT :limit")
    suspend fun getRecentInsightIds(limit: Int): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun record(insight: WeeklyInsightEntity)
}