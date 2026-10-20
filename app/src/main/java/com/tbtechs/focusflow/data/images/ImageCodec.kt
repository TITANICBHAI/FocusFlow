package com.tbtechs.focusflow.data.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build

data class ImageBounds(
    val width: Int,
    val height: Int,
)

/**
 * Platform-neutral image operations so storage and safety rules can be tested with a fake codec.
 */
interface ImageCodec {
    fun bounds(bytes: ByteArray): ImageBounds?

    /** Decodes, scales to [maxLongEdge], and re-encodes to WebP using [quality]. */
    fun reencode(bytes: ByteArray, maxLongEdge: Int, quality: Int): ByteArray?
}

class AndroidBitmapImageCodec : ImageCodec {
    override fun bounds(bytes: ByteArray): ImageBounds? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return ImageBounds(options.outWidth, options.outHeight)
    }

    override fun reencode(bytes: ByteArray, maxLongEdge: Int, quality: Int): ByteArray? {
        if (maxLongEdge <= 0) return null
        val bounds = bounds(bytes) ?: return null
        var sampleSize = 1
        while (maxOf(bounds.width / sampleSize, bounds.height / sampleSize) > maxLongEdge) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val scaled = if (maxOf(decoded.width, decoded.height) > maxLongEdge) {
            val scale = maxLongEdge.toFloat() / maxOf(decoded.width, decoded.height)
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            decoded
        }
        return try {
            val output = java.io.ByteArrayOutputStream()
            val format = if (Build.VERSION.SDK_INT >= 30) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            if (!scaled.compress(format, quality.coerceIn(1, 100), output)) null
            else output.toByteArray()
        } finally {
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
        }
    }
}
