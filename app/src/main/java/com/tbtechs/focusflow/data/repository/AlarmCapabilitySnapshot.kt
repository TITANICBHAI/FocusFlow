package com.tbtechs.focusflow.data.repository

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.tbtechs.focusflow.enforcement.ForegroundTaskService

/** Device capabilities that affect task-end alert delivery, without task/user content. */
data class AlarmCapabilitySnapshot(
    val postNotificationsGranted: Boolean,
    val channelImportance: Int?,
    val channelIsBlocked: Boolean?,
    val canUseFullScreenIntent: Boolean?,
    val canDrawOverlays: Boolean?,
    val canScheduleExactAlarms: Boolean,
    val batteryOptimizationExempt: Boolean?,
    val targetSdk: Int,
    val deviceInteractive: Boolean?,
    val keyguardLocked: Boolean?,
    val alarmTierUsed: String,
) {
    fun toDiagnosticString(): String = listOf(
        "postNotificationsGranted=$postNotificationsGranted",
        "channelImportance=${channelImportance ?: "unknown"}",
        "channelIsBlocked=${channelIsBlocked ?: "unknown"}",
        "canUseFullScreenIntent=${canUseFullScreenIntent ?: "not_applicable_or_unknown"}",
        "canDrawOverlays=${canDrawOverlays ?: "unknown"}",
        "canScheduleExactAlarms=$canScheduleExactAlarms",
        "batteryOptimizationExempt=${batteryOptimizationExempt ?: "unknown"}",
        "targetSdk=$targetSdk",
        "deviceInteractive=${deviceInteractive ?: "unknown"}",
        "keyguardLocked=${keyguardLocked ?: "unknown"}",
        "alarmTierUsed=$alarmTierUsed",
    ).joinToString(separator = " ")

    companion object {
        fun record(context: Context, phase: String, alarmTierUsed: String) {
            ForegroundTaskService.ensureTaskAlarmChannel(context)
            val snapshot = capture(context, alarmTierUsed)
            val summary = snapshot.toDiagnosticString()
            val capturedAtEpochMs = System.currentTimeMillis()
            runCatching {
                val registry = TaskAlarmRegistry(context)
                val changed = registry.recordCapabilitySnapshot(
                    phase = phase,
                    summary = summary,
                    capturedAtEpochMs = capturedAtEpochMs,
                )
                if (changed) {
                    StartupLogger.info(
                        "AlarmCapabilitySnapshot",
                        "phase=$phase capturedAtEpochMs=$capturedAtEpochMs $summary",
                    )
                }
            }.onFailure {
                StartupLogger.warn(
                    "AlarmCapabilitySnapshot",
                    "Could not persist the task-end alarm capability snapshot.",
                )
            }
        }

        fun capture(context: Context, alarmTierUsed: String): AlarmCapabilitySnapshot {
            val app = context.applicationContext
            val notificationManager =
                app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val powerManager = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val keyguardManager = app.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val channel = runCatching {
                notificationManager?.getNotificationChannel(
                    ForegroundTaskService.TASK_ALARM_CHANNEL,
                )
            }.getOrNull()
            val exactAccess = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                true
            } else {
                runCatching { alarmManager?.canScheduleExactAlarms() == true }.getOrDefault(false)
            }

            return AlarmCapabilitySnapshot(
                postNotificationsGranted = runCatching {
                    NotificationManagerCompat.from(app).areNotificationsEnabled()
                }.getOrDefault(false),
                channelImportance = channel?.importance,
                channelIsBlocked = channel?.let {
                    it.importance == NotificationManager.IMPORTANCE_NONE
                },
                canUseFullScreenIntent = if (Build.VERSION.SDK_INT >= 34) {
                    runCatching { notificationManager?.canUseFullScreenIntent() == true }
                        .getOrDefault(false)
                } else {
                    null
                },
                canDrawOverlays = runCatching { Settings.canDrawOverlays(app) }.getOrNull(),
                canScheduleExactAlarms = exactAccess,
                batteryOptimizationExempt = runCatching {
                    powerManager?.isIgnoringBatteryOptimizations(app.packageName)
                }.getOrNull(),
                targetSdk = app.applicationInfo.targetSdkVersion,
                deviceInteractive = runCatching { powerManager?.isInteractive }.getOrNull(),
                keyguardLocked = runCatching { keyguardManager?.isKeyguardLocked }.getOrNull(),
                alarmTierUsed = alarmTierUsed,
            )
        }
    }
}

/** Timestamped, content-free events for tracing the task-alarm presentation path. */
object AlarmRuntimeDiagnostics {
    fun record(event: String, details: String = "") {
        val suffix = details.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()
        StartupLogger.info(
            "TaskAlarmRuntime",
            "event=$event wallClockEpochMs=${System.currentTimeMillis()} " +
                "elapsedRealtimeMs=${android.os.SystemClock.elapsedRealtime()}$suffix",
        )
    }
}
