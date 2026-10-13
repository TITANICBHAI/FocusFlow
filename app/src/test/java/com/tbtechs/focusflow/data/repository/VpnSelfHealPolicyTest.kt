package com.tbtechs.focusflow.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnSelfHealPolicyTest {
    @Test
    fun migratesLegacyValueOnlyWhenNativePreferenceIsAbsent() {
        assertEquals(
            true,
            VpnSelfHealPolicy.migrationValue(
                nativePreferenceExists = false,
                legacyPreferenceValue = true,
            ),
        )
        assertEquals(
            false,
            VpnSelfHealPolicy.migrationValue(
                nativePreferenceExists = false,
                legacyPreferenceValue = false,
            ),
        )
        assertNull(
            VpnSelfHealPolicy.migrationValue(
                nativePreferenceExists = true,
                legacyPreferenceValue = true,
            ),
        )
        assertNull(
            VpnSelfHealPolicy.migrationValue(
                nativePreferenceExists = false,
                legacyPreferenceValue = null,
            ),
        )
    }

    @Test
    fun disablingSelfHealCancelsWatchdog() {
        val decision = VpnSelfHealPolicy.toggleDecision(
            currentValue = true,
            requestedValue = false,
        )
        assertFalse(decision.persistedValue)
        assertEquals(
            VpnSelfHealPolicy.ToggleEffect.CANCEL_WATCHDOG,
            decision.effect,
        )
    }

    @Test
    fun enablingSelfHealRequestsRecoverySync() {
        val decision = VpnSelfHealPolicy.toggleDecision(
            currentValue = false,
            requestedValue = true,
        )
        assertTrue(decision.persistedValue)
        assertEquals(
            VpnSelfHealPolicy.ToggleEffect.REQUEST_RECOVERY_SYNC,
            decision.effect,
        )
    }

    @Test
    fun unchangedSelfHealDoesNotRequestRecovery() {
        assertEquals(
            VpnSelfHealPolicy.ToggleEffect.NONE,
            VpnSelfHealPolicy.toggleDecision(
                currentValue = true,
                requestedValue = true,
            ).effect,
        )
        assertEquals(
            VpnSelfHealPolicy.ToggleEffect.NONE,
            VpnSelfHealPolicy.toggleDecision(
                currentValue = false,
                requestedValue = false,
            ).effect,
        )
    }

    @Test
    fun staleSettingsSnapshotPreservesStoredValueButExplicitChangesStillWin() {
        val preservedValue = VpnSelfHealPolicy.valueToPersist(
            loadedValue = false,
            requestedValue = false,
            storedValue = true,
        )
        assertTrue(preservedValue)
        assertEquals(
            VpnSelfHealPolicy.ToggleEffect.NONE,
            VpnSelfHealPolicy.toggleDecision(
                currentValue = true,
                requestedValue = preservedValue,
            ).effect,
        )

        val explicitlyDisabled = VpnSelfHealPolicy.valueToPersist(
            loadedValue = true,
            requestedValue = false,
            storedValue = true,
        )
        assertFalse(explicitlyDisabled)
        assertEquals(
            VpnSelfHealPolicy.ToggleEffect.CANCEL_WATCHDOG,
            VpnSelfHealPolicy.toggleDecision(
                currentValue = true,
                requestedValue = explicitlyDisabled,
            ).effect,
        )

        val explicitlyEnabled = VpnSelfHealPolicy.valueToPersist(
            loadedValue = false,
            requestedValue = true,
            storedValue = false,
        )
        assertTrue(explicitlyEnabled)
        assertEquals(
            VpnSelfHealPolicy.ToggleEffect.REQUEST_RECOVERY_SYNC,
            VpnSelfHealPolicy.toggleDecision(
                currentValue = false,
                requestedValue = explicitlyEnabled,
            ).effect,
        )
    }

    @Test
    fun emptyVpnListCannotDisableSelfHeal() {
        assertFalse(VpnSelfHealPolicy.shouldEnableFromList(hasVpnPackages = false))
        assertTrue(VpnSelfHealPolicy.shouldEnableFromList(hasVpnPackages = true))
    }
}
