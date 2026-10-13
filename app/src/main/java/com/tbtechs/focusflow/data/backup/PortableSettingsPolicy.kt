package com.tbtechs.focusflow.data.backup

import com.tbtechs.focusflow.data.model.AllowedAppPreset
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.BlockPreset
import com.tbtechs.focusflow.data.model.RecurringBlockSchedule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Converts native settings into the JSON key format used by FocusFlow backups.
 *
 * The legacy migration policy is applied as an additional filter, but the
 * current model's device-local fields are filtered independently so this
 * export policy does not change the one-time database migration contract.
 */
object PortableSettingsPolicy {
    private val json = Json {}
    private val portableBackupKeys: Set<String> by lazy {
        toPortableJson(AppSettings()).keys
    }

    private val deviceLocalFields = setOf(
        "standaloneBlockActive",
        "standaloneBlockPackages",
        "standaloneBlockVpnPackages",
        "standaloneBlockUntilMs",
        "alwaysBlockEnabled",
        "networkBlockEnabled",
        "pomodoroEnabled",
        "aversionDimmerEnabled",
        "aversionVibrateEnabled",
        "aversionSoundEnabled",
        "systemGuardEnabled",
        "blockInstallActionsEnabled",
        "blockYoutubeShortsEnabled",
        "blockInstagramReelsEnabled",
        "vpnSelfHealEnabled",
        "pinProtectionEnabled",
        "autoCopyToAlwaysOn",
    )

