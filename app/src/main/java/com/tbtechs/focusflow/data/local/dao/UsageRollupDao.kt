package com.tbtechs.focusflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tbtechs.focusflow.data.local.entity.UsagePipelineStateEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupAppDayEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupDayEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupSessionEntity

@Dao
interface UsageRollupDao {
    @Query("SELECT * FROM usage_pipeline_state WHERE id = 1")
    suspend fun getPipelineState(): UsagePipelineStateEntity?

    @Query(
        """
        INSERT OR IGNORE INTO usage_pipeline_state
            (id, cutover_date, pipeline_version, shadow_started_on)
        VALUES (1, NULL, 1, :shadowStartedOn)
        """,
    )
    suspend fun ensurePipelineState(shadowStartedOn: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPipelineState(state: UsagePipelineStateEntity)

    @Query(
        """
        UPDATE usage_pipeline_state
        SET cutover_date = :cutoverDate
        WHERE id = 1
          AND cutover_date IS NULL
          AND shadow_started_on <= :shadowStartedBy
        """,
    )
    suspend fun setCutoverDateIfShadowMature(
        cutoverDate: String,
        shadowStartedBy: String,
    ): Int

    @Query("SELECT * FROM usage_rollup_day WHERE date = :date")
    suspend fun getDay(date: String): UsageRollupDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(day: UsageRollupDayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppDays(rows: List<UsageRollupAppDayEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessions(rows: List<UsageRollupSessionEntity>)

    @Query("SELECT * FROM usage_rollup_app_day WHERE date = :date ORDER BY foreground_ms DESC")
    suspend fun getAppDays(date: String): List<UsageRollupAppDayEntity>

    @Query(
        "SELECT * FROM usage_rollup_app_day WHERE date BETWEEN :startDate AND :endDate " +
            "ORDER BY date, foreground_ms DESC",
    )
    suspend fun getAppDays(startDate: String, endDate: String): List<UsageRollupAppDayEntity>

    @Query("SELECT * FROM usage_rollup_session WHERE local_date BETWEEN :startDate AND :endDate ORDER BY started_at_ms")
    suspend fun getSessions(startDate: String, endDate: String): List<UsageRollupSessionEntity>

    @Query("SELECT * FROM usage_rollup_day WHERE date BETWEEN :startDate AND :endDate ORDER BY date")
    suspend fun getDays(startDate: String, endDate: String): List<UsageRollupDayEntity>

    @Query("DELETE FROM usage_rollup_app_day WHERE date = :date")
    suspend fun deleteAppDays(date: String)

    @Query("DELETE FROM usage_rollup_session WHERE local_date = :date")
    suspend fun deleteSessions(date: String)

    @Query("DELETE FROM usage_rollup_day WHERE date = :date")
    suspend fun deleteDay(date: String)

    @Query("SELECT COUNT(*) FROM usage_rollup_day WHERE date = :date")
    suspend fun countDay(date: String): Int
}
