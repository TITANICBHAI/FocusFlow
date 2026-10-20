package com.tbtechs.focusflow.data.images

import android.graphics.Bitmap
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
            assertFalse(containsBytes(store.openForExport(exifAsset)!!, "Exif".toByteArray()))

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

    private fun injectExifSegment(jpeg: ByteArray): ByteArray {
        val exifPayload = byteArrayOf(
            0x45, 0x78, 0x69, 0x66, 0, 0, // Exif marker
            0x49, 0x49, 0x2a, 0, 8, 0, 0, 0, // little-endian TIFF header
            0, 0, 0, 0, 0, 0, // empty IFD and next-IFD offset
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
