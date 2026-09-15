package com.tbtechs.focusflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tbtechs.focusflow.data.repository.FocusSessionRepository
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.data.repository.TaskRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * AppBootViewModel
 *
 * Drives the app's startup sequence and exposes loading / readiness / error state
 * to the UI layer so the root composable knows when to show the splash screen,
 * the main content, or an unrecoverable-error dialog.
 *
 * Boot sequence (preserves AppContext.init() order from the hybrid app,
 * ARCHITECTURE.md §2):
 *
 *   1. Settings check — verify SharedPreferences are accessible. Always fast;
 *      SharedPreferences are synchronous. Mirrors the "timeout-then-default fallback"
 *      from AppContext.init(): if the check exceeds [SETTINGS_TIMEOUT_MS], we proceed
 *      with defaults rather than blocking indefinitely.
 *
 *   2. Task refresh — trigger the first Room query (tasks table). This opens the
 *      SQLite database. If the DB is corrupt or the schema migration failed,
 *      the exception is caught here and [isDbUnrecoverable] is set.
 *
 *   3. Active session recovery — load any in-progress focus session from Room and
 *      propagate it to [FocusSessionViewModel] via [onSessionRecovered]. Matches
 *      AppContext.init()'s active-session-recovery step.
 *
 * Backed by:
 *   - [SettingsRepository] (Replit Stage 2)
 *   - [TaskRepository]     (Track A)
 *   - [FocusSessionRepository] (Track A)
 *
 * GPT Terra: [isLoading], [isDbReady], [isDbUnrecoverable] drive the root
 * navigation / splash logic. Do not add business logic here.
 */
class AppBootViewModel(
    private val settingsRepository: SettingsRepository,
    private val taskRepository: TaskRepository,
    private val focusSessionRepository: FocusSessionRepository,
) : ViewModel() {

    companion object {
        /** Timeout for the settings accessibility check (step 1). */
        private const val SETTINGS_TIMEOUT_MS = 3_000L
    }

    // ─── State ────────────────────────────────────────────────────────────────

    private val _isLoading          = MutableStateFlow(true)
    private val _isDbReady          = MutableStateFlow(false)
    private val _isDbUnrecoverable  = MutableStateFlow(false)

    /** True while the boot sequence is in progress. */
    val isLoading: StateFlow<Boolean>         = _isLoading.asStateFlow()

    /** True once Room has been successfully queried (DB is open and schema is valid). */
    val isDbReady: StateFlow<Boolean>         = _isDbReady.asStateFlow()

    /**
     * True if the database could not be opened or a migration failed.
     * When true, the app should show an unrecoverable-error screen and not
     * attempt any further DB operations.
     */
    val isDbUnrecoverable: StateFlow<Boolean> = _isDbUnrecoverable.asStateFlow()

    // ─── Callbacks (wired by the host Activity / NavGraph) ────────────────────

    /**
     * Called by the boot sequence when an in-progress focus session is found.
     * Wire this to [FocusSessionViewModel.loadActiveSession] at the call site.
     */
    var onSessionRecovered: (() -> Unit)? = null

    // ─── Boot sequence ────────────────────────────────────────────────────────

    init {
        runBootSequence()
    }

    /**
     * Runs the three-step boot sequence asynchronously.
     * Sets [isLoading] to false and the appropriate ready/error flag when done.
     */
    private fun runBootSequence() {
        viewModelScope.launch {
            try {
                // ── Step 1: Settings check ────────────────────────────────────
                // SharedPreferences are synchronous; the timeout is a safeguard
                // matching AppContext.init()'s timeout-then-default pattern.
                // On timeout we log and continue — settings are not DB-backed so
                // there is no unrecoverable state possible here.
                try {
                    withTimeout(SETTINGS_TIMEOUT_MS) {
                        // Touch SettingsRepository to confirm SharedPreferences are
                        // accessible. getLong() is a synchronous read under the hood.
                        settingsRepository.getLong("task_end_ms")
                    }
                } catch (_: TimeoutCancellationException) {
                    // Proceed with defaults — mirrors AppContext.init() fallback.
                    android.util.Log.w(
                        "AppBootViewModel",
                        "Settings check timed out after ${SETTINGS_TIMEOUT_MS}ms — continuing with defaults.",
                    )
                }

                // ── Step 2: Task refresh — opens the Room database ────────────
                // This is the first Room query; if the DB is corrupt or the schema
                // migration in FocusFlowDatabase failed, it will throw here.
                taskRepository.getRecentUnresolvedTasks()
                _isDbReady.value = true

                // ── Step 3: Active session recovery ───────────────────────────
                val activeSession = focusSessionRepository.getActiveFocusSession()
                if (activeSession != null) {
                    onSessionRecovered?.invoke()
                }

            } catch (e: Exception) {
                // DB open / migration failure — unrecoverable.
                android.util.Log.e(
                    "AppBootViewModel",
                    "Boot sequence failed — DB unrecoverable: ${e.message}",
                    e,
                )
                _isDbUnrecoverable.value = true
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Retries the boot sequence after a transient failure.
     * No-op if the DB is already ready or has been marked unrecoverable.
     */
    fun retry() {
        if (_isDbReady.value || _isDbUnrecoverable.value) return
        _isLoading.value = true
        runBootSequence()
    }
}
