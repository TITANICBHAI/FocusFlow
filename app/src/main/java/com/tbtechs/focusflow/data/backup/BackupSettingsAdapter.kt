package com.tbtechs.focusflow.data.backup

import com.tbtechs.focusflow.data.repository.SettingsRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Maps current portable backup settings that are not part of the retired
 * TypeScript-to-native one-time migration contract.
 *
 * Older fields continue through [LegacySettingsAdapter]; this adapter only
 * handles keys emitted by [PortableSettingsPolicy] and never changes the
 * legacy migration allow-list.
 */
internal object BackupSettingsAdapter {
    fun hasRestorableSettings(settings: JsonObject): Boolean {
        if (additionalPreferenceWrites(settings).isNotEmpty()) return true
        return try {
            LegacySettingsAdapter.toSharedPreferencesValues(settings).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    fun additionalPreferenceWrites(settings: JsonObject): Map<String, BackupPreferenceValue> {
        val portable = JsonObject(
            settings.filterKeys(PortableSettingsPolicy::isPortableBackupKey),
        )
        val writes = linkedMapOf<String, BackupPreferenceValue>()

        fun boolean(key: String, preferenceKey: String, missingValue: Boolean? = null) {
            val value = (portable[key] as? JsonPrimitive)?.booleanOrNull
                ?: missingValue
                ?: return
            writes[preferenceKey] = BackupPreferenceValue.BooleanValue(value)
        }

        fun int(key: String, preferenceKey: String) {
            val value = (portable[key] as? JsonPrimitive)?.intOrNull ?: return
            writes[preferenceKey] = BackupPreferenceValue.IntValue(value)
        }

        fun string(key: String, preferenceKey: String, maxChars: Int = BackupJsonLimits.MAX_STRING_CHARS) {
            val value = stringValue(portable[key]) ?: return
            if (value.length > maxChars) return
            writes[preferenceKey] = BackupPreferenceValue.StringValue(value)
        }

        fun nullableString(key: String, preferenceKey: String) {
            if (key !in portable) return
            val value = portable[key]
            if (value == JsonNull) {
                writes[preferenceKey] = BackupPreferenceValue.Remove
            } else {
                string(key, preferenceKey)
            }
        }

        fun float(key: String, preferenceKey: String) {
            val value = (portable[key] as? JsonPrimitive)?.floatOrNull ?: return
            if (!value.isFinite() || value !in TEXT_SCALE_RANGE) return
            writes[preferenceKey] = BackupPreferenceValue.FloatValue(value)
        }

        fun nullableFloat(key: String, preferenceKey: String) {
            if (key !in portable) return
            val value = portable[key]
            if (value == JsonNull) {
                writes[preferenceKey] = BackupPreferenceValue.Remove
                return
            }
            float(key, preferenceKey)
        }

        fun stringArray(key: String, preferenceKey: String) {
            val values = portable[key] as? JsonArray ?: return
            if (values.size > BackupJsonLimits.MAX_TASKS) return
            val normalized = values.map { element ->
                val value = stringValue(element) ?: return
                if (value.length > BackupJsonLimits.MAX_ID_CHARS) return
                JsonPrimitive(value)
            }
            writes[preferenceKey] =
                BackupPreferenceValue.StringValue(JsonArray(normalized).toString())
        }

        fun floatObject(key: String, preferenceKey: String) {
            val values = portable[key] as? JsonObject ?: return
            if (values.size > MAX_SCREEN_TEXT_SCALES) return
            val normalized = linkedMapOf<String, JsonElement>()
            values.forEach { (name, element) ->
                val value = (element as? JsonPrimitive)?.floatOrNull ?: return
                if (name.isBlank() || name.length > MAX_SCREEN_KEY_CHARS ||
                    !value.isFinite() || value !in TEXT_SCALE_RANGE
                ) {
                    return
                }
                normalized[name] = JsonPrimitive(value)
            }
            writes[preferenceKey] = BackupPreferenceValue.StringValue(JsonObject(normalized).toString())
        }

        fun stringObject(key: String, preferenceKey: String) {
            val values = portable[key] as? JsonObject ?: return
            if (values.size > BackupJsonLimits.MAX_TASKS) return
            val normalized = linkedMapOf<String, JsonElement>()
            values.forEach { (name, element) ->
                val value = stringValue(element) ?: return
                if (name.isBlank() || name.length > BackupJsonLimits.MAX_ID_CHARS ||
                    value.length > MAX_RESULT_CHARS
                ) {
                    return
                }
                normalized[name] = JsonPrimitive(value)
            }
            writes[preferenceKey] = BackupPreferenceValue.StringValue(JsonObject(normalized).toString())
        }

        nullableString("launcherWallpaperUri", "launcher_wallpaper_uri")
        float("generalTextScale", "general_text_scale")
        floatObject("screenTextScales", "screen_text_scales")
        nullableFloat("homeTextScale", "home_text_scale")
        nullableFloat("focusTextScale", "focus_text_scale")
        nullableFloat("statsTextScale", "stats_text_scale")
        nullableFloat("settingsTextScale", "settings_text_scale")
        nullableFloat("defenseTextScale", "defense_text_scale")

        boolean("morningDigestEnabled", "morning_digest_enabled")
        boolean("achievementNotificationsEnabled", "achievement_notifications_enabled")
        boolean("patternInsightNotificationsEnabled", "pattern_insight_notifications_enabled")
        boolean("rescheduleNotificationsEnabled", "reschedule_notifications_enabled")
        boolean("blockSuggestionEnabled", "block_suggestion_enabled")
        boolean("weekAheadEnabled", "week_ahead_enabled")
        boolean("temptationSpikeEnabled", "temptation_spike_enabled")
        int("temptationSpikeThreshold", "temptation_spike_threshold")
        boolean("productiveWindowNudgeEnabled", "productive_window_nudge_enabled")
        stringObject("lastSessionResultByTaskId", "last_session_result_by_task_id")
        stringArray("shownPatternInsightIds", "shown_pattern_insight_ids")
        if ("lastShownDebriefSessionId" in portable) {
            val value = portable["lastShownDebriefSessionId"]
            if (value == JsonNull) {
                writes["last_shown_debrief_session_id"] = BackupPreferenceValue.Remove
            } else {
                int("lastShownDebriefSessionId", "last_shown_debrief_session_id")
            }
        }

        boolean("taskRemindersEnabled", "task_reminders_enabled")
        val taskAlertSettingNames = setOf(
            "taskPreStartRemindersEnabled",
            "taskAtStartReminderEnabled",
            "taskMidSessionRemindersEnabled",
            "taskWrapUpReminderEnabled",
            "taskEndAlarmRings",
        )
        val hasLegacyPortableSettings = portable.keys.any { it !in taskAlertSettingNames }
        val legacyTaskAlertDefault = if (hasLegacyPortableSettings) true else null
        boolean(
            "taskPreStartRemindersEnabled",
            SettingsRepository.KEY_TASK_PRE_START_REMINDERS_ENABLED,
            missingValue = legacyTaskAlertDefault,
        )
        boolean(
            "taskAtStartReminderEnabled",
            SettingsRepository.KEY_TASK_AT_START_REMINDER_ENABLED,
            missingValue = legacyTaskAlertDefault,
        )
        boolean(
            "taskMidSessionRemindersEnabled",
            SettingsRepository.KEY_TASK_MID_SESSION_REMINDERS_ENABLED,
            missingValue = legacyTaskAlertDefault,
        )
        boolean(
            "taskWrapUpReminderEnabled",
            SettingsRepository.KEY_TASK_WRAP_UP_REMINDER_ENABLED,
            missingValue = legacyTaskAlertDefault,
        )
        boolean(
            "taskEndAlarmRings",
            SettingsRepository.KEY_TASK_END_ALARM_RINGS,
            missingValue = legacyTaskAlertDefault,
        )
        boolean("reflectionPromptsEnabled", "reflection_prompts_enabled")
        boolean("autoFocusEnabled", "auto_focus_enabled")
        boolean("focusDefenseHintDismissed", "focus_defense_hint_dismissed")
        boolean("localAnalyticsNoticeDismissed", "local_analytics_notice_dismissed")
        boolean("standaloneBlockHintDismissed", "standalone_block_hint_dismissed")
        boolean("alwaysOnInfoDismissed", "always_on_info_dismissed")
        boolean("protectionStatusBannerDismissed", "protection_status_banner_dismissed")

        val bedTime = stringValue(portable["bedTime"])
        if (bedTime != null && bedTime.matches(TIME_PATTERN)) {
            writes["bed_time"] = BackupPreferenceValue.StringValue(bedTime)
        } else {
            writes.remove("bed_time")
        }

        return writes
    }

    private fun stringValue(value: JsonElement?): String? =
        (value as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull

    private val TEXT_SCALE_RANGE = 0.8f..1.5f
    private const val MAX_SCREEN_TEXT_SCALES = 500
    private const val MAX_SCREEN_KEY_CHARS = 160
    private const val MAX_RESULT_CHARS = 100
    private val TIME_PATTERN = Regex("^(?:[01][0-9]|2[0-3]):[0-5][0-9]$")
}

internal sealed interface BackupPreferenceValue {
    data class StringValue(val value: String) : BackupPreferenceValue
    data class BooleanValue(val value: Boolean) : BackupPreferenceValue
    data class IntValue(val value: Int) : BackupPreferenceValue
    data class FloatValue(val value: Float) : BackupPreferenceValue
    data object Remove : BackupPreferenceValue
}
