package com.tbtechs.focusflow.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsWallpaperFlagsTest {
    @Test
    fun bothWallpaperNoticesStartOpen() {
        val settings = AppSettings()
        assertFalse(settings.wallpaperMatchCardDismissed)
        assertFalse(settings.wallpaperPhoneNoteDismissed)
    }

    @Test
    fun closingOneNoticeDoesNotCloseTheOther() {
        val settings = AppSettings().copy(wallpaperMatchCardDismissed = true)
        assertTrue(settings.wallpaperMatchCardDismissed)
        assertFalse(settings.wallpaperPhoneNoteDismissed)
        val both = settings.copy(wallpaperPhoneNoteDismissed = true)
        assertTrue(both.wallpaperMatchCardDismissed && both.wallpaperPhoneNoteDismissed)
    }
}
