package com.tbtechs.focusflow.data.images

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import androidx.core.content.FileProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Sets the FocusFlow wallpaper as the phone's own wallpaper, so Recents, app switching and
 * the lock screen match the launcher.
 *
 * This is the one place that does it. The Wallpaper screen and the launcher both call it, so
 * they cannot drift apart.
 *
 * What it adds over a bare setBitmap call:
 * - Home and lock are set in two separate calls. Some phones ignore a combined request.
 * - Each call is checked. The phone's answer and its reported wallpaper id are compared, so a
 *   silent failure is reported as one instead of as "updated".
 * - When the lock screen did not take, [systemPickerIntent] gives the person a way to finish
 *   it in the phone's own wallpaper picker.
 */
object PhoneWallpaper {

    enum class Outcome { NOT_ASKED, DONE, UNCONFIRMED, FAILED }

    /**
     * [home] and [lock] describe what happened for each screen. [blockedReason] is set when the
     * phone does not allow an app to change the wallpaper at all.
     */
    class PhoneWallpaperResult(
        val home: Outcome,
        val lock: Outcome,
        val blockedReason: String? = null,
    ) {
        /** True when something was asked for and the phone did not clearly accept all of it. */
        val needsSystemPicker: Boolean
            get() = blockedReason == null && (home.isProblem() || lock.isProblem())

        val allDone: Boolean
            get() = blockedReason == null && !home.isProblem() && !lock.isProblem() &&
                (home == Outcome.DONE || lock == Outcome.DONE)
    }

    private fun Outcome.isProblem() = this == Outcome.FAILED || this == Outcome.UNCONFIRMED

    /** Blocking: call off the main thread. [flags] is FLAG_SYSTEM, FLAG_LOCK or both. */
    fun apply(context: Context, bitmap: Bitmap, flags: Int): PhoneWallpaperResult {
        val wantHome = flags and WallpaperManager.FLAG_SYSTEM != 0
        val wantLock = flags and WallpaperManager.FLAG_LOCK != 0
        val askedHome = if (wantHome) Outcome.FAILED else Outcome.NOT_ASKED
        val askedLock = if (wantLock) Outcome.FAILED else Outcome.NOT_ASKED
        val manager = try {
            WallpaperManager.getInstance(context.applicationContext)
        } catch (_: Exception) {
            return PhoneWallpaperResult(
                askedHome,
                askedLock,
                "This phone doesn't let apps change the wallpaper.",
            )
        }
        val blocked = blockedReason(manager)
        if (blocked != null) {
            return PhoneWallpaperResult(askedHome, askedLock, blocked)
        }
        val home = if (wantHome) {
            setOne(manager, bitmap, WallpaperManager.FLAG_SYSTEM, WallpaperManager.FLAG_SYSTEM)
        } else {
            Outcome.NOT_ASKED
        }
        val lock = if (wantLock) setLock(manager, bitmap, alsoHome = wantHome) else Outcome.NOT_ASKED
        return PhoneWallpaperResult(home, lock)
    }

    /**
     * The lock screen is the one phones disagree about, so it gets more than one try, stopping
     * at the first that the phone clearly confirms: a bitmap for the lock screen alone, then
     * the same as a stream (some phone makers handle only one of the two), then, when the home
     * screen is being set as well, both screens in a single call.
     */
    private fun setLock(manager: WallpaperManager, bitmap: Bitmap, alsoHome: Boolean): Outcome {
        var best = setOne(manager, bitmap, WallpaperManager.FLAG_LOCK, WallpaperManager.FLAG_LOCK)
        if (best == Outcome.DONE) return best
        best = better(best, setOneStream(manager, bitmap, WallpaperManager.FLAG_LOCK))
        if (best == Outcome.DONE || !alsoHome) return best
        return better(
            best,
            setOne(
                manager,
                bitmap,
                WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
                WallpaperManager.FLAG_LOCK,
            ),
        )
    }

    /** The more trustworthy of two outcomes: done, then unconfirmed, then failed. */
    internal fun better(a: Outcome, b: Outcome): Outcome {
        fun rank(o: Outcome) = when (o) {
            Outcome.DONE -> 3
            Outcome.UNCONFIRMED -> 2
            Outcome.FAILED -> 1
            Outcome.NOT_ASKED -> 0
        }
        return if (rank(b) > rank(a)) b else a
    }

