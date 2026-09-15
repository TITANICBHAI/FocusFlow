package com.tbtechs.focusflow.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import com.tbtechs.focusflow.enforcement.NetworkBlockerVpnService
import com.tbtechs.focusflow.enforcement.VpnPolicyCoordinator
import com.tbtechs.focusflow.widget.FocusFlowWidget
import com.tbtechs.focusflow.data.model.AllowedAppPreset
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.DailyAllowanceEntry
import com.tbtechs.focusflow.data.model.RecurringBlockSchedule
import org.json.JSONArray
import org.json.JSONObject

/**
 * Snapshot of the allowance state that must be read under one native lock.
 *
 * This replaces the bridge's WritableNativeMap while keeping its field names and
 * null/default behavior available to the ViewModel layer.
 */
data class AllowanceSnapshot(
    val usageJson: String?,
    val configJson: String?,
    val activeSessionPackage: String?,
    val activeSessionEndMs: Long,
)

/**
 * Thrown when a user-facing operation is gated by the configured session PIN.
 *
 * The old bridge rejected these calls with the PIN_REQUIRED error code. Direct
 * Kotlin callers receive a typed SecurityException instead.
 */
class SessionPinRequiredException(message: String) : SecurityException(message)

/**
 * SettingsRepository
 *
 * Converted from SharedPrefsModule. It is the typed persistence facade for the
 * native enforcement state stored in the legacy "focusday_prefs" namespace.
 *
 * The repository intentionally retains the old key names and the distinction
 * between asynchronous apply() writes and synchronous commit() snapshots.
 */
class SettingsRepository(context: Context) {

