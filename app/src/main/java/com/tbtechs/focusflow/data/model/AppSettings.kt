package com.tbtechs.focusflow.data.model

/**
 * AppSettings
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
    val standaloneBlockVpnPackages: List<String> = emptyList(),
    val standaloneBlockUntilMs: Long = 0L,

    // ── Launcher and app-picker preferences ───────────────────────────────────
    val launcherTheme: String = "glassy",
    val launcherWallpaperUri: String? = null,
    val focusToolPackages: List<String> = emptyList(),
    val launcherHiddenPackages: List<String> = emptyList(),
    val launcherLockDuringStandalone: Boolean = true,
    val launcherBlockUninstall: Boolean = false,
    val launcherPresets: List<AllowedAppPreset> = emptyList(),
    val launcherDockPackages: List<String> = emptyList(),
    val launcherClockStyle: String = "",

    // ── Portable preset and overlay settings ───────────────────────────────────
    val blockPresets: List<BlockPreset> = emptyList(),
    val overlayQuotes: List<String> = emptyList(),

    /**
     * Kept distinct from the network-block explicit package list: those are
     * separate policy sources and must not overwrite one another.
     */
    val alwaysOnVpnPackages: List<String> = emptyList(),

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
    /** User-authored windows only; derived schedule windows remain separate. */
    val userGreyoutWindowsJson: String = "[]",

    // ── Network / VPN ────────────────────────────────────────────────────────
    /**
     * Setter: SettingsRepository.setNetworkBlockEnabled(enabled).
     * NO GETTER on SettingsRepository.
     */
    val networkBlockEnabled: Boolean = false,
    val vpnSelfHealEnabled: Boolean = false,
    val focusMirrorVpnEnabled: Boolean = false,

    // ── System guard ─────────────────────────────────────────────────────────
    val systemGuardEnabled: Boolean = false,
    val blockInstallActionsEnabled: Boolean = false,
    val blockYoutubeShortsEnabled: Boolean = false,
    val blockInstagramReelsEnabled: Boolean = false,
    val aversionDimmerEnabled: Boolean = false,
    val aversionVibrateEnabled: Boolean = false,
    val aversionSoundEnabled: Boolean = false,

    // ── Focus and standalone behavior ────────────────────────────────────────
    val keepFocusActiveUntilTaskEnd: Boolean = true,
    val autoRescheduleEnabled: Boolean = false,
    val autoCopyToAlwaysOn: Boolean = false,

    // ── Notification and insight preferences ─────────────────────────────────
    /** App-wide theme preference. True uses the dark Material 3 palette. */
    val darkModeEnabled: Boolean = true,

    // ── Text size ─────────────────────────────────────────────────────────────
    /** Default scale for all tabs; nullable per-tab values replace it when set. */
    val generalTextScale: Float = 1f,
    /** null inherits generalTextScale; a value replaces it for that tab. */
    val homeTextScale: Float? = null,
    val focusTextScale: Float? = null,
    val statsTextScale: Float? = null,
    val settingsTextScale: Float? = null,
    val defenseTextScale: Float? = null,
    /** Optional per-screen scales keyed by tab route and destination ID. */
    val screenTextScales: Map<String, Float> = emptyMap(),

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
    val taskPreStartRemindersEnabled: Boolean = true,
    val taskAtStartReminderEnabled: Boolean = true,
    val taskMidSessionRemindersEnabled: Boolean = true,
    val taskWrapUpReminderEnabled: Boolean = true,
    val taskEndAlarmRings: Boolean = true,
    val reflectionPromptsEnabled: Boolean = true,
    val defaultDurationMinutes: Int = 60,
    val autoFocusEnabled: Boolean = false,
    val allowedFocusPackages: List<String> = emptyList(),
    val pomodoroEnabled: Boolean = false,
    val pomodoroWorkMinutes: Int = 25,
    val pomodoroBreakMinutes: Int = 5,
    val focusDefenseHintDismissed: Boolean = false,
    val localAnalyticsNoticeDismissed: Boolean = false,
    val standaloneBlockHintDismissed: Boolean = false,
    val alwaysOnInfoDismissed: Boolean = false,
    val protectionStatusBannerDismissed: Boolean = false,
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

/** Named block selection, stored separately from allowed-app presets. */
data class BlockPreset(
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
    val startMinute: Int = 0, // 0..59
    val endHour: Int,     // 0..23
    val endMinute: Int = 0, // 0..59
    val daysOfWeek: List<Int>, // 0=Sun..6=Sat
    val enabled: Boolean = true,
    val vpnEnabled: Boolean = false,
    val name: String = "",
    val vpnPackages: List<String> = emptyList(),
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
    val standaloneBlockVpnPackages: List<String> = emptyList(),
    val pinHash: String? = null,
)