    private fun blockedReason(manager: WallpaperManager): String? = try {
        when {
            !manager.isWallpaperSupported ->
                "This phone doesn't let apps change the wallpaper."
            !manager.isSetWallpaperAllowed ->
                "Your phone's settings don't allow apps to change the wallpaper."
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    /** [flags] is what is asked for; [verifyFlag] is the one screen whose id is checked. */
    private fun setOne(manager: WallpaperManager, bitmap: Bitmap, flags: Int, verifyFlag: Int): Outcome {
        val id = try {
            manager.setBitmap(bitmap, null, true, flags)
        } catch (_: Exception) {
            return Outcome.FAILED
        }
        return verify(manager, id, verifyFlag)
    }

    /** Same, handing the picture over as a stream instead of a bitmap. */
    private fun setOneStream(manager: WallpaperManager, bitmap: Bitmap, flag: Int): Outcome {
        val id = try {
            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            manager.setStream(ByteArrayInputStream(bytes.toByteArray()), null, true, flag)
        } catch (_: Exception) {
            return Outcome.FAILED
        }
        return verify(manager, id, flag)
    }

    private fun verify(manager: WallpaperManager, id: Int, flag: Int): Outcome {
        if (id <= 0) return Outcome.FAILED
        val reported = try {
            manager.getWallpaperId(flag)
        } catch (_: Exception) {
            Int.MIN_VALUE
        }
        return if (reported == id) Outcome.DONE else Outcome.UNCONFIRMED
    }

    /** What to tell the person. Pure text, so it can be unit tested. */
    internal fun summary(result: PhoneWallpaperResult): String {
        result.blockedReason?.let { return it }
        val screens = listOf("home screen" to result.home, "lock screen" to result.lock)
            .filter { it.second != Outcome.NOT_ASKED }
        fun named(outcome: Outcome) = screens.filter { it.second == outcome }.map { it.first }
        val done = named(Outcome.DONE)
        val unconfirmed = named(Outcome.UNCONFIRMED)
        val failed = named(Outcome.FAILED)
        val parts = mutableListOf<String>()
        if (done.isNotEmpty()) {
            val tail = if (done.size == 2) " Recents and the lock screen now match." else ""
            parts += "Wallpaper updated on your ${done.joinToString(" and ")}.$tail"
        }
        if (unconfirmed.isNotEmpty()) {
            parts += "Your phone took the ${unconfirmed.joinToString(" and ")} wallpaper but didn't " +
                "confirm it. If it looks unchanged, finish in the system picker."
        }
        if (failed.isNotEmpty()) {
            parts += "Your phone didn't accept the ${failed.joinToString(" and ")} wallpaper."
        }
        return parts.joinToString(" ")
    }

    /** [summary], plus the phone and Android version when something went wrong. */
    fun message(result: PhoneWallpaperResult): String {
        val text = summary(result)
        if (!result.needsSystemPicker && result.blockedReason == null) return text
        val device = listOfNotNull(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return "$text ($device, Android ${Build.VERSION.RELEASE})"
    }

    /**
     * The bitmap to hand to the phone: exactly screen sized and centre-cropped, the same framing
     * the launcher shows. Blocking: call off the main thread. Null when nothing is selected.
     */
    fun screenBitmap(context: Context, customPath: String?, builtinId: String): Bitmap? {
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val spec = BuiltinWallpapers.byId(builtinId)
        return when {
            !customPath.isNullOrBlank() ->
                WallpaperLoader.decode(context, customPath)?.let { centreCropped(it, width, height) }
            spec != null -> BuiltinWallpapers.render(spec, width, height)
            else -> null
        }
    }

    /**
     * The part of a [srcW] x [srcH] image that fills a [dstW] x [dstH] screen without
     * stretching, as left, top, right, bottom.
     */
    internal fun cropBox(srcW: Int, srcH: Int, dstW: Int, dstH: Int): IntArray {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) {
            return intArrayOf(0, 0, max(srcW, 0), max(srcH, 0))
        }
        // Compare aspect ratios without division: src is wider when srcW/srcH > dstW/dstH.
        return if (srcW.toLong() * dstH > dstW.toLong() * srcH) {
            val newW = (srcH.toLong() * dstW / dstH).toInt().coerceIn(1, srcW)
            val left = (srcW - newW) / 2
            intArrayOf(left, 0, left + newW, srcH)
        } else {
            val newH = (srcW.toLong() * dstH / dstW).toInt().coerceIn(1, srcH)
            val top = (srcH - newH) / 2
            intArrayOf(0, top, srcW, top + newH)
        }
    }

    private fun centreCropped(src: Bitmap, width: Int, height: Int): Bitmap {
        if (src.width == width && src.height == height) return src
        val box = cropBox(src.width, src.height, width, height)
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        Canvas(out).drawBitmap(src, Rect(box[0], box[1], box[2], box[3]), Rect(0, 0, width, height), paint)
        return out
    }

    /**
     * An intent that opens the phone's own "set wallpaper" screen on [bitmap], where the person
     * picks Home, Lock or both. Used when the lock screen did not take. Blocking: call off the
     * main thread, then start the activity on the main thread. Null if it cannot be built.
     */
    fun systemPickerIntent(context: Context, bitmap: Bitmap): Intent? {
        val app = context.applicationContext
        return try {
            val dir = File(app.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "focusflow-wallpaper.jpg")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
            val crop = try {
                WallpaperManager.getInstance(app).getCropAndSetWallpaperIntent(uri)
            } catch (_: Exception) {
                null
            }
            val base = crop ?: Intent(Intent.ACTION_ATTACH_DATA)
                .setDataAndType(uri, "image/jpeg")
                .putExtra("mimeType", "image/jpeg")
            base.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            if (crop == null) Intent.createChooser(base, "Set wallpaper").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else base
        } catch (_: Throwable) {
            null
        }
    }
}
