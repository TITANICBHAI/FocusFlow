package com.tbtechs.focusflow.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.time.Clock
import java.time.Instant
import java.util.Date

/**
 * Kotlin/SAF port of backupService.ts.
 *
 * Settings and task payloads remain raw JSON objects on purpose. This prevents
 * a native migration from dropping fields that were added by the React Native
 * app or by a newer app version. The FocusFlowBackupV1 envelope and its field
 * names are part of the user's existing file format.
 */
class BackupManager(
    context: Context,
    private val dataSource: BackupDataSource,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val appContext = context.applicationContext

    suspend fun buildBackupJson(
        settings: JSONObject,
        appVersion: String? = null,
    ): String {
        val tasks = runCatching { dataSource.getAllTasks() }
            .getOrElse { emptyList() }
        val now = clock.instant()
        val taskArray = JSONArray()
        tasks.forEach { taskArray.put(copyJson(it)) }

        val envelope = JSONObject()
            .put("kind", BACKUP_ENVELOPE_KIND)
            .put("version", 1)
            .put("exportedAt", formatIso(now))
            .put(
                "exportedAtHuman",
                DateFormat.getDateTimeInstance().format(Date.from(now)),
            )
            .put("platform", JSONObject().put("os", "android"))
            .put("settings", getPortableSettings(settings))
            .put("tasks", taskArray)
            .put("presetSections", buildPresetSections(settings))
            .put(
                "summary",
                JSONObject()
                    .put("taskCount", tasks.size)
                    .put("blockedWordCount", jsonArray(settings, "blockedWords").length())
                    .put("greyoutWindowCount", jsonArray(settings, "greyoutSchedule").length())
                    .put(
                        "dailyAllowanceCount",
                        jsonArray(settings, "dailyAllowanceEntries").length(),
                    ),
            )

        // JSON.stringify omits an undefined optional appVersion. JSONObject
        // follows the same contract by not adding the key for null.
        if (appVersion != null) envelope.put("appVersion", appVersion)
        return envelope.toString(2)
    }

    /**
     * Builds the ACTION_CREATE_DOCUMENT request. The ActivityResult launcher
     * owns displaying it; this manager only owns the SAF contract and writing.
     */
    fun createExportDocumentIntent(now: Instant = clock.instant()): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = BACKUP_MIME_TYPE
            putExtra(Intent.EXTRA_TITLE, exportFileName(now))
        }

    /**
     * Writes an already-built backup to the URI returned by
     * ACTION_CREATE_DOCUMENT. The URI may be backed by Downloads, Drive, or
     * another document provider.
     */
    suspend fun exportBackup(
        settings: JSONObject,
        appVersion: String? = null,
        destinationUri: Uri,
    ): BackupFileResult {
        return try {
            val json = buildBackupJson(settings, appVersion)
            appContext.contentResolver.openOutputStream(destinationUri)?.use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
                output.flush()
            } ?: return BackupFileResult.failure("Could not open destination URI for writing.")
            BackupFileResult.success(destinationUri)
        } catch (error: Exception) {
            BackupFileResult.failure(error.toString())
        }
    }

    fun createImportDocumentIntent(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }

    suspend fun pickAndImportBackup(
        sourceUri: Uri,
        callbacks: RestoreCallbacks,
    ): RestoreResult {
        return try {
            val text = appContext.contentResolver.openInputStream(sourceUri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?: return RestoreResult.Error("Could not read the selected backup file.")
            restoreFromJson(text, callbacks)
        } catch (error: Exception) {
            RestoreResult.Error("Could not open file picker result: $error")
        }
    }

    fun parseBackupJson(text: String): BackupParseResult {
        val parsed = try {
            JSONObject(text)
        } catch (_: Exception) {
            return BackupParseResult.Error(
                "File is not valid JSON — is this a genuine .focusflow file?",
            )
        }

        if (parsed.optString("kind", null) != BACKUP_ENVELOPE_KIND) {
            val actual = if (parsed.has("kind")) parsed.opt("kind").toString() else "unknown"
            return BackupParseResult.Error(
                "Unsupported format (expected \"$BACKUP_ENVELOPE_KIND\", got \"$actual\"). " +
                    "Make sure you are importing a .focusflow backup file created by FocusFlow.",
            )
        }
        if (parsed.opt("settings") !is JSONObject) {
            return BackupParseResult.Error(
                "Backup is missing settings — the file may be corrupted.",
            )
        }
        if (parsed.opt("tasks") !is JSONArray) {
            return BackupParseResult.Error("Backup is missing task data.")
        }
        return BackupParseResult.Success(
            BackupEnvelope(
                raw = parsed,
                settings = parsed.getJSONObject("settings"),
                tasks = parsed.getJSONArray("tasks"),
            ),
        )
    }

    fun buildRestoreCallbacks(
        inputs: RestoreCallbackInputs,
        replaceTasks: Boolean = false,
    ): RestoreCallbacks = RestoreCallbacks(
        updateSettings = inputs.updateSettings,
        addTask = inputs.addTask,
        scheduleTasks = inputs.scheduleTasks,
        deleteTask = inputs.deleteTask,
        refreshTasks = inputs.refreshTasks,
        replaceTasks = replaceTasks,
        currentTasks = inputs.currentTasks,
        currentSettings = inputs.currentSettings,
        currentFocusSessionActive = inputs.currentFocusSessionActive,
    )

    suspend fun restoreFromJson(
        text: String,
        callbacks: RestoreCallbacks,
    ): RestoreResult {
        val parsed = parseBackupJson(text)
        if (parsed !is BackupParseResult.Success) return parsed.toRestoreError()
        val envelope = parsed.envelope

        if (callbacks.replaceTasks) {
            val databaseSessionActive = dataSource.hasActiveFocusSession()
            if (callbacks.currentFocusSessionActive || databaseSessionActive) {
                return RestoreResult.Error(
                    "Cannot replace tasks while a Focus Session is running. " +
                        "Stop the current session, then try importing the backup again.",
                )
            }
        }

        val summary = ImportSummary()

        try {
            val merged = mergeObjects(callbacks.currentSettings, envelope.settings)
            val importedMirror = envelope.settings.optBoolean("focusMirrorVpnEnabled", false)
            val currentMirror = callbacks.currentSettings.optBoolean("focusMirrorVpnEnabled", false)
            merged.put("focusMirrorVpnEnabled", importedMirror || currentMirror)
            callbacks.updateSettings(merged)
            summary.settings = true
        } catch (error: Exception) {
            summary.warnings += "Settings could not be restored: $error"
        }

        val existingTasks = runCatching { dataSource.getAllTasks() }
            .getOrElse { callbacks.currentTasks }

        if (callbacks.replaceTasks) {
            existingTasks.forEach { task ->
                runCatching { callbacks.deleteTask(task.optString("id")) }
            }
        }

        val existingIds = if (callbacks.replaceTasks) {
            emptySet()
        } else {
            existingTasks.mapNotNull { it.optString("id").takeIf(String::isNotBlank) }.toSet()
        }
        val importedIds = existingIds.toMutableSet()
        val tasksToSchedule = mutableListOf<JSONObject>()

        for (index in 0 until envelope.tasks.length()) {
            val rawTask = envelope.tasks.opt(index)
            if (rawTask !is JSONObject || rawTask.optString("id").isBlank()) {
                summary.tasksSkipped++
                continue
            }

            val taskId = rawTask.optString("id")
            if (!importedIds.add(taskId)) {
                summary.tasksSkipped++
                continue
            }

            try {
                val imported = copyJson(rawTask)
                val isPastScheduledTask =
                    imported.optString("status") == "scheduled" &&
                        parseInstantOrNull(imported.optString("endTime"))?.toEpochMilli()
                            ?.let { it < clock.millis() } == true
                if (isPastScheduledTask) {
                    imported.put("status", "skipped")
                    imported.put("updatedAt", formatIso(clock.instant()))
                }

                callbacks.addTask(imported, true)
                if (imported.optString("status") == "scheduled") {
                    tasksToSchedule += imported
                }
                summary.tasksImported++
            } catch (error: Exception) {
                summary.tasksSkipped++
                summary.warnings +=
                    "Task \"${rawTask.optString("title", taskId)}\" failed: $error"
            }
        }

        if (tasksToSchedule.isNotEmpty()) {
            try {
                callbacks.scheduleTasks(tasksToSchedule)
            } catch (error: Exception) {
                summary.warnings +=
                    "Some imported task reminders could not be scheduled: $error"
            }
        }

        runCatching { callbacks.refreshTasks() }
        return RestoreResult.Success(summary)
    }

    private fun getPortableSettings(settings: JSONObject): JSONObject {
        val portable = copyJson(settings)
        PORTABLE_OMITTED_KEYS.forEach(portable::remove)
        portable.put(
            "focusMirrorVpnEnabled",
            settings.optBoolean("focusMirrorVpnEnabled", false),
        )
        return portable
    }

    private fun buildPresetSections(settings: JSONObject): JSONArray {
        val allowedApps = jsonArray(settings, "allowedInFocus")
        val standaloneApps = jsonArray(settings, "standaloneBlockPackages")
        val standaloneVpnApps = jsonArray(settings, "standaloneVpnPackages")
        val alwaysOnApps = jsonArray(settings, "alwaysOnPackages")
        val alwaysOnVpnApps = jsonArray(settings, "alwaysOnVpnPackages")
        val allowances = jsonArray(settings, "dailyAllowanceEntries")
        val keywords = jsonArray(settings, "blockedWords")
        val schedules = jsonArray(settings, "greyoutSchedule")
        val blockPresets = jsonArray(settings, "blockPresets")
        val allowedAppPresets = jsonArray(settings, "allowedAppPresets")

        val sections = JSONArray()
        sections.put(
            JSONObject()
                .put("id", "focus-mode")
                .put("name", "Focus Mode")
                .put(
                    "configured",
                    allowedApps.length() > 0 || allowedAppPresets.length() > 0,
                )
                .put("appPackages", allowedApps)
                .put("itemCount", allowedAppPresets.length())
                .put(
                    "details",
                    JSONObject().put("allowedAppPresets", allowedAppPresets),
                ),
        )
        sections.put(
            JSONObject()
                .put("id", "standalone-block")
                .put("name", "Standalone Block")
                .put(
                    "configured",
                    standaloneApps.length() > 0 || standaloneVpnApps.length() > 0,
                )
                .put("appPackages", standaloneApps)
                .put("vpnPackages", standaloneVpnApps)
                .put("details", JSONObject().put("runtimeState", "local-only")),
        )
        sections.put(
            JSONObject()
                .put("id", "always-on")
                .put("name", "Always-On Blocking")
                .put(
                    "configured",
                    alwaysOnApps.length() > 0 || alwaysOnVpnApps.length() > 0,
                )
                .put("appPackages", alwaysOnApps)
                .put("vpnPackages", alwaysOnVpnApps),
        )
        sections.put(
            JSONObject()
                .put("id", "daily-allowance")
                .put("name", "Daily Allowance")
                .put("configured", allowances.length() > 0)
                .put("itemCount", allowances.length())
                .put("details", JSONObject().put("entries", allowances)),
        )
        sections.put(
            JSONObject()
                .put("id", "keyword-blocker")
                .put("name", "Keyword Blocker")
                .put("configured", keywords.length() > 0)
                .put("itemCount", keywords.length())
                .put("details", JSONObject().put("keywords", keywords)),
        )
        sections.put(
            JSONObject()
                .put("id", "block-schedules")
                .put("name", "Block Schedules")
                .put("configured", schedules.length() > 0)
                .put("itemCount", schedules.length())
                .put("details", JSONObject().put("windows", schedules)),
        )
        sections.put(
            JSONObject()
                .put("id", "defense")
                .put("name", "Defense")
                .put("configured", blockPresets.length() > 0)
                .put("itemCount", blockPresets.length())
                .put(
                    "details",
                    JSONObject()
                        .put("blockPresets", blockPresets)
                        .put("overlayQuotes", jsonArray(settings, "overlayQuotes"))
                        .put("overlayWallpaper", settings.optString("overlayWallpaper", "")),
                ),
        )
        return sections
    }

    private fun jsonArray(objectValue: JSONObject, key: String): JSONArray =
        objectValue.optJSONArray(key) ?: JSONArray()

    private fun copyJson(value: JSONObject): JSONObject = JSONObject(value.toString())

    private fun mergeObjects(base: JSONObject, overlay: JSONObject): JSONObject {
        val merged = copyJson(base)
        val keys = overlay.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            merged.put(key, overlay.get(key))
        }
        return merged
    }

    private fun parseInstantOrNull(value: String): Instant? =
        runCatching { Instant.parse(value) }
            .getOrElse {
                runCatching { java.time.OffsetDateTime.parse(value).toInstant() }.getOrNull()
            }

    private fun formatIso(value: Instant): String =
        java.time.format.DateTimeFormatterBuilder()
            .appendInstant(3)
            .toFormatter()
            .format(value)

    private fun exportFileName(now: Instant): String {
        val stamp = formatIso(now)
            .replace(":", "-")
            .replace(".", "-")
            .take(19)
        return "focusflow-$stamp$BACKUP_FILE_EXT"
    }

    companion object {
        const val BACKUP_ENVELOPE_KIND = "FocusFlowBackupV1"
        const val BACKUP_FILE_EXT = ".focusflow"
        const val BACKUP_MIME_TYPE = "application/octet-stream"

        private val PORTABLE_OMITTED_KEYS = setOf(
            "standaloneBlockPackages",
            "standaloneBlockUntil",
            "standaloneVpnPackages",
            "autoCopiedAlwaysOnPackages",
            "focusModeEnabled",
            "pomodoroEnabled",
            "notificationsEnabled",
            "weeklyReportEnabled",
            "launcherEnabled",
            "alwaysOnEnforcementEnabled",
            "aversionDimmerEnabled",
            "aversionVibrateEnabled",
            "aversionSoundEnabled",
            "systemGuardEnabled",
            "blockInstallActionsEnabled",
            "blockYoutubeShortsEnabled",
            "blockInstagramReelsEnabled",
            "vpnBlockEnabled",
            "autoCopyToAlwaysOn",
            "vpnSelfHealEnabled",
            "pinProtectionEnabled",
        )
    }
}

