package com.tbtechs.focusflow.data.backup

import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.CanonicalTimestamp
import com.tbtechs.focusflow.data.model.Subtask
import com.tbtechs.focusflow.data.model.SubtaskImage
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.data.images.ImageBounds
import com.tbtechs.focusflow.data.images.ImageCodec
import com.tbtechs.focusflow.data.images.SubtaskImageStore
import com.tbtechs.focusflow.data.images.SubtaskImageStoreTest
import java.nio.file.Files
import java.time.Instant
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreEngineTest {
    private val fixedNow = Instant.parse("2026-10-08T10:00:00Z")

    @Test
    fun mergeUsesDatabaseIdsAndSkipsDuplicatesWithinBackup() = runTest {
        val gate = RecordingGate()
        val access = FakeAccess(
            gate = gate,
            tasks = mutableListOf(task(id = "existing", status = "completed")),
            activeSession = true,
        )
        val engine = engine(access, gate)

        val result = engine.restore(
            envelope(
                task(id = "existing"),
                task(id = "new"),
                task(id = "new"),
            ),
            currentSettings = AppSettings(),
            replaceTasks = false,
        )

        assertEquals(1, success(result).tasksImported)
        assertEquals(2, success(result).tasksSkipped)
        assertEquals(listOf("existing", "new"), access.tasks.map { it.id })
        assertEquals(0, access.deleteAllCalls)
        assertEquals(listOf("backup-restore"), access.reconcileReasons)
        assertEquals(1, access.events.count { it.startsWith("insert:") })
        assertEquals(1, access.imageSweepCalls)
    }

    @Test
    fun replaceDeletesBeforeInsertingAndReconcilesOnceAfterTheBatch() = runTest {
        val gate = RecordingGate()
        val access = FakeAccess(
            gate = gate,
            tasks = mutableListOf(task(id = "old", status = "scheduled")),
        )
        val engine = engine(access, gate)

        val result = engine.restore(
            envelope(task(id = "replacement", status = "scheduled")),
            currentSettings = AppSettings(),
            replaceTasks = true,
        )

        assertEquals(1, success(result).tasksImported)
        assertEquals(listOf("replacement"), access.tasks.map { it.id })
        assertEquals(1, access.deleteAllCalls)
        assertEquals(listOf("backup-restore"), access.reconcileReasons)
        assertTrue(access.events.indexOf("delete") < access.events.indexOf("insert:replacement"))
        assertTrue(access.events.indexOf("insert:replacement") < access.events.indexOf("reconcile:backup-restore"))
        assertTrue(gate.owners.contains("BackupRestoreEngine.deleteAll"))
        assertTrue(gate.owners.contains("BackupRestoreEngine.insertTask"))
        assertTrue(gate.owners.contains("BackupRestoreEngine.reconcileAlarms"))
    }

    @Test
    fun replaceReconcilesOnceEvenWhenNoFutureScheduledTaskWasImported() = runTest {
        val gate = RecordingGate()
        val access = FakeAccess(
            gate = gate,
            tasks = mutableListOf(task(id = "old", status = "scheduled")),
        )
        val engine = engine(access, gate)

        val result = engine.restore(
            envelope(task(id = "completed", status = "completed")),
            currentSettings = AppSettings(),
            replaceTasks = true,
        )

        assertTrue(result is RestoreResult.Success)
        assertEquals(1, access.reconcileReasons.size)
        assertTrue(
            access.events.indexOf("reconcile:backup-restore") <
                access.events.indexOf("sweepImages"),
        )
        assertEquals(1, access.imageSweepCalls)
    }

    @Test
    fun optInEnrichmentFillsOnlyEmptyExistingTasksWithoutReplacingTaskFields() = runTest {
        val gate = RecordingGate()
        val localTask = task(id = "same", status = "completed").copy(
            title = "Local title",
            startTime = "2026-10-09T08:00:00Z",
        )
        val imported = task(id = "same").copy(
            title = "Incoming title",
            subtasks = listOf(Subtask(id = "part", title = "Part")),
        )
        val access = FakeAccess(gate, tasks = mutableListOf(localTask))
        val result = engine(access, gate).restore(
            envelope(imported),
            currentSettings = AppSettings(),
            replaceTasks = false,
            enrichExistingTasks = true,
        )

        val success = success(result)
        assertEquals(1, success.tasksSkipped)
        assertEquals(1, success.tasksEnriched)
        assertEquals(1, success.subtasksImported)
        assertEquals("Local title", access.tasks.single().title)
        assertEquals("2026-10-09T08:00:00Z", access.tasks.single().startTime)
        assertEquals(listOf("part"), access.tasks.single().subtasks.map { it.id })
        assertEquals(1, access.replaceSubtaskCalls)
    }

    @Test
    fun enrichmentNeverOverwritesExistingSubtasks() = runTest {
        val gate = RecordingGate()
        val local = task(id = "same").copy(
            subtasks = listOf(Subtask(id = "local", title = "Keep local")),
        )
        val incoming = task(id = "same").copy(
            subtasks = listOf(Subtask(id = "incoming", title = "Do not replace")),
        )
        val access = FakeAccess(gate, tasks = mutableListOf(local))

        val result = engine(access, gate).restore(
            envelope(incoming),
            currentSettings = AppSettings(),
            replaceTasks = false,
            enrichExistingTasks = true,
        )

        assertTrue(result is RestoreResult.Success)
        assertEquals(listOf("local"), access.tasks.single().subtasks.map { it.id })
        assertEquals(0, access.replaceSubtaskCalls)
    }

    @Test
    fun restoreIngestsOnlyReferencedImagesAndRemapsToStoredHashes() = runTest {
        val directory = Files.createTempDirectory("restore-images").toFile()
        try {
            val store = SubtaskImageStore(directory, FakeCodec())
            val gate = RecordingGate()
            val access = FakeAccess(gate)
            val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
            val task = task(id = "with-image").copy(
                subtasks = listOf(
                    Subtask(
                        id = "part",
                        title = "Part",
                        image = SubtaskImage("file-image"),
                    ),
                ),
            )
            val backup = envelope(
                task,
                assets = mapOf(
                    "file-image" to BackupAsset(
                        mime = "image/png",
                        data = Base64.getEncoder().withoutPadding().encodeToString(png),
                    ),
                ),
            )

            val result = BackupRestoreEngine(
                access = access,
                writeGate = gate,
                clock = { fixedNow },
                imageStore = store,
            ).restore(backup, AppSettings(), replaceTasks = false)

            val storedId = SubtaskImageStore.sha256(SubtaskImageStoreTest.fakeWebp())
            assertEquals(1, success(result).imagesStored)
            assertEquals(storedId, access.tasks.single().subtasks.single().image?.asset)
            assertTrue(store.exists(storedId))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun restoreProcessesCandidateImagesOneAtATimeAndClearsDecodedBuffers() = runTest {
        val directory = Files.createTempDirectory("restore-sequential-images").toFile()
        try {
            val codec = FakeCodec()
            val store = SubtaskImageStore(directory, codec)
            val gate = RecordingGate()
            val access = FakeAccess(gate)
            val pngOne = SubtaskImageStoreTest.fakePng()
            val pngTwo = pngOne.copyOf().also { it[it.lastIndex] = 1 }
            val incoming = task(
                id = "two-images",
                subtasks = listOf(
                    Subtask(id = "one", title = "One", image = SubtaskImage("image-one")),
                    Subtask(id = "two", title = "Two", image = SubtaskImage("image-two")),
                ),
            )
            val backup = envelope(
                incoming,
                assets = mapOf(
                    "image-one" to BackupAsset(
                        mime = "image/png",
                        data = Base64.getEncoder().encodeToString(pngOne),
                    ),
                    "image-two" to BackupAsset(
                        mime = "image/png",
                        data = Base64.getEncoder().encodeToString(pngTwo),
                    ),
                ),
            )

            val result = BackupRestoreEngine(
                access = access,
                writeGate = gate,
                clock = { fixedNow },
                imageStore = store,
            ).restore(backup, AppSettings(), replaceTasks = false)

            assertEquals(1, success(result).tasksImported)
            assertEquals(2, codec.reencodeCalls)
            assertEquals(1, codec.maxConcurrentReencodes)
            assertTrue(codec.observedInputs.all { bytes -> bytes.all { it == 0.toByte() } })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun mergeWithOnlyDuplicateImageTasksDoesNotIngestAssetsAndStillSweeps() = runTest {
        val directory = Files.createTempDirectory("restore-duplicate-images").toFile()
        try {
            val store = SubtaskImageStore(directory, FakeCodec())
            val gate = RecordingGate()
            val existing = task(id = "same")
            val access = FakeAccess(gate, tasks = mutableListOf(existing))
            val incoming = task(id = "same").copy(
                subtasks = listOf(
                    Subtask(
                        id = "part",
                        title = "Part",
                        image = SubtaskImage("file-image"),
                    ),
                ),
            )
            val png = SubtaskImageStoreTest.fakePng()
            val backup = envelope(
                incoming,
                assets = mapOf(
                    "file-image" to BackupAsset(
                        mime = "image/png",
                        data = Base64.getEncoder().encodeToString(png),
                    ),
                ),
            )

            val result = BackupRestoreEngine(
                access = access,
                writeGate = gate,
                clock = { fixedNow },
                imageStore = store,
            ).restore(backup, AppSettings(), replaceTasks = false)

            assertEquals(0, success(result).tasksImported)
            assertEquals(1, success(result).tasksSkipped)
            assertEquals(0, success(result).imagesStored)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
            assertEquals(1, access.imageSweepCalls)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedTaskWriteStillSweepsAfterImagePreparation() = runTest {
        val directory = Files.createTempDirectory("restore-write-failure").toFile()
        try {
            val codec = FakeCodec()
            val store = SubtaskImageStore(directory, codec)
            val gate = RecordingGate()
            val access = FakeAccess(gate).apply {
                insertFailure = IllegalStateException("task write failed")
            }
            val png = SubtaskImageStoreTest.fakePng()
            val incoming = task(
                id = "write-failure",
                subtasks = listOf(
                    Subtask(id = "part", title = "Part", image = SubtaskImage("file-image")),
                ),
            )
            val backup = envelope(
                incoming,
                assets = mapOf(
                    "file-image" to BackupAsset(
                        mime = "image/png",
                        data = Base64.getEncoder().encodeToString(png),
                    ),
                ),
            )

            val result = BackupRestoreEngine(
                access = access,
                writeGate = gate,
                clock = { fixedNow },
                imageStore = store,
            ).restore(backup, AppSettings(), replaceTasks = false)

            assertTrue(result is RestoreResult.Failure)
            assertEquals("task write failed", (result as RestoreResult.Failure).message)
            assertEquals(1, codec.reencodeCalls)
            assertEquals(1, access.imageSweepCalls)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedImageIngestWarnsAndKeepsTheTaskWithoutTheImage() = runTest {
        val directory = Files.createTempDirectory("restore-invalid-image").toFile()
        try {
            val store = SubtaskImageStore(directory, FakeCodec())
            val gate = RecordingGate()
            val access = FakeAccess(gate)
            val task = task(id = "bad-image").copy(
                subtasks = listOf(
                    Subtask(
                        id = "part",
                        title = "Part",
                        image = SubtaskImage("file-image"),
                    ),
                ),
            )
            val backup = envelope(
                task,
                assets = mapOf(
                    "file-image" to BackupAsset(
                        mime = "image/png",
                        data = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)),
                    ),
                ),
            )

            val result = BackupRestoreEngine(
                access = access,
                writeGate = gate,
                clock = { fixedNow },
                imageStore = store,
            ).restore(backup, AppSettings(), replaceTasks = false)

            val restored = success(result)
            assertEquals(1, restored.tasksImported)
            assertEquals(0, restored.tasksSkipped)
            assertTrue(restored.warnings.any { it.contains("images could not be decoded or stored") })
            assertEquals(1, access.tasks.size)
            assertNull(access.tasks.single().subtasks.single().image)
            assertEquals(1, access.imageSweepCalls)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun pastScheduledTasksAreImportedAsSkippedWithCanonicalUpdateTime() = runTest {
        val gate = RecordingGate()
        val access = FakeAccess(gate = gate)
        val engine = engine(access, gate)

        val result = engine.restore(
            envelope(
                task(
                    id = "past",
                    status = "scheduled",
                    endTime = "2026-10-08T09:59:00Z",
                ),
            ),
            currentSettings = AppSettings(),
            replaceTasks = false,
        )

        assertEquals(1, success(result).tasksImported)
        assertEquals(0, success(result).tasksSkipped)
        assertEquals("skipped", access.tasks.single().status)
        assertEquals(CanonicalTimestamp.format(fixedNow), access.tasks.single().updatedAt)
        assertTrue(access.reconcileReasons.isEmpty())
    }

    @Test
    fun activeFocusSessionBlocksReplaceBeforeAnyRestoreWrites() = runTest {
        val gate = RecordingGate()
        val original = task(id = "keep", status = "scheduled")
        val access = FakeAccess(
            gate = gate,
            tasks = mutableListOf(original),
            activeSession = true,
        )
        val engine = engine(access, gate)

        val result = engine.restore(
            envelope(task(id = "replacement")),
            currentSettings = AppSettings(),
            replaceTasks = true,
        )

        assertTrue(result is RestoreResult.Failure)
        assertTrue((result as RestoreResult.Failure).message.contains("Focus Session"))
        assertEquals(listOf(original), access.tasks)
        assertTrue(access.preferenceWrites.isEmpty())
        assertEquals(0, access.deleteAllCalls)
        assertTrue(access.insertedTasks.isEmpty())
        assertTrue(access.reconcileReasons.isEmpty())
        assertEquals(1, access.imageSweepCalls)
    }

    @Test
    fun settingsWritesKeepTypesAndRejectDeviceLocalKeysUnderTheGate() = runTest {
        val gate = RecordingGate()
        val access = FakeAccess(gate = gate)
        val engine = engine(access, gate)
        val settings = JsonObject(
            mapOf(
                "darkMode" to JsonPrimitive(false),
                "defaultDuration" to JsonPrimitive(45),
                "focusMirrorVpnEnabled" to JsonPrimitive(true),
                "standaloneBlockActive" to JsonPrimitive(true),
                "standaloneBlockPackages" to JsonArray(listOf(JsonPrimitive("com.example.local"))),
                "standaloneBlockVpnPackages" to JsonArray(listOf(JsonPrimitive("com.example.localvpn"))),
                "standaloneBlockUntilMs" to JsonPrimitive(1234),
                "alwaysBlockEnabled" to JsonPrimitive(true),
                "networkBlockEnabled" to JsonPrimitive(true),
                "pomodoroEnabled" to JsonPrimitive(true),
                "aversionDimmerEnabled" to JsonPrimitive(true),
                "aversionVibrateEnabled" to JsonPrimitive(true),
                "aversionSoundEnabled" to JsonPrimitive(true),
                "systemGuardEnabled" to JsonPrimitive(true),
                "blockInstallActionsEnabled" to JsonPrimitive(true),
                "blockYoutubeShortsEnabled" to JsonPrimitive(true),
                "blockInstagramReelsEnabled" to JsonPrimitive(true),
                "vpnSelfHealEnabled" to JsonPrimitive(true),
                "pinProtectionEnabled" to JsonPrimitive(true),
                "autoCopyToAlwaysOn" to JsonPrimitive(true),
            ),
        )

        val result = engine.restore(
            envelope(settings = settings),
            currentSettings = AppSettings(),
            replaceTasks = false,
        )

        assertTrue(result is RestoreResult.Success)
        assertEquals(
            mapOf(
                "dark_mode_enabled" to LegacyPreferenceValue.BooleanValue(false),
                "default_duration_minutes" to LegacyPreferenceValue.IntValue(45),
                "net_block_focus_mirror" to LegacyPreferenceValue.BooleanValue(true),
            ),
            access.preferenceWrites,
        )
        assertTrue(gate.owners.contains("BackupRestoreEngine.settings"))
    }

    @Test
    fun currentPortableSettingsRestoreBeyondLegacyMappingAndIgnoreLocalFields() = runTest {
        val gate = RecordingGate()
        val access = FakeAccess(gate = gate)
        val engine = engine(access, gate)
        val settings = JsonObject(
            mapOf(
                "generalTextScale" to JsonPrimitive(1.2),
                "homeTextScale" to kotlinx.serialization.json.JsonNull,
                "screenTextScales" to JsonObject(
                    mapOf("focus.home" to JsonPrimitive(0.9)),
                ),
                "morningDigestEnabled" to JsonPrimitive(false),
                "launcherWallpaperUri" to kotlinx.serialization.json.JsonNull,
                "lastShownDebriefSessionId" to kotlinx.serialization.json.JsonNull,
                "bedTime" to JsonPrimitive("23:15"),
                "taskRemindersEnabled" to JsonPrimitive(false),
                "standaloneBlockActive" to JsonPrimitive(true),
                "vpnSelfHealEnabled" to JsonPrimitive(true),
            ),
        )

        val result = engine.restore(
            envelope(settings = settings),
            currentSettings = AppSettings(),
            replaceTasks = false,
        )

        assertTrue(result is RestoreResult.Success)
        assertEquals(
            mapOf(
                "general_text_scale" to BackupPreferenceValue.FloatValue(1.2f),
                "home_text_scale" to BackupPreferenceValue.Remove,
                "screen_text_scales" to BackupPreferenceValue.StringValue("""{"focus.home":0.9}"""),
                "morning_digest_enabled" to BackupPreferenceValue.BooleanValue(false),
                "launcher_wallpaper_uri" to BackupPreferenceValue.Remove,
                "last_shown_debrief_session_id" to BackupPreferenceValue.Remove,
                "bed_time" to BackupPreferenceValue.StringValue("23:15"),
                "task_reminders_enabled" to BackupPreferenceValue.BooleanValue(false),
                "task_pre_start_reminders_enabled" to BackupPreferenceValue.BooleanValue(true),
                "task_at_start_reminder_enabled" to BackupPreferenceValue.BooleanValue(true),
                "task_mid_session_reminders_enabled" to BackupPreferenceValue.BooleanValue(true),
                "task_wrap_up_reminder_enabled" to BackupPreferenceValue.BooleanValue(true),
                "task_end_alarm_rings" to BackupPreferenceValue.BooleanValue(true),
            ),
            access.portablePreferenceWrites,
        )
        assertTrue(access.preferenceWrites.isEmpty())
        assertTrue(access.refreshedSettingKeys.containsAll(setOf(
            "general_text_scale",
            "bed_time",
            "task_reminders_enabled",
        )))
        assertEquals(listOf("backup-restore"), access.reconcileReasons)
        assertTrue(gate.owners.contains("BackupRestoreEngine.settings"))
    }

    @Test
    fun malformedTaskEndTimeIsSkippedWithWarning() = runTest {
        val gate = RecordingGate()
        val access = FakeAccess(gate = gate)
        val engine = engine(access, gate)

        val result = engine.restore(
            envelope(task(id = "bad-time", endTime = "not-a-timestamp")),
            currentSettings = AppSettings(),
            replaceTasks = false,
        )

        assertEquals(0, success(result).tasksImported)
        assertEquals(1, success(result).tasksSkipped)
        assertTrue(success(result).warnings.single().contains("end time is invalid"))
        assertTrue(access.tasks.isEmpty())
        assertTrue(access.reconcileReasons.isEmpty())
    }

    private fun engine(access: FakeAccess, gate: RecordingGate) =
        BackupRestoreEngine(
            access = access,
            writeGate = gate,
            clock = { fixedNow },
        )

    private fun success(result: RestoreResult): RestoreResult.Success {
        assertTrue("Expected restore success, got $result", result is RestoreResult.Success)
        return result as RestoreResult.Success
    }

    private fun envelope(
        vararg tasks: Task,
        settings: JsonObject = JsonObject(emptyMap()),
        assets: Map<String, BackupAsset> = emptyMap(),
    ) = BackupEnvelope(
        kind = BackupSerializer.BACKUP_KIND,
        version = BackupSerializer.BACKUP_VERSION,
        exportedAt = "2026-10-08T08:00:00Z",
        exportedAtHuman = "10/8/2026, 8:00:00 AM",
        appVersion = "1.1.4",
        platform = BackupPlatform(os = "android"),
        settings = settings,
        tasks = tasks.toList(),
        presetSections = emptyList(),
        summary = BackupSummary(
            taskCount = tasks.size,
            blockedWordCount = 0,
            greyoutWindowCount = 0,
            dailyAllowanceCount = 0,
        ),
        assets = assets,
    )

    private fun task(
        id: String,
        status: String = "scheduled",
        endTime: String = "2026-10-08T11:00:00Z",
        subtasks: List<Subtask> = emptyList(),
    ) = Task(
        id = id,
        title = "Task $id",
        startTime = "2026-10-08T10:30:00Z",
        endTime = endTime,
        durationMinutes = 30,
        status = status,
        priority = "medium",
        color = "#22c55e",
        focusMode = false,
        createdAt = "2026-10-07T12:00:00Z",
        updatedAt = "2026-10-07T12:00:00Z",
        subtasks = subtasks,
    )

    private class RecordingGate : BackupRestoreWriteGate {
        val owners = mutableListOf<String>()
        var depth = 0
            private set

        override suspend fun <T> write(owner: String, block: suspend () -> T): T {
            owners += owner
            depth++
            return try {
                block()
            } finally {
                depth--
            }
        }
    }

    private class FakeAccess(
        private val gate: RecordingGate,
        val tasks: MutableList<Task> = mutableListOf(),
        private val activeSession: Boolean = false,
    ) : BackupRestoreAccess {
        val preferenceWrites = linkedMapOf<String, LegacyPreferenceValue>()
        val portablePreferenceWrites = linkedMapOf<String, BackupPreferenceValue>()
        var refreshedSettingKeys = emptySet<String>()
        val events = mutableListOf<String>()
        val reconcileReasons = mutableListOf<String>()
        val insertedTasks = mutableListOf<Task>()
        var deleteAllCalls = 0
        var replaceSubtaskCalls = 0
        var imageSweepCalls = 0
        var insertFailure: Throwable? = null

        override suspend fun writePreference(key: String, value: LegacyPreferenceValue) {
            check(gate.depth > 0) { "Preference write bypassed RestoreGate." }
            preferenceWrites[key] = value
            events += "preference:$key"
        }

        override suspend fun writePortablePreference(key: String, value: BackupPreferenceValue) {
            check(gate.depth > 0) { "Portable preference write bypassed RestoreGate." }
            portablePreferenceWrites[key] = value
            events += "portable-preference:$key"
        }

        override suspend fun refreshRestoredSettings(preferenceKeys: Set<String>): List<String> {
            check(gate.depth > 0) { "Settings refresh bypassed RestoreGate." }
            refreshedSettingKeys = preferenceKeys
            events += "refreshSettings"
            return emptyList()
        }

        override suspend fun getAllTasks(): List<Task> {
            check(gate.depth > 0) { "Task ID snapshot was read outside RestoreGate." }
            events += "getAllTasks"
            return tasks.toList()
        }

        override suspend fun deleteAllTasks() {
            check(gate.depth > 0) { "Task deletion bypassed RestoreGate." }
            deleteAllCalls++
            tasks.clear()
            events += "delete"
        }

        override suspend fun insertTask(task: Task) {
            check(gate.depth > 0) { "Task insert bypassed RestoreGate." }
            insertFailure?.let { throw it }
            insertedTasks += task
            tasks += task
            events += "insert:${task.id}"
        }

        override suspend fun replaceSubtasks(taskId: String, subtasks: List<Subtask>) {
            check(gate.depth > 0) { "Subtask enrichment bypassed RestoreGate." }
            val index = tasks.indexOfFirst { it.id == taskId }
            if (index >= 0) tasks[index] = tasks[index].copy(subtasks = subtasks)
            replaceSubtaskCalls++
            events += "enrich:$taskId"
        }

        override suspend fun sweepSubtaskImages(): Int {
            check(gate.depth > 0) { "Image sweep bypassed RestoreGate." }
            imageSweepCalls++
            events += "sweepImages"
            return 0
        }

        override suspend fun hasActiveFocusSession(): Boolean = activeSession

        override suspend fun reconcileAlarms(reason: String) {
            check(gate.depth > 0) { "Alarm reconcile bypassed RestoreGate." }
            reconcileReasons += reason
            events += "reconcile:$reason"
        }
    }

    private class FakeCodec : ImageCodec {
        var reencodeCalls = 0
        var activeReencodes = 0
        var maxConcurrentReencodes = 0
        val observedInputs = mutableListOf<ByteArray>()

        override fun bounds(bytes: ByteArray) = ImageBounds(32, 32)

        override fun reencode(bytes: ByteArray, maxLongEdge: Int, quality: Int): ByteArray {
            reencodeCalls++
            activeReencodes++
            maxConcurrentReencodes = maxOf(maxConcurrentReencodes, activeReencodes)
            observedInputs += bytes
            return try {
                SubtaskImageStoreTest.fakeWebp()
            } finally {
                activeReencodes--
            }
        }
    }
}
