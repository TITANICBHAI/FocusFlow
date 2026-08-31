package com.tbtechs.focusflow.services

import java.util.Locale

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

    private val ALWAYS_EXCLUDED_NORMALIZED = ALWAYS_EXCLUDED.mapTo(mutableSetOf()) {
        it.lowercase(Locale.ROOT)
    }

    data class Result(
        val packages: Set<String>,
        val mode: String,
        val reasons: Map<String, Set<String>>,
        val unavailablePackages: Set<String>,
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
        installedPackages: Collection<String>? = null,
    ): Result {
        val focusFlowPackageNormalized = normalize(focusFlowPackage)
        val installed = installedPackages
            ?.map { normalize(it) }
            ?.toSet()
        val explicitCandidates = explicitPackages
            .asSequence()
            .map(::normalize)
            .filter { it.isNotBlank() }
            .filterNot { isExcluded(it, focusFlowPackageNormalized) }
            .toSet()
        val unavailable = if (installed == null) {
            emptySet()
        } else {
            explicitCandidates.filterNot { it in installed }.toSet()
        }
        val explicit = explicitCandidates
            .filter { installed == null || it in installed }
            .toSet()
        val allowed = allowedPackages.mapTo(mutableSetOf(), ::normalize)
        val mirrored = if (focusMirrorEnabled && focusActive && allowedPackagesReady) {
            installedLaunchablePackages.asSequence()
                .map(::normalize)
                .filter { it.isNotBlank() }
                .filter { it !in allowed }
                .filterNot { isExcluded(it, focusFlowPackageNormalized) }
                .toSet()
        } else {
            emptySet()
        }

        val reasons = linkedMapOf<String, MutableSet<String>>()
        explicit.forEach { reasons.getOrPut(it) { linkedSetOf() }.add("explicit_vpn") }
        mirrored.forEach { reasons.getOrPut(it) { linkedSetOf() }.add("focus_blocked") }

        return Result(
            packages = explicit + mirrored,
            mode = if (globalMode) NetworkBlockerVpnService.MODE_GLOBAL
                   else NetworkBlockerVpnService.MODE_PER_APP,
            reasons = reasons.mapValues { it.value.toSet() },
            unavailablePackages = unavailable,
        )
    }

    private fun normalize(packageName: String): String =
        packageName.trim().lowercase(Locale.ROOT)

    private fun isExcluded(packageName: String, focusFlowPackage: String): Boolean =
        packageName == focusFlowPackage || packageName in ALWAYS_EXCLUDED_NORMALIZED
}