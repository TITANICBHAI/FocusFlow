package com.tbtechs.focusflow.enforcement.receivers

import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tbtechs.focusflow.di.AppModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
 *     3. Brings MainActivity to the foreground; its onNewIntent path replays
 *        the persisted action immediately when the app process is active.
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
        private const val TAG = "NotificationActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val action = intent.action ?: return
        if (action !in setOf(ACTION_COMPLETE, ACTION_EXTEND, ACTION_SKIP)) return
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 15)
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs = context.applicationContext.getSharedPreferences(
                    AppBlockerAccessibilityService.PREFS_NAME,
                    Context.MODE_PRIVATE,
                )
                val stored = withTimeoutOrNull(8_000L) {
                    AppModule.restoreGate.write("NotificationActionReceiver:$action") {
                        prefs.edit()
                            .putString(PREF_PENDING_ACTION, action)
                            .putString(PREF_PENDING_TASK_ID, taskId)
                            .putInt(PREF_PENDING_MINUTES, minutes)
                            .putLong(PREF_PENDING_TIME_MS, System.currentTimeMillis())
                            .commit()
                    }
                }
                if (stored != true) {
                    Log.w(TAG, "Dropped notification action while restore gate was closed or storage failed.")
                    return@launch
                }

                // The activity replays the durable action through TaskViewModel,
                // whose repository operation owns the same process-wide gate.
                val launchIntent = context.packageManager
                    .getLaunchIntentForPackage(context.packageName)
                    ?.apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                launchIntent?.let { context.startActivity(it) }
            } catch (error: Exception) {
                Log.e(TAG, "Could not queue notification action.", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

}
