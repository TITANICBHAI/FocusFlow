package com.tbtechs.focusflow.data.backup

import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.Task
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull

object BackupSerializer {
    const val BACKUP_KIND = "FocusFlowBackupV1"
    const val BACKUP_VERSION = 1

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun buildEnvelope(
        settings: AppSettings,
        tasks: List<Task>,
        appVersion: String?,
    ): BackupEnvelope {
        require(tasks.size <= BackupJsonLimits.MAX_TASKS) {
            "Backup contains too many tasks."
        }
        val now = Instant.now()
        val exportedAtHuman = DateTimeFormatter
            .ofPattern("M/d/yyyy, h:mm:ss a", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(now)
        val allowances = settings.dailyAllowanceConfigJson
            ?.takeIf { it.isNotEmpty() }
            ?.let { PortableSettingsPolicy.parseArray("dailyAllowanceConfigJson", it) }
            ?: JsonArray(emptyList())
        val greyoutWindows = PortableSettingsPolicy.parseArray(
            "userGreyoutWindowsJson",
            settings.userGreyoutWindowsJson,
        )

        return BackupEnvelope(
            kind = BACKUP_KIND,
            version = BACKUP_VERSION,
            exportedAt = now.toString(),
            exportedAtHuman = exportedAtHuman,
            appVersion = appVersion,
            platform = BackupPlatform(os = "android"),
            settings = PortableSettingsPolicy.toPortableJson(settings),
            tasks = tasks,
            presetSections = buildPresetSections(settings, allowances),
            summary = BackupSummary(
                taskCount = tasks.size,
                blockedWordCount = settings.blockedWords.size,
                greyoutWindowCount = greyoutWindows.size,
                dailyAllowanceCount = allowances.size,
            ),
        )
    }

    fun serializeToJson(envelope: BackupEnvelope): String =
        json.encodeToString(envelope)

    fun buildSuggestedFilename(): String {
        val timestamp = Instant.now().toString().replace(":", "-").take(19)
        return "focusflow-$timestamp.focusflow"
    }

    fun parseAndValidate(jsonText: String): BackupParseResult {
        return try {
            val normalized = BackupJsonPreflight.validateAndStripBom(jsonText)
            val root = json.parseToJsonElement(normalized) as? JsonObject
                ?: return BackupParseResult.Failure("Backup must contain a JSON object.")

            val kind = root["kind"]?.let { (it as? JsonPrimitive)?.contentOrNull }
            if (kind != BACKUP_KIND) {
                return BackupParseResult.Failure("This is not a supported FocusFlow backup.")
            }

            val version = root["version"]?.let { (it as? JsonPrimitive)?.intOrNull }
            if (version != BACKUP_VERSION) {
                return BackupParseResult.Failure("This FocusFlow backup version is not supported.")
            }

            val settings = root["settings"] as? JsonObject
                ?: return BackupParseResult.Failure("Backup settings must be a JSON object.")

            val tasks = root["tasks"] as? JsonArray
                ?: return BackupParseResult.Failure("Backup tasks must be a JSON array.")
            if (tasks.size > BackupJsonLimits.MAX_TASKS) {
                return BackupParseResult.Failure("Backup contains too many tasks.")
            }

            // Decode the envelope metadata independently so one malformed task
            // row does not reject otherwise usable settings and task records.
            val metadataFields = root.toMutableMap()
            if ("exportedAtHuman" !in metadataFields) {
                metadataFields["exportedAtHuman"] =
                    (root["exportedAt"] as? JsonPrimitive)
                        ?.takeIf(JsonPrimitive::isString)
                        ?: JsonPrimitive("Unknown")
            }
            if ("appVersion" !in metadataFields) metadataFields["appVersion"] = JsonNull
            if ("presetSections" !in metadataFields) {
                metadataFields["presetSections"] = JsonArray(emptyList())
            }
            if ("summary" !in metadataFields) {
                metadataFields["summary"] = fallbackSummary(settings, tasks.size)
            }
            metadataFields["tasks"] = JsonArray(emptyList())
            val metadataRoot = JsonObject(metadataFields)
            val metadata = json.decodeFromJsonElement<BackupEnvelope>(metadataRoot)
            val validTasks = mutableListOf<Task>()
            var malformedTaskCount = 0
            tasks.forEach { taskElement ->
                try {
                    validTasks += json.decodeFromJsonElement<Task>(taskElement)
                } catch (_: Exception) {
                    malformedTaskCount++
                }
            }

            val warnings = buildList {
                if (malformedTaskCount > 0) {
                    val recordWord = if (malformedTaskCount == 1) "record will" else "records will"
                    add("$malformedTaskCount malformed task $recordWord be skipped.")
                }
                if (settings.isNotEmpty() && !BackupSettingsAdapter.hasRestorableSettings(settings)) {
                    add("Settings in this backup are not supported by this app version and will be left unchanged.")
                }
            }
            BackupParseResult.Success(
                envelope = metadata.copy(
                    tasks = validTasks,
                    summary = metadata.summary.copy(taskCount = tasks.size),
                ),
                warnings = warnings,
            )
        } catch (exception: BackupJsonFormatException) {
            BackupParseResult.Failure(exception.message ?: "Backup JSON is invalid.")
        } catch (exception: SerializationException) {
            BackupParseResult.Failure("Backup JSON is malformed or is missing required fields.")
        } catch (exception: IllegalArgumentException) {
            BackupParseResult.Failure(exception.message ?: "Backup JSON is invalid.")
        } catch (_: Exception) {
            BackupParseResult.Failure("Backup JSON is malformed.")
        }
    }

    private fun fallbackSummary(settings: JsonObject, taskCount: Int): JsonObject =
        JsonObject(
            mapOf(
                "taskCount" to JsonPrimitive(taskCount),
                "blockedWordCount" to JsonPrimitive(arrayCount(settings, "blockedWords")),
                "greyoutWindowCount" to JsonPrimitive(
                    arrayCount(settings, "greyoutSchedule", "userGreyoutWindowsJson"),
                ),
                "dailyAllowanceCount" to JsonPrimitive(
                    arrayCount(settings, "dailyAllowanceEntries", "dailyAllowanceConfigJson"),
                ),
            ),
        )

    private fun arrayCount(settings: JsonObject, vararg keys: String): Int {
        keys.forEach { key ->
            (settings[key] as? JsonArray)?.let { return it.size }
        }
        return 0
    }

    private fun buildPresetSections(
        settings: AppSettings,
        allowances: JsonArray,
    ): List<BackupPresetSection> {
        val allowedPresets = JsonArray(settings.launcherPresets.map(PortableSettingsPolicy::allowedPreset))
        val blockPresets = JsonArray(settings.blockPresets.map(PortableSettingsPolicy::blockPreset))
        val blockedWords = PortableSettingsPolicy.stringArray(settings.blockedWords)
        val scheduleWindows = JsonArray(
            settings.recurringBlockSchedules.map(PortableSettingsPolicy::recurringSchedule),
        )

        return listOf(
            BackupPresetSection(
                id = "focus-mode",
                name = "Focus Mode",
                configured = settings.allowedFocusPackages.isNotEmpty() || settings.launcherPresets.isNotEmpty(),
                appPackages = settings.allowedFocusPackages,
                itemCount = settings.launcherPresets.size,
                details = JsonObject(mapOf("allowedAppPresets" to allowedPresets)),
            ),
            BackupPresetSection(
                id = "standalone-block",
                name = "Standalone Block",
                configured = settings.standaloneBlockPackages.isNotEmpty() ||
                    settings.standaloneBlockVpnPackages.isNotEmpty(),
                appPackages = settings.standaloneBlockPackages,
                vpnPackages = settings.standaloneBlockVpnPackages,
                details = JsonObject(mapOf("runtimeState" to JsonPrimitive("local-only"))),
            ),
            BackupPresetSection(
                id = "always-on",
                name = "Always-On Blocking",
                configured = settings.alwaysBlockPackages.isNotEmpty() ||
                    settings.alwaysOnVpnPackages.isNotEmpty(),
                appPackages = settings.alwaysBlockPackages,
                vpnPackages = settings.alwaysOnVpnPackages,
            ),
            BackupPresetSection(
                id = "daily-allowance",
                name = "Daily Allowance",
                configured = settings.dailyAllowanceConfigJson?.isNotEmpty() == true,
                itemCount = allowances.size,
                details = JsonObject(mapOf("entries" to allowances)),
            ),
            BackupPresetSection(
                id = "keyword-blocker",
                name = "Keyword Blocker",
                configured = settings.blockedWords.isNotEmpty(),
                itemCount = settings.blockedWords.size,
                details = JsonObject(mapOf("keywords" to blockedWords)),
            ),
            BackupPresetSection(
                id = "block-schedules",
                name = "Block Schedules",
                configured = settings.recurringBlockSchedules.isNotEmpty(),
                itemCount = scheduleWindows.size,
                details = JsonObject(mapOf("windows" to scheduleWindows)),
            ),
            BackupPresetSection(
                id = "defense",
                name = "Defense",
                configured = settings.blockPresets.isNotEmpty(),
                itemCount = settings.blockPresets.size,
                details = JsonObject(
                    mapOf(
                        "blockPresets" to blockPresets,
                        "overlayQuotes" to PortableSettingsPolicy.stringArray(settings.overlayQuotes),
                        "overlayWallpaper" to JsonPrimitive(""),
                    ),
                ),
            ),
        )
    }
}
