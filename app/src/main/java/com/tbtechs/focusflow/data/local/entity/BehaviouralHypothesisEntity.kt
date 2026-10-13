package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Stores one answer to a cold-start behavioural seed question. */
@Entity(tableName = "behavioural_hypotheses")
data class BehaviouralHypothesisEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "answer_text") val answerText: String,
    @ColumnInfo(name = "answer_package") val answerPackage: String?,
    @ColumnInfo(name = "created_at") val createdAt: String,
)