package com.tbtechs.focusflow.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.tbtechs.focusflow.enforcement.AllowanceLedger
import com.tbtechs.focusflow.enforcement.AllowanceLedgerProvider
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import com.tbtechs.focusflow.enforcement.DayRatingNotificationScheduler
import com.tbtechs.focusflow.enforcement.NetworkBlockerVpnService
import com.tbtechs.focusflow.enforcement.VpnPolicyCoordinator
import com.tbtechs.focusflow.enforcement.receivers.VpnWatchdogReceiver
import com.tbtechs.focusflow.widget.FocusFlowWidget
import com.tbtechs.focusflow.data.model.AllowedAppPreset
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.BlockPreset
import com.tbtechs.focusflow.data.model.DailyAllowanceEntry
import com.tbtechs.focusflow.data.model.RecurringBlockSchedule
import com.tbtechs.focusflow.data.restore.RestoreGate
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun parseScreenTextScales(serialized: String?): Map<String, Float> =
    runCatching {
        val json = JSONObject(serialized ?: "{}")
        buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val scale = json.optDouble(key, Double.NaN).toFloat()
                if (key.isNotBlank() && key.length <= 160 && scale.isFinite() && scale in 0.8f..1.5f) {
                    put(key, scale)
                }
            }
        }
    }.getOrDefault(emptyMap())

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
    val usageByPackage: Map<String, AllowanceUsage> = emptyMap(),
)

/**
 * Read-only view of one package's persisted allowance counters.
 *
 * The enforcement service stores different fields for count, daily-budget,
 * and rolling-interval modes. Keeping those raw fields together lets the UI
 * present current usage without reimplementing or mutating enforcement logic.
 */
