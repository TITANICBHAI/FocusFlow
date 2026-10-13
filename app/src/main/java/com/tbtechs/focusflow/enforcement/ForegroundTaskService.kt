package com.tbtechs.focusflow.enforcement

import com.tbtechs.focusflow.enforcement.receivers.TaskEndAlarmReceiver

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.media.AudioAttributes
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationCompat
import com.tbtechs.focusflow.data.repository.AlarmRuntimeDiagnostics
import com.tbtechs.focusflow.data.repository.AlarmRepository
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.data.repository.TaskAlarmRegistry
import com.tbtechs.focusflow.data.repository.TaskEndAlarmIdentity
import com.tbtechs.focusflow.enforcement.health.AccessibilityStateChangeTracker
import com.tbtechs.focusflow.enforcement.health.EnforcementHealth
import com.tbtechs.focusflow.enforcement.health.EnforcementHealthReader
import com.tbtechs.focusflow.notifications.status.StatusCardClock
import com.tbtechs.focusflow.notifications.status.StatusCardMapper
import com.tbtechs.focusflow.notifications.status.StatusCardModel
import com.tbtechs.focusflow.notifications.status.StatusCardRenderer
import com.tbtechs.focusflow.R


import com.tbtechs.focusflow.widget.FocusFlowWidget
import org.json.JSONArray
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * ForegroundTaskService
 *
 * Runs persistently at all times — not only during focus sessions.
 * This keeps the process alive so the AccessibilityService is never killed.
 *
 * Two modes:
 *   IDLE   — No active task. Shows a quiet "FocusFlow is monitoring" notification.
 *   ACTIVE — Focus session running. Shows task name + live chronometer countdown
 *            + progress bar + action buttons.
 *
 * Notification action buttons (ACTIVE mode only):
 *   ✓ Done   → NotificationActionReceiver → JS completeTask()
 *   +15m     → NotificationActionReceiver → JS extendTaskTime(15)
 *   +30m     → NotificationActionReceiver → JS extendTaskTime(30)
 *   Skip     → NotificationActionReceiver → JS skipTask()
 *
 * Intent extras for ACTIVE mode:
 *   "taskId"   String  — DB id of the active task (for action buttons)
 *   "taskName" String  — display name of the active task
 *   "endTimeMs" Long   — absolute epoch ms when the task ends
 *   "nextName"  String? — name of the next task (shown as sub-text)
 */
class ForegroundTaskService : Service() {

