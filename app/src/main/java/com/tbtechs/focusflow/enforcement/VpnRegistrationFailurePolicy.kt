package com.tbtechs.focusflow.enforcement

internal object VpnRegistrationFailurePolicy {
    fun nextPersistedFailures(
        currentFailures: List<String>,
        reportedFailures: List<String>?,
    ): List<String> = (reportedFailures ?: currentFailures).distinct()
}
