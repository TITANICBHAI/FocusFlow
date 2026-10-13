package com.tbtechs.focusflow.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.tbtechs.focusflow.enforcement.TemptationLogManager

/** The complete aversion settings returned by the legacy bridge. */
data class AversionsSettings(
    val dimmerEnabled: Boolean,
    val vibrateEnabled: Boolean,
    val soundEnabled: Boolean,
    val weeklyReportEnabled: Boolean,
)

/**
 * Partial update accepted by the legacy settings API. Null means the key was
 * absent and must remain unchanged.
 */
data class AversionsSettingsUpdate(
    val dimmerEnabled: Boolean? = null,
    val vibrateEnabled: Boolean? = null,
    val soundEnabled: Boolean? = null,
    val weeklyReportEnabled: Boolean? = null,
)

/**
 * AversionsController
 *
 * Converted from AversionsModule.
 * AversiveActionsManager reads these SharedPreferences flags when a blocked app
 * is detected; TemptationLogManager owns the weekly report alarm.
 */
class AversionsController(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "focusday_prefs"
        private const val KEY_DIMMER_ENABLED = "aversion_dimmer_enabled"
        private const val KEY_VIBRATE_ENABLED = "aversion_vibrate_enabled"
        private const val KEY_SOUND_ENABLED = "aversion_sound_enabled"
        private const val KEY_WEEKLY_REPORT = "aversion_weekly_report"
    }

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun getSettings(): AversionsSettings =
        AversionsSettings(
            dimmerEnabled = prefs.getBoolean(KEY_DIMMER_ENABLED, false),
            vibrateEnabled = prefs.getBoolean(KEY_VIBRATE_ENABLED, false),
            soundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, false),
            weeklyReportEnabled = prefs.getBoolean(KEY_WEEKLY_REPORT, false),
        )

    suspend fun setSettings(settings: AversionsSettingsUpdate) {
        val edit = prefs.edit()
        settings.dimmerEnabled?.let { edit.putBoolean(KEY_DIMMER_ENABLED, it) }
        settings.vibrateEnabled?.let { edit.putBoolean(KEY_VIBRATE_ENABLED, it) }
        settings.soundEnabled?.let { edit.putBoolean(KEY_SOUND_ENABLED, it) }

        if (settings.weeklyReportEnabled != null) {
            val enabled = settings.weeklyReportEnabled
            edit.putBoolean(KEY_WEEKLY_REPORT, enabled)
            edit.apply()
            TemptationLogManager.scheduleWeeklyReport(context, enabled)
            return
        }

        edit.apply()
    }
}