    companion object {
        const val CHANNEL_ID        = "focusday_foreground"
        const val CHANNEL_NAME      = "FocusFlow Active Task"
        const val NOTIFICATION_ID   = 1001
        const val ACTION_ENSURE_RUNNING = "com.tbtechs.focusflow.ENSURE_RUNNING"
        const val ACTION_STOP       = "com.tbtechs.focusflow.STOP_SERVICE"
        const val ACTION_SET_IDLE   = "com.tbtechs.focusflow.SET_IDLE"
        const val ACTION_SET_BREAK  = "com.tbtechs.focusflow.SET_BREAK"
        const val ACTION_CLEAR_BREAK = "com.tbtechs.focusflow.CLEAR_BREAK"
        const val ACTION_TASK_ENDED = "com.tbtechs.focusflow.TASK_ENDED"

        const val EXTRA_TASK_ID     = "taskId"
        const val EXTRA_TASK_NAME   = "taskName"
        const val EXTRA_END_MS      = "endTimeMs"
        const val EXTRA_START_MS    = "startTimeMs"
        const val EXTRA_NEXT_NAME   = "nextName"
        const val EXTRA_BREAK_UNTIL_MS = "breakUntilMs"

        private const val PREFS_NAME = "focusday_prefs"

        /** How often the fallback poller checks the foreground app (ms). */
        private const val FALLBACK_POLL_MS = 1_000L

        /** Cooldown: don't re-block the same package within this window (ms). */
        private const val FALLBACK_COOLDOWN_MS = 2_000L

        /** How often the in-process VPN health check runs (ms). */
        private const val VPN_HEALTH_CHECK_MS = 60_000L

        /** Notification channel for full-screen block-overlay intent. */
        private const val BLOCK_ALERT_CHANNEL  = "focusday_block_alert"
        private const val BLOCK_ALERT_NOTIF_ID = 9001

        /**
         * Notification channel for the full-screen task-end alarm.  Must be
         * IMPORTANCE_HIGH with sound + vibration so the heads-up presents and
         * the full-screen intent is honoured even on locked / asleep devices.
         */
        const val TASK_ALARM_CHANNEL   = "task_alarm"
        const val TASK_END_QUIET_CHANNEL = "task_alarm_quiet"
        const val TASK_ALARM_NOTIF_ID  = TaskEndAlarmIdentity.NOTIFICATION_ID

        /** The single authoritative definition of the task-end notification channel. */
        fun ensureTaskAlarmChannel(context: Context) {
            val app = context.applicationContext
            val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            if (manager.getNotificationChannel(TASK_ALARM_CHANNEL) != null) return

            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val sound = android.media.RingtoneManager.getDefaultUri(
                android.media.RingtoneManager.TYPE_ALARM,
            )
            val channel = NotificationChannel(
                TASK_ALARM_CHANNEL,
                "Task End Alarm",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Wakes the device when a task ends."
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 600L, 600L, 600L)
                setSound(sound, attrs)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }

        private fun ensureQuietTaskEndChannel(context: Context) {
            val app = context.applicationContext
            val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            if (manager.getNotificationChannel(TASK_END_QUIET_CHANNEL) != null) return
            val channel = NotificationChannel(
                TASK_END_QUIET_CHANNEL,
                "Task End Notifications",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "A quiet notification when a scheduled task ends."
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }

        /**
         * Posts the heads-up + full-screen-intent task-end alarm notification.
         *
         * Exposed as a static helper so [TaskEndAlarmReceiver] (fired by
         * AlarmManager) can post the same alarm UI as the in-process Handler
         * tick. Without this split, only the foreground service could trigger
         * the full-screen alarm — which silently fails the moment Android
         * Doze pauses the service's main looper, which is exactly the bug
         * users hit when alarms "didn't go off" after the screen had been
         * off for a while.
         *
         * The (tag, ID) pair is task-specific. A durable dedupe ledger prevents
         * the service tick and AlarmManager receiver from posting the same end
         * event twice.
         */
        fun postTaskEndAlarmNotification(
            context: Context,
            endedTaskId: String,
            endedTaskName: String,
            endedAtMs: Long,
        ) {
            if (endedTaskId.isBlank()) return
            try {
                val app = context.applicationContext
                val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val ringAlarm = SettingsRepository.taskEndAlarmRings(app)

                if (ringAlarm) {
                    ensureTaskAlarmChannel(app)
                } else {
                    ensureQuietTaskEndChannel(app)
                }

                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                val fullScreenPi = AlarmRepository.buildShowPendingIntent(
                    app,
                    endedTaskId,
                    endedTaskName,
                    endedAtMs,
                    flags,
                    ringAlarm = ringAlarm,
                )

                val displayName = if (endedTaskName.isNotEmpty()) endedTaskName else "Your task"

                val notification = NotificationCompat.Builder(
                    app,
                    if (ringAlarm) TASK_ALARM_CHANNEL else TASK_END_QUIET_CHANNEL,
                )
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("\u23F0 Time's up")
                    .setContentText("$displayName has ended — tap to choose")
                    .setContentIntent(fullScreenPi)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setAutoCancel(true)
                    .apply {
                        if (ringAlarm) {
                            setFullScreenIntent(fullScreenPi, true)
                            setPriority(NotificationCompat.PRIORITY_MAX)
                            setCategory(NotificationCompat.CATEGORY_ALARM)
                            setOngoing(true)
                        } else {
                            setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            setCategory(NotificationCompat.CATEGORY_REMINDER)
                            setOngoing(false)
                        }
                    }
                    .build()
                val posted = TaskAlarmRegistry(app).postOnce(
                    endedTaskId,
                    endedAtMs,
                    System.currentTimeMillis(),
                ) {
                    AlarmRuntimeDiagnostics.record("notification.nm_notify.begin")
                    try {
                        nm.notify(
                            TaskEndAlarmIdentity.notificationTag(endedTaskId),
                            TaskEndAlarmIdentity.NOTIFICATION_ID,
                            notification,
                        )
                        AlarmRuntimeDiagnostics.record("notification.nm_notify.returned")
                    } catch (error: Exception) {
                        AlarmRuntimeDiagnostics.record(
                            "notification.nm_notify.threw",
                            "exception=${error.javaClass.simpleName}",
                        )
                        throw error
                    }
                }
                if (!posted) return

                // Background activity launches are restricted. Keep this
                // non-authoritative fallback only on devices with overlay access.
                val overlayAuthorized = ringAlarm && Settings.canDrawOverlays(app)
                if (overlayAuthorized) {
                    val activityIntent = Intent(app, TaskAlarmActivity::class.java).apply {
                        this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_NO_HISTORY
                        action = TaskAlarmActivity.ACTION_SHOW_ALARM
                        data = TaskEndAlarmIdentity.dataUri(endedTaskId)
                        putExtra(TaskAlarmActivity.EXTRA_TASK_ID, endedTaskId)
                        putExtra(TaskAlarmActivity.EXTRA_TASK_NAME, endedTaskName)
                        putExtra(TaskAlarmActivity.EXTRA_END_MS, endedAtMs)
                        putExtra(TaskAlarmActivity.EXTRA_RING_ALARM, true)
                    }
                    try {
                        app.startActivity(activityIntent)
                        AlarmRuntimeDiagnostics.record(
                            "activityFallback.startActivity.returned",
                            "overlayAuthorized=true visibility=unknown",
                        )
                    } catch (error: Exception) {
                        AlarmRuntimeDiagnostics.record(
                            "activityFallback.startActivity.threw",
                            "overlayAuthorized=true exception=${error.javaClass.simpleName}",
                        )
                    }
                } else {
                    AlarmRuntimeDiagnostics.record(
                        "activityFallback.suppressed",
                        "overlayAuthorized=false",
                    )
                }
            } catch (_: Exception) { /* alarm is best-effort */ }
        }
    }

    private var taskId: String    = ""
    private var taskName: String  = ""
    private var endTimeMs: Long   = 0L
    private var startTimeMs: Long = 0L
    private var nextName: String? = null
    private var isActiveMode: Boolean = false
    private var breakUntilMs: Long = 0L
    private var enforcementHealth = EnforcementHealth.UNKNOWN
    private val accessibilityStateChangeTracker = AccessibilityStateChangeTracker()

    /** Wall-clock ms when this service process first called onCreate(). Used
     *  by the idle notification chronometer so it always counts up from when
     *  monitoring started, not from when the latest goIdle() was called. */
    private var serviceStartMs: Long = 0L

    // ── Fallback blocker state (used only when accessibility is not granted) ──
    private lateinit var blockPrefs: SharedPreferences
    private val allowanceLedger by lazy { AllowanceLedgerProvider.get(this) }
    private var fallbackLastBlockedPkg: String? = null
    private var fallbackLastBlockedAtMs: Long   = 0L
    private val todayDateFormatter = java.text.SimpleDateFormat(
        "yyyy-MM-dd",
        java.util.Locale.US,
    )
    private var todayDateFormatterTimeZoneId = java.util.TimeZone.getDefault().id

    private val handler = Handler(Looper.getMainLooper())
    private val enforcementHealthScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var enforcementHealthRefreshJob: Job? = null

    /**
     * Secondary in-process VPN health check — runs every [VPN_HEALTH_CHECK_MS].
     *
     * The AccessibilityService runs the same check every 10 s, but
     * ForegroundTaskService is typically hardier (it holds a foreground notification
     * Android is reluctant to kill). Having a second, independent watcher here
     * means the VPN is restarted quickly even on devices where the accessibility
     * service is sluggish to recover.
     *
     * The AlarmManager-based [VpnWatchdogReceiver] is the ultimate fallback for
     * full process-death scenarios; this runnable handles in-process silent kills.
     */
    private val vpnHealthRunnable = object : Runnable {
        override fun run() {
            checkAndHealVpn()
            handler.postDelayed(this, VPN_HEALTH_CHECK_MS)
        }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            val remaining = endTimeMs - System.currentTimeMillis()
            if (remaining <= 0) {
                // Capture identity BEFORE clearing — goIdle() resets these fields.
                val endedTaskId   = taskId
                val endedTaskName = taskName
                clearFocusActive()
                sendBroadcast(Intent(ACTION_TASK_ENDED).apply {
                    `package` = applicationContext.packageName
                    putExtra("taskId", endedTaskId)
                })
                // Wake the device with a full-screen alarm so the user doesn't
                // miss the end of their task.  The task itself stays in the
                // awaiting-decision state until the user picks Done / Extend / Skip.
                triggerTaskAlarm(endedTaskId, endedTaskName, endTimeMs)
                goIdle()
                return
            }
            // Update the progress bar portion of the notification
            updateNotification(remaining)
            // Push fresh data to any home screen widgets
            FocusFlowWidget.pushWidgetUpdate(applicationContext)
            // Tick every 30 s — smooth enough for the progress bar, easy on battery
            handler.postDelayed(this, 30_000)
        }
    }

