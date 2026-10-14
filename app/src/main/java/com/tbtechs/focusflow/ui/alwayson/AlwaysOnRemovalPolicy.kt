package com.tbtechs.focusflow.ui.alwayson

internal object AlwaysOnRemovalPolicy {
    fun isRemoving(
        originalAlwaysOnPackages: Set<String>,
        alwaysOnPackages: Set<String>,
        originalVpnPackages: Set<String>,
        vpnPackages: Set<String>,
    ): Boolean =
        originalAlwaysOnPackages.any { it !in alwaysOnPackages } ||
            originalVpnPackages.any { it !in vpnPackages }
}
