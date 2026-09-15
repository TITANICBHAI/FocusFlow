package com.tbtechs.focusflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tbtechs.focusflow.data.local.entity.AchievementEntity

@Dao
interface AchievementDao {

    /**
     * Returns all earned achievement IDs ordered by earn date ascending.
     * Maps to `dbGetEarnedAchievementIds` in `database.ts`.
     */
    @Query("SELECT id FROM achievements ORDER BY earned_at ASC")
    suspend fun getEarnedIds(): List<String>

    /**
     * Persists a batch of newly earned achievements.
     * Maps to `dbRecordEarnedAchievements` in `database.ts`.
     *
     * `INSERT OR IGNORE` matches the original `INSERT OR IGNORE INTO achievements`
     * SQL — calling this twice for the same IDs is safe and produces no error.
     * Room wraps `@Insert(List)` in a single transaction automatically.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun recordEarned(achievements: List<AchievementEntity>)
}
