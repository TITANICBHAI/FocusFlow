package com.tbtechs.focusflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tbtechs.focusflow.data.local.entity.BehaviouralHypothesisEntity

@Dao
interface BehaviouralHypothesisDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(hypothesis: BehaviouralHypothesisEntity)

    @Query("SELECT * FROM behavioural_hypotheses ORDER BY created_at ASC")
    suspend fun getAll(): List<BehaviouralHypothesisEntity>

    @Query("SELECT answer_package FROM behavioural_hypotheses WHERE question_id = 'reflex_app' AND answer_package IS NOT NULL LIMIT 1")
    suspend fun getReflexAppPackage(): String?
}