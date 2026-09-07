package com.tbtechs.focusflow.services

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LauncherActivity — FocusFlow's full home-screen replacement.
 *
 * The home screen intentionally has one shared structure for both visual
 * themes: clock, status pill, current task, today's limits, search, and two
 * independent bottom actions. Classic is flat and performance-first; Glassy
 * reuses the existing wallpaper and frosted surface tokens.
 *
 * Swipe up opens the app drawer. The drawer shares functional filter chips
 * across both themes, while Classic is a dense list and Glassy is an editable
 * grid.
 */
class LauncherActivity : Activity() {

    companion object {
        private const val PREFS_NAME = AppBlockerAccessibilityService.PREFS_NAME
        private const val PREF_LAUNCHER_HIDDEN = "launcher_hidden_packages"
        private const val PREF_LAUNCHER_PINNED = "launcher_pinned_packages"
        private const val PREF_LAUNCHER_DOCK = "launcher_dock_packages"
        private const val PREF_LAUNCHER_WALLPAPER = "launcher_wallpaper"
        private const val PREF_LAUNCHER_THEME = "launcher_theme"
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
        private const val OWN_PACKAGE = "com.tbtechs.focusflow"

        private const val SIZE_CLOCK = 72f
        private const val SIZE_DATE = 13f
        private const val SIZE_SEARCH_HINT = 13f
        private const val SIZE_HOME_LABEL = 13f
        private const val UNIT = 8

        // Existing Glassy design tokens.
        private val GLASS_ULTRA = Color.parseColor("#0FFFFFFF")
        private val GLASS_LIGHT = Color.parseColor("#1AFFFFFF")
        private val GLASS_MID = Color.parseColor("#2AFFFFFF")
        private val GLASS_HEAVY = Color.parseColor("#3CFFFFFF")
        private val GLASS_BORDER = Color.parseColor("#20FFFFFF")
        private val GLASS_BORDER_BRIGHT = Color.parseColor("#35FFFFFF")
        private val ACCENT = Color.parseColor("#6366F1")
        private val ACCENT_TEXT = Color.parseColor("#818CF8")
        private val ACCENT_DIM = Color.parseColor("#406366F1")

        // Classic exact surfaces.
        private val CLASSIC_BACKGROUND = Color.parseColor("#0E0E0E")
        private val CLASSIC_STATUS = Color.parseColor("#1E1E1E")
        private val CLASSIC_CARD = Color.parseColor("#18181A")
        private val CLASSIC_BORDER = Color.parseColor("#252525")
        private val CLASSIC_MUTED = Color.parseColor("#8B92A5")
        private val CLASSIC_TEAL = Color.parseColor("#2DD4BF")
        private val CLASSIC_GREEN = Color.parseColor("#22C55E")

        private val TEXT_PRIMARY = Color.WHITE
        private val TEXT_DIM = Color.parseColor("#CCF0F4FF")
        private val TEXT_MUTED = Color.parseColor("#B3AAB8CC")
        private val RED_BLOCK = Color.parseColor("#EF4444")
    }

