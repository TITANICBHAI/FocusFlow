package com.tbtechs.focusflow.ui

import android.content.Context
import android.content.SharedPreferences
import android.content.Context.MODE_PRIVATE
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tbtechs.focusflow.data.model.FocusSession
import com.tbtechs.focusflow.data.repository.FocusSessionRepository
import com.tbtechs.focusflow.data.repository.ForegroundServiceController
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.data.repository.TaskRepository
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.Instant

/**
 * FocusSessionViewModel
 *
 * Manages focus session lifecycle in the UI layer and keeps the UI-visible
 * session state in sync with Room and the enforcement SharedPreferences layer.
 *
 * Backed by:
 *   - [FocusSessionRepository] (Track A) — Room reads/writes
 *   - [TaskRepository]         (Track A) — task lookup for startFocusMode
 *   - [SettingsRepository]     (Replit Stage 2) — enforcement SharedPrefs sync
 *   - [ForegroundServiceController] (Replit Stage 2) — foreground service control
 *
 * ─── FLAGS ────────────────────────────────────────────────────────────────────
 *
 * FLAG-1  [focusSession] is backed by a MutableStateFlow, NOT a reactive Room
 *         Flow. FocusSessionDao has no @Query returning Flow<FocusSessionEntity?>
 *         for the active session — all queries are one-shot suspend functions.
 *         The StateFlow is populated on init and updated on startFocusMode/
 *         stopFocusMode. If the session changes externally (BootReceiver, direct
 *         DAO write), the StateFlow will NOT update until loadActiveSession() is
 *         called explicitly or the ViewModel is recreated.
 *         Fix: add observeActiveSession(): Flow<FocusSessionEntity?> to
 *         FocusSessionDao, expose it via FocusSessionRepository.
 *
 * FLAG-2  [focusViolationApp] has no backing source in any repository.
 *         AppBlockerAccessibilityService detects violations in a separate process.
 *         There is no bridge (SharedPreferences key, broadcast, ContentProvider)
 *         to propagate the violated package name to this ViewModel.
 *         Fix: write violated package to a SharedPreferences key (e.g.
 *         "current_violation_app") from AppBlockerAccessibilityService, then
 *         listen for it here via SharedPreferences.OnSharedPreferenceChangeListener.
 *
 * FLAG-3  [ForegroundServiceController] is not in AppModule. Instantiated here
 *         directly with applicationContext. Once AppModule registers it, replace
 *         the inline instantiation with AppModule.foregroundServiceController.
 *
 * FLAG-4  [startFocusMode] resolves the task via observeAllTasks().first().
 *         No-op if taskId is not found. Requires the tasks Flow to have emitted
 *         at least once (guaranteed after TaskViewModel.init completes).
 *
 * FLAG-5  The "use global allowed_packages" fallback (when task.focusAllowedPackages
 *         is null) reads the raw SharedPreferences key "allowed_packages" through
 *         the repository's compatibility string accessor. A typed list accessor
 *         would still be preferable for future cleanup.
 *
 * GPT Terra: treat the public API here as the stable contract.
 */
