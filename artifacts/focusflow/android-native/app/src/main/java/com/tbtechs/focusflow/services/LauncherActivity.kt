package com.tbtechs.focusflow.services

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.VelocityTracker
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FlingAnimation
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LauncherActivity — FocusFlow's full home-screen replacement.
 *
 * Layout (top → bottom):
 *   ┌─────────────────────────────────┐
 *   │  Date (small, muted)            │
 *   │  Clock (large, bold)            │
 *   │  AM/PM + day-of-week            │
 *   │                                 │
 *   │  ┌── Home screen grid ───────┐  │
 *   │  │  4-column icon grid of    │  │
 *   │  │  home-screen shortcuts    │  │
 *   │  └───────────────────────────┘  │
 *   │                                 │
 *   │  ─── ─── ─── (divider) ─── ─── │
 *   │  [ dock: up to 5 apps ]         │
 *   └─────────────────────────────────┘
 *
 * Swipe UP → opens full-screen app drawer (bottom-sheet style, animated).
 * App drawer has: search bar + alphabetical sections + 5-column grid.
 * Long-press home icon → Remove / Add to Dock / App Info.
 * Long-press dock icon → Remove from Dock / App Info.
 * Long-press empty space → Add Apps to Home Screen dialog.
 * Long-press drawer icon → Add to Home / Add to Dock / App Info.
 */
class LauncherActivity : Activity() {

    companion object {
        private const val PREFS_NAME            = AppBlockerAccessibilityService.PREFS_NAME
        private const val PREF_LAUNCHER_HIDDEN  = "launcher_hidden_packages"
        private const val PREF_LAUNCHER_PINNED  = "launcher_pinned_packages"
        private const val PREF_LAUNCHER_DOCK    = "launcher_dock_packages"
        private const val PREF_LAUNCHER_WALLPAPER = "launcher_wallpaper"
        private const val PREF_SA_ACTIVE        = AppBlockerAccessibilityService.PREF_SA_ACTIVE
        private const val PREF_SA_PKGS          = AppBlockerAccessibilityService.PREF_SA_PKGS
        private const val PREF_SA_UNTIL         = AppBlockerAccessibilityService.PREF_SA_UNTIL
        private const val PREF_ALWAYS_BLOCK     = AppBlockerAccessibilityService.PREF_ALWAYS_BLOCK
        private const val PREF_ALWAYS_BLOCK_PKGS = AppBlockerAccessibilityService.PREF_ALWAYS_BLOCK_PKGS
        private const val OWN_PACKAGE           = "com.tbtechs.focusflow"
        private const val DRAWER_TYPE_HEADER   = 0
        private const val DRAWER_TYPE_APP      = 1

        // ── Typography scale ─────────────────────────────────────────────────
        private const val SIZE_CLOCK_MAIN  = 86f
        private const val SIZE_CLOCK_AMPM  = 18f
        private const val SIZE_DATE        = 13f
        private const val SIZE_LABEL_HOME  = 11.5f
        private const val SIZE_LABEL_DOCK  = 10.5f
        private const val SIZE_SEARCH_HINT = 14f
        private const val SIZE_SECTION_HDR = 11f

        // ── Spacing unit — 8-point grid ──────────────────────────────────────
        private const val UNIT = 8

        // ── Icon sizes ───────────────────────────────────────────────────────
        private const val ICON_HOME        = 54
        private const val ICON_DOCK        = 52
        private const val ICON_CELL_FRAME  = 62
        private const val ICON_DOCK_FRAME  = 60

        // ── Glass surfaces ───────────────────────────────────────────────────
        private val GLASS_ULTRA         = Color.parseColor("#0FFFFFFF")
        private val GLASS_LIGHT         = Color.parseColor("#1AFFFFFF")
        private val GLASS_MID           = Color.parseColor("#2AFFFFFF")
        private val GLASS_HEAVY         = Color.parseColor("#3CFFFFFF")
        private val GLASS_BORDER        = Color.parseColor("#20FFFFFF")
        private val GLASS_BORDER_BRIGHT = Color.parseColor("#35FFFFFF")

        // ── Brand ────────────────────────────────────────────────────────────
        private val ACCENT         = Color.parseColor("#6366f1")
        private val ACCENT_TEXT    = Color.parseColor("#818CF8")
        private val ACCENT_DIM     = Color.parseColor("#406366f1")
        private val ACCENT_GLOW    = Color.parseColor("#1A6366f1")
        private val ACCENT_SURFACE = Color.parseColor("#226366f1")

        // ── Text ─────────────────────────────────────────────────────────────
        private val TEXT_PRIMARY = Color.WHITE
        private val TEXT_DIM     = Color.parseColor("#CCF0F4FF")
        private val TEXT_MUTED   = Color.parseColor("#B3AAB8CC")
        private val TEXT_BLOCKED = Color.parseColor("#55FFFFFF")

        // ── Status ───────────────────────────────────────────────────────────
        private val RED_BLOCK     = Color.parseColor("#EF4444")
        private val RED_BLOCK_DIM = Color.parseColor("#99EF4444")
        private val AMBER_WARN    = Color.parseColor("#F59E0B")

        // ── Scrim ─────────────────────────────────────────────────────────────
        private val SCRIM_FULL_TOP = Color.parseColor("#26000000")
        private val SCRIM_FULL_BTM = Color.parseColor("#BF000000")
        private val SCRIM_DOCK_BTM = Color.parseColor("#F0000000")

        private val DRAWER_BG = Color.parseColor("#F0111827")
    }

    private data class AllowanceCardData(
        val pkg: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable?,
        val used: Long,
        val total: Long,
        val remaining: Long,
        val mode: String,
        val displayText: String,
        val fraction: Float,
    )

    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var clockRunnable: Runnable? = null

    private lateinit var rootFrame: FrameLayout
    private var clockView: TextView? = null
    private var minuteView: TextView? = null
    private var colonView: TextView? = null
    private var ampmView: TextView? = null
    private var dateView: TextView? = null
    private var analogClockView: AnalogClockView? = null
    private var digitalTimeRow: LinearLayout? = null
    private var focusCard: LinearLayout? = null
    private var focusTitleView: TextView? = null
    private var focusSubtitleView: TextView? = null
    private var allowanceStripContainer: LinearLayout? = null
    private var allowanceTickCount = 0
    private var customWallpaperView: ImageView? = null
    private var homeGrid: GridLayout? = null
    private var dockRow: LinearLayout? = null
    private var productivityStrip: LinearLayout? = null
    private var wallpaperAccent: Int = ACCENT
    private var dockFocusButton: View? = null
    private var drawerOverlay: FrameLayout? = null
    private var drawerRecycler: RecyclerView? = null
    private var drawerSearchInput: EditText? = null
    private var isDrawerOpen = false
    private var swipeTouchStartY = 0f
    private var swipeVelocityTracker: VelocityTracker? = null

    private sealed class DrawerItem {
        data class Header(val letter: String) : DrawerItem()
        data class App(val packageName: String, val label: String) : DrawerItem()
    }

