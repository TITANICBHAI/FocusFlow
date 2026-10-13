package com.tbtechs.focusflow.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tbtechs.focusflow.MainActivity
import com.tbtechs.focusflow.R

object ReminderNotificationPublisher {
    private const val NOTIFICATION_ID = 1

    fun post(context: Context, slot: ReminderSlot) {
        val appContext = context.applicationContext
        val contentIntent = PendingIntent.getActivity(
            appContext,
            slot.id.hashCode(),
            Intent(appContext, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                data = Uri.parse("focusflow-internal://reminder/${Uri.encode(slot.id)}")
                putExtra(EXTRA_TASK_ID, slot.taskId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
        )
        val notification = NotificationCompat.Builder(
            appContext,
            NotificationChannels.TASK_REMINDERS,
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(slot.title)
            .setContentText(slot.text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(appContext).notify(slot.id, NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context, slotId: String) {
        NotificationManagerCompat.from(context.applicationContext)
            .cancel(slotId, NOTIFICATION_ID)
    }

    private fun immutableFlag(): Int =
        PendingIntent.FLAG_IMMUTABLE

    private const val EXTRA_TASK_ID = "taskId"
}