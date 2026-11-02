package com.tbtechs.focusflow.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.tbtechs.focusflow.MainActivity
import com.tbtechs.focusflow.R
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.di.AppModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * Home screen widget: a live clock and date, plus the task that matters right now.
 *
 *  - NOW      a task is running: its name, when it ends, a live countdown and a progress bar.
 *  - NEXT UP  nothing is running: the next task, when it starts and a countdown to it.
 *  - EMPTY    nothing planned: how today went, and a hint to add a task.
 *
 * It deliberately shows nothing about Always-On, daily allowance or keyword blocking.
 *
 * Data comes straight from the task database (the same source as the app), so it no longer
 * depends on values that only a focus session wrote. The clock and the countdown tick by
 * themselves (TextClock / Chronometer). A cheap inexact alarm redraws the widget at the next
 * boundary (a task starting or ending), and the app pushes an update whenever tasks change.
 */
class FocusFlowWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                val pending = goAsync()
                refreshAsync(context.applicationContext) { pending.finish() }
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        refreshAsync(context.applicationContext) { pending.finish() }
    }

    override fun onEnabled(context: Context) {
        refreshAsync(context.applicationContext)
    }

    override fun onDisabled(context: Context) {
        cancelTick(context.applicationContext)
    }

    companion object {
        const val ACTION_REFRESH = "com.tbtechs.focusflow.action.WIDGET_REFRESH"

        private const val PREFS_NAME = "focusday_prefs"
        private const val DEFAULT_ACCENT = "#6366f1"
        private const val REQUEST_TAP = 100
        private const val REQUEST_TICK = 101
        private const val ACTIVE_REDRAW_MS = 5L * 60 * 1000
        private const val NEXT_REDRAW_MS = 15L * 60 * 1000
        private const val IDLE_REDRAW_MS = 60L * 60 * 1000
        private const val MAX_COUNTDOWN_MS = 12L * 60 * 60 * 1000

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Kept for existing callers (focus service, settings). Never blocks the caller. */
        fun pushWidgetUpdate(context: Context) {
            refreshAsync(context.applicationContext)
        }

        /** Called by code that already holds a fresh task list, so no second database read is needed. */
        fun onTasksChanged(context: Context, tasks: List<Task>, nowMs: Long = System.currentTimeMillis()) {
            runCatching { render(context.applicationContext, tasks, nowMs) }
        }

        private fun refreshAsync(context: Context, onDone: () -> Unit = {}) {
            scope.launch {
                try {
                    val tasks = withTimeoutOrNull(5_000L) {
                        runCatching { AppModule.taskRepository.getAllTasks() }.getOrNull()
                    }
                    runCatching { render(context, tasks, System.currentTimeMillis()) }
                } finally {
                    onDone()
                }
            }
        }

        /** [tasks] null means the database could not be read; then only a running session is drawn. */
        private fun render(context: Context, tasks: List<Task>?, nowMs: Long) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FocusFlowWidget::class.java))
            if (ids.isEmpty()) {
                cancelTick(context)
                return
            }
            val session = readSession(context, nowMs)
            if (tasks == null && session == null) return
            val snapshot = WidgetSnapshotBuilder.build(tasks.orEmpty(), nowMs, session)
            manager.updateAppWidget(ids, buildViews(context, snapshot, nowMs))
            scheduleTick(context, nextRedrawMs(snapshot, nowMs))
        }

        private fun readSession(context: Context, nowMs: Long): WidgetSession? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val name = prefs.getString("task_name", "").orEmpty()
            val end = prefs.getLong("task_end_ms", 0L)
            if (name.isBlank() || end <= nowMs) return null
            return WidgetSession(
                name = name,
                startMs = prefs.getLong("task_start_ms", 0L),
                endMs = end,
                colorHex = prefs.getString("task_color", DEFAULT_ACCENT),
            )
        }

        // ─── Drawing ──────────────────────────────────────────────────────────

        private fun buildViews(context: Context, snap: WidgetSnapshot, nowMs: Long): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_focusflow)
            val accent = parseColor(snap.colorHex)
            val progress = when (snap.mode) {
                WidgetMode.ACTIVE -> progressPercent(snap.startMs, snap.endMs, nowMs)
                else -> -1
            }
            val dayStats = if (snap.totalToday > 0) " · ${snap.doneToday}/${snap.totalToday}" else ""

            when (snap.mode) {
                WidgetMode.ACTIVE -> {
                    views.setTextViewText(R.id.widget_label, "NOW$dayStats")
                    views.setTextViewText(R.id.widget_title, snap.title)
                    val following = snap.followingTitle?.let { " · Next: $it" }.orEmpty()
                    views.setTextViewText(
                        R.id.widget_detail,
                        "Until ${formatTime(context, snap.endMs)}$following",
                    )
                    showCountdown(views, snap.endMs, nowMs, accent)
                }
                WidgetMode.NEXT -> {
                    views.setTextViewText(R.id.widget_label, "NEXT UP$dayStats")
                    views.setTextViewText(R.id.widget_title, snap.title)
                    val day = if (snap.startsToday) "" else "${weekday(snap.startMs)} "
                    views.setTextViewText(
                        R.id.widget_detail,
                        "Starts $day${formatTime(context, snap.startMs)}",
                    )
                    showCountdown(views, snap.startMs, nowMs, accent)
                }
                WidgetMode.EMPTY -> {
                    val allDone = snap.totalToday > 0 && snap.doneToday >= snap.totalToday
                    views.setTextViewText(R.id.widget_label, "FOCUSFLOW")
                    views.setTextViewText(
                        R.id.widget_title,
                        if (allDone) "All done for today" else "Nothing scheduled",
                    )
                    views.setTextViewText(
                        R.id.widget_detail,
                        if (snap.totalToday > 0) {
                            "${snap.doneToday}/${snap.totalToday} tasks done today"
                        } else {
                            "Tap to plan your day"
                        },
                    )
                    hideCountdown(views)
                }
            }
            views.setTextColor(R.id.widget_label, accent)

            if (progress >= 0) {
                views.setProgressBar(R.id.widget_progress, 100, progress, false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    views.setColorStateList(
                        R.id.widget_progress,
                        "setProgressTintList",
                        ColorStateList.valueOf(accent),
                    )
                }
                views.setViewVisibility(R.id.widget_progress, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_progress, View.GONE)
            }

            val target = if (snap.mode == WidgetMode.ACTIVE) "focus" else "home"
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context, target))
            return views
        }

        private fun showCountdown(views: RemoteViews, targetMs: Long, nowMs: Long, accent: Int) {
            val remaining = targetMs - nowMs
            if (remaining <= 0L || remaining > MAX_COUNTDOWN_MS) {
                hideCountdown(views)
                return
            }
            views.setChronometerCountDown(R.id.widget_timer, true)
            views.setChronometer(
                R.id.widget_timer,
                SystemClock.elapsedRealtime() + remaining,
                null,
                true,
            )
            views.setTextColor(R.id.widget_timer, accent)
            views.setViewVisibility(R.id.widget_timer, View.VISIBLE)
        }

        private fun hideCountdown(views: RemoteViews) {
            views.setChronometer(R.id.widget_timer, SystemClock.elapsedRealtime(), null, false)
            views.setViewVisibility(R.id.widget_timer, View.GONE)
        }

        private fun progressPercent(startMs: Long, endMs: Long, nowMs: Long): Int {
            val total = endMs - startMs
            if (startMs <= 0L || total <= 0L) return 0
            return ((nowMs - startMs) * 100L / total).toInt().coerceIn(0, 100)
        }

        private fun parseColor(hex: String?): Int = try {
            Color.parseColor(if (hex.isNullOrBlank()) DEFAULT_ACCENT else hex)
        } catch (_: IllegalArgumentException) {
            Color.parseColor(DEFAULT_ACCENT)
        }

        private fun formatTime(context: Context, epochMs: Long): String =
            android.text.format.DateFormat.getTimeFormat(context).format(Date(epochMs))

        private fun weekday(epochMs: Long): String =
            SimpleDateFormat("EEE", Locale.getDefault()).format(Date(epochMs))

        private fun openAppIntent(context: Context, route: String): PendingIntent {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("focusflow://app/$route")).apply {
                setClass(context, MainActivity::class.java)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                REQUEST_TAP,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        // ─── Redraw alarm (inexact, no special permission) ────────────────────

        private fun nextRedrawMs(snap: WidgetSnapshot, nowMs: Long): Long {
            val next = when (snap.mode) {
                WidgetMode.ACTIVE -> minOf(snap.endMs, nowMs + ACTIVE_REDRAW_MS)
                WidgetMode.NEXT -> minOf(snap.startMs, nowMs + NEXT_REDRAW_MS)
                WidgetMode.EMPTY -> minOf(nextMidnightMs(nowMs), nowMs + IDLE_REDRAW_MS)
            }
            return next.coerceAtLeast(nowMs + 15_000L)
        }

        private fun nextMidnightMs(nowMs: Long): Long =
            Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault())
                .toLocalDate().plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        private fun tickIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_TICK,
                Intent(context, FocusFlowWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun scheduleTick(context: Context, atMs: Long) {
            val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            runCatching {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC, atMs, tickIntent(context))
            }
        }

        private fun cancelTick(context: Context) {
            val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            runCatching { alarms.cancel(tickIntent(context)) }
        }
    }
}