interface BackupDataSource {
    suspend fun getAllTasks(): List<JSONObject>
    suspend fun hasActiveFocusSession(): Boolean
}

data class BackupFileResult(
    val ok: Boolean,
    val uri: Uri? = null,
    val error: String? = null,
) {
    companion object {
        fun success(uri: Uri) = BackupFileResult(ok = true, uri = uri)
        fun failure(error: String) = BackupFileResult(ok = false, error = error)
    }
}

data class BackupEnvelope(
    val raw: JSONObject,
    val settings: JSONObject,
    val tasks: JSONArray,
)

sealed class BackupParseResult {
    data class Success(val envelope: BackupEnvelope) : BackupParseResult()
    data class Error(val message: String) : BackupParseResult()

    fun toRestoreError(): RestoreResult.Error = when (this) {
        is Success -> error("A successful parse cannot be converted to an error.")
        is Error -> RestoreResult.Error(message)
    }
}

data class ImportSummary(
    var settings: Boolean = false,
    var tasksImported: Int = 0,
    var tasksSkipped: Int = 0,
    val warnings: MutableList<String> = mutableListOf(),
)

sealed class RestoreResult {
    data class Success(val summary: ImportSummary) : RestoreResult()
    data class Error(val message: String) : RestoreResult()
}

data class RestoreCallbackInputs(
    val updateSettings: suspend (JSONObject) -> Unit,
    val addTask: suspend (JSONObject, skipAlarms: Boolean) -> Unit,
    val scheduleTasks: suspend (List<JSONObject>) -> Unit,
    val deleteTask: suspend (String) -> Unit,
    val refreshTasks: suspend () -> Unit,
    val currentTasks: List<JSONObject>,
    val currentSettings: JSONObject,
    val currentFocusSessionActive: Boolean = false,
)

data class RestoreCallbacks(
    val updateSettings: suspend (JSONObject) -> Unit,
    val addTask: suspend (JSONObject, skipAlarms: Boolean) -> Unit,
    val scheduleTasks: suspend (List<JSONObject>) -> Unit,
    val deleteTask: suspend (String) -> Unit,
    val refreshTasks: suspend () -> Unit,
    val replaceTasks: Boolean,
    val currentTasks: List<JSONObject>,
    val currentSettings: JSONObject,
    val currentFocusSessionActive: Boolean,
)