package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** User's 1–10 end-of-day self-rating for one local calendar date. */
@Entity(tableName = "day_ratings")
data class DayRatingEntity(
    @PrimaryKey @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "rating") val rating: Int,
    @ColumnInfo(name = "context_tag") val contextTag: String?,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "app_tags", defaultValue = "[]") val appTags: String,
    @ColumnInfo(name = "word_tags", defaultValue = "[]") val wordTags: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)