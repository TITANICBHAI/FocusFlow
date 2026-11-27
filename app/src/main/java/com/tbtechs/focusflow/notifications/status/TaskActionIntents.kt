package com.tbtechs.focusflow.notifications.status

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.tbtechs.focusflow.MainActivity
import com.tbtechs.focusflow.enforcement.receivers.NotificationActionReceiver

/**
 * Notification action buttons open [MainActivity] directly. Since Android 12 an app
 * should start an activity from a broadcast receiver that a notification action fired
 * (the "notification trampoline" rule), and the platform can block that launch. An
 * activity PendingIntent sent by the system from a notification tap is allowed on every
 * supported version. [MainActivity] stores the action and replays it through the task
 * view model once the database is ready.
 */
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
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            this.action = receiverAction
            data = Uri.parse(
                "focusflow-internal://live-task/" +
                    "${Uri.encode(taskId)}/${Uri.encode(action.identityToken)}",
            )
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
            action.minutes?.let { putExtra(NotificationActionReceiver.EXTRA_MINUTES, it) }
        }
        return PendingIntent.getActivity(
            context,
            action.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
