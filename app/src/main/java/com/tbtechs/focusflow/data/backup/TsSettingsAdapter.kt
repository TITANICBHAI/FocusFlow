package com.tbtechs.focusflow.data.backup

import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

data class LegacySettingsNormalization(
    val settings: JsonObject,
    val warnings: List<String>,
    val fatalError: String? = null,
)

sealed class LegacyPreferenceValue {
    data class StringValue(val value: String) : LegacyPreferenceValue()
    data class BooleanValue(val value: Boolean) : LegacyPreferenceValue()
    data class IntValue(val value: Int) : LegacyPreferenceValue()
}

/**
 * Reads settings stored by the older TypeScript app and maps them to native
 * SharedPreferences values during the one-time database migration.
 */
object LegacySettingsAdapter {
    const val DARK_MODE = "darkMode"
    const val DEFAULT_DURATION = "defaultDuration"
    const val POMODORO_DURATION = "pomodoroDuration"
    const val POMODORO_BREAK = "pomodoroBreak"
    const val ALLOWED_IN_FOCUS = "allowedInFocus"
    const val ALWAYS_ON_PACKAGES = "alwaysOnPackages"
    const val ALWAYS_ON_VPN_PACKAGES = "alwaysOnVpnPackages"
    const val FOCUS_MIRROR_VPN_ENABLED = "focusMirrorVpnEnabled"
    const val BLOCKED_WORDS = "blockedWords"
    const val DAILY_ALLOWANCE_ENTRIES = "dailyAllowanceEntries"
    const val ALLOWED_APP_PRESETS = "allowedAppPresets"
    const val BLOCK_PRESETS = "blockPresets"
    const val RECURRING_BLOCK_SCHEDULES = "recurringBlockSchedules"
    const val GREYOUT_SCHEDULE = "greyoutSchedule"
    const val USER_PROFILE = "userProfile"
    const val LAUNCHER_THEME = "launcherTheme"
    const val FOCUS_TOOL_PACKAGES = "focusToolPackages"
    const val LAUNCHER_HIDDEN_PACKAGES = "launcherHiddenPackages"
    const val LAUNCHER_DOCK_PACKAGES = "launcherDockPackages"
    const val LAUNCHER_CLOCK_STYLE = "launcherClockStyle"
    const val LAUNCHER_BLOCK_UNINSTALL = "launcherBlockUninstall"
    const val LAUNCHER_LOCK_DURING_STANDALONE = "launcherLockDuringStandalone"
    const val KEEP_FOCUS_ACTIVE_UNTIL_TASK_END = "keepFocusActiveUntilTaskEnd"
    const val AUTO_RESCHEDULE_ENABLED = "autoRescheduleEnabled"
    const val OVERLAY_QUOTES = "overlayQuotes"
    const val PROTECTION_MODE = "protectionMode"

    private val json = Json { isLenient = false }
    private val packageRegex = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    private val ignoredKeys = setOf(
        "weekStartDay",
        "defaultReminderOffsets",
        "launcherWallpaperUri",
        "overlayWallpaper",
        "launcherPinnedPackages",
        "beginnerMode",
        "tipsCardDismissed",
        "tipsCardFirstShownAt",
        "lastShownStreakMilestone",
        "pendingAchievementCelebration",
        "pendingPresets",
        "onboardingComplete",
        "privacyAccepted",
    )
    private val profileFields = setOf(
        "name",
        "occupation",
        "dailyGoalHours",
        "wakeUpTime",
        "sleepTime",
        "focusGoals",
        "chronotype",
        "focusSessionLength",
        "breakStyle",
        "distractionTriggers",
        "motivationStyle",
        "weeklyReviewDay",
    )

