package com.tbtechs.nodespy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.tbtechs.nodespy.MainActivity
import com.tbtechs.nodespy.data.CaptureStore
import com.tbtechs.nodespy.data.NodeEntry
import com.tbtechs.nodespy.export.ExportBuilder
import com.tbtechs.nodespy.export.RuleAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class BubbleSelectMode { NONE, TAP, REGION }

class FloatingBubbleService : Service() {

    companion object {
        var instance: FloatingBubbleService? = null
        const val ACTION_STOP = "com.tbtechs.nodespy.STOP_BUBBLE"
        private const val NOTIF_CHANNEL = "nodespy_bubble"
        private const val NOTIF_ID = 42

        val C_BG = Color.parseColor("#0D1117")
        val C_SURFACE = Color.parseColor("#161B22")
        val C_OUTLINE = Color.parseColor("#30363D")
        val C_TEXT = Color.parseColor("#E6EDF3")
        val C_GREEN = Color.parseColor("#3FB950")
        val C_BLUE = Color.parseColor("#58A6FF")
        val C_ORANGE = Color.parseColor("#F0883E")
        val C_RED = Color.parseColor("#F85149")
        val C_MUTED = Color.parseColor("#8B949E")
        val C_PURPLE = Color.parseColor("#D2A8FF")
    }

    private lateinit var wm: WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateJob: Job? = null

    private var bubbleView: View? = null
    private var panelView: View? = null
    private var overlayView: NodeSelectOverlay? = null

    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var panelVisible = false
    private var selectMode = BubbleSelectMode.NONE

    private var loggingOn = true
    private var snapOn = false
    private var pinnedCount = 0
    private var lastPkg = ""

