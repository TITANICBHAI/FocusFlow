package com.tbtechs.focusflow.services

/**
 * Pure policy calculation for the optional Focus → VPN mirror.
 *
 * This intentionally knows nothing about AccessibilityService overlays,
 * standalone blocking, schedules, allowances, or global VPN activation.
 */
object VpnPolicyCalculator {
    private val ALWAYS_EXCLUDED = setOf(
        "android",
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.app.telephonyui",
        "com.android.server.telecom",
        "com.android.mms",
        "com.android.messaging",
        "com.google.android.apps.messaging",
        "com.google.android.permissioncontroller",
    )

    data class Result(
        val packages: Set<String>,
        val mode: String,
    )

    fun calculate(
        explicitPackages: Collection<String>,
        installedLaunchablePackages: Collection<String>,
        allowedPackages: Collection<String>,
        focusMirrorEnabled: Boolean,
        focusActive: Boolean,
        allowedPackagesReady: Boolean,
        globalMode: Boolean,
        focusFlowPackage: String,
    ): Result {
        val explicit = explicitPackages
            .asSequence()
            .filter { it.isNotBlank() }
            .toSet()
        val allowed = allowedPackages.toSet()
        val mirrored = if (focusMirrorEnabled && focusActive && allowedPackagesReady) {
            installedLaunchablePackages.asSequence()
                .filter { it.isNotBlank() }
                .filter { it !in allowed }
                .filter { it != focusFlowPackage && it !in ALWAYS_EXCLUDED }
                .toSet()
        } else {
            emptySet()
        }

        return Result(
            packages = explicit + mirrored,
            mode = if (globalMode) NetworkBlockerVpnService.MODE_GLOBAL
                   else NetworkBlockerVpnService.MODE_PER_APP,
        )
    }
}