    /**
     * Validates legacy settings before mapping them to native preference
     * values. Invalid fields are omitted, while unsupported protection modes
     * reject the migration rather than writing an invalid value.
     */
    private fun normalizeLegacySettings(input: JsonObject): LegacySettingsNormalization {
        val output = linkedMapOf<String, JsonElement>()
        val warnings = mutableListOf<String>()

        input.forEach { (key, value) ->
            if (!LegacySettingsPolicy.mayMigrateKey(key) || key in ignoredKeys) {
                return@forEach
            }

            val normalized = when (key) {
                DARK_MODE,
                FOCUS_MIRROR_VPN_ENABLED,
                LAUNCHER_BLOCK_UNINSTALL,
                LAUNCHER_LOCK_DURING_STANDALONE,
                KEEP_FOCUS_ACTIVE_UNTIL_TASK_END,
                AUTO_RESCHEDULE_ENABLED,
                -> value.booleanValue()?.let(::JsonPrimitive)
                    ?: invalidSetting(key, warnings)

                DEFAULT_DURATION -> value.intValueIn(5..480)
                    ?.let(::JsonPrimitive)
                    ?: invalidSetting(key, warnings)

                POMODORO_DURATION -> value.intValueIn(1..180)
                    ?.let(::JsonPrimitive)
                    ?: invalidSetting(key, warnings)

                POMODORO_BREAK -> value.intValueIn(1..60)
                    ?.let(::JsonPrimitive)
                    ?: invalidSetting(key, warnings)

                ALLOWED_IN_FOCUS,
                ALWAYS_ON_PACKAGES,
                ALWAYS_ON_VPN_PACKAGES,
                FOCUS_TOOL_PACKAGES,
                LAUNCHER_HIDDEN_PACKAGES,
                LAUNCHER_DOCK_PACKAGES,
                -> normalizeStringArray(
                    key = key,
                    element = value,
                    maxCount = BackupJsonLimits.MAX_PACKAGES_PER_LIST,
                    maxChars = BackupJsonLimits.MAX_PACKAGE_CHARS,
                    warnings = warnings,
                    packageNames = true,
                )

                BLOCKED_WORDS -> normalizeStringArray(
                    key = key,
                    element = value,
                    maxCount = BackupJsonLimits.MAX_BLOCKED_WORDS,
                    maxChars = BackupJsonLimits.MAX_BLOCKED_WORD_CHARS,
                    warnings = warnings,
                )

                OVERLAY_QUOTES -> normalizeStringArray(
                    key = key,
                    element = value,
                    maxCount = BackupJsonLimits.MAX_OVERLAY_QUOTES,
                    maxChars = BackupJsonLimits.MAX_STRING_CHARS,
                    warnings = warnings,
                )

                DAILY_ALLOWANCE_ENTRIES -> normalizeAllowanceEntries(value, warnings)
                ALLOWED_APP_PRESETS -> normalizePresets(
                    key = key,
                    value = value,
                    warnings = warnings,
                    allowBlockAllSentinel = true,
                )
                BLOCK_PRESETS -> normalizePresets(
                    key = key,
                    value = value,
                    warnings = warnings,
                    allowBlockAllSentinel = false,
                )
                RECURRING_BLOCK_SCHEDULES -> normalizeRecurringSchedules(value, warnings)
                GREYOUT_SCHEDULE -> normalizeGreyoutWindows(value, warnings)
                USER_PROFILE -> normalizeUserProfile(value, warnings)
                LAUNCHER_THEME -> value.stringValue()
                    ?.takeIf { it == "classic" || it == "glassy" }
                    ?.let(::JsonPrimitive)
                    ?: invalidSetting(key, warnings)
                LAUNCHER_CLOCK_STYLE -> value.stringValue()
                    ?.takeIf { it.isNotBlank() && it.length <= 128 }
                    ?.let(::JsonPrimitive)
                    ?: invalidSetting(key, warnings)
                PROTECTION_MODE -> {
                    val mode = value.stringValue()
                    if (mode == "standard" || mode == "iron") {
                        JsonPrimitive(mode)
                    } else {
                        return LegacySettingsNormalization(
                            settings = JsonObject(output),
                            warnings = warnings,
                            fatalError = "Unsupported protectionMode. Supported values are standard and iron.",
                        )
                    }
                }
                else -> null
            }
            if (normalized != null) output[key] = normalized
        }

        return LegacySettingsNormalization(JsonObject(output), warnings)
    }

