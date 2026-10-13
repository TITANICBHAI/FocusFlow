package com.tbtechs.focusflow.enforcement

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnRegistrationFailurePolicyTest {
    @Test
    fun statusUpdateWithoutRegistrationResultPreservesFailures() {
        val current = listOf("com.example.failed")

        assertEquals(
            current,
            VpnRegistrationFailurePolicy.nextPersistedFailures(
                currentFailures = current,
                reportedFailures = null,
            ),
        )
    }

    @Test
    fun registrationResultReplacesFailuresAndRemovesDuplicates() {
        assertEquals(
            listOf("com.example.first", "com.example.second"),
            VpnRegistrationFailurePolicy.nextPersistedFailures(
                currentFailures = listOf("com.example.stale"),
                reportedFailures = listOf(
                    "com.example.first",
                    "com.example.second",
                    "com.example.first",
                ),
            ),
        )
    }

    @Test
    fun successfulRegistrationCanClearPreviousFailures() {
        assertEquals(
            emptyList<String>(),
            VpnRegistrationFailurePolicy.nextPersistedFailures(
                currentFailures = listOf("com.example.stale"),
                reportedFailures = emptyList(),
            ),
        )
    }
}
