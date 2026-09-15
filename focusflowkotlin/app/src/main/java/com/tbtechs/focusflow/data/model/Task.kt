package com.tbtechs.focusflow.data.model

import kotlinx.serialization.Serializable

// ─── String-enum aliases (kept as String for forward-compatibility) ────────────

/** Mirrors `TaskStatus` in `types.ts`. */
typealias TaskStatus = String      // 'scheduled' | 'active' | 'completed' | 'skipped' | 'overdue'

/** Mirrors `TaskPriority` in `types.ts`. */
typealias TaskPriority = String    // 'low' | 'medium' | 'high' | 'critical'

/** Mirrors `ReminderType` in `types.ts`. */
typealias ReminderType = String    // 'pre-start' | 'at-start' | 'post-start'

// ─── Reminder ─────────────────────────────────────────────────────────────────

/**
 * Mirrors the `Reminder` interface in `types.ts`.
 *
 * Stored as a JSON-encoded element inside [Task.reminders] — the serializer is
 * used by [TaskRepository] when converting between [Task] and [TaskEntity].
 *
 * [offsetMinutes]: negative = before task start, positive = after start.
 * [notifId]: Android notification ID string, present after the reminder fires.
 */
@Serializable
data class Reminder(
    val id: String,
    val taskId: String,
    val offsetMinutes: Int,
    val type: ReminderType,
    val notifId: String? = null,
)

// ─── Task ─────────────────────────────────────────────────────────────────────

/**
 * Domain model for a scheduled task. Mirrors the `Task` interface in `types.ts`
 * field-for-field, including the three-way semantics of [focusAllowedPackages]:
 *
 *   null         → use the global `allowedInFocus` setting (TS: `undefined`)
 *   emptyList()  → all apps are allowed during this task's focus session (TS: `[]`)
 *   listOf(…)    → only the listed package names are allowed
 *
 * [tags] and [reminders] are typed collections; [TaskRepository] serializes
 * them to/from JSON when reading and writing [TaskEntity].
 *
 * Timestamps ([startTime], [endTime], [createdAt], [updatedAt]) are ISO-8601
 * strings (UTC), matching the JS storage format.
 */
@Serializable
data class Task(
    val id: String,
    val title: String,
    val description: String? = null,
    val startTime: String,
    val endTime: String,
    val durationMinutes: Int,
    val status: TaskStatus,
    val priority: TaskPriority,
    val tags: List<String> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val color: String,
    val focusMode: Boolean,
    /**
     * null         → use global `allowedInFocus` (TS: `undefined`)
     * emptyList()  → all apps allowed
     * listOf(…)    → specific allow-list
     *
     * Must NOT be silently coerced — null and emptyList() have different
     * enforcement semantics in AppBlockerAccessibilityService.
     */
    val focusAllowedPackages: List<String>? = null,
    val createdAt: String,
    val updatedAt: String,
)