    /**
     * Parses the old app_settings blob with strict duplicate-key, byte, depth,
     * and node checks before migration.
     */
    fun parseLegacySettingsJson(text: String): JsonObject {
        val normalized = BackupJsonPreflight.validateAndStripBom(text)
        val value = runCatching { json.parseToJsonElement(normalized) }
            .getOrElse { throw BackupJsonFormatException("Legacy settings JSON is malformed.") }
        return value as? JsonObject
            ?: throw BackupJsonFormatException("Legacy settings must be a JSON object.")
    }

    /**
     * Converts recognized legacy settings to their real SharedPreferences
     * value types. Migration still writes each entry only when its target key
     * is absent, preserving existing Kotlin-owned state.
     */
    fun toSharedPreferencesValues(input: JsonObject): Map<String, LegacyPreferenceValue> {
        val normalized = normalizeLegacySettings(input)
        normalized.fatalError?.let { throw BackupJsonFormatException(it) }
        val values = normalized.settings
        val result = linkedMapOf<String, LegacyPreferenceValue>()

        fun string(key: String, target: String, encode: (JsonElement) -> String = { it.toString() }) {
            values[key]?.let { result[target] = LegacyPreferenceValue.StringValue(encode(it)) }
        }
        fun boolean(key: String, target: String) {
            values[key]?.booleanValue()?.let {
                result[target] = LegacyPreferenceValue.BooleanValue(it)
            }
        }
        fun integer(key: String, target: String) {
            values[key]?.intValue()?.let { result[target] = LegacyPreferenceValue.IntValue(it) }
        }

        boolean(DARK_MODE, "dark_mode_enabled")
        integer(DEFAULT_DURATION, "default_duration_minutes")
        integer(POMODORO_DURATION, "pomodoro_work_minutes")
        integer(POMODORO_BREAK, "pomodoro_break_minutes")
        string(ALLOWED_IN_FOCUS, "allowed_focus_packages")
        string(ALWAYS_ON_PACKAGES, AppBlockerAccessibilityService.PREF_ALWAYS_BLOCK_PKGS)
        string(ALWAYS_ON_VPN_PACKAGES, "net_block_explicit_packages")
        boolean(FOCUS_MIRROR_VPN_ENABLED, "net_block_focus_mirror")
        string(BLOCKED_WORDS, AppBlockerAccessibilityService.PREF_BLOCKED_WORDS)
        string(DAILY_ALLOWANCE_ENTRIES, "daily_allowance_config", ::toInternalAllowanceJson)
        string(ALLOWED_APP_PRESETS, "allowed_app_presets")
        string(BLOCK_PRESETS, "block_presets")
        string(RECURRING_BLOCK_SCHEDULES, "recurring_block_schedules", ::toStoredRecurringJson)
        string(GREYOUT_SCHEDULE, "user_greyout_windows")
        string(GREYOUT_SCHEDULE, "greyout_schedule")
        values[USER_PROFILE]?.let {
            result["user_profile"] = LegacyPreferenceValue.StringValue(it.toString())
        }
        string(LAUNCHER_THEME, "launcher_theme") { element ->
            if (element.stringValue() == "classic") "classic" else "glassy"
        }
        string(FOCUS_TOOL_PACKAGES, "focus_tool_packages")
        string(LAUNCHER_HIDDEN_PACKAGES, "launcher_hidden_packages")
        string(LAUNCHER_HIDDEN_PACKAGES, "drawer_hidden_packages")
        string(LAUNCHER_DOCK_PACKAGES, "launcher_dock_packages")
        string(LAUNCHER_CLOCK_STYLE, "launcher_clock_style")
        boolean(LAUNCHER_BLOCK_UNINSTALL, "launcher_block_uninstall")
        boolean(LAUNCHER_LOCK_DURING_STANDALONE, "launcher_lock_during_standalone")
        boolean(KEEP_FOCUS_ACTIVE_UNTIL_TASK_END, "keep_focus_active_until_task_end")
        boolean(AUTO_RESCHEDULE_ENABLED, "auto_reschedule_enabled")
        string(OVERLAY_QUOTES, "block_overlay_quotes")
        values[PROTECTION_MODE]?.stringValue()?.let {
            result["protection_mode"] = LegacyPreferenceValue.StringValue(it)
        }
        return result
    }

