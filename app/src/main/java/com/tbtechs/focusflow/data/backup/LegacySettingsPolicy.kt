package com.tbtechs.focusflow.data.backup

/**
 * Settings from the retired TypeScript app that represent runtime or
 * device-local state must not overwrite the native app's preferences.
 */
object LegacySettingsPolicy {
    private val deviceLocalKeys = setOf(
        "standaloneBlockPackages",
        "standaloneBlockUntil",
        "standaloneVpnPackages",
        "autoCopiedAlwaysOnPackages",
        "alwaysOnEnforcementEnabled",
        "focusModeEnabled",
        "pomodoroEnabled",
        "notificationsEnabled",
        "weeklyReportEnabled",
        "launcherEnabled",
        "aversionDimmerEnabled",
        "aversionVibrateEnabled",
        "aversionSoundEnabled",
        "systemGuardEnabled",
        "blockInstallActionsEnabled",
        "blockYoutubeShortsEnabled",
        "blockInstagramReelsEnabled",
        "vpnBlockEnabled",
        "autoCopyToAlwaysOn",
        "vpnSelfHealEnabled",
        "pinProtectionEnabled",
    )

    fun mayMigrateKey(key: String): Boolean = key !in deviceLocalKeys
}
