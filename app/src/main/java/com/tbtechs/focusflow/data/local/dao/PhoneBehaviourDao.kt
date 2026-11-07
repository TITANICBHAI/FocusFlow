package com.tbtechs.focusflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tbtechs.focusflow.data.local.entity.BlockedAttemptDayEntity
import com.tbtechs.focusflow.data.local.entity.PhoneDayRollupEntity
import com.tbtechs.focusflow.data.local.entity.PhoneFirstAppDayEntity

@Dao
interface PhoneBehaviourDao {
    @Query("SELECT * FROM phone_day_rollup WHERE date BETWEEN :startDate AND :endDate ORDER BY date")
    suspend fun getDays(startDate: String, endDate: String): List<PhoneDayRollupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(row: PhoneDayRollupEntity)

    @Query("SELECT MIN(date) FROM phone_day_rollup")
    suspend fun earliestPhoneDate(): String?

    @Query("DELETE FROM phone_day_rollup WHERE date < :cutoff")
    suspend fun deleteDaysBefore(cutoff: String)

    @Query("SELECT * FROM phone_first_app_day WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getFirstApps(startDate: String, endDate: String): List<PhoneFirstAppDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFirstApps(rows: List<PhoneFirstAppDayEntity>)

    @Query("DELETE FROM phone_first_app_day WHERE date = :date")
    suspend fun deleteFirstApps(date: String)

    @Query("DELETE FROM phone_first_app_day WHERE date < :cutoff")
    suspend fun deleteFirstAppsBefore(cutoff: String)

    @Query("SELECT * FROM blocked_attempt_day WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getBlocked(startDate: String, endDate: String): List<BlockedAttemptDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlocked(rows: List<BlockedAttemptDayEntity>)

    @Query("DELETE FROM blocked_attempt_day WHERE date < :cutoff")
    suspend fun deleteBlockedBefore(cutoff: String)
}
