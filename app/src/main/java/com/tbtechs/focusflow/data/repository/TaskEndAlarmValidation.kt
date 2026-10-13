package com.tbtechs.focusflow.data.repository

object TaskEndAlarmValidation {
    const val EARLY_TOLERANCE_MS = 5_000L

    enum class Decision {
        DELIVER,
        SUPPRESS_MISSING_OR_TERMINAL,
        SUPPRESS_STALE_TRIGGER,
        SUPPRESS_EARLY,
    }

    fun evaluate(
        taskStatus: String?,
        endMs: Long,
        registeredEndMs: Long?,
        nowMs: Long,
    ): Decision {
        if (taskStatus !in setOf("scheduled", "active")) {
            return Decision.SUPPRESS_MISSING_OR_TERMINAL
        }
        if (registeredEndMs != null && registeredEndMs != endMs) {
            return Decision.SUPPRESS_STALE_TRIGGER
        }
        if (nowMs < endMs - EARLY_TOLERANCE_MS) return Decision.SUPPRESS_EARLY
        return Decision.DELIVER
    }
}