    fun toPortableJson(settings: AppSettings): JsonObject {
        val fields = linkedMapOf<String, JsonElement>()

        fun add(
            kotlinField: String,
            backupKey: String = kotlinField,
            value: JsonElement,
        ) {
            if (kotlinField in deviceLocalFields) return
            if (!LegacySettingsPolicy.mayMigrateKey(backupKey)) return
            fields[backupKey] = value
        }

        add("alwaysBlockPackages", LegacySettingsAdapter.ALWAYS_ON_PACKAGES, stringArray(settings.alwaysBlockPackages))
        add("blockedWords", LegacySettingsAdapter.BLOCKED_WORDS, stringArray(settings.blockedWords))
        add("launcherTheme", LegacySettingsAdapter.LAUNCHER_THEME, JsonPrimitive(settings.launcherTheme))
        add(
            "launcherWallpaperUri",
            value = settings.launcherWallpaperUri?.let { JsonPrimitive(it) } ?: JsonNull,
        )
        add("focusToolPackages", LegacySettingsAdapter.FOCUS_TOOL_PACKAGES, stringArray(settings.focusToolPackages))
        add("launcherHiddenPackages", LegacySettingsAdapter.LAUNCHER_HIDDEN_PACKAGES, stringArray(settings.launcherHiddenPackages))
        add("launcherLockDuringStandalone", LegacySettingsAdapter.LAUNCHER_LOCK_DURING_STANDALONE, JsonPrimitive(settings.launcherLockDuringStandalone))
        add("launcherBlockUninstall", LegacySettingsAdapter.LAUNCHER_BLOCK_UNINSTALL, JsonPrimitive(settings.launcherBlockUninstall))
        add("launcherPresets", LegacySettingsAdapter.ALLOWED_APP_PRESETS, presets(settings.launcherPresets, ::allowedPreset))
        add("launcherDockPackages", LegacySettingsAdapter.LAUNCHER_DOCK_PACKAGES, stringArray(settings.launcherDockPackages))
        add("launcherClockStyle", LegacySettingsAdapter.LAUNCHER_CLOCK_STYLE, JsonPrimitive(settings.launcherClockStyle))
        add("blockPresets", LegacySettingsAdapter.BLOCK_PRESETS, presets(settings.blockPresets, ::blockPreset))
        add("overlayQuotes", LegacySettingsAdapter.OVERLAY_QUOTES, stringArray(settings.overlayQuotes))
        add("alwaysOnVpnPackages", LegacySettingsAdapter.ALWAYS_ON_VPN_PACKAGES, stringArray(settings.alwaysOnVpnPackages))

        val allowanceEntries = settings.dailyAllowanceConfigJson
            ?.takeIf { it.isNotEmpty() }
            ?.let { parseArray("dailyAllowanceConfigJson", it) }
            ?: JsonArray(emptyList())
        add("dailyAllowanceConfigJson", LegacySettingsAdapter.DAILY_ALLOWANCE_ENTRIES, allowanceEntries)
        add(
            "recurringBlockSchedules",
            LegacySettingsAdapter.RECURRING_BLOCK_SCHEDULES,
            JsonArray(settings.recurringBlockSchedules.map(::recurringSchedule)),
        )
        add(
            "userGreyoutWindowsJson",
            LegacySettingsAdapter.GREYOUT_SCHEDULE,
            parseArray("userGreyoutWindowsJson", settings.userGreyoutWindowsJson),
        )
        add("focusMirrorVpnEnabled", LegacySettingsAdapter.FOCUS_MIRROR_VPN_ENABLED, JsonPrimitive(settings.focusMirrorVpnEnabled))
        add("keepFocusActiveUntilTaskEnd", LegacySettingsAdapter.KEEP_FOCUS_ACTIVE_UNTIL_TASK_END, JsonPrimitive(settings.keepFocusActiveUntilTaskEnd))
        add("autoRescheduleEnabled", LegacySettingsAdapter.AUTO_RESCHEDULE_ENABLED, JsonPrimitive(settings.autoRescheduleEnabled))
        add("darkModeEnabled", LegacySettingsAdapter.DARK_MODE, JsonPrimitive(settings.darkModeEnabled))
        add("generalTextScale", value = JsonPrimitive(settings.generalTextScale))
        add("homeTextScale", value = settings.homeTextScale?.let { JsonPrimitive(it) } ?: JsonNull)
        add("focusTextScale", value = settings.focusTextScale?.let { JsonPrimitive(it) } ?: JsonNull)
        add("statsTextScale", value = settings.statsTextScale?.let { JsonPrimitive(it) } ?: JsonNull)
        add("settingsTextScale", value = settings.settingsTextScale?.let { JsonPrimitive(it) } ?: JsonNull)
        add("defenseTextScale", value = settings.defenseTextScale?.let { JsonPrimitive(it) } ?: JsonNull)
        add(
            "screenTextScales",
            value = JsonObject(settings.screenTextScales.mapValues { JsonPrimitive(it.value) }),
        )
        add("morningDigestEnabled", value = JsonPrimitive(settings.morningDigestEnabled))
        add("achievementNotificationsEnabled", value = JsonPrimitive(settings.achievementNotificationsEnabled))
        add("patternInsightNotificationsEnabled", value = JsonPrimitive(settings.patternInsightNotificationsEnabled))
        add("rescheduleNotificationsEnabled", value = JsonPrimitive(settings.rescheduleNotificationsEnabled))
        add("blockSuggestionEnabled", value = JsonPrimitive(settings.blockSuggestionEnabled))
        add("weekAheadEnabled", value = JsonPrimitive(settings.weekAheadEnabled))
        add("temptationSpikeEnabled", value = JsonPrimitive(settings.temptationSpikeEnabled))
        add("temptationSpikeThreshold", value = JsonPrimitive(settings.temptationSpikeThreshold))
        add("bedTime", value = JsonPrimitive(settings.bedTime))
        add("productiveWindowNudgeEnabled", value = JsonPrimitive(settings.productiveWindowNudgeEnabled))
        add(
            "lastSessionResultByTaskId",
            value = JsonObject(settings.lastSessionResultByTaskId.mapValues { JsonPrimitive(it.value) }),
        )
        add("shownPatternInsightIds", value = stringArray(settings.shownPatternInsightIds))
        add(
            "lastShownDebriefSessionId",
            value = settings.lastShownDebriefSessionId?.let { JsonPrimitive(it) } ?: JsonNull,
        )
        add("taskRemindersEnabled", value = JsonPrimitive(settings.taskRemindersEnabled))
        add(
            "taskPreStartRemindersEnabled",
            value = JsonPrimitive(settings.taskPreStartRemindersEnabled),
        )
        add(
            "taskAtStartReminderEnabled",
            value = JsonPrimitive(settings.taskAtStartReminderEnabled),
        )
        add(
            "taskMidSessionRemindersEnabled",
            value = JsonPrimitive(settings.taskMidSessionRemindersEnabled),
        )
        add(
            "taskWrapUpReminderEnabled",
            value = JsonPrimitive(settings.taskWrapUpReminderEnabled),
        )
        add("taskEndAlarmRings", value = JsonPrimitive(settings.taskEndAlarmRings))
        add("reflectionPromptsEnabled", value = JsonPrimitive(settings.reflectionPromptsEnabled))
        add("defaultDurationMinutes", LegacySettingsAdapter.DEFAULT_DURATION, JsonPrimitive(settings.defaultDurationMinutes))
        add("autoFocusEnabled", value = JsonPrimitive(settings.autoFocusEnabled))
        add("allowedFocusPackages", LegacySettingsAdapter.ALLOWED_IN_FOCUS, stringArray(settings.allowedFocusPackages))
        add("pomodoroWorkMinutes", LegacySettingsAdapter.POMODORO_DURATION, JsonPrimitive(settings.pomodoroWorkMinutes))
        add("pomodoroBreakMinutes", LegacySettingsAdapter.POMODORO_BREAK, JsonPrimitive(settings.pomodoroBreakMinutes))
        add("focusDefenseHintDismissed", value = JsonPrimitive(settings.focusDefenseHintDismissed))
        add("localAnalyticsNoticeDismissed", value = JsonPrimitive(settings.localAnalyticsNoticeDismissed))
        add("standaloneBlockHintDismissed", value = JsonPrimitive(settings.standaloneBlockHintDismissed))
        add("alwaysOnInfoDismissed", value = JsonPrimitive(settings.alwaysOnInfoDismissed))
        add("protectionStatusBannerDismissed", value = JsonPrimitive(settings.protectionStatusBannerDismissed))

        return JsonObject(fields)
    }

