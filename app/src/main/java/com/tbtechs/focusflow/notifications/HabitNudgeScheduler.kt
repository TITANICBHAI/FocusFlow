package com.tbtechs.focusflow.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import com.tbtechs.focusflow.notifications.receivers.HabitNudgeReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schedules the opt-in "habit heads-up" notifications. Off until the person turns it on.
 *
 * It re-plans from the current findings whenever something relevant happens (app start, reboot,
 * the daily detection run, the toggle, or a nudge being shown), so a finding the person dismisses
 * stops producing nudges at the next re-plan. Alarms are inexact on purpose: no special permission,
 * and a delay of a few minutes does not matter for a heads-up.
 */
object HabitNudgeScheduler {
    const val ENABLED_KEY = "habit_nudges_enabled"
    private const val TAG = "HabitNudgeScheduler"
    private const val REQUEST_BASE = 9100
    private const val SLOTS = 2
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun isEnabled(context: Context): Boolean =
        preferences(context).getBoolean(ENABLED_KEY, false)

    fun rescheduleAsync(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching { reschedule(app) }
                .onFailure { Log.w(TAG, "Could not re-plan habit nudges", it) }
        }
    }

    suspend fun reschedule(context: Context) {
        val app = context.applicationContext
        cancelAll(app)
        if (!isEnabled(app)) return

        val findings = runCatching { AppModule.findingRepository.getActiveFindings() }
            .getOrDefault(emptyList())
        val nudges = HabitNudgePlanner.plan(findings, bedTimeMinutes(preferences(app)))
        val alarms = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        nudges.take(SLOTS).forEachIndexed { slot, nudge ->
            runCatching {
                alarms.setAndAllowWhileIdle(
                    AlarmManager.RTC,
                    nextOccurrenceMs(nudge.minuteOfDay),
                    pendingIntent(app, slot, nudge),
                )
            }.onFailure { Log.w(TAG, "Could not schedule nudge $slot", it) }
        }
    }

    private fun cancelAll(context: Context) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        repeat(SLOTS) { slot ->
            runCatching { alarms.cancel(pendingIntent(context, slot, null)) }
        }
    }

    private fun nextOccurrenceMs(minuteOfDay: Int): Long {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        var target = LocalDate.now().atTime(time).atZone(zone).toInstant().toEpochMilli()
        if (target <= now) {
            target = LocalDate.now().plusDays(1).atTime(time).atZone(zone).toInstant().toEpochMilli()
        }
        return target
    }

    private fun pendingIntent(context: Context, slot: Int, nudge: HabitNudge?): PendingIntent {
        val intent = Intent(context, HabitNudgeReceiver::class.java).apply {
            action = HabitNudgeReceiver.ACTION
            if (nudge != null) {
                putExtra(HabitNudgeReceiver.EXTRA_SLOT, slot)
                putExtra(HabitNudgeReceiver.EXTRA_TITLE, nudge.title)
                putExtra(HabitNudgeReceiver.EXTRA_TEXT, nudge.text)
            }
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + slot,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun bedTimeMinutes(prefs: SharedPreferences): Int? {
        val profileBedTime = (prefs.all["user_profile"] as? String)?.let { json ->
            runCatching {
                JSONObject(json).optString("sleepTime").takeUnless { it.isBlank() || it == "null" }
            }.getOrNull()
        }
        val configured = prefs.all["bed_time"] as? String
        return listOfNotNull(profileBedTime, configured)
            .firstNotNullOfOrNull { value -> runCatching { LocalTime.parse(value) }.getOrNull() }
            ?.let { it.hour * 60 + it.minute }
    }

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(
            AppBlockerAccessibilityService.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
}