    private fun toInternalAllowanceJson(element: JsonElement): String {
        val array = element as? JsonArray ?: return "[]"
        val internal = array.mapNotNull { entry ->
            val objectValue = entry as? JsonObject ?: return@mapNotNull null
            val packageName = objectValue["packageName"]?.stringValue()
                ?: objectValue["package"]?.stringValue()
                ?: return@mapNotNull null
            val fields = objectValue.toMutableMap()
            fields["package"] = JsonPrimitive(packageName)
            fields["packageName"] = JsonPrimitive(packageName)
            if (fields["dailyAllowanceMs"] == null) {
                val budget = fields["budgetMinutes"]?.intValue() ?: 30
                fields["dailyAllowanceMs"] =
                    JsonPrimitive(if (fields["mode"]?.stringValue() == "time_budget") budget * 60_000L else 0L)
            }
            JsonObject(fields)
        }
        return JsonArray(internal).toString()
    }

    private fun toStoredRecurringJson(element: JsonElement): String {
        val array = element as? JsonArray ?: return "[]"
        val stored = array.mapNotNull { value ->
            val schedule = value as? JsonObject ?: return@mapNotNull null
            val days = schedule["days"] as? JsonArray ?: JsonArray(emptyList())
            JsonObject(
                mapOf(
                    "id" to (schedule["id"] ?: JsonPrimitive("")),
                    "name" to (schedule["name"] ?: JsonPrimitive("")),
                    "packages" to (schedule["packages"] ?: JsonArray(emptyList())),
                    "startHour" to (schedule["startHour"] ?: JsonPrimitive(0)),
                    "startMinute" to (schedule["startMin"] ?: JsonPrimitive(0)),
                    "endHour" to (schedule["endHour"] ?: JsonPrimitive(0)),
                    "endMinute" to (schedule["endMin"] ?: JsonPrimitive(0)),
                    "daysOfWeek" to JsonArray(days.mapNotNull {
                        it.intValue()?.takeIf { day -> day in 1..7 }?.let { day ->
                            JsonPrimitive(day - 1)
                        }
                    }),
                    "enabled" to (schedule["enabled"] ?: JsonPrimitive(true)),
                    "vpnEnabled" to (schedule["vpnEnabled"] ?: JsonPrimitive(false)),
                    "vpnPackages" to (schedule["vpnPackages"] ?: JsonArray(emptyList())),
                ),
            )
        }
        return JsonArray(stored).toString()
    }

    private fun JsonElement.stringList(): List<String>? =
        (this as? JsonArray)?.mapNotNull { it.stringValue() }

    private fun normalizeStringArray(
        key: String,
        element: JsonElement,
        maxCount: Int,
        maxChars: Int,
        warnings: MutableList<String>,
        packageNames: Boolean = false,
        allowBlockAllSentinel: Boolean = false,
    ): JsonArray? {
        val array = element as? JsonArray ?: return invalidSetting(key, warnings)
        if (array.size > maxCount) {
            throw BackupJsonFormatException("$key exceeds its maximum item count.")
        }
        var dropped = 0
        val accepted = array.mapNotNull { item ->
            val string = item.stringValue()
            val valid = string != null &&
                string.length <= maxChars &&
                (!packageNames ||
                    packageRegex.matches(string) ||
                    (allowBlockAllSentinel && string == "__block_all__"))
            if (valid) JsonPrimitive(string!!) else {
                dropped++
                null
            }
        }
        if (dropped > 0) warnings += "$key: dropped $dropped invalid list item(s)."
        return JsonArray(accepted)
    }

