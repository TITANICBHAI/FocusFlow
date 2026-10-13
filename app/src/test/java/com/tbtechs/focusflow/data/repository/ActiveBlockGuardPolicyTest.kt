package com.tbtechs.focusflow.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveBlockGuardPolicyTest {
    @Test
    fun activeFocusOrStandaloneBlockLocksMasterToggle() {
        assertTrue(
            ActiveBlockGuardPolicy.isActive(
                focusActive = true,
                focusEndMs = 2_000L,
                standaloneActive = false,
                standaloneUntilMs = 0L,
                nowMs = 1_000L,
            ),
        )
        assertTrue(
            ActiveBlockGuardPolicy.isActive(
                focusActive = false,
                focusEndMs = 0L,
                standaloneActive = true,
                standaloneUntilMs = 2_000L,
                nowMs = 1_000L,
            ),
        )
    }

    @Test
    fun enablingNetworkBlockingRemainsAllowedDuringFocusOrStandaloneBlock() {
        val activeFocus = ActiveBlockGuardPolicy.isActive(
            focusActive = true,
            focusEndMs = 2_000L,
            standaloneActive = false,
            standaloneUntilMs = 0L,
            nowMs = 1_000L,
        )
        val activeStandalone = ActiveBlockGuardPolicy.isActive(
            focusActive = false,
            focusEndMs = 0L,
            standaloneActive = true,
            standaloneUntilMs = 2_000L,
            nowMs = 1_000L,
        )

        assertTrue(
            ActiveBlockGuardPolicy.mayChangeNetworkBlocking(
                currentlyEnabled = false,
                requestedEnabled = true,
                blockActive = activeFocus,
            ),
        )
        assertTrue(
            ActiveBlockGuardPolicy.mayChangeNetworkBlocking(
                currentlyEnabled = false,
                requestedEnabled = true,
                blockActive = activeStandalone,
            ),
        )
    }

    @Test
    fun expiredBlockDoesNotLockMasterToggleAtTheBoundary() {
        assertFalse(
            ActiveBlockGuardPolicy.isActive(
                focusActive = true,
                focusEndMs = 1_000L,
                standaloneActive = false,
                standaloneUntilMs = 0L,
                nowMs = 1_000L,
            ),
        )
    }

    @Test
    fun inactiveFlagDoesNotLockMasterToggleDespiteFutureDeadline() {
        assertFalse(
            ActiveBlockGuardPolicy.isActive(
                focusActive = false,
                focusEndMs = 2_000L,
                standaloneActive = false,
                standaloneUntilMs = 3_000L,
                nowMs = 1_000L,
            ),
        )
    }
}
