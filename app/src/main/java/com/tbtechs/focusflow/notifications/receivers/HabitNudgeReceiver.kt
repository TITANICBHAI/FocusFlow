package com.tbtechs.focusflow.notifications.receivers

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tbtechs.focusflow.MainActivity
import com.tbtechs.focusflow.R
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import com.tbtechs.focusflow.notifications.HabitNudgeScheduler
import com.tbtechs.focusflow.notifications.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Shows one habit heads-up, then re-plans so tomorrow's nudge reflects the latest findings. */
class HabitNudgeReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.tbtechs.focusflow.alarm.HABIT_NUDGE"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        private const val NOTIFICATION_BASE_ID = 8820
        private const val TAP_REQUEST = 8830
        private const val TAG = "HabitNudgeReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val app = context.applicationContext
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val slot = intent.getIntExtra(EXTRA_SLOT, 0)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (shouldShow(app, title, text)) show(app, slot, title, text)
            } catch (error: Exception) {
                Log.e(TAG, "Could not show habit nudge", error)
            } finally {
                runCatching { HabitNudgeScheduler.reschedule(app) }
                pending.finish()
            }
        }
    }

    private fun shouldShow(context: Context, title: String, text: String): Boolean {
        if (title.isBlank() || text.isBlank()) return false
        if (!HabitNudgeScheduler.isEnabled(context)) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        // Do not interrupt a focus session that is already protecting the person.
        val focusActive = context.getSharedPreferences(
            AppBlockerAccessibilityService.PREFS_NAME,
            Context.MODE_PRIVATE,
        ).getBoolean("focus_active", false)
        return !focusActive
    }

    private fun show(context: Context, slot: Int, title: String, text: String) {
        val tap = Intent(Intent.ACTION_VIEW, Uri.parse("focusflow://app/stats")).apply {
            setClass(context, MainActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            TAP_REQUEST,
            tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.HABIT_NUDGES)
            .setSmallIcon(R.drawable.focusflow_icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_BASE_ID + slot, notification)
    }
}
