package com.tbtechs.focusflow.ui.backup

import android.content.Context
import android.net.Uri
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.data.repository.BackupDataSource
import com.tbtechs.focusflow.data.repository.BackupFileResult
import com.tbtechs.focusflow.data.repository.BackupManager
import com.tbtechs.focusflow.data.repository.FocusSessionRepository
import com.tbtechs.focusflow.data.repository.RestoreCallbackInputs
import com.tbtechs.focusflow.data.repository.RestoreResult
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.data.repository.TaskRepository
import com.tbtechs.focusflow.ui.SettingsViewModel
import org.json.JSONArray
import org.json.JSONObject

/**
 * Activity-hosted bridge for the SAF backup contract.
 *
 * The Activity owns document launchers; this class owns the portable JSON
 * mapping and repository callbacks so Settings/Profile do not each implement a
 * slightly different restore path.
 */
class BackupCoordinator(
    context: Context,
    private val taskRepository: TaskRepository,
    private val focusSessionRepository: FocusSessionRepository,
    private val settingsRepository: SettingsRepository,
    private val settingsViewModel: SettingsViewModel,
) {
    private val manager = BackupManager(
        context = context,
        dataSource = object : BackupDataSource {
            override suspend fun getAllTasks(): List<JSONObject> =
                taskRepository.getAllTasks().map(taskRepository::taskToBackupJson)

            override suspend fun hasActiveFocusSession(): Boolean =
                focusSessionRepository.getActiveFocusSession()?.isActive == true
        },
    )

    fun createExportIntent() = manager.createExportDocumentIntent()

    fun createImportIntent() = manager.createImportDocumentIntent()

    suspend fun export(settings: AppSettings, destination: Uri): BackupFileResult =
        manager.exportBackup(
            settings = settings.toBackupJson(),
            destinationUri = destination,
        )

    suspend fun import(
        source: Uri,
        replaceTasks: Boolean,
        currentSettings: AppSettings,
        currentFocusActive: Boolean,
    ): RestoreResult {
        val currentTasks = taskRepository.getAllTasks()
        val callbacks = RestoreCallbackInputs(
            updateSettings = { imported ->
                settingsViewModel.updateSettings(
                    currentSettings.mergePortableBackup(imported),
                )
            },
            addTask = { rawTask, _ ->
                val task = taskRepository.taskFromBackupJson(rawTask)
                    ?: error("Backup task ${rawTask.optString("id")} is invalid.")
                taskRepository.insertTask(task)
            },
            scheduleTasks = { /* Imported reminders remain persisted with their tasks. */ },
            deleteTask = taskRepository::deleteTask,
            refreshTasks = { /* Room's observeAllTasks flow refreshes automatically. */ },
            currentTasks = currentTasks.map(taskRepository::taskToBackupJson),
            currentSettings = currentSettings.toBackupJson(),
            currentFocusSessionActive = currentFocusActive,
        )

        return manager.pickAndImportBackup(
            sourceUri = source,
            callbacks = manager.buildRestoreCallbacks(callbacks, replaceTasks),
        )
    }

    private fun AppSettings.toBackupJson(): JSONObject = JSONObject().apply {
        put("allowedInFocus", JSONArray(allowedFocusPackages))
        put("blockedWords", JSONArray(blockedWords))
        put("alwaysOnPackages", JSONArray(alwaysBlockPackages))
        put("alwaysOnEnforcementEnabled", alwaysBlockEnabled)
        put("dailyAllowanceEntries", dailyAllowanceConfigJson?.let(::JSONArray) ?: JSONArray())
        put("greyoutSchedule", JSONArray().also { array ->
            recurringBlockSchedules.forEach { schedule ->
                array.put(
                    JSONObject()
                        .put("id", schedule.id)
                        .put("packages", JSONArray(schedule.packages))
                        .put("startHour", schedule.startHour)
                        .put("endHour", schedule.endHour)
                        .put("daysOfWeek", JSONArray(schedule.daysOfWeek))
                        .put("enabled", schedule.enabled),
                )
            }
        })
        put("vpnBlockEnabled", networkBlockEnabled)
        put("systemGuardEnabled", systemGuardEnabled)
        put("morningDigestEnabled", morningDigestEnabled)
        put("achievementNotificationsEnabled", achievementNotificationsEnabled)
        put("patternInsightNotificationsEnabled", patternInsightNotificationsEnabled)
        put("rescheduleNotificationsEnabled", rescheduleNotificationsEnabled)
        put("blockSuggestionEnabled", blockSuggestionEnabled)
        put("weekAheadEnabled", weekAheadEnabled)
        put("temptationSpikeEnabled", temptationSpikeEnabled)
        put("temptationSpikeThreshold", temptationSpikeThreshold)
        put("bedTime", bedTime)
        put("productiveWindowNudgeEnabled", productiveWindowNudgeEnabled)
        put("taskRemindersEnabled", taskRemindersEnabled)
        put("defaultDurationMinutes", defaultDurationMinutes)
        put("autoFocusEnabled", autoFocusEnabled)
        put("pomodoroEnabled", pomodoroEnabled)
        put("pomodoroWorkMinutes", pomodoroWorkMinutes)
        put("pomodoroBreakMinutes", pomodoroBreakMinutes)
    }

    private fun AppSettings.mergePortableBackup(imported: JSONObject): AppSettings =
        copy(
            blockedWords = imported.stringList("blockedWords", blockedWords),
            alwaysBlockPackages = imported.stringList("alwaysOnPackages", alwaysBlockPackages),
            alwaysBlockEnabled = imported.optBoolean(
                "alwaysOnEnforcementEnabled",
                alwaysBlockEnabled,
            ),
            dailyAllowanceConfigJson = imported.optJSONArray("dailyAllowanceEntries")
                ?.toString()
                ?: dailyAllowanceConfigJson,
            recurringBlockSchedules = imported.optJSONArray("greyoutSchedule")
                ?.let(::parseSchedules)
                ?: recurringBlockSchedules,
            networkBlockEnabled = imported.optBoolean("vpnBlockEnabled", networkBlockEnabled),
            systemGuardEnabled = imported.optBoolean("systemGuardEnabled", systemGuardEnabled),
            morningDigestEnabled = imported.optBoolean("morningDigestEnabled", morningDigestEnabled),
            achievementNotificationsEnabled = imported.optBoolean(
                "achievementNotificationsEnabled",
                achievementNotificationsEnabled,
            ),
            patternInsightNotificationsEnabled = imported.optBoolean(
                "patternInsightNotificationsEnabled",
                patternInsightNotificationsEnabled,
            ),
            rescheduleNotificationsEnabled = imported.optBoolean(
                "rescheduleNotificationsEnabled",
                rescheduleNotificationsEnabled,
            ),
            blockSuggestionEnabled = imported.optBoolean(
                "blockSuggestionEnabled",
                blockSuggestionEnabled,
            ),
            weekAheadEnabled = imported.optBoolean("weekAheadEnabled", weekAheadEnabled),
            temptationSpikeEnabled = imported.optBoolean(
                "temptationSpikeEnabled",
                temptationSpikeEnabled,
            ),
            temptationSpikeThreshold = imported.optInt(
                "temptationSpikeThreshold",
                temptationSpikeThreshold,
            ),
            bedTime = imported.optString("bedTime", bedTime),
            productiveWindowNudgeEnabled = imported.optBoolean(
                "productiveWindowNudgeEnabled",
                productiveWindowNudgeEnabled,
            ),
            taskRemindersEnabled = imported.optBoolean("taskRemindersEnabled", taskRemindersEnabled),
            defaultDurationMinutes = imported.optInt(
                "defaultDurationMinutes",
                defaultDurationMinutes,
            ),
            autoFocusEnabled = imported.optBoolean("autoFocusEnabled", autoFocusEnabled),
            pomodoroEnabled = imported.optBoolean("pomodoroEnabled", pomodoroEnabled),
            pomodoroWorkMinutes = imported.optInt("pomodoroWorkMinutes", pomodoroWorkMinutes),
            pomodoroBreakMinutes = imported.optInt("pomodoroBreakMinutes", pomodoroBreakMinutes),
        )

    private fun JSONObject.stringList(key: String, fallback: List<String>): List<String> =
        optJSONArray(key)?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        } ?: fallback

    private fun parseSchedules(array: JSONArray) =
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            com.tbtechs.focusflow.data.model.RecurringBlockSchedule(
                id = item.optString("id").ifBlank { "imported-$index" },
                packages = item.stringList("packages"),
                startHour = item.optInt("startHour", 0).coerceIn(0, 23),
                endHour = item.optInt("endHour", 0).coerceIn(0, 23),
                daysOfWeek = item.optJSONArray("daysOfWeek")
                    ?.let { days -> (0 until days.length()).map { days.optInt(it) } }
                    ?: emptyList(),
                enabled = item.optBoolean("enabled", true),
            )
        }

    private fun JSONObject.stringList(key: String): List<String> =
        optJSONArray(key)?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        } ?: emptyList()
}