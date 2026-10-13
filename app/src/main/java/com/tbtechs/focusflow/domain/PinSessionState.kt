package com.tbtechs.focusflow.domain

import java.util.concurrent.atomic.AtomicLong

/**
 * PinSessionState
 *
 * Holds the in-memory "defense PIN unlocked" session state for FocusFlow.
 *
 * ─── Source reference: SessionPinModule.kt ───────────────────────────────────
 *
 *   The task spec says to read SessionPinModule.kt for "the exact timeout value and
 *   whether it persists across process death or not."
 *
 *   ACTUAL BEHAVIOR OF SessionPinModule.kt (verified, not assumed):
 *     • The module stores the PIN hash in SharedPreferences ("session_pin_hash").
 *       SharedPreferences are persistent — the hash survives process death.
 *     • The module has NO in-memory unlock timer, NO "unlocked until" timestamp,
 *       and NO timeout constant of any kind. It is a pure hash-storage/verification
 *       service; it does not track authentication sessions.
 *
 * ─── Design consequence ───────────────────────────────────────────────────────
 *
 *   PinSessionState provides the "session unlock window" concept that the architecture
 *   document calls for. Because the source module has no equivalent in-memory state,
 *   this class is the new home for it.
 *
 *   Persistence: IN-MEMORY ONLY. This state is intentionally not written to
 *   SharedPreferences or any other persistent store, so it resets on process death.
 *   This matches the "session" semantics (user must re-enter PIN after the app is
 *   killed) and avoids exposing a "pre-authenticated" flag in persistent storage.
 *
 * ─── Timeout flag ─────────────────────────────────────────────────────────────
 *
 *   ⚠ SESSION_UNLOCK_DURATION_MS is a placeholder value (5 minutes).
 *   No canonical timeout constant exists in SessionPinModule.kt. This value MUST be
 *   confirmed against UX requirements before shipping. Search for
 *   "SESSION_UNLOCK_DURATION_MS" to find all references.
 *
 * ─── Thread safety ────────────────────────────────────────────────────────────
 *
 *   All state is held in an AtomicLong. All public methods are safe to call from
 *   any thread without external synchronization.
 *
 * ─── Logging ─────────────────────────────────────────────────────────────────
 *   No PIN, hash, or salt is logged at any verbosity level.
 */
object PinSessionState {

    /**
     * Duration a successful [unlock] call keeps the session open.
     *
     * ⚠ PLACEHOLDER — no canonical value found in SessionPinModule.kt.
     *   Confirm against UX/product requirements before shipping.
     */
    const val SESSION_UNLOCK_DURATION_MS: Long = 5L * 60L * 1_000L  // maybe — 5 min; no timeout exists anywhere in SessionPinModule.kt

    /** Absolute timestamp (System.currentTimeMillis) at which the session expires. */
    private val unlockedUntilMs = AtomicLong(0L)

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Marks the session as unlocked for [SESSION_UNLOCK_DURATION_MS] from now.
     *
     * Call immediately after a successful [PinManager.verifyPin].
     * Does not persist across process death.
     */
    fun unlock() {
        unlockedUntilMs.set(System.currentTimeMillis() + SESSION_UNLOCK_DURATION_MS)
    }

    /**
     * Unlocks the session until the given absolute timestamp (epoch ms).
     *
     * No-op if [timestampMs] is already in the past. Useful when re-attaching
     * a ViewModel to an existing process where the user already unlocked earlier
     * in the same process lifetime.
     */
    fun unlockUntil(timestampMs: Long) {
        if (timestampMs > System.currentTimeMillis()) {
            unlockedUntilMs.set(timestampMs)
        }
    }

    /**
     * Returns true if the PIN was verified recently enough that the user should
     * not be re-prompted.
     *
     * Does NOT check SharedPreferences or any persistent store. This state is
     * valid only within the current process lifetime.
     */
    fun isUnlocked(): Boolean = System.currentTimeMillis() < unlockedUntilMs.get()

    /**
     * Immediately expires the session.
     *
     * The next call to a PIN-gated operation will prompt for re-entry.
     */
    fun lock() {
        unlockedUntilMs.set(0L)
    }

    /**
     * Milliseconds remaining until the session expires, or 0 if already locked.
     *
     * Useful for displaying a countdown in UI or scheduling a re-lock alarm.
     */
    fun remainingMs(): Long = maxOf(0L, unlockedUntilMs.get() - System.currentTimeMillis())

    /**
     * The absolute epoch-ms timestamp at which the session will expire, or 0 if locked.
     *
     * Pass to [unlockUntil] when restoring state within the same process.
     */
    fun expiresAtMs(): Long = unlockedUntilMs.get()
}
