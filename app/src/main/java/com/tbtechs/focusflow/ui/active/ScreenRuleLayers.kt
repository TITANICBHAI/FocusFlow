package com.tbtechs.focusflow.ui.active

import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.enforcement.customrules.CustomScreenRule

/**
 * What the "Screen rules" layer on the Active tab is made of: the person's custom screen rules plus the
 * built-in Shorts, Reels and install protections. One count so the card and the header pulse agree.
 */
data class ScreenRuleLayers(
    val customEnabled: Int,
    val customTotal: Int,
    val shorts: Boolean,
    val reels: Boolean,
    val installProtection: Boolean,
) {
    /** Number of individual things switched on. */
    val activeCount: Int
        get() = customEnabled + listOf(shorts, reels, installProtection).count { it }

    /** True when at least one of them is on: the whole layer counts as one active layer. */
    val isActive: Boolean get() = activeCount > 0

    companion object {
        fun from(settings: AppSettings, customRules: List<CustomScreenRule>): ScreenRuleLayers =
            ScreenRuleLayers(
                customEnabled = customRules.count { it.enabled },
                customTotal = customRules.size,
                shorts = settings.blockYoutubeShortsEnabled,
                reels = settings.blockInstagramReelsEnabled,
                installProtection = settings.blockInstallActionsEnabled,
            )
    }
}
