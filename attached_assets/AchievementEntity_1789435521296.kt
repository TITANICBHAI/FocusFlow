package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for the `achievements` table.
 *
 * The table already exists in the raw SQLite file created by the hybrid app's
 * `initSchema` (`id TEXT PRIMARY KEY, earned_at TEXT NOT NULL`). MIGRATION_2_3
 * in [FocusFlowDatabase] creates it with `CREATE TABLE IF NOT EXISTS` so it is
 * safe to run against databases that already contain the table.
 *
 * Rows are only ever inserted, never updated or deleted — achievement earning
 * is permanent. `INSERT OR IGNORE` in [AchievementDao.recordEarned] prevents
 * duplicate rows if `syncAchievements` is called twice for the same ID.
 */
@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** ISO-8601 UTC timestamp of when the achievement was earned. */
    @ColumnInfo(name = "earned_at")
    val earnedAt: String,
)
