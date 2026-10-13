package com.tbtechs.focusflow.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tbtechs.focusflow.MainActivity
import com.tbtechs.focusflow.R
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import com.tbtechs.focusflow.enforcement.receivers.NotificationActionReceiver
import com.tbtechs.focusflow.notifications.status.StatusCardAction
import com.tbtechs.focusflow.notifications.status.TaskActionIntents
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Maintains one ongoing status card for the task whose scheduled interval is
 * currently active, when Focus Mode is not already showing that task.
 *
 * This notification does not start Focus Mode, app blocking, or the VPN.
 */
object LiveTaskStatusNotificationPublisher {
    private const val NOTIFICATION_TAG = "live-task-status"
    private const val NOTIFICATION_ID = 1003
    private const val PREF_FOCUS_ACTIVE = "focus_active"
    private const val PREF_FOCUS_BREAK_UNTIL_MS = "focus_break_until_ms"
    private const val REQUEST_OPEN = 3000
    private val terminalStatuses = setOf("completed", "skipped", "overdue")

    fun sync(context: Context, tasks: List<Task>, nowMs: Long = System.currentTimeMillis()) {
        val appContext = context.applicationContext
        NotificationChannels.createAll(appContext)
        val manager = NotificationManagerCompat.from(appContext)
        val focusPrefs = appContext.getSharedPreferences(
            AppBlockerAccessibilityService.PREFS_NAME,
            Context.MODE_PRIVATE,
        )

        // ForegroundTaskService owns the task-status notification during focus
        // and its break. focus_active is false during a break, so check the
        // persisted break deadline as well to avoid a duplicate live-task card.
        if (
            shouldSuppressLiveTaskStatus(
                focusActive = focusPrefs.getBoolean(PREF_FOCUS_ACTIVE, false),
                breakUntilMs = focusPrefs.getLong(PREF_FOCUS_BREAK_UNTIL_MS, 0L),
                nowMs = nowMs,
            )
        ) {
            manager.cancel(NOTIFICATION_TAG, NOTIFICATION_ID)
            return
        }

        val currentTask = tasks.asSequence()
            .filter { it.status !in terminalStatuses }
            .mapNotNull { task ->
                val startMs = parseEpochMillis(task.startTime) ?: return@mapNotNull null
                val endMs = parseEpochMillis(task.endTime) ?: return@mapNotNull null
                if (startMs > nowMs || endMs <= nowMs || endMs <= startMs) {
                    return@mapNotNull null
                }
                LiveTask(task, startMs, endMs)
            }
            .maxWithOrNull(compareBy<LiveTask> { it.startMs }.thenBy { it.task.id })

        if (currentTask == null || !manager.areNotificationsEnabled()) {
            manager.cancel(NOTIFICATION_TAG, NOTIFICATION_ID)
            return
        }

        runCatching {
            manager.notify(
                NOTIFICATION_TAG,
                NOTIFICATION_ID,
                buildNotification(appContext, currentTask, nowMs),
            )
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context.applicationContext)
            .cancel(NOTIFICATION_TAG, NOTIFICATION_ID)
    }

    private fun buildNotification(
        context: Context,
        liveTask: LiveTask,
        nowMs: Long,
    ) = NotificationCompat.Builder(context, NotificationChannels.LIVE_TASK_STATUS)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("In progress · ${liveTask.task.title}")
        .setContentText("Ends at ${formatEndTime(liveTask.endMs)}")
        .setSubText("Live task status")
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setWhen(liveTask.endMs - nowMs + SystemClock.elapsedRealtime())
        .setUsesChronometer(true)
        .setChronometerCountDown(true)
        .setShowWhen(true)
        .setTimeoutAfter((liveTask.endMs - nowMs).coerceAtLeast(1_000L))
        .setContentIntent(openTaskPendingIntent(context, liveTask.task.id))
        .addAction(
            0,
            "✓ Done",
            TaskActionIntents.pendingIntent(context, liveTask.task.id, StatusCardAction.DONE),
        )
        .addAction(
            0,
            "+15m",
            TaskActionIntents.pendingIntent(context, liveTask.task.id, StatusCardAction.EXTEND_15),
        )
        .addAction(
            0,
            "+30m",
            TaskActionIntents.pendingIntent(context, liveTask.task.id, StatusCardAction.EXTEND_30),
        )
        .addAction(
            0,
            "Skip",
            TaskActionIntents.pendingIntent(context, liveTask.task.id, StatusCardAction.SKIP),
        )
        .build()

    private fun openTaskPendingIntent(context: Context, taskId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            data = Uri.parse("focusflow-internal://live-task/${Uri.encode(taskId)}")
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun parseEpochMillis(value: String): Long? =
        runCatching { Instant.parse(value).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .getOrNull()

    private fun formatEndTime(endMs: Long): String =
        Instant.ofEpochMilli(endMs)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

    private data class LiveTask(
        val task: Task,
        val startMs: Long,
        val endMs: Long,
    )
}

internal fun shouldSuppressLiveTaskStatus(
    focusActive: Boolean,
    breakUntilMs: Long,
    nowMs: Long,
): Boolean = focusActive || breakUntilMs > nowMs
