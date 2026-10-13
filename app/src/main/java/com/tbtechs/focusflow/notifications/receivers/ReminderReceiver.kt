package com.tbtechs.focusflow.notifications.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.notifications.LiveTaskStatusNotificationPublisher
import com.tbtechs.focusflow.notifications.ReminderChainAlarmIdentity
import com.tbtechs.focusflow.notifications.ReminderDelivery
import com.tbtechs.focusflow.notifications.ReminderKind
import com.tbtechs.focusflow.notifications.ReminderNotificationPublisher
import com.tbtechs.focusflow.notifications.ReminderPlanner
import com.tbtechs.focusflow.ui.common.AppErrorEvents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != ReminderChainAlarmIdentity.ACTION_FIRE ||
            intent.dataString != ReminderChainAlarmIdentity.DATA_URI
        ) {
            Log.w(TAG, "Ignoring a reminder-chain broadcast with the wrong identity.")
            return
        }

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(RECEIVER_BUDGET_MS) {
                    AppModule.restoreGate.write("ReminderReceiver") {
                        val nowMs = System.currentTimeMillis()
                        val settings = AppModule.settingsRepository.readAppSettings()

                        val tasks = try {
                            withTimeoutOrNull(ROOM_READ_BUDGET_MS) {
                                AppModule.taskRepository.getAllTasks()
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            Log.e(TAG, "Could not read tasks for reminder delivery.", error)
                            null
                        }
                        if (tasks == null) {
                            AppModule.reminderChainScheduler.rearm(null, nowMs)
                            AppErrorEvents.report(
                                tag = "Task reminders",
                                message = "Task reminders could not be replanned because tasks were unavailable.",
                            )
                            return@write
                        }

                        val firePlan = ReminderPlanner.plan(
                            tasks = tasks,
                            nowMs = nowMs,
                            remindersEnabled = settings.taskRemindersEnabled,
                            preStartRemindersEnabled = settings.taskPreStartRemindersEnabled,
                            atStartReminderEnabled = settings.taskAtStartReminderEnabled,
                            midSessionRemindersEnabled = settings.taskMidSessionRemindersEnabled,
                            wrapUpReminderEnabled = settings.taskWrapUpReminderEnabled,
                            includeDueSlots = true,
                        )
                        val dueReminderSlots = ReminderDelivery.dueSlots(firePlan, nowMs)
                            .filter { it.kind != ReminderKind.LIVE_STATUS_START }
                        if (dueReminderSlots.isNotEmpty()) {
                            try {
                                AppModule.reminderChainLedger.deliverDue(
                                    slots = dueReminderSlots,
                                    nowMs = nowMs,
                                    post = { slot ->
                                        ReminderNotificationPublisher.post(appContext, slot)
                                    },
                                    onPostFailure = { slot, error ->
                                        Log.e(TAG, "Could not post reminder slot ${slot.id}.", error)
                                    },
                                )
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                Log.e(TAG, "Could not persist reminder delivery state.", error)
                                AppErrorEvents.report(
                                    tag = "Task reminders",
                                    message = "A reminder was delivered but its duplicate-prevention state could not be saved.",
                                )
                            }
                        }

                        // This is independent of the one-shot reminder setting:
                        // publish or refresh a live card only while a task's
                        // scheduled interval is currently in progress.
                        LiveTaskStatusNotificationPublisher.sync(appContext, tasks, nowMs)

                        val replanNowMs = System.currentTimeMillis()
                        val nextPlan = ReminderPlanner.plan(
                            tasks = tasks,
                            nowMs = replanNowMs,
                            remindersEnabled = settings.taskRemindersEnabled,
                            preStartRemindersEnabled = settings.taskPreStartRemindersEnabled,
                            atStartReminderEnabled = settings.taskAtStartReminderEnabled,
                            midSessionRemindersEnabled = settings.taskMidSessionRemindersEnabled,
                            wrapUpReminderEnabled = settings.taskWrapUpReminderEnabled,
                        )
                        AppModule.reminderChainScheduler.rearm(nextPlan, replanNowMs)
                    }
                }
            } catch (error: TimeoutCancellationException) {
                Log.w(TAG, "Reminder receiver exceeded its execution budget.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Reminder-chain handling failed.", error)
                AppErrorEvents.report(
                    tag = "Task reminders",
                    message = "Task reminders could not be reconciled.",
                    throwable = error,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ReminderReceiver"
        const val RECEIVER_BUDGET_MS = 8_000L
        const val ROOM_READ_BUDGET_MS = 3_000L
    }
}