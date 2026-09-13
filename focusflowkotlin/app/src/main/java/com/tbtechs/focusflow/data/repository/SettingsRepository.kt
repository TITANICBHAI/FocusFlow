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
        prefs.edit().putBoolean(KEY_FOCUS_ACTIVE, active).apply()
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

    /** Generic overlay/config string setter; an empty value removes the key. */
    suspend fun putString(key: String, value: String) {
        if (value.isEmpty()) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, value).apply()
        }
    }

    suspend fun getString(key: String): String? = prefs.getString(key, null)

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
}