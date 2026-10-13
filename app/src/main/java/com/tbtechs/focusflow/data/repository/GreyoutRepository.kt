package com.tbtechs.focusflow.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import com.tbtechs.focusflow.enforcement.TemptationLogManager

/**
 * GreyoutRepository
 *
 * Stores the scheduled greyout JSON consumed directly by
 * AppBlockerAccessibilityService and exposes the temptation/blocking log used
 * by the analytics engine.
 */
class GreyoutRepository(context: Context) {

    companion object {
        /**
         * Existing native enforcement key. AppBlockerAccessibilityService reads
         * this exact key from the legacy focusday_prefs namespace.
         */
        private const val KEY_GREYOUT_SCHEDULE = "greyout_schedule"
    }

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences
        get() = appContext.getSharedPreferences(
            AppBlockerAccessibilityService.PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    suspend fun getSchedule(): String =
        prefs.getString(KEY_GREYOUT_SCHEDULE, "[]") ?: "[]"

    suspend fun setSchedule(json: String) {
        prefs.edit().putString(KEY_GREYOUT_SCHEDULE, json).apply()
    }

    /**
     * Returns the raw JSON array consumed by the analytics layer.
     */
    suspend fun getTemptationLog(): String =
        TemptationLogManager.getLogJson(appContext)

    suspend fun clearTemptationLog() {
        TemptationLogManager.clearLog(appContext)
    }

    suspend fun getWeeklySummary(): String =
        TemptationLogManager.buildWeeklySummary(appContext)
}