    private inner class DrawerAdapter(
        private val blockedPackages: Set<String>,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var items: List<DrawerItem> = emptyList()

        fun setItems(next: List<DrawerItem>) {
            items = next
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun getItemViewType(position: Int): Int = when (items[position]) {
            is DrawerItem.Header -> DRAWER_TYPE_HEADER
            is DrawerItem.App -> DRAWER_TYPE_APP
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == DRAWER_TYPE_HEADER) {
                val header = TextView(parent.context).apply {
                    textSize = SIZE_SECTION_HDR
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    setTextColor(ACCENT_TEXT)
                    letterSpacing = 0.12f
                    setPadding(dp(16), dp(10), dp(16), dp(4))
                    layoutParams = RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT,
                        RecyclerView.LayoutParams.WRAP_CONTENT,
                    )
                }
                return HeaderViewHolder(header)
            }

            val item = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT,
                )
                setPadding(dp(4), dp(8), dp(4), dp(2))
                isClickable = true
                isFocusable = true
            }
            val iconFrame = FrameLayout(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(62), dp(62)).also {
                    it.gravity = Gravity.CENTER_HORIZONTAL
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(12).toFloat()
                    setColor(GLASS_ULTRA)
                }
            }
            val iconView = ImageView(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(dp(52), dp(52)).also {
                    it.gravity = Gravity.CENTER
                }
            }
            val labelView = TextView(parent.context).apply {
                textSize = SIZE_LABEL_HOME
                setTextColor(TEXT_DIM)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setShadowLayer(2f, 0f, 1f, Color.parseColor("#CC000000"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = dp(6) }
            }
            iconFrame.addView(iconView)
            item.addView(iconFrame)
            item.addView(labelView)
            item.foreground = rippleForeground(12)
            addPressAnimation(item)
            return AppViewHolder(item, iconView, labelView)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is DrawerItem.Header -> (holder as HeaderViewHolder).view.text = item.letter
                is DrawerItem.App -> {
                    val appHolder = holder as AppViewHolder
                    val isBlocked = blockedPackages.contains(item.packageName)
                    appHolder.icon.setImageDrawable(
                        try {
                            packageManager.getApplicationIcon(item.packageName)
                        } catch (_: Exception) {
                            null
                        },
                    )
                    appHolder.icon.alpha = if (isBlocked) 0.28f else 1f
                    appHolder.label.text = item.label
                    appHolder.label.setTextColor(if (isBlocked) TEXT_MUTED else TEXT_DIM)
                    appHolder.itemView.contentDescription =
                        if (isBlocked) "${item.label}, blocked" else item.label
                    appHolder.itemView.setOnClickListener {
                        closeDrawer()
                        if (isBlocked) launchBlockOverlay(item.packageName)
                        else launchApp(item.packageName)
                    }
                    appHolder.itemView.setOnLongClickListener {
                        showDrawerIconMenu(item.packageName, item.label)
                        true
                    }
                }
            }
        }
    }

    private class HeaderViewHolder(val view: TextView) : RecyclerView.ViewHolder(view)

    private class AppViewHolder(
        view: View,
        val icon: ImageView,
        val label: TextView,
    ) : RecyclerView.ViewHolder(view)

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PREF_LAUNCHER_PINNED ||
            key == PREF_LAUNCHER_DOCK ||
            key == PREF_LAUNCHER_HIDDEN ||
            key == PREF_SA_ACTIVE ||
            key == PREF_SA_PKGS ||
            key == PREF_SA_UNTIL ||
            key == PREF_ALWAYS_BLOCK ||
            key == PREF_ALWAYS_BLOCK_PKGS ||
            key == PREF_LAUNCHER_WALLPAPER ||
            key == "launcher_clock_style" ||
            key == "next_task_name"
        ) {
            runOnUiThread {
                refreshHomeGrid()
                refreshDock()
                updateClockText()
                refreshProductivityStrip()
                loadCustomWallpaper()
                applyWallpaperTint()
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        rootFrame = FrameLayout(this)
        setContentView(rootFrame)

        buildHomeLayout()
        applyWallpaperTint()
        startClock()
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        refreshHomeGrid()
        refreshDock()
        refreshAllowanceStrip()
        refreshProductivityStrip()
        loadCustomWallpaper()
        applyWallpaperTint()
    }

    override fun onPause() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onPause()
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onDestroy()
        clockRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onBackPressed() {
        if (isDrawerOpen) closeDrawer()
        // Intentionally swallow back — no parent activity on home screen
    }

    // ── Home layout ───────────────────────────────────────────────────────────

    private fun buildHomeLayout() {
        // A selected launcher wallpaper sits above the system wallpaper but
        // below the scrim and all launcher controls. Empty means use the
        // device's normal wallpaper via FLAG_SHOW_WALLPAPER.
        customWallpaperView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootFrame.addView(customWallpaperView)
        loadCustomWallpaper()

        // Wallpaper scrim — light translucent overlay so the user's wallpaper
        // stays visible. FLAG_SHOW_WALLPAPER composites it behind the window.
        val scrim = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(SCRIM_FULL_TOP, SCRIM_FULL_BTM)
            )
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootFrame.addView(scrim)

        val gradDock = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, SCRIM_DOCK_BTM)
            )
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(240)
            ).also { it.gravity = Gravity.BOTTOM }
        }
        rootFrame.addView(gradDock)

        val gradTop = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.parseColor("#44000000"), Color.TRANSPARENT)
            )
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(180)
            ).also { it.gravity = Gravity.TOP }
        }
        rootFrame.addView(gradTop)

        // Root column
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // ── Clock widget ──────────────────────────────────────────────────────
        val clockWidget = buildClockWidget()
        column.addView(clockWidget)

        // ── Active focus session ──────────────────────────────────────────────
        column.addView(buildFocusSessionCard())

        // ── Productivity summary ──────────────────────────────────────────────
        column.addView(buildProductivityStrip())

        // ── Daily allowance strip ─────────────────────────────────────────────
        column.addView(buildAllowanceStrip())

        // ── Home screen grid (scrollable) ─────────────────────────────────────
        val gridScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            isVerticalScrollBarEnabled = false
        }

        homeGrid = GridLayout(this).apply {
            columnCount = 4
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(12), dp(16), dp(12), dp(16))
        }
        gridScroll.addView(homeGrid)

        // Long-press on scroll area (empty space) → add apps dialog
        gridScroll.setOnLongClickListener {
            showAddToHomeDialog()
            true
        }

        column.addView(gridScroll)

        // ── Search shortcut ────────────────────────────────────────────────────
        column.addView(buildSearchBar())

        // ── Dock area ─────────────────────────────────────────────────────────
        column.addView(buildDockArea())

        rootFrame.addView(column)

        refreshHomeGrid()
        refreshDock()
        refreshProductivityStrip()
    }

    /**
     * Preserve the launcher's global swipe gestures without making the root view
     * consume taps destined for child controls.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeTouchStartY = ev.rawY
                swipeVelocityTracker?.recycle()
                swipeVelocityTracker = VelocityTracker.obtain().also { it.addMovement(ev) }
            }
            MotionEvent.ACTION_MOVE -> swipeVelocityTracker?.addMovement(ev)
            MotionEvent.ACTION_UP -> {
                swipeVelocityTracker?.addMovement(ev)
                swipeVelocityTracker?.computeCurrentVelocity(1000)
                val velocityY = swipeVelocityTracker?.yVelocity ?: 0f
                swipeVelocityTracker?.recycle()
                swipeVelocityTracker = null
                val dy = swipeTouchStartY - ev.rawY
                when {
                    dy > dp(60) && velocityY < -250f && !isDrawerOpen -> {
                        openDrawer()
                        return true
                    }
                    dy < -dp(80) && velocityY > 250f -> {
                        expandNotificationsPanel()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                swipeVelocityTracker?.recycle()
                swipeVelocityTracker = null
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun buildClockWidget(): LinearLayout {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(UNIT * 2); it.bottomMargin = dp(UNIT) }
        }

        dateView = TextView(this).apply {
            textSize = SIZE_DATE
            setTextColor(TEXT_MUTED)
            gravity = Gravity.CENTER
            letterSpacing = 0.14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(10) }
        }
        wrap.addView(dateView)

        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(4) }
        }

        clockView = TextView(this).apply {
            textSize = SIZE_CLOCK_MAIN
            setTextColor(TEXT_DIM)
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        colonView = TextView(this).apply {
            text = ":"
            textSize = SIZE_CLOCK_MAIN * 0.85f
            setTextColor(TEXT_DIM)
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(10) }
        }

        minuteView = TextView(this).apply {
            textSize = SIZE_CLOCK_MAIN
            setTextColor(TEXT_PRIMARY)
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        ampmView = TextView(this).apply {
            textSize = SIZE_CLOCK_AMPM
            setTextColor(wallpaperAccent)
            gravity = Gravity.BOTTOM
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(22); it.leftMargin = dp(UNIT) }
        }

        timeRow.addView(clockView)
        timeRow.addView(colonView)
        timeRow.addView(minuteView)
        timeRow.addView(ampmView)
        digitalTimeRow = timeRow
        wrap.addView(timeRow)

        // Analog clock — shown instead of the digital row when style = "analog"
        analogClockView = AnalogClockView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(200), dp(200)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.topMargin = dp(4)
            }
            visibility = View.GONE
        }
        wrap.addView(analogClockView)

        updateClockText()
        return wrap
    }

    private fun buildSearchBar(): View {
        val searchBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24).toFloat()
                setColor(GLASS_MID)
                setStroke(dp(1), GLASS_BORDER_BRIGHT)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            ).also {
                it.setMargins(dp(UNIT * 2), dp(UNIT * 2), dp(UNIT * 2), dp(UNIT))
            }
            setPadding(dp(18), 0, dp(18), 0)
            contentDescription = "Search apps — opens app drawer"
            isClickable = true
            isFocusable = true
        }

        val searchIcon = object : View(this) {
            private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TEXT_MUTED
                style = Paint.Style.STROKE
                strokeWidth = dp(2).toFloat()
                strokeCap = Paint.Cap.ROUND
            }

            override fun onDraw(canvas: Canvas) {
                val radius = dp(6).toFloat()
                val centerX = dp(8).toFloat()
                val centerY = height / 2f - dp(1).toFloat()
                canvas.drawCircle(centerX, centerY, radius, iconPaint)
                canvas.drawLine(
                    centerX + dp(4).toFloat(),
                    centerY + dp(4).toFloat(),
                    centerX + dp(9).toFloat(),
                    centerY + dp(9).toFloat(),
                    iconPaint
                )
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            contentDescription = "Search"
        }

        val hint = TextView(this).apply {
            text = "Search apps"
            textSize = SIZE_SEARCH_HINT
            setTextColor(TEXT_MUTED)
            letterSpacing = 0.01f
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).also { it.leftMargin = dp(12) }
        }

        val micIcon = object : View(this) {
            private val micPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TEXT_MUTED
                style = Paint.Style.STROKE
                strokeWidth = dp(1.8f)
                strokeCap = Paint.Cap.ROUND
            }

            override fun onDraw(canvas: Canvas) {
                val cx = width / 2f
                val bodyPath = android.graphics.Path().apply {
                    addRoundRect(
                        cx - dp(2.5f), dp(2).toFloat(),
                        cx + dp(2.5f), dp(10).toFloat(),
                        dp(2).toFloat(), dp(2).toFloat(),
                        android.graphics.Path.Direction.CW
                    )
                }
                canvas.drawPath(bodyPath, micPaint)
                val curve = android.graphics.Path().apply {
                    moveTo(cx - dp(6).toFloat(), dp(9).toFloat())
                    cubicTo(
                        cx - dp(6).toFloat(), dp(16).toFloat(),
                        cx + dp(6).toFloat(), dp(16).toFloat(),
                        cx + dp(6).toFloat(), dp(9).toFloat()
                    )
                    moveTo(cx, dp(16).toFloat())
                    lineTo(cx, dp(19).toFloat())
                    moveTo(cx - dp(4).toFloat(), dp(19).toFloat())
                    lineTo(cx + dp(4).toFloat(), dp(19).toFloat())
                }
                canvas.drawPath(curve, micPaint)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            contentDescription = "Voice search"
        }

        searchBar.addView(searchIcon)
        searchBar.addView(hint)
        searchBar.addView(micIcon)
        searchBar.setOnClickListener {
            openDrawer()
            handler.postDelayed({ drawerSearchInput?.requestFocus() }, 150L)
        }
        searchBar.foreground = rippleForeground(24)
        return searchBar
    }

    private fun buildFocusSessionCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(dp(20), dp(UNIT), dp(20), dp(UNIT)) }
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            isFocusable = true
        }
        val layer0 = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#1A6366f1"), Color.parseColor("#0A000000"))
        ).apply { cornerRadius = dp(18).toFloat() }
        val layer1 = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1.5f), Color.parseColor("#4D6366f1"))
        }
        card.background = LayerDrawable(arrayOf(layer0, layer1))

        val iconFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ACCENT_SURFACE)
                setStroke(dp(1), ACCENT_DIM)
            }
        }
        val iconView = TextView(this).apply {
            text = "⏱"
            textSize = 17f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            contentDescription = "Active focus session"
        }
        iconFrame.addView(iconView)

        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.leftMargin = dp(12) }
        }
        val title = TextView(this).apply {
            textSize = 14f
            setTextColor(TEXT_PRIMARY)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val subtitle = TextView(this).apply {
            textSize = 12f
            setTextColor(TEXT_MUTED)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(3) }
        }
        labels.addView(title)
        labels.addView(subtitle)

        val chevron = TextView(this).apply {
            text = "›"
            textSize = 26f
            setTextColor(wallpaperAccent)
            gravity = Gravity.CENTER
            contentDescription = "Open active focus session"
        }
        card.addView(iconFrame)
        card.addView(labels)
        card.addView(chevron)
        focusCard = card
        focusTitleView = title
        focusSubtitleView = subtitle
        card.setOnClickListener { openFocusFlow() }
        card.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> if (animationsEnabled()) {
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80)
                        .setInterpolator(DecelerateInterpolator()).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (animationsEnabled()) {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(150)
                            .setInterpolator(android.view.animation.OvershootInterpolator(1.5f)).start()
                    } else {
                        v.scaleX = 1f
                        v.scaleY = 1f
                    }
                }
            }
            false
        }
        card.foreground = rippleForeground(18)
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
        val remaining = (endMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val minutes = remaining / 60_000L
        val seconds = (remaining / 1_000L) % 60L
        focusTitleView?.text = taskName
        focusSubtitleView?.text = if (endMs > 0L) {
            "Focused · %02d:%02d remaining".format(Locale.getDefault(), minutes, seconds)
        } else {
            "Focus session active"
        }
        card.visibility = View.VISIBLE
    }

    private fun openFocusFlow() {
        try {
            startActivity(Intent(this, com.tbtechs.focusflow.MainActivity::class.java))
        } catch (_: Exception) {
        }
    }

    private fun buildProductivityStrip(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(dp(20), dp(4), dp(20), dp(4)) }
            productivityStrip = this
        }
    }

    private fun buildChip(icon: String, label: String): View {
        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(44)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(GLASS_LIGHT)
                setStroke(dp(1), GLASS_BORDER)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)
            ).also { it.rightMargin = dp(6) }
            setPadding(dp(10), 0, dp(10), 0)
            contentDescription = label
            isClickable = true
            isFocusable = true
        }
        chip.addView(TextView(this).apply {
            text = icon
            textSize = 13f
        })
        chip.addView(TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(TEXT_DIM)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.leftMargin = dp(5) }
        })
        chip.foreground = rippleForeground(12)
        return chip
    }

    private fun refreshProductivityStrip() {
        val strip = productivityStrip ?: return
        strip.removeAllViews()
        val chips = mutableListOf<View>()
        val saActive = prefs.getBoolean(PREF_SA_ACTIVE, false)
        val alwaysActive = prefs.getBoolean(PREF_ALWAYS_BLOCK, false)
        val nextTask = prefs.getString("next_task_name", null)?.takeIf { it.isNotBlank() }
        if (saActive) {
            val count = parseJsonArray(prefs.getString(PREF_SA_PKGS, "[]") ?: "[]").size
            if (count > 0) chips += buildChip("🔒", "$count blocked")
        }
        if (alwaysActive) chips += buildChip("🛡️", "Always-On")
        if (nextTask != null) chips += buildChip("⏭", "Next: $nextTask")
        chips.forEach { strip.addView(it) }
        strip.visibility = if (chips.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun buildDockArea(): LinearLayout {
        val dockWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val quickActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.setMargins(dp(UNIT * 4), dp(12), dp(UNIT * 4), dp(UNIT))
            }
        }

        fun circleButton(isFocusFlow: Boolean): View {
            return object : View(this) {
                private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = TEXT_PRIMARY
                    style = Paint.Style.FILL
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                }

                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    iconPaint.color = if (isFocusFlow) wallpaperAccent else TEXT_PRIMARY
                    if (isFocusFlow) {
                        iconPaint.textSize = dp(22).toFloat()
                        val bounds = android.graphics.Rect()
                        iconPaint.getTextBounds("F", 0, 1, bounds)
                        val baseline = height / 2f - bounds.exactCenterY()
                        canvas.drawText("F", width / 2f, baseline, iconPaint)
                    } else {
                        val dotR = dp(2.5f)
                        val gap = dp(6)
                        val start = -gap
                        for (row in 0..2) for (col in 0..2) {
                            canvas.drawCircle(
                                width / 2f + start + col * gap,
                                height / 2f + start + row * gap,
                                dotR,
                                iconPaint
                            )
                        }
                    }
                }
            }.apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (isFocusFlow) ACCENT_SURFACE else GLASS_MID)
                    setStroke(dp(1.5f), if (isFocusFlow) ACCENT_DIM else GLASS_BORDER_BRIGHT)
                }
                layoutParams = LinearLayout.LayoutParams(dp(58), dp(58))
                contentDescription = if (isFocusFlow) "Open FocusFlow" else "All apps"
                isClickable = true
                isFocusable = true
            }
        }

        fun actionGroup(label: String, isFocusFlow: Boolean): LinearLayout {
            val group = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                contentDescription = label
                isClickable = true
                isFocusable = true
            }
            val button = circleButton(isFocusFlow)
            button.setOnClickListener {
                if (isFocusFlow) openFocusFlow() else openDrawer()
            }
            button.foreground = rippleForeground(999)
            addPressAnimation(button)
            val labelView = TextView(this).apply {
                text = label
                textSize = SIZE_LABEL_HOME
                setTextColor(TEXT_MUTED)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp(6) }
            }
            group.addView(button)
            group.addView(labelView)
            group.setOnClickListener {
                if (isFocusFlow) openFocusFlow() else openDrawer()
            }
            group.foreground = rippleForeground(999)
            addPressAnimation(group)
            return group
        }

        quickActions.addView(actionGroup("All Apps", false))
        val focusGroup = actionGroup("FocusFlow", true)
        dockFocusButton = focusGroup.getChildAt(0)
        quickActions.addView(focusGroup)
        dockWrapper.addView(quickActions)

        val dockCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(36).toFloat()
                setColor(GLASS_MID)
                setStroke(dp(1), GLASS_BORDER_BRIGHT)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(100)
            ).also {
                it.setMargins(dp(UNIT), dp(UNIT / 2), dp(UNIT), dp(UNIT * 3))
            }
            setPadding(dp(UNIT), 0, dp(UNIT), 0)
            elevation = dp(8).toFloat()
        }

        dockRow = dockCard
        dockWrapper.addView(dockCard)

        return dockWrapper
    }

    // ── Refresh home grid ──────────────────────────────────────────────────────

    private fun refreshHomeGrid() {
        val grid = homeGrid ?: return
        grid.removeAllViews()

        val pinnedJson = prefs.getString(PREF_LAUNCHER_PINNED, "[]") ?: "[]"
        val pinned = parseJsonArray(pinnedJson)
        val blocked = getBlockedPackages()

        for (pkg in pinned) {
            addHomeGridIcon(grid, pkg, blocked.contains(pkg))
        }
    }

    private fun loadCustomWallpaper() {
        val view = customWallpaperView ?: return
        val path = prefs.getString(PREF_LAUNCHER_WALLPAPER, "")?.trim().orEmpty()
        if (path.isEmpty()) {
            view.setImageDrawable(null)
            view.visibility = View.GONE
            return
        }

        val bitmap = try {
            if (path.startsWith("content://")) {
                contentResolver.openInputStream(Uri.parse(path))?.use(BitmapFactory::decodeStream)
            } else {
                BitmapFactory.decodeFile(path.removePrefix("file://"))
            }
        } catch (_: Exception) {
            null
        }

        if (bitmap != null) {
            view.setImageBitmap(bitmap)
            view.visibility = View.VISIBLE
        } else {
            view.setImageDrawable(null)
            view.visibility = View.GONE
        }
    }

    private fun addHomeGridIcon(parent: GridLayout, pkg: String, isBlocked: Boolean) {
        val pm = packageManager
        val appInfo = try { pm.getApplicationInfo(pkg, 0) } catch (_: Exception) { return }
        val label = pm.getApplicationLabel(appInfo).toString()
        val icon  = try { pm.getApplicationIcon(pkg) } catch (_: Exception) { return }

        val colSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val lp = GridLayout.LayoutParams(colSpec, colSpec)
            lp.width = 0
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT
            lp.setMargins(dp(3), dp(6), dp(3), dp(6))
            layoutParams = lp
            contentDescription = if (isBlocked) "$label, blocked" else label
            isClickable = true
            isFocusable = true
        }

        val iconFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(ICON_CELL_FRAME), dp(ICON_CELL_FRAME)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        val backdrop = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(if (isBlocked) Color.parseColor("#1AEF4444") else GLASS_ULTRA)
            }
            layoutParams = FrameLayout.LayoutParams(
                dp(ICON_CELL_FRAME), dp(ICON_CELL_FRAME)
            ).also { it.gravity = Gravity.CENTER }
        }
        iconFrame.addView(backdrop)

        val iconView = ImageView(this).apply {
            setImageDrawable(icon)
            alpha = if (isBlocked) 0.30f else 1f
            layoutParams = FrameLayout.LayoutParams(dp(ICON_HOME), dp(ICON_HOME)).also {
                it.gravity = Gravity.CENTER
            }
        }
        iconFrame.addView(iconView)

        if (isBlocked) {
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(RED_BLOCK)
                    setStroke(dp(1.5f), Color.parseColor("#BB000000"))
                }
                layoutParams = FrameLayout.LayoutParams(dp(12), dp(12)).also {
                    it.gravity = Gravity.TOP or Gravity.END
                    it.topMargin = dp(1)
                    it.rightMargin = dp(1)
                }
            }
            iconFrame.addView(dot)
        }

        val labelView = TextView(this).apply {
            text = label
            textSize = SIZE_LABEL_HOME
            setTextColor(if (isBlocked) TEXT_BLOCKED else TEXT_DIM)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setShadowLayer(5f, 0f, 2f, Color.parseColor("#BB000000"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(6) }
        }

        item.addView(iconFrame)
        item.addView(labelView)

        item.setOnClickListener {
            if (isBlocked) launchBlockOverlay(pkg) else launchApp(pkg)
        }

        item.setOnLongClickListener {
            showHomeIconMenu(pkg, label)
            true
        }
        item.foreground = rippleForeground()
        addPressAnimation(item)

        parent.addView(item)
    }

    // ── Refresh dock ──────────────────────────────────────────────────────────

    private fun refreshDock() {
        val row = dockRow ?: return
        row.removeAllViews()

        val dockJson = prefs.getString(PREF_LAUNCHER_DOCK, "[]") ?: "[]"
        val dockPkgs = parseJsonArray(dockJson)
        val blocked  = getBlockedPackages()

        for (pkg in dockPkgs.take(5)) {
            addDockIcon(row, pkg, blocked.contains(pkg))
        }
    }

    private fun addDockIcon(parent: LinearLayout, pkg: String, isBlocked: Boolean) {
        val pm = packageManager
        val appInfo = try { pm.getApplicationInfo(pkg, 0) } catch (_: Exception) { return }
        val label = pm.getApplicationLabel(appInfo).toString()
        val icon  = try { pm.getApplicationIcon(pkg) } catch (_: Exception) { return }

        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(4), dp(8), dp(4), dp(6))
            contentDescription = if (isBlocked) "$label, blocked" else label
            isClickable = true
            isFocusable = true
        }

        val iconFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(ICON_DOCK_FRAME), dp(ICON_DOCK_FRAME)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        val backdrop = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(GLASS_ULTRA)
            }
            layoutParams = FrameLayout.LayoutParams(
                dp(ICON_DOCK_FRAME), dp(ICON_DOCK_FRAME)
            ).also { it.gravity = Gravity.CENTER }
        }
        iconFrame.addView(backdrop)

        val iconView = ImageView(this).apply {
            setImageDrawable(icon)
            alpha = if (isBlocked) 0.30f else 1f
            contentDescription = if (isBlocked) "$label, blocked" else label
            layoutParams = FrameLayout.LayoutParams(dp(ICON_DOCK), dp(ICON_DOCK)).also {
                it.gravity = Gravity.CENTER
            }
        }
        iconFrame.addView(iconView)

        if (isBlocked) {
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(RED_BLOCK)
                    setStroke(dp(1.5f), Color.parseColor("#BB000000"))
                }
                layoutParams = FrameLayout.LayoutParams(dp(12), dp(12)).also {
                    it.gravity = Gravity.TOP or Gravity.END
                    it.topMargin = dp(1)
                    it.rightMargin = dp(1)
                }
            }
            iconFrame.addView(dot)
        }

        val labelView = TextView(this).apply {
            text = label
            textSize = SIZE_LABEL_DOCK
            setTextColor(TEXT_DIM)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setShadowLayer(5f, 0f, 2f, Color.parseColor("#BB000000"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(3) }
        }

        item.addView(iconFrame)
        item.addView(labelView)

        item.setOnClickListener {
            if (isBlocked) launchBlockOverlay(pkg) else launchApp(pkg)
        }

        item.setOnLongClickListener {
            showDockIconMenu(pkg, label)
            true
        }
        item.foreground = rippleForeground()
        addPressAnimation(item)

        parent.addView(item)
    }

    // ── App drawer ────────────────────────────────────────────────────────────

    private fun openDrawer() {
        if (isDrawerOpen) return
        isDrawerOpen = true

        val overlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
            alpha = 0f
        }

        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(DRAWER_BG)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.92).toInt()
            ).also { it.gravity = Gravity.BOTTOM }
            translationY = if (animationsEnabled()) {
                resources.displayMetrics.heightPixels.toFloat()
            } else {
                0f
            }
        }

        val topBar = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#40FFFFFF"), Color.TRANSPARENT)
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            )
        }
        sheet.addView(topBar)

        // Drag handle
        val handle = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor("#55AABBDD"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(4)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.topMargin = dp(12); it.bottomMargin = dp(14)
            }
        }
        sheet.addView(handle)

        // Drawer title
        val drawerTitle = TextView(this).apply {
            text = "All Apps"
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(10) }
        }
        sheet.addView(drawerTitle)

        // Search bar
        val searchBar = EditText(this).apply {
            hint = "Search apps…"
            setHintTextColor(TEXT_MUTED)
            setTextColor(TEXT_PRIMARY)
            textSize = 15f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(22).toFloat()
                setColor(GLASS_LIGHT)
                setStroke(dp(1), GLASS_BORDER)
            }
            setPadding(dp(18), 0, dp(18), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
            ).also { it.setMargins(dp(UNIT * 2), 0, dp(UNIT * 2), dp(UNIT * 2)) }
        }
        sheet.addView(searchBar)
        drawerSearchInput = searchBar

        // One recycled list for the complete drawer. Section headers occupy all
        // five columns; app cells occupy one. Filtering replaces the adapter's
        // data instead of walking and hiding already-created child views.
        val recycler = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            isVerticalScrollBarEnabled = false
        }

        val hiddenPkgs = parseJsonArray(prefs.getString(PREF_LAUNCHER_HIDDEN, "[]") ?: "[]").toSet()
        val blocked    = getBlockedPackages()
        val pm         = packageManager
        val allApps = loadDrawerApps(pm, hiddenPkgs)
        val adapter = DrawerAdapter(blocked)
        recycler.layoutManager = GridLayoutManager(this, 5).also { layoutManager ->
            layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (adapter.getItemViewType(position) == DRAWER_TYPE_HEADER) 5 else 1
                }
            }
        }
        recycler.adapter = adapter
        recycler.setPadding(dp(8), 0, dp(8), dp(24))
        adapter.setItems(buildDrawerItems(allApps, ""))
        drawerRecycler = recycler
        sheet.addView(recycler)
        overlay.addView(sheet)

        // Search filters the app data and submits a smaller list to the
        // RecyclerView. This keeps matching behavior correct even with a large
        // installed-app library and avoids stale hidden child views.
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.lowercase(Locale.getDefault())?.trim() ?: ""
                adapter.setItems(buildDrawerItems(allApps, q))
            }
        })

        // Swipe-down to close
        var swipeDownY = 0f
        var velocityTracker: VelocityTracker? = null
        sheet.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    swipeDownY = ev.rawY
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain().also { it.addMovement(ev) }
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(ev)
                    false
                }
                MotionEvent.ACTION_UP -> {
                    velocityTracker?.addMovement(ev)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vy = velocityTracker?.yVelocity ?: 0f
                    velocityTracker?.recycle()
                    velocityTracker = null
                    if (ev.rawY - swipeDownY > dp(80)) {
                        closeDrawer()
                        true
                    } else if (animationsEnabled() && vy > 800f) {
                        val fling = FlingAnimation(sheet, DynamicAnimation.TRANSLATION_Y).apply {
                            startVelocity = vy
                            minValue = 0f
                            maxValue = dp(1200).toFloat()
                            addEndListener { _, _, _, _ ->
                                if (isDrawerOpen) closeDrawer()
                            }
                        }
                        fling.start()
                        true
                    } else {
                        if (sheet.translationY > dp(200) || vy > 800f) {
                            closeDrawer()
                        } else if (animationsEnabled()) {
                            sheet.animate().translationY(0f).setDuration(200)
                                .setInterpolator(DecelerateInterpolator(1.5f)).start()
                        } else {
                            sheet.translationY = 0f
                        }
                        false
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    velocityTracker?.recycle()
                    velocityTracker = null
                    false
                }
                else -> false
            }
        }

        drawerOverlay = overlay
        rootFrame.addView(overlay)

        if (animationsEnabled()) {
            overlay.animate().alpha(1f).setDuration(300).start()
            sheet.animate().translationY(0f).setDuration(380)
                .setInterpolator(DecelerateInterpolator(2.2f)).start()
        } else {
            overlay.alpha = 1f
            sheet.translationY = 0f
        }
    }

    private fun loadDrawerApps(pm: PackageManager, hiddenPackages: Set<String>): List<DrawerItem.App> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { info ->
                val pkg = info.activityInfo.packageName
                if (pkg == OWN_PACKAGE || hiddenPackages.contains(pkg)) return@mapNotNull null
                DrawerItem.App(
                    packageName = pkg,
                    label = pm.getApplicationLabel(info.activityInfo.applicationInfo).toString(),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun buildDrawerItems(
        apps: List<DrawerItem.App>,
        query: String,
    ): List<DrawerItem> {
        val matching = if (query.isBlank()) apps else apps.filter {
            it.label.lowercase(Locale.getDefault()).contains(query) ||
                it.packageName.lowercase(Locale.getDefault()).contains(query)
        }
        val sections = matching.groupBy {
            val first = it.label.firstOrNull()?.uppercaseChar() ?: '#'
            if (first.isLetter()) first else '#'
        }.toSortedMap(compareBy { if (it == '#') '\uFFFF' else it })

        return buildList {
            sections.forEach { (letter, sectionApps) ->
                add(DrawerItem.Header(letter.toString()))
                addAll(sectionApps)
            }
        }
    }

    private fun closeDrawer() {
        val overlay = drawerOverlay ?: return
        val sheet = overlay.getChildAt(0)
        isDrawerOpen = false

        if (animationsEnabled() && sheet != null) {
            val targetY = sheet.height.toFloat().coerceAtLeast(dp(600).toFloat())
            sheet.animate()
                .translationY(targetY)
                .setDuration(280)
                .setInterpolator(AccelerateInterpolator(1.8f))
                .withEndAction {
                    rootFrame.removeView(overlay)
                    drawerOverlay = null
                    drawerRecycler = null
                    drawerSearchInput = null
                }
                .start()
            overlay.animate().alpha(0f).setDuration(240).start()
        } else {
            rootFrame.removeView(overlay)
            drawerOverlay = null
            drawerRecycler = null
            drawerSearchInput = null
        }
    }

    // ── Long-press context menus ───────────────────────────────────────────────

    private fun showHomeIconMenu(pkg: String, label: String) {
        AlertDialog.Builder(this)
            .setTitle(label)
            .setItems(arrayOf("Remove from Home", "Add to Dock", "App Info")) { _, which ->
                when (which) {
                    0 -> removeFromHome(pkg)
                    1 -> addToDock(pkg)
                    2 -> openAppInfo(pkg)
                }
            }
            .create()
            .show()
    }

    private fun showDockIconMenu(pkg: String, label: String) {
        AlertDialog.Builder(this)
            .setTitle(label)
            .setItems(arrayOf("Remove from Dock", "App Info")) { _, which ->
                when (which) {
                    0 -> removeFromDock(pkg)
                    1 -> openAppInfo(pkg)
                }
            }
            .create()
            .show()
    }

    private fun showDrawerIconMenu(pkg: String, label: String) {
        AlertDialog.Builder(this)
            .setTitle(label)
            .setItems(arrayOf("Add to Home Screen", "Add to Dock", "App Info")) { _, which ->
                when (which) {
                    0 -> addToHome(pkg)
                    1 -> addToDock(pkg)
                    2 -> openAppInfo(pkg)
                }
            }
            .create()
            .show()
    }

    private fun showAddToHomeDialog() {
        val pm     = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps   = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != OWN_PACKAGE }
            .sortedBy { pm.getApplicationLabel(it.activityInfo.applicationInfo).toString() }

        val names = apps.map {
            pm.getApplicationLabel(it.activityInfo.applicationInfo).toString()
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Add to Home Screen")
            .setItems(names) { _, idx ->
                addToHome(apps[idx].activityInfo.packageName)
            }
            .create()
            .show()
    }

    // ── Home / Dock management ─────────────────────────────────────────────────

    private fun addToHome(pkg: String) {
        val json    = prefs.getString(PREF_LAUNCHER_PINNED, "[]") ?: "[]"
        val current = parseJsonArray(json).toMutableList()
        if (!current.contains(pkg)) {
            current.add(pkg)
            saveJsonArray(PREF_LAUNCHER_PINNED, current)
            refreshHomeGrid()
        }
    }

    private fun removeFromHome(pkg: String) {
        val json    = prefs.getString(PREF_LAUNCHER_PINNED, "[]") ?: "[]"
        val updated = parseJsonArray(json).filter { it != pkg }
        saveJsonArray(PREF_LAUNCHER_PINNED, updated)
        refreshHomeGrid()
    }

    private fun addToDock(pkg: String) {
        val json    = prefs.getString(PREF_LAUNCHER_DOCK, "[]") ?: "[]"
        val current = parseJsonArray(json).toMutableList()
        if (!current.contains(pkg) && current.size < 5) {
            current.add(pkg)
            saveJsonArray(PREF_LAUNCHER_DOCK, current)
            refreshDock()
        } else if (current.size >= 5) {
            AlertDialog.Builder(this)
                .setTitle("Dock is full")
                .setMessage("Remove an existing dock app first (long-press it on the home screen).")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun removeFromDock(pkg: String) {
        val json    = prefs.getString(PREF_LAUNCHER_DOCK, "[]") ?: "[]"
        val updated = parseJsonArray(json).filter { it != pkg }
        saveJsonArray(PREF_LAUNCHER_DOCK, updated)
        refreshDock()
    }

    private fun openAppInfo(pkg: String) {
        val i = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:$pkg")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { startActivity(i) } catch (_: Exception) {}
    }

    // ── Launch helpers ────────────────────────────────────────────────────────

    private fun launchApp(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { startActivity(i) } catch (_: Exception) {}
    }

    private fun launchBlockOverlay(pkg: String) {
        val i = Intent(this, BlockOverlayActivity::class.java).apply {
            putExtra("blocked_package", pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try { startActivity(i) } catch (_: Exception) {}
    }

    // ── Block-state helpers ───────────────────────────────────────────────────

    private fun getBlockedPackages(): Set<String> {
        val now    = System.currentTimeMillis()
        val result = mutableSetOf<String>()

        val saActive = prefs.getBoolean(PREF_SA_ACTIVE, false)
        if (saActive) {
            val until = prefs.getLong(PREF_SA_UNTIL, 0L)
            if (until == 0L || now <= until) {
                result.addAll(parseJsonArray(prefs.getString(PREF_SA_PKGS, "[]") ?: "[]"))
            }
        }

        val alwaysActive = prefs.getBoolean(PREF_ALWAYS_BLOCK, false)
        if (alwaysActive) {
            result.addAll(parseJsonArray(prefs.getString(PREF_ALWAYS_BLOCK_PKGS, "[]") ?: "[]"))
        }

        return result
    }

    // ── Clock ─────────────────────────────────────────────────────────────────

    private fun startClock() {
        clockRunnable = object : Runnable {
            override fun run() {
                updateClockText()
                // Update every second for accurate display
                handler.postDelayed(this, 1_000L)
            }
        }
        handler.post(clockRunnable!!)
    }

    private fun updateClockText() {
        val now = Date()
        val use24h = android.text.format.DateFormat.is24HourFormat(this)
        val isAnalog = prefs.getString("launcher_clock_style", "digital") == "analog"

        if (isAnalog) {
            digitalTimeRow?.visibility = View.GONE
            analogClockView?.visibility = View.VISIBLE
            analogClockView?.invalidate()
        } else {
            digitalTimeRow?.visibility = View.VISIBLE
            analogClockView?.visibility = View.GONE
            if (use24h) {
                clockView?.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
                clockView?.setTextColor(TEXT_PRIMARY)
                colonView?.visibility = View.GONE
                minuteView?.visibility = View.GONE
                ampmView?.visibility = View.GONE
            } else {
                colonView?.visibility = View.VISIBLE
                minuteView?.visibility = View.VISIBLE
                ampmView?.visibility = View.VISIBLE
                clockView?.setTextColor(TEXT_DIM)
                clockView?.text = SimpleDateFormat("h", Locale.getDefault()).format(now)
                colonView?.text = ":"
                minuteView?.text = SimpleDateFormat("mm", Locale.getDefault()).format(now)
                ampmView?.text = SimpleDateFormat("a", Locale.getDefault()).format(now)
                ampmView?.setTextColor(wallpaperAccent)
            }
        }

        dateView?.text = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now)
        refreshFocusCard()
        allowanceTickCount++
        if (allowanceTickCount >= 60) {
            allowanceTickCount = 0
            refreshAllowanceStrip()
            refreshProductivityStrip()
        }
    }

    // ── Notification shade ────────────────────────────────────────────────────

    /**
     * Expands the notification shade panel on swipe-down.
     * Requires android.permission.EXPAND_STATUS_BAR in the manifest.
     */
    @Suppress("UNCHECKED_CAST")
    private fun expandNotificationsPanel() {
        try {
            val sbService = getSystemService("statusbar")
            val sbClass   = Class.forName("android.app.StatusBarManager")
            sbClass.getMethod("expandNotificationsPanel").invoke(sbService)
        } catch (_: Exception) {}
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private fun parseJsonArray(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveJsonArray(key: String, list: List<String>) {
        val json = "[${list.joinToString(",") { "\"$it\"" }}]"
        prefs.edit().putString(key, json).apply()
    }

    // ── Daily Allowance Strip ─────────────────────────────────────────────────

    private fun buildAllowanceStrip(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
        }
        allowanceStripContainer = container
        return container
    }

    private fun refreshAllowanceStrip() {
        val container = allowanceStripContainer ?: return
        container.removeAllViews()
        val cards = loadAllowanceCardData()
        if (cards.isEmpty()) {
            container.visibility = View.GONE
            return
        }

        val label = TextView(this).apply {
            text = "TODAY'S LIMITS"
            textSize = 10f
            setTextColor(TEXT_MUTED)
            letterSpacing = 0.08f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(dp(16), dp(10), dp(16), dp(4)) }
        }
        container.addView(label)

        val scroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(12), 0, dp(12), dp(10))
        }
        cards.forEach { card -> row.addView(buildAllowanceCard(card)) }
        scroll.addView(row)
        container.addView(scroll)
        container.visibility = View.VISIBLE
    }

    private fun buildAllowanceCard(card: AllowanceCardData): View {
        val fillColor = when {
            card.fraction > 0.5f  -> Color.parseColor("#4CAF50")
            card.fraction > 0.25f -> Color.parseColor("#FF9800")
            else                  -> Color.parseColor("#F44336")
        }
        val borderColor = when {
            card.fraction > 0.5f  -> Color.parseColor("#334CAF50")
            card.fraction > 0.25f -> Color.parseColor("#33FF9800")
            else                  -> Color.parseColor("#33F44336")
        }

        val cardView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT).also {
                it.setMargins(dp(4), 0, dp(4), 0)
            }
            setPadding(dp(6), dp(8), dp(6), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#1A1F2E"))
                setStroke(dp(1), borderColor)
            }
        }

        val iconView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
            scaleType = ImageView.ScaleType.FIT_CENTER
            card.icon?.let { setImageDrawable(it) }
        }
        cardView.addView(iconView)

        val nameView = TextView(this).apply {
            text = card.label
            textSize = 10f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(4) }
        }
        cardView.addView(nameView)

        val fraction = card.fraction.coerceIn(0f, 1f)
        val progressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(4)
            ).also { it.topMargin = dp(5) }
            background = GradientDrawable().apply {
                cornerRadius = dp(2).toFloat()
                setColor(Color.parseColor("#22FFFFFF"))
            }
        }
        if (fraction > 0f) {
            progressRow.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, fraction)
                background = GradientDrawable().apply {
                    cornerRadius = dp(2).toFloat()
                    setColor(fillColor)
                }
            })
        }
        if (fraction < 1f) {
            progressRow.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - fraction)
            })
        }
        cardView.addView(progressRow)

        val remainingView = TextView(this).apply {
            text = card.displayText
            textSize = 9f
            setTextColor(TEXT_DIM)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(3) }
        }
        cardView.addView(remainingView)

        return cardView
    }

    private fun loadAllowanceCardData(): List<AllowanceCardData> {
        val configJson = prefs.getString("daily_allowance_config", null) ?: return emptyList()
        if (configJson.isBlank() || configJson == "null") return emptyList()

        val usedJson = prefs.getString(AppBlockerAccessibilityService.PREF_DAILY_ALLOWANCE_USED, "{}") ?: "{}"
        val allUsed  = try { org.json.JSONObject(usedJson) } catch (_: Exception) { org.json.JSONObject() }
        val today    = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getDefault()
        }.format(Date())
        val now      = System.currentTimeMillis()
        val result   = mutableListOf<AllowanceCardData>()

        try {
            val arr = org.json.JSONArray(configJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val pkg = obj.optString("packageName", "").takeIf { it.isNotBlank() } ?: continue
                val mode    = obj.optString("mode", "count")
                val pkgUsed = allUsed.optJSONObject(pkg)

                val icon  = try { packageManager.getApplicationIcon(pkg) } catch (_: Exception) { null }
                val label = try {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(pkg, 0)
                    ).toString()
                } catch (_: Exception) { pkg.substringAfterLast('.') }

                when (mode) {
                    "count" -> {
                        val countPerDay = obj.optInt("countPerDay", 1).coerceAtLeast(1)
                        val usedDate    = pkgUsed?.optString("date", "") ?: ""
                        val usedCount   = if (usedDate == today) pkgUsed?.optInt("count", 0) ?: 0 else 0
                        val remaining   = (countPerDay - usedCount).coerceAtLeast(0)
                        val fraction    = remaining.toFloat() / countPerDay.toFloat()
                        val display     = if (remaining == 0) "no opens left" else "$remaining/$countPerDay opens"
                        result.add(AllowanceCardData(pkg, label, icon, usedCount.toLong(), countPerDay.toLong(), remaining.toLong(), mode, display, fraction))
                    }
                    "time_budget" -> {
                        val budgetMs    = obj.optLong("budgetMinutes", 30L) * 60_000L
                        val usedDate    = pkgUsed?.optString("date", "") ?: ""
                        val usedMs      = if (usedDate == today) pkgUsed?.optLong("usedMs", 0L) ?: 0L else 0L
                        val remainingMs = (budgetMs - usedMs).coerceAtLeast(0L)
                        val fraction    = if (budgetMs > 0L) remainingMs.toFloat() / budgetMs.toFloat() else 0f
                        result.add(AllowanceCardData(pkg, label, icon, usedMs, budgetMs, remainingMs, mode, formatRemainingMs(remainingMs), fraction))
                    }
                    "interval" -> {
                        val intervalMs    = obj.optLong("intervalMinutes", 5L) * 60_000L
                        val windowMs      = obj.optLong("intervalHours", 1L) * 3_600_000L
                        val windowStartMs = pkgUsed?.optLong("windowStartMs", 0L) ?: 0L
                        val windowExpired = now > windowStartMs + windowMs
                        val usedMs        = if (windowExpired) 0L else pkgUsed?.optLong("usedMs", 0L) ?: 0L
                        val remainingMs   = (intervalMs - usedMs).coerceAtLeast(0L)
                        val fraction      = if (intervalMs > 0L) remainingMs.toFloat() / intervalMs.toFloat() else 0f
                        val display       = if (windowExpired) "reset" else formatRemainingMs(remainingMs)
                        result.add(AllowanceCardData(pkg, label, icon, usedMs, intervalMs, remainingMs, mode, display, fraction))
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

    private fun animationsEnabled(): Boolean {
        val scale = android.provider.Settings.Global.getFloat(
            contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        return scale > 0f
    }

    private fun contrastRatio(fg: Int, bg: Int): Double {
        fun linearize(c: Double): Double =
            if (c <= 0.04045) c / 12.92
            else Math.pow((c + 0.055) / 1.055, 2.2)

        fun luminance(color: Int): Double {
            val r = linearize(Color.red(color) / 255.0)
            val g = linearize(Color.green(color) / 255.0)
            val b = linearize(Color.blue(color) / 255.0)
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }

        val l1 = luminance(fg)
        val l2 = luminance(bg)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun rippleForeground(cornerDp: Int = 16): Drawable {
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(cornerDp).toFloat()
        }
        return RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#30FFFFFF")),
            null,
            mask
        )
    }

    private fun addPressAnimation(view: View) {
        view.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> if (animationsEnabled()) {
                    v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(100)
                        .setInterpolator(DecelerateInterpolator()).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (animationsEnabled()) {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(200)
                            .setInterpolator(android.view.animation.OvershootInterpolator(1.5f)).start()
                    } else {
                        v.scaleX = 1f
                        v.scaleY = 1f
                    }
                }
            }
            false
        }
    }

    private fun applyWallpaperTint() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return
        try {
            val colors = WallpaperManager.getInstance(this)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return
            val dominant = colors.primaryColor.toArgb()
            val r = ((Color.red(dominant) * 0.4f) + (Color.red(ACCENT) * 0.6f)).toInt()
            val g = ((Color.green(dominant) * 0.4f) + (Color.green(ACCENT) * 0.6f)).toInt()
            val b = ((Color.blue(dominant) * 0.4f) + (Color.blue(ACCENT) * 0.6f)).toInt()
            val candidate = Color.rgb(r, g, b)
            val darkBg = Color.parseColor("#111827")
            wallpaperAccent = if (contrastRatio(candidate, darkBg) >= 3.0) candidate else ACCENT
        } catch (_: Exception) {
            wallpaperAccent = ACCENT
        }
        ampmView?.setTextColor(wallpaperAccent)
        dockFocusButton?.invalidate()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun dp(v: Float) = (v * resources.displayMetrics.density + 0.5f).toInt()
}

