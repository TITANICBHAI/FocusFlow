package com.tbtechs.focusflow.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnPolicyCalculatorTest {
    private fun calculate(
        explicit: Collection<String> = emptyList(),
        installed: Collection<String> = listOf(
            "com.example.allowed",
            "com.example.blocked",
            "com.android.dialer",
            "com.tbtechs.focusflow",
        ),
        allowed: Collection<String> = listOf("com.example.allowed"),
        enabled: Boolean = true,
        active: Boolean = true,
        ready: Boolean = true,
        global: Boolean = false,
    ) = VpnPolicyCalculator.calculate(
        explicitPackages = explicit,
        installedLaunchablePackages = installed,
        allowedPackages = allowed,
        focusMirrorEnabled = enabled,
        focusActive = active,
        allowedPackagesReady = ready,
        globalMode = global,
        focusFlowPackage = "com.tbtechs.focusflow",
    )

    @Test
    fun mirrorIsOptInAndOffByDefault() {
        assertTrue(calculate(enabled = false).packages.isEmpty())
        assertTrue(calculate(active = false).packages.isEmpty())
        assertTrue(calculate(ready = false).packages.isEmpty())
    }

    @Test
    fun mirrorsBlockedLaunchableAppsOnly() {
        val packages = calculate().packages
        assertEquals(setOf("com.example.blocked"), packages)
        assertFalse(packages.contains("com.example.allowed"))
        assertFalse(packages.contains("com.android.dialer"))
        assertFalse(packages.contains("com.tbtechs.focusflow"))
    }

    @Test
    fun explicitVpnSelectionsSurviveFocusChangesAndDeduplicate() {
        assertEquals(
            setOf("com.example.explicit", "com.example.blocked"),
            calculate(explicit = listOf("com.example.explicit", "com.example.blocked", "com.example.explicit")).packages,
        )
        assertEquals(
            setOf("com.example.explicit"),
            calculate(explicit = listOf("com.example.explicit"), active = false).packages,
        )
    }

    @Test
    fun mirroringNeverTurnsOnGlobalMode() {
        assertEquals(
            NetworkBlockerVpnService.MODE_PER_APP,
            calculate(global = false).mode,
        )
    }
}