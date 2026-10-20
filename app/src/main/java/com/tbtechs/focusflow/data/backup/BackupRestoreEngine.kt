package com.tbtechs.focusflow.data.backup

import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.CanonicalTimestamp
import com.tbtechs.focusflow.data.model.Subtask
import com.tbtechs.focusflow.data.model.SubtaskImage
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.data.images.SubtaskImageStore
import com.tbtechs.focusflow.data.repository.FocusSessionRepository
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.data.repository.TaskAlarmReconciler
import com.tbtechs.focusflow.data.repository.TaskRepository
import com.tbtechs.focusflow.data.restore.RestoreGate
import java.time.Instant
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

sealed interface RestoreResult {
    data class Success(
        val tasksImported: Int,
        val tasksSkipped: Int,
        val warnings: List<String>,
        val subtasksImported: Int = 0,
        val imagesStored: Int = 0,
        val tasksEnriched: Int = 0,
    ) : RestoreResult

    data class Failure(val message: String) : RestoreResult
}

/**
 * Narrow ports keep restore orchestration testable without Android or Room.
 * Production dependencies are adapted by [RepositoryBackupRestoreAccess].
 */
internal interface BackupRestoreAccess {
    suspend fun writePreference(key: String, value: LegacyPreferenceValue)
    suspend fun writePortablePreference(key: String, value: BackupPreferenceValue)
    suspend fun refreshRestoredSettings(preferenceKeys: Set<String>): List<String>
    suspend fun getAllTasks(): List<Task>
    suspend fun deleteAllTasks()
    suspend fun insertTask(task: Task)
    suspend fun replaceSubtasks(taskId: String, subtasks: List<Subtask>)
    suspend fun sweepSubtaskImages(): Int
    suspend fun hasActiveFocusSession(): Boolean
    suspend fun reconcileAlarms(reason: String)
}

internal interface BackupRestoreWriteGate {
    suspend fun <T> write(owner: String, block: suspend () -> T): T
}

