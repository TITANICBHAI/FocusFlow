package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the `focus_sessions` table.
 *
 * **Index names** — explicitly set to `idx_focus_sessions_task_active` and
 * `idx_focus_sessions_started_at` to match the names created by `initSchema`
 * in `database.ts`. Room validates index names; using the default generated
 * names (e.g. `index_focus_sessions_task_id_is_active`) would cause an
 * `IllegalStateException` on the first open of the hybrid-app database.
 *
 * **PK note**: The TypeScript `FocusSession` domain interface has **no `id` field**.
 * The auto-generated [Long] PK exists only at the database layer and is stripped
 * when mapping to the domain model.
 *
 * [isActive] is stored as INTEGER 0/1; Room converts Boolean automatically.
 * [allowedPackages] is a JSON-encoded `List<String>`.
 */
@Entity(
    tableName = "focus_sessions",
    indices = [
        Index(value = ["task_id", "is_active"], name = "idx_focus_sessions_task_active"),
        Index(value = ["started_at"],           name = "idx_focus_sessions_started_at"),
        Index(value = ["is_active", "id"],      name = "idx_focus_sessions_active"),
    ],
)
data class FocusSessionEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "task_id")
    val taskId: String,

    @ColumnInfo(name = "started_at")
    val startedAt: String,

    /** NULL while the session is still active. */
    @ColumnInfo(name = "ended_at")
    val endedAt: String? = null,

    /** Stored as INTEGER 0/1. */
    @ColumnInfo(name = "is_active", defaultValue = "1")
    val isActive: Boolean,

    /** JSON-encoded `List<String>` of package names. Never null; default `"[]"`. */
    @ColumnInfo(name = "allowed_packages", defaultValue = "[]")
    val allowedPackages: String,
)
