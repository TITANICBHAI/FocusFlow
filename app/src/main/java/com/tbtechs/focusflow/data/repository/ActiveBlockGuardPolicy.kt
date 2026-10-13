package com.tbtechs.focusflow.data.repository

internal class NetworkBlockingChangeRejectedException : IllegalStateException(
    "Network blocking cannot be disabled while Focus or Standalone Block is active",
)

internal object ActiveBlockGuardPolicy {
    /** Only turning protection off is locked while a block runs. */
    fun mayChangeNetworkBlocking(
        currentlyEnabled: Boolean,
        requestedEnabled: Boolean,
        blockActive: Boolean,
    ): Boolean =
        !(currentlyEnabled && !requestedEnabled && blockActive)

    fun isActive(
        focusActive: Boolean,
        focusEndMs: Long,
        standaloneActive: Boolean,
        standaloneUntilMs: Long,
        nowMs: Long,
    ): Boolean =
        isSessionActive(focusActive, focusEndMs, nowMs) ||
            isSessionActive(standaloneActive, standaloneUntilMs, nowMs)

    private fun isSessionActive(active: Boolean, endsAtMs: Long, nowMs: Long): Boolean =
        active && (endsAtMs <= 0L || nowMs < endsAtMs)
}
