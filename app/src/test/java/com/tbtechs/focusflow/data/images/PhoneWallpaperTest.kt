package com.tbtechs.focusflow.data.images

import com.tbtechs.focusflow.data.images.PhoneWallpaper.Outcome
import com.tbtechs.focusflow.data.images.PhoneWallpaper.PhoneWallpaperResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneWallpaperTest {
    @Test
    fun bothScreensDoneSaysSoAndNeedsNoPicker() {
        val result = PhoneWallpaperResult(Outcome.DONE, Outcome.DONE)
        assertTrue(result.allDone)
        assertFalse(result.needsSystemPicker)
        assertTrue(PhoneWallpaper.summary(result).startsWith("Wallpaper updated on your home screen and lock screen."))
    }

    @Test
    fun failedLockScreenIsReportedAndOffersThePicker() {
        val result = PhoneWallpaperResult(Outcome.DONE, Outcome.FAILED)
        assertFalse(result.allDone)
        assertTrue(result.needsSystemPicker)
        val text = PhoneWallpaper.summary(result)
        assertTrue(text, text.contains("updated on your home screen"))
        assertTrue(text, text.contains("didn't accept the lock screen wallpaper"))
    }

    @Test
    fun unconfirmedLockScreenIsNotCalledSuccess() {
        val result = PhoneWallpaperResult(Outcome.DONE, Outcome.UNCONFIRMED)
        assertTrue(result.needsSystemPicker)
        assertTrue(PhoneWallpaper.summary(result).contains("didn't confirm"))
    }

    @Test
    fun homeOnlyDoesNotMentionTheLockScreen() {
        val result = PhoneWallpaperResult(Outcome.DONE, Outcome.NOT_ASKED)
        assertTrue(result.allDone)
        assertEquals("Wallpaper updated on your home screen.", PhoneWallpaper.summary(result))
    }

    @Test
    fun aPhoneThatBlocksWallpapersGivesItsReasonAndNoPicker() {
        val result = PhoneWallpaperResult(Outcome.FAILED, Outcome.FAILED, "Your phone's settings don't allow apps to change the wallpaper.")
        assertFalse(result.needsSystemPicker)
        assertEquals("Your phone's settings don't allow apps to change the wallpaper.", PhoneWallpaper.summary(result))
    }

    @Test
    fun cropKeepsTheCentreOfAWiderPicture() {
        // 2000x1000 into a 500x1000 screen: keep the middle 500 columns.
        assertArrayEquals(intArrayOf(750, 0, 1250, 1000), PhoneWallpaper.cropBox(2000, 1000, 500, 1000))
    }

    @Test
    fun cropKeepsTheCentreOfATallerPicture() {
        // 1000x4000 into a 1000x2000 screen: keep the middle 2000 rows.
        assertArrayEquals(intArrayOf(0, 1000, 1000, 3000), PhoneWallpaper.cropBox(1000, 4000, 1000, 2000))
    }

    @Test
    fun cropOfAMatchingShapeKeepsEverything() {
        assertArrayEquals(intArrayOf(0, 0, 1080, 2400), PhoneWallpaper.cropBox(1080, 2400, 540, 1200))
    }

    @Test
    fun cropToleratesZeroSizes() {
        assertArrayEquals(intArrayOf(0, 0, 0, 0), PhoneWallpaper.cropBox(0, 0, 100, 100))
    }

    @Test
    fun betterKeepsTheMostTrustworthyOutcome() {
        assertEquals(Outcome.DONE, PhoneWallpaper.better(Outcome.FAILED, Outcome.DONE))
        assertEquals(Outcome.DONE, PhoneWallpaper.better(Outcome.DONE, Outcome.FAILED))
        assertEquals(Outcome.UNCONFIRMED, PhoneWallpaper.better(Outcome.FAILED, Outcome.UNCONFIRMED))
        assertEquals(Outcome.UNCONFIRMED, PhoneWallpaper.better(Outcome.UNCONFIRMED, Outcome.FAILED))
        assertEquals(Outcome.FAILED, PhoneWallpaper.better(Outcome.FAILED, Outcome.FAILED))
    }
}
