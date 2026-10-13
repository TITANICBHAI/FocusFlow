package com.tbtechs.focusflow.ui.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.tbtechs.focusflow.BuildConfig
import com.tbtechs.focusflow.data.backup.BackupEnvelope
import com.tbtechs.focusflow.data.backup.BackupFileManager
import com.tbtechs.focusflow.data.backup.BackupParseResult
import com.tbtechs.focusflow.data.backup.BackupRestoreEngine
import com.tbtechs.focusflow.data.backup.BackupSerializer
import com.tbtechs.focusflow.data.backup.RestoreResult
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
    data class Success(val uri: String) : ExportState
    data class Error(val message: String) : ExportState
}

sealed interface ImportState {
    data object Idle : ImportState
    data object Reading : ImportState
    data class PendingConfirm(
        val envelope: BackupEnvelope,
        val warnings: List<String> = emptyList(),
    ) : ImportState
    data object Restoring : ImportState
    data class Success(
        val tasksImported: Int,
        val tasksSkipped: Int,
        val warnings: List<String>,
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
    ): RestoreResult
    suspend fun refreshSettingsFromStore()
}

class BackupViewModel internal constructor(
    private val operations: BackupViewModelOperations,
    private val appVersion: String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()
    private val _pendingImportEnvelope = MutableStateFlow<BackupEnvelope?>(null)
    val pendingImportEnvelope: StateFlow<BackupEnvelope?> = _pendingImportEnvelope.asStateFlow()
    private var pendingImportWarnings: List<String> = emptyList()

    private var exportJob: Job? = null
    private var importJob: Job? = null

    fun beginExport(contentResolver: ContentResolver, uri: Uri) {
        val uriString = uri.toString()
        beginExport(uriString) { json ->
            BackupFileManager.writeToUri(contentResolver, uri, json)
        }
    }

    internal fun beginExport(uri: String, writeBackup: (String) -> Unit) {
        exportJob?.cancel()
        _exportState.value = ExportState.Building
        exportJob = viewModelScope.launch(ioDispatcher) {
            try {
                val settings = operations.readSettings()
                val tasks = operations.readTasks()
                val envelope = BackupSerializer.buildEnvelope(settings, tasks, appVersion)
                val json = BackupSerializer.serializeToJson(envelope)
                writeBackup(json)
                currentCoroutineContext().ensureActive()
                _exportState.value = ExportState.Success(uri)
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

    fun beginImport(contentResolver: ContentResolver, uri: Uri) {
        beginImport {
            BackupFileManager.readUri(contentResolver, uri)
        }
    }

    internal fun beginImport(readBackup: suspend () -> String) {
        if (_importState.value == ImportState.Restoring) return

        importJob?.cancel()
        _pendingImportEnvelope.value = null
        pendingImportWarnings = emptyList()
        _importState.value = ImportState.Reading
        importJob = viewModelScope.launch(ioDispatcher) {
            try {
                val text = readBackup()
                currentCoroutineContext().ensureActive()
                val result = BackupSerializer.parseAndValidate(text)
                currentCoroutineContext().ensureActive()
                when (result) {
                    is BackupParseResult.Success -> {
                        _pendingImportEnvelope.value = result.envelope
                        pendingImportWarnings = result.warnings
                        _importState.value = ImportState.PendingConfirm(
                            envelope = result.envelope,
                            warnings = result.warnings,
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
            }
        }
    }

    fun confirmImport(replaceTasks: Boolean) {
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
                        _importState.value = ImportState.Success(
                            tasksImported = result.tasksImported,
                            tasksSkipped = result.tasksSkipped,
                            warnings = warnings,
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
        _pendingImportEnvelope.value = null
        pendingImportWarnings = emptyList()
        _importState.value = ImportState.Idle
    }

    fun resetExport() {
        if (_exportState.value is ExportState.Success || _exportState.value is ExportState.Error) {
            exportJob = null
            _exportState.value = ExportState.Idle
        }
    }

    fun resetImport() {
        when (_importState.value) {
            is ImportState.Success -> {
                importJob = null
                _pendingImportEnvelope.value = null
                pendingImportWarnings = emptyList()
                _importState.value = ImportState.Idle
            }
            is ImportState.Error -> {
                importJob = null
                _importState.value = _pendingImportEnvelope.value?.let {
                    ImportState.PendingConfirm(it, pendingImportWarnings)
                } ?: ImportState.Idle
            }
            else -> Unit
        }
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
                        ),
                        refreshSettingsFromStore = settingsViewModel::refreshSettingsFromStore,
                    )
                    return BackupViewModel(
                        operations = operations,
                        appVersion = BuildConfig.VERSION_NAME,
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
    ): RestoreResult = restoreEngine.restore(envelope, currentSettings, replaceTasks)

    override suspend fun refreshSettingsFromStore() {
        refreshSettingsFromStore.invoke()
    }
}