    private val breakTickRunnable = object : Runnable {
        override fun run() {
            // A break must never suppress the task-end event. This can happen
            // when the final Pomodoro break runs into the task's scheduled end.
            if (endTimeMs > 0L && System.currentTimeMillis() >= endTimeMs) {
                val endedTaskId = taskId
                val endedTaskName = taskName
                clearFocusActive()
                sendBroadcast(Intent(ACTION_TASK_ENDED).apply {
                    `package` = applicationContext.packageName
                    putExtra("taskId", endedTaskId)
                })
                triggerTaskAlarm(endedTaskId, endedTaskName, endTimeMs)
                goIdle()
                return
            }
            if (breakUntilMs <= 0L || System.currentTimeMillis() >= breakUntilMs) {
                resumeFromBreak()
                return
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildBreakNotification())
            handler.postDelayed(this, 1_000L)
        }
    }

    /**
     * Fallback blocker poll runnable — runs every [FALLBACK_POLL_MS] ms.
     *
     * Immediately bails out if the accessibility service is enabled (that path
     * is faster and more reliable — no need to duplicate work).  When
     * accessibility is absent this is the only enforcement mechanism, so it
     * uses UsageStatsManager to detect the foreground package, mirrors the
     * same SharedPreferences block-check logic, and if a blocked app is found
     * it launches the overlay (which pushes the app to the background) then
     * fires killBackgroundProcesses 400 ms later.
     */
    private val fallbackPollRunnable = object : Runnable {
        override fun run() {
            // ── 1. Defer to accessibility if it is active ─────────────────
            val accessibilityEnabled = isAccessibilityServiceEnabled()
            if (accessibilityStateChangeTracker.observe(accessibilityEnabled)) {
                refreshEnforcementHealth()
            }
            if (accessibilityEnabled) {
                handler.postDelayed(this, FALLBACK_POLL_MS)
                return
            }

            // ── 2. Only act when at least one blocking mode is active ──────
            val now = System.currentTimeMillis()
            val focusActive = blockPrefs.getBoolean("focus_active", false).let { on ->
                if (on) {
                    val endMs = blockPrefs.getLong("task_end_ms", 0L)
                    if (endMs > 0L && now > endMs) {
                        blockPrefs.edit().putBoolean("focus_active", false).apply()
                        false
                    } else on
                } else false
            }
            val saActive = blockPrefs.getBoolean("standalone_block_active", false).let { on ->
                if (on) {
                    val untilMs = blockPrefs.getLong("standalone_block_until_ms", 0L)
                    if (untilMs > 0L && now >= untilMs) {
                        blockPrefs.edit().putBoolean("standalone_block_active", false).apply()
                        VpnPolicyCoordinator.requestSync(this@ForegroundTaskService)
                        false
                    } else on
                } else false
            }
            val greyoutJson = blockPrefs.getString("greyout_schedule", "[]") ?: "[]"
            val hasGreyout = greyoutJson != "[]" && greyoutJson.isNotEmpty()
            val alwaysBlockActive = blockPrefs.getBoolean("always_block_active", false)
            val hasAllowanceConfig = blockPrefs.getString("daily_allowance_config", null)
                ?.let { it.isNotBlank() && it != "null" && it != "[]" } == true
            val allowanceActive = hasAllowanceConfig &&
                (focusActive || saActive || alwaysBlockActive)

            if (!focusActive && !saActive && !hasGreyout &&
                !alwaysBlockActive && !allowanceActive) {
                // Nothing to enforce — reset cooldown and poll lightly
                fallbackLastBlockedPkg = null
                handler.postDelayed(this, FALLBACK_POLL_MS)
                return
            }

            // ── 3. Detect foreground package ──────────────────────────────
            val pkg = getFallbackForegroundPackage()
            if (pkg.isNullOrEmpty() || pkg == packageName) {
                handler.postDelayed(this, FALLBACK_POLL_MS)
                return
            }

            // ── 4. Skip BLOCKABLE_AFTER_WARNING packages ──────────────────
            // These are launcher / dialer / Settings etc. — bypassed by
            // default so the user is never trapped. They can still be
            // blocked if the user explicitly opts in via the picker (after
            // a confirmation warning), but the AccessibilityService handles
            // that opt-in path; the fallback poller skips them outright.
            if (AppBlockerAccessibilityService.BLOCKABLE_AFTER_WARNING.any {
                    pkg.equals(it, ignoreCase = true)
                }) {
                handler.postDelayed(this, FALLBACK_POLL_MS)
                return
            }

            // ── 5. Check if the package should be blocked ─────────────────
            if (!isFallbackBlocked(pkg, focusActive, saActive, greyoutJson)) {
                fallbackLastBlockedPkg = null
                handler.postDelayed(this, FALLBACK_POLL_MS)
                return
            }

            // ── 6. Cooldown guard — don't hammer the same package ─────────
            val samePackage     = pkg == fallbackLastBlockedPkg
            val cooldownExpired = (now - fallbackLastBlockedAtMs) > FALLBACK_COOLDOWN_MS
            if (!samePackage || cooldownExpired) {
                fallbackLastBlockedPkg = pkg
                fallbackLastBlockedAtMs = now
                handleFallbackBlock(pkg)
            }

            handler.postDelayed(this, FALLBACK_POLL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceStartMs = System.currentTimeMillis()
        blockPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        AllowanceUsageStateStore(
            prefs = blockPrefs,
            ledger = allowanceLedger,
        ).recoverPersistedCheckpoint(serviceStartMs)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildIdleNotification())
        accessibilityStateChangeTracker.observe(isAccessibilityServiceEnabled())
        refreshEnforcementHealth()
        // Start the fallback blocker poll — it self-disables instantly when
        // accessibility is active, so there is zero overhead in the normal path.
        handler.postDelayed(fallbackPollRunnable, FALLBACK_POLL_MS)
        // Start the in-process VPN health check. First tick is staggered by
        // half the interval so it doesn't race the AccessibilityService check.
        handler.postDelayed(vpnHealthRunnable, VPN_HEALTH_CHECK_MS / 2)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ENSURE_RUNNING -> {
                // MainActivity.onStart() reaches this path to refresh the idle card.
                refreshEnforcementHealth()
                if (!isActiveMode) restoreSessionStateFromPreferences()
            }
            ACTION_STOP -> {
                handler.removeCallbacks(tickRunnable)
                clearFocusActive()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SET_IDLE -> {
                handler.removeCallbacks(tickRunnable)
                handler.removeCallbacks(breakTickRunnable)
                clearFocusActive()
                goIdle()
                return START_STICKY
            }
            ACTION_SET_BREAK -> {
                enterBreak(intent?.getLongExtra(EXTRA_BREAK_UNTIL_MS, 0L) ?: 0L)
                return START_STICKY
            }
            ACTION_CLEAR_BREAK -> {
                resumeFromBreak()
                return START_STICKY
            }
            else -> {
                val id      = intent?.getStringExtra(EXTRA_TASK_ID)
                val name    = intent?.getStringExtra(EXTRA_TASK_NAME)
                val endMs   = intent?.getLongExtra(EXTRA_END_MS, 0L) ?: 0L
                val startMs = intent?.getLongExtra(EXTRA_START_MS, 0L) ?: 0L
                val next    = intent?.getStringExtra(EXTRA_NEXT_NAME)

                if (name != null && endMs > 0L) {
                    taskId    = id ?: ""
                    taskName  = name
                    endTimeMs = endMs
                    nextName  = next
                    breakUntilMs = 0L
                    handler.removeCallbacks(breakTickRunnable)

                    // Only reset the start time on the first launch of a session.
                    // If isActiveMode is already true this is an update call (e.g. after
                    // a +15m / +30m extend) — preserve the original startTimeMs so the
                    // notification progress bar continues from where it was, not from 0%.
                    if (!isActiveMode) {
                        // Prefer the actual task startTime from the JS layer (EXTRA_START_MS).
                        // Fall back to System.currentTimeMillis() only when not provided.
                        startTimeMs  = if (startMs > 0L) startMs else System.currentTimeMillis()
                        isActiveMode = true
                        // Persist start time so the widget can compute progress correctly
                        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putLong("task_start_ms", startTimeMs)
                            .apply()
                    } else {
                        // Update persisted end time so the widget reflects the extension
                        isActiveMode = true
                    }

                    val notification = buildActiveNotification(endMs - System.currentTimeMillis())
                    startForeground(NOTIFICATION_ID, notification)

                    // Acquire wake lock so the CPU stays alive during the session even
                    // when the screen turns off — prevents OEM schedulers from throttling
                    // the AccessibilityService on MIUI, ColorOS, and Samsung One UI.
                    WakeLockManager.acquire(this)

                    handler.removeCallbacks(tickRunnable)
                    handler.post(tickRunnable)

                    // Push widget update immediately — don't wait for the 30s tick
                    FocusFlowWidget.pushWidgetUpdate(applicationContext)
                } else if (intent == null) {
                    // Android OS restarted this service after it was killed (START_STICKY).
                    restoreSessionStateFromPreferences()
                } else {
                    // Explicit idle starts must not reset a live focus session.
                    if (!isActiveMode) {
                        goIdle()
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun restoreSessionStateFromPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val focusActive = prefs.getBoolean("focus_active", false)
        val restoredBreakUntil = prefs.getLong("focus_break_until_ms", 0L)
        if (restoredBreakUntil > System.currentTimeMillis()) {
            taskId = prefs.getString("task_id", "") ?: ""
            taskName = prefs.getString("task_name", "Focus session") ?: "Focus session"
            endTimeMs = prefs.getLong("task_end_ms", 0L)
            nextName = prefs.getString("next_task_name", null)
            startTimeMs = prefs.getLong("task_start_ms", System.currentTimeMillis())
            isActiveMode = true
            breakUntilMs = restoredBreakUntil
            startForeground(NOTIFICATION_ID, buildBreakNotification())
            WakeLockManager.acquire(this)
            handler.removeCallbacks(breakTickRunnable)
            handler.post(breakTickRunnable)
        } else if (focusActive) {
            val restoredName = prefs.getString("task_name", null)
            val restoredEndMs = prefs.getLong("task_end_ms", 0L)
            if (restoredName != null && restoredEndMs > System.currentTimeMillis()) {
                taskId = prefs.getString("task_id", "") ?: ""
                taskName = restoredName
                endTimeMs = restoredEndMs
                nextName = prefs.getString("next_task_name", null)
                startTimeMs = prefs.getLong("task_start_ms", System.currentTimeMillis())
                isActiveMode = true
                val notification = buildActiveNotification(restoredEndMs - System.currentTimeMillis())
                startForeground(NOTIFICATION_ID, notification)
                WakeLockManager.acquire(this)
                handler.removeCallbacks(tickRunnable)
                handler.post(tickRunnable)
                FocusFlowWidget.pushWidgetUpdate(applicationContext)
            } else {
                clearFocusActive()
                goIdle()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        handler.removeCallbacks(fallbackPollRunnable)
        handler.removeCallbacks(vpnHealthRunnable)
        enforcementHealthRefreshJob?.cancel()
        enforcementHealthScope.cancel()
        WakeLockManager.release()
        super.onDestroy()
    }

    // ─── Mode helpers ──────────────────────────────────────────────────────────

    private fun goIdle() {
        isActiveMode = false
        breakUntilMs = 0L
        taskId       = ""
        taskName     = ""
        endTimeMs    = 0L
        startTimeMs  = 0L
        nextName     = null
        handler.removeCallbacks(tickRunnable)
        handler.removeCallbacks(breakTickRunnable)
        blockPrefs.edit().remove("focus_break_until_ms").apply()
        // Release the wake lock — CPU throttling is fine again when no session is active
        WakeLockManager.release()
        // Stop all aversive deterrents (dim overlay, vibration) if they were running
        AversiveActionsManager.stopAll(this)
        // Stop network blocking and restore connectivity if the restore flag is set
        stopNetworkBlock()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildIdleNotification())
        // Update widget to idle state
        FocusFlowWidget.pushWidgetUpdate(applicationContext)
    }

    private fun enterBreak(untilMs: Long) {
        if (untilMs <= System.currentTimeMillis()) {
            resumeFromBreak()
            return
        }
        breakUntilMs = untilMs
        blockPrefs.edit()
            .putBoolean("focus_active", false)
            .putLong("focus_break_until_ms", untilMs)
            .apply()
        handler.removeCallbacks(tickRunnable)
        handler.removeCallbacks(breakTickRunnable)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildBreakNotification())
        handler.postDelayed(breakTickRunnable, 1_000L)
        FocusFlowWidget.pushWidgetUpdate(applicationContext)
    }

    private fun resumeFromBreak() {
        breakUntilMs = 0L
        blockPrefs.edit()
            .putBoolean("focus_active", true)
            .remove("focus_break_until_ms")
            .apply()
        handler.removeCallbacks(breakTickRunnable)
        if (isActiveMode && endTimeMs > System.currentTimeMillis()) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildActiveNotification(endTimeMs - System.currentTimeMillis()))
            handler.removeCallbacks(tickRunnable)
            handler.post(tickRunnable)
        } else if (isActiveMode) {
            goIdle()
        }
        FocusFlowWidget.pushWidgetUpdate(applicationContext)
    }

    /**
     * Stops the VPN network blocker when a focus session ends.
     * Only acts if net_block_enabled is true. Does NOT stop the VPN if a
     * standalone block session is still active — the VPN must keep running
     * for the standalone block even after the focus session ends.
     */
    private fun stopNetworkBlock() {
        val prefs = getSharedPreferences(AppBlockerAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("net_block_enabled", false)) return
        // Guard: if a standalone block or persistent VPN-only list is active,
        // leave the VPN running after the focus task ends.
        val saActive = prefs.getBoolean("standalone_block_active", false)
        if (saActive) {
            val untilMs = prefs.getLong("standalone_block_until_ms", 0L)
            if (untilMs <= 0L || System.currentTimeMillis() < untilMs) return
        }
        if (NetworkBlockerVpnService.hasPersistentVpnConfiguration(prefs)) return
        try {
            // Focus teardown is a policy update. Let the coordinator serialize
            // the stop with any queued start or reconfiguration command.
            VpnPolicyCoordinator.requestSync(this)
        } catch (e: Exception) {
            prefs.edit()
                .putString("vpn_status", NetworkBlockerVpnService.STATUS_STARTUP_FAILED)
                .putString("vpn_error", e.message ?: "ForegroundTaskService could not stop VPN service")
                .apply()
        }
    }

    // ─── VPN health check ──────────────────────────────────────────────────────

    /**
     * Checks whether the VPN tunnel should be running and restarts it if not.
     * Mirrors the same logic in AppBlockerAccessibilityService but runs inside
     * ForegroundTaskService, which is typically more resilient to OEM killers.
     *
     * Guards:
     *   • net_block_self_heal must be true  (user opted in)
     *   • net_block_vpn must be true        (VPN mechanism selected)
     *   • VPN is not already running
     *   • A blocking session is currently active
     *   • VPN permission is still held
     */
    private fun checkAndHealVpn() {
        val prefs = blockPrefs
        if (!prefs.getBoolean("net_block_self_heal", false)) return
        if (!prefs.getBoolean("net_block_vpn", true)) return
        if (NetworkBlockerVpnService.isRunning) return

        val now = System.currentTimeMillis()
        val focusActive = prefs.getBoolean("focus_active", false).let { on ->
            if (!on) false
            else {
                val endMs = prefs.getLong("task_end_ms", 0L)
                endMs <= 0L || now < endMs
            }
        }
        val saActive = prefs.getBoolean("standalone_block_active", false).let { on ->
            if (!on) false
            else {
                val untilMs = prefs.getLong("standalone_block_until_ms", 0L)
                untilMs <= 0L || now < untilMs
            }
        }
        if (!focusActive && !saActive &&
            !NetworkBlockerVpnService.hasPersistentVpnConfiguration(prefs)
        ) return

        // Cannot restart without VPN permission.
        // Write the permission-lost flag so the JS layer can surface a re-grant prompt.
        try {
            if (android.net.VpnService.prepare(this) != null) {
                prefs.edit().putBoolean("vpn_permission_lost", true).apply()
                VpnRecoveryNotifier.postPermissionRequired(this)
                return
            }
        } catch (_: Exception) { return }

        try {
            // The coordinator owns recovery dispatch and generation ordering.
            VpnPolicyCoordinator.requestRecoverySync(this)
        } catch (e: Exception) {
            blockPrefs.edit()
                .putString("vpn_status", NetworkBlockerVpnService.STATUS_STARTUP_FAILED)
                .putString("vpn_error", e.message ?: "ForegroundTaskService could not start VPN service")
                .apply()
        }
    }

    // ─── Notification builders ─────────────────────────────────────────────────

    private fun createNotificationChannel() {
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
        ensureTaskAlarmChannel(this)
    }

    /** Routes the in-process fallback tick through the same validation path as AlarmManager. */
    private fun triggerTaskAlarm(endedTaskId: String, endedTaskName: String, endedAtMs: Long) {
        if (endedTaskId.isBlank()) return
        runCatching {
            sendBroadcast(
                Intent(applicationContext, TaskEndAlarmReceiver::class.java).apply {
                    action = TaskEndAlarmReceiver.ACTION_FIRE
                    data = TaskEndAlarmIdentity.dataUri(endedTaskId)
                    putExtra(TaskEndAlarmReceiver.EXTRA_TASK_ID, endedTaskId)
                    putExtra(TaskEndAlarmReceiver.EXTRA_TASK_NAME, endedTaskName)
                    putExtra(TaskEndAlarmReceiver.EXTRA_END_MS, endedAtMs)
                },
            )
        }.onFailure {
            android.util.Log.w("ForegroundTaskService", "Could not dispatch task-end validation.")
        }
    }

    private fun notificationClock() = StatusCardClock(
        wallClockMs = System.currentTimeMillis(),
        elapsedRealtimeMs = SystemClock.elapsedRealtime(),
        zoneId = ZoneId.systemDefault(),
        locale = Locale.getDefault(),
    )

    private fun renderStatusCard(model: StatusCardModel): Notification =
        StatusCardRenderer.render(this, CHANNEL_ID, model)

    private fun refreshEnforcementHealth() {
        enforcementHealthRefreshJob?.cancel()
        enforcementHealthRefreshJob = enforcementHealthScope.launch {
            try {
                val refreshedHealth = EnforcementHealthReader.read(applicationContext)
                if (refreshedHealth != enforcementHealth) {
                    enforcementHealth = refreshedHealth
                    if (!isActiveMode) {
                        val notificationManager =
                            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.notify(NOTIFICATION_ID, buildIdleNotification())
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w(
                    "ForegroundTaskService",
                    "Could not refresh enforcement health",
                    error,
                )
            }
        }
    }

    private fun buildIdleNotification(): Notification =
        renderStatusCard(
            StatusCardMapper.idle(
                serviceStartMs = serviceStartMs,
                clock = notificationClock(),
                needsAttention = enforcementHealth.needsAttention,
                healthKnown = enforcementHealth.isKnown,
            ),
        )

    private fun buildActiveNotification(remainingMs: Long): Notification =
        renderStatusCard(
            StatusCardMapper.focus(
                taskId = taskId,
                taskName = taskName,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                remainingMs = remainingMs,
                nextName = nextName,
                clock = notificationClock(),
            ),
        )

    private fun updateNotification(remainingMs: Long) {
        val notification = buildActiveNotification(remainingMs)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildBreakNotification(): Notification =
        renderStatusCard(
            StatusCardMapper.breakTime(
                taskName = taskName,
                breakUntilMs = breakUntilMs,
                clock = notificationClock(),
            ),
        )

    private fun clearFocusActive() {
        getSharedPreferences(AppBlockerAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("focus_active", false)
            .remove("task_id")
            .remove("task_name")
            .remove("task_start_ms")
            .remove("task_end_ms")
            .remove("task_color")
            .remove("next_task_name")
            .remove("task_duration_ms")
            .remove("task_last_written_ms")
            .remove("focus_break_until_ms")
            .apply()
    }

    /** ISO-8601 date key in the device's local timezone, shared by allowance reads/writes. */
    private fun todayDateString(): String = synchronized(todayDateFormatter) {
        val timeZone = java.util.TimeZone.getDefault()
        if (timeZone.id != todayDateFormatterTimeZoneId) {
            todayDateFormatter.timeZone = timeZone
            todayDateFormatterTimeZoneId = timeZone.id
        }
        todayDateFormatter.format(java.util.Date())
    }

    // ─── Fallback blocker helpers ───────────────────────────────────────────────

    /**
     * Returns true if our AppBlockerAccessibilityService is currently active.
     * When true the fallback poller defers entirely — accessibility is faster
     * and event-driven so there is no value in running the polling path too.
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            enabled.contains(packageName, ignoreCase = true)
        } catch (_: Exception) { false }
    }

    /**
     * Returns the package name of the currently visible foreground app using
     * UsageStatsManager.  Returns null if the permission is not granted or the
     * query returns no results.
     *
     * A 5-second look-back window is used to find the latest foreground event,
     * rather than treating the most-recently-used app for the day as current.
     */
    private fun getFallbackForegroundPackage(): String? {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 5_000L, now)
            val event = UsageEvents.Event()
            val foregroundType = UsageEvents.Event.ACTIVITY_RESUMED
            var latest: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == foregroundType) {
                    latest = event.packageName
                }
            }
            latest
        } catch (_: Exception) { null }
    }

    /**
     * Mirrors the core blocking logic when the AccessibilityService is
     * unavailable.
     */
    private fun isFallbackBlocked(
        pkg: String,
        focusActive: Boolean,
        saActive: Boolean,
        greyoutJson: String
    ): Boolean {
        // ── Task focus: block anything NOT in the allowed list ────────────
        if (focusActive) {
            val allowedJson = blockPrefs.getString("allowed_packages", "[]") ?: "[]"
            val allowed = try {
                val arr = JSONArray(allowedJson)
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            } catch (_: Exception) { emptySet() }
            if (!allowed.any { pkg.equals(it, ignoreCase = true) }) return true
        }

        // ── Standalone block: block anything IN the blocked list ──────────
        if (saActive) {
            val blockedJson = blockPrefs.getString("standalone_blocked_packages", "[]") ?: "[]"
            try {
                val arr = JSONArray(blockedJson)
                for (i in 0 until arr.length()) {
                    if (pkg.equals(arr.getString(i), ignoreCase = true)) return true
                }
            } catch (_: Exception) { }
        }

        // ── Greyout schedule ──────────────────────────────────────────────
        if (greyoutJson != "[]" && greyoutJson.isNotEmpty()) {
            if (
                ForegroundTaskGreyoutPolicy.isPackageBlocked(
                    greyoutJson = greyoutJson,
                    packageName = pkg,
                    atMs = System.currentTimeMillis(),
                )
            ) {
                return true
            }
        }

        // ── Always-on enforcement ─────────────────────────────────────────
        val alwaysBlockActive = blockPrefs.getBoolean("always_block_active", false)
        if (alwaysBlockActive) {
            val alwaysJson = blockPrefs.getString("always_block_packages", "[]") ?: "[]"
            try {
                val arr = JSONArray(alwaysJson)
                for (i in 0 until arr.length()) {
                    if (pkg.equals(arr.getString(i), ignoreCase = true)) return true
                }
            } catch (_: Exception) { }
        }

        // ── Daily allowance exhaustion (read-only mirror of A11y state) ────
        // Allowances only enforce inside an active focus, standalone, or
        // always-on session. Keeping this guard aligned with the accessibility
        // service prevents the fallback path from blocking during free time.
        val allowanceActive = focusActive || saActive || alwaysBlockActive
        val configJson = blockPrefs.getString("daily_allowance_config", null)
        if (allowanceActive && !configJson.isNullOrBlank() && configJson != "null") {
            try {
                val today = todayDateString()
                val now = System.currentTimeMillis()
                val arr = JSONArray(configJson)
                for (i in 0 until arr.length()) {
                    val entry = arr.optJSONObject(i) ?: continue
                    if (!entry.optString("packageName", "")
                            .equals(pkg, ignoreCase = true)) continue

                    val mode = entry.optString("mode", "count")
                    val limit = when (mode) {
                        AllowanceLedger.MODE_COUNT ->
                            entry.optInt("countPerDay", 1).coerceAtLeast(1).toLong()
                        AllowanceLedger.MODE_TIME_BUDGET ->
                            entry.optInt("budgetMinutes", 30).toLong() * 60_000L
                        AllowanceLedger.MODE_INTERVAL ->
                            entry.optInt("intervalMinutes", 5).toLong() * 60_000L
                        else -> 0L
                    }
                    val windowMs = if (mode == AllowanceLedger.MODE_INTERVAL) {
                        entry.optInt("intervalHours", 1).toLong() * 3_600_000L
                    } else {
                        0L
                    }
                    if (mode in setOf(
                            AllowanceLedger.MODE_COUNT,
                            AllowanceLedger.MODE_TIME_BUDGET,
                            AllowanceLedger.MODE_INTERVAL,
                        ) &&
                        allowanceLedger.readAllowance(
                            packageName = pkg,
                            mode = mode,
                            today = today,
                            nowMs = now,
                            limit = limit,
                            windowMs = windowMs,
                        ).exhausted
                    ) {
                        return true
                    }
                    break
                }
            } catch (_: Exception) { }
        }

        return false
    }

    /**
     * Fallback enforcement action: launches [BlockOverlayActivity] via a
     * full-screen notification PendingIntent.
     *
     * Using a full-screen intent (rather than startActivity) bypasses the
     * Android 10+ restriction that prevents foreground services from starting
     * activities directly — the system launches the activity on our behalf so
     * SYSTEM_ALERT_WINDOW is not required.
     *
     * overlay_x_ready is written after a short delay so the X button only
     * appears once the overlay is visually in front (no accessibility events
     * are available in this no-accessibility path).
     */
    private fun handleFallbackBlock(blockedPackage: String) {
        // Signal overlay to await the blocked package leaving foreground
        blockPrefs.edit().putString("overlay_awaiting_pkg", blockedPackage).apply()

        // Resolve display name
        val appName = try {
            val pm   = applicationContext.packageManager
            val info = pm.getApplicationInfo(blockedPackage, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) { blockedPackage }

        // Build the PendingIntent pointing at BlockOverlayActivity
        val activityIntent = Intent(applicationContext, BlockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(BlockOverlayActivity.EXTRA_BLOCKED_PKG, blockedPackage)
            putExtra(BlockOverlayActivity.EXTRA_BLOCKED_NAME, appName)
        }
        val pi = PendingIntent.getActivity(
            applicationContext, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Ensure the block-alert channel exists
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(
            BLOCK_ALERT_CHANNEL, "Block Alert", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        nm?.createNotificationChannel(ch)

        // Post the full-screen intent notification — system launches the activity
        val notif = android.app.Notification.Builder(
            applicationContext,
            BLOCK_ALERT_CHANNEL
        ).apply {
            setSmallIcon(android.R.drawable.ic_lock_lock)
            setContentTitle("App Blocked")
            setContentText("\u201C$appName\u201D is blocked during this session.")
            setFullScreenIntent(pi, true)
            setAutoCancel(true)
            setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
        }.build()
        nm?.notify(BLOCK_ALERT_NOTIF_ID, notif)

        // Auto-cancel the alert notification after 2 s (activity already showing)
        // and write overlay_x_ready so the X button fades in.
        handler.postDelayed({
            nm?.cancel(BLOCK_ALERT_NOTIF_ID)
            blockPrefs.edit()
                .putBoolean(BlockOverlayActivity.PREF_OVERLAY_X_READY, true)
                .putString("overlay_awaiting_pkg", "")
                .apply()
        }, 2_000L)
    }
}
