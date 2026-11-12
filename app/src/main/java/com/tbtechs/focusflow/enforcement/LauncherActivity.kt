package com.tbtechs.focusflow.enforcement

import com.tbtechs.focusflow.data.repository.SettingsRepository
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
import android.text.style.RelativeSizeSpan
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
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
    private var focusTitleView: TextView? = null
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

    private fun layoutSignature(): String = "${readGlassLevel()}|${wallpaperPath()}"

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

    /** Soft aurora gradient built from the phone's own wallpaper colours. Used when no custom wallpaper is set. */
    private fun makeAuroraBitmap(palette: IntArray): Bitmap {
        val w = 180
        val h = 390
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val deep = Color.parseColor("#05060D")
        paint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(
                ColorUtils.blendARGB(palette[0], deep, 0.58f),
                ColorUtils.blendARGB(palette[1], deep, 0.74f),
                deep,
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        fun glow(cx: Float, cy: Float, r: Float, color: Int, alpha: Float) {
            paint.shader = RadialGradient(
                cx, cy, r,
                intArrayOf(
                    ColorUtils.setAlphaComponent(color, (alpha * 255).toInt()),
                    ColorUtils.setAlphaComponent(color, 0),
                ),
                null,
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, r, paint)
        }
        glow(w * 0.15f, h * 0.16f, w * 0.95f, ColorUtils.blendARGB(palette[0], Color.WHITE, 0.12f), 0.60f)
        glow(w * 0.95f, h * 0.60f, w * 0.90f, palette[2], 0.42f)
        glow(w * 0.20f, h * 0.96f, w * 0.85f, palette[1], 0.38f)
        return bmp
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
     * Prepares the blurred backdrop off the main thread. The wallpaper file goes through the
     * same path-confined, size-bounded loader as before; with no wallpaper a gradient built
     * from the phone's wallpaper colours is used instead.
     */
    private fun ensureBackdrop() {
        val path = wallpaperPath()
        val key = if (path.isEmpty()) "aurora:${wallpaperPalette().joinToString(",")}" else "file:$path"
        if (key == backdropKey && backdropBlur != null) return
        backdropKey = key
        val token = ++backdropToken
        val palette = if (path.isEmpty()) wallpaperPalette() else null
        val appContext = applicationContext
        Thread {
            val decoded = if (path.isNotEmpty()) WallpaperLoader.decode(appContext, path) else null
            val aurora = if (decoded == null) {
                makeAuroraBitmap(palette ?: wallpaperPalette())
            } else {
                null
            }
            val source = decoded ?: aurora!!
            val blurred = try {
                val small = shrinkTo(source, 160)
                blurBitmap(small.copy(Bitmap.Config.ARGB_8888, true), radius = 5, passes = 3)
            } catch (_: Throwable) {
                null
            }
            handler.post {
                if (token != backdropToken || isFinishing) return@post
                backdropBlur = blurred
                backdropAurora = aurora
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
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.leftMargin = dp(14) }
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
            row.addView(name)
            row.addView(badge)
            return AppHolder(row, icon, name, badge)
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
            cell.addView(icon)
            cell.addView(name)
            cell.addView(badge)
            return AppHolder(cell, icon, name, badge)
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
    ) : RecyclerView.ViewHolder(view)

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PREF_LAUNCHER_THEME ||
            key == PREF_LAUNCHER_WALLPAPER ||
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
                    key == PREF_GLASS_LEVEL
                ) {
                    if (renderedSignature != layoutSignature()) buildHomeLayout()
                } else if (key == PREF_DRAWER_LAYOUT) {
                    if (isDrawerOpen) applyDrawerLayout()
                } else {
                    refreshHomeContent()
                    refreshDockActions()
                    if (isDrawerOpen) {
                        if (!isEditMode) {
                            drawerAllApps = loadDrawerApps()
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
        if (renderedSignature != layoutSignature()) {
            buildHomeLayout()
        } else {
            refreshHomeContent()
            refreshDockActions()
        }
        if (isDrawerOpen && !isEditMode) {
            drawerAllApps = loadDrawerApps()
            refreshDrawerItems()
        }
    }

    override fun onPause() {
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        }
        super.onPause()
    }

    override fun onDestroy() {
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        }
        backdropToken++
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
        }
        homeScroll = scroll
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        column.addView(buildClockWidget())
        column.addView(buildStatusPill())
        column.addView(buildFocusSessionCard())
        column.addView(buildTodaysLimits())
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

    private fun refreshHomeContent() {
        if (!::rootFrame.isInitialized) return
        refreshStatusPill()
        refreshFocusCard()
        refreshTodaysLimits()
        updateClockText()
    }

    private fun buildClockWidget(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also {
                it.topMargin = dp(36)
                it.bottomMargin = dp(18)
            }
            val date = label("", SIZE_DATE, TEXT_DIM).apply {
                letterSpacing = 0.14f
                isAllCaps = true
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            val clock = label("", 68f, TEXT_PRIMARY).apply {
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                letterSpacing = -0.02f
                includeFontPadding = false
                setShadowLayer(dp(10).toFloat(), 0f, dp(2).toFloat(), Color.parseColor("#55000000"))
            }
            addView(date)
            addView(clock)
            dateView = date
            clockView = clock
        }
    }

    private fun buildStatusPill(): View {
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(34),
            ).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.bottomMargin = dp(18)
            }
            setPadding(dp(14), 0, dp(16), 0)
        }
        frost(pill, 17)
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
        return when {
            prefs.getBoolean(PREF_ALWAYS_BLOCK, false) -> "Always-On"
            prefs.getBoolean(PREF_SA_ACTIVE, false) -> "Focus Mode"
            prefs.getString("next_task_name", null)?.takeIf { it.isNotBlank() } != null -> {
                "Next: ${prefs.getString("next_task_name", "")}"
            }
            else -> "Ready"
        }
    }

    private fun buildFocusSessionCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = dp(16) }
            setPadding(dp(18), dp(14), dp(18), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { openFocusFlow() }
        }
        frost(card, 24)
        val header = label("CURRENT TASK", 11f, TEXT_DIM).apply {
            letterSpacing = 0.12f
        }
        val title = label("", 18f, TEXT_PRIMARY, bold = true).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(8) }
        }
        val track = FrameLayout(this).apply {
            background = roundedBackground(Color.parseColor("#33FFFFFF"), Color.TRANSPARENT, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(4),
            ).also { it.topMargin = dp(12) }
        }
        val fill = View(this).apply {
            background = roundedBackground(Color.parseColor("#E0E7FF"), Color.TRANSPARENT, 4)
            layoutParams = FrameLayout.LayoutParams(0, dp(4))
        }
        track.addView(fill)
        val subtitle = label("", 13f, TEXT_DIM).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(8) }
        }
        card.addView(header)
        card.addView(title)
        card.addView(track)
        card.addView(subtitle)
        focusCard = card
        focusTitleView = title
        focusSubtitleView = subtitle
        focusProgressTrack = track
        focusProgressFill = fill
        card.foreground = rippleForeground(24)
        return card
    }

    private fun refreshFocusCard() {
        val card = focusCard ?: return
        val active = prefs.getBoolean("focus_active", false)
        val taskName = prefs.getString("task_name", null)?.takeIf { it.isNotBlank() }
        if (!active || taskName == null) {
            card.visibility = View.GONE
            return
        }
        val endMs = prefs.getLong("task_end_ms", 0L)
        val startMs = prefs.getLong("task_start_ms", 0L)
        val remaining = (endMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val total = if (startMs > 0L && endMs > startMs) endMs - startMs
        else prefs.getLong("task_duration_ms", 0L).coerceAtLeast(1L)
        val elapsed = (total - remaining).coerceIn(0L, total)
        val fraction = if (total > 0L) elapsed.toFloat() / total.toFloat() else 0.35f
        focusTitleView?.text = taskName
        focusSubtitleView?.text = "Due today • ${formatDuration(remaining)} left"
        card.visibility = View.VISIBLE
        focusProgressTrack?.post {
            val track = focusProgressTrack ?: return@post
            val fill = focusProgressFill ?: return@post
            fill.layoutParams = (fill.layoutParams as FrameLayout.LayoutParams).also {
                it.width = (track.width * fraction.coerceIn(0.02f, 1f)).toInt()
            }
            fill.requestLayout()
        }
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

    private fun refreshTodaysLimits() {
        val container = allowanceContainer ?: return
        container.removeAllViews()
        val cards = loadAllowanceCardData()
        if (cards.isEmpty()) {
            container.visibility = View.GONE
            pruneFrosted()
            return
        }
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
            ).also { it.bottomMargin = dp(8) }
        })
        val innerList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scrollWrap = NestedScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52 * 3 + 16),
            )
            isNestedScrollingEnabled = true
        }
        cards.forEachIndexed { index, allowance ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                if (index > 0) setPadding(0, dp(6), 0, 0)
            }
            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(getRoundIcon(allowance.pkg))
            }
            val name = label(allowance.label, 15f, TEXT_PRIMARY).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ).also { it.leftMargin = dp(12) }
            }
            val value = label(formatUsedFull(allowance), 13f, TEXT_DIM).apply {
                gravity = Gravity.END
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
            row.addView(icon)
            row.addView(name)
            row.addView(value)
            innerList.addView(row)
        }
        scrollWrap.addView(innerList)
        card.addView(scrollWrap)
        container.addView(card)
        container.visibility = View.VISIBLE
        pruneFrosted()
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
        dock.addView(buildSearchBar())
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(18) }
        }
        dockActionsRow = row
        dock.addView(row)
        dockView = dock
        refreshDockActions()
        return dock
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
        row.addView(dockAction("All apps", gridGlyph()) { openDrawer() })
        row.addView(dockAction("FocusFlow", focusFlowGlyph()) { openFocusFlow() })
        val hiddenCount = hiddenInstalledApps().size
        if (hiddenCount > 0) {
            row.addView(dockAction("Hidden · $hiddenCount", eyeOffGlyph()) { openHiddenScreen() })
        }
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
        }
        val circle = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(58))
        }
        frost(circle, 29)
        circle.foreground = rippleForeground(29)
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

    private fun eyeOffGlyph(): View = object : View(this) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_PRIMARY
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
            strokeCap = Paint.Cap.ROUND
        }
        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val rx = dp(11).toFloat()
            val ry = dp(6.5f).toFloat()
            canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), paint)
            canvas.drawCircle(cx, cy, dp(2.5f).toFloat(), paint)
            canvas.drawLine(cx - rx, cy + ry + dp(2), cx + rx, cy - ry - dp(2), paint)
        }
    }.apply { layoutParams = FrameLayout.LayoutParams(dp(28), dp(28)) }

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
            DrawerFilter.LIMITED_TODAY to "LIMITED TODAY",
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
            DrawerFilter.LIMITED_TODAY -> {
                val limited = loadAllowanceCardData().map { it.pkg }.toSet()
                all.filter { it.packageName in limited }
            }
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

    private fun showSheet(build: (LinearLayout) -> Unit) {
        dismissSheet(animate = false)
        hideKeyboard()
        val overlay = FrameLayout(this).apply {
            layoutParams = matchParent()
            setBackgroundColor(Color.parseColor("#80000000"))
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
        rootFrame.addView(overlay)
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
                    setAppHidden(pkg, false)
                    if (hiddenOverlay != null) renderHiddenList()
                })
            } else {
                panel.addView(sheetRow("Hide from launcher", "Find it later under Hidden apps") {
                    dismissSheet()
                    setAppHidden(pkg, true)
                    Toast.makeText(this, "$name hidden. Find it under Hidden apps.", Toast.LENGTH_SHORT).show()
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
            panel.addView(sheetRow("App info") {
                dismissSheet()
                openAppInfo(pkg)
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
                    prefs.edit().putInt(PREF_GLASS_LEVEL, glassLevel).apply()
                    renderedSignature = layoutSignature()
                }
            })
            panel.addView(seek)

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
            "Hidden apps stay out of the drawer and search. Tap one to open it, or unhide it.",
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
                    setAppHidden(app.packageName, false)
                    renderHiddenList()
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

    private fun openAppInfo(pkg: String) {
        try {
            startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$pkg")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
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
                val pkg = obj.optString("packageName", "").takeIf { it.isNotBlank() } ?: continue
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
        val time: CharSequence = if (use24h) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        } else {
            val hm = SimpleDateFormat("h:mm", Locale.getDefault()).format(now)
            val ampm = SimpleDateFormat("a", Locale.getDefault()).format(now).lowercase(Locale.getDefault())
            SpannableString("$hm $ampm").apply {
                setSpan(RelativeSizeSpan(0.3f), hm.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        clockView?.text = time
        dateView?.text = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now)
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
