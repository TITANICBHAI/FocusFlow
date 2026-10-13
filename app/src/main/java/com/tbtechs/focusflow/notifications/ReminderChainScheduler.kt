package com.tbtechs.focusflow.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.tbtechs.focusflow.MainActivity
import com.tbtechs.focusflow.notifications.receivers.ReminderReceiver

interface ReminderChainAlarmDriver {
    fun schedule(triggerAtMs: Long)
    fun cancel()
}

/**
 * Keeps exactly one future reminder alarm armed. The same driver identity is
 * updated for each next slot, so replanning never creates per-slot alarms.
 */
class ReminderChainScheduler(
    private val alarmDriver: ReminderChainAlarmDriver,
) {
    fun rearm(slots: List<ReminderSlot>?, nowMs: Long): ReminderSlot? {
        val next = slots.orEmpty()
            .asSequence()
            .filter { it.triggerMs >= nowMs + ReminderPlanner.MIN_SCHEDULE_LEAD_MS }
            .minWithOrNull(compareBy<ReminderSlot> { it.triggerMs }.thenBy { it.id })

        if (next == null) {
            alarmDriver.cancel()
        } else {
            alarmDriver.schedule(next.triggerMs)
        }
        return next
    }
}

object ReminderChainAlarmIdentity {
    const val ACTION_FIRE = "com.tbtechs.focusflow.alarm.FIRE_REMINDER_CHAIN"
    const val DATA_URI = "focusflow-internal://reminder-chain"
    private const val SHOW_DATA_URI = "focusflow-internal://reminder-chain/show"
    private const val REQUEST_CODE = 0
    private const val SHOW_REQUEST_CODE = 1

    fun alarmPendingIntent(context: Context, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_FIRE
                data = Uri.parse(DATA_URI)
            },
            flags,
        )

    fun showPendingIntent(context: Context, flags: Int): PendingIntent? =
        PendingIntent.getActivity(
            context,
            SHOW_REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                data = Uri.parse(SHOW_DATA_URI)
            },
            flags,
        )
}

/**
 * Android alarm driver for the fixed chain identity. Exact-access devices use
 * the exact tiers first; reminders may fall back to inexact allow-while-idle.
 */
class AndroidReminderChainAlarmDriver(context: Context) : ReminderChainAlarmDriver {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        ?: throw IllegalStateException("AlarmManager is unavailable.")

    override fun schedule(triggerAtMs: Long) {
        if (triggerAtMs <= System.currentTimeMillis()) {
            cancel()
            return
        }

        val alarmPendingIntent = requireNotNull(
            ReminderChainAlarmIdentity.alarmPendingIntent(
                appContext,
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
            ),
        ) { "The reminder-chain PendingIntent could not be created." }
        var exactFailure: Exception? = null
        if (canScheduleExactAlarms()) {
            try {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(
                        triggerAtMs,
                        requireNotNull(
                            ReminderChainAlarmIdentity.showPendingIntent(
                                appContext,
                                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
                            ),
                        ) { "The reminder-chain display PendingIntent could not be created." },
                    ),
                    alarmPendingIntent,
                )
                return
            } catch (error: Exception) {
                exactFailure = error
                clearShowPendingIntent()
            }

            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMs,
                    alarmPendingIntent,
                )
                return
            } catch (error: Exception) {
                if (exactFailure == null) exactFailure = error
                clearShowPendingIntent()
            }
        } else {
            clearShowPendingIntent()
        }

        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMs,
                alarmPendingIntent,
            )
            exactFailure?.let {
                Log.w(TAG, "Exact reminder scheduling failed; using inexact allow-while-idle.", it)
            }
        } catch (error: Exception) {
            throw IllegalStateException("Could not schedule the reminder-chain alarm.", error)
        }
    }

    override fun cancel() {
        val pendingIntent = ReminderChainAlarmIdentity.alarmPendingIntent(
            appContext,
            PendingIntent.FLAG_NO_CREATE or immutableFlag(),
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        clearShowPendingIntent()
    }

    private fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return try {
            alarmManager.canScheduleExactAlarms()
        } catch (error: Exception) {
            Log.w(TAG, "Could not read exact-alarm access for reminder scheduling.", error)
            false
        }
    }

    private fun clearShowPendingIntent() {
        ReminderChainAlarmIdentity.showPendingIntent(
            appContext,
            PendingIntent.FLAG_NO_CREATE or immutableFlag(),
        )?.cancel()
    }

    private fun immutableFlag(): Int =
        PendingIntent.FLAG_IMMUTABLE

    private companion object {
        const val TAG = "ReminderChainScheduler"
    }
}