    private fun normalizeAllowanceEntries(
        value: JsonElement,
        warnings: MutableList<String>,
    ): JsonArray? {
        val array = value as? JsonArray ?: return invalidSetting(DAILY_ALLOWANCE_ENTRIES, warnings)
        if (array.size > BackupJsonLimits.MAX_ALLOWANCE_ENTRIES) {
            throw BackupJsonFormatException("dailyAllowanceEntries exceeds its maximum item count.")
        }
        var dropped = 0
        val accepted = array.mapNotNull { entry ->
            val obj = entry as? JsonObject
            val packageName = obj?.get("packageName")?.stringValue()
                ?: obj?.get("package")?.stringValue()
            val mode = obj?.get("mode")?.stringValue()
                ?: if (obj?.get("dailyAllowanceMs")?.longValue() != null) "time_budget" else null
            if (obj == null ||
                packageName.isNullOrBlank() ||
                packageName.length > BackupJsonLimits.MAX_PACKAGE_CHARS ||
                !packageRegex.matches(packageName) ||
                mode !in setOf("count", "time_budget", "interval")
            ) {
                dropped++
                return@mapNotNull null
            }

            val normalized = obj.toMutableMap()
            normalized["packageName"] = JsonPrimitive(packageName)
            normalized.remove("package")
            normalized["mode"] = JsonPrimitive(mode!!)
            val numericRules = listOf(
                "countPerDay" to (1..100_000),
                "budgetMinutes" to (1..100_000),
                "intervalMinutes" to (1..100_000),
                "intervalHours" to (1..8_760),
            )
            var valid = true
            numericRules.forEach { (field, range) ->
                if (normalized[field] != null && normalized[field] != JsonNull) {
                    val number = normalized[field]?.intValueIn(range)
                    if (number == null) valid = false else normalized[field] = JsonPrimitive(number)
                }
            }
            normalized["dailyAllowanceMs"]?.let { raw ->
                val milliseconds = raw.longValue()
                if (milliseconds == null || milliseconds !in 0L..6_000_000_000L) {
                    valid = false
                } else {
                    normalized["dailyAllowanceMs"] = JsonPrimitive(milliseconds)
                }
            }
            if (!valid) {
                dropped++
                null
            } else {
                JsonObject(normalized)
            }
        }
        if (dropped > 0) warnings += "dailyAllowanceEntries: dropped $dropped invalid entr(y/ies)."
        return JsonArray(accepted)
    }

    private fun normalizePresets(
        key: String,
        value: JsonElement,
        warnings: MutableList<String>,
        allowBlockAllSentinel: Boolean,
    ): JsonArray? {
        val array = value as? JsonArray ?: return invalidSetting(key, warnings)
        if (array.size > BackupJsonLimits.MAX_PRESETS) {
            throw BackupJsonFormatException("$key exceeds its maximum item count.")
        }
        var dropped = 0
        val accepted = array.mapNotNull { element ->
            val item = element as? JsonObject
            val id = item?.get("id")?.stringValue()
            val name = item?.get("name")?.stringValue()
            val packages = item?.get("packages") as? JsonArray
            if (id.isNullOrBlank() || id.length > BackupJsonLimits.MAX_ID_CHARS ||
                name == null || name.length > BackupJsonLimits.MAX_TITLE_CHARS ||
                packages == null || packages.size > BackupJsonLimits.MAX_PACKAGES_PER_LIST
            ) {
                dropped++
                return@mapNotNull null
            }
            val normalizedPackages = normalizeStringArray(
                key = "$key.packages",
                element = packages,
                maxCount = BackupJsonLimits.MAX_PACKAGES_PER_LIST,
                maxChars = BackupJsonLimits.MAX_PACKAGE_CHARS,
                warnings = warnings,
                packageNames = true,
                allowBlockAllSentinel = allowBlockAllSentinel,
            ) ?: JsonArray(emptyList())
            JsonObject(item.toMutableMap().apply {
                put("id", JsonPrimitive(id))
                put("name", JsonPrimitive(name))
                put("packages", normalizedPackages)
            })
        }
        if (dropped > 0) warnings += "$key: dropped $dropped invalid preset(s)."
        return JsonArray(accepted)
    }

