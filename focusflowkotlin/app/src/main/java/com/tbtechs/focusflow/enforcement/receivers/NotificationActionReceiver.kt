package com.tbtechs.focusflow.enforcement.receivers

import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import com.tbtechs.focusflow.enforcement.EnforcementEventContract

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * NotificationActionReceiver
 *
 * Handles taps on action buttons in the foreground task notification:
 *   ✓ Done  — completes the current task
 *   +15m    — extends the task by 15 minutes
 *   +30m    — extends the task by 30 minutes
 *   Skip    — skips the current task
 *
 * Flow:
 *   User taps action → PendingIntent fires this receiver → receiver:
 *     1. Writes a "pending_notif_action" entry to SharedPrefs as a fallback
 *        in case the app process is not yet alive.
 *     2. Launches MainActivity so the UI process starts (if not already alive).
 *     3. Sends an application-local broadcast for immediate handling when the
 *        app process is already active.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_COMPLETE  = "com.tbtechs.focusflow.notif.COMPLETE"
        const val ACTION_EXTEND    = "com.tbtechs.focusflow.notif.EXTEND"
        const val ACTION_SKIP      = "com.tbtechs.focusflow.notif.SKIP"

        const val EXTRA_TASK_ID    = "taskId"
        const val EXTRA_MINUTES    = "minutes"

        const val PREF_PENDING_ACTION    = "pending_notif_action"
        const val PREF_PENDING_TASK_ID   = "pending_notif_task_id"
        const val PREF_PENDING_MINUTES   = "pending_notif_minutes"
        const val PREF_PENDING_TIME_MS   = "pending_notif_time_ms"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val action = intent.action ?: return
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 15)

        // 1. Persist the action so the app can replay it after process startup.
        val prefs = context.getSharedPreferences(
            AppBlockerAccessibilityService.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        prefs.edit()
            .putString(PREF_PENDING_ACTION,  action)
            .putString(PREF_PENDING_TASK_ID, taskId)
            .putInt(PREF_PENDING_MINUTES,    minutes)
            .putLong(PREF_PENDING_TIME_MS,   System.currentTimeMillis())
            .apply()

        // 2. Launch MainActivity to wake up the React instance (no-op if already foreground).
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP }
        launchIntent?.let { context.startActivity(it) }

        // 3. Send broadcast for immediate handling if React is already alive.
        val bridgeIntent = Intent(EnforcementEventContract.ACTION_NOTIF_ACTION).apply {
            `package` = context.packageName
            putExtra(EnforcementEventContract.EXTRA_NOTIF_ACTION_TYPE, action)
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_MINUTES, minutes)
        }
        context.sendBroadcast(bridgeIntent)
    }
}
