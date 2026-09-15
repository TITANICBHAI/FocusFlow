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
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.domain.PinManager
import com.tbtechs.focusflow.domain.PinReuseTracker
import com.tbtechs.focusflow.domain.PinSessionState
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * ─── FLAGS ────────────────────────────────────────────────────────────────────
 *
 * FLAG-1  settings StateFlow is write-through only. SettingsRepository is a
 *         SharedPreferences write-only facade — it has no getters for most
 *         fields. Initial on-disk state is NOT loaded into [settings]. To fix:
 *         add get*() methods to SettingsRepository for each field.
 *
 * FLAG-2  [setRecurringBlockSchedules] is STUBBED. SettingsRepository has no
 *         method for recurring block schedules. Requires a new
 *         SettingsRepository.setRecurringBlockSchedules() backed by a
 *         SharedPreferences key that AppBlockerAccessibilityService reads.
 *
 * FLAG-3  [setQuickBlockTemporary] is STUBBED. No clear backing method in
 *         SettingsRepository. Likely maps to setStandaloneBlock() with an
 *         auto-computed untilMs, but the atomic behavior is undefined.
 *
 * FLAG-4  [setStandaloneBlockAndAllowance] is STUBBED. No combined atomic
 *         setter in SettingsRepository. publishStandaloneSnapshot() is the
 *         closest but does not update allowance state.
 *
 * FLAG-5  [rotatePin] uses ReuseTrackerKey.FOCUS for the defense PIN. This
 *         semantic mapping is provisional — the reuse tracker was originally
 *         designed for focus-session and always-on PINs. Confirm whether a
 *         dedicated key is needed for the defense PIN.
 *
 * GPT Terra: treat the public API surface here as the stable contract.
 * Do not change method signatures.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val pinManager: PinManager,
    context: Context,
) : ViewModel() {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(AppBlockerAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)

    // ─── Settings state ───────────────────────────────────────────────────────

    /**
     * Current app settings. Write-through: starts from defaults, updated on
     * each setter call. See FLAG-1 — initial on-disk state is not loaded.
     *
     * Backing source: this ViewModel (maintained internally). Individual field
     * setters write through to [SettingsRepository].
     */
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

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
            // alwaysBlock: SettingsRepository.setAlwaysBlockActive(active, packages)
            if (newSettings.alwaysBlockEnabled != current.alwaysBlockEnabled ||
                newSettings.alwaysBlockPackages != current.alwaysBlockPackages
            ) {
                settingsRepository.setAlwaysBlockActive(
                    newSettings.alwaysBlockEnabled,
                    newSettings.alwaysBlockPackages,
                )
            }
            // recurringBlockSchedules: STUBBED (FLAG-2)
            // dailyAllowanceConfigJson: handled via setDailyAllowanceEntries()
            // pinProtectionEnabled: managed via setPin()/clearPin() — not updated here

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

    /**
     * ⚠ STUB — FLAG-2
     *
     * SettingsRepository has no method for recurring block schedules.
     * This method is a no-op until SettingsRepository.setRecurringBlockSchedules()
     * is added and AppBlockerAccessibilityService is updated to read the key.
     *
     * Backing call: NONE — do not wire this in GPT Terra until the backing is added.
     */
    fun setRecurringBlockSchedules(schedules: List<RecurringBlockSchedule>) {
        // TODO (Track D or post-Terra): implement once SettingsRepository gains
        //   setRecurringBlockSchedules(schedules: List<RecurringBlockSchedule>)
        //   and AppBlockerAccessibilityService reads the corresponding key.
        _settings.update { it.copy(recurringBlockSchedules = schedules) }
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
     * ⚠ STUB — FLAG-3
     *
     * No backing method in SettingsRepository for a "quick block temporary" operation.
     * Likely maps to setStandaloneBlock() with auto-computed untilMs =
     * System.currentTimeMillis() + config.durationMs, but the exact behavior
     * and enforcement keys are undefined. Do not wire in GPT Terra until clarified.
     *
     * Backing call: NONE.
     */
    fun setQuickBlockTemporary(config: QuickBlockConfig) {
        // TODO: implement once SettingsRepository.setQuickBlockTemporary() is defined.
        // Provisional: could map to setStandaloneBlock(active=true, packages=config.packages,
        // untilMs=System.currentTimeMillis()+config.durationMs)
        // but atomic behavior with allowance state is unconfirmed.
    }

    /**
     * ⚠ STUB — FLAG-4
     *
     * No atomic setter in SettingsRepository for standalone block + allowance together.
     * publishStandaloneSnapshot() is the closest existing method but does not update
     * allowance state. Do not wire in GPT Terra until SettingsRepository provides an
     * atomic combined setter.
     *
     * Backing call: NONE.
     */
    fun setStandaloneBlockAndAllowance(config: StandaloneBlockAndAllowanceConfig) {
        // TODO: implement once SettingsRepository gains an atomic combined setter.
        // Two separate calls (setStandaloneBlock + setDailyAllowanceEntries) are
        // not atomic and risk a window where block state and allowance state diverge.
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
     * FLAG-5: uses ReuseTrackerKey.FOCUS for the defense PIN — confirm if a
     * dedicated key is needed.
     */
    fun rotatePin(oldPin: String, newPin: String): Boolean {
        if (!pinManager.verifyPin(oldPin)) return false

        if (newPin == oldPin) {
            val reuseInfo = PinReuseTracker.getPinReuseInfo(prefs, PinReuseTracker.ReuseTrackerKey.FOCUS)
            if (!reuseInfo.canReuse) return false
            PinReuseTracker.recordPinReuse(prefs, PinReuseTracker.ReuseTrackerKey.FOCUS)
            return true
        }

        pinManager.setPin(newPin)
        return true
    }
}
