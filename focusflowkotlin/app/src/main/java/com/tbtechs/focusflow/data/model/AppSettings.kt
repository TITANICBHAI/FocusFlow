package com.tbtechs.focusflow.data.model

/**
 * AppSettings
 *
 * ⚠ FLAG — MISSING FROM REPLIT STAGE 2 OUTPUT
 *
 * This file should have been produced by Replit's SettingsRepository work. It was not.
 * This is the authoritative definition until Replit's output is extended.
 *
 * IMPORTANT CONSTRAINTS:
 *   - SettingsRepository is a SharedPreferences *write-only* facade — it has individual
 *     setters but almost no getters. settings: StateFlow<AppSettings> in SettingsViewModel
 *     is therefore maintained write-through: it starts from defaults and is updated
 *     incrementally as each setter is called. The initial on-disk state (if the app was
 *     previously installed) is NOT loaded into this StateFlow.
 *   - Fields commented "NO GETTER" have no corresponding SettingsRepository.get*() method.
 *     Adding getters to SettingsRepository is required before initial state can be loaded.
 *   - Fields commented "NO SETTER" have no backing SettingsRepository method at all.
 *     Those SettingsViewModel methods are stubbed; see STUB comments there.
 *
 * GPT Terra: treat this as the stable contract. Do not redefine AppSettings.
 */
data class AppSettings(

    // ── PIN protection ────────────────────────────────────────────────────────
    /** True when a defense PIN is configured. Backed by PinManager.isPinSet(). */
    val pinProtectionEnabled: Boolean = false,

    // ── Content blocking ──────────────────────────────────────────────────────
    /**
     * Package names blocked by the "Always On" enforcement layer.
     * Setter: SettingsRepository.setAlwaysBlockActive(active, packages).
     * NO GETTER on SettingsRepository.
     */
    val alwaysBlockPackages: List<String> = emptyList(),
    val alwaysBlockEnabled: Boolean = false,

    /**
     * Words whose presence in an app's accessibility-visible text triggers a block.
     * Setter: SettingsRepository.setBlockedWords(words).
     * NO GETTER on SettingsRepository.
     */
    val blockedWords: List<String> = emptyList(),

    // ── Standalone block ──────────────────────────────────────────────────────
    /**
     * State of the user-initiated standalone block (not focus-session-linked).
     * Setter: SettingsRepository.setStandaloneBlock(active, packages, untilMs, pinHash).
     * NO GETTER on SettingsRepository.
     */
    val standaloneBlockActive: Boolean = false,
    val standaloneBlockPackages: List<String> = emptyList(),
    val standaloneBlockUntilMs: Long = 0L,

    // ── Daily allowance ───────────────────────────────────────────────────────
    /**
     * JSON-encoded allowance config.
     * Setter: SettingsRepository.setDailyAllowanceConfig(configJson).
     * NO GETTER on SettingsRepository (use AllowanceSnapshot for enforcement reads).
     */
    val dailyAllowanceConfigJson: String? = null,

    // ── Recurring block schedules ─────────────────────────────────────────────
    /**
     * ⚠ NO SETTER on SettingsRepository — setRecurringBlockSchedules is STUBBED.
     * Requires a new SettingsRepository method before it can be implemented.
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
 * ⚠ FLAG — This type has no backing setter in SettingsRepository.
 * It is defined here so the contract compiles; implementation requires
 * SettingsRepository.setRecurringBlockSchedules().
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
 * ⚠ FLAG — No SettingsRepository method backs this yet.
 * Placeholder so the ViewModel method signature compiles.
 * Intended for a temporary "quick block" with auto-expiry — likely maps to
 * setStandaloneBlock + setDailyAllowancePackages in combination, but the
 * exact atomic operation is undefined without a new SettingsRepository method.
 */
data class QuickBlockConfig(
    val packages: List<String>,
    val durationMs: Long,
)

/**
 * ⚠ FLAG — No SettingsRepository method backs this yet.
 * Intended for atomically setting standalone block state AND allowance state
 * together. Requires a new combined SettingsRepository method (publishStandaloneSnapshot
 * is the closest existing method but does not update allowance).
 */
data class StandaloneBlockAndAllowanceConfig(
    val standaloneBlockActive: Boolean,
    val standaloneBlockPackages: List<String>,
    val standaloneBlockUntilMs: Long,
    val allowanceEntries: List<DailyAllowanceEntry>,
    val pinHash: String? = null,
)
