package com.tbtechs.focusflow.enforcement

import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.domain.PinManager
import com.tbtechs.focusflow.widget.WidgetMode
import com.tbtechs.focusflow.widget.WidgetSession
import com.tbtechs.focusflow.widget.WidgetSnapshot
import com.tbtechs.focusflow.widget.WidgetSnapshotBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.tbtechs.focusflow.data.images.BuiltinWallpapers
import com.tbtechs.focusflow.data.images.PhoneWallpaper
import com.tbtechs.focusflow.data.images.WallpaperLoader
import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.TextUtils
import android.text.TextWatcher
import android.os.Process
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.VelocityTracker
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * LauncherActivity — FocusFlow's full home-screen replacement.
 *
 * One look, one structure. A single Glassiness level (0..100) controls the whole design:
 * at 0 the launcher is plain black; as it rises the wallpaper fades in and every panel
 * becomes frosted glass (a blurred copy of the wallpaper behind it, a light tint and a thin
 * bright edge). The home screen is a scrolling top area plus a dock (search, All Apps,
 * FocusFlow, and Hidden apps when something is hidden) that is always pinned to the bottom.
 *
 * Swipe up opens the app drawer. The drawer is a grid or a list (chosen in its ⋮ menu),
 * long-press any app to hide it, add it to Focus tools, lock it or open App info.
 */
class LauncherActivity : Activity() {

    companion object {
        const val ACTION_OPEN_DAY_RATING = "com.tbtechs.focusflow.action.OPEN_DAY_RATING"
        private const val PREFS_NAME = AppBlockerAccessibilityService.PREFS_NAME
        private const val PREF_LAUNCHER_HIDDEN = "launcher_hidden_packages"
        // Same key SettingsRepository writes when a wallpaper is picked in Launcher setup.
        private const val PREF_LAUNCHER_WALLPAPER = SettingsRepository.PREF_LAUNCHER_WALLPAPER_URI
        private const val PREF_LAUNCHER_THEME = "launcher_theme"
        private const val PREF_GLASS_LEVEL = "launcher_glass_level"
        private const val PREF_DRAWER_LAYOUT = "launcher_drawer_layout"
        private const val PREF_FOCUS_TOOLS = "focus_tool_packages"
        private const val PREF_DRAWER_HIDDEN = "drawer_hidden_packages"
        private const val PREF_DRAWER_ORDER = "drawer_custom_order"
        private const val PREF_DRAWER_SCALE = "drawer_icon_scale"
        private const val PREF_DRAWER_COLUMNS = "drawer_grid_columns"
        private const val PREF_DRAWER_SECTIONS = "drawer_sections"
        private const val STATUS_READY = "Ready"
        /** Same key the app's Wallpaper screen uses, so closing the note in one closes it in both. */
        private const val PREF_PHONE_NOTE_DISMISSED = "wallpaper_phone_note_dismissed"
        /** The list drawer's icon at 100%; the Icon size slider scales it like the grid's. */
        private const val LIST_ICON_BASE_DP = 40
        private const val LIST_ROW_MIN_DP = 58
        private const val PREF_SA_ACTIVE = AppBlockerAccessibilityService.PREF_SA_ACTIVE
        private const val PREF_SA_PKGS = AppBlockerAccessibilityService.PREF_SA_PKGS
        private const val PREF_SA_UNTIL = AppBlockerAccessibilityService.PREF_SA_UNTIL
        private const val PREF_ALWAYS_BLOCK = AppBlockerAccessibilityService.PREF_ALWAYS_BLOCK
        private const val PREF_ALWAYS_BLOCK_PKGS = AppBlockerAccessibilityService.PREF_ALWAYS_BLOCK_PKGS
        private const val PREF_WALLPAPER_BUILTIN = "launcher_wallpaper_builtin"
        private const val PREF_HOME_CLOCK = "launcher_home_clock"
        private const val PREF_HOME_STATUS = "launcher_home_status"
        private const val PREF_HOME_TASK = "launcher_home_task"
        private const val PREF_HOME_LIMITS = "launcher_home_limits"
        private const val PREF_HOME_SEARCH = "launcher_home_search"
        private const val PREF_HOME_ALL_APPS = "launcher_home_all_apps"
        private const val PREF_HOME_ORDER = "launcher_home_order"
        /** "list" (the stacked home, default) or "grid" (the free grid, beta). */
        private const val PREF_HOME_LAYOUT = "launcher_home_layout"
        private val HOME_WIDGETS = listOf("clock", "status", "task", "limits", "pinned", "widgets")
        private const val PREF_HOME_WIDGETS = "launcher_home_widgets"
        private const val PREF_HOME_WIDGETS_ITEMS = HomeWidgetEntries.PREF_KEY
        private const val REQ_BIND_WIDGET = 4202
        private const val REQ_CONFIGURE_WIDGET = 4203
        private const val PREF_HOME_PINNED = "launcher_home_pinned"
        private const val PREF_HOME_PINNED_ITEMS = HomePinnedItems.PREF_KEY
        private const val PREF_HOME_PINNED_SIZE = HomePinnedItems.PREF_SIZE_KEY
        /** Rows shown in the home limits list; the rest are one tap away in the drawer. */
        private const val MAX_HOME_LIMITS = 3
        /** Screen-reader actions on the reorder handle. Any ids clear of the system's own. */
        private const val ARRANGE_ROW_DP = 64
        private const val ARRANGE_GAP_DP = 8
        private const val PREV_STYLE_SUFFIX = "_prev"
        private const val A11Y_MOVE_UP = 0x0F100001
        private const val A11Y_MOVE_DOWN = 0x0F100002
        private const val REQ_PICK_WALLPAPER = 4201
        private const val SIZE_DATE = 12f
        private const val SIZE_SEARCH_HINT = 14f
        private const val SIZE_HOME_LABEL = 12f

        private val ACCENT = Color.parseColor("#6366F1")
        private val ACCENT_TEXT = Color.parseColor("#A5B4FC")
        private val TEXT_PRIMARY = Color.parseColor("#F7F8FC")
        private val TEXT_DIM = Color.parseColor("#D3D9E8")
        private val TEXT_MUTED = Color.parseColor("#9AA3B8")
        private val RED_BLOCK = Color.parseColor("#F87171")
        private val GREEN_OK = Color.parseColor("#34D399")
        private val AMBER = Color.parseColor("#FBBF24")
        private val PANEL_BLACK = Color.rgb(10, 10, 12)
    }

    private enum class DrawerFilter { ALL, FOCUS_TOOLS, LIMITED_TODAY, BLOCKED }

    private data class AllowanceCardData(
        val pkg: String,
        val label: String,
        val icon: Drawable?,
        val used: Long,
        val total: Long,
        val remaining: Long,
        val mode: String,
        val displayText: String,
        val fraction: Float,
        val approximate: Boolean,
        /** Shorter wording for the small chip on an app icon. Empty means use [displayText]. */
        val chipText: String = "",
    )

    private sealed class DrawerItem {
        data class App(val packageName: String, val label: String) : DrawerItem()
    }

    private lateinit var prefs: SharedPreferences
    private val allowanceLedger by lazy { AllowanceLedgerProvider.get(applicationContext) }
    private val handler = Handler(Looper.getMainLooper())
    private var clockRunnable: Runnable? = null

    private lateinit var rootFrame: FrameLayout
    private var renderedSignature: String? = null
    private var glassLevel = 70
    private var barTop = 0
    private var barBottom = 0

    // Home views
    private var homeContent: LinearLayout? = null
    private var homeColumn: LinearLayout? = null
    /** Set instead of [homeColumn] and [homeScroll] while the Grid home layout is on. */
    private var homeGrid: HomeGridLayout? = null
    private var homeLongPress: View.OnLongClickListener? = null
    private var clockStyle = "light"
    private var suppressPrefRebuild = false
    private var drawerLimitInfo: Map<String, Pair<String, Float>> = emptyMap()
    private var homeScroll: ScrollView? = null
    private var dockView: LinearLayout? = null
    private var dockActionsRow: LinearLayout? = null
    private var homeBackdrop: ImageView? = null
    private var clockView: TextView? = null
    private var dateView: TextView? = null
    private var statusText: TextView? = null
    private var statusDot: View? = null
    private var focusCard: LinearLayout? = null
    private var focusHeaderView: TextView? = null
    private var focusTitleView: TextView? = null
    private var homeTasks: List<com.tbtechs.focusflow.data.model.Task>? = null
    private var homeSnapshot: WidgetSnapshot? = null
    private var homeSnapshotValidUntilMs = 0L
    private var taskCollectJob: Job? = null
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var focusSubtitleView: TextView? = null
    private var focusProgressTrack: FrameLayout? = null
    private var focusProgressFill: View? = null
    private var allowanceContainer: LinearLayout? = null

    // Backdrop (wallpaper or generated gradient) and its blurred twin used by glass panels.
    private var backdropBlur: Bitmap? = null
    private var backdropAurora: Bitmap? = null
    private var backdropKey: String? = null
    private var backdropToken = 0
    private val frosted = mutableListOf<FrostedDrawable>()
    private val iconCache = HashMap<String, Bitmap>()

    // Drawer
    private var drawerOverlay: FrameLayout? = null
    private var drawerBackdrop: ImageView? = null
    private var drawerSheet: LinearLayout? = null
    private var drawerHeader: LinearLayout? = null
    private var drawerChipRow: LinearLayout? = null
    private var drawerRecycler: RecyclerView? = null
    private var drawerSearchInput: EditText? = null
    private var drawerCustomizeSheet: LinearLayout? = null
    private var drawerAdapter: DrawerAdapter? = null
    private var drawerTouchHelper: ItemTouchHelper? = null
    private var drawerAllApps: List<DrawerItem.App> = emptyList()
    private var drawerFilter = DrawerFilter.ALL
    private var drawerBlockedCount = 0
    private var isDrawerOpen = false
    private var isEditMode = false
    private var editDraftScale = 1f
    private var editDraftColumns = 4
    private var focusedDrawerPackage: String? = null

    // Hidden apps screen and bottom sheets
    private var hiddenOverlay: FrameLayout? = null
    private var hiddenBackdrop: ImageView? = null
    private var hiddenListContainer: LinearLayout? = null
    private var sheetOverlay: FrameLayout? = null

