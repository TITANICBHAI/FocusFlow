package com.tbtechs.focusflow.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.tbtechs.focusflow.R
import com.tbtechs.focusflow.MainActivity

/**
 * ForegroundTaskService
 *
 * Runs persistently at all times — not only during focus sessions.
 * This keeps the process alive so the AccessibilityService is never killed.
 *
 * Two modes:
 *   IDLE   — No active task. Shows a quiet "FocusFlow is monitoring" notification.
 *   ACTIVE — Focus session running. Shows task name + live countdown.
 *
 * Actions:
 *   ACTION_SET_IDLE  — Switch to idle mode (called by JS stopService / on session end).
 *   ACTION_STOP      — Truly stop the service (not called in normal flow, kept for emergencies).
 *   ACTION_TASK_ENDED — Broadcast fired when countdown reaches zero (caught by JS bridge).
 *
 * Intent extras for ACTIVE mode:
 *   "taskName"   String  — display name of the active task
 *   "endTimeMs"  Long    — absolute epoch ms when the task ends
 *   "nextName"   String? — name of the next task (shown as sub-text)
 */
class ForegroundTaskService : Service() {

    companion object {
        const val CHANNEL_ID        = "focusday_foreground"
        const val CHANNEL_NAME      = "FocusFlow Active Task"
        const val NOTIFICATION_ID   = 1001
        const val ACTION_STOP       = "com.tbtechs.focusflow.STOP_SERVICE"
        const val ACTION_SET_IDLE   = "com.tbtechs.focusflow.SET_IDLE"
        const val ACTION_TASK_ENDED = "com.tbtechs.focusflow.TASK_ENDED"

        const val EXTRA_TASK_NAME   = "taskName"
        const val EXTRA_END_MS      = "endTimeMs"
        const val EXTRA_NEXT_NAME   = "nextName"
    }

    private var taskName: String  = ""
    private var endTimeMs: Long   = 0L
    private var nextName: String? = null
    private var isActiveMode: Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            val remaining = endTimeMs - System.currentTimeMillis()
            if (remaining <= 0) {
                // Kotlin owns session end — clear flag before telling JS.
                clearFocusActive()
                sendBroadcast(Intent(ACTION_TASK_ENDED).apply {
                    `package` = applicationContext.packageName
                })
                goIdle()
                return
            }
            updateNotification(remaining)
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Start immediately in idle mode so the process is kept alive from the first launch.
        startForeground(NOTIFICATION_ID, buildIdleNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // Emergency hard-stop — not called in normal flow.
                handler.removeCallbacks(tickRunnable)
                clearFocusActive()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SET_IDLE -> {
                // JS called stopService() or session ended — go idle but keep running.
                handler.removeCallbacks(tickRunnable)
                clearFocusActive()
                goIdle()
                return START_STICKY
            }
            else -> {
                // Start or update an active focus session.
                val name    = intent?.getStringExtra(EXTRA_TASK_NAME)
                val endMs   = intent?.getLongExtra(EXTRA_END_MS, 0L) ?: 0L
                val next    = intent?.getStringExtra(EXTRA_NEXT_NAME)

                if (name != null && endMs > 0L) {
                    taskName     = name
                    endTimeMs    = endMs
                    nextName     = next
                    isActiveMode = true

                    val notification = buildActiveNotification(endMs - System.currentTimeMillis())
                    startForeground(NOTIFICATION_ID, notification)

                    handler.removeCallbacks(tickRunnable)
                    handler.post(tickRunnable)
                } else {
                    // No task extras — stay/go idle.
                    goIdle()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        super.onDestroy()
    }

    // ─── Mode helpers ──────────────────────────────────────────────────────────

    private fun goIdle() {
        isActiveMode = false
        taskName     = ""
        endTimeMs    = 0L
        nextName     = null
        handler.removeCallbacks(tickRunnable)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildIdleNotification())
    }

    // ─── Notification builders ─────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps FocusFlow running and shows your active task"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildIdleNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val tapPending = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusFlow")
            .setContentText("Monitoring active — tap to open")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tapPending)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun buildActiveNotification(remainingMs: Long): Notification {
        val mins    = (remainingMs / 60_000).coerceAtLeast(0)
        val secs    = ((remainingMs % 60_000) / 1_000).coerceAtLeast(0)
        val timeStr = String.format("%d:%02d remaining", mins, secs)

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val tapPending = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ForegroundTaskService::class.java).apply {
            action = ACTION_SET_IDLE
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎯 $taskName")
            .setContentText(timeStr)
            .setSubText(nextName?.let { "Next: $it" })
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tapPending)
            .addAction(0, "Stop Focus", stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(remainingMs: Long) {
        val notification = buildActiveNotification(remainingMs)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Authoritatively clears the focus_active flag in SharedPreferences.
     * Called by Kotlin on natural expiry and when going idle — before JS is notified.
     */
    private fun clearFocusActive() {
        getSharedPreferences(AppBlockerAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("focus_active", false)
            .apply()
    }
}