    private enum class LauncherTheme { CLASSIC, GLASSY }
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
    )

    private sealed class DrawerItem {
        data class App(val packageName: String, val label: String) : DrawerItem()
    }

    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var clockRunnable: Runnable? = null

    private lateinit var rootFrame: FrameLayout
    private var renderedTheme: LauncherTheme? = null
    private var clockView: TextView? = null
    private var dateView: TextView? = null
    private var focusCard: LinearLayout? = null
    private var focusTitleView: TextView? = null
    private var focusSubtitleView: TextView? = null
    private var focusProgressTrack: FrameLayout? = null
    private var focusProgressFill: View? = null
    private var allowanceContainer: LinearLayout? = null
    private var customWallpaperView: ImageView? = null
    private var wallpaperAccent: Int = ACCENT

    private var drawerOverlay: FrameLayout? = null
    private var drawerSheet: LinearLayout? = null
    private var drawerHeader: LinearLayout? = null
    private var drawerChipRow: LinearLayout? = null
    private var drawerRecycler: RecyclerView? = null
    private var drawerSearchInput: EditText? = null
    private var drawerCustomizeSheet: LinearLayout? = null
    private var drawerAdapter: DrawerAdapter? = null
    private var drawerAllApps: List<DrawerItem.App> = emptyList()
    private var drawerFilter = DrawerFilter.ALL
    private var drawerBlockedCount = 0
    private var isDrawerOpen = false
    private var isEditMode = false
    private var editDraftScale = 1f
    private var editDraftColumns = 4
    private var focusedDrawerPackage: String? = null
    private var swipeTouchStartY = 0f
    private var swipeVelocityTracker: VelocityTracker? = null

    private inner class DrawerAdapter(
        private val theme: LauncherTheme,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var items: MutableList<DrawerItem.App> = mutableListOf()
        private var iconScale = 1f

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
            return if (theme == LauncherTheme.CLASSIC) {
                createClassicHolder(parent)
            } else {
                createGlassyHolder(parent)
            }
        }

        private fun createClassicHolder(parent: ViewGroup): RecyclerView.ViewHolder {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(52)
                setPadding(dp(24), 0, dp(24), 0)
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    dp(52),
                )
            }
            val icon = ImageView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val label = TextView(parent.context).apply {
                textSize = 16f
                setTextColor(TEXT_PRIMARY)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ).also { it.leftMargin = dp(16) }
            }
            row.addView(icon)
            row.addView(label)
            row.addView(View(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1,
                ).also {
                    it.gravity = Gravity.BOTTOM
                    it.leftMargin = -dp(52)
                }
                setBackgroundColor(CLASSIC_BORDER)
            })
            return ClassicDrawerHolder(row, icon, label)
        }

        private fun createGlassyHolder(parent: ViewGroup): RecyclerView.ViewHolder {
            val cell = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    dp(92),
                )
                setPadding(dp(4), dp(2), dp(4), dp(2))
            }
            val icon = ImageView(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(dp(42), dp(42)).also {
                    it.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val label = TextView(parent.context).apply {
                textSize = SIZE_HOME_LABEL
                setTextColor(TEXT_DIM)
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
            cell.addView(label)
            cell.addView(badge)
            return GlassyDrawerHolder(cell, icon, label, badge)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            val blocked = getBlockedPackages().contains(item.packageName)
            when (holder) {
                is ClassicDrawerHolder -> {
                    holder.icon.setImageDrawable(getAppIcon(item.packageName))
                    holder.icon.alpha = if (blocked) 0.42f else 1f
                    holder.label.text = item.label
                    holder.label.setTextColor(if (blocked) TEXT_MUTED else TEXT_PRIMARY)
                    holder.itemView.contentDescription =
                        if (blocked) "${item.label}, blocked" else item.label
                    holder.itemView.setOnClickListener {
                        closeDrawer()
                        if (blocked) launchBlockOverlay(item.packageName) else launchApp(item.packageName)
                    }
                    holder.itemView.setOnLongClickListener {
                        showDrawerIconMenu(item.packageName, item.label)
                        true
                    }
                }
                is GlassyDrawerHolder -> {
                    val size = (42f * iconScale).toInt()
                    holder.icon.layoutParams = (holder.icon.layoutParams as FrameLayout.LayoutParams).also {
                        it.width = dp(size)
                        it.height = dp(size)
                    }
                    holder.icon.setImageDrawable(getAppIcon(item.packageName))
                    holder.icon.alpha = if (blocked) 0.45f else 1f
                    holder.label.text = item.label
                    holder.label.setTextColor(if (blocked) TEXT_MUTED else TEXT_DIM)
                    holder.badge.visibility = if (isEditMode) View.VISIBLE else View.GONE
                    holder.itemView.contentDescription =
                        if (blocked) "${item.label}, blocked" else item.label
                    holder.itemView.setOnClickListener {
                        if (!isEditMode) {
                            closeDrawer()
                            if (blocked) launchBlockOverlay(item.packageName) else launchApp(item.packageName)
                        }
                    }
                    holder.itemView.setOnLongClickListener {
                        focusedDrawerPackage = item.packageName
                        if (isEditMode) false else {
                            showDrawerIconMenu(item.packageName, item.label)
                            true
                        }
                    }
                }
            }
        }
    }

    private class ClassicDrawerHolder(
        view: View,
        val icon: ImageView,
        val label: TextView,
    ) : RecyclerView.ViewHolder(view)

    private class GlassyDrawerHolder(
        view: View,
        val icon: ImageView,
        val label: TextView,
        val badge: TextView,
    ) : RecyclerView.ViewHolder(view)

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PREF_LAUNCHER_THEME ||
            key == PREF_LAUNCHER_WALLPAPER ||
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
            key == AppBlockerAccessibilityService.PREF_DAILY_ALLOWANCE_USED
        ) {
            runOnUiThread {
                if (key == PREF_LAUNCHER_THEME || key == PREF_LAUNCHER_WALLPAPER) {
                    buildHomeLayout()
                } else {
                    refreshHomeContent()
                    if (isDrawerOpen) refreshDrawerItems()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        rootFrame = FrameLayout(this)
        setContentView(rootFrame)
        buildHomeLayout()
        startClock()
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        if (renderedTheme != currentTheme()) buildHomeLayout() else refreshHomeContent()
    }

    override fun onPause() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onPause()
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        clockRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (isDrawerOpen) closeDrawer()
    }

    // ── Shared home structure ──────────────────────────────────────────────────

    private fun currentTheme(): LauncherTheme =
        if (prefs.getString(PREF_LAUNCHER_THEME, "glassy") == "classic") {
            LauncherTheme.CLASSIC
        } else {
            LauncherTheme.GLASSY
        }

    private fun buildHomeLayout() {
        val theme = currentTheme()
        renderedTheme = theme
        rootFrame.removeAllViews()
        rootFrame.setBackgroundColor(
            if (theme == LauncherTheme.CLASSIC) CLASSIC_BACKGROUND else Color.TRANSPARENT,
        )
        clockView = null
        dateView = null
        focusCard = null
        allowanceContainer = null

        if (theme == LauncherTheme.GLASSY) {
            customWallpaperView = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
            }
            rootFrame.addView(customWallpaperView)
            loadCustomWallpaper()
            rootFrame.addView(View(this).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.parseColor("#26000000"), Color.parseColor("#88000000")),
                )
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
            })
        } else {
            customWallpaperView = null
        }

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(24))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        column.addView(buildClockWidget())
        column.addView(buildStatusPill())
        column.addView(buildFocusSessionCard())
        column.addView(buildTodaysLimits())
        column.addView(buildSearchBar())
        column.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        })
        column.addView(buildTwoIconRow())
        scroll.addView(column)
        rootFrame.addView(scroll)
        refreshHomeContent()
        applyWallpaperTint()
    }

    private fun buildClockWidget(): LinearLayout {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = dp(16) }
        }
        dateView = TextView(this).apply {
            textSize = SIZE_DATE
            setTextColor(if (currentTheme() == LauncherTheme.CLASSIC) Color.parseColor("#6F6F73") else TEXT_MUTED)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = dp(8) }
        }
        clockView = TextView(this).apply {
            textSize = SIZE_CLOCK
            setTextColor(if (currentTheme() == LauncherTheme.CLASSIC) TEXT_PRIMARY else TEXT_DIM)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            gravity = Gravity.CENTER
            includeFontPadding = true
        }
        wrap.addView(dateView)
        wrap.addView(clockView)
        updateClockText()
        return wrap
    }

    private fun buildStatusPill(): View {
        val theme = currentTheme()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = roundedBackground(
                if (theme == LauncherTheme.CLASSIC) CLASSIC_STATUS else GLASS_LIGHT,
                if (theme == LauncherTheme.CLASSIC) Color.parseColor("#2A2A2A") else GLASS_BORDER,
                32,
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(32),
            ).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.bottomMargin = dp(16)
            }
            setPadding(dp(13), 0, dp(13), 0)
            val icon = TextView(this@LauncherActivity).apply {
                text = "♢"
                textSize = 15f
                setTextColor(if (theme == LauncherTheme.CLASSIC) CLASSIC_MUTED else TEXT_DIM)
            }
            val text = TextView(this@LauncherActivity).apply {
                textSize = 13f
                setTextColor(if (theme == LauncherTheme.CLASSIC) CLASSIC_MUTED else TEXT_DIM)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.leftMargin = dp(6) }
            }
            addView(icon)
            addView(text)
            tag = text
            text.text = "${getBlockedPackages().size} blocked • ${statusLabel()}"
        }
    }

    private fun refreshStatusPillChildren() {
        val column = (rootFrame.getChildAt(0) as? ScrollView)?.getChildAt(0) as? LinearLayout ?: return
        val pill = column.getChildAt(1) as? LinearLayout ?: return
        val text = pill.tag as? TextView ?: return
        val blockedCount = getBlockedPackages().size
        text.text = "$blockedCount blocked • ${statusLabel()}"
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
        val theme = currentTheme()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = if (theme == LauncherTheme.CLASSIC) {
                roundedBackground(CLASSIC_CARD, CLASSIC_BORDER, 20)
            } else {
                layeredGlassBackground(20)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = dp(16) }
            setPadding(dp(20), dp(16), dp(20), dp(16))
            isClickable = true
            isFocusable = true
            setOnClickListener { openFocusFlow() }
        }
        val header = TextView(this).apply {
            text = "Current Task"
            textSize = 16f
            setTextColor(if (theme == LauncherTheme.CLASSIC) Color.parseColor("#B8B8BC") else TEXT_DIM)
        }
        val title = TextView(this).apply {
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(10) }
        }
        val track = FrameLayout(this).apply {
            background = roundedBackground(
                if (theme == LauncherTheme.CLASSIC) CLASSIC_BORDER else Color.parseColor("#66FFFFFF"),
                Color.TRANSPARENT,
                4,
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(4),
            ).also { it.topMargin = dp(16) }
        }
        val fill = View(this).apply {
            background = roundedBackground(
                if (theme == LauncherTheme.CLASSIC) CLASSIC_GREEN else Color.parseColor("#D9E1F2"),
                Color.TRANSPARENT,
                4,
            )
            layoutParams = FrameLayout.LayoutParams(0, dp(4))
        }
        track.addView(fill)
        val subtitle = TextView(this).apply {
            textSize = 13f
            setTextColor(if (theme == LauncherTheme.CLASSIC) Color.parseColor("#B8B8BC") else TEXT_MUTED)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(12) }
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
        card.foreground = rippleForeground(20)
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
            return
        }
        val theme = currentTheme()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = if (theme == LauncherTheme.CLASSIC) {
                roundedBackground(CLASSIC_CARD, CLASSIC_BORDER, 20)
            } else {
                layeredGlassBackground(20)
            }
            setPadding(dp(20), dp(16), dp(20), dp(14))
        }
        card.addView(TextView(this).apply {
            text = "Today's Limits"
            textSize = 18f
            setTextColor(if (theme == LauncherTheme.CLASSIC) Color.parseColor("#B8B8BC") else TEXT_DIM)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = dp(12) }
        })
        cards.forEachIndexed { index, allowance ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(52)
                if (index > 0) setPadding(0, dp(8), 0, 0)
            }
            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                scaleType = ImageView.ScaleType.FIT_CENTER
                allowance.icon?.let { setImageDrawable(it) }
            }
            val name = TextView(this).apply {
                text = allowance.label
                textSize = 15f
                setTextColor(TEXT_PRIMARY)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ).also { it.leftMargin = dp(12) }
            }
            val value = TextView(this).apply {
                text = if (theme == LauncherTheme.CLASSIC) {
                    formatUsedTerse(allowance)
                } else {
                    formatUsedFull(allowance)
                }
                textSize = 13f
                setTextColor(if (theme == LauncherTheme.CLASSIC) CLASSIC_MUTED else TEXT_DIM)
                gravity = Gravity.RIGHT
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
            row.addView(icon)
            row.addView(name)
            row.addView(value)
            card.addView(row)
        }
        container.addView(card)
        container.visibility = View.VISIBLE
    }

    private fun buildSearchBar(): View {
        val theme = currentTheme()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = if (theme == LauncherTheme.CLASSIC) {
                roundedBackground(Color.TRANSPARENT, CLASSIC_BORDER, 22)
            } else {
                roundedBackground(GLASS_MID, GLASS_BORDER_BRIGHT, 22)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44),
            ).also { it.bottomMargin = dp(16) }
            setPadding(dp(16), 0, dp(16), 0)
            addView(searchGlyph(if (theme == LauncherTheme.CLASSIC) CLASSIC_MUTED else TEXT_MUTED))
            addView(TextView(this@LauncherActivity).apply {
                text = "Search apps"
                textSize = SIZE_SEARCH_HINT
                setTextColor(if (theme == LauncherTheme.CLASSIC) CLASSIC_MUTED else TEXT_MUTED)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ).also { it.leftMargin = dp(12) }
            })
            isClickable = true
            isFocusable = true
            contentDescription = "Search apps — opens app drawer"
            setOnClickListener {
                openDrawer()
                handler.postDelayed({ drawerSearchInput?.requestFocus() }, 150L)
            }
            foreground = rippleForeground(22)
        }
    }

    private fun buildTwoIconRow(): LinearLayout {
        val theme = currentTheme()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(12) }
        }
        row.addView(buildBottomAction("All Apps", false, theme))
        row.addView(buildBottomAction("FocusFlow", true, theme))
        return row
    }

    private fun buildBottomAction(
        label: String,
        isFocusFlow: Boolean,
        theme: LauncherTheme,
    ): View {
        val group = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val circle = if (isFocusFlow) {
            ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setImageDrawable(packageManager.getApplicationIcon(OWN_PACKAGE))
                background = if (theme == LauncherTheme.CLASSIC) {
                    ovalBackground(CLASSIC_STATUS)
                } else {
                    ovalBackground(GLASS_MID, GLASS_BORDER_BRIGHT)
                }
                contentDescription = label
                isClickable = true
                isFocusable = true
                setOnClickListener { openFocusFlow() }
                foreground = rippleForeground(999)
            }
        } else {
            object : View(this) {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                }

                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val dotR = dp(2.5f).toFloat()
                    val gap = dp(6).toFloat()
                    for (r in 0..2) for (c in 0..2) {
                        canvas.drawCircle(
                            width / 2f + (c - 1) * gap,
                            height / 2f + (r - 1) * gap,
                            dotR,
                            paint,
                        )
                    }
                }
            }.apply {
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
                background = if (theme == LauncherTheme.CLASSIC) {
                    ovalBackground(CLASSIC_STATUS)
                } else {
                    ovalBackground(GLASS_MID, GLASS_BORDER_BRIGHT)
                }
                contentDescription = label
                isClickable = true
                isFocusable = true
                setOnClickListener { openDrawer() }
                foreground = rippleForeground(999)
            }
        }
        group.addView(circle)
        group.addView(TextView(this).apply {
            text = label
            textSize = SIZE_HOME_LABEL
            setTextColor(if (theme == LauncherTheme.CLASSIC) Color.parseColor("#818187") else TEXT_DIM)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(8) }
        })
        group.setOnClickListener { if (isFocusFlow) openFocusFlow() else openDrawer() }
        return group
    }

    private fun refreshHomeContent() {
        if (!::rootFrame.isInitialized) return
        refreshStatusPillChildren()
        refreshFocusCard()
        refreshTodaysLimits()
        updateClockText()
        if (currentTheme() == LauncherTheme.GLASSY) {
            loadCustomWallpaper()
            applyWallpaperTint()
        }
    }

    // ── App drawer ─────────────────────────────────────────────────────────────

    private fun openDrawer() {
        if (isDrawerOpen) return
        isDrawerOpen = true
        drawerFilter = DrawerFilter.ALL
        isEditMode = false
        focusedDrawerPackage = null
        drawerBlockedCount = getBlockedPackages().size

        val theme = currentTheme()
        val overlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            alpha = if (animationsEnabled()) 0f else 1f
        }
        if (theme == LauncherTheme.GLASSY) {
            val wallpaper = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
            }
            loadWallpaperInto(wallpaper)
            overlay.addView(wallpaper)
            overlay.addView(View(this).apply {
                setBackgroundColor(Color.parseColor("#66000000"))
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
            })
        } else {
            overlay.setBackgroundColor(CLASSIC_BACKGROUND)
        }

        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = if (theme == LauncherTheme.CLASSIC) {
                GradientDrawable().apply { setColor(CLASSIC_BACKGROUND) }
            } else {
                GradientDrawable().apply { setColor(Color.parseColor("#220E1422")) }
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            setPadding(dp(24), dp(if (theme == LauncherTheme.CLASSIC) 56 else 20), dp(24), dp(12))
        }
        drawerSheet = sheet
        if (theme == LauncherTheme.GLASSY) {
            sheet.addView(buildDrawerHandle())
        }
        drawerHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44),
            ).also { it.bottomMargin = dp(12) }
        }
        sheet.addView(drawerHeader)
        renderDrawerHeader()

        val search = EditText(this).apply {
            hint = if (theme == LauncherTheme.CLASSIC) "Search apps" else "Search FocusFlow"
            setHintTextColor(if (theme == LauncherTheme.CLASSIC) CLASSIC_MUTED else TEXT_MUTED)
            setTextColor(TEXT_PRIMARY)
            textSize = SIZE_SEARCH_HINT
            setSingleLine(true)
            background = if (theme == LauncherTheme.CLASSIC) {
                roundedBackground(Color.TRANSPARENT, CLASSIC_BORDER, 22)
            } else {
                roundedBackground(GLASS_MID, CLASSIC_TEAL, 22)
            }
            setPadding(dp(18), 0, dp(18), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44),
            ).also { it.bottomMargin = dp(12) }
        }
        drawerSearchInput = search
        sheet.addView(search)

        drawerChipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(40),
            ).also { it.bottomMargin = dp(16) }
        }
        sheet.addView(drawerChipRow)
        renderDrawerChips()

        drawerAllApps = loadDrawerApps(packageManager, drawerHiddenPackages())
        val adapter = DrawerAdapter(theme)
        drawerAdapter = adapter
        val recycler = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
            isVerticalScrollBarEnabled = false
            setPadding(0, 0, 0, dp(18))
            clipToPadding = false
        }
        drawerRecycler = recycler
        if (theme == LauncherTheme.CLASSIC) {
            recycler.layoutManager = LinearLayoutManager(this)
        } else {
            val columns = prefs.getInt(PREF_DRAWER_COLUMNS, 4).coerceIn(4, 5)
            recycler.layoutManager = GridLayoutManager(this, columns)
            attachDrawerDragSupport(recycler, adapter)
        }
        recycler.adapter = adapter
        sheet.addView(recycler)
        overlay.addView(sheet)
        if (theme == LauncherTheme.GLASSY) {
            drawerCustomizeSheet = buildCustomizeDrawerSheet()
            overlay.addView(drawerCustomizeSheet)
        }
        drawerOverlay = overlay
        rootFrame.addView(overlay)
        refreshDrawerItems()

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refreshDrawerItems()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        if (animationsEnabled()) {
            overlay.animate().alpha(1f).setDuration(240).start()
        }
    }

    private fun buildDrawerHandle(): View {
        return View(this).apply {
            background = roundedBackground(Color.parseColor("#66FFFFFF"), Color.TRANSPARENT, 3)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(4)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.bottomMargin = dp(16)
            }
        }
    }

    private fun renderDrawerHeader() {
        val header = drawerHeader ?: return
        header.removeAllViews()
        val theme = currentTheme()
        val title = TextView(this).apply {
            text = if (isEditMode) "Edit Mode" else if (theme == LauncherTheme.GLASSY) "FocusFlow" else ""
            textSize = if (isEditMode) 26f else 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            )
        }
        header.addView(title)
        if (theme == LauncherTheme.GLASSY && isEditMode) {
            header.addView(drawerHeaderButton("Cancel") { cancelEditMode() })
            header.addView(drawerHeaderButton("Done") { finishEditMode() })
        } else if (theme == LauncherTheme.GLASSY) {
            header.addView(drawerHeaderButton("✎") { enterEditMode() })
            header.addView(drawerHeaderButton("⋮") { closeDrawer() })
        }
    }

    private fun drawerHeaderButton(label: String, onClick: () -> Unit): View {
        return TextView(this).apply {
            text = label
            textSize = if (label.length > 1) 13f else 22f
            gravity = Gravity.CENTER
            setTextColor(TEXT_PRIMARY)
            background = roundedBackground(GLASS_LIGHT, GLASS_BORDER_BRIGHT, 18)
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(36),
            ).also { it.leftMargin = dp(8) }
            setOnClickListener { onClick() }
            foreground = rippleForeground(18)
        }
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
        val theme = currentTheme()
        labels.forEach { (filter, label) ->
            val active = filter == drawerFilter
            row.addView(TextView(this).apply {
                text = label
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(
                    if (active && theme == LauncherTheme.CLASSIC) CLASSIC_BACKGROUND
                    else if (active) Color.WHITE else TEXT_DIM,
                )
                background = if (active) {
                    roundedBackground(
                        if (theme == LauncherTheme.CLASSIC) CLASSIC_TEAL else ACCENT,
                        Color.TRANSPARENT,
                        18,
                    )
                } else {
                    roundedBackground(
                        if (theme == LauncherTheme.CLASSIC) Color.parseColor("#181818") else GLASS_LIGHT,
                        if (theme == LauncherTheme.CLASSIC) CLASSIC_BORDER else GLASS_BORDER,
                        18,
                    )
                }
                setPadding(dp(13), 0, dp(13), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(36),
                ).also { it.rightMargin = dp(8) }
                setOnClickListener {
                    drawerFilter = filter
                    renderDrawerChips()
                    refreshDrawerItems()
                }
            })
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
                val tools = parseJsonArray(prefs.getString(PREF_FOCUS_TOOLS, "[]") ?: "[]").toSet()
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

    private fun attachDrawerDragSupport(recycler: RecyclerView, adapter: DrawerAdapter) {
        val callback = object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ): Int {
                return makeMovementFlags(
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
                    0,
                )
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                focusedDrawerPackage = adapter.currentItems()
                    .getOrNull(target.bindingAdapterPosition)?.packageName
                return adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
            override fun isLongPressDragEnabled(): Boolean = isEditMode
            override fun isItemViewSwipeEnabled(): Boolean = false
        }
        ItemTouchHelper(callback).attachToRecyclerView(recycler)
    }

    private fun enterEditMode() {
        if (currentTheme() != LauncherTheme.GLASSY || isEditMode) return
        isEditMode = true
        drawerFilter = DrawerFilter.ALL
        renderDrawerChips()
        editDraftScale = prefs.getFloat(PREF_DRAWER_SCALE, 1f).coerceIn(0.8f, 1.2f)
        editDraftColumns = prefs.getInt(PREF_DRAWER_COLUMNS, 4).coerceIn(4, 5)
        drawerAdapter?.setIconScale(editDraftScale)
        drawerCustomizeSheet?.visibility = View.VISIBLE
        renderDrawerHeader()
        drawerAdapter?.notifyDataSetChanged()
    }

    private fun cancelEditMode() {
        isEditMode = false
        focusedDrawerPackage = null
        drawerAdapter?.setIconScale(prefs.getFloat(PREF_DRAWER_SCALE, 1f))
        drawerCustomizeSheet?.visibility = View.GONE
        renderDrawerHeader()
        refreshDrawerItems()
    }

    private fun finishEditMode() {
        val ordered = drawerAdapter?.currentItems()?.map { it.packageName }.orEmpty()
        saveJsonArray(PREF_DRAWER_ORDER, ordered)
        prefs.edit()
            .putFloat(PREF_DRAWER_SCALE, editDraftScale)
            .putInt(PREF_DRAWER_COLUMNS, editDraftColumns)
            .apply()
        (drawerRecycler?.layoutManager as? GridLayoutManager)?.spanCount = editDraftColumns
        isEditMode = false
        focusedDrawerPackage = null
        drawerCustomizeSheet?.visibility = View.GONE
        renderDrawerHeader()
        refreshDrawerItems()
    }

    private fun buildCustomizeDrawerSheet(): LinearLayout {
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = layeredGlassBackground(28)
            elevation = dp(12).toFloat()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(300),
            ).also {
                it.gravity = Gravity.BOTTOM
                it.setMargins(dp(24), 0, dp(24), dp(16))
            }
            visibility = View.GONE
            setPadding(dp(20), dp(12), dp(20), dp(18))
        }
        sheet.addView(View(this).apply {
            background = roundedBackground(Color.parseColor("#88FFFFFF"), Color.TRANSPARENT, 4)
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(4)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.bottomMargin = dp(12)
            }
        })
        sheet.addView(TextView(this).apply {
            text = "Customize Drawer"
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = dp(12) }
        })
        val scaleLabel = TextView(this).apply {
            textSize = 14f
            setTextColor(TEXT_DIM)
        }
        sheet.addView(scaleLabel)
        val seek = SeekBar(this).apply {
            max = 40
            progress = ((editDraftScale - 0.8f) * 100).toInt().coerceIn(0, 40)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(32),
            )
        }
        sheet.addView(seek)
        fun updateScale(progress: Int) {
            editDraftScale = (0.8f + progress / 100f).coerceIn(0.8f, 1.2f)
            scaleLabel.text = "Icon Size                         ${((editDraftScale * 100).toInt())}%"
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
            ).also { it.bottomMargin = dp(10) }
        }
        val four = customizeButton("4×5") {
            editDraftColumns = 4
            (drawerRecycler?.layoutManager as? GridLayoutManager)?.spanCount = 4
        }
        val five = customizeButton("5×6") {
            editDraftColumns = 5
            (drawerRecycler?.layoutManager as? GridLayoutManager)?.spanCount = 5
        }
        gridRow.addView(four)
        gridRow.addView(five)
        sheet.addView(gridRow)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44),
            )
        }
        actionRow.addView(customizeButton("Hide app") {
            focusedDrawerPackage?.let { toggleDrawerHidden(it) }
                ?: showNoFocusedAppMessage()
        })
        actionRow.addView(customizeButton("Lock app") {
            focusedDrawerPackage?.let { openAlwaysOnPicker(it) }
                ?: showNoFocusedAppMessage()
        })
        sheet.addView(actionRow)
        return sheet
    }

    private fun customizeButton(label: String, onClick: () -> Unit): View {
        return TextView(this).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(TEXT_PRIMARY)
            background = roundedBackground(GLASS_HEAVY, GLASS_BORDER_BRIGHT, 18)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f,
            ).also { it.leftMargin = dp(4); it.rightMargin = dp(4) }
            setOnClickListener { onClick() }
            foreground = rippleForeground(18)
        }
    }

    private fun showNoFocusedAppMessage() {
        AlertDialog.Builder(this)
            .setTitle("Select an app first")
            .setMessage("Long-press an app to select it, then use this drawer action.")
            .setPositiveButton("OK", null)
            .show()
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

    private fun closeDrawer() {
        val overlay = drawerOverlay ?: return
        isDrawerOpen = false
        val remove = {
            rootFrame.removeView(overlay)
            drawerOverlay = null
            drawerSheet = null
            drawerHeader = null
            drawerChipRow = null
            drawerRecycler = null
            drawerSearchInput = null
            drawerCustomizeSheet = null
            drawerAdapter = null
            isEditMode = false
        }
        if (animationsEnabled()) {
            overlay.animate().alpha(0f).setDuration(180).withEndAction(remove).start()
        } else {
            remove()
        }
    }

    // ── Drawer editing and app actions ─────────────────────────────────────────

    private fun showDrawerIconMenu(pkg: String, label: String) {
        if (currentTheme() == LauncherTheme.GLASSY) return
        val hidden = drawerHiddenPackages()
        val isHidden = pkg in hidden
        AlertDialog.Builder(this)
            .setTitle(label)
            .setItems(
                arrayOf(if (isHidden) "Unhide from Drawer" else "Hide from Drawer", "App Info"),
            ) { _, which ->
                when (which) {
                    0 -> toggleDrawerHidden(pkg)
                    1 -> openAppInfo(pkg)
                }
            }
            .create()
            .show()
    }

    private fun drawerHiddenPackages(): Set<String> {
        val current = parseJsonArray(prefs.getString(PREF_DRAWER_HIDDEN, "[]") ?: "[]")
        val legacy = parseJsonArray(prefs.getString(PREF_LAUNCHER_HIDDEN, "[]") ?: "[]")
        return (current + legacy).toSet()
    }

    private fun toggleDrawerHidden(pkg: String) {
        val hidden = drawerHiddenPackages().toMutableSet()
        if (hidden.contains(pkg)) {
            hidden.remove(pkg)
        } else {
            if (pkg !in getBlockedPackages()) {
                AlertDialog.Builder(this)
                    .setTitle("Only blocked apps can be hidden")
                    .setMessage("Add this app to Standalone or Always-On before hiding it from the drawer.")
                    .setPositiveButton("OK", null)
                    .show()
                return
            }
            hidden.add(pkg)
        }
        saveJsonArray(PREF_DRAWER_HIDDEN, hidden.toList())
        saveJsonArray(PREF_LAUNCHER_HIDDEN, hidden.toList())
        drawerAllApps = loadDrawerApps(packageManager, hidden)
        refreshDrawerItems()
    }

    private fun loadDrawerApps(
        pm: android.content.pm.PackageManager,
        hiddenPackages: Set<String>,
    ): List<DrawerItem.App> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
            .mapNotNull { info ->
                val pkg = info.activityInfo.packageName
                if (pkg == OWN_PACKAGE || pkg in hiddenPackages) return@mapNotNull null
                DrawerItem.App(
                    pkg,
                    pm.getApplicationLabel(info.activityInfo.applicationInfo).toString(),
                )
            }
            .distinctBy { it.packageName }
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
        return when (card.mode) {
            "count" -> "${card.used}/${card.total}"
            else -> if (card.used <= 0L) "<1m" else formatDuration(card.used)
        }
    }

    private fun formatUsedFull(card: AllowanceCardData): String {
        return when (card.mode) {
            "count" -> {
                val remaining = card.total - card.used
                if (remaining <= 0L) "no opens left" else "$remaining of ${card.total} opens left"
            }
            else -> "${formatDuration(card.used)} used • ${formatDuration(card.total - card.used)} left"
        }
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

    // ── Clock, wallpaper, gestures, utilities ──────────────────────────────────

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
        val time = if (use24h) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        } else {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(now).lowercase(Locale.getDefault())
        }
        clockView?.text = time
        dateView?.text = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now)
        refreshFocusCard()
    }

    private fun loadCustomWallpaper() {
        val view = customWallpaperView ?: return
        loadWallpaperInto(view)
    }

    private fun loadWallpaperInto(view: ImageView) {
        val path = prefs.getString(PREF_LAUNCHER_WALLPAPER, "")?.trim().orEmpty()
        if (path.isEmpty()) {
            view.setImageDrawable(null)
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
        view.setImageBitmap(bitmap)
    }

    private fun applyWallpaperTint() {
        if (currentTheme() != LauncherTheme.GLASSY || Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return
        try {
            val colors = WallpaperManager.getInstance(this)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return
            val dominant = colors.primaryColor.toArgb()
            val r = ((Color.red(dominant) * 0.4f) + (Color.red(ACCENT) * 0.6f)).toInt()
            val g = ((Color.green(dominant) * 0.4f) + (Color.green(ACCENT) * 0.6f)).toInt()
            val b = ((Color.blue(dominant) * 0.4f) + (Color.blue(ACCENT) * 0.6f)).toInt()
            wallpaperAccent = Color.rgb(r, g, b)
        } catch (_: Exception) {
            wallpaperAccent = ACCENT
        }
    }

    private fun expandNotificationsPanel() {
        try {
            val statusBar = getSystemService("statusbar")
            val cls = Class.forName("android.app.StatusBarManager")
            cls.getMethod("expandNotificationsPanel").invoke(statusBar)
        } catch (_: Exception) {
        }
    }

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
                val velocity = swipeVelocityTracker?.yVelocity ?: 0f
                swipeVelocityTracker?.recycle()
                swipeVelocityTracker = null
                val dy = swipeTouchStartY - ev.rawY
                if (dy > dp(60) && velocity < -250f && !isDrawerOpen) {
                    openDrawer()
                    return true
                }
                if (dy < -dp(80) && velocity > 250f) {
                    expandNotificationsPanel()
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                swipeVelocityTracker?.recycle()
                swipeVelocityTracker = null
            }
        }
        return super.dispatchTouchEvent(ev)
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

    private fun parseJsonArray(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveJsonArray(key: String, values: List<String>) {
        val arr = JSONArray()
        values.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

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

    private fun layeredGlassBackground(radiusDp: Int): Drawable {
        val base = roundedBackground(GLASS_MID, Color.TRANSPARENT, radiusDp)
        val border = roundedBackground(Color.TRANSPARENT, GLASS_BORDER_BRIGHT, radiusDp)
        return LayerDrawable(arrayOf(base, border))
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
}