    // Gestures
    private var swipeTouchStartY = 0f
    private var swipeStartedInDock = false
    private var swipeDrawerAtTop = true
    private var swipeMultiTouch = false
    private var pinchStartSpan = 0f
    private var pinchFired = false
    private val pinchDetector: ScaleGestureDetector by lazy {
        ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    pinchStartSpan = detector.currentSpan
                    pinchFired = false
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    if (!pinchFired && pinchStartSpan > 0f) {
                        val ratio = detector.currentSpan / pinchStartSpan
                        if (ratio < 0.8f || ratio > 1.25f) {
                            pinchFired = true
                            rootFrame.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            enterHomeArrange()
                        }
                    }
                    return true
                }
            },
        )
    }
    private var swipeStartScrollY = 0
    private var swipeContentCanScrollDown = false
    private var swipeContentScrolledDown = false
    private var swipeVelocityTracker: VelocityTracker? = null

    private val ownPackage: String
        get() = applicationContext.packageName

    private fun drawerIsList(): Boolean {
        val stored = prefs.getString(PREF_DRAWER_LAYOUT, null)
        if (stored == "list" || stored == "grid") return stored == "list"
        return prefs.getString(PREF_LAUNCHER_THEME, "glassy") == "classic"
    }

    private fun readGlassLevel(): Int {
        if (prefs.contains(PREF_GLASS_LEVEL)) {
            return prefs.getInt(PREF_GLASS_LEVEL, 70).coerceIn(0, 100)
        }
        return if (prefs.getString(PREF_LAUNCHER_THEME, "glassy") == "classic") 0 else 70
    }

    private fun glassFraction(): Float = glassLevel / 100f

    /**
     * How much of the wallpaper shows, 0..1. Glassiness 0 is a plain black launcher; the
     * wallpaper then fades in over the first few percent and from there on is shown at full
     * strength. Nothing dims it any more: no black blend, no darkening layer.
     */
    private fun wallpaperFade(): Float = LauncherGlass.wallpaperFade(glassFraction())

    private fun wallpaperPath(): String =
        prefs.getString(PREF_LAUNCHER_WALLPAPER, "")?.trim().orEmpty()

    private fun builtinWallpaperId(): String =
        prefs.getString(PREF_WALLPAPER_BUILTIN, "")?.takeIf { id -> BuiltinWallpapers.byId(id) != null }.orEmpty()

    private fun layoutSignature(): String = "${readGlassLevel()}|${wallpaperPath()}|${builtinWallpaperId()}"

    /**
     * Writes a look setting (glass level, wallpaper) without the preference listener
     * rebuilding the whole launcher underneath an open sheet or drawer. SharedPreferences
     * calls listeners synchronously on the main thread, so the guard has to wrap the write.
     */
    private fun writeLookPrefs(block: SharedPreferences.Editor.() -> Unit) {
        suppressPrefRebuild = true
        try {
            prefs.edit().apply(block).apply()
        } finally {
            suppressPrefRebuild = false
        }
        renderedSignature = layoutSignature()
    }

    // ── Glass: backdrop, blur and frosted panels ───────────────────────────────

    /**
     * A rounded panel that looks like frosted glass. It draws the matching region of the
     * blurred backdrop (so it really is "glass over the wallpaper"), a light tint, a soft top
     * sheen and a thin bright edge. At Glassiness 0 it is a flat near-black panel.
     */
    private inner class FrostedDrawable(
        val host: View,
        private val radiusPx: Float,
        private val extraDark: Float = 0f,
    ) : Drawable() {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1).toFloat()
        }
        private val rect = RectF()
        private val inner = RectF()
        private val matrix = Matrix()
        private val selfLoc = IntArray(2)
        private val rootLoc = IntArray(2)
        private var shader: BitmapShader? = null
        private var shaderBitmap: Bitmap? = null
        private var sheen: LinearGradient? = null
        private var edge: LinearGradient? = null
        private var builtKey = ""

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            rect.set(b)
            val w = rect.width()
            val h = rect.height()
            val g = glassFraction()

            val fade = wallpaperFade()

            // 1. Base: near-black, and only while the wallpaper is still fading in. At
            //    Glassiness 0 this is the whole panel; above the fade it is gone.
            fill.shader = null
            val baseAlpha = if (fade >= 1f) 0f else (0.97f * (1f - fade)).coerceIn(0f, 0.97f)
            if (baseAlpha > 0f) {
                fill.color = Color.argb((baseAlpha * 255).toInt(), 10, 10, 12)
                canvas.drawRoundRect(rect, radiusPx, radiusPx, fill)
            }

            // 2. The blurred wallpaper under this panel, at full strength.
            val bmp = backdropBlur
            if (fade > 0f && bmp != null && rootFrame.width > 0 && rootFrame.height > 0) {
                if (shaderBitmap !== bmp) {
                    shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                    shaderBitmap = bmp
                }
                host.getLocationInWindow(selfLoc)
                rootFrame.getLocationInWindow(rootLoc)
                val rw = rootFrame.width.toFloat()
                val rh = rootFrame.height.toFloat()
                val s = max(rw / bmp.width, rh / bmp.height)
                val ox = (rw - bmp.width * s) / 2f
                val oy = (rh - bmp.height * s) / 2f
                val left = (selfLoc[0] - rootLoc[0]).toFloat()
                val top = (selfLoc[1] - rootLoc[1]).toFloat()
                matrix.reset()
                matrix.setScale(s, s)
                matrix.postTranslate(ox - left, oy - top)
                shader?.setLocalMatrix(matrix)
                blurPaint.shader = shader
                blurPaint.alpha = (fade * 255).toInt()
                canvas.drawRoundRect(rect, radiusPx, radiusPx, blurPaint)
            }

            // 3. Frost. The higher the Glassiness, the thinner it is, so the wallpaper shows
            //    through more. A cool neutral tint plus a light veil: never a black layer.
            //    Panels that sit on top of everything (sheets) ask for a little more.
            if (g > 0f) {
                val frostAmount = 1f - g
                val tint = (0.06f + 0.16f * frostAmount + extraDark * 0.5f).coerceIn(0f, 0.6f)
                fill.color = Color.argb((tint * fade * 255).toInt(), 24, 28, 40)
                canvas.drawRoundRect(rect, radiusPx, radiusPx, fill)
                val veil = 0.04f + 0.10f * frostAmount
                fill.color = Color.argb((veil * fade * 255).toInt(), 255, 255, 255)
                canvas.drawRoundRect(rect, radiusPx, radiusPx, fill)
            } else {
                fill.color = Color.argb((0.03f * 255).toInt(), 255, 255, 255)
                canvas.drawRoundRect(rect, radiusPx, radiusPx, fill)
            }

            val key = "${w.toInt()}x${h.toInt()}@${glassLevel}"
            if (key != builtKey) {
                builtKey = key
                sheen = LinearGradient(
                    0f, 0f, 0f, h * 0.6f,
                    Color.argb((0.20f * g * wallpaperFade() * 255).toInt(), 255, 255, 255),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP,
                )
                edge = LinearGradient(
                    0f, 0f, w, h,
                    Color.argb(((0.12f + 0.40f * g) * 255).toInt(), 255, 255, 255),
                    Color.argb(((0.05f + 0.07f * g) * 255).toInt(), 255, 255, 255),
                    Shader.TileMode.CLAMP,
                )
            }

            // 4. Soft sheen along the top edge.
            if (g > 0f) {
                fill.shader = sheen
                canvas.drawRoundRect(rect, radiusPx, radiusPx, fill)
                fill.shader = null
            }

            // 5. Thin bright edge.
            inner.set(rect)
            inner.inset(0.5f, 0.5f)
            border.shader = edge
            canvas.drawRoundRect(inner, radiusPx, radiusPx, border)
        }

        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    /** Gives [view] a frosted-glass background. Returns the same view for chaining. */
    private fun <T : View> frost(view: T, radiusDp: Int, extraDark: Float = 0f): T {
        val drawable = FrostedDrawable(view, dp(radiusDp).toFloat(), extraDark)
        frosted += drawable
        view.background = drawable
        return view
    }

    private fun invalidateFrosted() {
        frosted.forEach { it.invalidateSelf() }
    }

    private fun pruneFrosted() {
        frosted.removeAll { !it.host.isAttachedToWindow }
    }

    private fun wallpaperPalette(): IntArray {
        val fallback = intArrayOf(
            Color.parseColor("#4F46E5"),
            Color.parseColor("#0EA5E9"),
            Color.parseColor("#C026D3"),
        )
        return try {
            val colors = WallpaperManager.getInstance(this)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return fallback
            intArrayOf(
                colors.primaryColor.toArgb(),
                colors.secondaryColor?.toArgb() ?: fallback[1],
                colors.tertiaryColor?.toArgb() ?: fallback[2],
            )
        } catch (_: Exception) {
            fallback
        }
    }

    private fun shrinkTo(src: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxEdge) return src
        val scale = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun blurBitmap(src: Bitmap, radius: Int, passes: Int): Bitmap {
        val w = src.width
        val h = src.height
        val a = IntArray(w * h)
        src.getPixels(a, 0, w, 0, 0, w, h)
        val b = IntArray(w * h)
        repeat(passes) {
            boxBlurPass(a, b, w, h, radius, horizontal = true)
            boxBlurPass(b, a, w, h, radius, horizontal = false)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(a, 0, w, 0, 0, w, h)
        return out
    }

    private fun boxBlurPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val div = 2 * r + 1
        for (line in 0 until lines) {
            val base = if (horizontal) line * w else line
            val step = if (horizontal) 1 else w
            var sa = 0
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val c = src[base + i.coerceIn(0, len - 1) * step]
                sa += c ushr 24
                sr += (c shr 16) and 255
                sg += (c shr 8) and 255
                sb += c and 255
            }
            for (i in 0 until len) {
                dst[base + i * step] =
                    ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = src[base + (i + r + 1).coerceAtMost(len - 1) * step]
                val rem = src[base + (i - r).coerceAtLeast(0) * step]
                sa += (add ushr 24) - (rem ushr 24)
                sr += ((add shr 16) and 255) - ((rem shr 16) and 255)
                sg += ((add shr 8) and 255) - ((rem shr 8) and 255)
                sb += (add and 255) - (rem and 255)
            }
        }
    }

    /**
     * Prepares the blurred backdrop off the main thread. Order of preference: the user's own
     * image (path-confined, size-bounded loader), a built-in wallpaper, then the adaptive
     * gradient built from the phone's wallpaper colours.
     */
    private fun ensureBackdrop() {
        val path = wallpaperPath()
        val builtin = if (path.isEmpty()) BuiltinWallpapers.byId(builtinWallpaperId()) else null
        val palette = if (path.isEmpty() && builtin == null) wallpaperPalette() else null
        val key = when {
            path.isNotEmpty() -> "file:$path"
            builtin != null -> "builtin:${builtin.id}"
            else -> "aurora:${palette!!.joinToString(",")}"
        }
        if (key == backdropKey && backdropBlur != null) return
        backdropKey = key
        val token = ++backdropToken
        val appContext = applicationContext
        Thread {
            val decoded = if (path.isNotEmpty()) WallpaperLoader.decode(appContext, path) else null
            // Drawn at the screen's own size. Drawing small and stretching it is what made the
            // launcher look softer than the phone's own wallpaper.
            val screen = appContext.resources.displayMetrics
            val generated = if (decoded == null) {
                try {
                    if (builtin != null) {
                        BuiltinWallpapers.render(builtin, screen.widthPixels, screen.heightPixels)
                    } else {
                        BuiltinWallpapers.adaptive(
                            palette ?: wallpaperPalette(),
                            screen.widthPixels,
                            screen.heightPixels,
                        )
                    }
                } catch (_: OutOfMemoryError) {
                    if (builtin != null) BuiltinWallpapers.render(builtin, 540, 1170)
                    else BuiltinWallpapers.adaptive(palette ?: wallpaperPalette(), 360, 780)
                }
            } else {
                null
            }
            val source = decoded ?: generated!!
            val blurred = try {
                val small = shrinkTo(source, 160)
                blurBitmap(small.copy(Bitmap.Config.ARGB_8888, true), radius = 5, passes = 3)
            } catch (_: Throwable) {
                null
            }
            handler.post {
                if (token != backdropToken || isFinishing) return@post
                backdropBlur = blurred
                backdropAurora = generated
                applyBackdropToViews()
                invalidateFrosted()
            }
        }.start()
    }

    private fun applyBackdropToViews() {
        val path = wallpaperPath()
        homeBackdrop?.let { view ->
            if (path.isNotEmpty() && backdropAurora == null) {
                WallpaperLoader.loadInto(view, path)
            } else if (backdropAurora != null) {
                view.setImageBitmap(backdropAurora)
            }
        }
        bindBlurredBackdrop(drawerBackdrop)
        bindBlurredBackdrop(hiddenBackdrop)
    }

    private fun bindBlurredBackdrop(view: ImageView?) {
        view ?: return
        val blur = backdropBlur ?: return
        view.setImageBitmap(blur)
    }

    private fun applyGlassLive() {
        val fade = wallpaperFade()
        homeBackdrop?.alpha = fade
        drawerBackdrop?.alpha = fade
        hiddenBackdrop?.alpha = fade
        invalidateFrosted()
    }

    // ── View helpers ───────────────────────────────────────────────────────────

    private fun label(
        text: String,
        sizeSp: Float,
        color: Int = TEXT_PRIMARY,
        bold: Boolean = false,
    ): TextView = TextView(this).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(color)
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(dp(4).toFloat(), 0f, dp(1).toFloat(), Color.parseColor("#80000000"))
    }

    private fun lp(w: Int, h: Int, weight: Float? = null): LinearLayout.LayoutParams =
        if (weight == null) LinearLayout.LayoutParams(w, h) else LinearLayout.LayoutParams(w, h, weight)

    private fun roundedBackground(fill: Int, stroke: Int, radiusDp: Int): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }
    }

    private fun ovalBackground(fill: Int, stroke: Int = Color.TRANSPARENT): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }
    }

    private fun rippleForeground(cornerDp: Int = 16): Drawable {
        val mask = roundedBackground(Color.WHITE, Color.TRANSPARENT, cornerDp)
        return RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#30FFFFFF")),
            null,
            mask,
        )
    }

    private fun animationsEnabled(): Boolean {
        val scale = android.provider.Settings.Global.getFloat(
            contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        return scale > 0f
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun matchParent(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )

    // ── Drawer adapter ─────────────────────────────────────────────────────────

    private inner class DrawerAdapter(
        private val listLayout: Boolean,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var items: MutableList<DrawerItem.App> = mutableListOf()
        private var iconScale = prefs.getFloat(PREF_DRAWER_SCALE, 1f).coerceIn(0.8f, 1.2f)

        fun setItems(next: List<DrawerItem.App>) {
            items = next.toMutableList()
            notifyDataSetChanged()
        }

        fun currentItems(): List<DrawerItem.App> = items.toList()

        fun setIconScale(scale: Float) {
            iconScale = scale.coerceIn(0.8f, 1.2f)
            notifyDataSetChanged()
        }

        fun moveItem(from: Int, to: Int): Boolean {
            if (from !in items.indices || to !in items.indices) return false
            val item = items.removeAt(from)
            items.add(to, item)
            notifyItemMoved(from, to)
            return true
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (listLayout) createListHolder(parent) else createGridHolder(parent)
        }

        private fun createListHolder(parent: ViewGroup): RecyclerView.ViewHolder {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), 0, dp(6), 0)
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    dp(58),
                )
            }
            val icon = ImageView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(LIST_ICON_BASE_DP), dp(LIST_ICON_BASE_DP))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val name = label("", 15f, TEXT_PRIMARY).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val sub = label("", 12f, AMBER, bold = true).apply {
                maxLines = 1
                visibility = View.GONE
            }
            val nameColumn = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.leftMargin = dp(14) }
                addView(name)
                addView(sub)
            }
            val badge = TextView(parent.context).apply {
                text = "≡"
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(TEXT_DIM)
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            }
            row.addView(icon)
            row.addView(nameColumn)
            row.addView(badge)
            return AppHolder(row, icon, name, badge, sub)
        }

        private fun createGridHolder(parent: ViewGroup): RecyclerView.ViewHolder {
            val cell = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    dp(82),
                )
                setPadding(dp(4), dp(4), dp(4), dp(2))
            }
            val icon = ImageView(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(dp(42), dp(42)).also {
                    it.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val name = label("", SIZE_HOME_LABEL, TEXT_DIM).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ).also {
                    it.gravity = Gravity.BOTTOM
                    it.bottomMargin = dp(2)
                }
            }
            val badge = TextView(parent.context).apply {
                text = "≡"
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(TEXT_DIM)
                background = ovalBackground(Color.parseColor("#663D4658"))
                visibility = View.GONE
                layoutParams = FrameLayout.LayoutParams(dp(18), dp(18)).also {
                    it.gravity = Gravity.TOP or Gravity.END
                    it.topMargin = dp(1)
                    it.rightMargin = dp(3)
                }
            }
            val limit = label("", 9f, AMBER, bold = true).apply {
                maxLines = 1
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(1), dp(5), dp(1))
                background = roundedBackground(Color.parseColor("#CC000000"), Color.TRANSPARENT, 8)
                visibility = View.GONE
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ).also {
                    it.gravity = Gravity.TOP or Gravity.START
                    it.topMargin = dp(1)
                    it.leftMargin = dp(1)
                }
            }
            cell.addView(icon)
            cell.addView(name)
            cell.addView(badge)
            cell.addView(limit)
            return AppHolder(cell, icon, name, badge, limit)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items.getOrNull(position) ?: return
            holder as AppHolder
            val blocked = getBlockedPackages().contains(item.packageName)
            if (listLayout) {
                val size = (LIST_ICON_BASE_DP * iconScale).toInt()
                holder.icon.layoutParams = (holder.icon.layoutParams as LinearLayout.LayoutParams).also {
                    it.width = dp(size)
                    it.height = dp(size)
                }
                holder.itemView.layoutParams = holder.itemView.layoutParams.also {
                    it.height = dp((size + 18).coerceAtLeast(LIST_ROW_MIN_DP))
                }
            } else {
                val size = (42f * iconScale).toInt()
                holder.icon.layoutParams = (holder.icon.layoutParams as FrameLayout.LayoutParams).also {
                    it.width = dp(size)
                    it.height = dp(size)
                }
                holder.itemView.layoutParams = holder.itemView.layoutParams.also {
                    it.height = dp((size + 30).coerceAtLeast(78))
                }
            }
            holder.icon.setImageDrawable(getRoundIcon(item.packageName))
            holder.icon.alpha = if (blocked) 0.45f else 1f
            holder.label.text = item.label
            holder.label.setTextColor(if (blocked) TEXT_MUTED else if (listLayout) TEXT_PRIMARY else TEXT_DIM)
            holder.badge.visibility = if (isEditMode) View.VISIBLE else View.GONE
            val limitInfo = drawerLimitInfo[item.packageName]
            if (limitInfo != null && !isEditMode) {
                holder.limit.text = limitInfo.first
                holder.limit.setTextColor(limitColor(limitInfo.second))
                holder.limit.visibility = View.VISIBLE
            } else {
                holder.limit.visibility = View.GONE
            }
            holder.itemView.contentDescription =
                if (blocked) "${item.label}, blocked" else item.label
            val selected = isEditMode && focusedDrawerPackage == item.packageName
            holder.itemView.background =
                if (selected) roundedBackground(Color.parseColor("#2AFFFFFF"), Color.parseColor("#40FFFFFF"), 14)
                else null
            holder.itemView.setOnClickListener {
                if (isEditMode) {
                    focusedDrawerPackage = item.packageName
                    updateCustomizeSelection()
                    notifyDataSetChanged()
                } else {
                    closeDrawer()
                    if (blocked) launchBlockOverlay(item.packageName) else launchApp(item.packageName)
                }
            }
            holder.itemView.setOnLongClickListener {
                focusedDrawerPackage = item.packageName
                if (isEditMode) {
                    updateCustomizeSelection()
                    false // let the drag helper take the long press
                } else {
                    // On the grid home, a long press that then moves carries the app out onto it;
                    // letting go without moving still opens the menu (see endDrawerPending).
                    if (!beginDrawerDrag(item.packageName, item.label)) {
                        showAppActions(item.packageName, item.label)
                    }
                    true
                }
            }
        }
    }

    private class AppHolder(
        view: View,
        val icon: ImageView,
        val label: TextView,
        val badge: TextView,
        val limit: TextView,
    ) : RecyclerView.ViewHolder(view)

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PREF_LAUNCHER_THEME ||
            key == PREF_LAUNCHER_WALLPAPER ||
            key == PREF_WALLPAPER_BUILTIN ||
            key == PREF_GLASS_LEVEL ||
            key == PREF_DRAWER_LAYOUT ||
            key == PREF_DRAWER_HIDDEN ||
            key == PREF_LAUNCHER_HIDDEN ||
            key == PREF_FOCUS_TOOLS ||
            key == PREF_SA_ACTIVE ||
            key == PREF_SA_PKGS ||
            key == PREF_SA_UNTIL ||
            key == PREF_ALWAYS_BLOCK ||
            key == PREF_ALWAYS_BLOCK_PKGS ||
            key == "focus_active" ||
            key == "task_name" ||
            key == "task_end_ms" ||
            key == "task_start_ms" ||
            key == "daily_allowance_config" ||
            key == AllowanceLedger.PREF_DAILY_ALLOWANCE_USED
        ) {
            runOnUiThread {
                if (!::rootFrame.isInitialized) return@runOnUiThread
                if (key == PREF_LAUNCHER_THEME || key == PREF_LAUNCHER_WALLPAPER ||
                    key == PREF_WALLPAPER_BUILTIN || key == PREF_GLASS_LEVEL
                ) {
                    if (!suppressPrefRebuild && renderedSignature != layoutSignature()) buildHomeLayout()
                } else if (key == PREF_DRAWER_LAYOUT) {
                    if (isDrawerOpen) applyDrawerLayout()
                } else {
                    invalidateSnapshot()
                    refreshHomeContent()
                    refreshDockActions()
                    if (isDrawerOpen) {
                        if (!isEditMode) {
                            drawerAllApps = loadDrawerApps()
                            reloadDrawerLimits()
                            drawerBlockedCount = getBlockedPackages().size
                            renderDrawerChips()
                            refreshDrawerItems()
                        }
                    }
                    if (hiddenOverlay != null) renderHiddenList()
                }
            }
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (intent?.action == ACTION_OPEN_DAY_RATING) {
            openDayRatingInMainActivity()
            finish()
            return
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        // With three-button navigation Android otherwise paints its own translucent strip
        // (white, for a light theme) behind the buttons. Turn that off so the wallpaper runs
        // all the way to the bottom edge, and use light icons because the launcher is dark.
        window.navigationBarDividerColor = Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightNavigationBars = false
            isAppearanceLightStatusBars = false
        }
        rootFrame = FrameLayout(this)
        rootFrame.setBackgroundColor(Color.BLACK)
        setContentView(rootFrame)
        ViewCompat.setOnApplyWindowInsetsListener(rootFrame) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars(),
            )
            barTop = bars.top
            barBottom = bars.bottom
            homeContent?.setPadding(dp(20), barTop + dp(14), dp(20), barBottom + dp(14))
            insets
        }
        buildHomeLayout()
        startClock()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_OPEN_DAY_RATING) {
            openDayRatingInMainActivity()
        } else if (intent.hasCategory(Intent.CATEGORY_HOME) && ::rootFrame.isInitialized) {
            // Pressing Home while an overlay is open takes you back to the plain home screen.
            closeAllOverlays()
            homeScroll?.scrollTo(0, 0)
        }
    }

    private fun openDayRatingInMainActivity() {
        startActivity(
            Intent(this, com.tbtechs.focusflow.MainActivity::class.java).apply {
                action = ACTION_OPEN_DAY_RATING
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        iconCache.clear()
        invalidateSnapshot()
        startTaskCollection()
        startWidgetListening()
        if (renderedSignature != layoutSignature()) {
            buildHomeLayout()
        } else {
            refreshHomeContent()
            refreshDockActions()
            // Something may have been pinned while the launcher was away (Chrome's "Add to
            // Home screen"), so redraw the grid if the saved list changed.
            if (prefs.getString(PREF_HOME_PINNED_ITEMS, null) != pinnedRenderedRaw ||
                prefs.getString(PREF_HOME_WIDGETS_ITEMS, null) != widgetsRenderedRaw
            ) {
                rebuildHomeSections()
            }
        }
        if (isDrawerOpen && !isEditMode) {
            drawerAllApps = loadDrawerApps()
            reloadDrawerLimits()
            refreshDrawerItems()
        }
    }

    override fun onPause() {
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        }
        stopTaskCollection()
        stopWidgetListening()
        abortHomeDrag()
        abortGridDrag()
        pendingDrawerDrag = null
        exitGridResize()
        dismissUndoBar(runExpire = true)
        super.onPause()
    }

    override fun onDestroy() {
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        }
        backdropToken++
        stopTaskCollection()
        clockRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            sheetOverlay != null -> dismissSheet()
            hiddenOverlay != null -> closeHiddenScreen()
            homeArrangeActive -> exitHomeArrange()
            isDrawerOpen -> if (isEditMode) cancelEditMode() else closeDrawer()
            else -> Unit
        }
    }

    private fun closeAllOverlays() {
        dismissSheet(animate = false)
        closeHiddenScreen(animate = false)
        if (isDrawerOpen) closeDrawer(animate = false)
    }

    // ── Home screen ────────────────────────────────────────────────────────────

    private fun buildHomeLayout() {
        glassLevel = readGlassLevel()
        renderedSignature = layoutSignature()
        frosted.clear()
        rootFrame.removeAllViews()
        drawerOverlay = null
        hiddenOverlay = null
        sheetOverlay = null
        isDrawerOpen = false
        isEditMode = false
        homeArrangeActive = false
        arrangeBar = null

        val backdrop = ImageView(this).apply {
            layoutParams = matchParent()
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = wallpaperFade()
        }
        homeBackdrop = backdrop
        rootFrame.addView(backdrop)

        // No darkening layer over the wallpaper: text stays readable through its own shadow.

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = matchParent()
            setPadding(dp(20), barTop + dp(14), dp(20), barBottom + dp(14))
        }
        homeContent = content

        // Long-press a widget and it lifts: drag it where you want it, no menu in between.
        // Long-press empty space or the dock and the arrange bar (Style & options) opens instead.
        val customize = View.OnLongClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val gridChild = gridChildOf(v)
            if (gridChild != null) {
                // On the grid, an item under the finger lifts; empty space opens the menu.
                if (!beginGridDrag(gridChild)) enterHomeArrange()
            } else if (!beginHomeDrag(lastTouchRawY)) {
                enterHomeArrange()
            }
            true
        }
        homeLongPress = customize
        content.setOnLongClickListener(customize)
        gridResizeId = null
        gridResizeFrame = null
        gridResizeDrag = null
        if (gridHomeOn()) {
            homeScroll = null
            homeColumn = null
            val grid = HomeGridLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                )
                insetPx = dp(4)
                setOnLongClickListener(customize)
            }
            homeGrid = grid
            populateHomeGrid(grid)
            content.addView(grid)
        } else {
            homeGrid = null
            val scroll = ScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                )
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                clipChildren = false
                clipToPadding = false
                isFillViewport = true
            }
            homeScroll = scroll
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            homeColumn = column
            column.setOnLongClickListener(customize)
            populateHomeColumn(column)
            scroll.addView(column)
            scroll.setOnScrollChangeListener { _, _, _, _, _ -> invalidateFrosted() }
            content.addView(scroll)
        }
        content.addView(buildDock())
        rootFrame.addView(content)

        applyBackdropToViews()
        ensureBackdrop()
        refreshHomeContent()
        rootFrame.requestApplyInsets()
    }

    // Per-widget style choices. Every one has a safe default and an "off".
    private fun homeOption(key: String, default: String, allowed: Set<String>): String {
        val value = prefs.getString(key, null)
        return if (value != null && value in allowed) value else default
    }

    private fun clockStyleValue() =
        homeOption(PREF_HOME_CLOCK, "light", setOf("light", "bold", "stacked", "compact", "off"))

    private fun statusStyleValue() = homeOption(PREF_HOME_STATUS, "pill", setOf("pill", "text", "off"))
    private fun taskStyleValue() = homeOption(PREF_HOME_TASK, "card", setOf("card", "compact", "off"))
    private fun limitsStyleValue() =
        homeOption(PREF_HOME_LIMITS, "bars", setOf("bars", "rings", "list", "off"))

    private fun searchStyleValue() = homeOption(PREF_HOME_SEARCH, "bar", setOf("bar", "off"))

    private fun pinnedStyleValue() = homeOption(PREF_HOME_PINNED, "grid", setOf("grid", "off"))

    private fun widgetsStyleValue() = homeOption(PREF_HOME_WIDGETS, "show", setOf("show", "off"))
    private fun allAppsButtonOn() = homeOption(PREF_HOME_ALL_APPS, "on", setOf("on", "off")) == "on"

    private fun homeAlignGravity(): Int =
        if (clockStyleValue() == "light" || clockStyleValue() == "off") Gravity.CENTER_HORIZONTAL
        else Gravity.START

    private fun populateHomeColumn(column: LinearLayout) {
        column.removeAllViews()
        clockView = null
        dateView = null
        statusText = null
        statusDot = null
        focusCard = null
        focusHeaderView = null
        focusTitleView = null
        focusSubtitleView = null
        focusProgressTrack = null
        focusProgressFill = null
        allowanceContainer = null
        // The views a drag was holding are about to be replaced.
        abortHomeDrag()
        homeBlocks = emptyList()
        if (homeArrangeActive) {
            populateArrangeColumn(column)
            return
        }
        if (clockStyleValue() == "off") {
            column.addView(View(this), LinearLayout.LayoutParams(1, dp(28)))
        }
        // In the order the person arranged them (long-press a widget and drag it to change it).
        val blocks = ArrayList<HomeBlock>()
        homeOrder().forEach { id ->
            val block: View? = when (id) {
                "clock" -> if (clockStyleValue() != "off") buildClockWidget() else null
                "status" -> if (statusStyleValue() != "off") buildStatusPill() else null
                "task" -> if (taskStyleValue() != "off") buildFocusSessionCard() else null
                "limits" -> if (limitsStyleValue() != "off") buildTodaysLimits() else null
                "pinned" -> if (pinnedStyleValue() != "off") buildPinnedWidget() else null
                "widgets" -> if (widgetsStyleValue() != "off") buildWidgetsBlock() else null
                else -> null
            }
            if (block != null) {
                column.addView(block)
                blocks += HomeBlock(id, block)
            }
        }
        homeBlocks = blocks
    }

    private fun gridHomeOn(): Boolean = prefs.getString(PREF_HOME_LAYOUT, "list") == "grid"

    /** Roughly how much height the grid gets, in dp: the screen less the bars, padding and dock. */
    private fun gridHeightDpEstimate(): Float =
        resources.displayMetrics.heightPixels / resources.displayMetrics.density - 240f

    /**
     * How many rows the grid has: about one per 84 dp of height, never fewer than six. Based only on
     * the screen size, so it does not change from one launch to the next.
     */
    private fun gridRowsEstimate(): Int =
        (gridHeightDpEstimate() / 84f).toInt().coerceIn(HomeGrid.DEFAULT_ROWS, 9)

    private fun focusCardShowing(): Boolean {
        val snapshot = currentSnapshot(System.currentTimeMillis()) ?: return false
        return snapshot.mode == WidgetMode.ACTIVE || (snapshot.mode == WidgetMode.NEXT && snapshot.startsToday)
    }

    private fun gridTaskWanted(): Boolean = taskStyleValue() != "off" && focusCardShowing()

    private fun gridLimitsWanted(): Boolean = limitsStyleValue() != "off" && loadAllowanceCardData().isNotEmpty()

    /** Which cards had something to show when the grid was last filled; a change means refilling it. */
    private var gridCardSignature = ""

    /** Items the grid had no room for, as last reported, so the message shows once rather than every redraw. */
    private var gridUnplacedShown: Set<String> = emptySet()

    /**
     * Fills the grid home screen. What is on it still comes from the same settings as the list home
     * (which cards are on, the pinned apps, the hosted widgets); only the positions are the grid's.
     * Anything new gets the first free spot and anything with no room is reported, never deleted.
     */
    private fun populateHomeGrid(grid: HomeGridLayout) {
        grid.removeAllViews()
        clockView = null
        dateView = null
        statusText = null
        statusDot = null
        focusCard = null
        focusHeaderView = null
        focusTitleView = null
        focusSubtitleView = null
        focusProgressTrack = null
        focusProgressFill = null
        allowanceContainer = null
        abortHomeDrag()
        abortGridDrag()
        gridResizeDrag = null
        gridResizeFrame = null // removeAllViews above took it off the grid
        homeBlocks = emptyList()

        val columns = HomeGrid.DEFAULT_COLUMNS
        val rows = gridRowsEstimate()
        grid.columns = columns
        grid.rows = rows

        // The task and limits cards only take cells while they have something to show; the rest
        // of the time they keep their saved place without using any room (a "dormant" card).
        val taskNow = gridTaskWanted()
        val limitsNow = gridLimitsWanted()
        gridCardSignature = "$taskNow|$limitsNow"
        val cards = HashSet<String>()
        val dormant = HashSet<String>()
        if (clockStyleValue() != "off") cards += "clock"
        if (statusStyleValue() != "off") cards += "status"
        if (taskStyleValue() != "off") {
            if (taskNow) cards += "task" else dormant += HomeGrid.id(HomeGrid.Kind.CARD, "task")
        }
        if (limitsStyleValue() != "off") {
            if (limitsNow) cards += "limits" else dormant += HomeGrid.id(HomeGrid.Kind.CARD, "limits")
        }

        val pinnedAll = pinnedEntries()
        val pinned = if (pinnedStyleValue() == "off") emptyList() else pinnedAll.filter { pinnedStillThere(it) }
        // Apps that were uninstalled quietly drop off, as on the list home.
        if (pinnedStyleValue() != "off" && pinned.size != pinnedAll.size) savePinned(pinned)
        pinnedRenderedRaw = prefs.getString(PREF_HOME_PINNED_ITEMS, null)

        val density = resources.displayMetrics.density
        // A guess at the cell height, only used to pick a widget's first size on the grid.
        val cellDpGuess = (gridHeightDpEstimate() / rows).toInt().coerceIn(60, 140)
        val widgets = liveWidgetsForGrid()
        val wanted = HomeGrid.wantedInListOrder(
            order = homeOrder(),
            cards = cards,
            pinnedKeys = pinned.map { it.key },
            widgets = widgets.map { (entry, info) ->
                val needsDp = (info.minHeight / density).toInt().coerceAtMost(320)
                HomeGrid.WidgetWant(entry.appWidgetId, HomeWidgetEntries.heightDp(entry.size, needsDp))
            },
            columns = columns,
            cellHeightDp = cellDpGuess,
        )
        val savedRaw = prefs.getString(HomeGrid.PREF_KEY, null)
        val layout = HomeGrid.reconcile(HomeGrid.parse(savedRaw), wanted, columns, rows, dormant)
        val raw = HomeGrid.serialize(layout.items + layout.parked)
        if (raw != (savedRaw ?: "")) prefs.edit().putString(HomeGrid.PREF_KEY, raw).apply()

        val unplaced = layout.unplaced.map { it.id }.toSet()
        if (unplaced.isNotEmpty() && unplaced != gridUnplacedShown) {
            Toast.makeText(
                this,
                "${unplaced.size} item${if (unplaced.size == 1) "" else "s"} don't fit on the grid yet. " +
                    "They are still on your List layout.",
                Toast.LENGTH_LONG,
            ).show()
        }
        gridUnplacedShown = unplaced
        gridItems = layout.items

        val pinnedByKey = pinned.associateBy { it.key }
        val widgetById = widgets.associateBy { it.first.appWidgetId }
        for (item in layout.items) {
            val view: View = when (item.kind) {
                HomeGrid.Kind.CARD -> {
                    val card = when (item.key) {
                        "clock" -> buildClockWidget()
                        "status" -> buildStatusPill()
                        "task" -> buildFocusSessionCard()
                        "limits" -> buildTodaysLimits()
                        else -> null
                    } ?: continue
                    gridCardCell(card)
                }
                HomeGrid.Kind.APP, HomeGrid.Kind.SHORTCUT -> {
                    val entry = pinnedByKey[item.key] ?: continue
                    (pinnedCell(entry, columns) as LinearLayout).apply { gravity = Gravity.CENTER }
                }
                HomeGrid.Kind.WIDGET -> {
                    val (entry, info) = widgetById[item.key.toIntOrNull()] ?: continue
                    val host = widgetViews.getOrPut(entry.appWidgetId) {
                        widgetHost.createView(this, entry.appWidgetId, info)
                    }
                    host
                }
            }
            (view.parent as? ViewGroup)?.removeView(view)
            view.layoutParams = HomeGridLayout.Params(item.col, item.row, item.spanX, item.spanY)
            // Long-press lifts it; releasing without moving opens its menu (see endGridDrag).
            view.tag = item.id
            view.setOnLongClickListener(homeLongPress)
            grid.addView(view)
        }
        // A rebuild (after a resize, say) keeps the item that was being resized in resize mode.
        gridResizeId?.let { id ->
            if (layout.items.any { it.id == id }) enterGridResize(id) else gridResizeId = null
        }
        grid.post {
            // Each widget needs to be told how big its cells turned out, in dp.
            for (i in 0 until grid.childCount) {
                val child = grid.getChildAt(i)
                if (child is AppWidgetHostView && child.width > 0) {
                    val w = (child.width / density).toInt()
                    val h = (child.height / density).toInt()
                    child.updateAppWidgetSize(null, w, h, w, h)
                }
            }
            invalidateFrosted()
        }
    }

    /**
     * A card keeps its own natural height at the top of the cells it covers, instead of stretching
     * to fill them: a status pill should stay a pill however many cells it was given.
     */
    private fun gridCardCell(card: View): View {
        val old = card.layoutParams as? LinearLayout.LayoutParams
        val width = old?.width ?: ViewGroup.LayoutParams.MATCH_PARENT
        val height = old?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
        val gravity = if (old != null && old.gravity != -1) old.gravity else Gravity.START
        (card.parent as? ViewGroup)?.removeView(card)
        return FrameLayout(this).apply {
            addView(card, FrameLayout.LayoutParams(width, height, Gravity.TOP or gravity))
        }
    }

    /** The hosted widgets that still exist. Ones Android dropped (their app was removed) are cleaned up. */
    private fun liveWidgetsForGrid(): List<Pair<HomeWidgetEntries.Entry, AppWidgetProviderInfo>> {
        widgetsRenderedRaw = prefs.getString(PREF_HOME_WIDGETS_ITEMS, null)
        if (widgetsStyleValue() == "off") return emptyList()
        val entries = widgetEntries()
        val live = ArrayList<Pair<HomeWidgetEntries.Entry, AppWidgetProviderInfo>>()
        val gone = ArrayList<Int>()
        for (entry in entries) {
            val info = try {
                appWidgetManager.getAppWidgetInfo(entry.appWidgetId)
            } catch (_: Exception) {
                null
            }
            if (info == null) gone += entry.appWidgetId else live += entry to info
        }
        if (gone.isNotEmpty()) {
            gone.forEach {
                try { widgetHost.deleteAppWidgetId(it) } catch (_: Exception) {}
                widgetViews.remove(it)
            }
            saveWidgets(entries.filterNot { it.appWidgetId in gone })
        }
        return live
    }

    /** The saved widget order, with anything missing or unknown put back in its default place. */
    private fun homeOrder(): List<String> {
        val saved = prefs.getString(PREF_HOME_ORDER, null)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it in HOME_WIDGETS }
            ?.distinct()
            .orEmpty()
        return saved + HOME_WIDGETS.filter { it !in saved }
    }

    private fun moveHomeWidget(id: String, delta: Int) {
        val order = homeOrder().toMutableList()
        val from = order.indexOf(id)
        val to = from + delta
        if (from < 0 || to !in order.indices) return
        order.removeAt(from)
        order.add(to, id)
        prefs.edit().putString(PREF_HOME_ORDER, order.joinToString(",")).apply()
        rebuildHomeSections()
    }

    // ── App widgets ────────────────────────────────────────────────────────────
    //
    // Real Android widgets (weather, calendar, music...) hosted on the home screen. Android draws
    // each one; the launcher only gives it room. They are listened to only while the launcher is
    // on screen, so a hidden launcher costs no widget updates. A widget can open any app, but the
    // block screen and allowances still apply the moment that app reaches the foreground.

    private val appWidgetManager: AppWidgetManager by lazy { AppWidgetManager.getInstance(this) }
    private val widgetHost by lazy { HomeWidgetHost.create(this) }
    private val widgetViews = HashMap<Int, AppWidgetHostView>()
    private var pendingWidgetId = -1
    private var widgetsRenderedRaw: String? = null
    private var widgetsListening = false

    private fun widgetEntries(): List<HomeWidgetEntries.Entry> =
        HomeWidgetEntries.parse(prefs.getString(PREF_HOME_WIDGETS_ITEMS, null))

    private fun saveWidgets(items: List<HomeWidgetEntries.Entry>) {
        prefs.edit().putString(PREF_HOME_WIDGETS_ITEMS, HomeWidgetEntries.serialize(items)).apply()
    }

    private fun startWidgetListening() {
        if (widgetsListening || widgetEntries().isEmpty()) return
        try {
            widgetHost.startListening()
            widgetsListening = true
        } catch (_: Exception) {
        }
    }

    private fun stopWidgetListening() {
        if (!widgetsListening) return
        try {
            widgetHost.stopListening()
        } catch (_: Exception) {
        }
        widgetsListening = false
    }

    private fun widgetLabel(entry: HomeWidgetEntries.Entry): String = try {
        appWidgetManager.getAppWidgetInfo(entry.appWidgetId)?.loadLabel(packageManager)
    } catch (_: Exception) {
        null
    } ?: entry.provider.substringAfterLast('/').trimStart('.')

    /** Null when no widget has been added, so the block takes up no room. */
    private fun buildWidgetsBlock(): View? {
        widgetsRenderedRaw = prefs.getString(PREF_HOME_WIDGETS_ITEMS, null)
        val entries = widgetEntries()
        if (entries.isEmpty()) return null
        val density = resources.displayMetrics.density
        val widthDp = (resources.displayMetrics.widthPixels / density).toInt() - 40
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(12) }
        }
        val gone = ArrayList<Int>()
        entries.forEach { entry ->
            val info = try {
                appWidgetManager.getAppWidgetInfo(entry.appWidgetId)
            } catch (_: Exception) {
                null
            }
            if (info == null) {
                // Its app was uninstalled or Android dropped it.
                gone.add(entry.appWidgetId)
                return@forEach
            }
            val view = widgetViews.getOrPut(entry.appWidgetId) {
                widgetHost.createView(this, entry.appWidgetId, info)
            }
            (view.parent as? ViewGroup)?.removeView(view)
            val needsDp = (info.minHeight / density).toInt().coerceAtMost(320)
            val heightDp = HomeWidgetEntries.heightDp(entry.size, needsDp)
            view.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(heightDp),
            ).also { it.bottomMargin = dp(10) }
            view.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
            view.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showWidgetActions(entry)
                true
            }
            block.addView(view)
        }
        if (gone.isNotEmpty()) {
            gone.forEach {
                try { widgetHost.deleteAppWidgetId(it) } catch (_: Exception) {}
                widgetViews.remove(it)
            }
            saveWidgets(entries.filterNot { it.appWidgetId in gone })
        }
        return if (block.childCount == 0) null else block
    }

    private fun showWidgetPicker() {
        if (!::rootFrame.isInitialized) return
        val providers = try {
            appWidgetManager.installedProviders
        } catch (_: Exception) {
            emptyList<AppWidgetProviderInfo>()
        }.map { info ->
            val widgetName = try { info.loadLabel(packageManager) } catch (_: Exception) { null }
            val appName = try {
                packageManager.getApplicationInfo(info.provider.packageName, 0)
                    .loadLabel(packageManager).toString()
            } catch (_: Exception) {
                info.provider.packageName
            }
            Triple(info, appName, widgetName ?: info.provider.shortClassName)
        }.sortedWith(compareBy({ it.second.lowercase() }, { it.third.lowercase() }))
        showSheet(scrimColor = Color.parseColor("#33000000")) { panel ->
            panel.addView(label("Add a widget", 20f, TEXT_PRIMARY, bold = true))
            panel.addView(label(
                "These are on your phone already. Android asks once to let FocusFlow show a widget.",
                12f,
                TEXT_MUTED,
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = dp(2); it.bottomMargin = dp(6) }
            })
            val scroller = ScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (resources.displayMetrics.heightPixels * 0.55f).toInt(),
                )
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            scroller.addView(body)
            panel.addView(scroller)
            if (providers.isEmpty()) {
                body.addView(label("No widgets found on this phone.", 14f, TEXT_DIM))
            }
            providers.forEach { (info, appName, widgetName) ->
                body.addView(sheetRow(widgetName, appName) {
                    dismissSheet()
                    startAddingWidget(info)
                })
            }
        }
    }

    private fun startAddingWidget(info: AppWidgetProviderInfo) {
        if (widgetEntries().size >= HomeWidgetEntries.MAX_WIDGETS) {
            Toast.makeText(this, "Home is full. Remove a widget first.", Toast.LENGTH_LONG).show()
            return
        }
        val id = try {
            widgetHost.allocateAppWidgetId()
        } catch (_: Exception) {
            Toast.makeText(this, "Couldn't add that widget.", Toast.LENGTH_SHORT).show()
            return
        }
        val bound = try {
            appWidgetManager.bindAppWidgetIdIfAllowed(id, info.provider)
        } catch (_: Exception) {
            false
        }
        if (bound) {
            onWidgetBound(id, info)
            return
        }
        // Android has not yet let FocusFlow show this widget: it asks the person once.
        pendingWidgetId = id
        val ask = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
        try {
            startActivityForResult(ask, REQ_BIND_WIDGET)
        } catch (_: Exception) {
            pendingWidgetId = -1
            try { widgetHost.deleteAppWidgetId(id) } catch (_: Exception) {}
            Toast.makeText(this, "Couldn't add that widget.", Toast.LENGTH_SHORT).show()
        }
    }

    /** Some widgets have a setup screen of their own; open it before the widget is placed. */
    private fun onWidgetBound(id: Int, info: AppWidgetProviderInfo) {
        if (info.configure == null) {
            finishAddingWidget(id)
            return
        }
        pendingWidgetId = id
        try {
            widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE_WIDGET, null)
        } catch (_: Exception) {
            pendingWidgetId = -1
            finishAddingWidget(id)
        }
    }

    private fun finishAddingWidget(id: Int) {
        val info = appWidgetManager.getAppWidgetInfo(id)
        if (info == null) {
            try { widgetHost.deleteAppWidgetId(id) } catch (_: Exception) {}
            return
        }
        val outcome = HomeWidgetEntries.add(
            widgetEntries(),
            HomeWidgetEntries.Entry(id, info.provider.flattenToString()),
        )
        if (outcome.status != HomeWidgetEntries.Status.ADDED) {
            try { widgetHost.deleteAppWidgetId(id) } catch (_: Exception) {}
            Toast.makeText(this, "Home is full. Remove a widget first.", Toast.LENGTH_LONG).show()
            return
        }
        saveWidgets(outcome.items)
        // A hidden "App widgets" block would make the new widget vanish: turn it back on.
        if (widgetsStyleValue() == "off") prefs.edit().remove(PREF_HOME_WIDGETS).apply()
        startWidgetListening()
        rebuildHomeSections()
        Toast.makeText(this, "Widget added", Toast.LENGTH_SHORT).show()
    }

    private fun removeWidget(entry: HomeWidgetEntries.Entry) {
        try { widgetHost.deleteAppWidgetId(entry.appWidgetId) } catch (_: Exception) {}
        widgetViews.remove(entry.appWidgetId)
        val remaining = HomeWidgetEntries.remove(widgetEntries(), entry.appWidgetId)
        saveWidgets(remaining)
        // With no widgets left there is nothing to listen for.
        if (remaining.isEmpty()) stopWidgetListening()
        rebuildHomeSections()
    }

    private fun showWidgetActions(entry: HomeWidgetEntries.Entry) {
        showSheet { panel ->
            panel.addView(label(widgetLabel(entry), 18f, TEXT_PRIMARY, bold = true))
            panel.addView(label("Height", 13f, TEXT_DIM).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = dp(10) }
            })
            val sizes = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = dp(6); it.bottomMargin = dp(6) }
            }
            HomeWidgetEntries.Size.values().forEach { size ->
                sizes.addView(chipView(size.label, size == entry.size) {
                    saveWidgets(HomeWidgetEntries.setSize(widgetEntries(), entry.appWidgetId, size))
                    dismissSheet()
                    rebuildHomeSections()
                })
            }
            panel.addView(sizes)
            panel.addView(sheetRow("Remove widget") {
                dismissSheet()
                removeWidget(entry)
            })
            panel.addView(sheetRow("Edit home", "Widgets, wallpaper, style", TEXT_DIM) {
                dismissSheet()
                enterHomeArrange()
            })
        }
    }

    private fun showManageWidgetsSheet() {
        showSheet { panel ->
            panel.addView(label("Widgets on your home", 18f, TEXT_PRIMARY, bold = true))
            widgetEntries().forEach { entry ->
                panel.addView(sheetRow(widgetLabel(entry), "Height: ${entry.size.label}") {
                    dismissSheet()
                    showWidgetActions(entry)
                })
            }
            panel.addView(sheetRow("Done", tint = TEXT_DIM) { dismissSheet() })
        }
    }

    // ── Pinned apps ────────────────────────────────────────────────────────────
    //
    // A grid of icons on the home screen: apps added from the drawer, and shortcuts that other
    // apps hand over through "Add to Home screen" (see PinShortcutActivity). It is empty and
    // invisible until something is pinned, and it is a widget like the others, so it can be moved
    // or hidden in arrange mode.

    private var pinnedRenderedRaw: String? = null
    private val shortcutIconCache = HashMap<String, Drawable>()

    private fun pinnedColumns(): Int =
        HomePinnedItems.columnsFromPref(prefs.getString(PREF_HOME_PINNED_SIZE, null))

    private fun pinnedEntries(): List<HomePinnedItems.Entry> =
        HomePinnedItems.parse(prefs.getString(PREF_HOME_PINNED_ITEMS, null))

    private fun savePinned(items: List<HomePinnedItems.Entry>) {
        prefs.edit().putString(PREF_HOME_PINNED_ITEMS, HomePinnedItems.serialize(items)).apply()
    }

    private fun pinApp(pkg: String, name: String) {
        val outcome = HomePinnedItems.add(
            pinnedEntries(),
            HomePinnedItems.Entry(HomePinnedItems.Kind.APP, pkg, "", name),
            HomePinnedItems.capacity(pinnedColumns()),
        )
        when (outcome.status) {
            HomePinnedItems.Status.ADDED -> {
                savePinned(outcome.items)
                // A hidden "Pinned apps" widget would make the new icon vanish: turn it back on.
                if (pinnedStyleValue() == "off") prefs.edit().remove(PREF_HOME_PINNED).apply()
                rebuildHomeSections()
                Toast.makeText(this, "$name added to home", Toast.LENGTH_SHORT).show()
            }
            HomePinnedItems.Status.DUPLICATE ->
                Toast.makeText(this, "$name is already on your home screen", Toast.LENGTH_SHORT).show()
            HomePinnedItems.Status.FULL ->
                Toast.makeText(this, "Home is full. Remove one first.", Toast.LENGTH_LONG).show()
        }
    }

    private fun unpinApp(pkg: String) {
        savePinned(HomePinnedItems.removeApp(pinnedEntries(), pkg))
        rebuildHomeSections()
    }

    private fun unpin(entry: HomePinnedItems.Entry) {
        savePinned(HomePinnedItems.remove(pinnedEntries(), entry.key))
        rebuildHomeSections()
    }

    /** Whether an app entry can still be opened. Shortcuts are checked when they are tapped. */
    private fun pinnedStillThere(entry: HomePinnedItems.Entry): Boolean =
        entry.kind == HomePinnedItems.Kind.SHORTCUT ||
            packageManager.getLaunchIntentForPackage(entry.packageName) != null

    /** Null when nothing is pinned, so an empty grid takes up no room at all. */
    private fun buildPinnedWidget(): View? {
        pinnedRenderedRaw = prefs.getString(PREF_HOME_PINNED_ITEMS, null)
        val all = pinnedEntries()
        val entries = all.filter { pinnedStillThere(it) }
        // Apps that were uninstalled quietly drop off.
        if (entries.size != all.size) savePinned(entries)
        if (entries.isEmpty()) return null
        val columns = pinnedColumns()
        // After choosing a smaller grid the extra icons are kept, just not shown.
        val shown = entries.take(HomePinnedItems.capacity(columns))
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(12); it.bottomMargin = dp(6) }
        }
        shown.chunked(columns).forEach { rowItems ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.bottomMargin = dp(10) }
            }
            rowItems.forEach { row.addView(pinnedCell(it, columns)) }
            // Empty cells keep the columns lined up on a short last row.
            repeat(columns - rowItems.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            }
            grid.addView(row)
        }
        if (shown.size < entries.size) {
            grid.addView(label("${entries.size - shown.size} more not shown. Choose a bigger grid in Customize home.", 11f, TEXT_DIM))
        }
        return grid
    }

    private fun pinnedCell(entry: HomePinnedItems.Entry, columns: Int): View {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(2), dp(4), dp(2), dp(4))
            isClickable = true
            background = rippleForeground(16)
            contentDescription = entry.label
        }
        // Fewer columns mean wider cells, so the icons grow; with five they shrink to still fit.
        val iconSize = dp(when (columns) { 3 -> 60; 5 -> 44; else -> 52 })
        if (entry.kind == HomePinnedItems.Kind.APP) {
            cell.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                setImageDrawable(getRoundIcon(entry.packageName))
            })
        } else {
            // A shortcut's own picture is fetched from the system, so a plain disc with the
            // first letter shows first and the real icon replaces it when it arrives.
            val holder = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                background = roundedBackground(Color.parseColor("#33FFFFFF"), Color.TRANSPARENT, 26)
            }
            val letter = label(entry.label.take(1).uppercase().ifBlank { "•" }, 20f, TEXT_PRIMARY, bold = true).apply {
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(iconSize, iconSize)
            }
            val picture = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            holder.addView(letter)
            holder.addView(picture)
            cell.addView(holder)
            val cached = shortcutIconCache[entry.key]
            if (cached != null) {
                picture.setImageDrawable(cached)
                letter.visibility = View.GONE
            } else {
                Thread {
                    val drawable = loadShortcutIcon(entry)
                    if (drawable != null) {
                        handler.post {
                            if (isFinishing) return@post
                            shortcutIconCache[entry.key] = drawable
                            picture.setImageDrawable(drawable)
                            letter.visibility = View.GONE
                        }
                    }
                }.start()
            }
        }
        cell.addView(label(entry.label, 11f, TEXT_DIM).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(4) }
        })
        cell.setOnClickListener { openPinned(entry) }
        cell.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showPinnedActions(entry)
            true
        }
        return cell
    }

    /** Same rules as the drawer: a blocked app shows the block screen instead of opening. */
    private fun openPinned(entry: HomePinnedItems.Entry) {
        if (entry.packageName in getBlockedPackages()) {
            launchBlockOverlay(entry.packageName)
            return
        }
        if (entry.kind == HomePinnedItems.Kind.APP) {
            launchApp(entry.packageName)
            return
        }
        try {
            val launcherApps = getSystemService(LauncherApps::class.java)
            launcherApps?.startShortcut(entry.packageName, entry.id, null, null, Process.myUserHandle())
        } catch (_: Exception) {
            Toast.makeText(this, "Couldn't open ${entry.label}. Hold it to remove it.", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadShortcutIcon(entry: HomePinnedItems.Entry): Drawable? = try {
        val launcherApps = getSystemService(LauncherApps::class.java)
        val query = LauncherApps.ShortcutQuery()
            .setPackage(entry.packageName)
            .setShortcutIds(listOf(entry.id))
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        val info = launcherApps?.getShortcuts(query, Process.myUserHandle())?.firstOrNull()
        if (info == null) null else launcherApps.getShortcutIconDrawable(info, resources.displayMetrics.densityDpi)
    } catch (_: Exception) {
        null
    }

    private fun showPinnedActions(entry: HomePinnedItems.Entry) {
        showSheet { panel ->
            panel.addView(label(entry.label, 18f, TEXT_PRIMARY, bold = true))
            panel.addView(sheetRow("Remove from home") {
                dismissSheet()
                unpin(entry)
            })
            panel.addView(sheetRow("Edit home", "Widgets, wallpaper, style", TEXT_DIM) {
                dismissSheet()
                enterHomeArrange()
            })
        }
    }

    // ── Arrange mode ───────────────────────────────────────────────────────────
    //
    // Long-press empty space or the dock, pinch the home screen in or out, or choose "Arrange home"
    // from a widget's menu, and the widgets turn into one compact tile each. (Long-pressing a widget
    // itself lifts it for a direct drag instead; see "Dragging widgets in place" below.) Compact
    // tiles are all the same height, so a long limits list or a tall task card is no harder to move
    // than the clock. Drag ≡ to reorder, Hide or Show to switch one off or on, Done to go back.
    // "Style & options" opens the full Customize home sheet.

    private var homeArrangeActive = false
    private var arrangeBar: View? = null

    private class HomeWidgetInfo(val title: String, val prefKey: String, val style: () -> String)

    private fun homeWidgetInfo(id: String): HomeWidgetInfo = when (id) {
        "clock" -> HomeWidgetInfo("Clock", PREF_HOME_CLOCK) { clockStyleValue() }
        "status" -> HomeWidgetInfo("Status", PREF_HOME_STATUS) { statusStyleValue() }
        "task" -> HomeWidgetInfo("Current task", PREF_HOME_TASK) { taskStyleValue() }
        "pinned" -> HomeWidgetInfo("Pinned apps", PREF_HOME_PINNED) { pinnedStyleValue() }
        "widgets" -> HomeWidgetInfo("App widgets", PREF_HOME_WIDGETS) { widgetsStyleValue() }
        else -> HomeWidgetInfo("Today's limits", PREF_HOME_LIMITS) { limitsStyleValue() }
    }

    /** Hiding keeps the style it had, so showing it again brings back the same look. */
    private fun setHomeWidgetVisible(id: String, visible: Boolean) {
        val info = homeWidgetInfo(id)
        val remembered = prefs.getString(info.prefKey + PREV_STYLE_SUFFIX, null)
        val current = info.style()
        val editor = prefs.edit()
        if (visible) {
            if (remembered != null && remembered != "off") editor.putString(info.prefKey, remembered)
            else editor.remove(info.prefKey)
            editor.remove(info.prefKey + PREV_STYLE_SUFFIX)
        } else {
            if (current != "off") editor.putString(info.prefKey + PREV_STYLE_SUFFIX, current)
            editor.putString(info.prefKey, "off")
        }
        editor.apply()
        rebuildHomeSections()
    }

    private fun enterHomeArrange() {
        if (homeArrangeActive || isDrawerOpen || sheetOverlay != null || hiddenOverlay != null || gridDrag != null) return
        // Long-press on empty space, or a pinch, opens the small Edit home menu (Widgets, Wallpaper,
        // Style & options) on both layouts. The cramped arrange screen is no longer opened.
        exitGridResize()
        showGridEditMenu()
    }

    private fun exitHomeArrange() {
        if (!homeArrangeActive) return
        homeArrangeActive = false
        arrangeBar?.let { (it.parent as? ViewGroup)?.removeView(it) }
        arrangeBar = null
        rebuildHomeSections()
    }

    private fun buildArrangeBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(8); it.bottomMargin = dp(8) }
        }
        fun button(text: String, selected: Boolean, onClick: () -> Unit): View =
            chipView(text, selected, onClick).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
                    .also { it.leftMargin = dp(4); it.rightMargin = dp(4) }
            }
        bar.addView(button("Style & options", false) {
            exitHomeArrange()
            showHomeCustomizeSheet()
        })
        bar.addView(button("Done", true) { exitHomeArrange() })
        return bar
    }

    private fun populateArrangeColumn(column: LinearLayout) {
        column.addView(label("Arrange your home", 20f, TEXT_PRIMARY, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(12) }
        })
        column.addView(label("Hold ≡ and drag to move a widget. Hide or show with the button on the right.", 12f, TEXT_DIM).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(4); it.bottomMargin = dp(14) }
        })
        val order = homeOrder().toMutableList()
        val rows = mutableListOf<View>()
        val stride = dp(ARRANGE_ROW_DP + ARRANGE_GAP_DP)
        order.forEach { id ->
            val info = homeWidgetInfo(id)
            val style = info.style()
            val hidden = style == "off"
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(ARRANGE_ROW_DP),
                ).also { it.bottomMargin = dp(ARRANGE_GAP_DP) }
                alpha = if (hidden) 0.55f else 1f
            }
            frost(row, 16)
            val handle = label("≡", 24f, TEXT_DIM).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(56), LinearLayout.LayoutParams.MATCH_PARENT)
                contentDescription = "Reorder ${info.title}"
            }
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            texts.addView(label(info.title, 16f, TEXT_PRIMARY, bold = true))
            texts.addView(label(
                when {
                    hidden -> "Hidden"
                    id == "pinned" && pinnedEntries().isEmpty() -> "Nothing pinned yet"
                    id == "widgets" -> widgetEntries().size.let { n ->
                        if (n == 0) "Nothing added yet" else if (n == 1) "1 widget" else "$n widgets"
                    }
                    else -> style.replaceFirstChar { it.uppercase() }
                },
                12f,
                TEXT_DIM,
            ))
            val toggle = label(if (hidden) "Show" else "Hide", 13f, if (hidden) ACCENT_TEXT else TEXT_DIM).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.MATCH_PARENT)
                isClickable = true
                background = rippleForeground(16)
                contentDescription = (if (hidden) "Show " else "Hide ") + info.title
                setOnClickListener { setHomeWidgetVisible(id, hidden) }
            }
            row.addView(handle)
            row.addView(texts)
            row.addView(toggle)
            attachReorderDrag(handle, row, rows, order, stride, id)
            rows.add(row)
            column.addView(row)
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(4) }
        }
        actions.addView(chipView("+ Add a widget", false) {
            exitHomeArrange()
            showWidgetPicker()
        })
        if (widgetEntries().isNotEmpty()) {
            actions.addView(chipView("Manage widgets", false) { showManageWidgetsSheet() })
        }
        column.addView(actions)
    }

    /**
     * Press the handle and drag: the row follows the finger and the others slide out of the way.
     * Letting go saves the order. Screen readers get "Move up" and "Move down" on the handle
     * instead, because a drag is not possible there.
     */
    private fun attachReorderDrag(
        handle: View,
        row: View,
        rows: List<View>,
        order: MutableList<String>,
        stride: Int,
        id: String,
    ) {
        var startY = 0f
        var from = 0
        var to = 0
        handle.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // The home screen scrolls; keep it from taking the gesture away mid-drag.
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    startY = ev.rawY
                    from = rows.indexOf(row)
                    to = from
                    row.scaleX = 1.03f
                    row.scaleY = 1.03f
                    row.translationZ = dp(8).toFloat()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val minDy = -from * stride.toFloat()
                    val maxDy = (rows.lastIndex - from) * stride.toFloat()
                    val dy = (ev.rawY - startY).coerceIn(minDy, maxDy)
                    row.translationY = dy
                    val newTo = (from + (dy / stride).roundToInt()).coerceIn(0, rows.lastIndex)
                    if (newTo != to) {
                        to = newTo
                        rows.forEachIndexed { i, other ->
                            if (other !== row) {
                                val shift = when {
                                    from < to && i in (from + 1)..to -> -stride.toFloat()
                                    from > to && i in to until from -> stride.toFloat()
                                    else -> 0f
                                }
                                other.animate().translationY(shift).setDuration(120).start()
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                    row.scaleX = 1f
                    row.scaleY = 1f
                    row.translationZ = 0f
                    if (ev.actionMasked == MotionEvent.ACTION_UP && to != from) {
                        val moved = order.removeAt(from)
                        order.add(to, moved)
                        prefs.edit().putString(PREF_HOME_ORDER, order.joinToString(",")).apply()
                        rebuildHomeSections()
                    } else {
                        rows.forEach { it.animate().translationY(0f).setDuration(120).start() }
                    }
                    true
                }
                else -> false
            }
        }
        handle.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: android.view.accessibility.AccessibilityNodeInfo,
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(
                    android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(A11Y_MOVE_UP, "Move up"),
                )
                info.addAction(
                    android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(A11Y_MOVE_DOWN, "Move down"),
                )
            }

            override fun performAccessibilityAction(host: View, action: Int, args: android.os.Bundle?): Boolean {
                val delta = when (action) {
                    A11Y_MOVE_UP -> -1
                    A11Y_MOVE_DOWN -> 1
                    else -> return super.performAccessibilityAction(host, action, args)
                }
                moveHomeWidget(id, delta)
                return true
            }
        }
    }

    // ── Dragging widgets in place ──────────────────────────────────────────────
    //
    // Long-press a widget on the home screen and it lifts off the page. Drag it up or down and the
    // others slide out of the way; let go and it stays where it is. No menu in between. Widgets are
    // different heights, so HomeDragMath works from each one's real size, and the home screen
    // scrolls by itself when the finger nears its top or bottom edge.
    //
    // Hide, Show and the styles stay in the arrange bar (long-press empty space, the dock, or pinch)
    // and in Customize home. A pinned app and an app widget keep their own long-press menus, so the
    // gaps around them are what start the drag.

    private class HomeBlock(val id: String, val view: View)

    private class HomeDrag(
        val blocks: List<HomeBlock>,
        val slots: List<HomeDragMath.Slot>,
        val from: Int,
        /** How far below the lifted block's top edge the finger first landed. */
        val grabOffset: Float,
    ) {
        var to = from
        val shifts = FloatArray(blocks.size)
    }

    /** The widgets showing on the home screen now, top to bottom. */
    private var homeBlocks: List<HomeBlock> = emptyList()
    private var homeDrag: HomeDrag? = null
    private var lastTouchRawX = 0f
    private var lastTouchRawY = 0f

    /**
     * Runs once per frame while a block is lifted: scrolls the home screen when the finger rests
     * near its top or bottom edge, and keeps the lifted block under the finger as it scrolls.
     */
    private val homeDragTick = object : Runnable {
        override fun run() {
            if (homeDrag == null) return
            val scroll = homeScroll ?: return
            val top = scroll.locationOnScreenY()
            val edge = dp(96)
            val y = lastTouchRawY
            val bottom = top + scroll.height
            val step = when {
                y < top + edge -> -((top + edge - y) / edge).coerceIn(0f, 1f) * dp(16)
                y > bottom - edge -> ((y - (bottom - edge)) / edge).coerceIn(0f, 1f) * dp(16)
                else -> 0f
            }
            if (step != 0f) scroll.scrollBy(0, step.roundToInt())
            updateHomeDrag()
            // The glass panels pick up the wallpaper under them by position, so redraw them.
            invalidateFrosted()
            scroll.postOnAnimation(this)
        }
    }

    /** Lifts the widget under [rawY], or returns false when there is nothing to drag there. */
    private fun beginHomeDrag(rawY: Float): Boolean {
        if (homeDrag != null || homeArrangeActive || isDrawerOpen ||
            sheetOverlay != null || hiddenOverlay != null
        ) return false
        val column = homeColumn ?: return false
        val scroll = homeScroll ?: return false
        if (!scroll.isShown) return false
        // A collapsed widget (a task card with no task) takes no room and cannot be dragged.
        val blocks = homeBlocks.filter { it.view.visibility != View.GONE && it.view.height > 0 }
        if (blocks.size < 2) return false
        val scrollTop = scroll.locationOnScreenY()
        if (rawY < scrollTop || rawY > scrollTop + scroll.height) return false
        val slots = blocks.map { b ->
            val lp = b.view.layoutParams as? ViewGroup.MarginLayoutParams
            HomeDragMath.Slot(b.view.top, b.view.height, lp?.topMargin ?: 0, lp?.bottomMargin ?: 0)
        }
        val fingerInColumn = rawY - column.locationOnScreenY()
        val from = HomeDragMath.indexAt(slots, fingerInColumn)
        if (from < 0) return false
        homeDrag = HomeDrag(blocks, slots, from, fingerInColumn - slots[from].top)
        // The views under the finger saw a press. End it, so the drag owns the rest of the gesture.
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, lastTouchRawX, lastTouchRawY, 0)
        window.superDispatchTouchEvent(cancel)
        cancel.recycle()
        val lifted = blocks[from].view
        // The lifted block grows a little and may sit at the very top or bottom: don't clip it.
        column.clipChildren = false
        lifted.animate().cancel()
        lifted.translationZ = dp(16).toFloat()
        lifted.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start()
        scroll.postOnAnimation(homeDragTick)
        return true
    }

    /** Moves the lifted widget to the finger and slides the ones it has passed out of the way. */
    private fun updateHomeDrag() {
        val d = homeDrag ?: return
        val column = homeColumn ?: return
        val slot = d.slots[d.from]
        val fingerInColumn = lastTouchRawY - column.locationOnScreenY()
        val top = HomeDragMath.clampTop(d.slots, d.from, fingerInColumn - d.grabOffset)
        val lifted = d.blocks[d.from].view
        lifted.translationY = top - slot.top
        val to = HomeDragMath.targetIndex(d.slots, d.from, top + slot.height / 2f)
        if (to == d.to) return
        d.to = to
        lifted.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        val next = HomeDragMath.shifts(d.slots, d.from, to)
        d.blocks.forEachIndexed { i, b ->
            if (i != d.from && next[i] != d.shifts[i]) {
                b.view.animate().translationY(next[i]).setDuration(140).start()
            }
            d.shifts[i] = next[i]
        }
    }

    /** Lets go of the lifted widget: settles it into its new place and saves, or puts everything back. */
    private fun endHomeDrag(commit: Boolean) {
        val d = homeDrag ?: return
        homeDrag = null
        homeScroll?.removeCallbacks(homeDragTick)
        val lifted = d.blocks[d.from].view
        if (commit && d.to != d.from) {
            // Save first, so the order holds even if the screen is rebuilt before the settle ends.
            val saved = HomeDragMath.reorder(homeOrder(), d.blocks.map { it.id }, d.from, d.to)
            prefs.edit().putString(PREF_HOME_ORDER, saved.joinToString(",")).apply()
            lifted.animate()
                .translationY(HomeDragMath.dropOffset(d.slots, d.from, d.to))
                .scaleX(1f).scaleY(1f)
                .setDuration(140)
                .setUpdateListener { invalidateFrosted() }
                .withEndAction {
                    lifted.translationZ = 0f
                    // Rebuild from the saved order, unless the screen was already rebuilt meanwhile.
                    if (homeBlocks.any { it.view === lifted }) rebuildHomeSections()
                }
                .start()
        } else {
            d.blocks.forEach { b ->
                if (b.view !== lifted) b.view.animate().translationY(0f).setDuration(140).start()
            }
            lifted.animate()
                .translationY(0f)
                .scaleX(1f).scaleY(1f)
                .setDuration(140)
                .setUpdateListener { invalidateFrosted() }
                .withEndAction { lifted.translationZ = 0f }
                .start()
        }
    }

    /** Drops a drag without saving anything and puts every widget straight back. */
    private fun abortHomeDrag() {
        val d = homeDrag ?: return
        homeDrag = null
        homeScroll?.removeCallbacks(homeDragTick)
        d.blocks.forEach { b ->
            b.view.animate().cancel()
            b.view.translationY = 0f
            b.view.scaleX = 1f
            b.view.scaleY = 1f
            b.view.translationZ = 0f
        }
    }

    // ── Dragging on the grid ───────────────────────────────────────────────────
    //
    // Long-press an item on the grid home and it lifts: a copy follows the finger, a soft outline
    // shows the cells it would take, and anything in the way slides aside once the finger has rested
    // there a moment. Let go and it settles into place. Drag it to the Remove bar at the top and it
    // is taken off home (with an Undo). Let go without moving and its own menu opens instead.

    private class GridDrag(
        val item: HomeGrid.Item,
        val view: View,
        val copy: ImageView,
        /** Where inside the item the finger first landed. */
        val grabX: Float,
        val grabY: Float,
        val startX: Float,
        val startY: Float,
        /** Everyone's positions when the drag began. */
        val items: List<HomeGrid.Item>,
        /** Set when the item is an app being dragged in from the drawer rather than one already on home. */
        val fromDrawer: HomePinnedItems.Entry? = null,
    ) {
        var moved = false
        var overBar = false
        /** The cell the finger is over now, and the one a drop would use. */
        var candidate: HomeGrid.Cell? = null
        var target: HomeGrid.Cell? = null
        /** Where everyone would be if dropped at [target]. */
        var placed: List<HomeGrid.Item>? = null
    }

    private var gridDrag: GridDrag? = null
    private var gridItems: List<HomeGrid.Item> = emptyList()
    private var gridOutline: View? = null
    private var gridDropBar: TextView? = null
    private val gridPushRunnable = Runnable { applyGridPlacement() }

    /** The direct child of the grid that [v] is, or sits inside; null for the grid itself or anything else. */
    private fun gridChildOf(v: View): View? {
        val grid = homeGrid ?: return null
        var cur: View? = v
        while (cur != null && cur.parent !== grid) cur = cur.parent as? View
        return if (cur != null && cur.tag is String) cur else null
    }

    private fun beginGridDrag(child: View): Boolean {
        val grid = homeGrid ?: return false
        if (gridDrag != null || homeArrangeActive || isDrawerOpen ||
            sheetOverlay != null || hiddenOverlay != null
        ) return false
        val id = child.tag as? String ?: return false
        val item = gridItems.firstOrNull { it.id == id } ?: return false
        if (child.width <= 0 || child.height <= 0) return false
        exitGridResize()
        val bitmap = try {
            Bitmap.createBitmap(child.width, child.height, Bitmap.Config.ARGB_8888)
                .also { child.draw(Canvas(it)) }
        } catch (_: Exception) {
            return false
        }
        val childLoc = IntArray(2)
        child.getLocationOnScreen(childLoc)
        val rootLoc = IntArray(2)
        rootFrame.getLocationOnScreen(rootLoc)

        val outline = View(this).apply {
            background = roundedBackground(Color.parseColor("#22FFFFFF"), Color.parseColor("#99FFFFFF"), 16)
            layoutParams = HomeGridLayout.Params(item.col, item.row, item.spanX, item.spanY)
        }
        grid.addView(outline, 0)
        gridOutline = outline

        val bar = label("Remove", 15f, TEXT_PRIMARY, bold = true).apply {
            gravity = Gravity.CENTER
            background = roundedBackground(Color.parseColor("#80000000"), Color.parseColor("#33FFFFFF"), 28)
            alpha = 0f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(56),
            ).also {
                it.topMargin = barTop + dp(8)
                it.leftMargin = dp(20)
                it.rightMargin = dp(20)
            }
        }
        rootFrame.addView(bar)
        bar.animate().alpha(1f).setDuration(120).start()
        gridDropBar = bar

        val copy = ImageView(this).apply {
            setImageBitmap(bitmap)
            layoutParams = FrameLayout.LayoutParams(child.width, child.height)
            translationX = (childLoc[0] - rootLoc[0]).toFloat()
            translationY = (childLoc[1] - rootLoc[1]).toFloat()
            translationZ = dp(16).toFloat()
        }
        rootFrame.addView(copy)
        copy.animate().scaleX(1.05f).scaleY(1.05f).alpha(0.95f).setDuration(120).start()
        child.alpha = 0f

        gridDrag = GridDrag(
            item, child, copy,
            grabX = lastTouchRawX - childLoc[0],
            grabY = lastTouchRawY - childLoc[1],
            startX = lastTouchRawX,
            startY = lastTouchRawY,
            items = gridItems,
        )
        // The views under the finger saw a press. End it, so the drag owns the rest of the gesture.
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, lastTouchRawX, lastTouchRawY, 0)
        window.superDispatchTouchEvent(cancel)
        cancel.recycle()
        return true
    }

    private fun updateGridDrag() {
        val d = gridDrag ?: return
        val grid = homeGrid ?: return
        val x = lastTouchRawX
        val y = lastTouchRawY
        if (!d.moved) {
            val slop = android.view.ViewConfiguration.get(this).scaledTouchSlop
            if (Math.hypot((x - d.startX).toDouble(), (y - d.startY).toDouble()) > slop) d.moved = true
        }
        if (!d.moved) return
        val rootLoc = IntArray(2)
        rootFrame.getLocationOnScreen(rootLoc)
        d.copy.translationX = x - d.grabX - rootLoc[0]
        d.copy.translationY = y - d.grabY - rootLoc[1]

        val overBar = d.fromDrawer == null && y < rootLoc[1] + barTop + dp(8) + dp(56) + dp(16)
        if (overBar != d.overBar) {
            d.overBar = overBar
            gridDropBar?.background = roundedBackground(
                if (overBar) Color.parseColor("#CCDC2626") else Color.parseColor("#80000000"),
                Color.parseColor("#33FFFFFF"),
                28,
            )
            d.copy.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            handler.removeCallbacks(gridPushRunnable)
            d.candidate = null
            showGridPlacement(d, null, null)
        }
        if (overBar) return

        val cw = grid.cellWidth
        val ch = grid.cellHeight
        if (cw <= 0 || ch <= 0) return
        val gridLoc = IntArray(2)
        grid.getLocationOnScreen(gridLoc)
        val fx = (x - d.grabX - gridLoc[0] - grid.paddingLeft - grid.insetPx) / cw
        val fy = (y - d.grabY - gridLoc[1] - grid.paddingTop - grid.insetPx) / ch
        val cell = HomeGrid.snap(grid.columns, grid.rows, d.item.spanX, d.item.spanY, fx, fy)
        if (cell == d.candidate) return
        d.candidate = cell
        handler.removeCallbacks(gridPushRunnable)
        val free = HomeGrid.fits(
            d.items, grid.columns, grid.rows, cell.col, cell.row, d.item.spanX, d.item.spanY,
            ignoreId = d.item.id,
        )
        // A free spot shows at once. Over something else, the others only move aside if the
        // finger rests here a moment, so a drag that merely passes over them does not shove them.
        if (free) applyGridPlacement() else handler.postDelayed(gridPushRunnable, 150)
    }

    private fun applyGridPlacement() {
        val d = gridDrag ?: return
        val grid = homeGrid ?: return
        val cell = d.candidate ?: return
        val placed = HomeGrid.place(
            d.items, grid.columns, grid.rows, d.item.id, cell.col, cell.row, d.item.spanX, d.item.spanY,
        )
        if (placed == null) showGridPlacement(d, null, null) else showGridPlacement(d, cell, placed)
    }

    /** Shows the outline and slides the others to where [placed] puts them; a null cell shows no drop spot. */
    private fun showGridPlacement(d: GridDrag, cell: HomeGrid.Cell?, placed: List<HomeGrid.Item>?) {
        val grid = homeGrid ?: return
        if (cell != d.target) d.copy.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        d.target = cell
        d.placed = placed
        val outline = gridOutline
        if (outline != null) {
            if (cell == null) {
                outline.visibility = View.INVISIBLE
            } else {
                (outline.layoutParams as HomeGridLayout.Params).also {
                    it.col = cell.col
                    it.row = cell.row
                }
                outline.visibility = View.VISIBLE
                outline.requestLayout()
            }
        }
        slideGridItems(d.items, placed, d.item.id)
    }

    /**
     * Slides every item except [exceptId] to where [placed] puts it, relative to where it is saved
     * in [before]; a null [placed] slides them all back.
     */
    private fun slideGridItems(before: List<HomeGrid.Item>, placed: List<HomeGrid.Item>?, exceptId: String) {
        val grid = homeGrid ?: return
        val byId = placed?.associateBy { it.id }
        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i)
            val id = child.tag as? String ?: continue
            if (id == exceptId) continue
            val was = before.firstOrNull { it.id == id } ?: continue
            val now = byId?.get(id) ?: was
            child.animate()
                .translationX(((now.col - was.col) * grid.cellWidth).toFloat())
                .translationY(((now.row - was.row) * grid.cellHeight).toFloat())
                .setDuration(150)
                .setUpdateListener { invalidateFrosted() }
                .start()
        }
    }

    private fun endGridDrag(commit: Boolean) {
        val d = gridDrag ?: return
        gridDrag = null
        handler.removeCallbacks(gridPushRunnable)
        val grid = homeGrid
        if (commit && !d.moved) {
            // A long press with no drag: show the item's own menu, as a long press used to.
            clearGridDrag(d)
            when (d.item.kind) {
                HomeGrid.Kind.APP, HomeGrid.Kind.SHORTCUT ->
                    pinnedEntries().firstOrNull { it.key == d.item.key }?.let { showPinnedActions(it) }
                // Widgets and cards get resize handles; their old menus only had height and remove,
                // which the grid does by handles and the Remove bar.
                HomeGrid.Kind.WIDGET, HomeGrid.Kind.CARD -> enterGridResize(d.item.id)
            }
            return
        }
        if (commit && d.overBar) {
            clearGridDrag(d)
            removeFromGrid(d.item)
            return
        }
        val target = d.target
        val placed = d.placed
        val pinning = d.fromDrawer
        if (commit && grid != null && target != null && placed != null) {
            // Save first, so the spot holds even if the screen is rebuilt before the settle ends.
            prefs.edit().putString(HomeGrid.PREF_KEY, HomeGrid.serialize(placed)).apply()
            if (pinning != null) {
                // The app joins the pinned list too; the grid already has its place saved above.
                val out = HomePinnedItems.add(pinnedEntries(), pinning, HomePinnedItems.MAX_ITEMS)
                if (out.status == HomePinnedItems.Status.ADDED) {
                    savePinned(out.items)
                    if (pinnedStyleValue() == "off") prefs.edit().remove(PREF_HOME_PINNED).apply()
                    Toast.makeText(this, "${pinning.label} added to home", Toast.LENGTH_SHORT).show()
                }
            }
            val rootLoc = IntArray(2)
            rootFrame.getLocationOnScreen(rootLoc)
            val gridLoc = IntArray(2)
            grid.getLocationOnScreen(gridLoc)
            val x = gridLoc[0] + grid.paddingLeft + target.col * grid.cellWidth + grid.insetPx - rootLoc[0]
            val y = gridLoc[1] + grid.paddingTop + target.row * grid.cellHeight + grid.insetPx - rootLoc[1]
            d.copy.animate().translationX(x.toFloat()).translationY(y.toFloat())
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(140)
                .withEndAction {
                    clearGridDrag(d)
                    // Rebuilds from what was just saved, and refreshes the clock and cards.
                    rebuildHomeSections()
                }
                .start()
            return
        }
        if (pinning != null) {
            // An app from the drawer with nowhere to land just fades away.
            d.copy.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(140)
                .withEndAction { clearGridDrag(d) }
                .start()
            showGridPlacement(d, null, null)
            return
        }
        // Nowhere to land (or the drag was cancelled): settle back where it came from.
        val childLoc = IntArray(2)
        d.view.getLocationOnScreen(childLoc)
        val rootLoc = IntArray(2)
        rootFrame.getLocationOnScreen(rootLoc)
        d.copy.animate()
            .translationX((childLoc[0] - rootLoc[0]).toFloat())
            .translationY((childLoc[1] - rootLoc[1]).toFloat())
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(140)
            .withEndAction { clearGridDrag(d) }
            .start()
        showGridPlacement(d, null, null)
    }

    /** Takes the lifted copy, the outline and the Remove bar away and puts everything back to rest. */
    private fun clearGridDrag(d: GridDrag) {
        d.copy.animate().cancel()
        (d.copy.parent as? ViewGroup)?.removeView(d.copy)
        gridDropBar?.let { bar ->
            bar.animate().cancel()
            (bar.parent as? ViewGroup)?.removeView(bar)
        }
        gridDropBar = null
        gridOutline?.let { (it.parent as? ViewGroup)?.removeView(it) }
        gridOutline = null
        d.view.alpha = 1f
        homeGrid?.let { grid ->
            for (i in 0 until grid.childCount) {
                val child = grid.getChildAt(i)
                child.animate().cancel()
                child.translationX = 0f
                child.translationY = 0f
            }
        }
        invalidateFrosted()
    }

    /** Drops a grid drag without saving anything. */
    private fun abortGridDrag() {
        val d = gridDrag ?: return
        gridDrag = null
        handler.removeCallbacks(gridPushRunnable)
        clearGridDrag(d)
    }

    /**
     * Takes an item off the home screen and offers Undo. Nothing is deleted for good until the Undo
     * has gone: a widget's slot is only given back to Android once the time is up.
     */
    private fun removeFromGrid(item: HomeGrid.Item) {
        val previousGrid = prefs.getString(HomeGrid.PREF_KEY, null)
        fun restoreGrid() = prefs.edit().putString(HomeGrid.PREF_KEY, previousGrid).apply()
        when (item.kind) {
            HomeGrid.Kind.CARD -> {
                val title = homeWidgetInfo(item.key).title
                setHomeWidgetVisible(item.key, false)
                showUndoBar("$title hidden", onUndo = {
                    restoreGrid()
                    setHomeWidgetVisible(item.key, true)
                })
            }
            HomeGrid.Kind.APP, HomeGrid.Kind.SHORTCUT -> {
                val before = pinnedEntries()
                val entry = before.firstOrNull { it.key == item.key } ?: return
                unpin(entry)
                showUndoBar("${entry.label} removed", onUndo = {
                    restoreGrid()
                    savePinned(before)
                    rebuildHomeSections()
                })
            }
            HomeGrid.Kind.WIDGET -> {
                val before = widgetEntries()
                val entry = before.firstOrNull { it.appWidgetId.toString() == item.key } ?: return
                saveWidgets(before.filterNot { it === entry })
                rebuildHomeSections()
                showUndoBar("Widget removed", onUndo = {
                    restoreGrid()
                    saveWidgets(before)
                    rebuildHomeSections()
                }, onExpire = {
                    try { widgetHost.deleteAppWidgetId(entry.appWidgetId) } catch (_: Exception) {}
                    widgetViews.remove(entry.appWidgetId)
                    if (widgetEntries().isEmpty()) stopWidgetListening()
                })
            }
        }
    }

    // ── Dragging an app from the drawer onto the grid ──────────────────────────
    //
    // On the Grid layout, long-press an app in the drawer and move: the drawer fades away and the
    // app's icon follows the finger over the home grid, using the same outline and push-aside as any
    // other drag. Let go over a spot and the app is pinned there. Let go without moving and the
    // app's menu opens, as it always did.

    private class PendingDrawerDrag(
        val entry: HomePinnedItems.Entry,
        val startX: Float,
        val startY: Float,
        /** Everyone's positions with the new icon added, so a drop can be worked out. */
        val items: List<HomeGrid.Item>,
    )

    private var pendingDrawerDrag: PendingDrawerDrag? = null

    /** False when this is not a drag-to-home case (grid off, already pinned, no room): the caller opens the menu. */
    private fun beginDrawerDrag(pkg: String, name: String): Boolean {
        val grid = homeGrid ?: return false
        if (!gridHomeOn() || gridDrag != null || pendingDrawerDrag != null) return false
        val entry = HomePinnedItems.Entry(HomePinnedItems.Kind.APP, pkg, "", name)
        if (pinnedEntries().any { it.key == entry.key }) return false
        val added = HomeGrid.add(
            gridItems, grid.columns, grid.rows,
            HomeGrid.Wanted(HomeGrid.Kind.APP, entry.key, 1, 1),
        )
        if (added.status == HomeGrid.Status.FULL) {
            Toast.makeText(this, "Home is full. Remove something first.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (added.status != HomeGrid.Status.ADDED) return false
        pendingDrawerDrag = PendingDrawerDrag(entry, lastTouchRawX, lastTouchRawY, added.items)
        // The drawer list saw a press. End it, so the drag owns the rest of the gesture.
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, lastTouchRawX, lastTouchRawY, 0)
        window.superDispatchTouchEvent(cancel)
        cancel.recycle()
        return true
    }

    /** Once the finger has moved far enough, the drawer gives way and the grid drag takes over. */
    private fun movePendingDrawerDrag(p: PendingDrawerDrag) {
        val grid = homeGrid
        if (grid == null) {
            pendingDrawerDrag = null
            return
        }
        val slop = android.view.ViewConfiguration.get(this).scaledTouchSlop
        if (Math.hypot((lastTouchRawX - p.startX).toDouble(), (lastTouchRawY - p.startY).toDouble()) <= slop) return
        pendingDrawerDrag = null
        closeDrawer()
        val size = dp(56)
        val copy = ImageView(this).apply {
            setImageDrawable(getRoundIcon(p.entry.packageName))
            layoutParams = FrameLayout.LayoutParams(size, size)
            translationZ = dp(16).toFloat()
        }
        rootFrame.addView(copy)
        copy.animate().scaleX(1.1f).scaleY(1.1f).alpha(0.95f).setDuration(120).start()
        val virtual = p.items.last()
        val outline = View(this).apply {
            background = roundedBackground(Color.parseColor("#22FFFFFF"), Color.parseColor("#99FFFFFF"), 16)
            layoutParams = HomeGridLayout.Params(virtual.col, virtual.row, 1, 1)
            visibility = View.INVISIBLE
        }
        grid.addView(outline, 0)
        gridOutline = outline
        gridDrag = GridDrag(
            item = virtual, view = copy, copy = copy,
            grabX = size / 2f, grabY = size / 2f,
            startX = p.startX, startY = p.startY,
            items = p.items,
            fromDrawer = p.entry,
        ).also { it.moved = true }
        updateGridDrag()
    }

    // ── Resizing on the grid ───────────────────────────────────────────────────
    //
    // Letting go of a long press without moving, on a widget or a card, shows an outline with a
    // handle on each edge. Drag a handle to grow or shrink it in whole cells; whatever is in the way
    // slides aside. A tap outside the outline closes it.

    private class GridResize(
        val item: HomeGrid.Item,
        val edge: HomeGrid.Edge,
        val items: List<HomeGrid.Item>,
    ) {
        var current: HomeGrid.Item = item
        var placed: List<HomeGrid.Item>? = null
    }

    private var gridResizeId: String? = null
    private var gridResizeFrame: HomeResizeFrame? = null
    private var gridResizeDrag: GridResize? = null
    private var swallowGesture = false

    private fun enterGridResize(id: String) {
        val grid = homeGrid ?: return
        val item = gridItems.firstOrNull { it.id == id } ?: return
        exitGridResize()
        gridResizeId = id
        val frame = HomeResizeFrame(this, ACCENT_TEXT).apply {
            layoutParams = HomeGridLayout.Params(item.col, item.row, item.spanX, item.spanY)
        }
        grid.addView(frame)
        gridResizeFrame = frame
    }

    private fun exitGridResize() {
        gridResizeDrag = null
        gridResizeFrame?.let { (it.parent as? ViewGroup)?.removeView(it) }
        gridResizeFrame = null
        gridResizeId = null
    }

    /** The frame's rectangle on screen, in pixels, or null when there is none. */
    private fun gridResizeRect(): RectF? {
        val grid = homeGrid ?: return null
        val item = gridItems.firstOrNull { it.id == gridResizeId } ?: return null
        val loc = IntArray(2)
        grid.getLocationOnScreen(loc)
        val left = loc[0] + grid.paddingLeft + item.col * grid.cellWidth + grid.insetPx
        val top = loc[1] + grid.paddingTop + item.row * grid.cellHeight + grid.insetPx
        return RectF(
            left.toFloat(),
            top.toFloat(),
            (left + item.spanX * grid.cellWidth - 2 * grid.insetPx).toFloat(),
            (top + item.spanY * grid.cellHeight - 2 * grid.insetPx).toFloat(),
        )
    }

    private fun beginGridResize(edge: HomeGrid.Edge) {
        val item = gridItems.firstOrNull { it.id == gridResizeId } ?: return
        gridResizeDrag = GridResize(item, edge, gridItems)
        homeGrid?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun updateGridResize() {
        val d = gridResizeDrag ?: return
        val grid = homeGrid ?: return
        if (grid.cellWidth <= 0 || grid.cellHeight <= 0) return
        val loc = IntArray(2)
        grid.getLocationOnScreen(loc)
        val fx = (lastTouchRawX - loc[0] - grid.paddingLeft) / grid.cellWidth
        val fy = (lastTouchRawY - loc[1] - grid.paddingTop) / grid.cellHeight
        val resized = HomeGrid.resizedBlock(d.item, d.edge, fx, fy, grid.columns, grid.rows)
        if (resized == d.current) return
        val placed = HomeGrid.place(
            d.items, grid.columns, grid.rows, d.item.id,
            resized.col, resized.row, resized.spanX, resized.spanY,
        ) ?: return // nowhere for the others to go: stay at the last size that worked
        d.current = resized
        d.placed = placed
        grid.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        // Show it live: the item and its frame take the new cells, the others slide.
        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i)
            if (child === gridResizeFrame || child.tag == d.item.id) {
                (child.layoutParams as HomeGridLayout.Params).also {
                    it.col = resized.col
                    it.row = resized.row
                    it.spanX = resized.spanX
                    it.spanY = resized.spanY
                }
                child.requestLayout()
            }
        }
        slideGridItems(d.items, placed, d.item.id)
    }

    private fun endGridResize(commit: Boolean) {
        val d = gridResizeDrag ?: return
        gridResizeDrag = null
        val placed = d.placed
        if (commit && placed != null && d.current != d.item) {
            // Save, then rebuild from what was saved; resize mode carries over to the rebuilt item.
            prefs.edit().putString(HomeGrid.PREF_KEY, HomeGrid.serialize(placed)).apply()
            rebuildHomeSections()
        } else if (placed != null) {
            // Cancelled: rebuild to put everything back.
            rebuildHomeSections()
        }
    }

    private var undoBar: View? = null
    private var undoExpireAction: (() -> Unit)? = null
    private val undoExpireRunnable = Runnable { dismissUndoBar(runExpire = true) }

    private fun showUndoBar(message: String, onUndo: () -> Unit, onExpire: () -> Unit = {}) {
        if (!::rootFrame.isInitialized) {
            onExpire()
            return
        }
        // A second removal settles the first one for good.
        dismissUndoBar(runExpire = true)
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(4), dp(8), dp(4))
            background = roundedBackground(Color.parseColor("#F2232A3B"), Color.parseColor("#33FFFFFF"), 24)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(52),
                Gravity.BOTTOM,
            ).also {
                it.leftMargin = dp(20)
                it.rightMargin = dp(20)
                it.bottomMargin = barBottom + (dockView?.height ?: dp(120)) + dp(28)
            }
        }
        bar.addView(label(message, 14f, TEXT_PRIMARY).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        bar.addView(label("UNDO", 14f, ACCENT_TEXT, bold = true).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isClickable = true
            setOnClickListener {
                dismissUndoBar(runExpire = false)
                onUndo()
            }
        })
        rootFrame.addView(bar)
        undoBar = bar
        undoExpireAction = onExpire
        handler.postDelayed(undoExpireRunnable, 5000)
    }

    private fun dismissUndoBar(runExpire: Boolean) {
        handler.removeCallbacks(undoExpireRunnable)
        undoBar?.let { (it.parent as? ViewGroup)?.removeView(it) }
        undoBar = null
        val action = undoExpireAction
        undoExpireAction = null
        if (runExpire) action?.invoke()
    }

    /** Rebuilds the home widgets and dock in place, so a style change shows instantly under an open sheet. */
    private fun rebuildHomeSections() {
        val grid = homeGrid
        if (grid != null) {
            populateHomeGrid(grid)
        } else {
            val column = homeColumn ?: return
            populateHomeColumn(column)
        }
        populateDock()
        pruneFrosted()
        refreshHomeContent()
        invalidateFrosted()
    }

    private fun refreshHomeContent() {
        if (!::rootFrame.isInitialized) return
        refreshStatusPill()
        refreshFocusCard()
        refreshTodaysLimits()
        updateClockText()
        // On the grid, a card that starts or stops having something to show takes or gives back its
        // cells. Not while something is being dragged: the drag would be cancelled by the redraw,
        // and the next refresh catches up.
        if (homeGrid != null && gridDrag == null && gridResizeDrag == null && pendingDrawerDrag == null &&
            gridCardSignature != "${gridTaskWanted()}|${gridLimitsWanted()}"
        ) {
            rebuildHomeSections()
        }
    }

    private fun buildClockWidget(): LinearLayout {
        val style = clockStyleValue()
        clockStyle = style
        val date = label("", SIZE_DATE, TEXT_DIM).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            if (style == "light") {
                letterSpacing = 0.14f
                isAllCaps = true
            } else {
                textSize = 14f
                letterSpacing = 0.04f
            }
        }
        val clock = label(
            "",
            when (style) {
                "bold" -> 60f
                "stacked" -> 72f
                "compact" -> 30f
                else -> 68f
            },
            TEXT_PRIMARY,
        ).apply {
            includeFontPadding = false
            typeface = when (style) {
                "bold", "compact" -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
                else -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            }
            letterSpacing = if (style == "bold") -0.03f else -0.02f
            if (style == "stacked") setLineSpacing(0f, 0.82f)
            setShadowLayer(dp(10).toFloat(), 0f, dp(2).toFloat(), Color.parseColor("#80000000"))
        }
        val wrap = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also {
                // The stacked clock is two lines tall, so it gets less air above and below to leave
                // room for the widgets without making the home screen scroll.
                it.topMargin = dp(if (style == "compact") 8 else if (style == "stacked") 20 else 36)
                it.bottomMargin = dp(if (style == "stacked") 12 else 18)
            }
        }
        when (style) {
            "light" -> {
                wrap.orientation = LinearLayout.VERTICAL
                wrap.gravity = Gravity.CENTER_HORIZONTAL
                wrap.addView(date)
                wrap.addView(clock)
            }
            "compact" -> {
                wrap.orientation = LinearLayout.HORIZONTAL
                wrap.gravity = Gravity.BOTTOM or Gravity.START
                wrap.addView(clock)
                date.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also {
                    it.leftMargin = dp(12)
                    it.bottomMargin = dp(4)
                }
                wrap.addView(date)
            }
            else -> {
                wrap.orientation = LinearLayout.VERTICAL
                wrap.gravity = Gravity.START
                wrap.addView(clock)
                date.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = dp(4) }
                wrap.addView(date)
            }
        }
        dateView = date
        clockView = clock
        return wrap
    }

    private fun buildStatusPill(): View {
        val asPill = statusStyleValue() == "pill"
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                if (asPill) dp(34) else LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also {
                it.gravity = homeAlignGravity()
                it.bottomMargin = dp(18)
            }
            if (asPill) setPadding(dp(14), 0, dp(16), 0)
        }
        if (asPill) frost(pill, 17)
        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8))
            background = ovalBackground(GREEN_OK)
        }
        val text = label("", 13f, TEXT_DIM).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.leftMargin = dp(8) }
        }
        pill.addView(dot)
        pill.addView(text)
        statusDot = dot
        statusText = text
        refreshStatusPill()
        return pill
    }

    private fun refreshStatusPill() {
        val text = statusText ?: return
        val label = statusLabel()
        // "Ready" says nothing the person needs, so the status only appears when something is
        // actually going on: Focus Mode, Always-On, a task, or what is coming up next.
        (text.parent as? View)?.visibility = if (label == STATUS_READY) View.GONE else View.VISIBLE
        text.text = "${getBlockedPackages().size} blocked • $label"
        val color = when {
            prefs.getBoolean(PREF_ALWAYS_BLOCK, false) -> RED_BLOCK
            prefs.getBoolean(PREF_SA_ACTIVE, false) -> AMBER
            else -> GREEN_OK
        }
        statusDot?.background = ovalBackground(color)
    }

    private fun statusLabel(): String {
        val snapshot = currentSnapshot(System.currentTimeMillis())
        return when {
            prefs.getBoolean(PREF_ALWAYS_BLOCK, false) -> "Always-On"
            prefs.getBoolean(PREF_SA_ACTIVE, false) || prefs.getBoolean("focus_active", false) -> "Focus Mode"
            snapshot?.mode == WidgetMode.ACTIVE -> "In a task"
            snapshot?.mode == WidgetMode.NEXT && snapshot.startsToday -> "Next: ${snapshot.title}"
            else -> STATUS_READY
        }
    }

    private fun buildFocusSessionCard(): LinearLayout {
        val compact = taskStyleValue() == "compact"
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = dp(16) }
            setPadding(dp(18), dp(if (compact) 10 else 14), dp(18), dp(if (compact) 10 else 14))
            isClickable = true
            isFocusable = true
            setOnClickListener { openFocusFlow() }
            homeLongPress?.let { setOnLongClickListener(it) }
        }
        frost(card, if (compact) 18 else 24)
        focusHeaderView = null
        if (!compact) {
            val header = label("Now", 12f, TEXT_DIM)
            focusHeaderView = header
            card.addView(header)
        }
        val title = label("", if (compact) 15f else 18f, TEXT_PRIMARY, bold = true).apply {
            maxLines = if (compact) 1 else 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(if (compact) 0 else 8) }
        }
        val track = FrameLayout(this).apply {
            background = roundedBackground(Color.parseColor("#33FFFFFF"), Color.TRANSPARENT, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(4),
            ).also { it.topMargin = dp(if (compact) 8 else 12) }
        }
        val fill = View(this).apply {
            background = roundedBackground(Color.parseColor("#E0E7FF"), Color.TRANSPARENT, 4)
            layoutParams = FrameLayout.LayoutParams(0, dp(4))
        }
        track.addView(fill)
        val subtitle = label("", if (compact) 12f else 13f, TEXT_DIM).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(if (compact) 6 else 8) }
        }
        card.addView(title)
        card.addView(track)
        card.addView(subtitle)
        focusCard = card
        focusTitleView = title
        focusSubtitleView = subtitle
        focusProgressTrack = track
        focusProgressFill = fill
        card.foreground = rippleForeground(if (compact) 18 else 24)
        return card
    }

    /**
     * The same "what is happening now" the phone widget and the live notification use: the
     * task in progress on the schedule (Focus Mode or not), a running Focus session, or the
     * next task today. Rebuilt only when the schedule changes or a task starts or ends.
     */
    private fun currentSnapshot(now: Long): WidgetSnapshot? {
        if (now < homeSnapshotValidUntilMs) return homeSnapshot
        val tasks = homeTasks
        val sessionName = prefs.getString("task_name", "").orEmpty()
        val sessionEnd = prefs.getLong("task_end_ms", 0L)
        val session = if (sessionName.isNotBlank() && sessionEnd > now) {
            WidgetSession(
                name = sessionName,
                startMs = prefs.getLong("task_start_ms", 0L),
                endMs = sessionEnd,
                colorHex = prefs.getString("task_color", null),
            )
        } else null
        val snapshot = if (tasks == null && session == null) {
            null
        } else {
            WidgetSnapshotBuilder.build(tasks.orEmpty(), now, session)
        }
        homeSnapshot = snapshot
        homeSnapshotValidUntilMs = when (snapshot?.mode) {
            WidgetMode.ACTIVE -> min(snapshot.endMs, now + 60_000L)
            WidgetMode.NEXT -> min(snapshot.startMs, now + 60_000L)
            else -> now + 60_000L
        }
        return snapshot
    }

    private fun invalidateSnapshot() {
        homeSnapshotValidUntilMs = 0L
    }

    /** Follows the task list while the launcher is on screen; stopped again in onPause. */
    private fun startTaskCollection() {
        if (taskCollectJob?.isActive == true) return
        taskCollectJob = uiScope.launch {
            try {
                AppModule.taskRepository.observeAllTasks().collect { tasks ->
                    homeTasks = tasks
                    invalidateSnapshot()
                    refreshFocusCard()
                    refreshStatusPill()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // No task list available: the card still shows a running Focus session.
            }
        }
    }

    private fun stopTaskCollection() {
        taskCollectJob?.cancel()
        taskCollectJob = null
    }

    private fun refreshFocusCard() {
        val card = focusCard ?: return
        val now = System.currentTimeMillis()
        val snapshot = currentSnapshot(now)
        val showUpNext = snapshot?.mode == WidgetMode.NEXT && snapshot.startsToday
        if (snapshot == null || !(snapshot.mode == WidgetMode.ACTIVE || showUpNext)) {
            card.visibility = View.GONE
            return
        }
        focusTitleView?.text = snapshot.title
        if (snapshot.mode == WidgetMode.ACTIVE) {
            val remaining = (snapshot.endMs - now).coerceAtLeast(0L)
            val start = if (snapshot.startMs in 1 until snapshot.endMs) snapshot.startMs else now
            val total = (snapshot.endMs - start).coerceAtLeast(1L)
            val fraction = ((total - remaining).toFloat() / total.toFloat()).coerceIn(0f, 1f)
            val dayStats = if (snapshot.totalToday > 0) " · ${snapshot.doneToday} of ${snapshot.totalToday}" else ""
            focusHeaderView?.text = "Now$dayStats"
            val following = snapshot.followingTitle?.let { " · Next: $it" }.orEmpty()
            focusSubtitleView?.text = "${formatDuration(remaining)} left$following"
            focusProgressTrack?.visibility = View.VISIBLE
            focusProgressTrack?.post {
                val track = focusProgressTrack ?: return@post
                val fill = focusProgressFill ?: return@post
                fill.layoutParams = (fill.layoutParams as FrameLayout.LayoutParams).also {
                    it.width = (track.width * fraction.coerceIn(0.02f, 1f)).toInt()
                }
                fill.requestLayout()
            }
        } else {
            focusHeaderView?.text = "Up next"
            val at = android.text.format.DateFormat.getTimeFormat(this).format(Date(snapshot.startMs))
            focusSubtitleView?.text = "Starts at $at · in ${formatDuration(snapshot.startMs - now)}"
            focusProgressTrack?.visibility = View.GONE
        }
        card.visibility = View.VISIBLE
    }

    private fun buildTodaysLimits(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = dp(16) }
        }
        allowanceContainer = container
        return container
    }

    /** Opens the drawer already filtered to the apps with a limit today. */
    private fun openDrawerLimited() {
        openDrawer()
        handler.post {
            if (!isDrawerOpen) return@post
            drawerFilter = DrawerFilter.LIMITED_TODAY
            renderDrawerChips()
            refreshDrawerItems()
        }
    }

    private fun limitColor(fractionLeft: Float): Int = when {
        fractionLeft > 0.5f -> GREEN_OK
        fractionLeft > 0.2f -> AMBER
        else -> RED_BLOCK
    }

    /** Draws "how much is left" as a ring. */
    private inner class RingView(
        private val fractionLeft: Float,
        private val color: Int,
    ) : View(this@LauncherActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(4).toFloat()
            strokeCap = Paint.Cap.ROUND
        }
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            val inset = paint.strokeWidth / 2f + 1f
            rect.set(inset, inset, width - inset, height - inset)
            paint.color = Color.parseColor("#33FFFFFF")
            canvas.drawArc(rect, 0f, 360f, false, paint)
            paint.color = color
            canvas.drawArc(rect, -90f, 360f * fractionLeft.coerceIn(0.02f, 1f), false, paint)
        }
    }

    private fun refreshTodaysLimits() {
        val container = allowanceContainer ?: return
        container.removeAllViews()
        val cards = loadAllowanceCardData()
        if (cards.isEmpty()) {
            container.visibility = View.GONE
            pruneFrosted()
            return
        }
        val style = limitsStyleValue()
        // No frame and no heading: each row already says which app and how much is left, so the
        // limits sit quietly on the wallpaper instead of adding another box to the home screen.
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        // Rings scroll sideways, so they take everything. Bars and the list are capped, with the
        // ones closest to running out first, so a long list never makes the whole home scroll.
        val capped = style != "rings" && cards.size > MAX_HOME_LIMITS
        val shown = if (capped) cards.sortedBy { it.fraction }.take(MAX_HOME_LIMITS) else cards
        when (style) {
            "rings" -> card.addView(buildLimitRings(shown))
            "list" -> card.addView(buildLimitList(shown))
            else -> card.addView(buildLimitBars(shown))
        }
        if (capped) {
            card.addView(label("+${cards.size - shown.size} more · tap to see all", 12f, TEXT_DIM).apply {
                setPadding(0, dp(10), 0, dp(4))
                isClickable = true
                setOnClickListener { openDrawerLimited() }
            })
        }
        container.addView(card)
        container.visibility = View.VISIBLE
        pruneFrosted()
    }

    /** Default: icon, name and what is left, with a thin bar that shrinks as the allowance is used. */
    private fun buildLimitBars(cards: List<AllowanceCardData>): View {
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        cards.forEachIndexed { index, allowance ->
            val color = limitColor(allowance.fraction)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                if (index > 0) setPadding(0, dp(12), 0, 0)
            }
            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(getRoundIcon(allowance.pkg))
            })
            top.addView(label(allowance.label, 15f, TEXT_PRIMARY).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.leftMargin = dp(12) }
            })
            top.addView(label(
                if (allowance.approximate) "~${allowance.displayText}" else allowance.displayText,
                13f,
                color,
                bold = true,
            ).apply { gravity = Gravity.END })
            row.addView(top)
            val track = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = roundedBackground(Color.parseColor("#26FFFFFF"), Color.TRANSPARENT, 3)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(5),
                ).also { it.topMargin = dp(8) }
            }
            val left = allowance.fraction.coerceIn(0f, 1f)
            track.addView(View(this).apply {
                background = roundedBackground(color, Color.TRANSPARENT, 3)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, max(left, 0.001f))
            })
            track.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, max(1f - left, 0.001f))
            })
            row.addView(track)
            list.addView(row)
        }
        return NestedScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                if (cards.size > 3) dp(66 * 3) else LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            isNestedScrollingEnabled = true
            addView(list)
        }
    }

    /** A row of rings: the app icon inside, the time left underneath. Scrolls sideways. */
    private fun buildLimitRings(cards: List<AllowanceCardData>): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        cards.forEach { allowance ->
            val color = limitColor(allowance.fraction)
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(dp(76), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val ringBox = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(58), dp(58))
            }
            ringBox.addView(RingView(allowance.fraction, color), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ))
            ringBox.addView(ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(getRoundIcon(allowance.pkg))
            }, FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER))
            item.addView(ringBox)
            item.addView(label(allowance.label, 11f, TEXT_DIM).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = dp(6) }
            })
            item.addView(label(
                if (allowance.approximate) "~${allowance.displayText}" else allowance.displayText,
                11f,
                color,
                bold = true,
            ).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
            })
            row.addView(item)
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(row)
        }
    }

    /** Plain text rows, as before. */
    private fun buildLimitList(cards: List<AllowanceCardData>): View {
        val innerList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        cards.forEachIndexed { index, allowance ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                if (index > 0) setPadding(0, dp(6), 0, 0)
            }
            row.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(getRoundIcon(allowance.pkg))
            })
            row.addView(label(allowance.label, 15f, TEXT_PRIMARY).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.leftMargin = dp(12) }
            })
            row.addView(label(formatUsedFull(allowance), 13f, TEXT_DIM).apply {
                gravity = Gravity.END
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            })
            innerList.addView(row)
        }
        return NestedScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52 * 3 + 16),
            )
            isNestedScrollingEnabled = true
            addView(innerList)
        }
    }

    // ── Dock (always pinned to the bottom) ─────────────────────────────────────

    private fun buildDock(): LinearLayout {
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            setPadding(0, dp(10), 0, 0)
        }
        dockView = dock
        populateDock()
        return dock
    }

    private fun populateDock() {
        val dock = dockView ?: return
        dock.removeAllViews()
        val withSearch = searchStyleValue() == "bar"
        if (withSearch) dock.addView(buildSearchBar())
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(if (withSearch) 18 else 0) }
        }
        dockActionsRow = row
        dock.addView(row)
        refreshDockActions()
    }

    private fun buildSearchBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52),
            )
            setPadding(dp(18), 0, dp(18), 0)
            isClickable = true
            isFocusable = true
            contentDescription = "Search apps"
            setOnClickListener { openDrawer(focusSearch = true) }
            homeLongPress?.let { setOnLongClickListener(it) }
        }
        frost(bar, 26)
        bar.foreground = rippleForeground(26)
        bar.addView(searchGlyph(TEXT_DIM))
        bar.addView(label("Search apps", SIZE_SEARCH_HINT + 1f, TEXT_DIM).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.leftMargin = dp(12) }
        })
        return bar
    }

    private fun refreshDockActions() {
        val row = dockActionsRow ?: return
        row.removeAllViews()
        if (allAppsButtonOn()) {
            row.addView(dockAction("All apps", gridGlyph()) { openDrawer() })
        }
        // FocusFlow's own button is deliberately not removable: it is the way back into the app.
        row.addView(dockAction("FocusFlow", focusFlowGlyph()) { openFocusFlow() })
        pruneFrosted()
    }

    private fun dockAction(text: String, glyph: View, onClick: () -> Unit): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also {
                it.leftMargin = dp(14)
                it.rightMargin = dp(14)
            }
            minimumWidth = dp(72)
            isClickable = true
            isFocusable = true
            contentDescription = text
            setOnClickListener { onClick() }
            // Long-press anywhere on the home screen, dock included, opens arrange mode.
            homeLongPress?.let { setOnLongClickListener(it) }
        }
        val circle = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(58))
        }
        frost(circle, 29)
        circle.addView(glyph, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ))
        column.addView(circle)
        column.addView(label(text, SIZE_HOME_LABEL, TEXT_DIM).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(6) }
        })
        // Press feedback: the whole button dips slightly while pressed.
        column.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
        return column
    }

    private fun gridGlyph(): View = object : View(this) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT_PRIMARY }
        override fun onDraw(canvas: Canvas) {
            val gap = dp(8).toFloat()
            val r = dp(2).toFloat() + 0.5f
            val startX = width / 2f - gap
            val startY = height / 2f - gap
            for (row in 0..2) for (col in 0..2) {
                canvas.drawCircle(startX + col * gap, startY + row * gap, r, paint)
            }
        }
    }.apply { layoutParams = FrameLayout.LayoutParams(dp(26), dp(26)) }

    private fun focusFlowGlyph(): View = ImageView(this).apply {
        layoutParams = FrameLayout.LayoutParams(dp(30), dp(30))
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageDrawable(getRoundIcon(ownPackage))
    }


    // ── App drawer ─────────────────────────────────────────────────────────────

    private var appliedListLayout: Boolean? = null
    private var customizeSelectionView: TextView? = null

    private fun openDrawer(focusSearch: Boolean = false) {
        if (isDrawerOpen || !::rootFrame.isInitialized) return
        isDrawerOpen = true
        isEditMode = false
        drawerFilter = drawerSections().first()
        focusedDrawerPackage = null
        appliedListLayout = null
        drawerBlockedCount = getBlockedPackages().size
        drawerAllApps = loadDrawerApps()
        reloadDrawerLimits()

        val overlay = FrameLayout(this).apply {
            layoutParams = matchParent()
            setBackgroundColor(Color.BLACK)
            isClickable = true
            alpha = if (animationsEnabled()) 0f else 1f
        }
        val backdrop = ImageView(this).apply {
            layoutParams = matchParent()
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = wallpaperFade()
        }
        drawerBackdrop = backdrop
        bindBlurredBackdrop(backdrop)
        overlay.addView(backdrop)
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = matchParent()
            setPadding(dp(20), barTop + dp(12), dp(20), barBottom + dp(8))
        }
        ViewCompat.setOnApplyWindowInsetsListener(sheet) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime(),
            )
            v.setPadding(dp(20), bars.top + dp(12), dp(20), bars.bottom + dp(8))
            insets
        }
        drawerSheet = sheet

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44),
            ).also { it.bottomMargin = dp(8) }
        }
        drawerHeader = header
        sheet.addView(header)

        val search = EditText(this).apply {
            hint = "Search apps"
            setHintTextColor(TEXT_MUTED)
            setTextColor(TEXT_PRIMARY)
            textSize = 16f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(dp(18), 0, dp(18), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48),
            ).also { it.bottomMargin = dp(12) }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = refreshDrawerItems()
            })
            setOnEditorActionListener { _, _, _ ->
                launchSingleSearchResult()
                true
            }
        }
        frost(search, 24)
        drawerSearchInput = search
        sheet.addView(search)

        val chipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = dp(10) }
        }
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chipScroll.addView(chipRow)
        drawerChipRow = chipRow
        sheet.addView(chipScroll)

        val recycler = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        drawerRecycler = recycler
        attachDrawerDragSupport(recycler)
        sheet.addView(recycler)

        overlay.addView(sheet)
        val customize = buildCustomizeDrawerSheet()
        drawerCustomizeSheet = customize
        overlay.addView(customize)
        drawerOverlay = overlay
        rootFrame.addView(overlay)
        sheet.requestApplyInsets()

        renderDrawerHeader()
        renderDrawerChips()
        applyDrawerLayout()

        if (animationsEnabled()) {
            overlay.animate().alpha(1f).setDuration(180).start()
        }
        if (focusSearch) {
            search.postDelayed({
                search.requestFocus()
                showKeyboard(search)
            }, 160)
        }
    }

    private fun closeDrawer(animate: Boolean = true) {
        val overlay = drawerOverlay ?: return
        isDrawerOpen = false
        hideKeyboard()
        val remove = {
            rootFrame.removeView(overlay)
            drawerOverlay = null
            drawerBackdrop = null
            drawerSheet = null
            drawerHeader = null
            drawerChipRow = null
            drawerRecycler = null
            drawerSearchInput = null
            drawerCustomizeSheet = null
            customizeSelectionView = null
            drawerAdapter = null
            drawerTouchHelper = null
            appliedListLayout = null
            isEditMode = false
            focusedDrawerPackage = null
            pruneFrosted()
        }
        if (animate && animationsEnabled()) {
            overlay.animate().alpha(0f).setDuration(160).withEndAction(remove).start()
        } else {
            remove()
        }
    }

    private fun showKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val token = (drawerSearchInput ?: rootFrame).windowToken
        imm?.hideSoftInputFromWindow(token, 0)
    }

    /** Pressing Go/Enter in the search box opens the app if exactly one matches. */
    private fun launchSingleSearchResult() {
        val items = drawerAdapter?.currentItems().orEmpty()
        if (items.size == 1) {
            val pkg = items.first().packageName
            closeDrawer()
            if (pkg in getBlockedPackages()) launchBlockOverlay(pkg) else launchApp(pkg)
        } else {
            hideKeyboard()
        }
    }

    private fun renderDrawerHeader() {
        val header = drawerHeader ?: return
        header.removeAllViews()
        header.addView(label(if (isEditMode) "Edit mode" else "Apps", 22f, TEXT_PRIMARY, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (isEditMode) {
            header.addView(drawerHeaderButton("Cancel", "Cancel editing") { cancelEditMode() })
            header.addView(drawerHeaderButton("Done", "Save changes") { finishEditMode() })
        } else {
            header.addView(drawerHeaderButton("✎", "Edit apps") { enterEditMode() })
            header.addView(drawerHeaderButton("⋮", "Drawer options") { showDrawerMenu() })
        }
        pruneFrosted()
    }

    private fun drawerHeaderButton(text: String, description: String, onClick: () -> Unit): View {
        val view = TextView(this).apply {
            this.text = text
            textSize = if (text.length <= 1) 20f else 14f
            gravity = Gravity.CENTER
            setTextColor(TEXT_PRIMARY)
            minWidth = dp(40)
            setPadding(dp(if (text.length <= 1) 0 else 16), 0, dp(if (text.length <= 1) 0 else 16), 0)
            contentDescription = description
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(40),
            ).also { it.leftMargin = dp(8) }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        frost(view, 20)
        view.foreground = rippleForeground(20)
        return view
    }

    /** The sections shown above the apps, in their usual order. Never empty. */
    private fun drawerSections(): List<DrawerFilter> {
        val saved = prefs.getString(PREF_DRAWER_SECTIONS, null)
            ?.split(',')
            ?.mapNotNull { name -> DrawerFilter.values().firstOrNull { it.name == name.trim() } }
            ?.toSet()
        val chosen = DrawerFilter.values().filter { saved == null || it in saved }
        return chosen.ifEmpty { listOf(DrawerFilter.ALL) }
    }

    private fun clampDrawerFilter() {
        val sections = drawerSections()
        if (drawerFilter !in sections) drawerFilter = sections.first()
    }

    private fun showDrawerSectionsSheet() {
        showSheet { panel ->
            panel.addView(label("Drawer sections", 20f, TEXT_PRIMARY, bold = true))
            panel.addView(label(
                "Remove the ones you don't use. With only one left, the row of sections is hidden.",
                12f,
                TEXT_DIM,
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = dp(2); it.bottomMargin = dp(10) }
            })
            val names = listOf(
                DrawerFilter.ALL to "All apps",
                DrawerFilter.FOCUS_TOOLS to "Focus tools",
                DrawerFilter.LIMITED_TODAY to "Limited time",
                DrawerFilter.BLOCKED to "Blocked",
            )
            val current = drawerSections()
            names.forEach { (filter, name) ->
                val on = filter in current
                panel.addView(sheetRow(if (on) "✓  $name" else "     $name") {
                    val next = if (on) current - filter else current + filter
                    if (next.isEmpty()) {
                        Toast.makeText(this, "Keep at least one section.", Toast.LENGTH_SHORT).show()
                        return@sheetRow
                    }
                    prefs.edit().putString(PREF_DRAWER_SECTIONS, next.joinToString(",") { it.name }).apply()
                    clampDrawerFilter()
                    renderDrawerChips()
                    refreshDrawerItems()
                    dismissSheet(animate = false)
                    showDrawerSectionsSheet()
                })
            }
            panel.addView(sheetRow("Done", tint = TEXT_DIM) { dismissSheet() })
        }
    }

    private fun renderDrawerChips() {
        val row = drawerChipRow ?: return
        row.removeAllViews()
        val sections = drawerSections()
        // With a single section there is nothing to switch between, so the row (and the name
        // of the section) is not shown at all.
        (row.parent as? View)?.visibility = if (sections.size > 1 || isEditMode) View.VISIBLE else View.GONE
        val labels = listOf(
            DrawerFilter.ALL to "ALL APPS",
            DrawerFilter.FOCUS_TOOLS to "FOCUS TOOLS",
            DrawerFilter.LIMITED_TODAY to "LIMITED TIME",
            DrawerFilter.BLOCKED to "BLOCKED ($drawerBlockedCount)",
        ).filter { (filter, _) -> filter in sections }
        labels.forEach { (filter, text) ->
            val active = filter == drawerFilter
            val chip = TextView(this).apply {
                this.text = text
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(if (active) Color.WHITE else TEXT_DIM)
                setPadding(dp(14), 0, dp(14), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(36),
                ).also { it.rightMargin = dp(8) }
                setOnClickListener {
                    if (isEditMode) return@setOnClickListener
                    drawerFilter = filter
                    renderDrawerChips()
                    refreshDrawerItems()
                }
            }
            if (active) {
                chip.background = roundedBackground(ACCENT, Color.TRANSPARENT, 18)
            } else {
                frost(chip, 18)
            }
            row.addView(chip)
        }
        pruneFrosted()
    }

    /** What is left of each limited app today, kept so typing in search does not hit the ledger. */
    private fun reloadDrawerLimits() {
        drawerLimitInfo = loadAllowanceCardData().associate {
            it.pkg to ((it.chipText.ifBlank { it.displayText }) to it.fraction)
        }
    }

    private fun refreshDrawerItems() {
        val adapter = drawerAdapter ?: return
        val query = drawerSearchInput?.text?.toString()?.lowercase(Locale.getDefault())?.trim().orEmpty()
        val filtered = filterDrawerApps(drawerAllApps, drawerFilter).filter {
            query.isBlank() ||
                it.label.lowercase(Locale.getDefault()).contains(query) ||
                it.packageName.lowercase(Locale.getDefault()).contains(query)
        }
        adapter.setItems(filtered)
    }

    private fun filterDrawerApps(
        all: List<DrawerItem.App>,
        filter: DrawerFilter,
    ): List<DrawerItem.App> {
        return when (filter) {
            DrawerFilter.ALL -> all
            DrawerFilter.FOCUS_TOOLS -> {
                val tools = focusToolPackages()
                all.filter { it.packageName in tools }
            }
            DrawerFilter.LIMITED_TODAY -> all.filter { it.packageName in drawerLimitInfo }
            DrawerFilter.BLOCKED -> {
                val blocked = getBlockedPackages()
                all.filter { it.packageName in blocked }
            }
        }
    }

    /** Builds the grid or list adapter for the current layout choice. */
    private fun applyDrawerLayout() {
        val recycler = drawerRecycler ?: return
        val list = drawerIsList()
        if (appliedListLayout == list && drawerAdapter != null) return
        appliedListLayout = list
        val adapter = DrawerAdapter(list)
        drawerAdapter = adapter
        while (recycler.itemDecorationCount > 0) recycler.removeItemDecorationAt(0)
        if (list) {
            recycler.layoutManager = LinearLayoutManager(this)
            recycler.addItemDecoration(object : RecyclerView.ItemDecoration() {
                private val paint = Paint().apply { color = Color.parseColor("#18FFFFFF") }
                override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                    for (i in 0 until parent.childCount - 1) {
                        val child = parent.getChildAt(i)
                        val y = child.bottom.toFloat()
                        c.drawRect(child.left + dp(60).toFloat(), y, child.right.toFloat(), y + 1f, paint)
                    }
                }
            })
        } else {
            val columns = prefs.getInt(PREF_DRAWER_COLUMNS, 4).coerceIn(4, 5)
            recycler.layoutManager = GridLayoutManager(this, columns)
        }
        recycler.adapter = adapter
        refreshDrawerItems()
    }

    private fun attachDrawerDragSupport(recycler: RecyclerView) {
        val callback = object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ): Int {
                val flags = if (drawerIsList()) {
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN
                } else {
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                }
                return makeMovementFlags(flags, 0)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val adapter = drawerAdapter ?: return false
                focusedDrawerPackage = adapter.currentItems()
                    .getOrNull(target.bindingAdapterPosition)?.packageName
                return adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
            override fun isLongPressDragEnabled(): Boolean = isEditMode
            override fun isItemViewSwipeEnabled(): Boolean = false
        }
        drawerTouchHelper = ItemTouchHelper(callback).also { it.attachToRecyclerView(recycler) }
    }

    // ── Edit mode ──────────────────────────────────────────────────────────────

    private fun enterEditMode() {
        if (isEditMode || !isDrawerOpen) return
        isEditMode = true
        drawerFilter = DrawerFilter.ALL
        drawerSearchInput?.apply {
            setText("")
            isEnabled = false
            alpha = 0.5f
        }
        hideKeyboard()
        editDraftScale = prefs.getFloat(PREF_DRAWER_SCALE, 1f).coerceIn(0.8f, 1.2f)
        editDraftColumns = prefs.getInt(PREF_DRAWER_COLUMNS, 4).coerceIn(4, 5)
        drawerAllApps = loadDrawerApps()
        val overlay = drawerOverlay ?: return
        drawerCustomizeSheet?.let { overlay.removeView(it) }
        val customize = buildCustomizeDrawerSheet()
        customize.visibility = View.VISIBLE
        overlay.addView(customize)
        drawerCustomizeSheet = customize
        drawerRecycler?.setPadding(0, 0, 0, dp(if (drawerIsList()) 250 else 330))
        renderDrawerChips()
        renderDrawerHeader()
        drawerAdapter?.setIconScale(editDraftScale)
        refreshDrawerItems()
    }

    private fun leaveEditMode() {
        isEditMode = false
        focusedDrawerPackage = null
        drawerCustomizeSheet?.visibility = View.GONE
        drawerRecycler?.setPadding(0, 0, 0, 0)
        drawerSearchInput?.apply {
            isEnabled = true
            alpha = 1f
        }
        clampDrawerFilter()
        renderDrawerChips()
        renderDrawerHeader()
    }

    private fun cancelEditMode() {
        leaveEditMode()
        drawerAdapter?.setIconScale(prefs.getFloat(PREF_DRAWER_SCALE, 1f))
        (drawerRecycler?.layoutManager as? GridLayoutManager)?.spanCount =
            prefs.getInt(PREF_DRAWER_COLUMNS, 4).coerceIn(4, 5)
        drawerAllApps = loadDrawerApps()
        refreshDrawerItems()
    }

    private fun finishEditMode() {
        // Edit mode always shows the whole, unfiltered list (search is disabled and the
        // filter is ALL), so this is the complete order the user arranged.
        val ordered = drawerAdapter?.currentItems()?.map { it.packageName }.orEmpty()
        prefs.edit()
            .putString(PREF_DRAWER_ORDER, JSONArray(ordered).toString())
            .putFloat(PREF_DRAWER_SCALE, editDraftScale)
            .putInt(PREF_DRAWER_COLUMNS, editDraftColumns)
            .apply()
        (drawerRecycler?.layoutManager as? GridLayoutManager)?.spanCount = editDraftColumns
        leaveEditMode()
        // Re-read from storage so what is on screen is exactly what was saved.
        drawerAllApps = loadDrawerApps()
        refreshDrawerItems()
    }

    private fun buildCustomizeDrawerSheet(): LinearLayout {
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            elevation = dp(12).toFloat()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).also {
                it.gravity = Gravity.BOTTOM
                it.setMargins(dp(12), 0, dp(12), barBottom + dp(12))
            }
            visibility = View.GONE
            setPadding(dp(20), dp(12), dp(20), dp(16))
            isClickable = true
        }
        frost(sheet, 28, extraDark = 0.45f)
        sheet.addView(label("Customize drawer", 20f, TEXT_PRIMARY, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = dp(4) }
        })
        val selection = label("", 13f, TEXT_DIM).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = dp(10) }
        }
        customizeSelectionView = selection
        sheet.addView(selection)
        updateCustomizeSelection()

        run {
            val scaleLabel = label("", 14f, TEXT_DIM)
            sheet.addView(scaleLabel)
            val seek = SeekBar(this).apply {
                max = 40
                progress = ((editDraftScale - 0.8f) * 100).toInt().coerceIn(0, 40)
                progressTintList = ColorStateList.valueOf(ACCENT_TEXT)
                thumbTintList = ColorStateList.valueOf(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(32),
                )
            }
            sheet.addView(seek)
            fun updateScale(progress: Int) {
                editDraftScale = (0.8f + progress / 100f).coerceIn(0.8f, 1.2f)
                scaleLabel.text = "Icon size · ${(editDraftScale * 100).toInt()}%"
                drawerAdapter?.setIconScale(editDraftScale)
            }
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    updateScale(progress)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
            updateScale(seek.progress)
        }

        if (!drawerIsList()) {
            val gridRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(44),
                ).also { it.bottomMargin = dp(10); it.topMargin = dp(6) }
            }
            gridRow.addView(customizeButton("4 across") {
                editDraftColumns = 4
                (drawerRecycler?.layoutManager as? GridLayoutManager)?.spanCount = 4
            })
            gridRow.addView(customizeButton("5 across") {
                editDraftColumns = 5
                (drawerRecycler?.layoutManager as? GridLayoutManager)?.spanCount = 5
            })
            sheet.addView(gridRow)
        }

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44),
            ).also { it.topMargin = dp(6) }
        }
        actionRow.addView(customizeButton("Hide app") {
            val pkg = focusedDrawerPackage
            if (pkg == null) showNoFocusedAppMessage() else hideFromEdit(pkg)
        })
        actionRow.addView(customizeButton("Lock app") {
            focusedDrawerPackage?.let { openAlwaysOnPicker(it) } ?: showNoFocusedAppMessage()
        })
        sheet.addView(actionRow)
        return sheet
    }

    private fun updateCustomizeSelection() {
        val pkg = focusedDrawerPackage
        val name = pkg?.let { p -> drawerAllApps.firstOrNull { it.packageName == p }?.label }
        customizeSelectionView?.text =
            if (name != null) "Selected: $name" else "Long-press and drag to reorder. Tap an app to select it."
    }

    private fun customizeButton(text: String, onClick: () -> Unit): View {
        val view = TextView(this).apply {
            this.text = text
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f,
            ).also { it.leftMargin = dp(4); it.rightMargin = dp(4) }
            setOnClickListener { onClick() }
        }
        view.background = roundedBackground(Color.parseColor("#26FFFFFF"), Color.parseColor("#33FFFFFF"), 18)
        view.foreground = rippleForeground(18)
        return view
    }

    private fun showNoFocusedAppMessage() {
        Toast.makeText(this, "Tap an app first to select it.", Toast.LENGTH_SHORT).show()
    }

    private fun hideFromEdit(pkg: String) {
        // Keep the order being edited: drop the app from the draft instead of reloading.
        val draft = drawerAdapter?.currentItems().orEmpty()
        setAppHidden(pkg, true)
        drawerAllApps = draft.filter { it.packageName != pkg }
        focusedDrawerPackage = null
        updateCustomizeSelection()
        refreshDrawerItems()
    }

    private fun openAlwaysOnPicker(pkg: String) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("focusflow://always-on?package=${Uri.encode(pkg)}"))
                    .setPackage(packageName),
            )
        } catch (_: Exception) {
            try {
                startActivity(Intent(this, com.tbtechs.focusflow.MainActivity::class.java))
            } catch (_: Exception) {
            }
        }
    }

    // ── Bottom sheets: app actions and drawer options ──────────────────────────

    /**
     * Bottom sheets dim what is behind them. Once the wallpaper is showing that dimming is cut
     * to a third, so opening a sheet does not turn the launcher dark. At Glassiness 0 the
     * launcher is plain black and the original dimming is kept.
     */
    private fun softenScrim(color: Int): Int {
        if (glassLevel <= 0) return color
        val alpha = (Color.alpha(color) * 0.33f).toInt()
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun showSheet(
        scrimColor: Int = Color.parseColor("#80000000"),
        build: (LinearLayout) -> Unit,
    ) {
        dismissSheet(animate = false)
        hideKeyboard()
        val overlay = FrameLayout(this).apply {
            layoutParams = matchParent()
            setBackgroundColor(softenScrim(scrimColor))
            isClickable = true
            alpha = if (animationsEnabled()) 0f else 1f
            setOnClickListener { dismissSheet() }
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(16))
            isClickable = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ).also { it.setMargins(dp(12), 0, dp(12), barBottom + dp(12)) }
        }
        frost(panel, 28, extraDark = 0.45f)
        panel.addView(View(this).apply {
            background = roundedBackground(Color.parseColor("#66FFFFFF"), Color.TRANSPARENT, 4)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(4)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.bottomMargin = dp(12)
            }
        })
        build(panel)
        overlay.addView(panel)
        ViewCompat.setOnApplyWindowInsetsListener(overlay) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime(),
            )
            (panel.layoutParams as? FrameLayout.LayoutParams)?.let {
                it.bottomMargin = bars.bottom + dp(12)
                panel.layoutParams = it
            }
            insets
        }
        rootFrame.addView(overlay)
        overlay.requestApplyInsets()
        sheetOverlay = overlay
        if (animationsEnabled()) {
            overlay.animate().alpha(1f).setDuration(150).start()
            panel.translationY = dp(36).toFloat()
            panel.animate().translationY(0f).setDuration(200).start()
        }
    }

    private fun dismissSheet(animate: Boolean = true) {
        val overlay = sheetOverlay ?: return
        sheetOverlay = null
        val remove = {
            rootFrame.removeView(overlay)
            pruneFrosted()
        }
        if (animate && animationsEnabled()) {
            overlay.animate().alpha(0f).setDuration(130).withEndAction(remove).start()
        } else {
            remove()
        }
    }

    private fun sheetRow(
        text: String,
        sub: String? = null,
        tint: Int = TEXT_PRIMARY,
        onClick: () -> Unit,
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(54)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        row.foreground = rippleForeground(14)
        row.addView(label(text, 16f, tint))
        if (sub != null) row.addView(label(sub, 12f, TEXT_MUTED))
        return row
    }

    /** Long-press menu for an app. [hiddenContext] is true when opened from the Hidden apps screen. */
    private fun showAppActions(pkg: String, name: String, hiddenContext: Boolean = false) {
        showSheet { panel ->
            val head = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.bottomMargin = dp(8) }
            }
            head.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                setImageDrawable(getRoundIcon(pkg))
            })
            head.addView(label(name, 18f, TEXT_PRIMARY, bold = true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ).also { it.leftMargin = dp(12) }
            })
            panel.addView(head)

            if (hiddenContext) {
                panel.addView(sheetRow("Unhide", "Show it in the drawer and search again") {
                    dismissSheet()
                    requestUnhide(pkg, name) {
                        if (hiddenOverlay != null) renderHiddenList()
                    }
                })
            } else {
                panel.addView(sheetRow("Hide from launcher", "Find it later in ⋮ → Hidden apps") {
                    dismissSheet()
                    setAppHidden(pkg, true)
                    Toast.makeText(this, "$name hidden. Find it in ⋮ → Hidden apps.", Toast.LENGTH_SHORT).show()
                })
            }
            if (!hiddenContext) {
                val onHome = HomePinnedItems.contains(pinnedEntries(), HomePinnedItems.Kind.APP, pkg, "")
                panel.addView(sheetRow(
                    if (onHome) "Remove from home" else "Add to home",
                    "An icon on your home screen",
                ) {
                    dismissSheet()
                    if (onHome) unpinApp(pkg) else pinApp(pkg, name)
                })
            }
            val isTool = pkg in focusToolPackages()
            panel.addView(
                sheetRow(
                    if (isTool) "Remove from Focus tools" else "Add to Focus tools",
                    "Focus tools get their own filter in the drawer",
                ) {
                    dismissSheet()
                    setFocusTool(pkg, !isTool)
                },
            )
            panel.addView(sheetRow("Lock in Always-On…") {
                dismissSheet()
                openAlwaysOnPicker(pkg)
            })
        }
    }

    private fun showDrawerMenu() {
        showSheet { panel ->
            panel.addView(label("Launcher", 20f, TEXT_PRIMARY, bold = true).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.bottomMargin = dp(12) }
            })

            panel.addView(label("Drawer layout", 13f, TEXT_DIM))
            val segment = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(44),
                ).also { it.topMargin = dp(6); it.bottomMargin = dp(16) }
            }
            val isList = drawerIsList()
            fun choice(text: String, list: Boolean): View {
                val selected = list == isList
                val view = TextView(this).apply {
                    this.text = text
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(if (selected) Color.WHITE else TEXT_DIM)
                    typeface = Typeface.create(Typeface.DEFAULT, if (selected) Typeface.BOLD else Typeface.NORMAL)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                        .also { it.leftMargin = dp(4); it.rightMargin = dp(4) }
                    setOnClickListener {
                        prefs.edit().putString(PREF_DRAWER_LAYOUT, if (list) "list" else "grid").apply()
                        dismissSheet()
                        applyDrawerLayout()
                    }
                }
                view.background = if (selected) {
                    roundedBackground(ACCENT, Color.TRANSPARENT, 18)
                } else {
                    roundedBackground(Color.parseColor("#26FFFFFF"), Color.parseColor("#33FFFFFF"), 18)
                }
                return view
            }
            segment.addView(choice("Grid", false))
            segment.addView(choice("List", true))
            panel.addView(segment)

            val glassLabel = label("Glassiness · $glassLevel%", 13f, TEXT_DIM)
            panel.addView(glassLabel)
            val seek = SeekBar(this).apply {
                max = 100
                progress = glassLevel
                progressTintList = ColorStateList.valueOf(ACCENT_TEXT)
                thumbTintList = ColorStateList.valueOf(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(36),
                ).also { it.topMargin = dp(4) }
            }
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    glassLevel = progress
                    glassLabel.text = if (progress == 0) "Glassiness · 0% (plain black)" else "Glassiness · $progress%"
                    applyGlassLive()
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) {
                    writeLookPrefs { putInt(PREF_GLASS_LEVEL, glassLevel) }
                }
            })
            panel.addView(seek)

            panel.addView(sheetRow("Sections", "All apps, Focus tools, Limited time, Blocked") {
                dismissSheet()
                showDrawerSectionsSheet()
            })
            panel.addView(sheetRow("Customize home", "Clock, widgets, wallpaper and dock") {
                dismissSheet()
                closeDrawer(animate = false)
                showHomeCustomizeSheet()
            })
            val hiddenCount = hiddenInstalledApps().size
            panel.addView(sheetRow(
                if (hiddenCount > 0) "Hidden apps · $hiddenCount" else "Hidden apps",
                "Apps you hid from the drawer",
            ) {
                dismissSheet()
                openHiddenScreen()
            }.also {
                (it.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(10)
            })
        }
    }

    // ── Customize home ─────────────────────────────────────────────────────────

    private fun chipView(text: String, selected: Boolean, onClick: () -> Unit): View {
        val chip = TextView(this).apply {
            this.text = text
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, if (selected) Typeface.BOLD else Typeface.NORMAL)
            gravity = Gravity.CENTER
            setTextColor(if (selected) Color.WHITE else TEXT_DIM)
            setPadding(dp(14), 0, dp(14), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(36),
            ).also { it.rightMargin = dp(8) }
            setOnClickListener { onClick() }
        }
        chip.background = if (selected) {
            roundedBackground(ACCENT, Color.TRANSPARENT, 18)
        } else {
            roundedBackground(Color.parseColor("#26FFFFFF"), Color.parseColor("#33FFFFFF"), 18)
        }
        return chip
    }

    /** A titled row of choices that saves to [key] and rebuilds the home widgets as you tap. */
    private fun optionRow(
        title: String,
        key: String,
        options: List<Pair<String, String>>,
        default: String,
        onChanged: () -> Unit = { rebuildHomeSections() },
    ): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(14) }
        }
        wrap.addView(label(title, 13f, TEXT_DIM))
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(6) }
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        scroller.addView(row)
        fun render() {
            row.removeAllViews()
            val stored = prefs.getString(key, null)
            val current = if (options.any { it.first == stored }) stored else default
            options.forEach { (value, optionLabel) ->
                row.addView(chipView(optionLabel, value == current) {
                    prefs.edit().putString(key, value).apply()
                    onChanged()
                    render()
                })
            }
        }
        render()
        wrap.addView(scroller)
        return wrap
    }

    private fun glassSliderBlock(): View {
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(14) }
        }
        val glassLabel = label(
            if (glassLevel == 0) "Glassiness · 0% (plain black)" else "Glassiness · $glassLevel%",
            13f,
            TEXT_DIM,
        )
        block.addView(glassLabel)
        val seek = SeekBar(this).apply {
            max = 100
            progress = glassLevel
            progressTintList = ColorStateList.valueOf(ACCENT_TEXT)
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(36),
            ).also { it.topMargin = dp(4) }
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                glassLevel = progress
                glassLabel.text =
                    if (progress == 0) "Glassiness · 0% (plain black)" else "Glassiness · $progress%"
                applyGlassLive()
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) {
                writeLookPrefs { putInt(PREF_GLASS_LEVEL, glassLevel) }
            }
        })
        block.addView(seek)
        return block
    }

    private fun wallpaperSummary(): String = when {
        wallpaperPath().isNotEmpty() -> "Your own picture"
        builtinWallpaperId().isNotEmpty() ->
            BuiltinWallpapers.byId(builtinWallpaperId())?.name ?: "Adaptive"
        else -> "Adaptive (matches your phone)"
    }

    /** Long-press the home screen to get here. Every widget has a few styles and an Off. */
    /** What a long press on empty space opens on the grid home: the three things people look for there. */
    private fun showGridEditMenu() {
        if (!::rootFrame.isInitialized) return
        showSheet(scrimColor = Color.parseColor("#33000000")) { panel ->
            panel.addView(label("Edit home", 20f, TEXT_PRIMARY, bold = true))
            panel.addView(label(
                if (gridHomeOn()) "Long-press an item to move it, or let go to resize. Drag it to the top to remove it."
                else "Long-press a widget to drag it up or down. Turn cards on or off under Style & options.",
                12f,
                TEXT_MUTED,
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = dp(2); it.bottomMargin = dp(6) }
            })
            panel.addView(sheetRow("Widgets", "Add an app widget") {
                dismissSheet()
                showWidgetPicker()
            })
            if (widgetEntries().isNotEmpty()) {
                panel.addView(sheetRow("Manage widgets", "Resize or remove an app widget") {
                    dismissSheet()
                    showManageWidgetsSheet()
                })
            }
            panel.addView(sheetRow("Wallpaper", wallpaperSummary()) {
                dismissSheet()
                showWallpaperSheet()
            })
            panel.addView(sheetRow("Style & options", "Clock, cards, glass and layout") {
                dismissSheet()
                showHomeCustomizeSheet()
            })
        }
    }

    private fun showHomeCustomizeSheet() {
        if (!::rootFrame.isInitialized) return
        showSheet(scrimColor = Color.parseColor("#33000000")) { panel ->
            panel.addView(label("Customize home", 20f, TEXT_PRIMARY, bold = true))
            panel.addView(label(
                "Changes show instantly. Long-press empty space on the home screen any time to come back.",
                12f,
                TEXT_MUTED,
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = dp(2); it.bottomMargin = dp(6) }
            })
            val scroller = ScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (resources.displayMetrics.heightPixels * 0.5f).toInt(),
                )
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            scroller.addView(body)
            panel.addView(scroller)

            body.addView(sheetRow("Wallpaper", wallpaperSummary()) {
                dismissSheet()
                showWallpaperSheet()
            })
            body.addView(glassSliderBlock())
            body.addView(optionRow(
                "Home layout", PREF_HOME_LAYOUT,
                listOf("list" to "List", "grid" to "Grid (beta)"),
                "list",
            ) {
                // The two layouts are built differently, so redraw the whole home screen.
                dismissSheet(animate = false)
                buildHomeLayout()
                showHomeCustomizeSheet()
            })
            body.addView(label(
                "Grid puts everything on a 4 by 6 grid like a stock launcher. Your List layout is kept, so you can switch back any time.",
                11f,
                TEXT_MUTED,
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = dp(4) }
            })
            body.addView(optionRow(
                "Clock", PREF_HOME_CLOCK,
                listOf("light" to "Light", "bold" to "Bold", "stacked" to "Stacked", "compact" to "Compact", "off" to "Off"),
                "light",
            ))
            body.addView(optionRow(
                "Status", PREF_HOME_STATUS,
                listOf("pill" to "Pill", "text" to "Text", "off" to "Off"),
                "pill",
            ))
            body.addView(optionRow(
                "Current task", PREF_HOME_TASK,
                listOf("card" to "Card", "compact" to "Compact", "off" to "Off"),
                "card",
            ))
            body.addView(optionRow(
                "Today's limits", PREF_HOME_LIMITS,
                listOf("bars" to "Bars", "rings" to "Rings", "list" to "List", "off" to "Off"),
                "bars",
            ))
            body.addView(optionRow(
                "Pinned apps", PREF_HOME_PINNED,
                listOf("grid" to "Show", "off" to "Hide"),
                "grid",
            ))
            body.addView(sheetRow("Add a widget", "Pick from the widgets on your phone") {
                dismissSheet()
                showWidgetPicker()
            })
            body.addView(optionRow(
                "App widgets", PREF_HOME_WIDGETS,
                listOf("show" to "Show", "off" to "Hide"),
                "show",
            ))
            body.addView(optionRow(
                "Pinned grid size", PREF_HOME_PINNED_SIZE,
                listOf("3" to "3×3", "4" to "4×4", "5" to "5×5"),
                "4",
            ))
            body.addView(optionRow(
                "Search bar", PREF_HOME_SEARCH,
                listOf("bar" to "Show", "off" to "Hide"),
                "bar",
            ))
            body.addView(optionRow(
                "All apps button", PREF_HOME_ALL_APPS,
                listOf("on" to "Show", "off" to "Hide"),
                "on",
            ))
            body.addView(label(
                "Swipe up still opens your apps if you hide the search bar or the All apps button. The FocusFlow button always stays so you can get back into the app.",
                11f,
                TEXT_MUTED,
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = dp(12) }
            })
            body.addView(sheetRow("Reset home layout", "Back to the default clock and widgets", TEXT_DIM) {
                prefs.edit()
                    .remove(PREF_HOME_CLOCK).remove(PREF_HOME_STATUS).remove(PREF_HOME_TASK)
                    .remove(PREF_HOME_LIMITS).remove(PREF_HOME_SEARCH).remove(PREF_HOME_ALL_APPS)
                    .remove(PREF_HOME_ORDER).remove(PREF_HOME_PINNED).remove(PREF_HOME_PINNED_SIZE)
                    .remove(PREF_HOME_WIDGETS).remove(HomeGrid.PREF_KEY)
                    .apply()
                dismissSheet(animate = false)
                rebuildHomeSections()
                showHomeCustomizeSheet()
            })
        }
    }

    // ── Wallpaper chooser ──────────────────────────────────────────────────────

    private fun wallpaperTile(
        title: String,
        hint: String,
        preview: Bitmap?,
        selected: Boolean,
        onClick: () -> Unit,
    ): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(100), LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.rightMargin = dp(10) }
            isClickable = true
            isFocusable = true
            contentDescription = "$title wallpaper. $hint"
            setOnClickListener { onClick() }
        }
        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(100), dp(150))
            background = roundedBackground(
                Color.parseColor("#26FFFFFF"),
                if (selected) ACCENT_TEXT else Color.parseColor("#33FFFFFF"),
                14,
            )
            clipToOutline = true
        }
        if (preview != null) {
            frame.addView(ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(preview)
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ))
        } else {
            frame.addView(label("+", 30f, TEXT_DIM), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ))
        }
        if (selected) {
            frame.addView(label("✓", 14f, Color.WHITE, bold = true).apply {
                gravity = Gravity.CENTER
                background = ovalBackground(ACCENT)
            }, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.TOP or Gravity.END).also {
                it.topMargin = dp(6)
                it.rightMargin = dp(6)
            })
        }
        tile.addView(frame)
        tile.addView(label(title, 12f, TEXT_PRIMARY, bold = true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(6) }
        })
        // The "looks best at" hint lives on the wallpaper itself, not in a separate section.
        tile.addView(label(hint, 10.5f, TEXT_MUTED).apply {
            maxLines = 2
        })
        return tile
    }

    private fun showWallpaperSheet() {
        showSheet(scrimColor = Color.parseColor("#22000000")) { panel ->
            panel.addView(label("Wallpaper", 20f, TEXT_PRIMARY, bold = true))
            panel.addView(label(
                "Drawn by the app itself, so there is nothing to download. Try the slider to see each one at its best.",
                12f,
                TEXT_MUTED,
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = dp(2); it.bottomMargin = dp(10) }
            })
            val scroller = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            scroller.addView(row)
            panel.addView(scroller)
            val previews = HashMap<String, Bitmap>()
            fun choose(builtinId: String) {
                writeLookPrefs {
                    remove(PREF_LAUNCHER_WALLPAPER)
                    if (builtinId.isEmpty()) remove(PREF_WALLPAPER_BUILTIN)
                    else putString(PREF_WALLPAPER_BUILTIN, builtinId)
                }
                Thread { try { AppModule.wallpaperStore.clear() } catch (_: Throwable) { } }.start()
                ensureBackdrop()
            }
            fun render() {
                row.removeAllViews()
                val customOn = wallpaperPath().isNotEmpty()
                val builtinOn = if (customOn) "" else builtinWallpaperId()
                // Your own photo comes first, like the Wallpaper screen in the app.
                row.addView(wallpaperTile(
                    if (customOn) "Your photo" else "+ Your photo",
                    if (customOn) "In use · tap to change" else "From your gallery",
                    null,
                    customOn,
                ) { pickWallpaperFromGallery() })
                row.addView(wallpaperTile(
                    "Adaptive", "Matches your phone · best at 70%",
                    previews.getOrPut("adaptive") { BuiltinWallpapers.adaptive(wallpaperPalette(), 90, 150) },
                    !customOn && builtinOn.isEmpty(),
                ) {
                    // Picking one offers it to the phone too; tapping the one in use offers it again.
                    if (!customOn && builtinOn.isEmpty()) return@wallpaperTile
                    choose(""); render()
                })
                BuiltinWallpapers.all.forEach { w ->
                    row.addView(wallpaperTile(
                        w.name, "Best at ${w.bestAt}%",
                        previews.getOrPut(w.id) { BuiltinWallpapers.render(w, 90, 150) },
                        builtinOn == w.id,
                    ) {
                        if (builtinOn != w.id) choose(w.id)
                        offerPhoneWallpaper()
                    })
                }
            }
            render()
            panel.addView(glassSliderBlock())
        }
    }

    private fun pickWallpaperFromGallery() {
        try {
            startActivityForResult(
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                },
                REQ_PICK_WALLPAPER,
            )
        } catch (_: Exception) {
            Toast.makeText(this, "No gallery app found.", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_BIND_WIDGET || requestCode == REQ_CONFIGURE_WIDGET) {
            val id = pendingWidgetId
            pendingWidgetId = -1
            if (id < 0) return
            if (resultCode != RESULT_OK) {
                // Cancelled at the permission or setup step: give the widget id back.
                try { widgetHost.deleteAppWidgetId(id) } catch (_: Exception) {}
                return
            }
            val info = appWidgetManager.getAppWidgetInfo(id)
            when {
                info == null -> try { widgetHost.deleteAppWidgetId(id) } catch (_: Exception) {}
                requestCode == REQ_BIND_WIDGET -> onWidgetBound(id, info)
                else -> finishAddingWidget(id)
            }
            return
        }
        if (requestCode != REQ_PICK_WALLPAPER || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        Toast.makeText(this, "Preparing wallpaper…", Toast.LENGTH_SHORT).show()
        Thread {
            // Validated and re-encoded into app-private storage, same as the setup screen does.
            val result = runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    AppModule.wallpaperStore.ingest(input).getOrThrow()
                } ?: error("The picture could not be opened.")
            }
            handler.post {
                if (isFinishing) return@post
                result.onSuccess { path ->
                    writeLookPrefs {
                        putString(PREF_LAUNCHER_WALLPAPER, path)
                        remove(PREF_WALLPAPER_BUILTIN)
                    }
                    ensureBackdrop()
                    Toast.makeText(this, "Wallpaper set.", Toast.LENGTH_SHORT).show()
                    offerPhoneWallpaper()
                }
                result.onFailure {
                    Toast.makeText(this, it.message ?: "That picture could not be used.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    /**
     * "Use it on your phone too?", the same choice as the Wallpaper screen in the app: Home and
     * lock, Home only, or not now. The warning about replacing the phone's wallpaper always
     * stays; the explanation above the buttons can be closed for good.
     */
    private fun offerPhoneWallpaper() {
        if (wallpaperPath().isEmpty() && builtinWallpaperId().isEmpty()) return
        showSheet(scrimColor = Color.parseColor("#66000000")) { panel ->
            panel.addView(label("Use it on your phone too?", 20f, TEXT_PRIMARY, bold = true))
            panel.addView(label(
                "This replaces your current phone wallpaper, and FocusFlow can't bring the old one back.",
                13f,
                TEXT_DIM,
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = dp(6); it.bottomMargin = dp(8) }
            })
            if (!prefs.getBoolean(PREF_PHONE_NOTE_DISMISSED, false)) {
                val note = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.TOP
                    background = roundedBackground(Color.parseColor("#14FFFFFF"), Color.parseColor("#22FFFFFF"), 12)
                    setPadding(dp(12), dp(8), dp(4), dp(8))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).also { it.bottomMargin = dp(8) }
                }
                note.addView(label(
                    "The same picture on your home and lock screens makes Recents and app switching match FocusFlow.",
                    12f,
                    TEXT_DIM,
                ).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        .also { it.topMargin = dp(4) }
                })
                note.addView(label("✕", 14f, TEXT_DIM).apply {
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                    contentDescription = "Dismiss"
                    isClickable = true
                    background = rippleForeground(20)
                    setOnClickListener {
                        // Closed for good: remembered, so it does not come back.
                        prefs.edit().putBoolean(PREF_PHONE_NOTE_DISMISSED, true).apply()
                        (note.parent as? ViewGroup)?.removeView(note)
                    }
                })
                panel.addView(note)
            }
            panel.addView(sheetRow("Home + lock screen") {
                applyPhoneWallpaper(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            })
            panel.addView(sheetRow("Home screen only") {
                applyPhoneWallpaper(WallpaperManager.FLAG_SYSTEM)
            })
            panel.addView(sheetRow("Not now", tint = TEXT_DIM) { dismissSheet() })
        }
    }

    private fun applyPhoneWallpaper(flags: Int) {
        dismissSheet()
        val path = wallpaperPath()
        val builtinId = builtinWallpaperId()
        val appContext = applicationContext
        Toast.makeText(this, "Setting wallpaper…", Toast.LENGTH_SHORT).show()
        Thread {
            // Same routine as the Wallpaper screen: screen-sized picture, home and lock set
            // separately, and each one checked rather than assumed.
            val bitmap = runCatching { PhoneWallpaper.screenBitmap(appContext, path, builtinId) }.getOrNull()
            val result = if (bitmap == null) null else {
                runCatching { PhoneWallpaper.apply(appContext, bitmap, flags) }.getOrNull()
            }
            val picker = if (bitmap != null && result != null && result.needsSystemPicker) {
                runCatching { PhoneWallpaper.systemPickerIntent(appContext, bitmap) }.getOrNull()
            } else {
                null
            }
            handler.post {
                if (isFinishing) return@post
                if (result == null) {
                    Toast.makeText(this, "Couldn't set the phone wallpaper.", Toast.LENGTH_SHORT).show()
                    return@post
                }
                Toast.makeText(this, PhoneWallpaper.message(result), Toast.LENGTH_LONG).show()
                if (picker != null) showSystemPickerSheet(picker)
            }
        }.start()
    }

    /** Offered when the phone did not clearly accept the lock screen wallpaper. */
    private fun showSystemPickerSheet(picker: Intent) {
        showSheet(scrimColor = Color.parseColor("#66000000")) { panel ->
            panel.addView(label("Finish in the system picker", 20f, TEXT_PRIMARY, bold = true))
            panel.addView(label(
                "Your phone's own wallpaper screen opens with this picture. Choose Home, Lock or both there.",
                13f,
                TEXT_DIM,
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = dp(6); it.bottomMargin = dp(8) }
            })
            panel.addView(sheetRow("Open system picker") {
                dismissSheet()
                try {
                    startActivity(picker)
                } catch (_: Exception) {
                    Toast.makeText(this, "Couldn't open the system picker.", Toast.LENGTH_SHORT).show()
                }
            })
            panel.addView(sheetRow("Not now", tint = TEXT_DIM) { dismissSheet() })
        }
    }

    // ── Unhiding takes a little effort on purpose ──────────────────────────────

    /** A standalone block is running right now. */
    private fun standaloneBlockRunning(): Boolean =
        prefs.getBoolean(PREF_SA_ACTIVE, false) && prefs.getLong(PREF_SA_UNTIL, 0L) > System.currentTimeMillis()

    /**
     * Hiding is instant, but unhiding is not: it is refused while a standalone block is
     * running, and asks for the defense password when one is set. Otherwise hiding an app
     * would be no barrier at all.
     */
    private fun requestUnhide(pkg: String, name: String, onDone: () -> Unit) {
        if (standaloneBlockRunning()) {
            Toast.makeText(
                this,
                "Apps can't be unhidden while a block is running.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        val pinSet = try { PinManager(applicationContext).isPinSet() } catch (_: Throwable) { false }
        if (!pinSet) {
            setAppHidden(pkg, false)
            onDone()
            return
        }
        showPasswordPrompt(
            title = "Unhide $name",
            message = "Enter your defense password to show this app again.",
        ) {
            setAppHidden(pkg, false)
            onDone()
        }
    }

    /** Asks for the defense password. The check runs off the main thread because it is deliberately slow. */
    private fun showPasswordPrompt(title: String, message: String, onVerified: () -> Unit) {
        showSheet(scrimColor = Color.parseColor("#99000000")) { panel ->
            panel.addView(label(title, 20f, TEXT_PRIMARY, bold = true))
            panel.addView(label(message, 13f, TEXT_DIM).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = dp(6); it.bottomMargin = dp(12) }
            })
            val input = EditText(this).apply {
                hint = "Password"
                setHintTextColor(TEXT_MUTED)
                setTextColor(TEXT_PRIMARY)
                textSize = 16f
                isSingleLine = true
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                imeOptions = EditorInfo.IME_ACTION_DONE
                setPadding(dp(16), 0, dp(16), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(48),
                )
            }
            frost(input, 16, extraDark = 0.25f)
            panel.addView(input)
            val error = label("That password is not right.", 12f, RED_BLOCK).apply {
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = dp(6) }
            }
            panel.addView(error)
            val buttons = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(46),
                ).also { it.topMargin = dp(14) }
            }
            lateinit var confirm: TextView
            fun submit() {
                val typed = input.text.toString()
                if (typed.isBlank()) return
                confirm.isEnabled = false
                confirm.alpha = 0.5f
                error.visibility = View.GONE
                Thread {
                    val ok = try { PinManager(applicationContext).verifyPin(typed) } catch (_: Throwable) { false }
                    handler.post {
                        if (isFinishing) return@post
                        if (ok) {
                            hideKeyboard()
                            dismissSheet()
                            onVerified()
                        } else {
                            confirm.isEnabled = true
                            confirm.alpha = 1f
                            error.visibility = View.VISIBLE
                        }
                    }
                }.start()
            }
            buttons.addView(customizeButton("Cancel") {
                hideKeyboard()
                dismissSheet()
            })
            confirm = customizeButton("Continue") { submit() } as TextView
            buttons.addView(confirm)
            panel.addView(buttons)
            input.setOnEditorActionListener { _, _, _ ->
                submit()
                true
            }
            input.postDelayed({
                input.requestFocus()
                showKeyboard(input)
            }, 200)
        }
    }

    // ── Hidden apps screen ─────────────────────────────────────────────────────

    private fun openHiddenScreen() {
        if (hiddenOverlay != null || !::rootFrame.isInitialized) return
        hideKeyboard()
        val overlay = FrameLayout(this).apply {
            layoutParams = matchParent()
            setBackgroundColor(Color.BLACK)
            isClickable = true
            alpha = if (animationsEnabled()) 0f else 1f
        }
        val backdrop = ImageView(this).apply {
            layoutParams = matchParent()
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = wallpaperFade()
        }
        hiddenBackdrop = backdrop
        bindBlurredBackdrop(backdrop)
        overlay.addView(backdrop)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = matchParent()
            setPadding(dp(20), barTop + dp(12), dp(20), barBottom + dp(8))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44),
            )
        }
        val back = TextView(this).apply {
            text = "‹"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(TEXT_PRIMARY)
            contentDescription = "Back"
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            isClickable = true
            isFocusable = true
            setOnClickListener { closeHiddenScreen() }
        }
        frost(back, 20)
        back.foreground = rippleForeground(20)
        header.addView(back)
        header.addView(label("Hidden apps", 22f, TEXT_PRIMARY, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.leftMargin = dp(14) }
        })
        column.addView(header)
        column.addView(label(
            "Hidden apps stay out of the drawer and search. Tap one to open it. Unhiding asks for your password if you set one, and is not possible while a block is running.",
            13f,
            TEXT_DIM,
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(10); it.bottomMargin = dp(14) }
        })
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setOnScrollChangeListener { _, _, _, _, _ -> invalidateFrosted() }
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        hiddenListContainer = list
        column.addView(scroll)
        overlay.addView(column)
        hiddenOverlay = overlay
        rootFrame.addView(overlay)
        renderHiddenList()
        if (animationsEnabled()) overlay.animate().alpha(1f).setDuration(170).start()
    }

    private fun closeHiddenScreen(animate: Boolean = true) {
        val overlay = hiddenOverlay ?: return
        hiddenOverlay = null
        val remove = {
            rootFrame.removeView(overlay)
            hiddenBackdrop = null
            hiddenListContainer = null
            refreshDockActions()
            pruneFrosted()
        }
        if (animate && animationsEnabled()) {
            overlay.animate().alpha(0f).setDuration(150).withEndAction(remove).start()
        } else {
            remove()
        }
    }

    private fun renderHiddenList() {
        val container = hiddenListContainer ?: return
        container.removeAllViews()
        val apps = hiddenInstalledApps()
        if (apps.isEmpty()) {
            container.addView(label(
                "Nothing is hidden.\nLong-press any app in the drawer to hide it.",
                14f,
                TEXT_DIM,
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(48), 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            })
            pruneFrosted()
            return
        }
        apps.forEach { app ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(64),
                ).also { it.bottomMargin = dp(10) }
                setPadding(dp(14), 0, dp(10), 0)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val blocked = app.packageName in getBlockedPackages()
                    closeAllOverlays()
                    if (blocked) launchBlockOverlay(app.packageName) else launchApp(app.packageName)
                }
                setOnLongClickListener {
                    showAppActions(app.packageName, app.label, hiddenContext = true)
                    true
                }
            }
            frost(row, 20)
            row.foreground = rippleForeground(20)
            row.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                setImageDrawable(getRoundIcon(app.packageName))
            })
            row.addView(label(app.label, 16f, TEXT_PRIMARY).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.leftMargin = dp(14) }
            })
            val unhide = label("Unhide", 13f, ACCENT_TEXT, bold = true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(16), 0, dp(16), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(36),
                )
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    requestUnhide(app.packageName, app.label) { renderHiddenList() }
                }
            }
            frost(unhide, 18)
            unhide.foreground = rippleForeground(18)
            row.addView(unhide)
            container.addView(row)
        }
        pruneFrosted()
    }

    // ── App lists, hide and Focus tools ────────────────────────────────────────

    private fun drawerHiddenPackages(): Set<String> {
        val current = parseJsonArray(prefs.getString(PREF_DRAWER_HIDDEN, "[]") ?: "[]")
        val legacy = parseJsonArray(prefs.getString(PREF_LAUNCHER_HIDDEN, "[]") ?: "[]")
        return (current + legacy).toSet()
    }

    private fun focusToolPackages(): Set<String> =
        parseJsonArray(prefs.getString(PREF_FOCUS_TOOLS, "[]") ?: "[]").toSet()

    /**
     * Any app can be hidden. Both stored lists are written in one edit so they never disagree,
     * and every screen re-reads them from storage rather than trusting an in-memory copy.
     */
    private fun setAppHidden(pkg: String, hide: Boolean) {
        // A hidden app must not stay on the home screen, or hiding it would mean nothing.
        if (hide && HomePinnedItems.contains(pinnedEntries(), HomePinnedItems.Kind.APP, pkg, "")) {
            savePinned(HomePinnedItems.removeApp(pinnedEntries(), pkg))
        }
        val hidden = drawerHiddenPackages().toMutableSet()
        if (hide) hidden.add(pkg) else hidden.remove(pkg)
        val json = JSONArray(hidden.toList()).toString()
        prefs.edit()
            .putString(PREF_DRAWER_HIDDEN, json)
            .putString(PREF_LAUNCHER_HIDDEN, json)
            .commit()
        if (isDrawerOpen && !isEditMode) {
            drawerAllApps = loadDrawerApps()
            refreshDrawerItems()
        }
        refreshDockActions()
    }

    private fun setFocusTool(pkg: String, on: Boolean) {
        val tools = focusToolPackages().toMutableSet()
        if (on) tools.add(pkg) else tools.remove(pkg)
        prefs.edit().putString(PREF_FOCUS_TOOLS, JSONArray(tools.toList()).toString()).commit()
        if (isDrawerOpen && !isEditMode) refreshDrawerItems()
        Toast.makeText(
            this,
            if (on) "Added to Focus tools" else "Removed from Focus tools",
            Toast.LENGTH_SHORT,
        ).show()
    }

    /** Every launchable app except FocusFlow itself, regardless of hidden state. */
    private fun queryLauncherApps(): List<DrawerItem.App> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return try {
            pm.queryIntentActivities(intent, 0)
                .mapNotNull { info ->
                    val pkg = info.activityInfo.packageName
                    if (pkg == ownPackage) return@mapNotNull null
                    DrawerItem.App(
                        pkg,
                        pm.getApplicationLabel(info.activityInfo.applicationInfo).toString(),
                    )
                }
                .distinctBy { it.packageName }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun hiddenInstalledApps(): List<DrawerItem.App> {
        val hidden = drawerHiddenPackages()
        if (hidden.isEmpty()) return emptyList()
        return queryLauncherApps()
            .filter { it.packageName in hidden }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun loadDrawerApps(): List<DrawerItem.App> {
        val hidden = drawerHiddenPackages()
        val apps = queryLauncherApps().filter { it.packageName !in hidden }
        val customOrder = parseJsonArray(prefs.getString(PREF_DRAWER_ORDER, "[]") ?: "[]")
        val positions = customOrder.withIndex().associate { it.value to it.index }
        return apps.sortedWith(
            compareBy<DrawerItem.App> { positions[it.packageName] ?: Int.MAX_VALUE }
                .thenBy { it.label.lowercase(Locale.getDefault()) },
        )
    }

    // ── Launch helpers ─────────────────────────────────────────────────────────

    private fun launchApp(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun launchBlockOverlay(pkg: String) {
        try {
            startActivity(
                Intent(this, BlockOverlayActivity::class.java).apply {
                    putExtra("blocked_package", pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
            )
        } catch (_: Exception) {
        }
    }

    private fun openFocusFlow() {
        try {
            startActivity(Intent(this, com.tbtechs.focusflow.MainActivity::class.java))
        } catch (_: Exception) {
        }
    }


    // ── Block and allowance data ────────────────────────────────────────────────

    private fun getBlockedPackages(): Set<String> {
        val result = mutableSetOf<String>()
        val now = System.currentTimeMillis()
        if (prefs.getBoolean(PREF_SA_ACTIVE, false)) {
            val until = prefs.getLong(PREF_SA_UNTIL, 0L)
            if (until == 0L || now <= until) {
                result.addAll(parseJsonArray(prefs.getString(PREF_SA_PKGS, "[]") ?: "[]"))
            }
        }
        if (prefs.getBoolean(PREF_ALWAYS_BLOCK, false)) {
            result.addAll(parseJsonArray(prefs.getString(PREF_ALWAYS_BLOCK_PKGS, "[]") ?: "[]"))
        }
        return result
    }

    private fun formatUsedTerse(card: AllowanceCardData): String {
        val value = when (card.mode) {
            "count" -> "${card.used}/${card.total}"
            else -> if (card.used <= 0L) "<1m" else formatDuration(card.used)
        }
        return if (card.approximate) "About $value" else value
    }

    private fun formatUsedFull(card: AllowanceCardData): String {
        val value = when (card.mode) {
            "count" -> {
                val remaining = card.total - card.used
                if (remaining <= 0L) "no opens left" else "$remaining of ${card.total} opens left"
            }
            "interval" -> if (card.remaining <= 0L) {
                "${formatDuration(card.used)} used • ${card.displayText}"
            } else {
                "${formatDuration(card.used)} used • ${formatDuration(card.total - card.used)} left"
            }
            else -> "${formatDuration(card.used)} used • ${formatDuration(card.total - card.used)} left"
        }
        return if (card.approximate) "About $value" else value
    }

    private fun formatDuration(ms: Long): String {
        val minutes = (ms.coerceAtLeast(0L) / 60_000L)
        val hours = minutes / 60
        val remainder = minutes % 60
        return when {
            hours > 0 && remainder > 0 -> "${hours}h ${remainder}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "0m"
        }
    }

    private fun loadAllowanceCardData(): List<AllowanceCardData> {
        val configJson = prefs.getString("daily_allowance_config", null) ?: return emptyList()
        if (configJson.isBlank() || configJson == "null") return emptyList()

        val today    = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getDefault()
        }.format(Date())
        val now      = System.currentTimeMillis()
        val result   = mutableListOf<AllowanceCardData>()
        val usageAccessGranted = try {
            val appOps = getSystemService(android.content.Context.APP_OPS_SERVICE)
                as? android.app.AppOpsManager
            val accessMode = appOps?.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName,
            )
            accessMode != null &&
                accessMode != android.app.AppOpsManager.MODE_IGNORED &&
                accessMode != android.app.AppOpsManager.MODE_ERRORED
        } catch (_: Exception) {
            false
        }
        fun usageIsApproximate(pkg: String): Boolean {
            val lastReadAt = allowanceLedger.usage(pkg).confirmedAtMs
            return AllowanceUsageFreshnessReducer.reduce(
                hasUsageAccess = usageAccessGranted,
                lastSuccessfulReadAtMs = lastReadAt,
                unlockedAtMs = now,
                nowMs = now,
            ) != AllowanceUsageFreshness.FRESH
        }

        try {
            val arr = org.json.JSONArray(configJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val pkg = obj.allowancePackage().takeIf { it.isNotBlank() } ?: continue
                val mode    = obj.optString("mode", "count")

                val icon  = try { packageManager.getApplicationIcon(pkg) } catch (_: Exception) { null }
                val label = try {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(pkg, 0)
                    ).toString()
                } catch (_: Exception) { pkg.substringAfterLast('.') }

                when (mode) {
                    "count" -> {
                        val countPerDay = obj.optInt("countPerDay", 1).coerceAtLeast(1)
                        val usedCount   = allowanceLedger.readAllowance(
                            packageName = pkg,
                            mode = mode,
                            today = today,
                            nowMs = now,
                            limit = countPerDay.toLong(),
                        ).count
                        val remaining   = (countPerDay - usedCount).coerceAtLeast(0)
                        val fraction    = remaining.toFloat() / countPerDay.toFloat()
                        val display     = if (remaining == 0) "no opens left" else "$remaining/$countPerDay opens"
                        result.add(AllowanceCardData(pkg, label, icon, usedCount.toLong(), countPerDay.toLong(), remaining.toLong(), mode, display, fraction, usageIsApproximate(pkg)))
                    }
                    "time_budget" -> {
                        val budgetMs    = obj.optLong("budgetMinutes", 30L) * 60_000L
                        val usedMs      = allowanceLedger.readAllowance(
                            packageName = pkg,
                            mode = mode,
                            today = today,
                            nowMs = now,
                            limit = budgetMs,
                        ).usedMs
                        val remainingMs = (budgetMs - usedMs).coerceAtLeast(0L)
                        val fraction    = if (budgetMs > 0L) remainingMs.toFloat() / budgetMs.toFloat() else 0f
                        result.add(AllowanceCardData(pkg, label, icon, usedMs, budgetMs, remainingMs, mode, formatRemainingMs(remainingMs), fraction, usageIsApproximate(pkg)))
                    }
                    "interval" -> {
                        val intervalMs    = obj.optLong("intervalMinutes", 5L) * 60_000L
                        val windowMs      = obj.optLong("intervalHours", 1L) * 3_600_000L
                        val usage         = allowanceLedger.readAllowance(
                            packageName = pkg,
                            mode = mode,
                            today = today,
                            nowMs = now,
                            limit = intervalMs,
                            windowMs = windowMs,
                        )
                        val windowExpired = usage.windowExpired
                        // A window that has ended has its whole allowance back.
                        val usedMs        = if (windowExpired) 0L else usage.usedMs
                        val remainingMs   = (intervalMs - usedMs).coerceAtLeast(0L)
                        val fraction      = if (intervalMs > 0L) remainingMs.toFloat() / intervalMs.toFloat() else 0f
                        // Used up: say when it comes back instead of a bare "time's up".
                        val resetAt       = AllowanceResetText.resetAtMs(usage.windowStartMs, windowMs)
                        val backText      = if (remainingMs <= 0L) {
                            AllowanceResetText.backText(resetAt, now) { ms -> clockText(ms) }
                        } else null
                        val display       = backText ?: formatRemainingMs(remainingMs)
                        // The small chip on a drawer icon has no room for a clock time, so it keeps
                        // the short wording. The home screen and the block screen say when.
                        val chip          = if (remainingMs <= 0L) "time's up" else ""
                        result.add(AllowanceCardData(pkg, label, icon, usedMs, intervalMs, remainingMs, mode, display, fraction, usageIsApproximate(pkg), chip))
                    }
                }
            }
        } catch (_: Exception) {}

        return result
    }

    /** A clock time in the phone's own 12 or 24 hour style. */
    private fun clockText(ms: Long): String =
        android.text.format.DateFormat.getTimeFormat(this).format(Date(ms))

    private fun formatRemainingMs(ms: Long): String {
        if (ms <= 0L) return "time's up"
        val totalMin = ms / 60_000L
        val hours    = totalMin / 60
        val mins     = totalMin % 60
        return when {
            hours > 0 -> "${hours}h ${mins}m"
            mins  > 0 -> "${mins}m left"
            else      -> "${ms / 1000}s left"
        }
    }


    // ── Clock, gestures, utilities ─────────────────────────────────────────────

    private fun startClock() {
        clockRunnable = object : Runnable {
            override fun run() {
                updateClockText()
                handler.postDelayed(this, 1_000L)
            }
        }
        handler.post(clockRunnable!!)
    }

    private fun updateClockText() {
        val now = Date()
        val use24h = android.text.format.DateFormat.is24HourFormat(this)
        val hour = SimpleDateFormat(if (use24h) "HH" else "h", Locale.getDefault()).format(now)
        val minute = SimpleDateFormat("mm", Locale.getDefault()).format(now)
        val ampm = if (use24h) "" else
            SimpleDateFormat("a", Locale.getDefault()).format(now).lowercase(Locale.getDefault())
        val time: CharSequence = when (clockStyle) {
            "stacked" -> {
                val text = if (ampm.isEmpty()) "$hour\n$minute" else "$hour\n$minute $ampm"
                SpannableString(text).apply {
                    setSpan(
                        ForegroundColorSpan(ACCENT_TEXT),
                        hour.length + 1,
                        hour.length + 1 + minute.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    if (ampm.isNotEmpty()) {
                        setSpan(
                            RelativeSizeSpan(0.3f),
                            hour.length + 1 + minute.length,
                            length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }
                }
            }
            else -> {
                if (ampm.isEmpty()) {
                    "$hour:$minute"
                } else {
                    val hm = "$hour:$minute"
                    SpannableString("$hm $ampm").apply {
                        setSpan(RelativeSizeSpan(0.3f), hm.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
        }
        clockView?.text = time
        val fullDate = clockStyle == "bold" || clockStyle == "stacked"
        dateView?.text = SimpleDateFormat(
            if (fullDate) "EEEE, d MMMM" else "EEE, d MMM",
            Locale.getDefault(),
        ).format(now)
        refreshFocusCard()
    }

    private fun expandNotificationsPanel() {
        try {
            val statusBar = getSystemService("statusbar")
            val cls = Class.forName("android.app.StatusBarManager")
            cls.getMethod("expandNotificationsPanel").invoke(statusBar)
        } catch (_: Exception) {
        }
    }

    /**
     * Swipe up on the home screen opens the drawer; swipe down opens the notification
     * shade. Inside the drawer a swipe down first scrolls the list back to the top, and
     * only closes the drawer when the swipe started with the list already at the top.
     * The touch is always passed on to the views first, so lists and sheets keep scrolling.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        lastTouchRawX = ev.rawX
        lastTouchRawY = ev.rawY
        // While a widget is lifted the drag owns the whole gesture: nothing underneath it scrolls,
        // taps or swipes, and letting go never counts as a drawer or notification swipe.
        val pending = pendingDrawerDrag
        if (pending != null) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> { movePendingDrawerDrag(pending); return true }
                MotionEvent.ACTION_UP -> {
                    pendingDrawerDrag = null
                    showAppActions(pending.entry.packageName, pending.entry.label)
                    return true
                }
                MotionEvent.ACTION_CANCEL -> { pendingDrawerDrag = null; return true }
                MotionEvent.ACTION_DOWN -> pendingDrawerDrag = null // stale: start fresh
                else -> return true
            }
        }
        if (gridResizeId != null && gridDrag == null) {
            if (swallowGesture) {
                if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                    swallowGesture = false
                }
                return true
            }
            val resizing = gridResizeDrag
            if (resizing != null) {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> endGridResize(commit = false) // a stale one: start fresh
                    MotionEvent.ACTION_MOVE -> { updateGridResize(); return true }
                    MotionEvent.ACTION_UP -> { endGridResize(commit = true); return true }
                    MotionEvent.ACTION_CANCEL -> { endGridResize(commit = false); return true }
                    else -> return true
                }
            }
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                if (isDrawerOpen || sheetOverlay != null || hiddenOverlay != null || homeArrangeActive) {
                    exitGridResize()
                } else {
                    val rect = gridResizeRect()
                    if (rect != null) {
                        val edge = HomeGrid.edgeHit(
                            rect.left, rect.top, rect.right, rect.bottom, ev.rawX, ev.rawY, dp(28).toFloat(),
                        )
                        if (edge != null) {
                            beginGridResize(edge)
                            return true
                        }
                        if (!rect.contains(ev.rawX, ev.rawY)) {
                            // A tap outside closes resize mode and does nothing else.
                            exitGridResize()
                            swallowGesture = true
                            return true
                        }
                    }
                }
            }
        }
        if (gridDrag != null) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> endGridDrag(commit = false) // a stale drag: start fresh
                MotionEvent.ACTION_MOVE -> { updateGridDrag(); return true }
                MotionEvent.ACTION_UP -> { endGridDrag(commit = true); return true }
                MotionEvent.ACTION_CANCEL -> { endGridDrag(commit = false); return true }
                else -> return true
            }
        }
        if (homeDrag != null) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> endHomeDrag(commit = false) // a stale drag: start fresh
                MotionEvent.ACTION_MOVE -> { updateHomeDrag(); return true }
                MotionEvent.ACTION_UP -> { endHomeDrag(commit = true); return true }
                MotionEvent.ACTION_CANCEL -> { endHomeDrag(commit = false); return true }
                else -> return true
            }
        }
        // Pinching the home screen in or out does what a long press does: opens arrange mode.
        if (!homeArrangeActive && !isDrawerOpen && sheetOverlay == null && hiddenOverlay == null) {
            pinchDetector.onTouchEvent(ev)
        }
        if (ev.actionMasked == MotionEvent.ACTION_POINTER_DOWN) swipeMultiTouch = true
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeMultiTouch = false
                swipeTouchStartY = ev.rawY
                swipeVelocityTracker?.recycle()
                swipeVelocityTracker = VelocityTracker.obtain().also { it.addMovement(ev) }
                val dock = dockView
                swipeStartedInDock = dock != null && dock.isShown &&
                    ev.rawY >= dock.locationOnScreenY()
                // Remember what the home content could do when the finger landed. Deciding at
                // lift-off is what let a swipe that merely scrolled the home screen to its
                // bottom open the drawer as well.
                val homeScrollView = homeScroll
                swipeStartScrollY = homeScrollView?.scrollY ?: 0
                swipeContentCanScrollDown = homeScrollView?.canScrollVertically(1) == true
                swipeContentScrolledDown = homeScrollView?.canScrollVertically(-1) == true
                val recycler = drawerRecycler
                swipeDrawerAtTop = recycler == null ||
                    !recycler.canScrollVertically(-1) ||
                    ev.rawY < recycler.locationOnScreenY()
            }
            MotionEvent.ACTION_MOVE -> swipeVelocityTracker?.addMovement(ev)
            MotionEvent.ACTION_CANCEL -> {
                swipeVelocityTracker?.recycle()
                swipeVelocityTracker = null
            }
        }
        val handled = super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP) {
            swipeVelocityTracker?.addMovement(ev)
            swipeVelocityTracker?.computeCurrentVelocity(1000)
            val velocity = swipeVelocityTracker?.yVelocity ?: 0f
            swipeVelocityTracker?.recycle()
            swipeVelocityTracker = null
            val overlayOpen = sheetOverlay != null || hiddenOverlay != null
            val dy = swipeTouchStartY - ev.rawY
            // No drawer or notification swipes while arranging, or during a two-finger pinch.
            if (!overlayOpen && !homeArrangeActive && !swipeMultiTouch) {
                if (dy > dp(60) && velocity < -250f && !isDrawerOpen) {
                    val scrollMoved = (homeScroll?.scrollY ?: 0) != swipeStartScrollY
                    val ownsTheSwipe = swipeContentCanScrollDown || scrollMoved
                    if (!ownsTheSwipe || swipeStartedInDock) openDrawer()
                } else if (dy < -dp(80) && velocity > 250f) {
                    if (isDrawerOpen) {
                        if (!isEditMode && swipeDrawerAtTop) closeDrawer()
                    } else {
                        val scrollMoved = (homeScroll?.scrollY ?: 0) != swipeStartScrollY
                        if (!swipeContentScrolledDown && !scrollMoved) expandNotificationsPanel()
                    }
                }
            }
        }
        return handled
    }

    private fun View.locationOnScreenY(): Int {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        return loc[1]
    }

    private fun searchGlyph(color: Int): View {
        return object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = dp(2).toFloat()
                strokeCap = Paint.Cap.ROUND
            }

            override fun onDraw(canvas: Canvas) {
                val r = dp(6).toFloat()
                val cx = dp(8).toFloat()
                val cy = height / 2f - dp(1)
                canvas.drawCircle(cx, cy, r, paint)
                canvas.drawLine(cx + r * 0.7f, cy + r * 0.7f, cx + dp(13), cy + dp(12), paint)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            contentDescription = "Search"
        }
    }

    private fun getAppIcon(pkg: String): Drawable? =
        try { packageManager.getApplicationIcon(pkg) } catch (_: Exception) { null }

    private fun getRoundIcon(pkg: String): Drawable? {
        val bitmap = iconCache[pkg] ?: run {
            val raw = getAppIcon(pkg) ?: return null
            val created = Bitmap.createBitmap(
                raw.intrinsicWidth.coerceIn(1, 192),
                raw.intrinsicHeight.coerceIn(1, 192),
                Bitmap.Config.ARGB_8888,
            )
            raw.setBounds(0, 0, created.width, created.height)
            raw.draw(Canvas(created))
            iconCache[pkg] = created
            created
        }
        // A fresh drawable per use: drawables carry their own bounds and must not be shared.
        return RoundedBitmapDrawableFactory.create(resources, bitmap).also {
            it.isCircular = true
        }
    }

    private fun parseJsonArray(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
