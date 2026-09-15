package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A permanently earned achievement.
 *
 * This maps to the hybrid app's existing `achievements` table, so the entity
 * intentionally contains only the two persisted columns.
 */
@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "earned_at")
    val earnedAt: String,
)