    private var tvPanelPkg: TextView? = null
    private var tvPanelPinCount: TextView? = null
    private var tvLogToggle: TextView? = null
    private var tvSnapToggle: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WindowManager::class.java)
        createNotifChannel()
        startForeground(NOTIF_ID, buildNotif())
        showBubble()
        observeStore()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        stateJob?.cancel()
        removeBubble()
        removePanel()
        removeOverlay()
        super.onDestroy()
    }

    private fun observeStore() {
        stateJob = scope.launch {
            combine(
                CaptureStore.loggingEnabled,
                CaptureStore.screenshotEnabled,
                CaptureStore.bubblePinnedIds,
                CaptureStore.captures
            ) { log, snap, pins, caps ->
                listOf<Any>(log, snap, pins.size, caps.firstOrNull()?.pkg ?: "")
            }.collect { values ->
                val log = values[0] as Boolean
                val snap = values[1] as Boolean
                val pins = values[2] as Int
                val pkg = values[3] as String
                loggingOn = log
                snapOn = snap
                pinnedCount = pins
                lastPkg = pkg
                tvLogToggle?.let { updateToggleChip(it, log, "LOG") }
                tvSnapToggle?.let { updateToggleChip(it, snap, "SNAP") }
                tvPanelPinCount?.text = "Pinned: $pins node${if (pins == 1) "" else "s"}"
                tvPanelPkg?.text = if (pkg.isNotEmpty()) pkg else "—"
            }
        }
    }

    // ── Bubble ──────────────────────────────────────────────────────────────

    private fun showBubble() {
        if (bubbleView != null) return
        val dm = resources.displayMetrics
        val dp = dm.density
        val size = (60 * dp).toInt()

        val view = object : FrameLayout(this) {
            private var initX = 0f; private var initY = 0f
            private var startRawX = 0f; private var startRawY = 0f
            private var moved = false
            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = bubbleParams!!.x.toFloat()
                        initY = bubbleParams!!.y.toFloat()
                        startRawX = e.rawX; startRawY = e.rawY
                        moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - startRawX; val dy = e.rawY - startRawY
                        if (!moved && (Math.abs(dx) > 8 || Math.abs(dy) > 8)) moved = true
                        if (moved) {
                            bubbleParams!!.x = (initX + dx).toInt()
                            bubbleParams!!.y = (initY + dy).toInt()
                            wm.updateViewLayout(this, bubbleParams)
                        }
                    }
                    MotionEvent.ACTION_UP -> if (!moved) togglePanel()
                }
                return true
            }
        }

        val circle = TextView(this).apply {
            text = "NS"
            setTextColor(C_BG)
            textSize = 13f
            gravity = Gravity.CENTER
            typeface = Typeface.MONOSPACE
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(C_GREEN)
            }
        }
        view.addView(circle, FrameLayout.LayoutParams(size, size))

        bubbleParams = baseWmParams().apply {
            width = size; height = size
            gravity = Gravity.TOP or Gravity.START
            x = dm.widthPixels - size - (8 * dp).toInt()
            y = (300 * dp).toInt()
        }
        wm.addView(view, bubbleParams)
        bubbleView = view
    }

    private fun removeBubble() {
        bubbleView?.let { runCatching { wm.removeView(it) } }
        bubbleView = null
    }

    // ── Panel ───────────────────────────────────────────────────────────────

    private fun togglePanel() {
        if (panelVisible) removePanel() else showPanel()
    }

    private fun showPanel() {
        if (panelVisible) return
        panelVisible = true
        val dp = resources.displayMetrics.density
        fun px(v: Float) = (v * dp).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(C_SURFACE)
                cornerRadius = px(14f).toFloat()
                setStroke(px(1f), C_OUTLINE)
            }
            setPadding(px(14f), px(10f), px(14f), px(14f))
        }

        root.addView(headerRow(::px))
        root.addView(divider(dp))
        root.addView(toggleRow(::px))
        root.addView(spacer(px(8f)))
        root.addView(selectRow(::px))
        root.addView(divider(dp))
        root.addView(pinRow(::px))
        root.addView(spacer(px(6f)))
        root.addView(pkgLine())

        val container = FrameLayout(this)
        container.addView(root, FrameLayout.LayoutParams(px(300f), ViewGroup.LayoutParams.WRAP_CONTENT))

        panelParams = baseWmParams().apply {
            width = ViewGroup.LayoutParams.WRAP_CONTENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.END
            x = px(8f); y = px(60f)
        }
        wm.addView(container, panelParams)
        panelView = container
    }

    private fun headerRow(px: (Float) -> Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, px(8f))
        }
        val title = TextView(this).apply {
            text = "NodeSpy Bubble"
            setTextColor(C_TEXT)
            textSize = 14f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(title)
        row.addView(chip("✕", C_RED) { removePanel() })
        row.addView(spacer((6 * resources.displayMetrics.density).toInt()))
        row.addView(chip("Stop", C_MUTED) { stopSelf() })
        return row
    }

    private fun toggleRow(px: (Float) -> Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tvLogToggle = chip(if (loggingOn) "● LOG" else "○ LOG", if (loggingOn) C_GREEN else C_MUTED) {
            CaptureStore.setLoggingEnabled(!CaptureStore.loggingEnabled.value)
        }
        tvSnapToggle = chip(if (snapOn) "● SNAP" else "○ SNAP", if (snapOn) C_BLUE else C_MUTED) {
            if (!snapOn && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                toast("Screenshot requires Android 11+")
            } else {
                CaptureStore.setScreenshotEnabled(!CaptureStore.screenshotEnabled.value)
            }
        }
        row.addView(tvLogToggle)
        row.addView(spacer(px(8f)))
        row.addView(tvSnapToggle)
        return row
    }

    private fun selectRow(px: (Float) -> Int): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(chip("👆 Tap Select", C_ORANGE) {
            removePanel()
            enterSelectMode(BubbleSelectMode.TAP)
        })
        row.addView(spacer(px(8f)))
        row.addView(chip("⬜ Region", C_PURPLE) {
            removePanel()
            enterSelectMode(BubbleSelectMode.REGION)
        })
        return row
    }

    private fun pinRow(px: (Float) -> Int): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(10f), 0, 0)
        }
        val countTv = TextView(this).apply {
            text = "Pinned: $pinnedCount node${if (pinnedCount == 1) "" else "s"}"
            setTextColor(C_TEXT)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvPanelPinCount = countTv
        row.addView(countTv)
        row.addView(chip("Export", C_GREEN) { exportPinned() })
        row.addView(spacer(px(6f)))
        row.addView(chip("Clear", C_RED) { CaptureStore.clearBubblePins(); toast("Pins cleared") })
        return row
    }

    private fun pkgLine(): View {
        return TextView(this).apply {
            text = if (lastPkg.isNotEmpty()) lastPkg else "—"
            setTextColor(C_MUTED)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
        }.also { tvPanelPkg = it }
    }

    private fun removePanel() {
        panelView?.let { runCatching { wm.removeView(it) } }
        panelView = null
        tvPanelPkg = null; tvPanelPinCount = null; tvLogToggle = null; tvSnapToggle = null
        panelVisible = false
    }

    // ── Select Overlay ───────────────────────────────────────────────────────

    private fun enterSelectMode(mode: BubbleSelectMode) {
        selectMode = mode
        val capture = CaptureStore.latest() ?: run {
            toast("No capture yet — open any app first"); return
        }
        CaptureStore.setBubbleActiveCaptureId(capture.id)

        val overlay = NodeSelectOverlay(
            context = this,
            mode = mode,
            nodes = capture.nodes,
            onPinNode = { node ->
                CaptureStore.addBubblePinnedId(node.id)
                toast("Pinned: ${node.resId?.substringAfterLast('/') ?: node.text ?: node.cls.substringAfterLast('.')}")
            },
            onDone = { exitSelectMode() }
        )
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        wm.addView(overlay, params)
        overlayView = overlay
    }

    private fun exitSelectMode() {
        selectMode = BubbleSelectMode.NONE
        removeOverlay()
        showPanel()
    }

    private fun removeOverlay() {
        overlayView?.let { runCatching { wm.removeView(it) } }
        overlayView = null
    }

    // ── Export ───────────────────────────────────────────────────────────────

    private fun exportPinned() {
        val captureId = CaptureStore.bubbleActiveCaptureId.value
        val capture = (captureId?.let { CaptureStore.findById(it) }) ?: CaptureStore.latest()
        if (capture == null) { toast("Nothing to export"); return }
        val pinned = CaptureStore.bubblePinnedIds.value
        val summary = RuleAnalyzer.summarize(RuleAnalyzer.analyze(capture, pinned, CaptureStore.recentForPackage(capture.pkg)))
        if (summary.weakRules > 0 || summary.exportableRules == 0) {
            toast("${summary.exportableRules} recommended · ${summary.weakRules} weak")
        }
        val json = ExportBuilder.build(capture, pinned)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, json)
            putExtra(Intent.EXTRA_SUBJECT, "NodeSpy — ${capture.pkg}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(Intent.createChooser(intent, "Export JSON").also {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    // ── View Helpers ─────────────────────────────────────────────────────────

    private fun chip(label: String, color: Int, onClick: () -> Unit): TextView {
        val dp = resources.displayMetrics.density
        return TextView(this).apply {
            text = label
            setTextColor(color)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 6 * dp
                setColor(Color.argb(40, Color.red(color), Color.green(color), Color.blue(color)))
                setStroke((1 * dp).toInt(), Color.argb(80, Color.red(color), Color.green(color), Color.blue(color)))
            }
            setOnClickListener { onClick() }
        }
    }

    private fun updateToggleChip(tv: TextView, on: Boolean, label: String) {
        val onColor = if (label == "LOG") C_GREEN else C_BLUE
        tv.text = if (on) "● $label" else "○ $label"
        tv.setTextColor(if (on) onColor else C_MUTED)
        (tv.background as? GradientDrawable)?.setColor(
            if (on) Color.argb(40, Color.red(onColor), Color.green(onColor), Color.blue(onColor))
            else Color.argb(30, 139, 148, 158)
        )
    }

    private fun divider(dp: Float): View {
        return View(this).apply {
            setBackgroundColor(C_OUTLINE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
            ).also { it.setMargins(0, (8 * dp).toInt(), 0, (8 * dp).toInt()) }
        }
    }

    private fun spacer(size: Int): View =
        View(this).apply { layoutParams = ViewGroup.LayoutParams(size, size) }

    private fun baseWmParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    )

    private fun toast(msg: String) =
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()

    private fun createNotifChannel() {
        val ch = NotificationChannel(NOTIF_CHANNEL, "NodeSpy Bubble", NotificationManager.IMPORTANCE_LOW)
        ch.description = "NodeSpy floating bubble overlay"
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotif(): Notification {
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, FloatingBubbleService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPi = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, NOTIF_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("NodeSpy Bubble active")
            .setContentText("Tap to open NodeSpy")
            .setContentIntent(openPi)
            .addAction(Notification.Action.Builder(null, "Stop", stopPi).build())
            .setOngoing(true)
            .build()
    }
}

