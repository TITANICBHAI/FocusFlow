package com.tbtechs.focusflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tbtechs.focusflow.data.local.entity.ClarifyingQuestionEntity

@Dao
interface ClarifyingQuestionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(question: ClarifyingQuestionEntity)

    @Query("SELECT * FROM clarifying_questions WHERE response IS NULL ORDER BY asked_at DESC LIMIT 1")
    suspend fun getUnanswered(): ClarifyingQuestionEntity?

    @Query("UPDATE clarifying_questions SET response = :response, responded_at = :now WHERE id = :id")
    suspend fun answer(id: String, response: String, now: String)

    @Query("SELECT COUNT(*) FROM clarifying_questions WHERE asked_at >= :sinceIso")
    suspend fun countAskedSince(sinceIso: String): Int

    @Query("DELETE FROM clarifying_questions WHERE response IS NOT NULL AND responded_at < :cutoffIso")
    suspend fun deleteAnsweredBefore(cutoffIso: String)
}