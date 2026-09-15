package com.tbtechs.focusflow.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.DailyAllowanceEntry
import com.tbtechs.focusflow.data.model.QuickBlockConfig
import com.tbtechs.focusflow.data.model.RecurringBlockSchedule
import com.tbtechs.focusflow.data.model.StandaloneBlockAndAllowanceConfig
import com.tbtechs.focusflow.data.model.StandaloneBlockConfig
import com.tbtechs.focusflow.data.repository.AllowanceUsage
import com.tbtechs.focusflow.data.repository.AllowanceSnapshot
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.domain.PinManager
import com.tbtechs.focusflow.domain.FocusPinManager
import com.tbtechs.focusflow.domain.PinReuseTracker
import com.tbtechs.focusflow.domain.PinSessionState
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * SettingsViewModel
 *
 * Exposes app settings state and PIN management to the UI layer.
 *
 * Backed by:
 *   - [SettingsRepository]  (Replit Stage 2)
 *   - [PinManager]          (Track B)
 *   - [PinReuseTracker]     (Track B)
 *   - [PinSessionState]     (Track B)
 *
 * Defense PIN rotation uses ReuseTrackerKey.ALWAYSON. The source tracker has
 * no dedicated defense bucket; FOCUS remains reserved for session PINs.
 *
 * GPT Terra: treat the public API surface here as the stable contract.
 * Do not change method signatures.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val pinManager: PinManager,
    context: Context,
) : ViewModel() {

    private val focusPinManager = FocusPinManager(context)
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(AppBlockerAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)

    // ─── Settings state ───────────────────────────────────────────────────────

    /**
     * Current app settings. Hydrated from the enforcement preference namespace
     * on startup, then updated on each setter call.
     */
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /**
     * Read-only allowance counters refreshed while a caller is observing them.
     * The repository reads the whole native snapshot under the same lock used by
     * the enforcement service, so the editor never writes or fabricates usage.
     */
    val allowanceSnapshot: StateFlow<AllowanceSnapshot> = flow {
        while (true) {
            emit(
                runCatching {
                    settingsRepository.getAllowanceSnapshot()
                }.getOrDefault(
                    AllowanceSnapshot(
                        usageJson = null,
                        configJson = null,
                        activeSessionPackage = null,
                        activeSessionEndMs = 0L,
                    ),
                ),
            )
            delay(1_000)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AllowanceSnapshot(
            usageJson = null,
            configJson = null,
            activeSessionPackage = null,
            activeSessionEndMs = 0L,
        ),
    )

    val allowanceUsage: StateFlow<Map<String, AllowanceUsage>> = flow {
        allowanceSnapshot.collect { emit(it.usageByPackage) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyMap(),
    )

    init {
        viewModelScope.launch {
            _settings.value = settingsRepository.readAppSettings().copy(
                pinProtectionEnabled = pinManager.isPinSet(),
            )
        }
    }

    // ─── PIN session state ────────────────────────────────────────────────────

    /**
     * True while the defense PIN unlock window is active (the user verified the
     * PIN within [PinSessionState.SESSION_UNLOCK_DURATION_MS] ago).
     *
     * Polled every second. Does not persist across process death.
     *
     * Backing call: [PinSessionState.isUnlocked] (polling).
     */
    val isPinSessionActive: StateFlow<Boolean> = flow {
        while (true) {
            emit(PinSessionState.isUnlocked())
            delay(1_000)
        }
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = PinSessionState.isUnlocked(),
    )

    // ─── Settings mutations ───────────────────────────────────────────────────

    /**
     * Applies [newSettings] by calling the appropriate [SettingsRepository]
     * setter for each changed field, then updates the internal StateFlow.
     *
     * Backing calls: SettingsRepository.set*() per field (see inline comments).
     */
    fun updateSettings(newSettings: AppSettings) {
        viewModelScope.launch {
            val current = _settings.value

            // blockedWords: SettingsRepository.setBlockedWords(words)
            if (newSettings.blockedWords != current.blockedWords) {
                settingsRepository.setBlockedWords(newSettings.blockedWords)
            }
            // networkBlockEnabled: SettingsRepository.setNetworkBlockEnabled(enabled)
            if (newSettings.networkBlockEnabled != current.networkBlockEnabled) {
                settingsRepository.setNetworkBlockEnabled(newSettings.networkBlockEnabled)
            }
            // systemGuardEnabled: SettingsRepository.setSystemGuardEnabled(enabled)
            if (newSettings.systemGuardEnabled != current.systemGuardEnabled) {
                settingsRepository.setSystemGuardEnabled(newSettings.systemGuardEnabled)
            }
            if (newSettings.blockInstallActionsEnabled != current.blockInstallActionsEnabled) {
                settingsRepository.setBlockInstallActionsEnabled(newSettings.blockInstallActionsEnabled)
            }
            if (newSettings.blockYoutubeShortsEnabled != current.blockYoutubeShortsEnabled) {
                settingsRepository.setBlockYoutubeShortsEnabled(newSettings.blockYoutubeShortsEnabled)
            }
            if (newSettings.blockInstagramReelsEnabled != current.blockInstagramReelsEnabled) {
                settingsRepository.setBlockInstagramReelsEnabled(newSettings.blockInstagramReelsEnabled)
            }
            // alwaysBlock: SettingsRepository.setAlwaysBlockActive(active, packages)
            if (newSettings.alwaysBlockEnabled != current.alwaysBlockEnabled ||
                newSettings.alwaysBlockPackages != current.alwaysBlockPackages
            ) {
                settingsRepository.setAlwaysBlockActive(
                    newSettings.alwaysBlockEnabled,
                    newSettings.alwaysBlockPackages,
                )
            }
            if (newSettings.recurringBlockSchedules != current.recurringBlockSchedules) {
                settingsRepository.setRecurringBlockSchedules(newSettings.recurringBlockSchedules)
            }
            if (newSettings.launcherTheme != current.launcherTheme) {
                settingsRepository.setLauncherTheme(newSettings.launcherTheme)
            }
            if (newSettings.launcherWallpaperUri != current.launcherWallpaperUri) {
                settingsRepository.setLauncherWallpaperUri(newSettings.launcherWallpaperUri)
            }
            if (newSettings.focusToolPackages != current.focusToolPackages) {
                settingsRepository.setFocusToolPackages(JSONArray(newSettings.focusToolPackages).toString())
            }
            if (newSettings.launcherHiddenPackages != current.launcherHiddenPackages) {
                settingsRepository.setLauncherHiddenPackages(
                    JSONArray(newSettings.launcherHiddenPackages).toString(),
                )
            }
            if (newSettings.launcherLockDuringStandalone != current.launcherLockDuringStandalone) {
                settingsRepository.setLauncherLockDuringStandalone(newSettings.launcherLockDuringStandalone)
            }
            if (newSettings.launcherPresets != current.launcherPresets) {
                settingsRepository.setLauncherPresets(newSettings.launcherPresets)
            }
            if (newSettings != current) {
                settingsRepository.setNotificationPreferences(newSettings)
            }

            _settings.value = newSettings
        }
    }

    /**
     * Serializes [entries] to JSON and writes the daily allowance config.
     * Updates the [settings] StateFlow.
     *
     * Backing call: [SettingsRepository.setDailyAllowanceConfig]
     */
    fun setDailyAllowanceEntries(entries: List<DailyAllowanceEntry>) {
        viewModelScope.launch {
            val json = JSONArray().also { arr ->
                entries.forEach { entry ->
                    arr.put(JSONObject().apply {
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
            settingsRepository.setDailyAllowanceConfig(json)
            _settings.update { it.copy(dailyAllowanceConfigJson = json) }
        }
    }

    /**
     * Writes [words] to the enforcement layer's blocked-word list.
     * Updates the [settings] StateFlow.
     *
     * Backing call: [SettingsRepository.setBlockedWords]
     */
    fun setBlockedWords(words: List<String>) {
        viewModelScope.launch {
            settingsRepository.setBlockedWords(words)
            _settings.update { it.copy(blockedWords = words) }
        }
    }

    /** Persists recurring block schedules and updates the settings snapshot. */
    fun setRecurringBlockSchedules(schedules: List<RecurringBlockSchedule>) {
        viewModelScope.launch {
            settingsRepository.setRecurringBlockSchedules(schedules)
            _settings.update { it.copy(recurringBlockSchedules = schedules) }
        }
    }

    /**
     * Sets the standalone block configuration.
     * Updates the [settings] StateFlow.
     *
     * Backing call: [SettingsRepository.setStandaloneBlock]
     */
    fun setStandaloneBlock(config: StandaloneBlockConfig) {
        viewModelScope.launch {
            settingsRepository.setStandaloneBlock(
                active   = config.active,
                packages = config.packages,
                untilMs  = config.untilMs,
                pinHash  = config.pinHash,
            )
            _settings.update {
                it.copy(
                    standaloneBlockActive   = config.active,
                    standaloneBlockPackages = config.packages,
                    standaloneBlockUntilMs  = config.untilMs,
                )
            }
        }
    }

    /**
     * Starts a temporary standalone block using the same persisted state as the
     * regular standalone-block flow.
     */
    fun setQuickBlockTemporary(config: QuickBlockConfig) {
        viewModelScope.launch {
            val untilMs = System.currentTimeMillis() + config.durationMs.coerceAtLeast(0L)
            settingsRepository.setStandaloneBlock(
                active = true,
                packages = config.packages,
                untilMs = untilMs,
            )
            _settings.update {
                it.copy(
                    standaloneBlockActive = true,
                    standaloneBlockPackages = config.packages,
                    standaloneBlockUntilMs = untilMs,
                )
            }
        }
    }

    /**
     * Commits standalone-block and allowance state together through the repository's
     * synchronous snapshot write.
     */
    fun setStandaloneBlockAndAllowance(config: StandaloneBlockAndAllowanceConfig) {
        viewModelScope.launch {
            val allowanceJson = JSONArray().also { arr ->
                config.allowanceEntries.forEach { entry ->
                    arr.put(JSONObject().apply {
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
            settingsRepository.publishStandaloneAndAllowanceSnapshot(
                active = config.standaloneBlockActive,
                packages = config.standaloneBlockPackages,
                untilMs = config.standaloneBlockUntilMs,
                allowanceEntries = config.allowanceEntries,
                pinHash = config.pinHash,
            )
            _settings.update {
                it.copy(
                    standaloneBlockActive = config.standaloneBlockActive,
                    standaloneBlockPackages = config.standaloneBlockPackages,
                    standaloneBlockUntilMs = config.standaloneBlockUntilMs,
                    dailyAllowanceConfigJson = allowanceJson,
                )
            }
        }
    }

    // ─── PIN management ───────────────────────────────────────────────────────

    /**
     * Verifies [pin] against the stored defense PIN.
     * On success, unlocks the PIN session window via [PinSessionState.unlock].
     * Returns true if the PIN matches (or no PIN is set).
     *
     * Backing call: [PinManager.verifyPin] → [PinSessionState.unlock]
     */
    fun verifyPin(pin: String): Boolean {
        val ok = pinManager.verifyPin(pin)
        if (ok) PinSessionState.unlock()
        return ok
    }

    /**
     * Sets a new defense PIN.
     * Updates [settings.pinProtectionEnabled] to true.
     *
     * Backing call: [PinManager.setPin]
     */
    fun setPin(newPin: String) {
        viewModelScope.launch {
            pinManager.setPin(newPin)
            _settings.update { it.copy(pinProtectionEnabled = true) }
        }
    }

    /** Clears the defense PIN. The caller must have verified it first. */
    fun clearPin() {
        viewModelScope.launch {
            pinManager.clearPin()
            _settings.update { it.copy(pinProtectionEnabled = false) }
        }
    }

    fun isFocusPinSet(): Boolean = focusPinManager.isPinSet()

    fun setFocusPin(pin: String) = focusPinManager.setPin(pin)

    fun verifyFocusPin(pin: String): Boolean = focusPinManager.verifyPin(pin)

    fun clearFocusPin(pin: String): Boolean = focusPinManager.clearPin(pin)

    /**
     * Rotates the defense PIN from [oldPin] to [newPin].
     *
     * Rules:
     *   - Verifies [oldPin] first; returns false if it doesn't match.
     *   - If [newPin] == [oldPin]: checks daily reuse limit via [PinReuseTracker].
     *     Records a reuse if permitted; returns false if the daily limit is hit.
     *   - If [newPin] != [oldPin]: calls [PinManager.setPin]. No reuse check needed.
     *
     * Backing calls: [PinManager.verifyPin], [PinReuseTracker.getPinReuseInfo],
     *                [PinReuseTracker.recordPinReuse], [PinManager.setPin]
     *
     * The source has no dedicated defense bucket; the defense/protection PIN
     * uses the Always-On counter, while FOCUS remains for session PINs.
     */
    fun rotatePin(oldPin: String, newPin: String): Boolean {
        if (!pinManager.verifyPin(oldPin)) return false

        if (newPin == oldPin) {
            val reuseInfo = PinReuseTracker.getPinReuseInfo(prefs, PinReuseTracker.ReuseTrackerKey.ALWAYSON)
            if (!reuseInfo.canReuse) return false
            PinReuseTracker.recordPinReuse(prefs, PinReuseTracker.ReuseTrackerKey.ALWAYSON)
            return true
        }

        pinManager.setPin(newPin)
        _settings.update { it.copy(pinProtectionEnabled = true) }
        return true
    }
}
