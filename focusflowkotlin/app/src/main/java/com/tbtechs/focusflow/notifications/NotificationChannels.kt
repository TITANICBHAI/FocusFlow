package com.tbtechs.focusflow.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build

/**
 * Channels formerly created by notificationService.ts.
 *
 * The persistent foreground-service and task-alarm channels remain owned by
 * their existing native components. These are the app-owned channels used by
 * user-scheduled, report, achievement, insight, and resistance notifications.
 */
object NotificationChannels {
    const val TASK_REMINDERS = "task-reminders"
    const val MORNING_DIGEST = "morning-digest"
    const val WEEKLY_REPORT = "weekly-report"
    const val ACHIEVEMENTS = "achievements"
    const val INSIGHTS = "insights"
    const val RESISTANCE = "resistance"

    const val TASK_REMINDERS_NAME = "Task Reminders"
    const val MORNING_DIGEST_NAME = "Morning Digest"
    const val WEEKLY_REPORT_NAME = "Weekly Report"
    const val ACHIEVEMENTS_NAME = "Achievements"
    const val INSIGHTS_NAME = "Insights"
    const val RESISTANCE_NAME = "Resistance"

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channels = listOf(
            NotificationChannel(
                TASK_REMINDERS,
                TASK_REMINDERS_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Task start, check-in, and end reminders."
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 250L, 250L, 250L)
                enableLights(true)
                lightColor = Color.parseColor("#6366f1")
                setSound(
                    android.media.RingtoneManager.getDefaultUri(
                        android.media.RingtoneManager.TYPE_NOTIFICATION,
                    ),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            },
            NotificationChannel(
                MORNING_DIGEST,
                MORNING_DIGEST_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Daily focus-performance summary."
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 200L)
                enableLights(true)
                lightColor = Color.parseColor("#f59e0b")
                setSound(
                    android.media.RingtoneManager.getDefaultUri(
                        android.media.RingtoneManager.TYPE_NOTIFICATION,
                    ),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            },
            NotificationChannel(
                WEEKLY_REPORT,
                WEEKLY_REPORT_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Weekly focus-performance report."
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 200L)
                enableLights(true)
                lightColor = Color.parseColor("#6366f1")
                setSound(
                    android.media.RingtoneManager.getDefaultUri(
                        android.media.RingtoneManager.TYPE_NOTIFICATION,
                    ),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            },
            NotificationChannel(
                ACHIEVEMENTS,
                ACHIEVEMENTS_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Achievement unlocks and milestone celebrations."
                setShowBadge(true)
                enableVibration(true)
            },
            NotificationChannel(
                INSIGHTS,
                INSIGHTS_NAME,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "One-time focus pattern discoveries."
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
            NotificationChannel(
                RESISTANCE,
                RESISTANCE_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Opt-in blocked-app resistance alerts."
                setShowBadge(true)
                enableVibration(true)
            },
        )

        val manager = context.applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE,
        ) as? NotificationManager ?: return
        manager.createNotificationChannels(channels)
    }
}