class BackupRestoreEngine internal constructor(
    private val access: BackupRestoreAccess,
    private val writeGate: BackupRestoreWriteGate,
    private val clock: () -> Instant = Instant::now,
    private val imageStore: SubtaskImageStore? = null,
) {
    constructor(
        settingsRepository: SettingsRepository,
        taskRepository: TaskRepository,
        focusSessionRepository: FocusSessionRepository,
        alarmReconciler: TaskAlarmReconciler,
        restoreGate: RestoreGate,
        imageStore: SubtaskImageStore? = null,
    ) : this(
        access = RepositoryBackupRestoreAccess(
            settingsRepository = settingsRepository,
            taskRepository = taskRepository,
            focusSessionRepository = focusSessionRepository,
            alarmReconciler = alarmReconciler,
        ),
        writeGate = RestoreGateWriter(restoreGate),
        imageStore = imageStore,
    )

    /**
     * Applies portable settings and imports tasks. Replace-mode confirmation is
     * represented by [replaceTasks]; the active-session check and destructive
     * replacement run under the same gate as session/task mutations.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun restore(
        envelope: BackupEnvelope,
        currentSettings: AppSettings,
        replaceTasks: Boolean,
        enrichExistingTasks: Boolean = false,
    ): RestoreResult {
        val preferenceWrites = try {
            val portableSettings = JsonObject(
                envelope.settings.filterKeys { LegacySettingsPolicy.mayMigrateKey(it) },
            )
            val source = LegacySettingsAdapter.parseLegacySettingsJson(portableSettings.toString())
            LegacySettingsAdapter.toSharedPreferencesValues(source)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            return RestoreResult.Failure(
                exception.message ?: "Backup settings could not be read.",
            )
        }
        val additionalPreferenceWrites = try {
            BackupSettingsAdapter.additionalPreferenceWrites(envelope.settings)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            return RestoreResult.Failure(
                exception.message ?: "Backup settings could not be read.",
            )
        }

        val preparedImages = prepareImages(envelope)
        val settingsWarnings = mutableListOf<String>()
        val workResult = try {
            writeGate.write("BackupRestoreEngine.restore") {
                if (replaceTasks && access.hasActiveFocusSession()) {
                    return@write RestoreWorkResult.Blocked(
                        "Cannot replace tasks while a Focus Session is running. " +
                            "Stop the current session first.",
                    )
                }

                writeGate.write("BackupRestoreEngine.settings") {
                    preferenceWrites.forEach { (key, value) ->
                        access.writePreference(key, value)
                    }
                    additionalPreferenceWrites.forEach { (key, value) ->
                        access.writePortablePreference(key, value)
                    }
                    val changedPreferenceKeys = preferenceWrites.keys + additionalPreferenceWrites.keys
                    if (changedPreferenceKeys.isNotEmpty()) {
                        settingsWarnings += access.refreshRestoredSettings(changedPreferenceKeys)
                    }
                }

                if (replaceTasks) {
                    writeGate.write("BackupRestoreEngine.deleteAll") {
                        access.deleteAllTasks()
                    }
                }

                // Read the database snapshot while holding the same mutation
                // gate, so an intervening task insert cannot evade collision checks.
                val existingTasks = if (replaceTasks) emptyList() else access.getAllTasks()
                val existingById = existingTasks.associateBy(Task::id)
                val existingIds = existingById.keys
                val importedIds = mutableSetOf<String>()
                val enrichedIds = mutableSetOf<String>()
                val tasksToSchedule = mutableListOf<Task>()
                val warnings = settingsWarnings.toMutableList()
                warnings += preparedImages.warnings
                var importedCount = 0
                var skippedCount = 0
                var subtasksImportedCount = 0
                var tasksEnrichedCount = 0
                val now = clock()
                val canonicalNow = CanonicalTimestamp.format(now)

                preparedImages.tasks.forEach { task ->
                    if (task.id.isBlank()) {
                        skippedCount++
                        warnings += "Skipped a backup task without an ID."
                        return@forEach
                    }
                    if (task.id in existingIds || task.id in importedIds) {
                        skippedCount++
                        warnings += "Skipped duplicate task ID '${task.id}'."
                        val existing = existingById[task.id]
                        if (enrichExistingTasks && existing != null &&
                            existing.subtasks.isEmpty() && task.subtasks.isNotEmpty() &&
                            enrichedIds.add(task.id)
                        ) {
                            writeGate.write("BackupRestoreEngine.enrichSubtasks") {
                                access.replaceSubtasks(task.id, task.subtasks)
                            }
                            tasksEnrichedCount++
                            subtasksImportedCount += task.subtasks.size
                        }
                        return@forEach
                    }

                    val endInstant = try {
                        Instant.parse(task.endTime)
                    } catch (_: Exception) {
                        skippedCount++
                        warnings += "Skipped task '${task.id}' because its end time is invalid."
                        return@forEach
                    }

                    val taskToInsert = if (
                        task.status == "scheduled" && endInstant.isBefore(now)
                    ) {
                        task.copy(status = "skipped", updatedAt = canonicalNow)
                    } else {
                        task
                    }

                    writeGate.write("BackupRestoreEngine.insertTask") {
                        access.insertTask(taskToInsert)
                    }
                    importedIds += task.id
                    importedCount++
                    subtasksImportedCount += task.subtasks.size
                    if (taskToInsert.status == "scheduled") {
                        tasksToSchedule += taskToInsert
                    }
                }

                // Reconcile once after the batch. Replace mode also needs a
                // pass when it imported no future schedules, to clear alarms
                // belonging to tasks it deleted.
                if (replaceTasks || tasksToSchedule.isNotEmpty() ||
                    "task_reminders_enabled" in additionalPreferenceWrites
                ) {
                    writeGate.write("BackupRestoreEngine.reconcileAlarms") {
                        access.reconcileAlarms(reason = "backup-restore")
                    }
                }
                if (replaceTasks) {
                    writeGate.write("BackupRestoreEngine.sweepImages") {
                        access.sweepSubtaskImages()
                    }
                }

                RestoreWorkResult.Completed(
                    importedCount = importedCount,
                    skippedCount = skippedCount,
                    warnings = warnings,
                    subtasksImportedCount = subtasksImportedCount,
                    imagesStoredCount = preparedImages.storedCount,
                    tasksEnrichedCount = tasksEnrichedCount,
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            return RestoreResult.Failure(
                exception.message ?: "Backup restore failed.",
            )
        }

        return when (workResult) {
            is RestoreWorkResult.Blocked -> RestoreResult.Failure(workResult.message)
            is RestoreWorkResult.Completed -> RestoreResult.Success(
                tasksImported = workResult.importedCount,
                tasksSkipped = workResult.skippedCount,
                warnings = workResult.warnings,
                subtasksImported = workResult.subtasksImportedCount,
                imagesStored = workResult.imagesStoredCount,
                tasksEnriched = workResult.tasksEnrichedCount,
            )
        }
    }

    private data class PreparedImages(
        val tasks: List<Task>,
        val warnings: List<String>,
        val storedCount: Int,
    )

    /**
     * Decode and ingest file assets before entering RestoreGate. File IDs never
     * become paths; only the image store's content hash is persisted.
     */
    private fun prepareImages(envelope: BackupEnvelope): PreparedImages {
        val referenced = SubtaskImageStore.referencedAssetIds(envelope.tasks)
        if (referenced.isEmpty()) return PreparedImages(envelope.tasks, emptyList(), 0)
        val store = imageStore
        val remapped = mutableMapOf<String, String>()
        var totalBytes = 0
        var failed = 0
        val idsToProcess = referenced.sorted().take(BackupJsonLimits.MAX_ASSETS)
        if (referenced.size > BackupJsonLimits.MAX_ASSETS) {
            failed += referenced.size - BackupJsonLimits.MAX_ASSETS
        }
        idsToProcess.forEach { fileId ->
            val asset = envelope.assets[fileId]
            if (asset == null || asset.data.length > BackupJsonLimits.MAX_ASSET_DATA_CHARS) {
                failed++
                return@forEach
            }
            val bytes = try {
                Base64.getDecoder().decode(asset.data)
            } catch (_: IllegalArgumentException) {
                failed++
                return@forEach
            }
            if (bytes.isEmpty() ||
                bytes.size > BackupJsonLimits.MAX_ASSET_TOTAL_BYTES - totalBytes
            ) {
                failed++
                return@forEach
            }
            totalBytes += bytes.size
            val storedId = store?.ingest(bytes)?.getOrNull()
            if (storedId == null) {
                failed++
            } else {
                remapped[fileId] = storedId
            }
        }
        val tasks = envelope.tasks.map { task ->
            task.copy(
                subtasks = task.subtasks.map { subtask ->
                    subtask.copy(
                        image = remapImage(subtask.image, remapped),
                        steps = subtask.steps.map { step ->
                            step.copy(image = remapImage(step.image, remapped))
                        },
                    )
                },
            )
        }
        return PreparedImages(
            tasks = tasks,
            warnings = if (failed == 0) emptyList() else {
                listOf("$failed images could not be decoded or stored and were removed.")
            },
            storedCount = remapped.values.toSet().size,
        )
    }

    private fun remapImage(
        image: SubtaskImage?,
        ids: Map<String, String>,
    ): SubtaskImage? = image?.let { source ->
        ids[source.asset]?.let { newId -> source.copy(asset = newId) }
    }
}

