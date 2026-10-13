package com.tbtechs.focusflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tbtechs.focusflow.data.local.entity.FocusOverrideEntity

@Dao
interface FocusOverrideDao {

    // ── Writes ────────────────────────────────────────────────────────────────

    /**
     * Maps to `dbLogFocusOverride`. Inserts a new override log row.
     * The auto-generated [FocusOverrideEntity.id] is set by the database;
     * callers pass `id = 0` (the default).
     */
    @Insert
    suspend fun insertOverride(override: FocusOverrideEntity)

    // ── Count queries ─────────────────────────────────────────────────────────

    /**
     * Returns the number of overrides recorded since [cutoff] (ISO timestamp).
     * Maps to `dbGetTodayOverrideCount`.
     *
     * [cutoff] should be the start-of-local-day ISO timestamp, computed by
     * [FocusSessionRepository.getTodayOverrideCount] — NOT a UTC midnight,
     * matching the JS `startOfDay.setHours(0, 0, 0, 0)` pattern.
     */
    @Query("SELECT COUNT(*) FROM focus_overrides WHERE overridden_at >= :cutoff")
    suspend fun getOverrideCountFrom(cutoff: String): Int

    /**
     * Returns the number of overrides within [[startISO], [endISO]] inclusive.
     * Maps to `dbGetOverrideCountInRange`.
     */
    @Query("""
        SELECT COUNT(*) FROM focus_overrides
        WHERE overridden_at >= :startISO AND overridden_at <= :endISO
    """)
    suspend fun getOverrideCountInRange(startISO: String, endISO: String): Int
}
