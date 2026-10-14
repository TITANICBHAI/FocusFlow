package com.tbtechs.focusflow.ui.alwayson

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlwaysOnRemovalPolicyTest {
    @Test
    fun uncheckingPreviouslySavedAlwaysOnPackageRequiresRemovalConfirmation() {
        assertTrue(
            AlwaysOnRemovalPolicy.isRemoving(
                originalAlwaysOnPackages = setOf("com.example.saved"),
                alwaysOnPackages = emptySet(),
                originalVpnPackages = emptySet(),
                vpnPackages = emptySet(),
            ),
        )
    }

    @Test
    fun uncheckingPreviouslySavedVpnPackageRequiresRemovalConfirmation() {
        assertTrue(
            AlwaysOnRemovalPolicy.isRemoving(
                originalAlwaysOnPackages = emptySet(),
                alwaysOnPackages = emptySet(),
                originalVpnPackages = setOf("com.example.vpn"),
                vpnPackages = emptySet(),
            ),
        )
    }

    @Test
    fun aNewUnsavedPackageDoesNotCountAsAProtectedRemoval() {
        assertFalse(
            AlwaysOnRemovalPolicy.isRemoving(
                originalAlwaysOnPackages = emptySet(),
                alwaysOnPackages = emptySet(),
                originalVpnPackages = emptySet(),
                vpnPackages = emptySet(),
            ),
        )
    }
}
