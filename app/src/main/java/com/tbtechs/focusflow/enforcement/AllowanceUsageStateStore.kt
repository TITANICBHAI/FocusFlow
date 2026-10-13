package com.tbtechs.focusflow.enforcement

import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray

/** Reads allowance targets and owns the existing active-session preference keys. */
internal class AllowanceUsageStateStore(
    private val prefs: SharedPreferences,
    private val ledger: AllowanceLedger,
) {
    /**
     * Upgrade cleanup for allowance bookkeeping removed in Batch 4. These values
     * are no longer read or migrated; current checkpoints use the active-session
     * marker keys below.
     */
    fun clearRetiredPreferenceKeys() {
        prefs.edit()
            .remove(LEGACY_USAGE_STATS_SYNC)
            .remove(LEGACY_ACTIVE_SESSION_OPEN_AT)
            .apply()
    }

    fun configuredTargets(): List<AllowanceUsageTarget> {
        val raw = prefs.getString(
            AppBlockerAccessibilityService.PREF_DAILY_ALLOWANCE_CONFIG,
            null,
        ) ?: return emptyList()
        return try {
            val entries = JSONArray(raw)
            buildList {
                for (index in 0 until entries.length()) {
                    val item = entries.optJSONObject(index) ?: continue
                    val packageName = item.optString("packageName", "")
                        .takeIf(String::isNotBlank) ?: continue
                    val mode = item.optString("mode", AllowanceLedger.MODE_COUNT)
                    when (mode) {
                        AllowanceLedger.MODE_COUNT,
                        AllowanceLedger.MODE_TIME_BUDGET ->
                            add(AllowanceUsageTarget(packageName, mode))
                        AllowanceLedger.MODE_INTERVAL ->
                            add(
                                AllowanceUsageTarget(
                                    packageName = packageName,
                                    mode = mode,
                                    windowStartMs = ledger.usage(packageName).windowStartMs,
                                    windowMs = item.optInt("intervalHours", 1)
                                        .coerceAtLeast(1).toLong() * HOUR_MS,
                                ),
                            )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun targetForPackage(packageName: String): AllowanceUsageTarget? =
        configuredTargets().firstOrNull {
            it.packageName.equals(packageName, ignoreCase = true)
        }

    fun persistMarker(packageName: String, atMs: Long) {
        prefs.edit()
            .putString(AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_PKG, packageName)
            .putLong(AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_LAST_CHECKPOINT_MS, atMs)
            .apply()
    }

    fun clearMarker(packageName: String) {
        if (prefs.getString(AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_PKG, null)
                ?.equals(packageName, ignoreCase = true) != true
        ) return
        prefs.edit()
            .remove(AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_PKG)
            .remove(AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_LAST_CHECKPOINT_MS)
            .remove(AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_END_MS)
            .apply()
    }

    fun setActiveSessionEnd(packageName: String, endAtMs: Long) {
        if (prefs.getString(AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_PKG, null)
                ?.equals(packageName, ignoreCase = true) == true
        ) {
            prefs.edit()
                .putLong(AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_END_MS, endAtMs)
                .apply()
        }
    }

    fun recoverPersistedCheckpoint(nowMs: Long) {
        val packageName = prefs.getString(
            AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_PKG,
            null,
        ) ?: return
        val checkpointAtMs = prefs.getLong(
            AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_LAST_CHECKPOINT_MS,
            0L,
        )
        if (checkpointAtMs <= 0L) return
        val recoveredEndMs = AllowanceUsageTimeAccounting.recoverableEnd(
            checkpointAtMs = checkpointAtMs,
            nowMs = nowMs,
            maximumRecoveryMs = AllowanceUsageCoordinator.MAX_RECOVERABLE_CHECKPOINT_GAP_MS,
        )
        val config = try {
            val array = JSONArray(
                prefs.getString(
                    AppBlockerAccessibilityService.PREF_DAILY_ALLOWANCE_CONFIG,
                    "[]",
                ) ?: "[]",
            )
            (0 until array.length())
                .mapNotNull { array.optJSONObject(it) }
                .firstOrNull {
                    it.optString("packageName", "").equals(packageName, ignoreCase = true)
                }
        } catch (_: Exception) {
            null
        } ?: return
        val mode = config.optString("mode", AllowanceLedger.MODE_COUNT)
        if (mode != AllowanceLedger.MODE_TIME_BUDGET && mode != AllowanceLedger.MODE_INTERVAL) {
            prefs.edit()
                .putLong(AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_LAST_CHECKPOINT_MS, nowMs)
                .apply()
            return
        }

        val windowStart = ledger.usage(packageName).windowStartMs
        var recovered = 0L
        AllowanceUsageTimeAccounting.splitAtLocalMidnight(
            startMs = checkpointAtMs,
            endMs = recoveredEndMs,
        ).forEach { slice ->
            recovered += ledger.recoverCheckpointTime(
                packageName = packageName,
                mode = mode,
                today = slice.localDate,
                windowStartMs = windowStart,
                elapsedMs = slice.durationMs,
                maximumRecoveryMs = slice.durationMs,
            )
        }
        prefs.edit()
            .putLong(AppBlockerAccessibilityService.PREF_ACTIVE_SESSION_LAST_CHECKPOINT_MS, nowMs)
            .apply()
        Log.i(
            TAG,
            "recovered checkpoint pkg=$packageName gapMs=${(nowMs - checkpointAtMs).coerceAtLeast(0L)} recoveredMs=$recovered",
        )
    }

    private companion object {
        const val HOUR_MS = 60 * 60 * 1_000L
        const val TAG = "AllowanceUsage"
        const val LEGACY_USAGE_STATS_SYNC = "daily_allowance_usage_stats_sync"
        const val LEGACY_ACTIVE_SESSION_OPEN_AT = "active_session_open_at_ms"
    }
}
