package com.tbtechs.focusflow.enforcement.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tbtechs.focusflow.di.AppModule
import kotlinx.coroutines.CancellationException

class TaskAlarmReconcileWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!isUserUnlocked(applicationContext)) return Result.retry()
        val reason = inputData.getString(KEY_REASON).orEmpty().ifBlank { "work" }
        return try {
            AppModule.taskAlarmReconciler.reconcile(reason)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Task-end alarm reconciliation failed; WorkManager will retry.", error)
            Result.retry()
        }
    }

    private fun isUserUnlocked(context: Context): Boolean {
        return context.getSystemService(UserManager::class.java)?.isUserUnlocked == true
    }

    companion object {
        private const val TAG = "TaskAlarmReconcileWorker"
        private const val WORK_NAME = "task-end-alarm-reconciliation"
        private const val KEY_REASON = "reason"

        fun enqueue(context: Context, reason: String) {
            val request = OneTimeWorkRequestBuilder<TaskAlarmReconcileWorker>()
                .setInputData(workDataOf(KEY_REASON to reason.take(80)))
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}

/** Manifest receiver for exact-alarm grants and wall-clock changes. */
class TaskAlarmReconcileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!isUserUnlocked(context)) return
        val reason = when (intent.action) {
            android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
                "exact_alarm_permission_granted"
            Intent.ACTION_TIME_CHANGED -> "system_time_changed"
            else -> return
        }
        runCatching { TaskAlarmReconcileWorker.enqueue(context, reason) }
            .onFailure {
                Log.e("TaskAlarmReconcileReceiver", "Could not enqueue alarm reconciliation.", it)
            }
    }

    private fun isUserUnlocked(context: Context): Boolean {
        return context.getSystemService(UserManager::class.java)?.isUserUnlocked == true
    }
}