    private fun normalizeRecurringSchedules(
        value: JsonElement,
        warnings: MutableList<String>,
    ): JsonArray? {
        val array = value as? JsonArray ?: return invalidSetting(RECURRING_BLOCK_SCHEDULES, warnings)
        if (array.size > BackupJsonLimits.MAX_RECURRING_SCHEDULES) {
            throw BackupJsonFormatException("recurringBlockSchedules exceeds its maximum item count.")
        }
        var dropped = 0
        val accepted = array.mapNotNull { element ->
            val normalized = (element as? JsonObject)?.let { normalizeSchedule(it, warnings) }
            if (normalized == null) {
                dropped++
                null
            } else {
                normalized
            }
        }
        if (dropped > 0) warnings += "recurringBlockSchedules: dropped $dropped invalid schedule(s)."
        return JsonArray(accepted)
    }

    private fun normalizeSchedule(
        item: JsonObject,
        warnings: MutableList<String>,
    ): JsonObject? {
        val id = item["id"]?.stringValue()
        val name = item["name"]?.stringValue()
        val packages = item["packages"] as? JsonArray ?: return null
        val startHour = item["startHour"]?.intValueIn(0..23) ?: return null
        val startMinute = (item["startMin"] ?: item["startMinute"])?.intValueIn(0..59)
            ?: return null
        val endHour = item["endHour"]?.intValueIn(0..23) ?: return null
        val endMinute = (item["endMin"] ?: item["endMinute"])?.intValueIn(0..59)
            ?: return null
        val days = if (item["days"] != null) {
            normalizeDays(item["days"], oneBased = true)
        } else {
            normalizeDays(item["daysOfWeek"], oneBased = false)
        }
            ?: return null
        val enabled = item["enabled"]?.let { it.booleanValue() ?: return null } ?: true
        val vpnEnabled = item["vpnEnabled"]?.let { it.booleanValue() ?: return null } ?: false
        if (id.isNullOrBlank() || id.length > BackupJsonLimits.MAX_ID_CHARS ||
            name.isNullOrBlank() || name.length > BackupJsonLimits.MAX_TITLE_CHARS ||
            packages.size > BackupJsonLimits.MAX_PACKAGES_PER_LIST
        ) return null

        val normalizedPackages = normalizeStringArray(
            key = "$RECURRING_BLOCK_SCHEDULES.packages",
            element = packages,
            maxCount = BackupJsonLimits.MAX_PACKAGES_PER_LIST,
            maxChars = BackupJsonLimits.MAX_PACKAGE_CHARS,
            warnings = warnings,
            packageNames = true,
        ) ?: return null
        val vpnPackages = if (item.containsKey("vpnPackages")) {
            normalizeStringArray(
                key = "$RECURRING_BLOCK_SCHEDULES.vpnPackages",
                element = item["vpnPackages"] ?: JsonNull,
                maxCount = BackupJsonLimits.MAX_PACKAGES_PER_LIST,
                maxChars = BackupJsonLimits.MAX_PACKAGE_CHARS,
                warnings = warnings,
                packageNames = true,
            ) ?: return null
        } else JsonArray(emptyList())
        if (enabled && normalizedPackages.isEmpty()) return null

        val normalized = item.toMutableMap()
        normalized["id"] = JsonPrimitive(id)
        normalized["name"] = JsonPrimitive(name)
        normalized["packages"] = normalizedPackages
        normalized["startHour"] = JsonPrimitive(startHour)
        normalized["startMin"] = JsonPrimitive(startMinute)
        normalized["endHour"] = JsonPrimitive(endHour)
        normalized["endMin"] = JsonPrimitive(endMinute)
        normalized["days"] = days
        normalized["enabled"] = JsonPrimitive(enabled)
        normalized["vpnEnabled"] = JsonPrimitive(vpnEnabled)
        normalized["vpnPackages"] = vpnPackages
        normalized.remove("startMinute")
        normalized.remove("endMinute")
        normalized.remove("daysOfWeek")
        return JsonObject(normalized)
    }

