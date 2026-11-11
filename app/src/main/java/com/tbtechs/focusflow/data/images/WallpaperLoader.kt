package com.tbtechs.focusflow.data.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import java.util.WeakHashMap
import java.util.concurrent.Executors

/**
 * The single place wallpaper images are decoded.
 *
 * - Only canonical paths inside the app's private `filesDir` are opened
 *   (see [WallpaperPathPolicy]); anything else yields null.
 * - Image bounds are read first and absurd sizes are refused.
 * - The bitmap is sub-sampled to roughly the display size, so a large source
 *   cannot exhaust memory.
 * - [loadInto] decodes on a background thread and applies the result on the
 *   main thread. Every failure (including OutOfMemoryError) yields null.
 */
object WallpaperLoader {
    /** Sanity ceiling; sub-sampling keeps normal large photos safe well below this. */
    private const val MAX_SOURCE_PIXELS = 256L * 1024 * 1024

    private val executor by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "wallpaper-loader").apply { isDaemon = true }
        }
    }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val latestRequest = WeakHashMap<ImageView, Long>()
    private var requestCounter = 0L

    /** Largest power-of-two sample size that keeps both dimensions at or above the target. */
    internal fun sampleSizeFor(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        if (width <= 0 || height <= 0 || targetWidth <= 0 || targetHeight <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        return sample
    }

    /** Blocking. Call off the main thread. Returns null on any failure. */
    fun decode(context: Context, rawPath: String?): Bitmap? {
        val appContext = context.applicationContext ?: context
        val file = WallpaperPathPolicy.resolve(appContext.filesDir, rawPath) ?: return null
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, boundsOptions)
            val width = boundsOptions.outWidth
            val height = boundsOptions.outHeight
            if (width <= 0 || height <= 0 || width.toLong() * height > MAX_SOURCE_PIXELS) {
                return null
            }
            val metrics = appContext.resources.displayMetrics
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(width, height, metrics.widthPixels, metrics.heightPixels)
            }
            BitmapFactory.decodeFile(file.path, options)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Decodes [rawPath] off the main thread and sets it on [view] (null clears the
     * image). Must be called on the main thread. A newer call for the same view
     * supersedes an older one that has not finished.
     */
    fun loadInto(view: ImageView, rawPath: String?) {
        val appContext = view.context.applicationContext ?: view.context
        val token = synchronized(latestRequest) {
            requestCounter += 1
            latestRequest[view] = requestCounter
            requestCounter
        }
        try {
            executor.execute {
                val bitmap = decode(appContext, rawPath)
                mainHandler.post {
                    val current = synchronized(latestRequest) { latestRequest[view] }
                    if (current == token) view.setImageBitmap(bitmap)
                }
            }
        } catch (_: Throwable) {
            // Executor rejected the work: leave the view as it is.
        }
    }
}