    companion object {
        private const val TAG = "SettingsRepository"
        private const val PREF_PIN_HASH = "session_pin_hash"

        private const val KEY_FOCUS_ACTIVE = "focus_active"
        private const val KEY_FOCUS_BREAK_UNTIL_MS = "focus_break_until_ms"
        private const val KEY_ALLOWED_PACKAGES = "allowed_packages"
        private const val KEY_TASK_ID = "task_id"
        private const val KEY_TASK_NAME = "task_name"
        private const val KEY_TASK_END_MS = "task_end_ms"
        private const val KEY_TASK_START_MS = "task_start_ms"
        private const val KEY_TASK_COLOR = "task_color"
        private const val KEY_NEXT_TASK_NAME = "next_task_name"
        private const val KEY_TASK_DURATION_MS = "task_duration_ms"
        private const val KEY_TASK_LAST_WRITTEN_MS = "task_last_written_ms"

        private const val KEY_STANDALONE_ACTIVE = "standalone_block_active"
        private const val KEY_STANDALONE_PACKAGES = "standalone_blocked_packages"
        private const val KEY_STANDALONE_UNTIL_MS = "standalone_block_until_ms"
        private const val KEY_STANDALONE_VPN_PACKAGES = "net_block_standalone_vpn_packages"

        private const val KEY_SCHEDULE_VPN_PACKAGES = "net_block_schedule_vpn_pkgs"
        private const val KEY_NETWORK_BLOCK_ENABLED = "net_block_enabled"
        private const val KEY_NETWORK_BLOCK_VPN = "net_block_vpn"
        private const val KEY_VPN_SELECTED_PACKAGES = "vpn_selected_packages"
        private const val KEY_EXPLICIT_VPN_PACKAGES = "net_block_explicit_packages"

        private const val KEY_DAILY_ALLOWANCE_USED = "daily_allowance_used"
        private const val KEY_DAILY_ALLOWANCE_CONFIG = "daily_allowance_config"
        private const val KEY_RECURRING_BLOCK_SCHEDULES = "recurring_block_schedules"
        private const val KEY_MORNING_DIGEST_ENABLED = "morning_digest_enabled"
        private const val KEY_ACHIEVEMENT_NOTIFICATIONS_ENABLED = "achievement_notifications_enabled"
        private const val KEY_PATTERN_INSIGHT_NOTIFICATIONS_ENABLED = "pattern_insight_notifications_enabled"
        private const val KEY_RESCHEDULE_NOTIFICATIONS_ENABLED = "reschedule_notifications_enabled"
        private const val KEY_BLOCK_SUGGESTION_ENABLED = "block_suggestion_enabled"
        private const val KEY_WEEK_AHEAD_ENABLED = "week_ahead_enabled"
        private const val KEY_TEMPTATION_SPIKE_ENABLED = "temptation_spike_enabled"
        private const val KEY_TEMPTATION_SPIKE_THRESHOLD = "temptation_spike_threshold"
        private const val KEY_BED_TIME = "bed_time"
        private const val KEY_PRODUCTIVE_WINDOW_NUDGE_ENABLED = "productive_window_nudge_enabled"
        private const val KEY_LAST_SESSION_RESULT_BY_TASK_ID = "last_session_result_by_task_id"
        private const val KEY_SHOWN_PATTERN_INSIGHT_IDS = "shown_pattern_insight_ids"
        private const val KEY_LAST_SHOWN_DEBRIEF_SESSION_ID = "last_shown_debrief_session_id"
        private const val KEY_TASK_REMINDERS_ENABLED = "task_reminders_enabled"
        private const val KEY_DEFAULT_DURATION_MINUTES = "default_duration_minutes"
        private const val KEY_AUTO_FOCUS_ENABLED = "auto_focus_enabled"
        private const val KEY_ALLOWED_FOCUS_PACKAGES = "allowed_focus_packages"
        private const val KEY_POMODORO_ENABLED = "pomodoro_enabled"
        private const val KEY_POMODORO_WORK_MINUTES = "pomodoro_work_minutes"
        private const val KEY_POMODORO_BREAK_MINUTES = "pomodoro_break_minutes"
        private const val KEY_FOCUS_DEFENSE_HINT_DISMISSED = "focus_defense_hint_dismissed"
        private const val KEY_LOCAL_ANALYTICS_NOTICE_DISMISSED = "local_analytics_notice_dismissed"
        private const val KEY_REPORT_NOTE_PREFIX = "report_note_"
        private const val KEY_LAUNCHER_DOCK_PACKAGES = "launcher_dock_packages"
        private const val KEY_LAUNCHER_HIDDEN_PACKAGES = "launcher_hidden_packages"
        private const val KEY_DRAWER_HIDDEN_PACKAGES = "drawer_hidden_packages"
        private const val KEY_LAUNCHER_THEME = "launcher_theme"
        private const val KEY_FOCUS_TOOL_PACKAGES = "focus_tool_packages"
        private const val KEY_LAUNCHER_LOCK_DURING_STANDALONE =
            "launcher_lock_during_standalone"
        private const val KEY_LAUNCHER_BLOCK_UNINSTALL = "launcher_block_uninstall"
        private const val KEY_LAUNCHER_CLOCK_STYLE = "launcher_clock_style"

        private const val KEY_DAILY_TASKS_DONE = "daily_tasks_done"
        private const val KEY_DAILY_TASKS_TOTAL = "daily_tasks_total"
        private const val KEY_DAILY_FOCUS_MINS = "daily_focus_mins"
        private const val KEY_STREAK_DAYS = "streak_days"

        private const val KEY_ACTIVE_SESSION_PACKAGE = "active_session_pkg"
        private const val KEY_ACTIVE_SESSION_END_MS = "active_session_end_ms"
    }

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences
        get() = appContext.getSharedPreferences(
            AppBlockerAccessibilityService.PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    /** Small typed escape hatch for profile metadata that is not enforcement state. */
    fun getString(key: String): String? = prefs.getString(key, null)

    fun putString(key: String, value: String) {
        if (!prefs.edit().putString(key, value).commit()) {
            throw IllegalStateException("WRITE_FAILED: settings string commit() returned false")
        }
    }

    /**
     * Tells native enforcement whether task focus mode is active.
     *
     * Ending a focus session is PIN-gated; starting one is not.
     */
    suspend fun setFocusActive(active: Boolean, pinHash: String? = null) {
        if (!active) {
            requireValidSessionPin(
                pinHash,
                "A session PIN is set — supply the correct PIN hash to end the session",
            )
        }
        val editor = prefs.edit().putBoolean(KEY_FOCUS_ACTIVE, active)
        if (!active) editor.remove(AppBlockerAccessibilityService.PREF_CURRENT_VIOLATION_APP)
        editor.apply()
        requestVpnSync()
    }

    /** Pauses or resumes blocking for an intentional Pomodoro break. */
    suspend fun setFocusBreak(active: Boolean, untilMs: Long) {
        val editor = prefs.edit()
        if (active) {
            editor
                .putBoolean(KEY_FOCUS_ACTIVE, false)
                .putLong(KEY_FOCUS_BREAK_UNTIL_MS, untilMs)
        } else {
            editor
                .putBoolean(KEY_FOCUS_ACTIVE, true)
                .remove(KEY_FOCUS_BREAK_UNTIL_MS)
        }
        editor.apply()
        requestVpnSync()
    }

    /** Compatibility overload for the bridge's JavaScript number timestamp. */
    suspend fun setFocusBreak(active: Boolean, untilMs: Double) =
        setFocusBreak(active, untilMs.toLong())

    /** Clears a pending break without changing the focus_active flag. */
    suspend fun clearFocusBreak() {
        prefs.edit().remove(KEY_FOCUS_BREAK_UNTIL_MS).apply()
    }

    suspend fun getFocusBreakUntilMs(): Long =
        prefs.getLong(KEY_FOCUS_BREAK_UNTIL_MS, 0L)

    /** Replaces the complete task-focus allow-list. */
    suspend fun setAllowedPackages(packages: List<String>) {
        prefs.edit().putString(KEY_ALLOWED_PACKAGES, packages.toJsonArrayString()).apply()
        requestVpnSync()
    }

    /**
     * Stores the active task details used by boot recovery and the widget.
     */
    suspend fun setActiveTask(
        taskId: String,
        name: String,
        endMs: Long,
        nextName: String?,
    ) {
        val now = System.currentTimeMillis()
        val durationMs = (endMs - now).coerceAtLeast(0L)
        val previousId = prefs.getString(KEY_TASK_ID, "") ?: ""
        val editor = prefs.edit()
            .putString(KEY_TASK_ID, taskId)
            .putString(KEY_TASK_NAME, name)
            .putLong(KEY_TASK_END_MS, endMs)
            .putString(KEY_NEXT_TASK_NAME, nextName?.takeIf { it.isNotBlank() })

        if (previousId != taskId) {
            editor.remove(KEY_TASK_START_MS)
        }

        editor
            .putLong(KEY_TASK_DURATION_MS, durationMs)
            .putLong(KEY_TASK_LAST_WRITTEN_MS, now)
            .apply()
        pushWidgetUpdate()
    }

    /** Compatibility overload for the bridge's JavaScript number timestamp. */
    suspend fun setActiveTask(
        taskId: String,
        name: String,
        endMs: Double,
        nextName: String?,
    ) = setActiveTask(taskId, name, endMs.toLong(), nextName)

    /** Writes or clears the active task's widget accent color. */
    suspend fun setActiveTaskColor(colorHex: String) {
        val editor = prefs.edit()
        if (colorHex.isBlank()) {
            editor.remove(KEY_TASK_COLOR)
        } else {
            editor.putString(KEY_TASK_COLOR, colorHex)
        }
        editor.apply()
        pushWidgetUpdate()
    }

    /**
     * Persists the active task's wall-clock start time without moving an existing
     * start time backwards for the same task.
     */
    suspend fun setActiveTaskStartMs(taskId: String, startMs: Long) {
        val currentTaskId = prefs.getString(KEY_TASK_ID, "") ?: ""
        val currentStartMs = prefs.getLong(KEY_TASK_START_MS, 0L)
        if (taskId != currentTaskId || currentStartMs <= 0L || startMs <= 0L) {
            val editor = prefs.edit()
            if (startMs <= 0L) {
                editor.remove(KEY_TASK_START_MS)
            } else {
                editor.putLong(KEY_TASK_START_MS, startMs)
            }
            editor.apply()
            pushWidgetUpdate()
        }
    }

    /** Compatibility overload for the bridge's JavaScript number timestamp. */
    suspend fun setActiveTaskStartMs(taskId: String, startMs: Double) =
        setActiveTaskStartMs(taskId, startMs.toLong())

    /**
     * Clears active task fields only. Focus and block flags are deliberately
     * untouched.
     */
    suspend fun clearActiveTask() {
        prefs.edit()
            .remove(KEY_TASK_ID)
            .remove(KEY_TASK_NAME)
            .remove(KEY_TASK_END_MS)
            .remove(KEY_TASK_START_MS)
            .remove(KEY_TASK_COLOR)
            .remove(KEY_NEXT_TASK_NAME)
            .apply()
        pushWidgetUpdate()
    }

    /**
     * Atomically publishes the complete focus snapshot and enforces the
     * user-facing PIN when an active session is ended.
     */
    suspend fun publishFocusSnapshot(
        active: Boolean,
        taskId: String?,
        taskName: String?,
        taskEndMs: Long,
        taskColor: String?,
        allowedPackages: List<String>?,
        nextTaskName: String?,
        pinHash: String?,
    ) {
        publishFocusSnapshotImpl(
            active = active,
            taskId = taskId,
            taskName = taskName,
            taskEndMs = taskEndMs,
            taskColor = taskColor,
            allowedPackages = allowedPackages,
            nextTaskName = nextTaskName,
            pinHash = pinHash,
            enforceSessionPin = true,
        )
    }

    /** Compatibility overload for the bridge's JavaScript number timestamp. */
    suspend fun publishFocusSnapshot(
        active: Boolean,
        taskId: String?,
        taskName: String?,
        taskEndMs: Double,
        taskColor: String?,
        allowedPackages: List<String>?,
        nextTaskName: String?,
        pinHash: String?,
    ) = publishFocusSnapshot(
        active,
        taskId,
        taskName,
        taskEndMs.toLong(),
        taskColor,
        allowedPackages,
        nextTaskName,
        pinHash,
    )

    /**
     * Publishes an authorized system transition without checking the session PIN.
     * This is intentionally separate from publishFocusSnapshot.
     */
    suspend fun publishFocusSnapshotInternal(
        active: Boolean,
        taskId: String?,
        taskName: String?,
        taskEndMs: Long,
        taskColor: String?,
        allowedPackages: List<String>?,
        nextTaskName: String?,
    ) {
        publishFocusSnapshotImpl(
            active = active,
            taskId = taskId,
            taskName = taskName,
            taskEndMs = taskEndMs,
            taskColor = taskColor,
            allowedPackages = allowedPackages,
            nextTaskName = nextTaskName,
            pinHash = null,
            enforceSessionPin = false,
        )
    }

    /** Compatibility overload for the bridge's JavaScript number timestamp. */
    suspend fun publishFocusSnapshotInternal(
        active: Boolean,
        taskId: String?,
        taskName: String?,
        taskEndMs: Double,
        taskColor: String?,
        allowedPackages: List<String>?,
        nextTaskName: String?,
    ) = publishFocusSnapshotInternal(
        active,
        taskId,
        taskName,
        taskEndMs.toLong(),
        taskColor,
        allowedPackages,
        nextTaskName,
    )

    private fun publishFocusSnapshotImpl(
        active: Boolean,
        taskId: String?,
        taskName: String?,
        taskEndMs: Long,
        taskColor: String?,
        allowedPackages: List<String>?,
        nextTaskName: String?,
        pinHash: String?,
        enforceSessionPin: Boolean,
    ) {
        try {
            if (enforceSessionPin && !active) {
                requireValidSessionPin(
                    pinHash,
                    "A session PIN is set — supply the correct PIN hash to end the session",
                )
            }

            val now = System.currentTimeMillis()
            val editor = prefs.edit()
            if (active && taskId != null) {
                editor
                    .putBoolean(KEY_FOCUS_ACTIVE, true)
                    .putString(KEY_TASK_ID, taskId)
                    .putString(KEY_TASK_NAME, taskName ?: "")
                    .putLong(KEY_TASK_END_MS, taskEndMs)
                    .putString(KEY_TASK_COLOR, taskColor ?: "")
                    .putString(
                        KEY_ALLOWED_PACKAGES,
                        allowedPackages?.toJsonArrayString() ?: "[]",
                    )
                    .putString(KEY_NEXT_TASK_NAME, nextTaskName?.takeIf { it.isNotBlank() })
                    .putLong(KEY_TASK_DURATION_MS, (taskEndMs - now).coerceAtLeast(0L))
                    .putLong(KEY_TASK_LAST_WRITTEN_MS, now)
            } else {
                editor
                    .putBoolean(KEY_FOCUS_ACTIVE, false)
                    .remove(KEY_TASK_ID)
                    .remove(KEY_TASK_NAME)
                    .remove(KEY_TASK_END_MS)
                    .remove(KEY_TASK_COLOR)
                    .remove(KEY_ALLOWED_PACKAGES)
                    .remove(KEY_NEXT_TASK_NAME)
                    .remove(KEY_TASK_DURATION_MS)
                    .remove(KEY_TASK_START_MS)
                    .remove(KEY_TASK_LAST_WRITTEN_MS)
            }

            if (!editor.commit()) {
                Log.e(TAG, "[NATIVE_PREFS_COMMIT_FAILED] publishFocusSnapshot")
                throw IllegalStateException("PREFS_WRITE_FAILED: commit() returned false")
            }

            Log.d(TAG, "[NATIVE_PREFS_OK] publishFocusSnapshot active=$active")
            requestVpnSync()
            pushWidgetUpdate()
        } catch (error: SessionPinRequiredException) {
            throw error
        } catch (error: Exception) {
            throw IllegalStateException("PREFS_ERROR: ${error.message}", error)
        }
    }

    suspend fun pushWidgetUpdate() {
        FocusFlowWidget.pushWidgetUpdate(appContext)
    }

    /** Controls standalone blocking and its optional early-cancel PIN gate. */
    suspend fun setStandaloneBlock(
        active: Boolean,
        packages: List<String>,
        untilMs: Long,
        pinHash: String? = null,
    ) {
        if (!active && prefs.getLong(KEY_STANDALONE_UNTIL_MS, 0L) > System.currentTimeMillis()) {
            requireValidSessionPin(
                pinHash,
                "A session PIN is set — supply the correct PIN hash to end the standalone block early",
            )
        }
        prefs.edit()
            .putBoolean(KEY_STANDALONE_ACTIVE, active)
            .putString(KEY_STANDALONE_PACKAGES, packages.toJsonArrayString())
            .putLong(KEY_STANDALONE_UNTIL_MS, untilMs)
            .apply()
        requestVpnSync()
        pushWidgetUpdate()
    }

    /** Compatibility overload for the bridge's JavaScript number timestamp. */
    suspend fun setStandaloneBlock(
        active: Boolean,
        packages: List<String>,
        untilMs: Double,
        pinHash: String? = null,
    ) = setStandaloneBlock(active, packages, untilMs.toLong(), pinHash)

    /**
     * Atomically publishes standalone state. Inactive snapshots clear the
     * package and expiry values and persist an empty VPN package list.
     */
    suspend fun publishStandaloneSnapshot(
        active: Boolean,
        packages: List<String>,
        untilMs: Long,
        pinHash: String?,
        vpnPackages: List<String>?,
    ) {
        try {
            if (!active && prefs.getLong(KEY_STANDALONE_UNTIL_MS, 0L) > System.currentTimeMillis()) {
                requireValidSessionPin(
                    pinHash,
                    "A session PIN is set — supply the correct PIN hash to end the standalone block early",
                )
            }

            val editor = prefs.edit()
            if (active) {
                editor
                    .putBoolean(KEY_STANDALONE_ACTIVE, true)
                    .putString(KEY_STANDALONE_PACKAGES, packages.toJsonArrayString())
                    .putLong(KEY_STANDALONE_UNTIL_MS, untilMs)
                    .putString(
                        KEY_STANDALONE_VPN_PACKAGES,
                        vpnPackages?.toJsonArrayString() ?: "[]",
                    )
            } else {
                editor
                    .putBoolean(KEY_STANDALONE_ACTIVE, false)
                    .putString(KEY_STANDALONE_PACKAGES, "[]")
                    .putLong(KEY_STANDALONE_UNTIL_MS, 0L)
                    .putString(KEY_STANDALONE_VPN_PACKAGES, "[]")
            }

            if (!editor.commit()) {
                Log.e(TAG, "[NATIVE_PREFS_COMMIT_FAILED] publishStandaloneSnapshot")
                throw IllegalStateException("PREFS_WRITE_FAILED: commit() returned false")
            }

            Log.d(TAG, "[NATIVE_PREFS_OK] publishStandaloneSnapshot active=$active")
            requestVpnSync()
            pushWidgetUpdate()
        } catch (error: SessionPinRequiredException) {
            throw error
        } catch (error: Exception) {
            throw IllegalStateException("PREFS_ERROR: ${error.message}", error)
        }
    }

    /** Compatibility overload for the bridge's JavaScript number timestamp. */
    suspend fun publishStandaloneSnapshot(
        active: Boolean,
        packages: List<String>,
        untilMs: Double,
        pinHash: String?,
        vpnPackages: List<String>?,
    ) = publishStandaloneSnapshot(
        active,
        packages,
        untilMs.toLong(),
        pinHash,
        vpnPackages,
    )

    suspend fun setAlwaysBlockActive(active: Boolean, packages: List<String>) {
        prefs.edit()
            .putBoolean(AppBlockerAccessibilityService.PREF_ALWAYS_BLOCK, active)
            .putString(
                AppBlockerAccessibilityService.PREF_ALWAYS_BLOCK_PKGS,
                packages.toJsonArrayString(),
            )
            .apply()
    }

    /**
     * Stores recurring blocks and mirrors them to the greyout schedule consumed
     * by the accessibility service. Existing user-created greyout windows
     * (entries without a scheduleId) are preserved.
     */
    suspend fun setRecurringBlockSchedules(schedules: List<RecurringBlockSchedule>) {
        val recurringJson = JSONArray().apply {
            schedules.forEach { schedule ->
                put(JSONObject().apply {
                    put("id", schedule.id)
                    put("packages", JSONArray(schedule.packages))
                    put("startHour", schedule.startHour)
                    put("endHour", schedule.endHour)
                    put("daysOfWeek", JSONArray(schedule.daysOfWeek))
                    put("enabled", schedule.enabled)
                })
            }
        }.toString()

        val existingWindows = parseJsonArrayObjects(
            prefs.getString("greyout_schedule", "[]") ?: "[]",
        ).filter { it.optString("scheduleId").isBlank() }
        val scheduleWindows = schedules
            .filter { it.enabled && it.packages.isNotEmpty() }
            .flatMap { schedule ->
                schedule.packages.map { packageName ->
                    JSONObject().apply {
                        put("pkg", packageName)
                        put("startHour", schedule.startHour.coerceIn(0, 23))
                        put("startMin", 0)
                        put("endHour", schedule.endHour.coerceIn(0, 23))
                        put("endMin", 0)
                        put(
                            "days",
                            JSONArray(schedule.daysOfWeek.map { day -> (day.coerceIn(0, 6) + 1) }),
                        )
                        put("scheduleId", schedule.id)
                    }
                }
            }

        if (!prefs.edit()
                .putString(KEY_RECURRING_BLOCK_SCHEDULES, recurringJson)
                .putString(
                    "greyout_schedule",
                    JSONArray(existingWindows + scheduleWindows).toString(),
                )
                .commit()
        ) {
            throw IllegalStateException("WRITE_FAILED: recurring schedule commit() returned false")
        }
    }

    suspend fun publishScheduleVpnSnapshot(packagesJson: String) {
        if (!prefs.edit().putString(KEY_SCHEDULE_VPN_PACKAGES, packagesJson).commit()) {
            throw IllegalStateException("WRITE_FAILED: commit() returned false")
        }
        VpnPolicyCoordinator.requestSync(appContext)
    }

    suspend fun setDailyAllowancePackages(packages: List<String>) {
        prefs.edit()
            .putString(
                AppBlockerAccessibilityService.PREF_DAILY_ALLOWANCE_PKGS,
                packages.toJsonArrayString(),
            )
            .apply()
    }

    suspend fun setBlockedWords(words: List<String>) {
        prefs.edit()
            .putString(AppBlockerAccessibilityService.PREF_BLOCKED_WORDS, words.toJsonArrayString())
            .apply()
    }

    suspend fun setLauncherDockPackages(packagesJson: String) {
        prefs.edit().putString(KEY_LAUNCHER_DOCK_PACKAGES, packagesJson).apply()
    }

    suspend fun setSystemGuardEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(AppBlockerAccessibilityService.PREF_SYSTEM_GUARD_ENABLED, enabled)
            .apply()
    }

