package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the `focus_overrides` table.
 *
 * **Index name** — explicitly set to `idx_focus_overrides_overridden_at` to
 * match the name created by `initSchema` in `database.ts`. Room validates
 * index names; using the default generated name would cause an
 * `IllegalStateException` on the first open of the hybrid-app database.
 *
 * **PK note**: The auto-generated [Long] PK exists only at the database layer;
 * `dbLogFocusOverride` in `database.ts` does not expose it. It enables
 * `COUNT(o.id)` in the analytics JOIN queries in [FocusSessionDao].
 *
 * ARCHITECTURE.md listed the PK as possibly `String` or composite; the actual
 * schema in `database.ts` uses `INTEGER PRIMARY KEY AUTOINCREMENT`.
 */
@Entity(
    tableName = "focus_overrides",
    indices = [Index(value = ["overridden_at"], name = "idx_focus_overrides_overridden_at")],
)
data class FocusOverrideEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "task_id")
    val taskId: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    @ColumnInfo(name = "overridden_at")
    val overriddenAt: String,

    @ColumnInfo(name = "reason")
    val reason: String?,
)
