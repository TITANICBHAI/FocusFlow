package com.tbtechs.focusflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tbtechs.focusflow.data.local.entity.DayRatingEntity

@Dao
interface DayRatingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rating: DayRatingEntity)

    @Query("SELECT * FROM day_ratings WHERE date = :date LIMIT 1")
    suspend fun getForDate(date: String): DayRatingEntity?

    @Query("SELECT * FROM day_ratings WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getForDateRange(startDate: String, endDate: String): List<DayRatingEntity>

    @Query("SELECT COUNT(*) FROM day_ratings")
    suspend fun count(): Int

    @Query("SELECT date FROM day_ratings ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentRatedDates(limit: Int): List<String>
}