    private fun normalizeGreyoutWindows(
        value: JsonElement,
        warnings: MutableList<String>,
    ): JsonArray? {
        val array = value as? JsonArray ?: return invalidSetting(GREYOUT_SCHEDULE, warnings)
        if (array.size > BackupJsonLimits.MAX_GREYOUT_WINDOWS) {
            throw BackupJsonFormatException("greyoutSchedule exceeds its maximum item count.")
        }
        var dropped = 0
        val accepted = array.mapNotNull { element ->
            val item = element as? JsonObject
            if (item == null) {
                dropped++
                return@mapNotNull null
            }
            if (item["scheduleId"]?.stringValue()?.isNotBlank() == true) {
                dropped++
                return@mapNotNull null
            }
            val normalized = normalizeGreyoutWindow(item, warnings)
            if (normalized == null) dropped++
            normalized
        }
        if (dropped > 0) warnings += "greyoutSchedule: dropped $dropped derived or invalid window(s)."
        return JsonArray(accepted)
    }

    private fun normalizeGreyoutWindow(
        item: JsonObject,
        warnings: MutableList<String>,
    ): JsonObject? {
        val startHour = item["startHour"]?.intValueIn(0..23) ?: return null
        val startMinute = (item["startMin"] ?: item["startMinute"])?.intValueIn(0..59)
            ?: return null
        val endHour = item["endHour"]?.intValueIn(0..23) ?: return null
        val endMinute = (item["endMin"] ?: item["endMinute"])?.intValueIn(0..59)
            ?: return null
        val days = if (item["days"] != null) {
            normalizeDays(item["days"], oneBased = true)
        } else {
            normalizeDays(item["daysOfWeek"], oneBased = false)
        }
            ?: return null
        val pkg = item["pkg"]?.stringValue()
        val packages = item["pkgs"] as? JsonArray
        if (pkg.isNullOrBlank() && (packages == null || packages.isEmpty())) return null
        if (packages != null && packages.size > BackupJsonLimits.MAX_PACKAGES_PER_LIST) return null

        val normalizedPackages = packages?.let {
            normalizeStringArray(
                key = "$GREYOUT_SCHEDULE.pkgs",
                element = it,
                maxCount = BackupJsonLimits.MAX_PACKAGES_PER_LIST,
                maxChars = BackupJsonLimits.MAX_PACKAGE_CHARS,
                warnings = warnings,
                packageNames = true,
            )
        }
        val normalizedPkg = pkg?.takeIf {
            it.length <= BackupJsonLimits.MAX_PACKAGE_CHARS && packageRegex.matches(it)
        }
        if (normalizedPkg == null && (normalizedPackages == null || normalizedPackages.isEmpty())) {
            return null
        }
        val vpnEnabled = item["vpnEnabled"]?.booleanValue()

        val normalized = item.toMutableMap()
        normalized["startHour"] = JsonPrimitive(startHour)
        normalized["startMin"] = JsonPrimitive(startMinute)
        normalized["endHour"] = JsonPrimitive(endHour)
        normalized["endMin"] = JsonPrimitive(endMinute)
        normalized["days"] = days
        normalized.remove("startMinute")
        normalized.remove("endMinute")
        normalized.remove("daysOfWeek")
        if (normalizedPkg != null) normalized["pkg"] = JsonPrimitive(normalizedPkg)
        else normalized.remove("pkg")
        if (normalizedPackages != null) normalized["pkgs"] = normalizedPackages
        if (vpnEnabled != null) normalized["vpnEnabled"] = JsonPrimitive(vpnEnabled)
        normalized.remove("scheduleId")
        normalized.remove("scheduleName")
        return JsonObject(normalized)
    }

