package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the `tasks` table.
 *
 * **Index names** — explicitly set to the names created by `initSchema` in
 * `database.ts` (`idx_tasks_start_time`, etc.). Room schema validation checks
 * index names by default; mismatched names would cause an
 * `IllegalStateException` on first open of the hybrid-app database.
 *
 * Storage notes:
 *  - [tags]      — JSON-encoded `List<String>`;  never null; default `"[]"`.
 *  - [reminders] — JSON-encoded `List<Reminder>`; never null; default `"[]"`.
 *  - [focusMode] — stored as INTEGER 0/1; Room converts Boolean automatically.
 *  - [focusAllowedPackages] — NULLABLE. Three distinct states:
 *      null   → use the global `allowedInFocus` setting (TS: `undefined`)
 *      `"[]"` → all apps allowed during this task's focus session
 *      `"[…]"` → only the listed packages are allowed
 *    null and `"[]"` are NOT interchangeable; repositories must preserve
 *    this distinction end-to-end.
 */
@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["start_time"], name = "idx_tasks_start_time"),
        Index(value = ["status"],     name = "idx_tasks_status"),
        Index(value = ["status", "end_time"], name = "idx_tasks_status_end"),
    ],
)
data class TaskEntity(

    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String?,

    @ColumnInfo(name = "start_time")
    val startTime: String,

    @ColumnInfo(name = "end_time")
    val endTime: String,

    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int,

    /** One of: `'scheduled' | 'active' | 'completed' | 'skipped' | 'overdue'`. */
    @ColumnInfo(name = "status", defaultValue = "scheduled")
    val status: String,

    /** One of: `'low' | 'medium' | 'high' | 'critical'`. */
    @ColumnInfo(name = "priority", defaultValue = "medium")
    val priority: String,

    /** JSON-encoded `List<String>`. Never null; default `"[]"`. */
    @ColumnInfo(name = "tags", defaultValue = "[]")
    val tags: String,

    /** JSON-encoded `List<Reminder>`. Never null; default `"[]"`. */
    @ColumnInfo(name = "reminders", defaultValue = "[]")
    val reminders: String,

    @ColumnInfo(name = "color", defaultValue = "#6366f1")
    val color: String,

    /** Stored as INTEGER 0/1; Room converts Boolean automatically. */
    @ColumnInfo(name = "focus_mode", defaultValue = "0")
    val focusMode: Boolean,

    /**
     * JSON-encoded `List<String>` or NULL.
     * null  → use global `allowedInFocus` (mirrors TS `undefined`)
     * `"[]"` → all apps allowed during this task's focus session
     * `"[…]"` → specific allow-list
     *
     * Column was added via ALTER TABLE migration — pre-migration rows have NULL.
     * Must NEVER be silently coerced to `"[]"`.
     */
    @ColumnInfo(name = "focus_allowed_packages")
    val focusAllowedPackages: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String,
)
