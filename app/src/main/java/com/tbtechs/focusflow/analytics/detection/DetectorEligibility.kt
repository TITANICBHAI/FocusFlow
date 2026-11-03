package com.tbtechs.focusflow.analytics.detection

import java.util.Locale

/**
 * Decides which apps the "attention-capture" detectors may talk about.
 *
 * Those detectors say things like "you open this app out of habit". That is only fair for apps
 * that are built around endless feeds and short-form content. Messaging, maps, browsers, music
 * and podcast players, and anything we could not classify are excluded, because heavy or daily
 * use of those is normal and a finding about them would be a false alarm.
 */
internal object DetectorEligibility {
    private val AUDIO_HINTS = listOf(
        "spotify", "music", "podcast", "audible", "soundcloud", "deezer",
        "pandora", "radio", "audiobook", "shazam",
    )

    fun isEngagementDesigned(packageName: String, category: String?): Boolean {
        if (category != "social" && category != "entertainment") return false
        val name = packageName.lowercase(Locale.ROOT)
        return AUDIO_HINTS.none { name.contains(it) }
    }
}
