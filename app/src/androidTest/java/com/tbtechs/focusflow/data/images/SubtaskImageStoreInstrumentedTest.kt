package com.tbtechs.focusflow.data.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubtaskImageStoreInstrumentedTest {
    @Test
    fun decodesSupportedFormatsReencodesToWebpAndRejectsOversizedDimensions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "subtask-image-store-test")
        directory.deleteRecursively()
        val store = SubtaskImageStore(directory, AndroidBitmapImageCodec())
        val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
        val formats = listOf(Bitmap.CompressFormat.PNG, Bitmap.CompressFormat.JPEG, webpFormat())
        try {
            formats.forEach { format ->
                val encoded = ByteArrayOutputStream().use { output ->
                    assertTrue(bitmap.compress(format, 85, output))
                    output.toByteArray()
                }
                val assetId = store.ingest(encoded).getOrThrow()
                assertTrue(store.exists(assetId))
                assertTrue(SubtaskImageStore.isWebp(store.openForExport(assetId)!!))
            }

            val jpeg = ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
                output.toByteArray()
            }
            val withExif = injectExifSegment(jpeg)
            val exifAsset = store.ingest(withExif).getOrThrow()
            val normalizedExif = store.openForExport(exifAsset)!!
            assertFalse(containsBytes(normalizedExif, "Exif".toByteArray()))
            val rotated = BitmapFactory.decodeByteArray(normalizedExif, 0, normalizedExif.size)
            try {
                assertTrue(rotated.width == 24 && rotated.height == 32)
            } finally {
                rotated.recycle()
            }

            val textBitmap = Bitmap.createBitmap(240, 90, Bitmap.Config.ARGB_8888)
            Canvas(textBitmap).apply {
                drawColor(Color.WHITE)
                drawText(
                    "FocusFlow",
                    8f,
                    62f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = 48f
                    },
                )
            }
            val textPng = ByteArrayOutputStream().use { output ->
                assertTrue(textBitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
            val textAsset = store.ingest(textPng).getOrThrow()
            val decodedText = store.openForExport(textAsset)!!
            val restoredText = BitmapFactory.decodeByteArray(decodedText, 0, decodedText.size)
            try {
                assertTrue(restoredText.width == textBitmap.width)
                assertTrue(restoredText.height == textBitmap.height)
                if (Build.VERSION.SDK_INT >= 30) {
                    for (y in 0 until textBitmap.height) {
                        for (x in 0 until textBitmap.width) {
                            assertTrue(
                                "PNG-derived WebP must preserve pixel ($x, $y).",
                                textBitmap.getPixel(x, y) == restoredText.getPixel(x, y),
                            )
                        }
                    }
                }
            } finally {
                restoredText.recycle()
                textBitmap.recycle()
            }

            val oversized = Bitmap.createBitmap(4_097, 1, Bitmap.Config.ARGB_8888)
            val oversizedPng = ByteArrayOutputStream().use { output ->
                assertTrue(oversized.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
            assertTrue(store.ingest(oversizedPng).isFailure)
            oversized.recycle()
        } finally {
            bitmap.recycle()
            directory.deleteRecursively()
        }
    }

    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= 30) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }

    private fun injectExifSegment(jpeg: ByteArray, orientation: Int = 6): ByteArray {
        val exifPayload = byteArrayOf(
            0x45, 0x78, 0x69, 0x66, 0, 0, // Exif marker
            0x49, 0x49, 0x2a, 0, 8, 0, 0, 0, // little-endian TIFF header
            1, 0, // one IFD entry
            0x12, 0x01, // orientation tag
            3, 0, // SHORT
            1, 0, 0, 0, // one value
            orientation.toByte(), 0, 0, 0, // orientation value and padding
            0, 0, 0, 0, // next-IFD offset
        )
        val segmentLength = exifPayload.size + 2
        return ByteArrayOutputStream().use { output ->
            output.write(jpeg, 0, 2)
            output.write(0xff)
            output.write(0xe1)
            output.write(segmentLength shr 8)
            output.write(segmentLength and 0xff)
            output.write(exifPayload)
            output.write(jpeg, 2, jpeg.size - 2)
            output.toByteArray()
        }
    }

    private fun containsBytes(source: ByteArray, needle: ByteArray): Boolean =
        source.indices.any { start ->
            start + needle.size <= source.size &&
                source.copyOfRange(start, start + needle.size).contentEquals(needle)
        }
}
