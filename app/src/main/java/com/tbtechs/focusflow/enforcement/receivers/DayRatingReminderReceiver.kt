package com.tbtechs.focusflow.enforcement.receivers

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tbtechs.focusflow.MainActivity
import com.tbtechs.focusflow.R
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.enforcement.DayRatingNotificationScheduler
import com.tbtechs.focusflow.enforcement.LauncherActivity
import com.tbtechs.focusflow.notifications.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Posts the daily self-rating prompt and re-arms the next reminder. */
class DayRatingReminderReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.tbtechs.focusflow.alarm.DAY_RATING_REMIND"
        private const val NOTIFICATION_ID = 8812
        private const val TAG = "DayRatingReminder"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FocusFlow:DayRatingReminder")
            ?.also { it.setReferenceCounted(false); it.acquire(8_000L) }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!DayRatingNotificationScheduler.isEnabled(context)) {
                    DayRatingNotificationScheduler.cancel(context)
                    return@launch
                }

                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val alreadyRated = runCatching {
                    AppModule.dayRatingRepository.getForDate(today) != null
                }.onFailure {
                    Log.w(TAG, "Could not check whether today was already rated", it)
                }.getOrDefault(false)

                if (!alreadyRated && NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    val tapIntent = Intent(context, MainActivity::class.java).apply {
                        action = LauncherActivity.ACTION_OPEN_DAY_RATING
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        0,
                        tapIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    val notification = NotificationCompat.Builder(
                        context,
                        NotificationChannels.DAY_RATING,
                    )
                        .setSmallIcon(R.drawable.focusflow_icon)
                        .setContentTitle("How was today?")
                        .setContentText("Rate your day — one tap.")
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .build()
                    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Failed to deliver day-rating reminder", error)
            } finally {
                runCatching { DayRatingNotificationScheduler.scheduleNext(context) }
                    .onFailure { Log.e(TAG, "Failed to schedule next day-rating reminder", it) }
                if (wakeLock?.isHeld == true) runCatching { wakeLock.release() }
                pendingResult.finish()
            }
        }
    }
}