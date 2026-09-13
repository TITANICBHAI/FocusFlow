package com.tbtechs.focusflow.data.repository

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import com.tbtechs.focusflow.enforcement.ForegroundTaskService

/**
 * ForegroundServiceController
 *
 * Converted from ForegroundServiceModule.
 * This is intentionally a thin intent-based controller. ForegroundTaskService
 * remains the owner of notification, focus, break, fallback-enforcement, and
 * lifecycle behavior.
 */
class ForegroundServiceController(private val context: Context) {

    companion object {
        private const val PREF_PIN_HASH = "session_pin_hash"
    }

    private val appContext = context.applicationContext

    /**
     * Starts the service without task extras so ForegroundTaskService enters or
     * remains in idle monitoring mode.
     */
    suspend fun startIdleService() {
        startForegroundService(Intent(appContext, ForegroundTaskService::class.java))
    }

    /**
     * Starts or updates an active task session.
     */
    suspend fun startService(
        taskId: String,
        taskName: String,
        startTimeMs: Long,
        endTimeMs: Long,
        nextName: String?,
    ) {
        val intent = Intent(appContext, ForegroundTaskService::class.java).apply {
            putExtra(ForegroundTaskService.EXTRA_TASK_ID, taskId)
            putExtra(ForegroundTaskService.EXTRA_TASK_NAME, taskName)
            putExtra(ForegroundTaskService.EXTRA_START_MS, startTimeMs)
            putExtra(ForegroundTaskService.EXTRA_END_MS, endTimeMs)
            nextName?.let { putExtra(ForegroundTaskService.EXTRA_NEXT_NAME, it) }
        }
        startForegroundService(intent)
    }

    /** Compatibility overload for the bridge's JavaScript number timestamps. */
    suspend fun startService(
        taskId: String,
        taskName: String,
        startTimeMs: Double,
        endTimeMs: Double,
        nextName: String?,
    ) = startService(
        taskId,
        taskName,
        startTimeMs.toLong(),
        endTimeMs.toLong(),
        nextName,
    )

    /**
     * Switches the service to idle mode after validating the user-facing
     * session PIN, when one is configured.
     */
    suspend fun stopService(pinHash: String?) {
        requireValidSessionPin(
            pinHash,
            "A session PIN is set — supply the correct PIN hash to stop the service",
        )
        startServiceCommand(ForegroundTaskService.ACTION_SET_IDLE)
    }

    /**
     * Authorized system transition. This intentionally does not validate the
     * session PIN; task completion, skipping, and orphan-session reconciliation
     * use this path.
     */
    suspend fun stopServiceInternal() {
        startServiceCommand(ForegroundTaskService.ACTION_SET_IDLE)
    }

    /**
     * Sends updated task details to the service so it rebuilds its notification.
     */
    suspend fun updateNotification(
        taskId: String,
        taskName: String,
        endTimeMs: Long,
        nextName: String?,
    ) {
        val intent = Intent(appContext, ForegroundTaskService::class.java).apply {
            putExtra(ForegroundTaskService.EXTRA_TASK_ID, taskId)
            putExtra(ForegroundTaskService.EXTRA_TASK_NAME, taskName)
            putExtra(ForegroundTaskService.EXTRA_END_MS, endTimeMs)
            nextName?.let { putExtra(ForegroundTaskService.EXTRA_NEXT_NAME, it) }
        }
        startForegroundService(intent)
    }

    /** Compatibility overload for the bridge's JavaScript number timestamp. */
    suspend fun updateNotification(
        taskId: String,
        taskName: String,
        endTimeMs: Double,
        nextName: String?,
    ) = updateNotification(taskId, taskName, endTimeMs.toLong(), nextName)

    suspend fun setBreak(untilMs: Long) {
        val intent = Intent(appContext, ForegroundTaskService::class.java).apply {
            action = ForegroundTaskService.ACTION_SET_BREAK
            putExtra(ForegroundTaskService.EXTRA_BREAK_UNTIL_MS, untilMs)
        }
        appContext.startService(intent)
    }

    /** Compatibility overload for the bridge's JavaScript number timestamp. */
    suspend fun setBreak(untilMs: Double) = setBreak(untilMs.toLong())

    suspend fun clearBreak() {
        startServiceCommand(ForegroundTaskService.ACTION_CLEAR_BREAK)
    }

    /**
     * Opens Android's battery-optimization exemption screen when needed.
     *
     * The legacy bridge treated this as best-effort and resolved even when an
     * OEM rejected every settings intent, so this method intentionally swallows
     * launch failures after trying the same fallback sequence.
     */
    suspend fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        try {
            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (powerManager.isIgnoringBatteryOptimizations(appContext.packageName)) return
        } catch (_: Exception) {
            return
        }

        fun launch(intent: Intent): Boolean {
            return try {
                val activity = context as? Activity
                if (activity != null && !activity.isFinishing) {
                    activity.startActivity(intent)
                } else {
                    appContext.startActivity(
                        intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                    )
                }
                true
            } catch (_: Exception) {
                false
            }
        }

        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${appContext.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (launch(direct)) return

        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (launch(list)) return

        launch(
            Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    private fun startForegroundService(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
    }

    private fun startServiceCommand(action: String) {
        appContext.startService(
            Intent(appContext, ForegroundTaskService::class.java).apply {
                this.action = action
            },
        )
    }

    private fun requireValidSessionPin(pinHash: String?, message: String) {
        val prefs = appContext.getSharedPreferences(
            AppBlockerAccessibilityService.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        val storedHash = prefs.getString(PREF_PIN_HASH, null)
        if (storedHash.isNullOrBlank()) return
        if (pinHash.isNullOrBlank() ||
            !storedHash.equals(pinHash.lowercase(), ignoreCase = true)
        ) {
            throw SessionPinRequiredException(message)
        }
    }
}