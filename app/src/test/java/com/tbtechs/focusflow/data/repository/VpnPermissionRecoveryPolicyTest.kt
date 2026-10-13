package com.tbtechs.focusflow.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnPermissionRecoveryPolicyTest {
    @Test
    fun focusMirrorOnlyConfigurationShowsBannerWhenVpnPermissionIsRevoked() {
        assertTrue(
            VpnPermissionRecoveryPolicy.shouldShowBanner(
                vpnBlockEnabled = true,
                global = false,
                effectiveTargets = emptyList(),
                mirrorActive = true,
                permissionGranted = false,
                serviceNeedsAttention = false,
            ),
        )
    }

    @Test
    fun revokedPermissionDoesNotShowBannerWhenNoVpnSourceIsConfigured() {
        assertFalse(
            VpnPermissionRecoveryPolicy.shouldShowBanner(
                vpnBlockEnabled = true,
                global = false,
                effectiveTargets = emptyList(),
                mirrorActive = false,
                permissionGranted = false,
                serviceNeedsAttention = false,
            ),
        )
    }

    @Test
    fun globalAndEffectiveTargetsAreRecognizedAsConfiguredVpnSources() {
        assertTrue(
            VpnPermissionRecoveryPolicy.hasConfiguredVpnSource(
                vpnBlockEnabled = true,
                global = true,
                effectiveTargets = emptyList(),
                mirrorActive = false,
            ),
        )
        assertTrue(
            VpnPermissionRecoveryPolicy.hasConfiguredVpnSource(
                vpnBlockEnabled = true,
                global = false,
                effectiveTargets = listOf("com.example.app"),
                mirrorActive = false,
            ),
        )
    }
}
