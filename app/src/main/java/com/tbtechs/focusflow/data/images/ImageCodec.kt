package com.tbtechs.focusflow.data.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import java.io.ByteArrayInputStream

data class ImageBounds(
    val width: Int,
    val height: Int,
) {
    fun rotatedForExif(orientation: Int): ImageBounds =
        if (orientation in EXIF_ORIENTATIONS_THAT_SWAP_AXES) {
            ImageBounds(width = height, height = width)
        } else {
            this
        }

    private companion object {
        val EXIF_ORIENTATIONS_THAT_SWAP_AXES = setOf(5, 6, 7, 8)
    }
}

/**
 * Platform-neutral image operations so storage and safety rules can be tested with a fake codec.
 */
interface ImageCodec {
    fun bounds(bytes: ByteArray): ImageBounds?

    fun orientation(bytes: ByteArray): Int = 1

    /** A short reason the last call failed (for example "it took too long"), or null. */
    fun lastFailure(): String? = null

    /** Decodes, scales to [maxLongEdge], and re-encodes to WebP using [quality]. */
    fun reencode(bytes: ByteArray, maxLongEdge: Int, quality: Int): ByteArray?

    /** Compatibility hook for codecs that need orientation and source-format details. */
    fun reencodeWithOrientation(
        bytes: ByteArray,
        maxLongEdge: Int,
        quality: Int,
        orientation: Int,
        pngSource: Boolean,
    ): ByteArray? = reencode(bytes, maxLongEdge, quality)
}

class AndroidBitmapImageCodec : ImageCodec {
    override fun orientation(bytes: ByteArray): Int =
        runCatching {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    override fun bounds(bytes: ByteArray): ImageBounds? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return ImageBounds(options.outWidth, options.outHeight)
    }

    override fun reencode(bytes: ByteArray, maxLongEdge: Int, quality: Int): ByteArray? =
        reencodeWithOrientation(
            bytes = bytes,
            maxLongEdge = maxLongEdge,
            quality = quality,
            orientation = orientation(bytes),
            pngSource = isPng(bytes),
        )

    override fun reencodeWithOrientation(
        bytes: ByteArray,
        maxLongEdge: Int,
        quality: Int,
        orientation: Int,
        pngSource: Boolean,
    ): ByteArray? {
        if (maxLongEdge <= 0) return null
        val bounds = bounds(bytes) ?: return null
        var sampleSize = 1
        while (maxOf(bounds.width / sampleSize, bounds.height / sampleSize) > maxLongEdge) {
            sampleSize *= 2
        }
        // Huge photos can run out of memory at the first sample size; retry coarser instead of failing.
        var attempts = 0
        while (true) {
            try {
                return decodeAndEncode(bytes, sampleSize, maxLongEdge, quality, orientation)
            } catch (oom: OutOfMemoryError) {
                attempts++
                if (attempts >= 3) throw oom
                sampleSize *= 2
            }
        }
    }

    private fun decodeAndEncode(
        bytes: ByteArray,
        sampleSize: Int,
        maxLongEdge: Int,
        quality: Int,
        orientation: Int,
    ): ByteArray? {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        var oriented: Bitmap? = null
        var scaled: Bitmap? = null
        try {
            oriented = applyExifOrientation(decoded, orientation)
            scaled = if (maxOf(oriented.width, oriented.height) > maxLongEdge) {
                val scale = maxLongEdge.toFloat() / maxOf(oriented.width, oriented.height)
                Bitmap.createScaledBitmap(
                    oriented,
                    (oriented.width * scale).toInt().coerceAtLeast(1),
                    (oriented.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else oriented
            val output = java.io.ByteArrayOutputStream()
            // Only pictures that really have see-through pixels need lossless WebP. Lossless at
            // full effort is slow enough to time out on mid-range phones, and AI-generated PNGs
            // are almost always opaque, so they take the fast lossy path like photos.
            val needsLossless = Build.VERSION.SDK_INT >= 30 && hasTransparentPixels(scaled)
            val format = when {
                needsLossless -> Bitmap.CompressFormat.WEBP_LOSSLESS
                Build.VERSION.SDK_INT >= 30 -> Bitmap.CompressFormat.WEBP_LOSSY
                else -> {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
            val outputQuality = if (needsLossless) LOSSLESS_EFFORT else quality.coerceIn(1, 100)
            return if (!scaled.compress(format, outputQuality, output)) null
            else output.toByteArray()
        } finally {
            if (scaled != null && scaled !== decoded) scaled.recycle()
            if (oriented != null && oriented !== decoded && oriented !== scaled) oriented.recycle()
            decoded.recycle()
        }
    }

    private fun hasTransparentPixels(bitmap: Bitmap): Boolean {
        if (!bitmap.hasAlpha()) return false
        val width = bitmap.width
        val row = IntArray(width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            for (pixel in row) {
                if ((pixel ushr 24) < 255) return true
            }
        }
        return false
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        if (orientation !in 2..8) return bitmap
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val matrix = Matrix()
        val values = when (orientation) {
            2 -> floatArrayOf(-1f, 0f, width, 0f, 1f, 0f, 0f, 0f, 1f)
            3 -> floatArrayOf(-1f, 0f, width, 0f, -1f, height, 0f, 0f, 1f)
            4 -> floatArrayOf(1f, 0f, 0f, 0f, -1f, height, 0f, 0f, 1f)
            5 -> floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
            6 -> floatArrayOf(0f, -1f, height, 1f, 0f, 0f, 0f, 0f, 1f)
            7 -> floatArrayOf(0f, -1f, height, -1f, 0f, width, 0f, 0f, 1f)
            8 -> floatArrayOf(0f, 1f, 0f, -1f, 0f, width, 0f, 0f, 1f)
            else -> return bitmap
        }
        matrix.setValues(values)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        /** Lossless WebP "quality" is really encoder effort; 70 is fast and still lossless. */
        const val LOSSLESS_EFFORT = 70
    }

    private fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a),
        )
}