    private fun normalizeDays(value: JsonElement?, oneBased: Boolean): JsonArray? {
        val array = value as? JsonArray ?: return null
        val result = mutableListOf<JsonElement>()
        val seen = mutableSetOf<Int>()
        array.forEach { element ->
            val day = element.intValue() ?: return null
            val mapped = if (oneBased && day in 1..7) day else if (!oneBased && day in 0..6) day + 1 else return null
            if (!seen.add(mapped)) return null
            result += JsonPrimitive(mapped)
        }
        return JsonArray(result)
    }

    private fun normalizeUserProfile(
        value: JsonElement,
        warnings: MutableList<String>,
    ): JsonObject? {
        val profile = value as? JsonObject ?: return invalidSetting(USER_PROFILE, warnings)
        val output = linkedMapOf<String, JsonElement>()
        var invalid = false
        profile.forEach { (key, field) ->
            if (key !in profileFields) return@forEach
            val normalized: JsonElement? = when (key) {
                "name", "occupation" -> optionalString(field, maxChars = 1_000)
                "wakeUpTime", "sleepTime" -> {
                    if (field == JsonNull) JsonNull
                    else field.stringValue()?.takeIf { it.matches(Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")) }
                        ?.let(::JsonPrimitive)
                }
                "dailyGoalHours" -> {
                    if (field == JsonNull) JsonNull else field.intValueIn(1..16)?.let(::JsonPrimitive)
                }
                "focusSessionLength" -> {
                    if (field == JsonNull) JsonNull else field.intValueIn(1..480)?.let(::JsonPrimitive)
                }
                "chronotype" -> optionalEnum(
                    field,
                    setOf("morning", "midday", "afternoon", "evening", "night", "flexible"),
                )
                "breakStyle" -> optionalEnum(
                    field,
                    setOf("short_frequent", "balanced", "long_infrequent", "no_break"),
                )
                "weeklyReviewDay" -> optionalEnum(
                    field,
                    setOf("sun", "mon", "tue", "wed", "thu", "fri", "sat"),
                )
                "focusGoals", "distractionTriggers", "motivationStyle" ->
                    normalizeStringArray(
                        key = "$USER_PROFILE.$key",
                        element = field,
                        maxCount = 100,
                        maxChars = 200,
                        warnings = warnings,
                    )
                else -> null
            }
            if (normalized == null) invalid = true else output[key] = normalized
        }
        if (invalid) {
            warnings += "userProfile: ignored because a recognized field has an invalid value."
            return null
        }
        return JsonObject(output)
    }

    private fun optionalString(element: JsonElement, maxChars: Int): JsonElement? {
        if (element == JsonNull) return JsonNull
        return element.stringValue()
            ?.takeIf { it.length <= maxChars }
            ?.let(::JsonPrimitive)
    }

    private fun optionalEnum(element: JsonElement, options: Set<String>): JsonElement? {
        if (element == JsonNull) return JsonNull
        return element.stringValue()
            ?.takeIf(options::contains)
            ?.let(::JsonPrimitive)
    }

    private fun invalidSetting(key: String, warnings: MutableList<String>): Nothing? {
        warnings += "$key: ignored because its value is invalid."
        return null
    }

    private fun JsonElement.stringValue(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        return primitive.content.takeIf { primitive.isString }
    }

    private fun JsonElement.booleanValue(): Boolean? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        return primitive.booleanOrNull
    }

    private fun JsonElement.intValue(): Int? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        return primitive.intOrNull
    }

    private fun JsonElement.longValue(): Long? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        return primitive.longOrNull
    }

    private fun JsonElement.intValueIn(range: IntRange): Int? =
        intValue()?.takeIf(range::contains)
}

/** Key value shared with the accessibility service without importing its Android class here. */
private object AppBlockerKeys {
    const val PREF_BLOCKED_WORDS = "blocked_words"
}