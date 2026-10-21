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
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val oriented = applyExifOrientation(decoded, orientation)
        val scaled = if (maxOf(oriented.width, oriented.height) > maxLongEdge) {
            val scale = maxLongEdge.toFloat() / maxOf(oriented.width, oriented.height)
            Bitmap.createScaledBitmap(
                oriented,
                (oriented.width * scale).toInt().coerceAtLeast(1),
                (oriented.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else oriented
        return try {
            val output = java.io.ByteArrayOutputStream()
            val format = if (pngSource && Build.VERSION.SDK_INT >= 30) {
                Bitmap.CompressFormat.WEBP_LOSSLESS
            } else if (Build.VERSION.SDK_INT >= 30) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            val outputQuality = if (pngSource && Build.VERSION.SDK_INT >= 30) {
                100
            } else {
                quality.coerceIn(1, 100)
            }
            if (!scaled.compress(format, outputQuality, output)) null
            else output.toByteArray()
        } finally {
            if (scaled !== decoded) scaled.recycle()
            if (oriented !== decoded && oriented !== scaled) oriented.recycle()
            decoded.recycle()
        }
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

    private fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a),
        )
}
