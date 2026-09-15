package com.tbtechs.focusflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tbtechs.focusflow.data.local.entity.AchievementEntity

@Dao
interface AchievementDao {

    /** Returns earned IDs in their original earning order. */
    @Query("SELECT id FROM achievements ORDER BY earned_at ASC")
    suspend fun getEarnedIds(): List<String>

    /** INSERT OR IGNORE preserves one-time earning semantics. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun recordEarned(achievements: List<AchievementEntity>)
}