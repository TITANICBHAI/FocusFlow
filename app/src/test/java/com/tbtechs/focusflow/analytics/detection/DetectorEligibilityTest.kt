package com.tbtechs.focusflow.analytics.detection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorEligibilityTest {
    @Test
    fun feedAppsAreEligible() {
        assertTrue(DetectorEligibility.isEngagementDesigned("com.instagram.android", "social"))
        assertTrue(DetectorEligibility.isEngagementDesigned("com.google.android.youtube", "entertainment"))
    }

    @Test
    fun audioMessagingToolsAndUnknownAppsAreNot() {
        assertFalse(DetectorEligibility.isEngagementDesigned("com.spotify.music", "entertainment"))
        assertFalse(DetectorEligibility.isEngagementDesigned("com.example.podcastplayer", "entertainment"))
        assertFalse(DetectorEligibility.isEngagementDesigned("com.whatsapp", "communication"))
        assertFalse(DetectorEligibility.isEngagementDesigned("com.android.chrome", "utility"))
        assertFalse(DetectorEligibility.isEngagementDesigned("com.bank.app", "other"))
        assertFalse(DetectorEligibility.isEngagementDesigned("com.example.app", null))
    }
}
