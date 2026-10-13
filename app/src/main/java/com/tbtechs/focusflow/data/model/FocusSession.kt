package com.tbtechs.focusflow.data.model

import kotlinx.serialization.Serializable

/**
 * Domain model for an active or recently-ended focus session.
 * Mirrors the `FocusSession` interface in `types.ts` field-for-field.
 *
 * **No `id` field**: The TypeScript domain type has no `id`. The auto-generated
 * integer PK in `FocusSessionEntity` exists only at the database layer (for
 * `ORDER BY id DESC` disambiguation and analytics JOINs). [FocusSessionRepository]
 * strips it when mapping entity → domain.
 *
 * [allowedPackages]: Android package names allowed to run without triggering
 * the block overlay during this session. An empty list means all apps are
 * allowed (the global `allowedInFocus` list was empty when the session started).
 *
 * [isActive]: mirrors `is_active` INTEGER 0/1. The JS `dbGetActiveFocusSession`
 * always returns `isActive: true` (it filters `WHERE is_active = 1`); the Kotlin
 * type carries the field faithfully so it can represent completed sessions too
 * (e.g. in [FocusSessionRepository.getRecentCompletedSession]).
 */
@Serializable
data class FocusSession(
    val taskId: String,
    val startedAt: String,
    val isActive: Boolean,
    val allowedPackages: List<String> = emptyList(),
)
