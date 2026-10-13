package com.tbtechs.focusflow.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkBlockingGuardTest {
    @Test
    fun enablingNetworkBlockingIsAllowedDuringFocusAndStandaloneBlocks() {
        val nowMs = 1_000L
        val focusActive = ActiveBlockGuardPolicy.isActive(
            focusActive = true,
            focusEndMs = 2_000L,
            standaloneActive = false,
            standaloneUntilMs = 0L,
            nowMs = nowMs,
        )
        val standaloneActive = ActiveBlockGuardPolicy.isActive(
            focusActive = false,
            focusEndMs = 0L,
            standaloneActive = true,
            standaloneUntilMs = 2_000L,
            nowMs = nowMs,
        )

        for (blockActive in listOf(focusActive, standaloneActive)) {
            assertTrue(
                ActiveBlockGuardPolicy.mayChangeNetworkBlocking(
                    currentlyEnabled = false,
                    requestedEnabled = true,
                    blockActive = blockActive,
                ),
            )
        }
    }

    @Test
    fun disablingAnEnabledProtectionIsRejectedDuringAnActiveBlock() {
        assertFalse(
            ActiveBlockGuardPolicy.mayChangeNetworkBlocking(
                currentlyEnabled = true,
                requestedEnabled = false,
                blockActive = true,
            ),
        )
    }

    @Test
    fun disablingAProtectionThatIsAlreadyOffIsAllowedDuringAnActiveBlock() {
        assertTrue(
            ActiveBlockGuardPolicy.mayChangeNetworkBlocking(
                currentlyEnabled = false,
                requestedEnabled = false,
                blockActive = true,
            ),
        )
    }

    @Test
    fun noOpAndInactiveBlockChangesAreAllowed() {
        for (value in listOf(true, false)) {
            assertTrue(
                ActiveBlockGuardPolicy.mayChangeNetworkBlocking(
                    currentlyEnabled = value,
                    requestedEnabled = value,
                    blockActive = true,
                ),
            )
        }
        assertTrue(
            ActiveBlockGuardPolicy.mayChangeNetworkBlocking(
                currentlyEnabled = true,
                requestedEnabled = false,
                blockActive = false,
            ),
        )
    }
}
