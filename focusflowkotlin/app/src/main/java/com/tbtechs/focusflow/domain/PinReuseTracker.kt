package com.tbtechs.focusflow.domain

import android.content.SharedPreferences
import java.util.Calendar

/**
 * PinReuseTracker
 *
 * Direct port of src/utils/pinReuseTracker.ts.
 *
 * Tracks how many times today the user has chosen "keep the same password" at a
 * PIN-rotation prompt. Once MAX_DAILY_REUSES is reached for the day, the
 * "keep same" option must be disabled and the user must set a new custom or
 * auto-generated password.
 *
 * ─── SharedPreferences keys (identical to the TypeScript implementation) ─────
 *
 *   pin_reuse_count_focus      — reuse count for Focus Session PIN rotations
 *   pin_reuse_date_focus       — ISO date (YYYY-MM-DD) the count was last updated
 *   pin_reuse_count_alwayson   — reuse count for Always-On Enforcement PIN
 *   pin_reuse_date_alwayson    — ISO date the count was last updated
 *
 * ─── Architecture doc discrepancy — important ─────────────────────────────────
 *
 *   ARCHITECTURE.md §3.9 and the task spec describe this as a "last-5-hash reuse
 *   check". The actual source (pinReuseTracker.ts) does NOT track a rolling window
 *   of previous hashes. It tracks a daily "keep same" usage count capped at
 *   MAX_DAILY_REUSES = 3. This file ports the actual source behavior, not the
 *   architecture doc description. If a hash-history check is needed, it must be
 *   implemented separately and is not present in the reference source.
 *
 * ─── Logging ─────────────────────────────────────────────────────────────────
 *   No PIN, hash, or salt is logged at any verbosity level.
 */
object PinReuseTracker {

    /** Maximum number of "keep same" reuses permitted per day. Mirrors pinReuseTracker.ts. */
    const val MAX_DAILY_REUSES = 3

    /**
     * Which PIN type to track. Corresponds to ReuseTrackerKey in the TypeScript source.
     *   FOCUS    → "focus"    → keys: pin_reuse_count_focus,    pin_reuse_date_focus
     *   ALWAYSON → "alwayson" → keys: pin_reuse_count_alwayson, pin_reuse_date_alwayson
     */
    enum class ReuseTrackerKey { FOCUS, ALWAYSON }

    /**
     * Returned by [getPinReuseInfo]. Mirrors the { count, canReuse } object from TS.
     *
     * @property count    Number of "keep same" reuses already used today (0 if date reset).
     * @property canReuse True if another reuse is permitted (count < MAX_DAILY_REUSES).
     */
    data class ReuseInfo(val count: Int, val canReuse: Boolean)

    // ─── Key helpers ─────────────────────────────────────────────────────────

    /** Returns the SharedPreferences key for the daily count. Matches TS countKey(). */
    fun countKey(key: ReuseTrackerKey): String = "pin_reuse_count_${key.name.lowercase()}"

    /** Returns the SharedPreferences key for the stored ISO date. Matches TS dateKey(). */
    fun dateKey(key: ReuseTrackerKey): String = "pin_reuse_date_${key.name.lowercase()}"

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns the current reuse count and whether another reuse is permitted today.
     *
     * If the stored date is not today, the count is treated as 0 (day boundary reset).
     * Returns { count=0, canReuse=true } on any read failure, matching the TS catch branch.
     *
     * Mirrors getPinReuseInfo() from pinReuseTracker.ts.
     */
    fun getPinReuseInfo(prefs: SharedPreferences, key: ReuseTrackerKey): ReuseInfo {
        val today = todayISO()
        return try {
            val storedDate  = prefs.getString(dateKey(key),  null)
            val storedCount = prefs.getString(countKey(key), null)
            val count = if (storedDate == today) {
                maxOf(0, storedCount?.toIntOrNull() ?: 0)
            } else {
                0
            }
            ReuseInfo(count, count < MAX_DAILY_REUSES)
        } catch (_: Exception) {
            ReuseInfo(0, true)
        }
    }

    /**
     * Records one "keep same" reuse for today.
     * Reads the current count, increments it, and writes both the new count and today's date.
     *
     * Mirrors recordPinReuse() from pinReuseTracker.ts.
     */
    fun recordPinReuse(prefs: SharedPreferences, key: ReuseTrackerKey) {
        val today   = todayISO()
        val current = getPinReuseInfo(prefs, key)
        prefs.edit()
            .putString(countKey(key), (current.count + 1).toString())
            .putString(dateKey(key),  today)
            .apply()
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    /**
     * Returns today's date as "YYYY-MM-DD", matching the TS todayISO() implementation.
     * Uses the device's default locale/timezone via Calendar — same behaviour as JS Date.
     */
    private fun todayISO(): String {
        val cal = Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,   // Calendar.MONTH is 0-indexed
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }
}
