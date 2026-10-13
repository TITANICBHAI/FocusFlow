package com.tbtechs.focusflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tbtechs.focusflow.data.local.entity.FindingAcknowledgementEntity

@Dao
interface FindingAcknowledgementDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(ack: FindingAcknowledgementEntity)

    @Query("SELECT * FROM finding_acknowledgements WHERE finding_id = :findingId ORDER BY created_at DESC, id DESC")
    suspend fun getForFinding(findingId: String): List<FindingAcknowledgementEntity>
}