// ── Node Select Overlay ───────────────────────────────────────────────────────

class NodeSelectOverlay(
    context: Context,
    private val mode: BubbleSelectMode,
    private val nodes: List<NodeEntry>,
    private val onPinNode: (NodeEntry) -> Unit,
    private val onDone: () -> Unit
) : View(context) {

    private val dp = resources.displayMetrics.density

    private val pHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f * dp
        color = FloatingBubbleService.C_GREEN
    }
    private val pHighlightFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.argb(50, 63, 185, 80)
    }
    private val pRegion = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f * dp
        color = FloatingBubbleService.C_BLUE
        pathEffect = DashPathEffect(floatArrayOf(12f, 6f), 0f)
    }
    private val pRegionFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.argb(25, 88, 166, 255)
    }
    private val pBar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = FloatingBubbleService.C_SURFACE
    }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = FloatingBubbleService.C_TEXT; textSize = 13f * dp; typeface = Typeface.MONOSPACE
    }
    private val pAction = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = FloatingBubbleService.C_GREEN; textSize = 13f * dp
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val pMuted = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = FloatingBubbleService.C_MUTED; textSize = 11f * dp; typeface = Typeface.MONOSPACE
    }
    private val pPin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = FloatingBubbleService.C_ORANGE; textSize = 13f * dp
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private var hoveredNode: NodeEntry? = null
    private var regionStart: PointF? = null
    private var regionEnd: PointF? = null
    private var regionNodes: List<NodeEntry> = emptyList()
    private var regionFinalized = false

    private val barH = (52 * dp).toInt()

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawRect(0f, 0f, w, barH.toFloat(), pBar)

        val modeLabel = if (mode == BubbleSelectMode.TAP) "TAP MODE" else "REGION MODE"
        val instr = if (mode == BubbleSelectMode.TAP) "Tap any element to pin it" else "Drag to select a region"
        canvas.drawText(modeLabel, 16f * dp, 20f * dp, pAction)
        canvas.drawText(instr, 16f * dp, 38f * dp, pMuted)

        val doneText = "✕ DONE"
        canvas.drawText(doneText, w - pAction.measureText(doneText) - 16f * dp, 30f * dp, pAction)

        if (mode == BubbleSelectMode.TAP) {
            hoveredNode?.let { node ->
                val rect = RectF(node.boundsL.toFloat(), node.boundsT.toFloat(), node.boundsR.toFloat(), node.boundsB.toFloat())
                canvas.drawRect(rect, pHighlightFill)
                canvas.drawRect(rect, pHighlight)

                val label = (node.resId?.substringAfterLast('/') ?: node.text ?: node.cls.substringAfterLast('.')).take(40)
                val infoW = pText.measureText(label)
                val infoY = (node.boundsB + 26f * dp).coerceAtMost(h - 60f * dp)
                val bgX = node.boundsL.toFloat().coerceAtMost(w - infoW - 80f * dp)
                canvas.drawRoundRect(RectF(bgX, infoY - 18f * dp, bgX + infoW + 24f * dp, infoY + 8f * dp), 6 * dp, 6 * dp, pBar)
                canvas.drawText(label, bgX + 8f * dp, infoY, pText)

                val pinText = "[ PIN ]"
                canvas.drawText(pinText, bgX + infoW + 28f * dp, infoY, pPin)
            }
        }

        if (mode == BubbleSelectMode.REGION) {
            val start = regionStart; val end = regionEnd
            if (start != null && end != null) {
                val rect = RectF(minOf(start.x, end.x), minOf(start.y, end.y), maxOf(start.x, end.x), maxOf(start.y, end.y))
                canvas.drawRect(rect, pRegionFill)
                canvas.drawRect(rect, pRegion)
            }
            if (regionFinalized && regionNodes.isNotEmpty()) {
                regionNodes.forEach { node ->
                    canvas.drawRect(RectF(node.boundsL.toFloat(), node.boundsT.toFloat(), node.boundsR.toFloat(), node.boundsB.toFloat()), pHighlightFill)
                    canvas.drawRect(RectF(node.boundsL.toFloat(), node.boundsT.toFloat(), node.boundsR.toFloat(), node.boundsB.toFloat()), pHighlight)
                }
                val confirmText = "PIN ${regionNodes.size} NODE${if (regionNodes.size == 1) "" else "S"}"
                val cancelText = "CANCEL"
                val cy = h - 70f * dp
                val cw = pPin.measureText(confirmText)
                canvas.drawRoundRect(RectF(w / 2 - cw / 2 - 14f * dp, cy - 22f * dp, w / 2 + cw / 2 + 14f * dp, cy + 8f * dp), 6 * dp, 6 * dp, pBar)
                canvas.drawText(confirmText, w / 2 - cw / 2, cy, pPin)
                canvas.drawText(cancelText, w / 2 - pMuted.measureText(cancelText) / 2, cy + 28f * dp, pMuted)
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x; val y = e.y; val w = width.toFloat()
        if (y < barH && e.action == MotionEvent.ACTION_UP) {
            val doneText = "✕ DONE"
            if (x > w - pAction.measureText(doneText) - 20f * dp) { onDone(); return true }
        }
        return if (mode == BubbleSelectMode.TAP) handleTap(e, x, y, w) else handleRegion(e, x, y, w)
    }

    private fun handleTap(e: MotionEvent, x: Float, y: Float, w: Float): Boolean {
        if (e.action != MotionEvent.ACTION_UP) return true
        if (y < barH) return true

        val prev = hoveredNode
        if (prev != null) {
            val label = (prev.resId?.substringAfterLast('/') ?: prev.text ?: prev.cls.substringAfterLast('.')).take(40)
            val infoW = pText.measureText(label)
            val infoY = (prev.boundsB + 26f * dp).coerceAtMost(height - 60f * dp)
            val bgX = prev.boundsL.toFloat().coerceAtMost(w - infoW - 80f * dp)
            val pinText = "[ PIN ]"
            val pinX = bgX + infoW + 28f * dp
            val pinW = pPin.measureText(pinText)
            if (x >= pinX && x <= pinX + pinW && y >= infoY - 20f * dp && y <= infoY + 10f * dp) {
                onPinNode(prev)
                hoveredNode = null; invalidate(); return true
            }
        }
        hoveredNode = nodeAt(x, y)
        invalidate()
        return true
    }

    private fun handleRegion(e: MotionEvent, x: Float, y: Float, w: Float): Boolean {
        if (y < barH) return true
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                if (regionFinalized && regionNodes.isNotEmpty()) {
                    val cy = height - 70f * dp
                    val confirmText = "PIN ${regionNodes.size} NODE${if (regionNodes.size == 1) "" else "S"}"
                    val cw = pPin.measureText(confirmText)
                    if (y >= cy - 24f * dp && y <= cy + 10f * dp && x >= w / 2 - cw / 2 - 16f * dp && x <= w / 2 + cw / 2 + 16f * dp) {
                        regionNodes.forEach { onPinNode(it) }
                        regionStart = null; regionEnd = null; regionFinalized = false; regionNodes = emptyList()
                        invalidate(); return true
                    }
                    if (y > cy + 18f * dp) {
                        regionStart = null; regionEnd = null; regionFinalized = false; regionNodes = emptyList()
                        invalidate(); return true
                    }
                }
                regionStart = PointF(x, y); regionEnd = PointF(x, y); regionFinalized = false; regionNodes = emptyList()
            }
            MotionEvent.ACTION_MOVE -> { regionEnd = PointF(x, y); invalidate() }
            MotionEvent.ACTION_UP -> {
                regionEnd = PointF(x, y)
                val start = regionStart ?: return true; val end = regionEnd ?: return true
                val r = Rect(minOf(start.x, end.x).toInt(), minOf(start.y, end.y).toInt(), maxOf(start.x, end.x).toInt(), maxOf(start.y, end.y).toInt())
                regionNodes = nodes.filter { n -> Rect.intersects(r, Rect(n.boundsL, n.boundsT, n.boundsR, n.boundsB)) }
                regionFinalized = true; invalidate()
            }
        }
        return true
    }

    private fun nodeAt(x: Float, y: Float): NodeEntry? {
        var best: NodeEntry? = null; var bestArea = Long.MAX_VALUE
        nodes.forEach { n ->
            if (x >= n.boundsL && x <= n.boundsR && y >= n.boundsT && y <= n.boundsB) {
                val area = (n.boundsR - n.boundsL).toLong() * (n.boundsB - n.boundsT).toLong()
                if (area < bestArea) { bestArea = area; best = n }
            }
        }
        return best
    }
}
