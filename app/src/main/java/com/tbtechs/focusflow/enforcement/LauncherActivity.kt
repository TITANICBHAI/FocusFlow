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
import com.tbtechs.focusflow.data.images.WallpaperLoader
import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
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
    private var homeLongPress: View.OnLongClickListener? = null
    private var clockStyle = "light"
    private var suppressPrefRebuild = false
    private var drawerLimitInfo: Map<String, Pair<String, Float>> = emptyMap()
    private var homeScroll: ScrollView? = null
    private var dockView: LinearLayout? = null
    private var dockActionsRow: LinearLayout? = null
    private var homeBackdrop: ImageView? = null
    private var homeScrim: View? = null
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
    private var drawerScrim: View? = null
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
    private var hiddenScrim: View? = null
    private var hiddenListContainer: LinearLayout? = null
    private var sheetOverlay: FrameLayout? = null

    // Gestures
    private var swipeTouchStartY = 0f
    private var swipeStartedInDock = false
    private var swipeDrawerAtTop = true
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

            // 1. Base: near-black. Fully opaque at 0, mostly see-through when glassy.
            fill.shader = null
            val baseAlpha = (0.94f - 0.80f * g + extraDark).coerceIn(0f, 0.97f)
            fill.color = Color.argb((baseAlpha * 255).toInt(), 10, 10, 12)
            canvas.drawRoundRect(rect, radiusPx, radiusPx, fill)

            // 2. The blurred wallpaper under this panel.
            val bmp = backdropBlur
            if (g > 0f && bmp != null && rootFrame.width > 0 && rootFrame.height > 0) {
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
                blurPaint.alpha = (min(1f, g * 1.6f) * 255).toInt()
                canvas.drawRoundRect(rect, radiusPx, radiusPx, blurPaint)
            }

            // 3. Light tint.
            fill.color = Color.argb(((0.03f + 0.11f * g) * 255).toInt(), 255, 255, 255)
            canvas.drawRoundRect(rect, radiusPx, radiusPx, fill)

            val key = "${w.toInt()}x${h.toInt()}@${glassLevel}"
            if (key != builtKey) {
                builtKey = key
                sheen = LinearGradient(
                    0f, 0f, 0f, h * 0.6f,
                    Color.argb((0.20f * g * 255).toInt(), 255, 255, 255),
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
            val generated = if (decoded == null) {
                if (builtin != null) BuiltinWallpapers.render(builtin, 270, 585)
                else BuiltinWallpapers.adaptive(palette ?: wallpaperPalette(), 180, 390)
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
        val g = glassFraction()
        homeBackdrop?.alpha = g
        homeScrim?.alpha = min(1f, g * 2f)
        drawerBackdrop?.alpha = g
        drawerScrim?.alpha = min(1f, g * 2f)
        hiddenBackdrop?.alpha = g
        hiddenScrim?.alpha = min(1f, g * 2f)
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
        setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), Color.parseColor("#55000000"))
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
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
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
            if (!listLayout) {
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
                    showAppActions(item.packageName, item.label)
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
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
        if (renderedSignature != layoutSignature()) {
            buildHomeLayout()
        } else {
            refreshHomeContent()
            refreshDockActions()
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
        val g = glassFraction()

        val backdrop = ImageView(this).apply {
            layoutParams = matchParent()
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = g
        }
        homeBackdrop = backdrop
        rootFrame.addView(backdrop)

        val scrim = View(this).apply {
            layoutParams = matchParent()
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#44000000"),
                    Color.parseColor("#14000000"),
                    Color.parseColor("#78000000"),
                ),
            )
            alpha = min(1f, g * 2f)
        }
        homeScrim = scrim
        rootFrame.addView(scrim)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = matchParent()
            setPadding(dp(20), barTop + dp(14), dp(20), barBottom + dp(14))
        }
        homeContent = content

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
        val customize = View.OnLongClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showHomeCustomizeSheet()
            true
        }
        homeLongPress = customize
        column.setOnLongClickListener(customize)
        content.setOnLongClickListener(customize)
        populateHomeColumn(column)
        scroll.addView(column)
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> invalidateFrosted() }
        content.addView(scroll)
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
        if (clockStyleValue() != "off") {
            column.addView(buildClockWidget())
        } else {
            column.addView(View(this), LinearLayout.LayoutParams(1, dp(28)))
        }
        if (statusStyleValue() != "off") column.addView(buildStatusPill())
        if (taskStyleValue() != "off") column.addView(buildFocusSessionCard())
        if (limitsStyleValue() != "off") column.addView(buildTodaysLimits())
    }

    /** Rebuilds the home widgets and dock in place, so a style change shows instantly under an open sheet. */
    private fun rebuildHomeSections() {
        val column = homeColumn ?: return
        populateHomeColumn(column)
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
                "stacked" -> 88f
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
            setShadowLayer(dp(10).toFloat(), 0f, dp(2).toFloat(), Color.parseColor("#55000000"))
        }
        val wrap = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also {
                it.topMargin = dp(if (style == "compact") 8 else 36)
                it.bottomMargin = dp(18)
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
        text.text = "${getBlockedPackages().size} blocked • ${statusLabel()}"
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
            else -> "Ready"
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
            val header = label("CURRENT TASK", 11f, TEXT_DIM).apply { letterSpacing = 0.12f }
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
            val dayStats = if (snapshot.totalToday > 0) " · ${snapshot.doneToday}/${snapshot.totalToday}" else ""
            focusHeaderView?.text = "CURRENT TASK$dayStats"
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
            focusHeaderView?.text = "UP NEXT"
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
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }
        frost(card, 24)
        card.addView(label("TODAY'S LIMITS", 11f, TEXT_DIM).apply {
            letterSpacing = 0.12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = dp(10) }
        })
        when (style) {
            "rings" -> card.addView(buildLimitRings(cards))
            "list" -> card.addView(buildLimitList(cards))
            else -> card.addView(buildLimitBars(cards))
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
            // Long-press anywhere on the home screen, dock included, opens Customize home.
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
        drawerFilter = DrawerFilter.ALL
        focusedDrawerPackage = null
        appliedListLayout = null
        drawerBlockedCount = getBlockedPackages().size
        drawerAllApps = loadDrawerApps()
        reloadDrawerLimits()
        val g = glassFraction()

        val overlay = FrameLayout(this).apply {
            layoutParams = matchParent()
            setBackgroundColor(Color.BLACK)
            isClickable = true
            alpha = if (animationsEnabled()) 0f else 1f
        }
        val backdrop = ImageView(this).apply {
            layoutParams = matchParent()
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = g
        }
        drawerBackdrop = backdrop
        bindBlurredBackdrop(backdrop)
        overlay.addView(backdrop)
        val scrim = View(this).apply {
            layoutParams = matchParent()
            setBackgroundColor(Color.parseColor("#99000000"))
            alpha = min(1f, g * 2f)
        }
        drawerScrim = scrim
        overlay.addView(scrim)

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
            drawerScrim = null
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

    private fun renderDrawerChips() {
        val row = drawerChipRow ?: return
        row.removeAllViews()
        val labels = listOf(
            DrawerFilter.ALL to "ALL APPS",
            DrawerFilter.FOCUS_TOOLS to "FOCUS TOOLS",
            DrawerFilter.LIMITED_TODAY to "LIMITED TIME",
            DrawerFilter.BLOCKED to "BLOCKED ($drawerBlockedCount)",
        )
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
            it.pkg to (it.displayText to it.fraction)
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
        drawerRecycler?.setPadding(0, 0, 0, dp(if (drawerIsList()) 190 else 330))
        renderDrawerChips()
        renderDrawerHeader()
        if (!drawerIsList()) drawerAdapter?.setIconScale(editDraftScale)
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

        if (!drawerIsList()) {
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

    private fun showSheet(
        scrimColor: Int = Color.parseColor("#80000000"),
        build: (LinearLayout) -> Unit,
    ) {
        dismissSheet(animate = false)
        hideKeyboard()
        val overlay = FrameLayout(this).apply {
            layoutParams = matchParent()
            setBackgroundColor(scrimColor)
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
                    rebuildHomeSections()
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
    private fun showHomeCustomizeSheet() {
        if (!::rootFrame.isInitialized) return
        showSheet(scrimColor = Color.parseColor("#33000000")) { panel ->
            panel.addView(label("Customize home", 20f, TEXT_PRIMARY, bold = true))
            panel.addView(label(
                "Changes show instantly. Long-press the home screen any time to come back.",
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
            val phoneRow = sheetRow("Use as phone wallpaper…", "Also for the lock screen, and it fixes the Recents mismatch") {
                confirmSetPhoneWallpaper()
            }
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
                row.addView(wallpaperTile(
                    "Adaptive", "Matches your phone · best at 70%",
                    previews.getOrPut("adaptive") { BuiltinWallpapers.adaptive(wallpaperPalette(), 90, 150) },
                    !customOn && builtinOn.isEmpty(),
                ) { choose(""); render() })
                BuiltinWallpapers.all.forEach { w ->
                    row.addView(wallpaperTile(
                        w.name, "Best at ${w.bestAt}%",
                        previews.getOrPut(w.id) { BuiltinWallpapers.render(w, 90, 150) },
                        builtinOn == w.id,
                    ) { choose(w.id); render() })
                }
                row.addView(wallpaperTile(
                    if (customOn) "Your picture" else "From gallery",
                    if (customOn) "Try 40–70%" else "Pick your own photo",
                    null,
                    customOn,
                ) { pickWallpaperFromGallery() })
                phoneRow.visibility =
                    if (customOn || builtinOn.isNotEmpty()) View.VISIBLE else View.GONE
            }
            render()
            panel.addView(glassSliderBlock())
            panel.addView(phoneRow)
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
                    Toast.makeText(this, "Wallpaper set. Try 40–70% glass.", Toast.LENGTH_SHORT).show()
                }
                result.onFailure {
                    Toast.makeText(this, it.message ?: "That picture could not be used.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    /** One-shot, explicit: makes Recents, the lock screen and home all show the same picture. */
    private fun confirmSetPhoneWallpaper() {
        showSheet(scrimColor = Color.parseColor("#66000000")) { panel ->
            panel.addView(label("Use as phone wallpaper", 20f, TEXT_PRIMARY, bold = true))
            panel.addView(label(
                "This replaces your phone's current wallpaper and FocusFlow can't bring the old one back. Your home screen, lock screen and the Recents screen will then all match.",
                13f,
                TEXT_DIM,
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = dp(6); it.bottomMargin = dp(8) }
            })
            panel.addView(sheetRow("Home and lock screen") {
                applyPhoneWallpaper(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            })
            panel.addView(sheetRow("Home screen only") {
                applyPhoneWallpaper(WallpaperManager.FLAG_SYSTEM)
            })
            panel.addView(sheetRow("Lock screen only") {
                applyPhoneWallpaper(WallpaperManager.FLAG_LOCK)
            })
            panel.addView(sheetRow("Cancel", tint = TEXT_DIM) { dismissSheet() })
        }
    }

    private fun applyPhoneWallpaper(flags: Int) {
        dismissSheet()
        val path = wallpaperPath()
        val builtin = BuiltinWallpapers.byId(builtinWallpaperId())
        val metrics = resources.displayMetrics
        val appContext = applicationContext
        Toast.makeText(this, "Setting wallpaper…", Toast.LENGTH_SHORT).show()
        Thread {
            val ok = try {
                val bitmap = when {
                    path.isNotEmpty() -> WallpaperLoader.decode(appContext, path)
                    builtin != null ->
                        BuiltinWallpapers.render(builtin, metrics.widthPixels, metrics.heightPixels)
                    else -> null
                }
                if (bitmap == null) {
                    false
                } else {
                    WallpaperManager.getInstance(appContext).setBitmap(bitmap, null, true, flags)
                    true
                }
            } catch (_: Exception) {
                false
            }
            handler.post {
                if (isFinishing) return@post
                Toast.makeText(
                    this,
                    if (ok) "Phone wallpaper updated." else "Couldn't set the phone wallpaper.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }.start()
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
        val g = glassFraction()
        val overlay = FrameLayout(this).apply {
            layoutParams = matchParent()
            setBackgroundColor(Color.BLACK)
            isClickable = true
            alpha = if (animationsEnabled()) 0f else 1f
        }
        val backdrop = ImageView(this).apply {
            layoutParams = matchParent()
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = g
        }
        hiddenBackdrop = backdrop
        bindBlurredBackdrop(backdrop)
        overlay.addView(backdrop)
        val scrim = View(this).apply {
            layoutParams = matchParent()
            setBackgroundColor(Color.parseColor("#99000000"))
            alpha = min(1f, g * 2f)
        }
        hiddenScrim = scrim
        overlay.addView(scrim)

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
            hiddenScrim = null
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
                        val usedMs        = usage.usedMs
                        val remainingMs   = (intervalMs - usedMs).coerceAtLeast(0L)
                        val fraction      = if (intervalMs > 0L) remainingMs.toFloat() / intervalMs.toFloat() else 0f
                        val display       = if (windowExpired) "reset" else formatRemainingMs(remainingMs)
                        result.add(AllowanceCardData(pkg, label, icon, usedMs, intervalMs, remainingMs, mode, display, fraction, usageIsApproximate(pkg)))
                    }
                }
            }
        } catch (_: Exception) {}

        return result
    }

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
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeTouchStartY = ev.rawY
                swipeVelocityTracker?.recycle()
                swipeVelocityTracker = VelocityTracker.obtain().also { it.addMovement(ev) }
                val dock = dockView
                swipeStartedInDock = dock != null && dock.isShown &&
                    ev.rawY >= dock.locationOnScreenY()
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
            if (!overlayOpen) {
                if (dy > dp(60) && velocity < -250f && !isDrawerOpen) {
                    val scroll = homeScroll
                    val contentCanScroll = scroll != null && scroll.canScrollVertically(1)
                    if (!contentCanScroll || swipeStartedInDock) openDrawer()
                } else if (dy < -dp(80) && velocity > 250f) {
                    if (isDrawerOpen) {
                        if (!isEditMode && swipeDrawerAtTop) closeDrawer()
                    } else {
                        val scrolled = homeScroll?.canScrollVertically(-1) == true
                        if (!scrolled) expandNotificationsPanel()
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
