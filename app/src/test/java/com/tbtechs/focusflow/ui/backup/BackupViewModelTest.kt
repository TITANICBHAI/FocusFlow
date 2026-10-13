package com.tbtechs.focusflow.ui.backup

import androidx.lifecycle.ViewModelStore
import com.tbtechs.focusflow.data.backup.BackupEnvelope
import com.tbtechs.focusflow.data.backup.BackupParseResult
import com.tbtechs.focusflow.data.backup.BackupSerializer
import com.tbtechs.focusflow.data.backup.RestoreResult
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.Task
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private var store: ViewModelStore? = null

    @After
    fun tearDown() {
        store?.clear()
        store = null
        Dispatchers.resetMain()
    }

    @Test
    fun exportMovesThroughBuildingToSuccessAndCanReset() = runTest {
        val harness = createHarness(testScheduler)
        var writtenJson: String? = null

        harness.viewModel.beginExport("content://backup/export") { writtenJson = it }
        assertEquals(ExportState.Building, harness.viewModel.exportState.value)
        runCurrent()

        val success = harness.viewModel.exportState.value as ExportState.Success
        assertEquals("content://backup/export", success.uri)
        val parsed = BackupSerializer.parseAndValidate(requireNotNull(writtenJson))
        assertTrue(parsed is BackupParseResult.Success)
        assertEquals("1.1.4-test", (parsed as BackupParseResult.Success).envelope.appVersion)

        harness.viewModel.resetExport()
        assertEquals(ExportState.Idle, harness.viewModel.exportState.value)
    }

    @Test
    fun exportFailureIsReportedAndResettable() = runTest {
        val harness = createHarness(testScheduler)
        harness.viewModel.beginExport("content://backup/export") {
            throw IOException("Storage unavailable")
        }
        runCurrent()

        assertEquals(ExportState.Error("Storage unavailable"), harness.viewModel.exportState.value)
        harness.viewModel.resetExport()
        assertEquals(ExportState.Idle, harness.viewModel.exportState.value)
    }

    @Test
    fun validImportCanBeConfirmedAndRefreshesSettingsAfterSuccess() = runTest {
        val harness = createHarness(testScheduler)
        harness.viewModel.beginImport { validBackupJson() }
        assertEquals(ImportState.Reading, harness.viewModel.importState.value)
        runCurrent()

        val pending = harness.viewModel.importState.value as ImportState.PendingConfirm
        assertEquals(BackupSerializer.BACKUP_KIND, pending.envelope.kind)
        harness.operations.restoreResult = RestoreResult.Success(
            tasksImported = 3,
            tasksSkipped = 1,
            warnings = listOf("One duplicate was skipped."),
        )
        harness.viewModel.confirmImport(replaceTasks = true)
        assertEquals(ImportState.Restoring, harness.viewModel.importState.value)
        runCurrent()

        assertEquals(
            ImportState.Success(
                tasksImported = 3,
                tasksSkipped = 1,
                warnings = listOf("One duplicate was skipped."),
            ),
            harness.viewModel.importState.value,
        )
        assertTrue(harness.operations.lastReplaceTasks)
        assertEquals(1, harness.operations.refreshCount)
        harness.viewModel.resetImport()
        assertEquals(ImportState.Idle, harness.viewModel.importState.value)
    }

    @Test
    fun malformedTaskWarningsAppearInPreviewAndSurviveRetry() = runTest {
        val harness = createHarness(testScheduler)
        val parseWarning = "2 malformed task records will be skipped."
        harness.viewModel.beginImport { backupWithMalformedTasks() }
        runCurrent()

        val pending = harness.viewModel.importState.value as ImportState.PendingConfirm
        assertEquals(listOf(parseWarning), pending.warnings)

        harness.operations.restoreResult = RestoreResult.Failure("Temporary restore failure.")
        harness.viewModel.confirmImport(replaceTasks = false)
        runCurrent()
        harness.viewModel.resetImport()
        assertEquals(
            listOf(parseWarning),
            (harness.viewModel.importState.value as ImportState.PendingConfirm).warnings,
        )

        harness.operations.restoreResult = RestoreResult.Success(
            tasksImported = 0,
            tasksSkipped = 0,
            warnings = listOf("Another restore warning."),
        )
        harness.viewModel.confirmImport(replaceTasks = false)
        runCurrent()
        assertEquals(
            listOf(parseWarning, "Another restore warning."),
            (harness.viewModel.importState.value as ImportState.Success).warnings,
        )
    }

    @Test
    fun cancelingPendingImportDiscardsEnvelopeWithoutRestoring() = runTest {
        val harness = createHarness(testScheduler)
        harness.viewModel.beginImport { validBackupJson() }
        runCurrent()
        assertTrue(harness.viewModel.importState.value is ImportState.PendingConfirm)

        harness.viewModel.cancelImport()

        assertEquals(ImportState.Idle, harness.viewModel.importState.value)
        harness.viewModel.confirmImport(replaceTasks = true)
        runCurrent()
        assertFalse(harness.operations.restoreCalled)
    }

    @Test
    fun malformedInputAndRestoreFailureReportErrorsWithoutRefreshingSettings() = runTest {
        val harness = createHarness(testScheduler)
        harness.viewModel.beginImport { "{not json}" }
        runCurrent()
        assertTrue(harness.viewModel.importState.value is ImportState.Error)
        harness.viewModel.resetImport()
        assertEquals(ImportState.Idle, harness.viewModel.importState.value)

        harness.viewModel.beginImport { validBackupJson() }
        runCurrent()
        harness.operations.restoreResult = RestoreResult.Failure("Restore was blocked.")
        harness.viewModel.confirmImport(replaceTasks = false)
        runCurrent()

        assertEquals(
            ImportState.Error("Restore was blocked."),
            harness.viewModel.importState.value,
        )
        assertEquals(0, harness.operations.refreshCount)
        harness.viewModel.resetImport()
        assertTrue(harness.viewModel.importState.value is ImportState.PendingConfirm)
        harness.operations.restoreResult = RestoreResult.Success(0, 0, emptyList())
        harness.viewModel.confirmImport(replaceTasks = false)
        runCurrent()

        assertTrue(harness.viewModel.importState.value is ImportState.Success)
        assertEquals(1, harness.operations.refreshCount)
        harness.viewModel.resetImport()
        assertEquals(ImportState.Idle, harness.viewModel.importState.value)
    }

    @Test
    fun cancelIsIgnoredWhileRestoreIsRunning() = runTest {
        val harness = createHarness(testScheduler)
        var restoreStarted = false
        var restoreCancelled = false
        harness.operations.restoreAction = { _, _, _ ->
            restoreStarted = true
            try {
                awaitCancellation()
            } catch (cancellation: CancellationException) {
                restoreCancelled = true
                throw cancellation
            }
        }
        harness.viewModel.beginImport { validBackupJson() }
        runCurrent()
        harness.viewModel.confirmImport(replaceTasks = false)
        runCurrent()
        assertTrue(restoreStarted)
        assertEquals(ImportState.Restoring, harness.viewModel.importState.value)

        harness.viewModel.cancelImport()
        assertEquals(ImportState.Restoring, harness.viewModel.importState.value)
        assertFalse(restoreCancelled)

        store?.clear()
        store = null
        runCurrent()
        assertTrue(restoreCancelled)
    }

    @Test
    fun cancelingReadLeavesIdleAndDoesNotPublishLateResults() = runTest {
        val harness = createHarness(testScheduler)
        harness.viewModel.beginImport {
            awaitCancellation()
        }
        assertEquals(ImportState.Reading, harness.viewModel.importState.value)
        runCurrent()

        harness.viewModel.cancelImport()
        assertEquals(ImportState.Idle, harness.viewModel.importState.value)
        advanceUntilIdle()
        assertEquals(ImportState.Idle, harness.viewModel.importState.value)
    }

    private fun TestScope.createHarness(
        scheduler: TestCoroutineScheduler,
    ): Harness {
        val dispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(dispatcher)
        val operations = FakeBackupViewModelOperations()
        val viewModel = BackupViewModel(
            operations = operations,
            appVersion = "1.1.4-test",
            ioDispatcher = dispatcher,
        )
        store = ViewModelStore().apply { put("backup", viewModel) }
        return Harness(viewModel, operations)
    }

    private fun validBackupJson(): String = BackupSerializer.serializeToJson(
        BackupSerializer.buildEnvelope(
            settings = AppSettings(),
            tasks = emptyList(),
            appVersion = "test",
        ),
    )

    private fun backupWithMalformedTasks(): String {
        val root = Json.parseToJsonElement(validBackupJson()).jsonObject
        val malformedTasks = JsonArray(
            listOf(
                JsonNull,
                JsonObject(mapOf("id" to JsonPrimitive("incomplete-task"))),
            ),
        )
        return JsonObject(root + ("tasks" to malformedTasks)).toString()
    }

    private data class Harness(
        val viewModel: BackupViewModel,
        val operations: FakeBackupViewModelOperations,
    )

    private class FakeBackupViewModelOperations : BackupViewModelOperations {
        var settings = AppSettings()
        var tasks = emptyList<Task>()
        var restoreResult: RestoreResult = RestoreResult.Success(0, 0, emptyList())
        var restoreCalled = false
        var restoreAction: suspend (
            BackupEnvelope,
            AppSettings,
            Boolean,
        ) -> RestoreResult = { _, _, _ -> restoreResult }
        var lastReplaceTasks = false
        var refreshCount = 0

        override suspend fun readSettings(): AppSettings = settings

        override suspend fun readTasks(): List<Task> = tasks

        override suspend fun restore(
            envelope: BackupEnvelope,
            currentSettings: AppSettings,
            replaceTasks: Boolean,
        ): RestoreResult {
            restoreCalled = true
            lastReplaceTasks = replaceTasks
            return restoreAction(envelope, currentSettings, replaceTasks)
        }

        override suspend fun refreshSettingsFromStore() {
            refreshCount++
        }
    }
}
