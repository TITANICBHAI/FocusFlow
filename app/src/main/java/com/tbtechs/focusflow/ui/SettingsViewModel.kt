package com.tbtechs.focusflow.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.DailyAllowanceEntry
import com.tbtechs.focusflow.data.model.QuickBlockConfig
import com.tbtechs.focusflow.data.model.RecurringBlockSchedule
import com.tbtechs.focusflow.data.restore.RestoreGate
import com.tbtechs.focusflow.data.model.StandaloneBlockAndAllowanceConfig
import com.tbtechs.focusflow.data.model.StandaloneBlockConfig
import com.tbtechs.focusflow.data.repository.AllowanceUsage
import com.tbtechs.focusflow.data.repository.AllowanceSnapshot
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.data.repository.SetupPersistenceManager
import com.tbtechs.focusflow.data.repository.VpnSelfHealPolicy
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.domain.PinManager
import com.tbtechs.focusflow.domain.FocusPinManager
import com.tbtechs.focusflow.domain.PinReuseTracker
import com.tbtechs.focusflow.domain.PinSessionState
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import com.tbtechs.focusflow.enforcement.DayRatingNotificationScheduler
import com.tbtechs.focusflow.ui.common.AppErrorEvents
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
    private val restoreGate: RestoreGate = RestoreGate(),
) : ViewModel() {

    private val appContext = context.applicationContext
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
     * Startup gates retain the existing preference contract used by the
     * privacy and onboarding screens. They are exposed separately from
     * AppSettings because these flags control routing rather than enforcement.
     */
    val privacyAccepted: StateFlow<Boolean> = persistedFlag(SetupPersistenceManager.KEY_PRIVACY_ACCEPTED)
    val onboardingComplete: StateFlow<Boolean> = persistedFlag(SetupPersistenceManager.KEY_ONBOARDING_COMPLETE)

    private fun persistedFlag(key: String): StateFlow<Boolean> = flow {
        while (true) {
            emit(settingsRepository.setupPersistence.readDurableFlag(key))
            delay(250)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = settingsRepository.setupPersistence.readDurableFlag(key),
    )

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

    suspend fun refreshSettingsFromStore() {
        _settings.value = settingsRepository.readAppSettings().copy(
            pinProtectionEnabled = pinManager.isPinSet(),
        )
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
            var taskReminderPreferenceChanged = false
            var dayRatingScheduleChanged = false
            restoreGate.write("SettingsViewModel.updateSettings") {
            val current = _settings.value
            var networkBlockEnabledForUi = newSettings.networkBlockEnabled
            val selfHealEnabled = VpnSelfHealPolicy.valueToPersist(
                loadedValue = current.vpnSelfHealEnabled,
                requestedValue = newSettings.vpnSelfHealEnabled,
                storedValue = settingsRepository.isVpnSelfHealEnabledNow(),
            )
            taskReminderPreferenceChanged =
                newSettings.taskRemindersEnabled != current.taskRemindersEnabled ||
                    newSettings.taskPreStartRemindersEnabled != current.taskPreStartRemindersEnabled ||
                    newSettings.taskAtStartReminderEnabled != current.taskAtStartReminderEnabled ||
                    newSettings.taskMidSessionRemindersEnabled != current.taskMidSessionRemindersEnabled ||
                    newSettings.taskWrapUpReminderEnabled != current.taskWrapUpReminderEnabled ||
                    newSettings.taskEndAlarmRings != current.taskEndAlarmRings
            dayRatingScheduleChanged =
                newSettings.reflectionPromptsEnabled != current.reflectionPromptsEnabled ||
                    newSettings.bedTime != current.bedTime

            // blockedWords: SettingsRepository.setBlockedWords(words)
            if (newSettings.blockedWords != current.blockedWords) {
                settingsRepository.setBlockedWords(newSettings.blockedWords)
            }
            if (newSettings.dailyAllowanceConfigJson != current.dailyAllowanceConfigJson) {
                settingsRepository.setDailyAllowanceConfig(
                    newSettings.dailyAllowanceConfigJson ?: "[]",
                )
            }
            // networkBlockEnabled: SettingsRepository.setNetworkBlockEnabled(enabled)
            if (newSettings.networkBlockEnabled != current.networkBlockEnabled) {
                networkBlockEnabledForUi =
                    NetworkBlockSettingsUpdatePolicy.persistToggleOrRestoreStoredValue(
                        requestedValue = newSettings.networkBlockEnabled,
                        persist = settingsRepository::setNetworkBlockEnabled,
                        readStoredValue = settingsRepository::isNetworkBlockEnabledNow,
                    ) { exception ->
                        AppErrorEvents.report(
                            tag = "Network Blocking",
                            message = exception.message
                                ?: "Network Blocking could not be changed during an active block.",
                            throwable = exception,
                        )
                    }
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
            if (newSettings.alwaysOnVpnPackages != current.alwaysOnVpnPackages) {
                settingsRepository.setAlwaysOnVpnPackages(newSettings.alwaysOnVpnPackages)
            }
            if (newSettings.blockPresets != current.blockPresets) {
                settingsRepository.setBlockPresets(newSettings.blockPresets)
            }
            if (newSettings.overlayQuotes != current.overlayQuotes) {
                settingsRepository.setOverlayQuotes(newSettings.overlayQuotes)
            }
            if (newSettings.recurringBlockSchedules != current.recurringBlockSchedules) {
                settingsRepository.setRecurringBlockSchedules(newSettings.recurringBlockSchedules)
            }
            if (newSettings.userGreyoutWindowsJson != current.userGreyoutWindowsJson) {
                settingsRepository.setUserGreyoutWindows(newSettings.userGreyoutWindowsJson)
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
            if (newSettings.launcherDockPackages != current.launcherDockPackages) {
                settingsRepository.setLauncherDockPackages(newSettings.launcherDockPackages)
            }
            if (newSettings.launcherClockStyle != current.launcherClockStyle) {
                settingsRepository.setLauncherClockStyle(newSettings.launcherClockStyle)
            }
            if (newSettings.launcherLockDuringStandalone != current.launcherLockDuringStandalone) {
                settingsRepository.setLauncherLockDuringStandalone(newSettings.launcherLockDuringStandalone)
            }
            if (newSettings.launcherPresets != current.launcherPresets) {
                settingsRepository.setLauncherPresets(newSettings.launcherPresets)
            }
            if (
                newSettings.launcherBlockUninstall != current.launcherBlockUninstall ||
                newSettings.vpnSelfHealEnabled != current.vpnSelfHealEnabled ||
                newSettings.focusMirrorVpnEnabled != current.focusMirrorVpnEnabled ||
                newSettings.aversionDimmerEnabled != current.aversionDimmerEnabled ||
                newSettings.aversionVibrateEnabled != current.aversionVibrateEnabled ||
                newSettings.aversionSoundEnabled != current.aversionSoundEnabled ||
                newSettings.keepFocusActiveUntilTaskEnd != current.keepFocusActiveUntilTaskEnd ||
                newSettings.autoRescheduleEnabled != current.autoRescheduleEnabled ||
                newSettings.autoCopyToAlwaysOn != current.autoCopyToAlwaysOn
            ) {
                settingsRepository.setDefensePreferences(
                    newSettings.copy(vpnSelfHealEnabled = selfHealEnabled),
                )
            }
            if (newSettings != current) {
                settingsRepository.setNotificationPreferences(newSettings)
            }

            _settings.value = newSettings.copy(
                networkBlockEnabled = networkBlockEnabledForUi,
                vpnSelfHealEnabled = selfHealEnabled,
            )
            }
            if (dayRatingScheduleChanged) {
                DayRatingNotificationScheduler.ensureScheduled(appContext)
            }
            if (taskReminderPreferenceChanged) {
                AppModule.requestTaskAlarmReconciliation("task_alert_settings")
            }
        }
    }

    fun refreshFromStore() {
        viewModelScope.launch {
            _settings.value = settingsRepository.readAppSettings().copy(
                pinProtectionEnabled = pinManager.isPinSet(),
            )
        }
    }

    /** Raw profile metadata is stored beside enforcement settings for the profile screen. */
    fun getUserProfileJson(): String? = settingsRepository.getString("user_profile")

    /**
     * Serializes [entries] to JSON and writes the daily allowance config.
     * Updates the [settings] StateFlow.
     *
     * Backing call: [SettingsRepository.setDailyAllowanceConfig]
     */
    fun setDailyAllowanceEntries(entries: List<DailyAllowanceEntry>) {
        viewModelScope.launch {
            restoreGate.write("SettingsViewModel.setDailyAllowanceEntries") {
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
    }

    /**
     * Writes [words] to the enforcement layer's blocked-word list.
     * Updates the [settings] StateFlow.
     *
     * Backing call: [SettingsRepository.setBlockedWords]
     */
    fun setBlockedWords(words: List<String>) {
        viewModelScope.launch {
            restoreGate.write("SettingsViewModel.setBlockedWords") {
            settingsRepository.setBlockedWords(words)
            _settings.update { it.copy(blockedWords = words) }
            }
        }
    }

    /** Persists recurring block schedules and updates the settings snapshot. */
    fun setRecurringBlockSchedules(schedules: List<RecurringBlockSchedule>) {
        viewModelScope.launch {
            restoreGate.write("SettingsViewModel.setRecurringBlockSchedules") {
            settingsRepository.setRecurringBlockSchedules(schedules)
            _settings.update { it.copy(recurringBlockSchedules = schedules) }
            }
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
            restoreGate.write("SettingsViewModel.setStandaloneBlock") {
            val existing = _settings.value
            val keepCurrentVpnPackages = config.active &&
                existing.standaloneBlockActive &&
                (existing.standaloneBlockUntilMs <= 0L ||
                    existing.standaloneBlockUntilMs > System.currentTimeMillis())
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
                    standaloneBlockVpnPackages = if (keepCurrentVpnPackages) {
                        existing.standaloneBlockVpnPackages
                    } else {
                        emptyList()
                    },
                    standaloneBlockUntilMs  = config.untilMs,
                )
            }
            }
        }
    }

    /**
     * Starts a temporary standalone block using the same persisted state as the
     * regular standalone-block flow.
     */
    fun setQuickBlockTemporary(config: QuickBlockConfig) {
        viewModelScope.launch {
            restoreGate.write("SettingsViewModel.setQuickBlockTemporary") {
            val existing = _settings.value
            val existingBlockIsActive = existing.standaloneBlockActive &&
                (existing.standaloneBlockUntilMs <= 0L ||
                    existing.standaloneBlockUntilMs > System.currentTimeMillis())
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
                    standaloneBlockVpnPackages = if (existingBlockIsActive) {
                        existing.standaloneBlockVpnPackages
                    } else {
                        emptyList()
                    },
                    standaloneBlockUntilMs = untilMs,
                )
            }
            }
        }
    }

    /**
     * Commits standalone-block and allowance state together through the repository's
     * synchronous snapshot write.
     */
    fun setStandaloneBlockAndAllowance(config: StandaloneBlockAndAllowanceConfig) {
        viewModelScope.launch {
            restoreGate.write("SettingsViewModel.setStandaloneBlockAndAllowance") {
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
                vpnPackages = config.standaloneBlockVpnPackages,
                untilMs = config.standaloneBlockUntilMs,
                allowanceEntries = config.allowanceEntries,
                pinHash = config.pinHash,
            )
            _settings.update {
                it.copy(
                    standaloneBlockActive = config.standaloneBlockActive,
                    standaloneBlockPackages = config.standaloneBlockPackages,
                    standaloneBlockVpnPackages = if (config.standaloneBlockActive) {
                        config.standaloneBlockVpnPackages
                    } else {
                        emptyList()
                    },
                    standaloneBlockUntilMs = config.standaloneBlockUntilMs,
                    dailyAllowanceConfigJson = allowanceJson,
                )
            }
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
            return PinReuseTracker.tryRecordPinReuse(
                prefs,
                PinReuseTracker.ReuseTrackerKey.ALWAYSON,
            )
        }

        pinManager.setPin(newPin)
        _settings.update { it.copy(pinProtectionEnabled = true) }
        return true
    }

    fun alwaysOnPinReuseInfo(): PinReuseTracker.ReuseInfo =
        PinReuseTracker.getPinReuseInfo(prefs, PinReuseTracker.ReuseTrackerKey.ALWAYSON)

    fun keepAlwaysOnPin(): Boolean {
        return PinReuseTracker.tryRecordPinReuse(
            prefs,
            PinReuseTracker.ReuseTrackerKey.ALWAYSON,
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: runCatching { AppModule.applicationContext }.getOrNull()
                val ctx = app?.applicationContext
                    ?: throw IllegalStateException("Application context not available to instantiate SettingsViewModel")
                return SettingsViewModel(
                    settingsRepository = AppModule.settingsRepository,
                    pinManager = AppModule.pinManager,
                    context = ctx,
                    restoreGate = AppModule.restoreGate,
                ) as T
            }

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val ctx = runCatching { AppModule.applicationContext }.getOrNull()
                    ?: throw IllegalStateException("AppModule not initialized to instantiate SettingsViewModel")
                return SettingsViewModel(
                    settingsRepository = AppModule.settingsRepository,
                    pinManager = AppModule.pinManager,
                    context = ctx,
                    restoreGate = AppModule.restoreGate,
                ) as T
            }
        }
    }
}