data class AllowanceUsage(
    val mode: String?,
    val date: String?,
    val count: Int,
    val windowStartMs: Long,
    val usedMs: Long,
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
class SettingsRepository(
    context: Context,
    private val restoreGate: RestoreGate = RestoreGate(),
    private val requestVpnSyncAction: (Context) -> Unit = {
        NetworkBlockerVpnService.requestSync(it)
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pushWidgetUpdateAction: (Context) -> Unit = {
        FocusFlowWidget.pushWidgetUpdate(it)
    },
    private val allowanceConfigChangedBroadcastAction: (Context) -> Unit = { context ->
        context.sendBroadcast(
            Intent(AppBlockerAccessibilityService.ACTION_ALLOWANCE_CONFIG_CHANGED).apply {
                `package` = context.packageName
            },
        )
    },
) {

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

        private const val KEY_NETWORK_BLOCK_ENABLED = "net_block_enabled"
        private const val KEY_NETWORK_BLOCK_VPN = "net_block_vpn"
        private const val KEY_EXPLICIT_VPN_PACKAGES = "net_block_explicit_packages"
        private const val KEY_LEGACY_VPN_PACKAGES = "always_on_vpn_packages"
        private const val KEY_VPN_PACKAGES_MIGRATION_COMPLETE = "always_on_vpn_packages_migrated"

        private const val KEY_DAILY_ALLOWANCE_CONFIG = "daily_allowance_config"
        private const val KEY_RECURRING_BLOCK_SCHEDULES = "recurring_block_schedules"
        private const val KEY_USER_GREYOUT_WINDOWS = "user_greyout_windows"
        private const val KEY_BLOCK_PRESETS = "block_presets"
        private const val KEY_OVERLAY_QUOTES = "block_overlay_quotes"
        private const val KEY_DARK_MODE_ENABLED = "dark_mode_enabled"
        private const val KEY_GENERAL_TEXT_SCALE = "general_text_scale"
        private const val KEY_SCREEN_TEXT_SCALES = "screen_text_scales"
        private const val KEY_HOME_TEXT_SCALE = "home_text_scale"
        private const val KEY_FOCUS_TEXT_SCALE = "focus_text_scale"
        private const val KEY_STATS_TEXT_SCALE = "stats_text_scale"
        private const val KEY_SETTINGS_TEXT_SCALE = "settings_text_scale"
        private const val KEY_DEFENSE_TEXT_SCALE = "defense_text_scale"
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
        const val KEY_TASK_PRE_START_REMINDERS_ENABLED = "task_pre_start_reminders_enabled"
        const val KEY_TASK_AT_START_REMINDER_ENABLED = "task_at_start_reminder_enabled"
        const val KEY_TASK_MID_SESSION_REMINDERS_ENABLED = "task_mid_session_reminders_enabled"
        const val KEY_TASK_WRAP_UP_REMINDER_ENABLED = "task_wrap_up_reminder_enabled"
        const val KEY_TASK_END_ALARM_RINGS = "task_end_alarm_rings"
        const val REFLECTION_PROMPTS_ENABLED_KEY = "reflection_prompts_enabled"
        private const val KEY_DEFAULT_DURATION_MINUTES = "default_duration_minutes"
        private const val KEY_AUTO_FOCUS_ENABLED = "auto_focus_enabled"
        private const val KEY_ALLOWED_FOCUS_PACKAGES = "allowed_focus_packages"
        private const val KEY_POMODORO_ENABLED = "pomodoro_enabled"
        private const val KEY_POMODORO_WORK_MINUTES = "pomodoro_work_minutes"
        private const val KEY_POMODORO_BREAK_MINUTES = "pomodoro_break_minutes"
        private const val KEY_FOCUS_DEFENSE_HINT_DISMISSED = "focus_defense_hint_dismissed"
        private const val KEY_LOCAL_ANALYTICS_NOTICE_DISMISSED = "local_analytics_notice_dismissed"
        private const val KEY_STANDALONE_BLOCK_HINT_DISMISSED = "standalone_block_hint_dismissed"
        private const val KEY_ALWAYS_ON_INFO_DISMISSED = "always_on_info_dismissed"
        private const val KEY_PROTECTION_STATUS_BANNER_DISMISSED = "protection_status_banner_dismissed"
        private const val KEY_LAUNCHER_DOCK_PACKAGES = "launcher_dock_packages"
        private const val KEY_LAUNCHER_HIDDEN_PACKAGES = "launcher_hidden_packages"
        private const val KEY_DRAWER_HIDDEN_PACKAGES = "drawer_hidden_packages"
        private const val KEY_LAUNCHER_THEME = "launcher_theme"
        private const val KEY_FOCUS_TOOL_PACKAGES = "focus_tool_packages"
        private const val KEY_LAUNCHER_LOCK_DURING_STANDALONE =
            "launcher_lock_during_standalone"
        private const val KEY_LAUNCHER_BLOCK_UNINSTALL = "launcher_block_uninstall"
        private const val KEY_LAUNCHER_CLOCK_STYLE = "launcher_clock_style"
        private const val KEY_VPN_SELF_HEAL_ENABLED =
            VpnSelfHealPolicy.NATIVE_PREFERENCE_KEY
        private const val KEY_FOCUS_MIRROR_VPN_ENABLED = "net_block_focus_mirror"
        private const val KEY_AVERSION_DIMMER_ENABLED = "aversion_dimmer_enabled"
        private const val KEY_AVERSION_VIBRATE_ENABLED = "aversion_vibrate_enabled"
        private const val KEY_AVERSION_SOUND_ENABLED = "aversion_sound_enabled"
        private const val KEY_KEEP_FOCUS_ACTIVE_UNTIL_TASK_END =
            "keep_focus_active_until_task_end"
        private const val KEY_AUTO_RESCHEDULE_ENABLED = "auto_reschedule_enabled"
        private const val KEY_AUTO_COPY_TO_ALWAYS_ON = "auto_copy_to_always_on"

        private const val KEY_DAILY_TASKS_DONE = "daily_tasks_done"
        private const val KEY_DAILY_TASKS_TOTAL = "daily_tasks_total"
        private const val KEY_DAILY_FOCUS_MINS = "daily_focus_mins"
        private const val KEY_STREAK_DAYS = "streak_days"

        private const val KEY_ACTIVE_SESSION_PACKAGE = "active_session_pkg"
        private const val KEY_ACTIVE_SESSION_END_MS = "active_session_end_ms"

        /**
         * Synchronous read for the shared native alarm-notification publisher.
         * The normal SettingsRepository read remains the typed settings path.
         */
        fun taskEndAlarmRings(context: Context): Boolean =
            context.applicationContext
                .getSharedPreferences(
                    AppBlockerAccessibilityService.PREFS_NAME,
                    Context.MODE_PRIVATE,
                )
                .getBoolean(KEY_TASK_END_ALARM_RINGS, true)
    }

    private val appContext = context.applicationContext
    private val allowanceLedger by lazy { AllowanceLedgerProvider.get(appContext) }

    private val prefs: SharedPreferences
        get() = appContext.getSharedPreferences(
            AppBlockerAccessibilityService.PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    val setupPersistence = SetupPersistenceManager(context)

    /**
     * Small typed escape hatch for metadata that is not enforcement state.
     *
     * Read the preference map instead of calling SharedPreferences.getString
     * directly so a legacy value stored under the key with another type does
     * not crash the app. The next putString call replaces that legacy value.
     */
    fun getString(key: String): String? {
        return when (key) {
            SetupPersistenceManager.KEY_PRIVACY_ACCEPTED -> if (setupPersistence.isPrivacyAccepted()) "true" else "false"
            SetupPersistenceManager.KEY_ONBOARDING_COMPLETE -> if (setupPersistence.isOnboardingComplete()) "true" else "false"
            SetupPersistenceManager.KEY_USER_CONSENTED_BACKGROUND_SERVICE -> if (setupPersistence.isUserConsentedBackgroundService()) "true" else "false"
            SetupPersistenceManager.KEY_PROTECTION_MODE -> setupPersistence.getProtectionMode()
            else -> prefs.all[key] as? String
        }
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        prefs.getBoolean(key, defaultValue)

    fun isFocusActive(): Boolean = prefs.getBoolean(KEY_FOCUS_ACTIVE, false)

    suspend fun putString(key: String, value: String) {
        restoreGate.write("SettingsRepository.putString") {
            when (key) {
                SetupPersistenceManager.KEY_PRIVACY_ACCEPTED -> setupPersistence.setPrivacyAccepted(
                    value.equals("true", ignoreCase = true),
                )
                SetupPersistenceManager.KEY_ONBOARDING_COMPLETE -> setupPersistence.setOnboardingComplete(
                    value.equals("true", ignoreCase = true),
                )
                SetupPersistenceManager.KEY_USER_CONSENTED_BACKGROUND_SERVICE -> setupPersistence.setUserConsentedBackgroundService(
                    value.equals("true", ignoreCase = true),
                )
                SetupPersistenceManager.KEY_PROTECTION_MODE -> setupPersistence.setProtectionMode(value)
                else -> prefs.edit().putString(key, value).apply()
            }
        }
    }

    suspend fun putBoolean(key: String, value: Boolean) {
        restoreGate.write("SettingsRepository.putBoolean") {
            prefs.edit().putBoolean(key, value).apply()
        }
    }

    suspend fun putInt(key: String, value: Int) {
        restoreGate.write("SettingsRepository.putInt") {
            prefs.edit().putInt(key, value).apply()
        }
    }

    suspend fun putFloat(key: String, value: Float) {
        restoreGate.write("SettingsRepository.putFloat") {
            prefs.edit().putFloat(key, value).apply()
        }
    }

    suspend fun removePreference(key: String) {
        restoreGate.write("SettingsRepository.removePreference") {
            prefs.edit().remove(key).apply()
        }
    }

    suspend fun refreshBackupSettingSideEffects(preferenceKeys: Set<String>): List<String> {
        if (preferenceKeys.isEmpty()) return emptyList()

        return restoreGate.write("SettingsRepository.refreshBackupSettingSideEffects") {
            val warnings = mutableListOf<String>()

            suspend fun runSideEffect(label: String, action: suspend () -> Unit) {
                try {
                    action()
                } catch (cancellation: kotlinx.coroutines.CancellationException) {
                    throw cancellation
                } catch (exception: Exception) {
                    val detail = exception.message?.takeIf(String::isNotBlank)
                    warnings += if (detail == null) {
                        "$label could not be refreshed after restoring settings."
                    } else {
                        "$label could not be refreshed after restoring settings: $detail"
                    }
                }
            }

            if ("daily_allowance_config" in preferenceKeys) {
                runSideEffect("Daily allowance enforcement") {
                    allowanceConfigChangedBroadcastAction(appContext)
                }
            }

            val schedulePreferences = setOf("recurring_block_schedules", "user_greyout_windows")
            val vpnPreferences = setOf("net_block_explicit_packages", "net_block_focus_mirror")
            if (preferenceKeys.any(schedulePreferences::contains)) {
                runSideEffect("Block schedules") {
                    setRecurringBlockSchedules(readAppSettings().recurringBlockSchedules)
                }
            } else if (preferenceKeys.any(vpnPreferences::contains)) {
                runSideEffect("VPN settings") { requestVpnSync() }
            }

            if ("block_overlay_quotes" in preferenceKeys) {
                runSideEffect("Overlay quotes") {
                    setOverlayQuotes(parseStringArray(stringPreference(KEY_OVERLAY_QUOTES, "[]")))
                }
            }

            if ("bed_time" in preferenceKeys || "reflection_prompts_enabled" in preferenceKeys) {
                runSideEffect("Reflection reminder schedule") {
                    DayRatingNotificationScheduler.ensureScheduled(appContext)
                }
            }

            warnings
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
            .remove(KEY_TASK_DURATION_MS)
            .remove(KEY_TASK_LAST_WRITTEN_MS)
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

    private suspend fun publishFocusSnapshotImpl(
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

            commitEditor(editor, "publishFocusSnapshot")

            Log.d(TAG, "[NATIVE_PREFS_OK] publishFocusSnapshot active=$active")
            requestVpnSync()
            pushWidgetUpdate()
        } catch (error: SessionPinRequiredException) {
            throw error
        } catch (error: Exception) {
            throw IllegalStateException("PREFS_ERROR: ${error.message}", error)
        }
    }

    fun pushWidgetUpdate() {
        pushWidgetUpdateAction(appContext)
    }

    /** Controls standalone blocking and its optional early-cancel PIN gate. */
    suspend fun setStandaloneBlock(
        active: Boolean,
        packages: List<String>,
        untilMs: Long,
        pinHash: String? = null,
    ) {
        restoreGate.write("SettingsRepository.setStandaloneBlock") {
        if (!active && prefs.getLong(KEY_STANDALONE_UNTIL_MS, 0L) > System.currentTimeMillis()) {
            requireValidSessionPin(
                pinHash,
                "A session PIN is set — supply the correct PIN hash to end the standalone block early",
            )
        }
        val existingStandaloneUntilMs = prefs.getLong(KEY_STANDALONE_UNTIL_MS, 0L)
        val keepCurrentVpnPackages = active &&
            prefs.getBoolean(KEY_STANDALONE_ACTIVE, false) &&
            (existingStandaloneUntilMs <= 0L ||
                existingStandaloneUntilMs > System.currentTimeMillis())
        val editor = prefs.edit()
            .putBoolean(KEY_STANDALONE_ACTIVE, active)
            .putString(KEY_STANDALONE_PACKAGES, packages.toJsonArrayString())
            .putLong(KEY_STANDALONE_UNTIL_MS, untilMs)
        if (!keepCurrentVpnPackages) {
            editor.putString(KEY_STANDALONE_VPN_PACKAGES, "[]")
        }
        editor.apply()
        requestVpnSync()
        pushWidgetUpdate()
        }
    }

    /** Compatibility overload for the bridge's JavaScript number timestamp. */
    suspend fun setStandaloneBlock(
        active: Boolean,
        packages: List<String>,
        untilMs: Double,
        pinHash: String? = null,
    ) = setStandaloneBlock(active, packages, untilMs.toLong(), pinHash)

    suspend fun setAlwaysBlockActive(active: Boolean, packages: List<String>) {
        restoreGate.write("SettingsRepository.setAlwaysBlockActive") {
        prefs.edit()
            .putBoolean(AppBlockerAccessibilityService.PREF_ALWAYS_BLOCK, active)
            .putString(
                AppBlockerAccessibilityService.PREF_ALWAYS_BLOCK_PKGS,
                packages.toJsonArrayString(),
            )
            .apply()
        }
    }

    /**
     * Stores recurring blocks and mirrors them to the greyout schedule consumed
     * by the accessibility service. Existing user-created greyout windows
     * (entries without a scheduleId) are preserved.
     */
    suspend fun setRecurringBlockSchedules(schedules: List<RecurringBlockSchedule>) {
        restoreGate.write("SettingsRepository.setRecurringBlockSchedules") {
        val recurringJson = JSONArray().apply {
            schedules.forEach { schedule ->
                put(JSONObject().apply {
                    put("id", schedule.id)
                    put("name", schedule.name)
                    put("packages", JSONArray(schedule.packages))
                    put("startHour", schedule.startHour)
                    put("startMinute", schedule.startMinute)
                    put("endHour", schedule.endHour)
                    put("endMinute", schedule.endMinute)
                    put("daysOfWeek", JSONArray(schedule.daysOfWeek))
                    put("enabled", schedule.enabled)
                    put("vpnEnabled", schedule.vpnEnabled)
                    put(
                        "vpnPackages",
                        JSONArray(if (schedule.vpnEnabled) schedule.packages else emptyList()),
                    )
                })
            }
        }.toString()

        val previouslyStoredUserWindows =
            prefs.all[KEY_USER_GREYOUT_WINDOWS] as? String
        val existingWindows = previouslyStoredUserWindows
            ?.let(::parseJsonArrayObjects)
            ?.filter { it.optString("scheduleId").isBlank() }
            ?: parseJsonArrayObjects(
                stringPreference("greyout_schedule", "[]"),
            ).filter { it.optString("scheduleId").isBlank() }
        val userWindowsJson = JSONArray(existingWindows).toString()
        val scheduleWindows = buildScheduleGreyoutWindows(schedules)

        commitEditor(
            prefs.edit()
                .putString(KEY_RECURRING_BLOCK_SCHEDULES, recurringJson)
                .putString(KEY_USER_GREYOUT_WINDOWS, userWindowsJson)
                .putString(
                    "greyout_schedule",
                    JSONArray(existingWindows + scheduleWindows).toString(),
                ),
            "recurring schedule",
        )
        requestVpnSync()
        }
    }

    /**
     * Stores only user-authored greyout windows, then rebuilds the combined
     * schedule consumed by AppBlockerAccessibilityService.
     */
    suspend fun setUserGreyoutWindows(windowsJson: String) {
        restoreGate.write("SettingsRepository.setUserGreyoutWindows") {
        val parsedWindows = JSONArray(windowsJson)
        val userWindows = buildList {
            for (index in 0 until parsedWindows.length()) {
                val window = parsedWindows.optJSONObject(index) ?: continue
                if (window.optString("scheduleId").isBlank()) {
                    add(JSONObject(window.toString()))
                }
            }
        }
        val recurringSchedules = parseRecurringSchedules(
            stringPreference(KEY_RECURRING_BLOCK_SCHEDULES, "[]"),
        )
        val combined = userWindows + buildScheduleGreyoutWindows(recurringSchedules)
        commitEditor(
            prefs.edit()
                .putString(KEY_USER_GREYOUT_WINDOWS, JSONArray(userWindows).toString())
                .putString("greyout_schedule", JSONArray(combined).toString()),
            "user greyout windows",
        )
        requestVpnSync()
        }
    }

    suspend fun setDailyAllowancePackages(packages: List<String>) {
        restoreGate.write("SettingsRepository.setDailyAllowancePackages") {
        prefs.edit()
            .putString(
                AppBlockerAccessibilityService.PREF_DAILY_ALLOWANCE_PKGS,
                packages.toJsonArrayString(),
            )
            .apply()
        }
    }

    suspend fun setAlwaysOnVpnPackages(packages: List<String>) {
        ensureLegacyAlwaysOnVpnPackagesMigrated()
        restoreGate.write("SettingsRepository.setAlwaysOnVpnPackages") {
        prefs.edit()
            .putString(KEY_EXPLICIT_VPN_PACKAGES, packages.toJsonArrayString())
            .apply()
        requestVpnSync()
        }
    }

    suspend fun setBlockPresets(presets: List<BlockPreset>) {
        restoreGate.write("SettingsRepository.setBlockPresets") {
        val json = JSONArray().apply {
            presets.forEach { preset ->
                put(JSONObject().apply {
                    put("id", preset.id)
                    put("name", preset.name)
                    put("packages", JSONArray(preset.packages))
                })
            }
        }.toString()
        prefs.edit().putString(KEY_BLOCK_PRESETS, json).apply()
        }
    }

    suspend fun setOverlayQuotes(quotes: List<String>) {
        restoreGate.write("SettingsRepository.setOverlayQuotes") {
            BlockOverlayController(appContext).setCustomQuotes(JSONArray(quotes).toString())
        }
    }

    suspend fun setBlockedWords(words: List<String>) {
        restoreGate.write("SettingsRepository.setBlockedWords") {
        prefs.edit()
            .putString(AppBlockerAccessibilityService.PREF_BLOCKED_WORDS, words.toJsonArrayString())
            .apply()
        }
    }

    suspend fun setLauncherDockPackages(packagesJson: String) {
        restoreGate.write("SettingsRepository.setLauncherDockPackages") {
            prefs.edit().putString(KEY_LAUNCHER_DOCK_PACKAGES, packagesJson).apply()
        }
    }

    suspend fun setSystemGuardEnabled(enabled: Boolean) {
        restoreGate.write("SettingsRepository.setSystemGuardEnabled") {
        prefs.edit()
            .putBoolean(AppBlockerAccessibilityService.PREF_SYSTEM_GUARD_ENABLED, enabled)
            .apply()
        }
    }

    suspend fun setBlockInstallActionsEnabled(enabled: Boolean) {
        restoreGate.write("SettingsRepository.setBlockInstallActionsEnabled") {
        prefs.edit()
            .putBoolean(AppBlockerAccessibilityService.PREF_BLOCK_INSTALL_ACTIONS, enabled)
            .apply()
        }
    }

    suspend fun setBlockYoutubeShortsEnabled(enabled: Boolean) {
        restoreGate.write("SettingsRepository.setBlockYoutubeShortsEnabled") {
        prefs.edit()
            .putBoolean(AppBlockerAccessibilityService.PREF_BLOCK_YT_SHORTS, enabled)
            .apply()
        }
    }

    suspend fun setBlockInstagramReelsEnabled(enabled: Boolean) {
        restoreGate.write("SettingsRepository.setBlockInstagramReelsEnabled") {
        prefs.edit()
            .putBoolean(AppBlockerAccessibilityService.PREF_BLOCK_IG_REELS, enabled)
            .apply()
        }
    }

    suspend fun setNetworkBlockEnabled(enabled: Boolean) {
        restoreGate.write("SettingsRepository.setNetworkBlockEnabled") {
            val currentlyEnabled =
                prefs.getBoolean(KEY_NETWORK_BLOCK_ENABLED, false) ||
                    prefs.getBoolean(KEY_NETWORK_BLOCK_VPN, false)
            if (!ActiveBlockGuardPolicy.mayChangeNetworkBlocking(
                    currentlyEnabled = currentlyEnabled,
                    requestedEnabled = enabled,
                    blockActive = isBlockingSessionActive(),
                )
            ) {
                throw NetworkBlockingChangeRejectedException()
            }
            prefs.edit()
                .putBoolean(KEY_NETWORK_BLOCK_ENABLED, enabled)
                .putBoolean(KEY_NETWORK_BLOCK_VPN, enabled)
                .apply()
            requestVpnSync()
        }
    }

    fun isNetworkBlockEnabledNow(): Boolean =
        prefs.getBoolean(KEY_NETWORK_BLOCK_ENABLED, false)

    fun isVpnSelfHealEnabledNow(): Boolean =
        prefs.getBoolean(KEY_VPN_SELF_HEAL_ENABLED, false)

    private fun isBlockingSessionActive(): Boolean =
        ActiveBlockGuardPolicy.isActive(
            focusActive = prefs.getBoolean(KEY_FOCUS_ACTIVE, false),
            focusEndMs = prefs.getLong(KEY_TASK_END_MS, 0L),
            standaloneActive = prefs.getBoolean(KEY_STANDALONE_ACTIVE, false),
            standaloneUntilMs = prefs.getLong(KEY_STANDALONE_UNTIL_MS, 0L),
            nowMs = System.currentTimeMillis(),
        )

    suspend fun setDailyAllowanceConfig(configJson: String) {
        restoreGate.write("SettingsRepository.setDailyAllowanceConfig") {
        prefs.edit().putString(KEY_DAILY_ALLOWANCE_CONFIG, configJson).apply()
        allowanceConfigChangedBroadcastAction(appContext)
        }
    }

    /**
     * Atomically updates standalone enforcement and allowance configuration.
     * The allowance-change broadcast is sent only after the shared preference
     * commit succeeds.
     */
    suspend fun publishStandaloneAndAllowanceSnapshot(
        active: Boolean,
        packages: List<String>,
        vpnPackages: List<String>,
        untilMs: Long,
        allowanceEntries: List<DailyAllowanceEntry>,
        pinHash: String?,
    ) {
        restoreGate.write("SettingsRepository.publishStandaloneAndAllowanceSnapshot") {
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
                .putString(KEY_STANDALONE_VPN_PACKAGES, vpnPackages.toJsonArrayString())
                .putLong(KEY_STANDALONE_UNTIL_MS, untilMs)
        } else {
            editor
                .putBoolean(KEY_STANDALONE_ACTIVE, false)
                .putString(KEY_STANDALONE_PACKAGES, "[]")
                .putString(KEY_STANDALONE_VPN_PACKAGES, "[]")
                .putLong(KEY_STANDALONE_UNTIL_MS, 0L)
        }
        editor.putString(KEY_DAILY_ALLOWANCE_CONFIG, allowanceJson)
        commitEditor(editor, "standalone and allowance snapshot")
        allowanceConfigChangedBroadcastAction(appContext)
        requestVpnSync()
        pushWidgetUpdate()
        }
    }

    suspend fun setNotificationPreferences(settings: AppSettings) {
        restoreGate.write("SettingsRepository.setNotificationPreferences") {
        val results = JSONObject().apply {
            settings.lastSessionResultByTaskId.forEach { (taskId, result) -> put(taskId, result) }
        }
        prefs.edit()
            .putBoolean(KEY_DARK_MODE_ENABLED, settings.darkModeEnabled)
            .putFloat(KEY_GENERAL_TEXT_SCALE, settings.generalTextScale)
            .putString(
                KEY_SCREEN_TEXT_SCALES,
                JSONObject().apply {
                    settings.screenTextScales.forEach { (key, scale) ->
                        put(key, scale.coerceIn(0.8f, 1.5f))
                    }
                }.toString(),
            )
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
            .putBoolean(KEY_TASK_PRE_START_REMINDERS_ENABLED, settings.taskPreStartRemindersEnabled)
            .putBoolean(KEY_TASK_AT_START_REMINDER_ENABLED, settings.taskAtStartReminderEnabled)
            .putBoolean(KEY_TASK_MID_SESSION_REMINDERS_ENABLED, settings.taskMidSessionRemindersEnabled)
            .putBoolean(KEY_TASK_WRAP_UP_REMINDER_ENABLED, settings.taskWrapUpReminderEnabled)
            .putBoolean(KEY_TASK_END_ALARM_RINGS, settings.taskEndAlarmRings)
            .putBoolean(REFLECTION_PROMPTS_ENABLED_KEY, settings.reflectionPromptsEnabled)
            .putInt(KEY_DEFAULT_DURATION_MINUTES, settings.defaultDurationMinutes.coerceIn(5, 480))
            .putBoolean(KEY_AUTO_FOCUS_ENABLED, settings.autoFocusEnabled)
            .putString(KEY_ALLOWED_FOCUS_PACKAGES, settings.allowedFocusPackages.toJsonArrayString())
            .putBoolean(KEY_POMODORO_ENABLED, settings.pomodoroEnabled)
            .putInt(KEY_POMODORO_WORK_MINUTES, settings.pomodoroWorkMinutes.coerceIn(1, 180))
            .putInt(KEY_POMODORO_BREAK_MINUTES, settings.pomodoroBreakMinutes.coerceIn(1, 60))
            .putBoolean(KEY_FOCUS_DEFENSE_HINT_DISMISSED, settings.focusDefenseHintDismissed)
            .putBoolean(KEY_LOCAL_ANALYTICS_NOTICE_DISMISSED, settings.localAnalyticsNoticeDismissed)
            .putBoolean(KEY_STANDALONE_BLOCK_HINT_DISMISSED, settings.standaloneBlockHintDismissed)
            .putBoolean(KEY_ALWAYS_ON_INFO_DISMISSED, settings.alwaysOnInfoDismissed)
            .putBoolean(KEY_PROTECTION_STATUS_BANNER_DISMISSED, settings.protectionStatusBannerDismissed)
            .apply {
                if (settings.homeTextScale == null) {
                    remove(KEY_HOME_TEXT_SCALE)
                } else {
                    putFloat(KEY_HOME_TEXT_SCALE, settings.homeTextScale)
                }
                if (settings.focusTextScale == null) {
                    remove(KEY_FOCUS_TEXT_SCALE)
                } else {
                    putFloat(KEY_FOCUS_TEXT_SCALE, settings.focusTextScale)
                }
                if (settings.statsTextScale == null) {
                    remove(KEY_STATS_TEXT_SCALE)
                } else {
                    putFloat(KEY_STATS_TEXT_SCALE, settings.statsTextScale)
                }
                if (settings.settingsTextScale == null) {
                    remove(KEY_SETTINGS_TEXT_SCALE)
                } else {
                    putFloat(KEY_SETTINGS_TEXT_SCALE, settings.settingsTextScale)
                }
                if (settings.defenseTextScale == null) {
                    remove(KEY_DEFENSE_TEXT_SCALE)
                } else {
                    putFloat(KEY_DEFENSE_TEXT_SCALE, settings.defenseTextScale)
                }
                if (settings.lastShownDebriefSessionId == null) {
                    remove(KEY_LAST_SHOWN_DEBRIEF_SESSION_ID)
                } else {
                    putInt(KEY_LAST_SHOWN_DEBRIEF_SESSION_ID, settings.lastShownDebriefSessionId)
                }
            }
            .apply()
        }
    }

    /**
     * Reads the settings fields owned by this repository from the existing
     * enforcement preference namespace.
     */
    suspend fun readAppSettings(): AppSettings {
        ensureVpnSelfHealPreferenceMigrated()
        ensureLegacyAlwaysOnVpnPackagesMigrated()
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
            standaloneBlockVpnPackages = parseStringArray(
                stringPreference(KEY_STANDALONE_VPN_PACKAGES, "[]"),
            ),
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
            launcherBlockUninstall = prefs.getBoolean(KEY_LAUNCHER_BLOCK_UNINSTALL, false),
            launcherPresets = parsePresets(prefs.getString("allowed_app_presets", "[]")),
            launcherDockPackages = parseStringArray(
                stringPreference(KEY_LAUNCHER_DOCK_PACKAGES, "[]"),
            ),
            launcherClockStyle = stringPreference(KEY_LAUNCHER_CLOCK_STYLE, ""),
            blockPresets = parseBlockPresets(
                stringPreference(KEY_BLOCK_PRESETS, "[]"),
            ),
            overlayQuotes = parseStringArray(
                stringPreference(KEY_OVERLAY_QUOTES, "[]"),
            ),
            alwaysOnVpnPackages = parseStringArray(
                stringPreference(KEY_EXPLICIT_VPN_PACKAGES, "[]"),
            ),
            dailyAllowanceConfigJson = prefs.getString(KEY_DAILY_ALLOWANCE_CONFIG, null),
            recurringBlockSchedules = parseRecurringSchedules(
                prefs.getString(KEY_RECURRING_BLOCK_SCHEDULES, "[]"),
            ),
            userGreyoutWindowsJson = stringPreference(KEY_USER_GREYOUT_WINDOWS, "[]"),
            networkBlockEnabled = prefs.getBoolean(KEY_NETWORK_BLOCK_ENABLED, false),
            vpnSelfHealEnabled = prefs.getBoolean(KEY_VPN_SELF_HEAL_ENABLED, false),
            focusMirrorVpnEnabled = prefs.getBoolean(KEY_FOCUS_MIRROR_VPN_ENABLED, false),
            systemGuardEnabled = prefs.getBoolean(AppBlockerAccessibilityService.PREF_SYSTEM_GUARD_ENABLED, false),
            blockInstallActionsEnabled = prefs.getBoolean(
                AppBlockerAccessibilityService.PREF_BLOCK_INSTALL_ACTIONS,
                false,
            ),
            blockYoutubeShortsEnabled = prefs.getBoolean(
                AppBlockerAccessibilityService.PREF_BLOCK_YT_SHORTS,
                false,
            ),
            blockInstagramReelsEnabled = prefs.getBoolean(
                AppBlockerAccessibilityService.PREF_BLOCK_IG_REELS,
                false,
            ),
            aversionDimmerEnabled = prefs.getBoolean(KEY_AVERSION_DIMMER_ENABLED, false),
            aversionVibrateEnabled = prefs.getBoolean(KEY_AVERSION_VIBRATE_ENABLED, false),
            aversionSoundEnabled = prefs.getBoolean(KEY_AVERSION_SOUND_ENABLED, false),
            keepFocusActiveUntilTaskEnd = prefs.getBoolean(
                KEY_KEEP_FOCUS_ACTIVE_UNTIL_TASK_END,
                true,
            ),
            autoRescheduleEnabled = prefs.getBoolean(KEY_AUTO_RESCHEDULE_ENABLED, false),
            autoCopyToAlwaysOn = prefs.getBoolean(KEY_AUTO_COPY_TO_ALWAYS_ON, false),
            darkModeEnabled = prefs.getBoolean(KEY_DARK_MODE_ENABLED, true),
            generalTextScale = prefs.getFloat(KEY_GENERAL_TEXT_SCALE, 1f),
            screenTextScales = parseScreenTextScales(
                prefs.getString(KEY_SCREEN_TEXT_SCALES, null),
            ),
            homeTextScale = if (prefs.contains(KEY_HOME_TEXT_SCALE)) {
                prefs.getFloat(KEY_HOME_TEXT_SCALE, 1f)
            } else {
                null
            },
            focusTextScale = if (prefs.contains(KEY_FOCUS_TEXT_SCALE)) {
                prefs.getFloat(KEY_FOCUS_TEXT_SCALE, 1f)
            } else {
                null
            },
            statsTextScale = if (prefs.contains(KEY_STATS_TEXT_SCALE)) {
                prefs.getFloat(KEY_STATS_TEXT_SCALE, 1f)
            } else {
                null
            },
            settingsTextScale = if (prefs.contains(KEY_SETTINGS_TEXT_SCALE)) {
                prefs.getFloat(KEY_SETTINGS_TEXT_SCALE, 1f)
            } else {
                null
            },
            defenseTextScale = if (prefs.contains(KEY_DEFENSE_TEXT_SCALE)) {
                prefs.getFloat(KEY_DEFENSE_TEXT_SCALE, 1f)
            } else {
                null
            },
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
            taskPreStartRemindersEnabled = prefs.getBoolean(KEY_TASK_PRE_START_REMINDERS_ENABLED, true),
            taskAtStartReminderEnabled = prefs.getBoolean(KEY_TASK_AT_START_REMINDER_ENABLED, true),
            taskMidSessionRemindersEnabled = prefs.getBoolean(KEY_TASK_MID_SESSION_REMINDERS_ENABLED, true),
            taskWrapUpReminderEnabled = prefs.getBoolean(KEY_TASK_WRAP_UP_REMINDER_ENABLED, true),
            taskEndAlarmRings = prefs.getBoolean(KEY_TASK_END_ALARM_RINGS, true),
            reflectionPromptsEnabled = prefs.getBoolean(REFLECTION_PROMPTS_ENABLED_KEY, true),
            defaultDurationMinutes = prefs.getInt(KEY_DEFAULT_DURATION_MINUTES, 60).coerceIn(5, 480),
            autoFocusEnabled = prefs.getBoolean(KEY_AUTO_FOCUS_ENABLED, false),
            allowedFocusPackages = parseStringArray(prefs.getString(KEY_ALLOWED_FOCUS_PACKAGES, "[]")),
            pomodoroEnabled = prefs.getBoolean(KEY_POMODORO_ENABLED, false),
            pomodoroWorkMinutes = prefs.getInt(KEY_POMODORO_WORK_MINUTES, 25).coerceIn(1, 180),
            pomodoroBreakMinutes = prefs.getInt(KEY_POMODORO_BREAK_MINUTES, 5).coerceIn(1, 60),
            focusDefenseHintDismissed = prefs.getBoolean(KEY_FOCUS_DEFENSE_HINT_DISMISSED, false),
            localAnalyticsNoticeDismissed = prefs.getBoolean(KEY_LOCAL_ANALYTICS_NOTICE_DISMISSED, false),
            standaloneBlockHintDismissed = prefs.getBoolean(KEY_STANDALONE_BLOCK_HINT_DISMISSED, false),
            alwaysOnInfoDismissed = prefs.getBoolean(KEY_ALWAYS_ON_INFO_DISMISSED, false),
            protectionStatusBannerDismissed = prefs.getBoolean(KEY_PROTECTION_STATUS_BANNER_DISMISSED, false),
        )
    }

    /** Generic overlay/config string setter; an empty value removes the key. */
    suspend fun getLong(key: String): Long = prefs.getLong(key, 0L)

    suspend fun getAllowanceSnapshot(): AllowanceSnapshot =
        allowanceLedger.withLock {
            val usage = allowanceLedger.snapshot()
            AllowanceSnapshot(
                usageJson = usage.usageJson,
                configJson = prefs.getString(KEY_DAILY_ALLOWANCE_CONFIG, null),
                activeSessionPackage = prefs.getString(KEY_ACTIVE_SESSION_PACKAGE, null),
                activeSessionEndMs = prefs.getLong(KEY_ACTIVE_SESSION_END_MS, 0L),
                usageByPackage = usage.usageByPackage.mapValues { (_, record) ->
                    AllowanceUsage(
                        mode = record.mode,
                        date = record.date,
                        count = record.count,
                        windowStartMs = record.windowStartMs,
                        usedMs = record.usedMs,
                    )
                },
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
        restoreGate.write("SettingsRepository.setLauncherHiddenPackages") {
        prefs.edit()
            .putString(KEY_LAUNCHER_HIDDEN_PACKAGES, packagesJson)
            .putString(KEY_DRAWER_HIDDEN_PACKAGES, packagesJson)
            .apply()
        }
    }

    suspend fun setLauncherWallpaperUri(uri: String?) {
        val editor = prefs.edit()
        if (uri.isNullOrBlank()) editor.remove("launcher_wallpaper_uri")
        else editor.putString("launcher_wallpaper_uri", uri)
        editor.apply()
    }

    suspend fun setLauncherPresets(presets: List<AllowedAppPreset>) {
        restoreGate.write("SettingsRepository.setLauncherPresets") {
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
    }

    suspend fun setLauncherDockPackages(packages: List<String>) {
        setLauncherDockPackages(JSONArray(packages).toString())
    }

    suspend fun setLauncherTheme(theme: String) {
        restoreGate.write("SettingsRepository.setLauncherTheme") {
        prefs.edit()
            .putString(KEY_LAUNCHER_THEME, if (theme == "classic") "classic" else "glassy")
            .apply()
        }
    }

    suspend fun setFocusToolPackages(packagesJson: String) {
        restoreGate.write("SettingsRepository.setFocusToolPackages") {
            prefs.edit().putString(KEY_FOCUS_TOOL_PACKAGES, packagesJson).apply()
        }
    }

    suspend fun setLauncherLockDuringStandalone(enabled: Boolean) {
        restoreGate.write("SettingsRepository.setLauncherLockDuringStandalone") {
            prefs.edit().putBoolean(KEY_LAUNCHER_LOCK_DURING_STANDALONE, enabled).apply()
        }
    }

    suspend fun setLauncherBlockUninstall(enabled: Boolean) {
        restoreGate.write("SettingsRepository.setLauncherBlockUninstall") {
            prefs.edit().putBoolean(KEY_LAUNCHER_BLOCK_UNINSTALL, enabled).apply()
        }
    }

    /**
     * Persists the Defense screen preferences that are consumed by native
     * enforcement or by the focus/task schedulers.
     */
    suspend fun setDefensePreferences(settings: AppSettings) {
        ensureVpnSelfHealPreferenceMigrated()
        restoreGate.write("SettingsRepository.setDefensePreferences") {
            val previousSelfHealValue = prefs.getBoolean(KEY_VPN_SELF_HEAL_ENABLED, false)
            val selfHealDecision = VpnSelfHealPolicy.toggleDecision(
                currentValue = previousSelfHealValue,
                requestedValue = settings.vpnSelfHealEnabled,
            )
            val editor = prefs.edit()
                .putBoolean(KEY_LAUNCHER_BLOCK_UNINSTALL, settings.launcherBlockUninstall)
                .putBoolean(KEY_VPN_SELF_HEAL_ENABLED, selfHealDecision.persistedValue)
                .putBoolean(KEY_FOCUS_MIRROR_VPN_ENABLED, settings.focusMirrorVpnEnabled)
                .putBoolean(KEY_AVERSION_DIMMER_ENABLED, settings.aversionDimmerEnabled)
                .putBoolean(KEY_AVERSION_VIBRATE_ENABLED, settings.aversionVibrateEnabled)
                .putBoolean(KEY_AVERSION_SOUND_ENABLED, settings.aversionSoundEnabled)
                .putBoolean(
                    KEY_KEEP_FOCUS_ACTIVE_UNTIL_TASK_END,
                    settings.keepFocusActiveUntilTaskEnd,
                )
                .putBoolean(KEY_AUTO_RESCHEDULE_ENABLED, settings.autoRescheduleEnabled)
                .putBoolean(KEY_AUTO_COPY_TO_ALWAYS_ON, settings.autoCopyToAlwaysOn)
            commitEditor(editor, "defense preferences")
            when (selfHealDecision.effect) {
                VpnSelfHealPolicy.ToggleEffect.CANCEL_WATCHDOG -> {
                    VpnWatchdogReceiver.cancel(appContext)
                    requestVpnSync()
                }
                VpnSelfHealPolicy.ToggleEffect.REQUEST_RECOVERY_SYNC ->
                    VpnPolicyCoordinator.requestRecoverySync(appContext)
                VpnSelfHealPolicy.ToggleEffect.NONE -> requestVpnSync()
            }
        }
    }

    private suspend fun ensureVpnSelfHealPreferenceMigrated() {
        withContext(ioDispatcher) {
            restoreGate.write("SettingsRepository.migrateVpnSelfHealPreference") {
                synchronized(VpnSelfHealPolicy.preferenceLock) {
                    val migrationValue = VpnSelfHealPolicy.migrationValue(
                        nativePreferenceExists = prefs.contains(KEY_VPN_SELF_HEAL_ENABLED),
                        legacyPreferenceValue =
                            prefs.all[VpnSelfHealPolicy.LEGACY_PREFERENCE_KEY] as? Boolean,
                    ) ?: return@synchronized

                    check(
                        prefs.edit()
                            .putBoolean(KEY_VPN_SELF_HEAL_ENABLED, migrationValue)
                            .commit(),
                    ) {
                        "Could not persist the one-time VPN self-healing preference migration"
                    }
                }
            }
        }
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
        restoreGate.write("SettingsRepository.setLauncherClockStyle") {
            prefs.edit().putString(KEY_LAUNCHER_CLOCK_STYLE, style).apply()
        }
    }

    suspend fun resetDailyAllowanceUsage(packageName: String?) {
        allowanceLedger.reset(packageName)
    }

    private fun requestVpnSync() = requestVpnSyncAction(appContext)

    /**
     * SharedPreferences.commit() is reserved for durability boundaries and is
     * always dispatched off the caller's thread. The snapshot is built before
     * entering this helper, so one Editor still represents one coherent write.
     */
    private suspend fun commitEditor(
        editor: SharedPreferences.Editor,
        operation: String,
    ) {
        val committed = withContext(ioDispatcher) { editor.commit() }
        if (!committed) {
            Log.e(TAG, "[NATIVE_PREFS_COMMIT_FAILED] $operation")
            throw IllegalStateException("PREFS_WRITE_FAILED: $operation commit() returned false")
        }
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

    /**
     * Moves the legacy backup-only VPN package list into the native explicit
     * list once. The old preference is intentionally retained as a copy.
     */
    internal suspend fun ensureLegacyAlwaysOnVpnPackagesMigrated() {
        VpnPolicyCoordinator.ensureExplicitPackagesMigrated(prefs, restoreGate)
        restoreGate.write("SettingsRepository.migrateLegacyAlwaysOnVpnPackages") {
            synchronized(VpnPackageListMigrationPolicy) {
                if (prefs.getBoolean(KEY_VPN_PACKAGES_MIGRATION_COMPLETE, false)) {
                    return@synchronized
                }

                val explicitPackages = parseVpnMigrationList(
                    prefs.all[KEY_EXPLICIT_VPN_PACKAGES],
                ) ?: return@synchronized
                val legacyPackages = parseVpnMigrationList(
                    prefs.all[KEY_LEGACY_VPN_PACKAGES],
                ) ?: return@synchronized

                val editor = prefs.edit()
                    .putBoolean(KEY_VPN_PACKAGES_MIGRATION_COMPLETE, true)
                if (legacyPackages.isNotEmpty()) {
                    editor.putString(
                        KEY_EXPLICIT_VPN_PACKAGES,
                        VpnPackageListMigrationPolicy
                            .mergeLegacyPackages(explicitPackages, legacyPackages)
                            .toJsonArrayString(),
                    )
                }
                check(editor.commit()) {
                    "Could not migrate the legacy VPN package list"
                }
            }
        }
    }

    private fun parseVpnMigrationList(value: Any?): List<String>? {
        if (value == null) return emptyList()
        val json = value as? String ?: return null
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val packageName = array.get(index) as? String
                        ?: throw IllegalArgumentException("VPN package list entry is not a string")
                    if (packageName.isNotBlank()) add(packageName)
                }
            }
        }.getOrNull()
    }

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

    private fun parseRecurringSchedules(json: String?): List<RecurringBlockSchedule> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null

                // Accept both Kotlin-written ("daysOfWeek", 0-based) and
                // TS-written ("days", 1-based Calendar.DAY_OF_WEEK) keys.
                val rawDays = item.optJSONArray("daysOfWeek")
                    ?: item.optJSONArray("days")
                    ?: JSONArray()
                val isOneBased = item.has("days") && !item.has("daysOfWeek")
                val daysOfWeek = (0 until rawDays.length())
                    .map { rawDays.optInt(it) }
                    .map { if (isOneBased) (it - 1).coerceIn(0, 6) else it.coerceIn(0, 6) }

                // Accept both "startMinute" (Kotlin) and "startMin" (TS).
                val startMinute = (item.optInt("startMinute", -1).takeIf { it >= 0 }
                    ?: item.optInt("startMin", 0)).coerceIn(0, 59)
                val endMinute = (item.optInt("endMinute", -1).takeIf { it >= 0 }
                    ?: item.optInt("endMin", 0)).coerceIn(0, 59)

                val packages = parseStringArray(
                    item.optJSONArray("packages")?.toString()
                        ?: item.optJSONArray("pkgs")?.toString()
                        ?: item.optString("pkg").takeIf { it.isNotBlank() }?.let { "[\"$it\"]" }
                )

                RecurringBlockSchedule(
                    id          = item.optString("id").ifBlank { "migrated-$i" },
                    packages    = packages,
                    startHour   = item.optInt("startHour").coerceIn(0, 23),
                    startMinute = startMinute,
                    endHour     = item.optInt("endHour").coerceIn(0, 23),
                    endMinute   = endMinute,
                    daysOfWeek  = daysOfWeek,
                    enabled     = item.optBoolean("enabled", true),
                    vpnEnabled  = item.optBoolean("vpnEnabled", false),
                    name        = item.optString("name"),
                    vpnPackages = parseStringArray(item.optJSONArray("vpnPackages")?.toString()),
                )
            }
        }.getOrDefault(emptyList())
    }

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

    private fun parseBlockPresets(json: String?): List<BlockPreset> =
        runCatching {
            val array = JSONArray(json ?: "[]")
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                BlockPreset(
                    id = id,
                    name = item.optString("name"),
                    packages = parseStringArray(item.optJSONArray("packages")?.toString()),
                )
            }
        }.getOrDefault(emptyList())

    private fun buildScheduleGreyoutWindows(
        schedules: List<RecurringBlockSchedule>,
    ): List<JSONObject> =
        schedules
            .filter { it.enabled && it.packages.isNotEmpty() }
            .flatMap { schedule ->
                schedule.packages.map { packageName ->
                    JSONObject().apply {
                        put("pkg", packageName)
                        put("startHour", schedule.startHour.coerceIn(0, 23))
                        put("startMin", schedule.startMinute.coerceIn(0, 59))
                        put("endHour", schedule.endHour.coerceIn(0, 23))
                        put("endMin", schedule.endMinute.coerceIn(0, 59))
                        put(
                            "days",
                            JSONArray(schedule.daysOfWeek.map { day -> day.coerceIn(0, 6) + 1 }),
                        )
                        put("scheduleId", schedule.id)
                        put("scheduleName", schedule.name)
                        put("vpnEnabled", schedule.vpnEnabled)
                        put(
                            "vpnPackages",
                            JSONArray(if (schedule.vpnEnabled) schedule.packages else emptyList()),
                        )
                    }
                }
            }

    private fun stringPreference(key: String, defaultValue: String): String =
        prefs.all[key] as? String ?: defaultValue
}