private sealed interface RestoreWorkResult {
    data class Blocked(val message: String) : RestoreWorkResult
    data class Completed(
        val importedCount: Int,
        val skippedCount: Int,
        val warnings: List<String>,
        val subtasksImportedCount: Int,
        val imagesStoredCount: Int,
        val tasksEnrichedCount: Int,
    ) : RestoreWorkResult
}

private class RestoreGateWriter(
    private val restoreGate: RestoreGate,
) : BackupRestoreWriteGate {
    override suspend fun <T> write(owner: String, block: suspend () -> T): T =
        restoreGate.write(owner, block)
}

private class RepositoryBackupRestoreAccess(
    private val settingsRepository: SettingsRepository,
    private val taskRepository: TaskRepository,
    private val focusSessionRepository: FocusSessionRepository,
    private val alarmReconciler: TaskAlarmReconciler,
) : BackupRestoreAccess {
    override suspend fun writePreference(key: String, value: LegacyPreferenceValue) {
        when (value) {
            is LegacyPreferenceValue.StringValue ->
                settingsRepository.putString(key, value.value)
            is LegacyPreferenceValue.BooleanValue ->
                settingsRepository.putBoolean(key, value.value)
            is LegacyPreferenceValue.IntValue ->
                settingsRepository.putInt(key, value.value)
        }
    }

    override suspend fun writePortablePreference(key: String, value: BackupPreferenceValue) {
        when (value) {
            is BackupPreferenceValue.StringValue -> settingsRepository.putString(key, value.value)
            is BackupPreferenceValue.BooleanValue -> settingsRepository.putBoolean(key, value.value)
            is BackupPreferenceValue.IntValue -> settingsRepository.putInt(key, value.value)
            is BackupPreferenceValue.FloatValue -> settingsRepository.putFloat(key, value.value)
            BackupPreferenceValue.Remove -> settingsRepository.removePreference(key)
        }
    }

    override suspend fun refreshRestoredSettings(preferenceKeys: Set<String>): List<String> =
        settingsRepository.refreshBackupSettingSideEffects(preferenceKeys)

    override suspend fun getAllTasks(): List<Task> = taskRepository.getAllTasks()

    override suspend fun deleteAllTasks() {
        taskRepository.deleteAllTasks()
    }

    override suspend fun insertTask(task: Task) {
        taskRepository.insertTask(task)
    }

    override suspend fun replaceSubtasks(taskId: String, subtasks: List<Subtask>) {
        taskRepository.replaceSubtasks(taskId, subtasks)
    }

    override suspend fun sweepSubtaskImages(): Int =
        taskRepository.sweepOrphanedSubtaskImages()

    override suspend fun hasActiveFocusSession(): Boolean =
        focusSessionRepository.getActiveFocusSession() != null

    override suspend fun reconcileAlarms(reason: String) {
        alarmReconciler.reconcile(reason)
    }
}
