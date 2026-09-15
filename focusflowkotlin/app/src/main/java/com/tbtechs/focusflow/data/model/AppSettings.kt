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
)

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