    suspend fun setBlockInstallActionsEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(AppBlockerAccessibilityService.PREF_BLOCK_INSTALL_ACTIONS, enabled)
            .apply()
    }

    suspend fun setBlockYoutubeShortsEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(AppBlockerAccessibilityService.PREF_BLOCK_YT_SHORTS, enabled)
            .apply()
    }

    suspend fun setBlockInstagramReelsEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(AppBlockerAccessibilityService.PREF_BLOCK_IG_REELS, enabled)
            .apply()
    }

    suspend fun setNetworkBlockEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_NETWORK_BLOCK_ENABLED, enabled)
            .putBoolean(KEY_NETWORK_BLOCK_VPN, enabled)
            .apply()
        requestVpnSync()
    }

    suspend fun setVpnSelectedPackages(packagesJson: String) {
        prefs.edit()
            .putString(KEY_VPN_SELECTED_PACKAGES, packagesJson)
            .putString(KEY_EXPLICIT_VPN_PACKAGES, packagesJson)
            .apply()
        requestVpnSync()
    }

    suspend fun setDailyAllowanceConfig(configJson: String) {
        prefs.edit().putString(KEY_DAILY_ALLOWANCE_CONFIG, configJson).apply()
        appContext.sendBroadcast(
            Intent(AppBlockerAccessibilityService.ACTION_ALLOWANCE_CONFIG_CHANGED).apply {
                `package` = appContext.packageName
            },
        )
    }

    /**
     * Atomically updates standalone enforcement and allowance configuration.
     * The allowance-change broadcast is sent only after the shared preference
     * commit succeeds.
     */
    suspend fun publishStandaloneAndAllowanceSnapshot(
        active: Boolean,
        packages: List<String>,
        untilMs: Long,
        allowanceEntries: List<DailyAllowanceEntry>,
        pinHash: String?,
    ) {
        if (!active && prefs.getLong(KEY_STANDALONE_UNTIL_MS, 0L) > System.currentTimeMillis()) {
            requireValidSessionPin(
                pinHash,
                "A session PIN is set — supply the correct PIN to end the standalone block early",
            )
        }
        val allowanceJson = JSONArray().apply {
            allowanceEntries.forEach { entry ->
                put(JSONObject().apply {
                    put("package", entry.packageName)
                    put("dailyAllowanceMs", entry.dailyAllowanceMs)
                    put("mode", entry.mode)
                    put("countPerDay", entry.countPerDay)
                    put("budgetMinutes", entry.budgetMinutes)
                    put("intervalMinutes", entry.intervalMinutes)
                    put("intervalHours", entry.intervalHours)
                })
            }
        }.toString()
        val editor = prefs.edit()
        if (active) {
            editor
                .putBoolean(KEY_STANDALONE_ACTIVE, true)
                .putString(KEY_STANDALONE_PACKAGES, packages.toJsonArrayString())
                .putLong(KEY_STANDALONE_UNTIL_MS, untilMs)
        } else {
            editor
                .putBoolean(KEY_STANDALONE_ACTIVE, false)
                .putString(KEY_STANDALONE_PACKAGES, "[]")
                .putLong(KEY_STANDALONE_UNTIL_MS, 0L)
        }
        editor.putString(KEY_DAILY_ALLOWANCE_CONFIG, allowanceJson)
        if (!editor.commit()) {
            throw IllegalStateException("WRITE_FAILED: standalone and allowance commit() returned false")
        }
        appContext.sendBroadcast(
            Intent(AppBlockerAccessibilityService.ACTION_ALLOWANCE_CONFIG_CHANGED).apply {
                `package` = appContext.packageName
            },
        )
        requestVpnSync()
        pushWidgetUpdate()
    }

    suspend fun setNotificationPreferences(settings: AppSettings) {
        val results = JSONObject().apply {
            settings.lastSessionResultByTaskId.forEach { (taskId, result) -> put(taskId, result) }
        }
        prefs.edit()
            .putBoolean(KEY_MORNING_DIGEST_ENABLED, settings.morningDigestEnabled)
            .putBoolean(KEY_ACHIEVEMENT_NOTIFICATIONS_ENABLED, settings.achievementNotificationsEnabled)
            .putBoolean(KEY_PATTERN_INSIGHT_NOTIFICATIONS_ENABLED, settings.patternInsightNotificationsEnabled)
            .putBoolean(KEY_RESCHEDULE_NOTIFICATIONS_ENABLED, settings.rescheduleNotificationsEnabled)
            .putBoolean(KEY_BLOCK_SUGGESTION_ENABLED, settings.blockSuggestionEnabled)
            .putBoolean(KEY_WEEK_AHEAD_ENABLED, settings.weekAheadEnabled)
            .putBoolean(KEY_TEMPTATION_SPIKE_ENABLED, settings.temptationSpikeEnabled)
            .putInt(KEY_TEMPTATION_SPIKE_THRESHOLD, settings.temptationSpikeThreshold)
            .putString(KEY_BED_TIME, settings.bedTime)
            .putBoolean(KEY_PRODUCTIVE_WINDOW_NUDGE_ENABLED, settings.productiveWindowNudgeEnabled)
            .putString(KEY_LAST_SESSION_RESULT_BY_TASK_ID, results.toString())
            .putString(KEY_SHOWN_PATTERN_INSIGHT_IDS, JSONArray(settings.shownPatternInsightIds).toString())
            .putBoolean(KEY_TASK_REMINDERS_ENABLED, settings.taskRemindersEnabled)
            .putInt(KEY_DEFAULT_DURATION_MINUTES, settings.defaultDurationMinutes.coerceIn(5, 480))
            .putBoolean(KEY_AUTO_FOCUS_ENABLED, settings.autoFocusEnabled)
            .putString(KEY_ALLOWED_FOCUS_PACKAGES, settings.allowedFocusPackages.toJsonArrayString())
            .putBoolean(KEY_POMODORO_ENABLED, settings.pomodoroEnabled)
            .putInt(KEY_POMODORO_WORK_MINUTES, settings.pomodoroWorkMinutes.coerceIn(1, 180))
            .putInt(KEY_POMODORO_BREAK_MINUTES, settings.pomodoroBreakMinutes.coerceIn(1, 60))
            .putBoolean(KEY_FOCUS_DEFENSE_HINT_DISMISSED, settings.focusDefenseHintDismissed)
            .putBoolean(KEY_LOCAL_ANALYTICS_NOTICE_DISMISSED, settings.localAnalyticsNoticeDismissed)
            .apply {
                if (settings.lastShownDebriefSessionId == null) {
                    remove(KEY_LAST_SHOWN_DEBRIEF_SESSION_ID)
                } else {
                    putInt(KEY_LAST_SHOWN_DEBRIEF_SESSION_ID, settings.lastShownDebriefSessionId)
                }
            }
            .apply()
    }

    /**
     * Reads the settings fields owned by this repository from the existing
     * enforcement preference namespace.
     */
    suspend fun readAppSettings(): AppSettings {
        val resultMap = mutableMapOf<String, String>()
        runCatching {
            val obj = JSONObject(prefs.getString(KEY_LAST_SESSION_RESULT_BY_TASK_ID, "{}") ?: "{}")
            obj.keys().forEach { key -> resultMap[key] = obj.optString(key) }
        }
        return AppSettings(
            alwaysBlockPackages = parseStringArray(
                prefs.getString(AppBlockerAccessibilityService.PREF_ALWAYS_BLOCK_PKGS, "[]"),
            ),
            alwaysBlockEnabled = prefs.getBoolean(AppBlockerAccessibilityService.PREF_ALWAYS_BLOCK, false),
            blockedWords = parseStringArray(
                prefs.getString(AppBlockerAccessibilityService.PREF_BLOCKED_WORDS, "[]"),
            ),
            standaloneBlockActive = prefs.getBoolean(KEY_STANDALONE_ACTIVE, false),
            standaloneBlockPackages = parseStringArray(prefs.getString(KEY_STANDALONE_PACKAGES, "[]")),
            standaloneBlockUntilMs = prefs.getLong(KEY_STANDALONE_UNTIL_MS, 0L),
            launcherTheme = prefs.getString(KEY_LAUNCHER_THEME, "glassy") ?: "glassy",
            launcherWallpaperUri = prefs.getString("launcher_wallpaper_uri", null),
            focusToolPackages = parseStringArray(
                prefs.getString(KEY_FOCUS_TOOL_PACKAGES, "[]"),
            ),
            launcherHiddenPackages = parseStringArray(
                prefs.getString(KEY_LAUNCHER_HIDDEN_PACKAGES, "[]"),
            ),
            launcherLockDuringStandalone = prefs.getBoolean(
                KEY_LAUNCHER_LOCK_DURING_STANDALONE,
                true,
            ),
            launcherPresets = parsePresets(prefs.getString("allowed_app_presets", "[]")),
            dailyAllowanceConfigJson = prefs.getString(KEY_DAILY_ALLOWANCE_CONFIG, null),
            recurringBlockSchedules = parseRecurringSchedules(
                prefs.getString(KEY_RECURRING_BLOCK_SCHEDULES, "[]"),
            ),
            networkBlockEnabled = prefs.getBoolean(KEY_NETWORK_BLOCK_ENABLED, false),
            systemGuardEnabled = prefs.getBoolean(AppBlockerAccessibilityService.PREF_SYSTEM_GUARD_ENABLED, false),
            morningDigestEnabled = prefs.getBoolean(KEY_MORNING_DIGEST_ENABLED, true),
            achievementNotificationsEnabled = prefs.getBoolean(
                KEY_ACHIEVEMENT_NOTIFICATIONS_ENABLED,
                true,
            ),
            patternInsightNotificationsEnabled = prefs.getBoolean(
                KEY_PATTERN_INSIGHT_NOTIFICATIONS_ENABLED,
                false,
            ),
            rescheduleNotificationsEnabled = prefs.getBoolean(KEY_RESCHEDULE_NOTIFICATIONS_ENABLED, true),
            blockSuggestionEnabled = prefs.getBoolean(KEY_BLOCK_SUGGESTION_ENABLED, true),
            weekAheadEnabled = prefs.getBoolean(KEY_WEEK_AHEAD_ENABLED, true),
            temptationSpikeEnabled = prefs.getBoolean(KEY_TEMPTATION_SPIKE_ENABLED, false),
            temptationSpikeThreshold = prefs.getInt(KEY_TEMPTATION_SPIKE_THRESHOLD, 8),
            bedTime = prefs.getString(KEY_BED_TIME, "22:00") ?: "22:00",
            productiveWindowNudgeEnabled = prefs.getBoolean(
                KEY_PRODUCTIVE_WINDOW_NUDGE_ENABLED,
                false,
            ),
            lastSessionResultByTaskId = resultMap,
            shownPatternInsightIds = parseStringArray(
                prefs.getString(KEY_SHOWN_PATTERN_INSIGHT_IDS, "[]"),
            ),
            lastShownDebriefSessionId = if (prefs.contains(KEY_LAST_SHOWN_DEBRIEF_SESSION_ID)) {
                prefs.getInt(KEY_LAST_SHOWN_DEBRIEF_SESSION_ID, 0)
            } else {
                null
            },
            taskRemindersEnabled = prefs.getBoolean(KEY_TASK_REMINDERS_ENABLED, true),
            defaultDurationMinutes = prefs.getInt(KEY_DEFAULT_DURATION_MINUTES, 60).coerceIn(5, 480),
            autoFocusEnabled = prefs.getBoolean(KEY_AUTO_FOCUS_ENABLED, false),
            allowedFocusPackages = parseStringArray(prefs.getString(KEY_ALLOWED_FOCUS_PACKAGES, "[]")),
            pomodoroEnabled = prefs.getBoolean(KEY_POMODORO_ENABLED, false),
            pomodoroWorkMinutes = prefs.getInt(KEY_POMODORO_WORK_MINUTES, 25).coerceIn(1, 180),
            pomodoroBreakMinutes = prefs.getInt(KEY_POMODORO_BREAK_MINUTES, 5).coerceIn(1, 60),
            focusDefenseHintDismissed = prefs.getBoolean(KEY_FOCUS_DEFENSE_HINT_DISMISSED, false),
            localAnalyticsNoticeDismissed = prefs.getBoolean(KEY_LOCAL_ANALYTICS_NOTICE_DISMISSED, false),
        )
    }

    fun getReportNote(reportKey: String): String =
        prefs.getString(KEY_REPORT_NOTE_PREFIX + reportKey.sanitizedPreferenceKey(), "") ?: ""

    fun setReportNote(reportKey: String, note: String) {
        prefs.edit()
            .putString(KEY_REPORT_NOTE_PREFIX + reportKey.sanitizedPreferenceKey(), note.take(4_000))
            .apply()
    }

    /** Generic overlay/config string setter; an empty value removes the key. */
    suspend fun getLong(key: String): Long = prefs.getLong(key, 0L)

    suspend fun getAllowanceSnapshot(): AllowanceSnapshot =
        synchronized(AppBlockerAccessibilityService.ALLOWANCE_USAGE_LOCK) {
            AllowanceSnapshot(
                usageJson = prefs.getString(KEY_DAILY_ALLOWANCE_USED, null),
                configJson = prefs.getString(KEY_DAILY_ALLOWANCE_CONFIG, null),
                activeSessionPackage = prefs.getString(KEY_ACTIVE_SESSION_PACKAGE, null),
                activeSessionEndMs = prefs.getLong(KEY_ACTIVE_SESSION_END_MS, 0L),
            )
        }

    suspend fun isDebuggable(): Boolean =
        (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    suspend fun setDailyStats(
        tasksDone: Int,
        tasksTotal: Int,
        focusMins: Int,
        streakDays: Int,
    ) {
        prefs.edit()
            .putInt(KEY_DAILY_TASKS_DONE, tasksDone.coerceAtLeast(0))
            .putInt(KEY_DAILY_TASKS_TOTAL, tasksTotal.coerceAtLeast(0))
            .putInt(KEY_DAILY_FOCUS_MINS, focusMins.coerceAtLeast(0))
            .putInt(KEY_STREAK_DAYS, streakDays.coerceAtLeast(0))
            .apply()
        pushWidgetUpdate()
    }

    suspend fun setLauncherHiddenPackages(packagesJson: String) {
        prefs.edit()
            .putString(KEY_LAUNCHER_HIDDEN_PACKAGES, packagesJson)
            .putString(KEY_DRAWER_HIDDEN_PACKAGES, packagesJson)
            .apply()
    }

    suspend fun setLauncherWallpaperUri(uri: String?) {
        val editor = prefs.edit()
        if (uri.isNullOrBlank()) editor.remove("launcher_wallpaper_uri")
        else editor.putString("launcher_wallpaper_uri", uri)
        editor.apply()
    }

    suspend fun setLauncherPresets(presets: List<AllowedAppPreset>) {
        val json = JSONArray().apply {
            presets.forEach { preset ->
                put(JSONObject().apply {
                    put("id", preset.id)
                    put("name", preset.name)
                    put("packages", JSONArray(preset.packages))
                })
            }
        }.toString()
        prefs.edit().putString("allowed_app_presets", json).apply()
    }

    suspend fun setLauncherTheme(theme: String) {
        prefs.edit()
            .putString(KEY_LAUNCHER_THEME, if (theme == "classic") "classic" else "glassy")
            .apply()
    }

    suspend fun setFocusToolPackages(packagesJson: String) {
        prefs.edit().putString(KEY_FOCUS_TOOL_PACKAGES, packagesJson).apply()
    }

    suspend fun setLauncherLockDuringStandalone(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LAUNCHER_LOCK_DURING_STANDALONE, enabled).apply()
    }

    suspend fun setLauncherBlockUninstall(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LAUNCHER_BLOCK_UNINSTALL, enabled).apply()
    }

    suspend fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = appContext.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        return resolveInfo?.activityInfo?.packageName == appContext.packageName
    }

    suspend fun setLauncherClockStyle(style: String) {
        prefs.edit().putString(KEY_LAUNCHER_CLOCK_STYLE, style).apply()
    }

    suspend fun resetDailyAllowanceUsage(packageName: String?) {
        synchronized(AppBlockerAccessibilityService.ALLOWANCE_USAGE_LOCK) {
            val editor = prefs.edit()
            if (packageName == null) {
                editor.putString(AppBlockerAccessibilityService.PREF_DAILY_ALLOWANCE_USED, "{}")
            } else {
                val usedJson = prefs.getString(
                    AppBlockerAccessibilityService.PREF_DAILY_ALLOWANCE_USED,
                    "{}",
                ) ?: "{}"
                try {
                    val obj = JSONObject(usedJson)
                    obj.remove(packageName)
                    editor.putString(
                        AppBlockerAccessibilityService.PREF_DAILY_ALLOWANCE_USED,
                        obj.toString(),
                    )
                } catch (_: Exception) {
                    editor.putString(
                        AppBlockerAccessibilityService.PREF_DAILY_ALLOWANCE_USED,
                        "{}",
                    )
                }
            }
            editor.apply()
        }
    }

    private fun requestVpnSync() {
        NetworkBlockerVpnService.requestSync(appContext)
    }

    private fun requireValidSessionPin(pinHash: String?, message: String) {
        val storedHash = prefs.getString(PREF_PIN_HASH, null)
        if (storedHash.isNullOrBlank()) return
        if (pinHash.isNullOrBlank() ||
            !storedHash.equals(pinHash.lowercase(), ignoreCase = true)
        ) {
            throw SessionPinRequiredException(message)
        }
    }

    private fun List<String>.toJsonArrayString(): String = JSONArray(this).toString()

