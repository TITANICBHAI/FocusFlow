package com.tbtechs.focusflow.ui.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.tbtechs.focusflow.BuildConfig
import com.tbtechs.focusflow.data.backup.AssetByteSource
import com.tbtechs.focusflow.data.backup.BackupBundleWriter
import com.tbtechs.focusflow.data.backup.BackupAsset
import com.tbtechs.focusflow.data.backup.BackupEnvelope
import com.tbtechs.focusflow.data.backup.BackupFileManager
import com.tbtechs.focusflow.data.backup.BackupParseResult
import com.tbtechs.focusflow.data.backup.BackupRestoreEngine
import com.tbtechs.focusflow.data.backup.BackupSource
import com.tbtechs.focusflow.data.backup.BackupSourceDocument
import com.tbtechs.focusflow.data.backup.BackupSerializer
import com.tbtechs.focusflow.data.backup.BundleExportPreparation
import com.tbtechs.focusflow.data.backup.BundleExportPlan
import com.tbtechs.focusflow.data.backup.CombinedAssetByteSource
import com.tbtechs.focusflow.data.backup.EmptyAssetByteSource
import com.tbtechs.focusflow.data.backup.InlineAssetByteSource
import com.tbtechs.focusflow.data.backup.SubtaskAssetExporter
import com.tbtechs.focusflow.data.backup.RestoreResult
import com.tbtechs.focusflow.data.images.SubtaskImageStore
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.data.repository.TaskRepository
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.ui.SettingsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ExportState {
    data object Idle : ExportState
    data object Building : ExportState
    data class Success(
        val uri: String,
        val warnings: List<String> = emptyList(),
        val containerDescription: String = "JSON backup",
    ) : ExportState
    data class TooLarge(val uri: String, val projectedBytes: Long) : ExportState
    data class Error(val message: String) : ExportState
}

sealed interface ImportState {
    data object Idle : ImportState
    data object Reading : ImportState
    data class PendingConfirm(
        val envelope: BackupEnvelope,
        val warnings: List<String> = emptyList(),
        val subtaskEnrichmentCandidates: Int = 0,
        val isBundle: Boolean = false,
        val bundleImageCount: Int = 0,
    ) : ImportState
    data object Restoring : ImportState
    data class Success(
        val tasksImported: Int,
        val tasksSkipped: Int,
        val warnings: List<String>,
        val subtasksImported: Int = 0,
        val imagesStored: Int = 0,
        val tasksEnriched: Int = 0,
    ) : ImportState
    data class Error(val message: String) : ImportState
}

internal interface BackupViewModelOperations {
    suspend fun readSettings(): AppSettings
    suspend fun readTasks(): List<Task>
    suspend fun restore(
        envelope: BackupEnvelope,
        currentSettings: AppSettings,
        replaceTasks: Boolean,
        enrichExistingTasks: Boolean,
        assetByteSource: AssetByteSource?,
    ): RestoreResult
    suspend fun refreshSettingsFromStore()
}

