package com.tbtechs.focusflow.enforcement

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tbtechs.focusflow.enforcement.receivers.DayRatingReminderReceiver
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Schedules the daily day-rating reminder without requiring an exact alarm. */
object DayRatingNotificationScheduler {
    private const val TAG = "DayRatingScheduler"
    private const val BED_TIME_KEY = "bed_time"
    private const val USER_PROFILE_KEY = "user_profile"
    private const val REFLECTION_PROMPTS_ENABLED_KEY = "reflection_prompts_enabled"
    private const val REQUEST = 8813
    private val DEFAULT_BED_TIME = LocalTime.of(22, 0)

    fun scheduleNext(context: Context) {
        val prefs = preferences(context)
        if (!isEnabled(context)) {
            cancel(context)
            return
        }

        val reminderTime = resolveBedTime(prefs).minusMinutes(30)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        var targetMs = LocalDate.now().atTime(reminderTime).atZone(zone).toInstant().toEpochMilli()
        if (targetMs <= now) {
            targetMs = LocalDate.now().plusDays(1).atTime(reminderTime)
                .atZone(zone).toInstant().toEpochMilli()
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            prefs.edit().putBoolean("day_rating_alarm_set", false).apply()
            Log.e(TAG, "AlarmManager is unavailable; day-rating reminder was not scheduled")
            return
        }
        runCatching {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC,
                targetMs,
                AlarmManager.INTERVAL_DAY,
                buildPendingIntent(context),
            )
        }.onSuccess {
            prefs.edit().putBoolean("day_rating_alarm_set", true).apply()
            Log.d(TAG, "Scheduled for $targetMs")
        }.onFailure { error ->
            prefs.edit().putBoolean("day_rating_alarm_set", false).apply()
            Log.e(TAG, "Could not schedule day-rating reminder", error)
        }
    }

    fun ensureScheduled(context: Context) {
        // Reapply the alarm on startup and boot instead of trusting a persisted
        // flag; Android clears AlarmManager alarms during reboot.
        scheduleNext(context)
    }

    fun isEnabled(context: Context): Boolean =
        preferences(context).all[REFLECTION_PROMPTS_ENABLED_KEY] as? Boolean ?: true

    fun cancel(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
            ?.cancel(buildPendingIntent(context))
        preferences(context).edit().putBoolean("day_rating_alarm_set", false).apply()
    }

    private fun preferences(context: Context) = context.getSharedPreferences(
        AppBlockerAccessibilityService.PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    private fun resolveBedTime(prefs: android.content.SharedPreferences): LocalTime {
        val profileBedTime = (prefs.all[USER_PROFILE_KEY] as? String)?.let { json ->
            runCatching {
                JSONObject(json).optString("sleepTime")
                    .takeUnless { it.isBlank() || it == "null" }
            }.getOrNull()
        }
        val configuredBedTime = prefs.all[BED_TIME_KEY] as? String
        return listOfNotNull(profileBedTime, configuredBedTime)
            .firstNotNullOfOrNull { value -> runCatching { LocalTime.parse(value) }.getOrNull() }
            ?: DEFAULT_BED_TIME
    }

    private fun buildPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST,
            Intent(context, DayRatingReminderReceiver::class.java).apply {
                action = DayRatingReminderReceiver.ACTION
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}