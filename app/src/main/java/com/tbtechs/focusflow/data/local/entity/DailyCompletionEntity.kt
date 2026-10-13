package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for the `daily_completions` table.
 *
 * Stores a per-day snapshot of how many tasks were completed vs scheduled —
 * the source of truth for the streak counter and the weekly completion-rate
 * analytics chart.
 *
 * **PK note**: The primary key is [date] — a local-calendar date string
 * (`"YYYY-MM-DD"` in the **device's timezone**), NOT a UTC ISO timestamp.
 * Writers must derive this via the same local-date logic as `localDateString()`
 * in `database.ts`:
 *
 * ```kotlin
 * LocalDate.now(ZoneId.systemDefault()).toString()   // "2025-04-07"
 * ```
 *
 * Using an ISO UTC timestamp here would break the streak for users in UTC-N
 * timezones whose evening tasks cross the UTC midnight boundary.
 */
@Entity(tableName = "daily_completions")
data class DailyCompletionEntity(

    /**
     * Local-calendar date string "YYYY-MM-DD" (device timezone).
     * Acts as a natural unique key — one row per local day.
     */
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String,

    /** Number of tasks with status `'completed'` on this day. */
    @ColumnInfo(name = "completed", defaultValue = "0")
    val completed: Int,

    /** Total number of tasks scheduled for this day. */
    @ColumnInfo(name = "total", defaultValue = "0")
    val total: Int,
)
