package com.tbtechs.focusflow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.tbtechs.focusflow.MainActivity
import com.tbtechs.focusflow.R
import org.json.JSONArray
import java.util.Calendar

/**
 * FocusFlowWidget
 *
 * Home screen widget that reflects live app state. Three render modes:
 *
 *   1. ACTIVE TASK   — task_name set and task_end_ms in the future.
 *      Shows task title, "Nm remaining", and a progress bar.
 *      Header label is tinted with the task color (task_color hex).
 *      Tapping → focusflow:///focus  (Focus tab)
 *
 *   2. STANDALONE    — no active task, but standalone_block_active=true
 *      and standalone_block_until_ms in the future.
 *      Shows "Blocking N apps" + "until HH:MM".
 *      Tapping → focusflow:///       (Schedule / index tab)
 *
 *   3. IDLE          — nothing running.
 *      Shows "No active task" + "+ Add Task" CTA.
 *      Tapping anywhere or the CTA → focusflow:///
 *
 * Update path:
 *   - System triggers onUpdate() every 30 min (Android minimum)
 *   - ForegroundTaskService.pushWidgetUpdate() pushes during focus sessions
 *   - SharedPrefsModule.pushWidgetUpdate() lets JS push on task / block
 *     state changes that happen outside a focus session
 */
class FocusFlowWidget : AppWidgetProvider() {

