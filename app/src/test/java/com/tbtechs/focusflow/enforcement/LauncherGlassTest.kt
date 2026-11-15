package com.tbtechs.focusflow.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherGlassTest {
    @Test
    fun zeroGlassIsPlainBlack() {
        assertEquals(0f, LauncherGlass.wallpaperFade(0f), 0f)
    }

    @Test
    fun wallpaperIsFullyVisibleOnceTheFadeEnds() {
        assertEquals(1f, LauncherGlass.wallpaperFade(LauncherGlass.FADE_END), 0f)
    }

    @Test
    fun anyHigherGlassNeverDimsTheWallpaperAgain() {
        for (percent in 15..100) {
            assertEquals("at $percent%", 1f, LauncherGlass.wallpaperFade(percent / 100f), 0f)
        }
    }

    @Test
    fun fadeRisesSteadilyAndStaysInRange() {
        var last = -1f
        for (percent in 0..100) {
            val fade = LauncherGlass.wallpaperFade(percent / 100f)
            assertTrue("never goes backwards at $percent%", fade >= last)
            assertTrue(fade in 0f..1f)
            last = fade
        }
    }
}
