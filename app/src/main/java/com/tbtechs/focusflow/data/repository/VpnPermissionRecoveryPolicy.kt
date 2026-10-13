package com.tbtechs.focusflow.data.repository

internal object VpnPermissionRecoveryPolicy {
    fun hasConfiguredVpnSource(
        vpnBlockEnabled: Boolean,
        global: Boolean,
        effectiveTargets: List<String>,
        mirrorActive: Boolean,
    ): Boolean =
        vpnBlockEnabled &&
            (global || effectiveTargets.any(String::isNotBlank) || mirrorActive)

    fun shouldShowBanner(
        vpnBlockEnabled: Boolean,
        global: Boolean,
        effectiveTargets: List<String>,
        mirrorActive: Boolean,
        permissionGranted: Boolean,
        serviceNeedsAttention: Boolean,
    ): Boolean =
        hasConfiguredVpnSource(
            vpnBlockEnabled = vpnBlockEnabled,
            global = global,
            effectiveTargets = effectiveTargets,
            mirrorActive = mirrorActive,
        ) && (!permissionGranted || serviceNeedsAttention)
}
