package com.tbtechs.focusflow.notifications.status

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.tbtechs.focusflow.MainActivity
import com.tbtechs.focusflow.R
import com.tbtechs.focusflow.ui.navigation.Routes

object StatusCardRenderer {
    private const val REQUEST_OPEN = 0

    fun render(
        context: Context,
        channelId: String,
        model: StatusCardModel,
    ): Notification {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            when (model.tapTarget) {
                StatusCardTapTarget.HOME -> {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                StatusCardTapTarget.PERMISSIONS -> {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse("focusflow://app/${Routes.PERMISSIONS}")
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            }
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(model.title)
            .setContentText(model.text)
            .setSubText(model.subText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(model.ongoing)
            .setOnlyAlertOnce(model.onlyAlertOnce)
            .setContentIntent(tapPendingIntent)
            .setPriority(
                when (model.priority) {
                    StatusCardPriority.MIN -> NotificationCompat.PRIORITY_MIN
                    StatusCardPriority.LOW -> NotificationCompat.PRIORITY_LOW
                },
            )
            .setWhen(model.chronometerBaseMs)
            .setUsesChronometer(model.usesChronometer)
            .setChronometerCountDown(model.chronometerCountDown)
            .setShowWhen(model.showWhen)

        model.progressPercent?.let { builder.setProgress(100, it, false) }
        model.actions.forEach { action ->
            val taskId = requireNotNull(model.taskId) {
                "Status-card actions require a task ID"
            }
            builder.addAction(
                0,
                action.label,
                TaskActionIntents.pendingIntent(context, taskId, action),
            )
        }
        return builder.build()
    }
}