/**
 * AnalogClockView — a Canvas-drawn analog clock face styled to match
 * the FocusFlow launcher's dark aesthetic (white hands, indigo accent,
 * subtle tick marks).
 *
 * Renders the current time each time invalidate() is called (driven by
 * LauncherActivity's 1-second clock tick).
 */
class AnalogClockView(context: android.content.Context) : android.view.View(context) {

    private val paintFace = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1A1F2E")
        style = android.graphics.Paint.Style.FILL
    }
    private val paintRim = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#6366f1")
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val paintHour = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    private val paintMinute = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    private val paintSecond = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#6366f1")
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    private val paintTick = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#55667799")
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val paintCenter = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#6366f1")
        style = android.graphics.Paint.Style.FILL
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val radius = minOf(cx, cy) - 6f

        // Face
        canvas.drawCircle(cx, cy, radius, paintFace)
        canvas.drawCircle(cx, cy, radius, paintRim)

        // Tick marks (12 hour marks, slightly longer)
        for (i in 0 until 60) {
            val angle = Math.toRadians((i * 6 - 90).toDouble())
            val isHour = i % 5 == 0
            val outerR = radius - 4f
            val innerR = if (isHour) radius - 16f else radius - 10f
            paintTick.strokeWidth = if (isHour) 3f else 1.5f
            paintTick.color = if (isHour)
                android.graphics.Color.parseColor("#99AAAACC")
            else
                android.graphics.Color.parseColor("#33667799")
            canvas.drawLine(
                cx + (innerR * Math.cos(angle)).toFloat(),
                cy + (innerR * Math.sin(angle)).toFloat(),
                cx + (outerR * Math.cos(angle)).toFloat(),
                cy + (outerR * Math.sin(angle)).toFloat(),
                paintTick
            )
        }

        // Current time
        val cal = java.util.Calendar.getInstance()
        val hours   = cal.get(java.util.Calendar.HOUR)
        val minutes = cal.get(java.util.Calendar.MINUTE)
        val seconds = cal.get(java.util.Calendar.SECOND)

        // Hour hand (moves smoothly with minutes)
        val hourAngle = Math.toRadians(((hours * 30 + minutes * 0.5f) - 90).toDouble())
        val hourLen = radius * 0.5f
        canvas.drawLine(
            cx, cy,
            cx + (hourLen * Math.cos(hourAngle)).toFloat(),
            cy + (hourLen * Math.sin(hourAngle)).toFloat(),
            paintHour
        )

        // Minute hand
        val minuteAngle = Math.toRadians(((minutes * 6 + seconds * 0.1f) - 90).toDouble())
        val minuteLen = radius * 0.72f
        canvas.drawLine(
            cx, cy,
            cx + (minuteLen * Math.cos(minuteAngle)).toFloat(),
            cy + (minuteLen * Math.sin(minuteAngle)).toFloat(),
            paintMinute
        )

        // Second hand
        val secondAngle = Math.toRadians((seconds * 6 - 90).toDouble())
        val secondLen = radius * 0.80f
        canvas.drawLine(
            cx - (secondLen * 0.15f * Math.cos(secondAngle)).toFloat(),
            cy - (secondLen * 0.15f * Math.sin(secondAngle)).toFloat(),
            cx + (secondLen * Math.cos(secondAngle)).toFloat(),
            cy + (secondLen * Math.sin(secondAngle)).toFloat(),
            paintSecond
        )

        // Center dot
        canvas.drawCircle(cx, cy, 6f, paintCenter)
    }
}
