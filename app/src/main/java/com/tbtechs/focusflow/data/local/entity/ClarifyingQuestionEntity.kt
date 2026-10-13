package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** System-generated prompt for an inconsistency between ratings and observed patterns. */
@Entity(
    tableName = "clarifying_questions",
    indices = [Index(value = ["asked_at"], name = "idx_cq_asked_at")],
)
data class ClarifyingQuestionEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "question_type") val questionType: String,
    @ColumnInfo(name = "date_of_concern") val dateOfConcern: String,
    @ColumnInfo(name = "context_json") val contextJson: String,
    @ColumnInfo(name = "asked_at") val askedAt: String,
    @ColumnInfo(name = "response") val response: String?,
    @ColumnInfo(name = "responded_at") val respondedAt: String?,
)