class FocusSessionViewModel(
    private val focusSessionRepository: FocusSessionRepository,
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    context: Context,
) : ViewModel() {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("focusday_prefs", MODE_PRIVATE)

    // FLAG-3: not in AppModule — instantiated with applicationContext directly.
    private val foregroundServiceController = ForegroundServiceController(context.applicationContext)

    // ─── State ────────────────────────────────────────────────────────────────

    /**
     * Currently active focus session, or null if none is running.
     * See FLAG-1 — not backed by a reactive Room Flow.
     *
     * Initial value: loaded from Room on [init]. Updated synchronously on
     * [startFocusMode] and [stopFocusMode].
     */
    private val _focusSession = MutableStateFlow<FocusSession?>(null)
    val focusSession: StateFlow<FocusSession?> = _focusSession.asStateFlow()

    data class FocusBreakState(
        val active: Boolean,
        val untilMs: Long,
    )

    val focusBreak: StateFlow<FocusBreakState> = flow {
        while (true) {
            val untilMs = settingsRepository.getFocusBreakUntilMs()
            val active = untilMs > System.currentTimeMillis()
            if (!active && untilMs > 0L) {
                settingsRepository.setFocusBreak(active = false, untilMs = 0L)
            }
            emit(FocusBreakState(active = active, untilMs = if (active) untilMs else 0L))
            delay(1_000)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FocusBreakState(active = false, untilMs = 0L),
    )

    val todayFocusMinutes: StateFlow<Int> = flow {
        while (true) {
            emit(runCatching { focusSessionRepository.getTodayFocusMinutes() }.getOrDefault(0))
            delay(15_000)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

    val todayOverrideCount: StateFlow<Int> = flow {
        while (true) {
            emit(runCatching { focusSessionRepository.getTodayOverrideCount() }.getOrDefault(0))
            delay(15_000)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

    /**
     * Package name of the app that most recently triggered a focus violation,
     * or null if no violation has been detected this session.
     * See FLAG-2 — updated only via [onViolationDetected]; no enforcement bridge yet.
     */
    private val _focusViolationApp = MutableStateFlow<String?>(null)
    val focusViolationApp: StateFlow<String?> = _focusViolationApp.asStateFlow()

    private val violationPreferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == AppBlockerAccessibilityService.PREF_CURRENT_VIOLATION_APP) {
                _focusViolationApp.value = sharedPreferences.getString(key, null)
            }
        }

    // ─── Init ─────────────────────────────────────────────────────────────────

    init {
        prefs.registerOnSharedPreferenceChangeListener(violationPreferenceListener)
        _focusViolationApp.value = prefs.getString(
            AppBlockerAccessibilityService.PREF_CURRENT_VIOLATION_APP,
            null,
        )
        viewModelScope.launch {
            focusSessionRepository.observeActiveFocusSession().collect { session ->
                _focusSession.value = session
            }
        }
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(violationPreferenceListener)
        super.onCleared()
    }

    // ─── Session lifecycle ────────────────────────────────────────────────────

    /**
     * Starts focus mode for the task with [taskId].
     *
     * Sequence (fulfils the Track C TODO comments in FocusSessionRepository and
     * Risk 9 in ARCHITECTURE.md):
     *   1. Look up the task from TaskRepository (FLAG-4).
     *   2. Insert a new FocusSession row in Room.
     *   3. Mirror enforcement state to SharedPreferences (focus_active, task_id,
     *      task_end_ms, allowed_packages) so AppBlockerAccessibilityService picks
     *      up the change synchronously without a restart.
     *   4. Start the ForegroundTaskService.
     *   5. Update [focusSession] StateFlow.
     *
     * Backing calls:
     *   [TaskRepository.observeAllTasks] (.first() — one-shot)
     *   [FocusSessionRepository.startFocusSession]
     *   [SettingsRepository.setFocusActive]
     *   [SettingsRepository.setActiveTask]
     *   [SettingsRepository.setAllowedPackages]
     *   [ForegroundServiceController.startService]
     */
    fun startFocusMode(taskId: String) {
        viewModelScope.launch {
            // Step 1 — resolve task (FLAG-4)
            val task = taskRepository.observeAllTasks().first()
                .firstOrNull { it.id == taskId } ?: return@launch

            // Resolve allowed packages:
            //   task.focusAllowedPackages != null → use task-specific list
            //   null                              → use global "allowed_packages" key (FLAG-5)
            val allowedPackages: List<String> = task.focusAllowedPackages
                ?: run {
                    val configured = settingsRepository.readAppSettings().allowedFocusPackages
                    if (configured.isNotEmpty()) return@run configured
                    val raw = settingsRepository.getString("allowed_packages")
                    if (raw.isNullOrBlank()) emptyList()
                    else runCatching {
                        JSONArray(raw).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                    }.getOrDefault(emptyList())
                }

            val startMs = Instant.parse(task.startTime).toEpochMilli()
            val endMs   = Instant.parse(task.endTime).toEpochMilli()

            val session = FocusSession(
                taskId          = task.id,
                startedAt       = Instant.now().toString(),
                isActive        = true,
                allowedPackages = allowedPackages,
            )

            // Step 2 — Room write
            focusSessionRepository.startFocusSession(session)

            // Step 3 — mirror to enforcement SharedPreferences
            // setFocusActive(true) is not PIN-gated when starting.
            settingsRepository.setFocusActive(active = true)
            settingsRepository.setActiveTask(
                taskId       = task.id,
                name         = task.title,
                endMs        = endMs,
                nextName     = null,
            )
            settingsRepository.setActiveTaskColor(task.color ?: "")
            settingsRepository.setActiveTaskStartMs(task.id, startMs)
            settingsRepository.setAllowedPackages(allowedPackages)

            // Step 4 — start the foreground service
            foregroundServiceController.startService(
                taskId      = task.id,
                taskName    = task.title,
                startTimeMs = startMs,
                endTimeMs   = endMs,
                nextName    = null,
            )

            // Step 5 — update UI state
            _focusSession.value = session
        }
    }

    /**
     * Stops the currently active focus session.
     *
     * Sequence:
     *   1. Validate and clear enforcement SharedPreferences (focus_active=false).
     *   2. End the session row in Room and clear its active-task snapshot.
     *   3. Stop ForegroundTaskService via the internal lifecycle path.
     *   4. Clear [focusSession] and [focusViolationApp] StateFlows.
     *
     * [pinHash] is the SHA-256 hex digest of the verified focus-session PIN.
     * The repository enforces it before clearing the active session, so a
     * missing hash fails closed when a focus PIN is configured.
     *
     * Backing calls:
     *   [FocusSessionRepository.endFocusSession]
     *   [SettingsRepository.setFocusActive]
     *   [SettingsRepository.clearActiveTask]
     *   [ForegroundServiceController.stopServiceInternal]
     */
    fun stopFocusMode(pinHash: String? = null) {
        viewModelScope.launch {
            val current = _focusSession.value ?: return@launch

            // Step 1 — validate the PIN before any Room mutation, then clear
            // the enforcement flag. The repository fails closed here.
            settingsRepository.setFocusActive(active = false, pinHash = pinHash)

            // Step 2 — Room write and active-task cleanup
            focusSessionRepository.endFocusSession(current.taskId)
            settingsRepository.clearActiveTask()

            // Step 3 — stop foreground service (internal / non-PIN-gated path)
            foregroundServiceController.stopServiceInternal()

            // Step 4 — clear UI state
            _focusSession.value      = null
            _focusViolationApp.value = null
        }
    }

    // ─── External update hooks ────────────────────────────────────────────────

    /**
     * Updates [focusViolationApp] when the enforcement layer detects a blocked-app
     * access during a focus session.
     *
     * See FLAG-2 — wire this to a BroadcastReceiver or SharedPreferences listener
     * once the enforcement bridge is implemented in AppBlockerAccessibilityService.
     */
    fun onViolationDetected(packageName: String) {
        _focusViolationApp.value = packageName
    }

    /** Records an explicit emergency/temptation override before the session ends. */
    fun recordOverride(taskId: String, reason: String) {
        viewModelScope.launch {
            focusSessionRepository.logFocusOverride(
                taskId = taskId,
                appName = "focus_session",
                reason = reason,
            )
        }
    }

    /**
     * Reloads the active session from Room.
     * Call after a process restart, BootReceiver recovery, or any out-of-band
     * change to the focus_sessions table.
     *
     * Backing call: [FocusSessionRepository.getActiveFocusSession]
     */
    fun loadActiveSession() {
        viewModelScope.launch {
            _focusSession.value = focusSessionRepository.getActiveFocusSession()
        }
    }

    fun startPomodoroBreak(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setFocusBreak(
                active = true,
                untilMs = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L,
            )
        }
    }

    fun endPomodoroBreak() {
        viewModelScope.launch {
            settingsRepository.setFocusBreak(active = false, untilMs = 0L)
        }
    }
}