private fun String.sanitizedPreferenceKey(): String =
    replace(Regex("[^A-Za-z0-9_-]"), "_").take(120)

    private fun parseStringArray(json: String?): List<String> =
        runCatching {
            val array = JSONArray(json ?: "[]")
            (0 until array.length()).mapNotNull { index ->
                array.optString(index).takeIf { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())

    private fun parseJsonArrayObjects(json: String): List<JSONObject> =
        runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        }.getOrDefault(emptyList())

    private fun parseRecurringSchedules(json: String?): List<RecurringBlockSchedule> =
        runCatching {
            val array = JSONArray(json ?: "[]")
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val days = item.optJSONArray("daysOfWeek") ?: JSONArray()
                RecurringBlockSchedule(
                    id = item.optString("id"),
                    packages = parseStringArray(item.optJSONArray("packages")?.toString()),
                    startHour = item.optInt("startHour").coerceIn(0, 23),
                    endHour = item.optInt("endHour").coerceIn(0, 23),
                    daysOfWeek = (0 until days.length()).map { days.optInt(it) },
                    enabled = item.optBoolean("enabled", true),
                )
            }
        }.getOrDefault(emptyList())

    private fun parsePresets(json: String?): List<AllowedAppPreset> =
        runCatching {
            val array = JSONArray(json ?: "[]")
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val packages = item.optJSONArray("packages") ?: JSONArray()
                val id = item.optString("id").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                AllowedAppPreset(
                    id = id,
                    name = item.optString("name"),
                    packages = parseStringArray(packages.toString()),
                )
            }
        }.getOrDefault(emptyList())
}