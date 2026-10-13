package com.tbtechs.focusflow.ui.alwayson

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnConsentPolicyTest {
    @Test
    fun missingRepositoryUsesPlatformPermissionCheck() {
        var checkCount = 0

        assertTrue(
            VpnConsentPolicy.permissionGranted(repositoryPermission = null) {
                checkCount++
                true
            },
        )
        assertEquals(1, checkCount)
        assertFalse(
            VpnConsentPolicy.permissionGranted(repositoryPermission = null) {
                checkCount++
                false
            },
        )
        assertEquals(2, checkCount)
    }

    @Test
    fun repositoryPermissionResultIsAuthoritative() {
        var platformCheckCalled = false
        val granted = VpnConsentPolicy.permissionGranted(repositoryPermission = true) {
            platformCheckCalled = true
            false
        }
        val denied = VpnConsentPolicy.permissionGranted(repositoryPermission = false) {
            platformCheckCalled = true
            true
        }

        assertTrue(granted)
        assertFalse(denied)
        assertFalse(platformCheckCalled)
    }

    @Test
    fun platformPermissionCheckFailureFailsClosed() {
        assertFalse(
            VpnConsentPolicy.permissionGranted(repositoryPermission = null) {
                throw IllegalStateException("permission check failed")
            },
        )
    }

    @Test
    fun cancelledConsentDoesNotConfirmPermissionOrEnableProtection() {
        val outcome = VpnConsentPolicy.afterResult(permissionGranted = false)

        assertEquals(VpnConsentOutcome.NOT_GRANTED, outcome)
        assertFalse(outcome.isGranted)
    }

    @Test
    fun confirmedSystemPermissionAllowsProtectionFlowToContinue() {
        val outcome = VpnConsentPolicy.afterResult(permissionGranted = true)

        assertEquals(VpnConsentOutcome.GRANTED, outcome)
        assertTrue(outcome.isGranted)
    }
}
