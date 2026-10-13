package com.tbtechs.focusflow.data.repository

internal data class VpnListMasterSwitches(
    val enabled: Boolean,
    val vpn: Boolean,
)

internal object VpnListSavePolicy {
    /**
     * A list screen may turn Network Blocking on when it has targets, but it
     * does not own turning the Defense master switches off.
     */
    fun masterSwitchesAfterSave(
        currentEnabled: Boolean,
        currentVpn: Boolean,
        hasPackages: Boolean,
    ): VpnListMasterSwitches = VpnListMasterSwitches(
        enabled = currentEnabled || hasPackages,
        vpn = currentVpn || hasPackages,
    )
}

internal object VpnPackageListMigrationPolicy {
    fun mergeLegacyPackages(
        explicitPackages: List<String>,
        legacyPackages: List<String>,
    ): List<String> = (explicitPackages + legacyPackages)
        .filter(String::isNotBlank)
        .distinct()
        .sorted()
}
