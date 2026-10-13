package com.tbtechs.focusflow.notifications.status

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.tbtechs.focusflow.enforcement.receivers.NotificationActionReceiver

object TaskActionIntents {
    fun pendingIntent(
        context: Context,
        taskId: String,
        action: StatusCardAction,
    ): PendingIntent {
        val receiverAction = when (action) {
            StatusCardAction.DONE -> NotificationActionReceiver.ACTION_COMPLETE
            StatusCardAction.EXTEND_15,
            StatusCardAction.EXTEND_30 -> NotificationActionReceiver.ACTION_EXTEND
            StatusCardAction.SKIP -> NotificationActionReceiver.ACTION_SKIP
        }
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = receiverAction
            data = Uri.parse(
                "focusflow-internal://live-task/" +
                    "${Uri.encode(taskId)}/${Uri.encode(action.identityToken)}",
            )
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
            action.minutes?.let { putExtra(NotificationActionReceiver.EXTRA_MINUTES, it) }
        }
        return PendingIntent.getBroadcast(
            context,
            action.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
