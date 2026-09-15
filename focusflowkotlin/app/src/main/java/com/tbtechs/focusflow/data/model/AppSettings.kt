package com.tbtechs.focusflow.data.model

/**
 * AppSettings
 *
 * ⚠ FLAG — MISSING FROM REPLIT STAGE 2 OUTPUT
 *
 * This file should have been produced by Replit's SettingsRepository work. It was not.
 * This is the authoritative definition until Replit's output is extended.
 *
 * SettingsRepository persists the enforcement-facing fields and the
 * notification/insight preferences. SettingsViewModel hydrates this model on
 * startup and writes changes through the repository.
 */
data class AppSettings(

    // ── PIN protection ────────────────────────────────────────────────────────
    /** True when a defense PIN is configured. Backed by PinManager.isPinSet(). */
    val pinProtectionEnabled: Boolean = false,

    // ── Content blocking ──────────────────────────────────────────────────────
    /**
     * Package names blocked by the "Always On" enforcement layer.
     * Setter/read path: SettingsRepository.setAlwaysBlockActive/readAppSettings.
     */
    val alwaysBlockPackages: List<String> = emptyList(),
    val alwaysBlockEnabled: Boolean = false,

    /**
     * Words whose presence in an app's accessibility-visible text triggers a block.
     * Setter/read path: SettingsRepository.setBlockedWords/readAppSettings.
     */
    val blockedWords: List<String> = emptyList(),

    // ── Standalone block ──────────────────────────────────────────────────────
    /**
     * State of the user-initiated standalone block (not focus-session-linked).
     * Setter/read path: SettingsRepository.setStandaloneBlock/readAppSettings.
     */
    val standaloneBlockActive: Boolean = false,
    val standaloneBlockPackages: List<String> = emptyList(),
    val standaloneBlockUntilMs: Long = 0L,

    // ── Launcher and app-picker preferences ───────────────────────────────────
    val launcherTheme: String = "glassy",
    val launcherWallpaperUri: String? = null,
    val focusToolPackages: List<String> = emptyList(),
    val launcherHiddenPackages: List<String> = emptyList(),
    val launcherLockDuringStandalone: Boolean = true,
    val launcherPresets: List<AllowedAppPreset> = emptyList(),

    // ── Daily allowance ───────────────────────────────────────────────────────
    /**
     * JSON-encoded allowance config.
     * Setter/read path: SettingsRepository.setDailyAllowanceConfig/readAppSettings.
     */
    val dailyAllowanceConfigJson: String? = null,

    // ── Recurring block schedules ─────────────────────────────────────────────
    /**
     * Persisted by SettingsRepository.setRecurringBlockSchedules and mirrored
     * into the service's greyout schedule format.
     */
    val recurringBlockSchedules: List<RecurringBlockSchedule> = emptyList(),

    // ── Network / VPN ────────────────────────────────────────────────────────
    /**
     * Setter: SettingsRepository.setNetworkBlockEnabled(enabled).
     * NO GETTER on SettingsRepository.
     */
    val networkBlockEnabled: Boolean = false,

    // ── System guard ─────────────────────────────────────────────────────────
    val systemGuardEnabled: Boolean = false,

    // ── Notification and insight preferences ─────────────────────────────────
    /**
     * Notification toggles used by the analytics-driven notification layer.
     * These are deliberately explicit so older settings snapshots can continue
     * to deserialize with the reference defaults.
     */
    val morningDigestEnabled: Boolean = true,
    val achievementNotificationsEnabled: Boolean = true,
    val patternInsightNotificationsEnabled: Boolean = false,
    val rescheduleNotificationsEnabled: Boolean = true,
    val blockSuggestionEnabled: Boolean = true,
    val weekAheadEnabled: Boolean = true,
    val temptationSpikeEnabled: Boolean = false,
    val temptationSpikeThreshold: Int = 8,
    val bedTime: String = "22:00",
    val productiveWindowNudgeEnabled: Boolean = false,
    val lastSessionResultByTaskId: Map<String, String> = emptyMap(),
    val shownPatternInsightIds: List<String> = emptyList(),
    val lastShownDebriefSessionId: Int? = null,

    // ── Productivity preferences ─────────────────────────────────────────────
    val taskRemindersEnabled: Boolean = true,
    val defaultDurationMinutes: Int = 60,
    val autoFocusEnabled: Boolean = false,
    val allowedFocusPackages: List<String> = emptyList(),
    val pomodoroEnabled: Boolean = false,
    val pomodoroWorkMinutes: Int = 25,
    val pomodoroBreakMinutes: Int = 5,
    val focusDefenseHintDismissed: Boolean = false,
    val localAnalyticsNoticeDismissed: Boolean = false,
)

/**
 * Named app selection saved from the allowed-app picker.
 *
 * The empty list and [BLOCK_ALL_SENTINEL] are intentionally preserved as
 * distinct values by the picker because they mean "allow none" and "block all"
 * in different caller contexts.
 */
data class AllowedAppPreset(
    val id: String,
    val name: String,
    val packages: List<String>,
)

const val BLOCK_ALL_SENTINEL = "__block_all__"

/**
 * A time-windowed block schedule (e.g. "block social apps 10pm–7am daily").
 *
 * Persisted by SettingsRepository.setRecurringBlockSchedules().
 */
data class RecurringBlockSchedule(
    val id: String,
    val packages: List<String>,
    val startHour: Int,   // 0..23
    val endHour: Int,     // 0..23
    val daysOfWeek: List<Int>, // 0=Sun..6=Sat
    val enabled: Boolean = true,
)

/**
 * Config passed to SettingsViewModel.setDailyAllowanceEntries().
 * Serialized to JSON and stored via SettingsRepository.setDailyAllowanceConfig().
 */
data class DailyAllowanceEntry(
    val packageName: String,
    val dailyAllowanceMs: Long,
    val mode: String = "time_budget",
    val countPerDay: Int = 1,
    val budgetMinutes: Int = (dailyAllowanceMs / 60_000L).coerceAtLeast(1L).toInt(),
    val intervalMinutes: Int = 5,
    val intervalHours: Int = 1,
)

/**
 * Config passed to SettingsViewModel.setStandaloneBlock().
 * Maps to SettingsRepository.setStandaloneBlock(active, packages, untilMs, pinHash).
 */
data class StandaloneBlockConfig(
    val active: Boolean,
    val packages: List<String>,
    val untilMs: Long,
    val pinHash: String? = null,
)

/**
 * Used by SettingsViewModel.setQuickBlockTemporary, which maps the duration
 * to the standalone block expiry timestamp.
 */
data class QuickBlockConfig(
    val packages: List<String>,
    val durationMs: Long,
)

/**
 * Persisted atomically by SettingsRepository.publishStandaloneAndAllowanceSnapshot.
 */
data class StandaloneBlockAndAllowanceConfig(
    val standaloneBlockActive: Boolean,
    val standaloneBlockPackages: List<String>,
    val standaloneBlockUntilMs: Long,
    val allowanceEntries: List<DailyAllowanceEntry>,
    val pinHash: String? = null,
)