class BackupViewModel internal constructor(
    private val operations: BackupViewModelOperations,
    private val appVersion: String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val assetExporter: SubtaskAssetExporter? = null,
    private val bundleWriter: BackupBundleWriter? = null,
) : ViewModel() {
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()
    private val _pendingImportEnvelope = MutableStateFlow<BackupEnvelope?>(null)
    val pendingImportEnvelope: StateFlow<BackupEnvelope?> = _pendingImportEnvelope.asStateFlow()
    private var pendingImportWarnings: List<String> = emptyList()
    private var pendingEnrichmentCandidates = 0
    private var pendingSource: BackupSourceDocument? = null

    private var exportJob: Job? = null
    private var importJob: Job? = null

    fun beginExport(contentResolver: ContentResolver, uri: Uri) {
        val uriString = uri.toString()
        startExport(
            uri = uriString,
            writeJson = { json -> BackupFileManager.writeToUri(contentResolver, uri, json) },
            writeBundle = { plan ->
                val writer = requireNotNull(bundleWriter) {
                    "Subtask image storage is unavailable for export."
                }
                BackupFileManager.writeBundleToUri(contentResolver, uri, writer, plan)
            },
        )
    }

    internal fun beginExport(uri: String, writeBackup: (String) -> Unit) {
        startExport(uri = uri, writeJson = writeBackup, writeBundle = null)
    }

    fun exportWithoutImages(contentResolver: ContentResolver, uriString: String) {
        val uri = Uri.parse(uriString)
        startExport(
            uri = uriString,
            writeJson = { json -> BackupFileManager.writeToUri(contentResolver, uri, json) },
            writeBundle = null,
            stripImages = true,
        )
    }

    private fun startExport(
        uri: String,
        writeJson: (String) -> Unit,
        writeBundle: ((BundleExportPlan) -> Unit)?,
        stripImages: Boolean = false,
    ) {
        exportJob?.cancel()
        _exportState.value = ExportState.Building
        exportJob = viewModelScope.launch(ioDispatcher) {
            try {
                val settings = operations.readSettings()
                val tasks = operations.readTasks()
                val imageIds = if (stripImages) {
                    emptySet()
                } else {
                    SubtaskImageStore.referencedAssetIds(tasks)
                }
                if (imageIds.isEmpty()) {
                    val exportTasks = if (stripImages) tasks.withoutImages() else tasks
                    val json = BackupSerializer.serializeToJson(
                        BackupSerializer.buildEnvelope(settings, exportTasks, appVersion),
                    )
                    require(json.toByteArray(Charsets.UTF_8).size <=
                        com.tbtechs.focusflow.data.backup.BackupJsonLimits.MAX_FILE_BYTES
                    ) {
                        "Backup exceeds the 8 MiB file limit."
                    }
                    writeJson(json)
                    currentCoroutineContext().ensureActive()
                    _exportState.value = ExportState.Success(
                        uri = uri,
                        warnings = emptyList(),
                        containerDescription = "JSON file (no images)",
                    )
                } else {
                    val exporter = assetExporter
                        ?: error("Subtask image storage is unavailable for export.")
                    val writer = bundleWriter
                        ?: error("Subtask image storage is unavailable for export.")
                    val bundleAssets = imageIds.associateWith { assetId ->
                        BackupAsset(
                            mime = "image/webp",
                            file = "assets/$assetId.webp",
                            sha256 = assetId,
                        )
                    }
                    val envelope = BackupSerializer.buildEnvelope(
                        settings = settings,
                        tasks = tasks,
                        appVersion = appVersion,
                        assets = bundleAssets,
                    )
                    val manifest = BackupSerializer.serializeToJson(envelope)
                    when (val preparation = writer.prepare(
                        manifestJson = manifest,
                        assetIds = exporter.referencedAssetIds(tasks),
                    )) {
                        is BundleExportPreparation.Ready -> {
                            val write = writeBundle
                                ?: error("Bundle export is unavailable for this destination.")
                            write(preparation.plan)
                            currentCoroutineContext().ensureActive()
                            _exportState.value = ExportState.Success(
                                uri = uri,
                                warnings = emptyList(),
                                containerDescription = "ZIP bundle (includes images)",
                            )
                        }
                        is BundleExportPreparation.TooLarge -> {
                            currentCoroutineContext().ensureActive()
                            _exportState.value = ExportState.TooLarge(uri, preparation.projectedBytes)
                        }
                        is BundleExportPreparation.Failure -> error(preparation.message)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                currentCoroutineContext().ensureActive()
                _exportState.value = ExportState.Error(
                    exception.message?.takeIf(String::isNotBlank) ?: "Backup export failed.",
                )
            }
        }
    }

    fun beginImport(contentResolver: ContentResolver, uri: Uri, cacheDir: java.io.File) {
        beginImportSource {
            BackupFileManager.readSource(contentResolver, cacheDir, uri)
        }
    }

    internal fun beginImport(readBackup: suspend () -> String) {
        beginImportSource { BackupSource.jsonDocument(readBackup()) }
    }

    private fun beginImportSource(readSource: suspend () -> BackupSourceDocument) {
        if (_importState.value == ImportState.Restoring) return

        importJob?.cancel()
        closePendingSource()
        _pendingImportEnvelope.value = null
        pendingImportWarnings = emptyList()
        pendingEnrichmentCandidates = 0
        _importState.value = ImportState.Reading
        importJob = viewModelScope.launch(ioDispatcher) {
            var openedSource: BackupSourceDocument? = null
            try {
                openedSource = readSource()
                currentCoroutineContext().ensureActive()
                val document = requireNotNull(openedSource)
                val result = BackupSerializer.parseAndValidate(
                    jsonText = document.manifestJson,
                    isBundle = document.isBundle,
                    bundleAssetIds = document.bundleAssetIds,
                    sourceWarnings = document.warnings,
                )
                currentCoroutineContext().ensureActive()
                when (result) {
                    is BackupParseResult.Success -> {
                        _pendingImportEnvelope.value = result.envelope
                        pendingImportWarnings = result.warnings
                        pendingSource = document
                        openedSource = null
                        val existingById = operations.readTasks().associateBy(Task::id)
                        val enrichmentCandidates = result.envelope.tasks.count { incoming ->
                            incoming.subtasks.isNotEmpty() &&
                            existingById[incoming.id]?.let { it.subtasks.isEmpty() } == true
                        }
                        pendingEnrichmentCandidates = enrichmentCandidates
                        _importState.value = ImportState.PendingConfirm(
                            envelope = result.envelope,
                            warnings = result.warnings,
                            subtaskEnrichmentCandidates = enrichmentCandidates,
                            isBundle = document.isBundle,
                            bundleImageCount = if (document.isBundle) {
                                SubtaskImageStore.referencedAssetIds(result.envelope.tasks)
                                    .count { it in result.envelope.assets }
                            } else {
                                0
                            },
                        )
                    }
                    is BackupParseResult.Failure ->
                        _importState.value = ImportState.Error(result.message)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                currentCoroutineContext().ensureActive()
                _importState.value = ImportState.Error(
                    exception.message?.takeIf(String::isNotBlank) ?: "Backup could not be read.",
                )
            } finally {
                openedSource?.close()
            }
        }
    }

    fun confirmImport(
        replaceTasks: Boolean,
        enrichExistingTasks: Boolean = true,
    ) {
        val pending = _importState.value as? ImportState.PendingConfirm ?: return
        _importState.value = ImportState.Restoring
        importJob = viewModelScope.launch(ioDispatcher) {
            try {
                val currentSettings = operations.readSettings()
                when (
                    val result = operations.restore(
                        envelope = pending.envelope,
                        currentSettings = currentSettings,
                        replaceTasks = replaceTasks,
                        enrichExistingTasks = enrichExistingTasks,
                        assetByteSource = CombinedAssetByteSource(
                            primary = pendingSource?.assetByteSource
                                ?: EmptyAssetByteSource,
                            inline = InlineAssetByteSource(pending.envelope.assets),
                        ),
                    )
                ) {
                    is RestoreResult.Failure -> {
                        _importState.value = ImportState.Error(result.message)
                    }
                    is RestoreResult.Success -> {
                        val warnings = (pending.warnings + result.warnings).toMutableList()
                        try {
                            operations.refreshSettingsFromStore()
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (exception: Exception) {
                            val detail = exception.message?.takeIf(String::isNotBlank)
                            warnings += if (detail == null) {
                                "Backup restored, but settings could not be refreshed."
                            } else {
                                "Backup restored, but settings could not be refreshed: $detail"
                            }
                        }
                        _pendingImportEnvelope.value = null
                        pendingImportWarnings = emptyList()
                        closePendingSource()
                        _importState.value = ImportState.Success(
                            tasksImported = result.tasksImported,
                            tasksSkipped = result.tasksSkipped,
                            warnings = warnings,
                            subtasksImported = result.subtasksImported,
                            imagesStored = result.imagesStored,
                            tasksEnriched = result.tasksEnriched,
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                currentCoroutineContext().ensureActive()
                _importState.value = ImportState.Error(
                    exception.message?.takeIf(String::isNotBlank) ?: "Backup restore failed.",
                )
            }
        }
    }

    fun cancelImport() {
        if (_importState.value == ImportState.Restoring) return
        importJob?.cancel()
        importJob = null
        closePendingSource()
        _pendingImportEnvelope.value = null
        pendingImportWarnings = emptyList()
        pendingEnrichmentCandidates = 0
        _importState.value = ImportState.Idle
    }

    fun resetExport() {
        if (_exportState.value is ExportState.Success ||
            _exportState.value is ExportState.Error ||
            _exportState.value is ExportState.TooLarge
        ) {
            exportJob = null
            _exportState.value = ExportState.Idle
        }
    }

    fun resetImport() {
        when (_importState.value) {
            is ImportState.Success -> {
                importJob = null
                _pendingImportEnvelope.value = null
                closePendingSource()
                pendingImportWarnings = emptyList()
                pendingEnrichmentCandidates = 0
                _importState.value = ImportState.Idle
            }
            is ImportState.Error -> {
                importJob = null
                _importState.value = _pendingImportEnvelope.value?.let {
                    ImportState.PendingConfirm(
                        envelope = it,
                        warnings = pendingImportWarnings,
                        subtaskEnrichmentCandidates = pendingEnrichmentCandidates,
                        isBundle = pendingSource?.isBundle == true,
                        bundleImageCount = if (pendingSource?.isBundle == true) {
                            SubtaskImageStore.referencedAssetIds(it.tasks).count { id ->
                                id in it.assets
                            }
                        } else {
                            0
                        },
                    )
                } ?: ImportState.Idle
            }
            else -> Unit
        }
    }

    override fun onCleared() {
        closePendingSource()
        super.onCleared()
    }

    private fun closePendingSource() {
        pendingSource?.close()
        pendingSource = null
    }

    companion object {
        fun factory(settingsViewModel: SettingsViewModel): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (!modelClass.isAssignableFrom(BackupViewModel::class.java)) {
                        throw IllegalArgumentException(
                            "Unsupported ViewModel class: ${modelClass.name}",
                        )
                    }
                    val operations = RepositoryBackupViewModelOperations(
                        settingsRepository = AppModule.settingsRepository,
                        taskRepository = AppModule.taskRepository,
                        restoreEngine = BackupRestoreEngine(
                            settingsRepository = AppModule.settingsRepository,
                            taskRepository = AppModule.taskRepository,
                            focusSessionRepository = AppModule.focusSessionRepository,
                            alarmReconciler = AppModule.taskAlarmReconciler,
                            restoreGate = AppModule.restoreGate,
                            imageStore = AppModule.subtaskImageStore,
                        ),
                        refreshSettingsFromStore = settingsViewModel::refreshSettingsFromStore,
                    )
                    return BackupViewModel(
                        operations = operations,
                        appVersion = BuildConfig.VERSION_NAME,
                        assetExporter = SubtaskAssetExporter(),
                        bundleWriter = BackupBundleWriter(AppModule.subtaskImageStore),
                    ) as T
                }

                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T = create(modelClass)
            }
    }
}

private class RepositoryBackupViewModelOperations(
    private val settingsRepository: SettingsRepository,
    private val taskRepository: TaskRepository,
    private val restoreEngine: BackupRestoreEngine,
    private val refreshSettingsFromStore: suspend () -> Unit,
) : BackupViewModelOperations {
    override suspend fun readSettings(): AppSettings = settingsRepository.readAppSettings()

    override suspend fun readTasks(): List<Task> = taskRepository.getAllTasks()

    override suspend fun restore(
        envelope: BackupEnvelope,
        currentSettings: AppSettings,
        replaceTasks: Boolean,
        enrichExistingTasks: Boolean,
        assetByteSource: AssetByteSource?,
    ): RestoreResult = restoreEngine.restore(
        envelope = envelope,
        currentSettings = currentSettings,
        replaceTasks = replaceTasks,
        enrichExistingTasks = enrichExistingTasks,
        assetByteSource = assetByteSource,
    )

    override suspend fun refreshSettingsFromStore() {
        refreshSettingsFromStore.invoke()
    }
}

private fun List<Task>.withoutImages(): List<Task> =
    map { task ->
        task.copy(
            subtasks = task.subtasks.map { subtask ->
                subtask.copy(
                    image = null,
                    steps = subtask.steps.map { step -> step.copy(image = null) },
                )
            },
        )
    }
