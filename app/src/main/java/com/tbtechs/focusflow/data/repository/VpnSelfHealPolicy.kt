package com.tbtechs.focusflow.data.repository

/**
 * Pure preference and transition rules for the VPN self-healing setting.
 */
internal object VpnSelfHealPolicy {
    const val NATIVE_PREFERENCE_KEY = "net_block_self_heal"
    const val LEGACY_PREFERENCE_KEY = "vpn_self_heal_enabled"

    val preferenceLock: Any = Any()

    enum class ToggleEffect {
        NONE,
        CANCEL_WATCHDOG,
        REQUEST_RECOVERY_SYNC,
    }

    data class ToggleDecision(
        val persistedValue: Boolean,
        val effect: ToggleEffect,
    )

    fun migrationValue(
        nativePreferenceExists: Boolean,
        legacyPreferenceValue: Boolean?,
    ): Boolean? {
        if (nativePreferenceExists) return null
        return legacyPreferenceValue
    }

    fun toggleDecision(currentValue: Boolean, requestedValue: Boolean): ToggleDecision =
        ToggleDecision(
            persistedValue = requestedValue,
            effect = when {
                currentValue == requestedValue -> ToggleEffect.NONE
                requestedValue -> ToggleEffect.REQUEST_RECOVERY_SYNC
                else -> ToggleEffect.CANCEL_WATCHDOG
            },
        )

    /** Preserve newer stored state unless this edit explicitly changes the loaded value. */
    fun valueToPersist(
        loadedValue: Boolean,
        requestedValue: Boolean,
        storedValue: Boolean,
    ): Boolean =
        if (requestedValue == loadedValue) storedValue else requestedValue

    fun shouldEnableFromList(hasVpnPackages: Boolean): Boolean = hasVpnPackages
}
