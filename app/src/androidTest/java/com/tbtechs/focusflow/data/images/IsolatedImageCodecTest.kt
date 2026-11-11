package com.tbtechs.focusflow.data.images

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against the real isolated process, so it needs a device or emulator. */
@RunWith(AndroidJUnit4::class)
class IsolatedImageCodecTest {
    private lateinit var codec: IsolatedImageCodec

    @Before
    fun setUp() {
        codec = IsolatedImageCodec(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    @Test
    fun readsBoundsInTheIsolatedProcess() {
        assertEquals(ImageBounds(64, 32), codec.bounds(png(64, 32)))
    }

    @Test
    fun reencodesToWebp() {
        val encoded = codec.reencodeWithOrientation(png(200, 100), 100, 80, 1, true)
        assertNotNull(encoded)
        assertTrue(SubtaskImageStore.isWebp(encoded!!))
    }

    @Test
    fun rejectsGarbageAndKeepsWorkingAfterwards() {
        assertNull(codec.bounds(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
        assertNull(codec.reencode(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), 100, 80))
        assertEquals(ImageBounds(8, 8), codec.bounds(png(8, 8)))
    }

    @Test
    fun handlesSeveralImagesBackToBack() {
        repeat(5) { assertEquals(ImageBounds(16 + it, 16), codec.bounds(png(16 + it, 16))) }
    }
}