    /**
     * Returns whether [key] is part of the current backup contract.
     *
     * Restore uses this allow-list for backup-only settings. It is derived from
     * the same projection used by export so future additions cannot accidentally
     * make device-local settings importable.
     */
    fun isPortableBackupKey(key: String): Boolean = key in portableBackupKeys

    internal fun parseArray(fieldName: String, raw: String): JsonArray {
        val parsed = try {
            json.parseToJsonElement(raw)
        } catch (exception: Exception) {
            throw IllegalArgumentException("$fieldName is not valid JSON.", exception)
        }
        return parsed as? JsonArray
            ?: throw IllegalArgumentException("$fieldName must contain a JSON array.")
    }

    internal fun stringArray(values: List<String>): JsonArray =
        JsonArray(values.map { JsonPrimitive(it) })

    internal fun <T> presets(
        values: List<T>,
        encode: (T) -> JsonObject,
    ): JsonArray = JsonArray(values.map(encode))

    internal fun allowedPreset(preset: AllowedAppPreset): JsonObject = JsonObject(
        mapOf(
            "id" to JsonPrimitive(preset.id),
            "name" to JsonPrimitive(preset.name),
            "packages" to stringArray(preset.packages),
        ),
    )

    internal fun blockPreset(preset: BlockPreset): JsonObject = JsonObject(
        mapOf(
            "id" to JsonPrimitive(preset.id),
            "name" to JsonPrimitive(preset.name),
            "packages" to stringArray(preset.packages),
        ),
    )

    internal fun recurringSchedule(schedule: RecurringBlockSchedule): JsonObject = JsonObject(
        mapOf(
            "id" to JsonPrimitive(schedule.id),
            "name" to JsonPrimitive(schedule.name.ifBlank { schedule.id }),
            "packages" to stringArray(schedule.packages),
            "days" to JsonArray(schedule.daysOfWeek.map { JsonPrimitive(it + 1) }),
            "startHour" to JsonPrimitive(schedule.startHour),
            "startMin" to JsonPrimitive(schedule.startMinute),
            "endHour" to JsonPrimitive(schedule.endHour),
            "endMin" to JsonPrimitive(schedule.endMinute),
            "enabled" to JsonPrimitive(schedule.enabled),
            "vpnEnabled" to JsonPrimitive(schedule.vpnEnabled),
            "vpnPackages" to stringArray(schedule.vpnPackages),
        ),
    )
}