    companion object {
        private const val PREFS_NAME       = "focusday_prefs"
        private const val DEFAULT_ACCENT   = "#6366f1"

        // PendingIntent request codes — must be unique per target
        private const val PI_TAP_ROOT = 100
        private const val PI_TAP_CTA  = 101

        /**
         * Called by ForegroundTaskService and SharedPrefsModule to push live data
         * to all instances of this widget.
         */
        fun pushWidgetUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, FocusFlowWidget::class.java)
            )
            if (ids.isEmpty()) return
            val views = buildViews(context)
            manager.updateAppWidget(ids, views)
        }

        private fun parseColor(hex: String?): Int {
            return try {
                Color.parseColor(if (hex.isNullOrBlank()) DEFAULT_ACCENT else hex)
            } catch (_: Exception) {
                Color.parseColor(DEFAULT_ACCENT)
            }
        }

        private fun pendingDeepLink(context: Context, requestCode: Int, path: String): PendingIntent {
            // Use a URI deep-link so Expo Router routes to the right tab. If the URI
            // intent fails to resolve for any reason, ACTIVITY_NEW_TASK on MainActivity
            // still launches the app (URI is informational for the JS layer).
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("focusflow://$path")).apply {
                setClass(context, MainActivity::class.java)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun formatHmm(epochMs: Long): String {
            val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val min  = cal.get(Calendar.MINUTE)
            return String.format("%02d:%02d", hour, min)
        }

        private fun standaloneBlockedCount(json: String?): Int {
            if (json.isNullOrBlank() || json == "[]") return 0
            return try {
                JSONArray(json).length()
            } catch (_: Exception) {
                0
            }
        }

        private fun buildViews(context: Context): RemoteViews {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val now   = System.currentTimeMillis()

            val views = RemoteViews(context.packageName, R.layout.widget_focusflow)

            // ── Active task signals ──
            val taskName  = prefs.getString("task_name", "") ?: ""
            val endTimeMs = prefs.getLong("task_end_ms", 0L)
            val startMs   = prefs.getLong("task_start_ms", 0L)
            val taskColor = prefs.getString("task_color", DEFAULT_ACCENT)

            // ── Standalone block signals ──
            val saActive  = prefs.getBoolean("standalone_block_active", false)
            val saUntil   = prefs.getLong("standalone_block_until_ms", 0L)
            val saPkgJson = prefs.getString("standalone_blocked_packages", "[]")

            val isTaskActive = taskName.isNotBlank() && endTimeMs > now
            val isStandaloneActive = saActive && saUntil > now &&
                standaloneBlockedCount(saPkgJson) > 0

            when {
                isTaskActive -> renderActiveTask(
                    context, views, taskName, taskColor, startMs, endTimeMs
                )
                isStandaloneActive -> renderStandaloneBlock(
                    context, views, standaloneBlockedCount(saPkgJson), saUntil
                )
                else -> renderIdle(context, views)
            }

            return views
        }

        private fun renderActiveTask(
            context: Context,
            views: RemoteViews,
            taskName: String,
            taskColorHex: String?,
            startMs: Long,
            endTimeMs: Long
        ) {
            val now           = System.currentTimeMillis()
            val remainingMs   = (endTimeMs - now).coerceAtLeast(0L)
            val totalMs       = if (startMs > 0L) endTimeMs - startMs else remainingMs
            val progressPct   = if (totalMs > 0L) {
                ((totalMs - remainingMs) * 100L / totalMs).toInt().coerceIn(0, 100)
            } else 0

            val remainingMins = remainingMs / 60_000
            val timeStr = when {
                remainingMins < 1L  -> "< 1m remaining"
                remainingMins == 1L -> "1m remaining"
                else                -> "${remainingMins}m remaining"
            }

            val accent = parseColor(taskColorHex)

            views.setTextViewText(R.id.widget_header_label, "ACTIVE TASK")
            views.setTextColor(R.id.widget_header_label, accent)
            views.setTextViewText(R.id.widget_task_name, taskName)
            views.setTextViewText(R.id.widget_time_remaining, timeStr)
            views.setTextColor(R.id.widget_time_remaining, accent)
            views.setViewVisibility(R.id.widget_time_remaining, View.VISIBLE)
            views.setProgressBar(R.id.widget_progress, 100, progressPct, false)
            views.setViewVisibility(R.id.widget_progress, View.VISIBLE)
            views.setViewVisibility(R.id.widget_add_task_btn, View.GONE)

            // Tap → Focus tab
            val tap = pendingDeepLink(context, PI_TAP_ROOT, "/focus")
            views.setOnClickPendingIntent(R.id.widget_root, tap)
        }

        private fun renderStandaloneBlock(
            context: Context,
            views: RemoteViews,
            blockedCount: Int,
            untilMs: Long
        ) {
            val accent = parseColor(DEFAULT_ACCENT)
            val plural = if (blockedCount == 1) "app" else "apps"

            views.setTextViewText(R.id.widget_header_label, "BLOCK ACTIVE")
            views.setTextColor(R.id.widget_header_label, accent)
            // Single-line summary as required: "Blocking N apps · until HH:MM"
            views.setTextViewText(
                R.id.widget_task_name,
                "Blocking $blockedCount $plural · until ${formatHmm(untilMs)}"
            )
            views.setTextViewText(R.id.widget_time_remaining, "")
            views.setViewVisibility(R.id.widget_time_remaining, View.GONE)
            views.setProgressBar(R.id.widget_progress, 100, 0, false)
            views.setViewVisibility(R.id.widget_progress, View.GONE)
            views.setViewVisibility(R.id.widget_add_task_btn, View.GONE)

            // Tap → Schedule tab (index)
            val tap = pendingDeepLink(context, PI_TAP_ROOT, "/")
            views.setOnClickPendingIntent(R.id.widget_root, tap)
        }

        private fun renderIdle(context: Context, views: RemoteViews) {
            val accent = parseColor(DEFAULT_ACCENT)

            views.setTextViewText(R.id.widget_header_label, "FOCUSFLOW")
            views.setTextColor(R.id.widget_header_label, accent)
            views.setTextViewText(R.id.widget_task_name, "No active task")
            views.setTextViewText(R.id.widget_time_remaining, "Tap to plan your day")
            views.setTextColor(R.id.widget_time_remaining, accent)
            views.setViewVisibility(R.id.widget_time_remaining, View.VISIBLE)
            views.setProgressBar(R.id.widget_progress, 100, 0, false)
            views.setViewVisibility(R.id.widget_progress, View.GONE)
            views.setViewVisibility(R.id.widget_add_task_btn, View.VISIBLE)

            val tap    = pendingDeepLink(context, PI_TAP_ROOT, "/")
            val tapCta = pendingDeepLink(context, PI_TAP_CTA,  "/")
            views.setOnClickPendingIntent(R.id.widget_root, tap)
            views.setOnClickPendingIntent(R.id.widget_add_task_btn, tapCta)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val views = buildViews(context)
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
