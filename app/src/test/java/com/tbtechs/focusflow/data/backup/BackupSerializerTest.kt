package com.tbtechs.focusflow.data.backup

import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.AllowedAppPreset
import com.tbtechs.focusflow.data.model.BlockPreset
import com.tbtechs.focusflow.data.model.RecurringBlockSchedule
import com.tbtechs.focusflow.data.model.Task
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSerializerTest {
    @Test
    fun envelopeSerializesAndParsesWithTaskAndPortableSettingsIntact() {
        val task = sampleTask()
        val envelope = BackupSerializer.buildEnvelope(
            settings = sampleSettings(),
            tasks = listOf(task),
            appVersion = "1.1.4",
        )

        val serialized = BackupSerializer.serializeToJson(envelope)
        val result = BackupSerializer.parseAndValidate(serialized)

        assertTrue(result is BackupParseResult.Success)
        val restored = (result as BackupParseResult.Success).envelope
        assertEquals(envelope, restored)
        assertEquals(listOf(task), restored.tasks)
        assertEquals(JsonPrimitive(true), restored.settings["focusMirrorVpnEnabled"])
        assertEquals(JsonPrimitive(false), restored.settings["darkMode"])
    }

    @Test
    fun portableSettingsExcludeDeviceLocalFieldsAndKeepPortableVpnPreference() {
        val portable = PortableSettingsPolicy.toPortableJson(sampleSettings())

        listOf(
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
            "pinProtectionEnabled",
            "vpnSelfHealEnabled",
            "autoCopyToAlwaysOn",
            "systemGuardEnabled",
            "blockInstallActionsEnabled",
            "blockYoutubeShortsEnabled",
            "blockInstagramReelsEnabled",
        ).forEach { key ->
            assertFalse("Unexpected device-local setting: $key", portable.containsKey(key))
        }

        assertEquals(JsonPrimitive(true), portable["focusMirrorVpnEnabled"])
        assertTrue(portable.containsKey("darkMode"))
        assertTrue(portable.containsKey("defaultDuration"))
        assertTrue(portable.containsKey("allowedInFocus"))
        assertTrue(portable.containsKey("generalTextScale"))

        val schedule = portable["recurringBlockSchedules"]!!.jsonArray.single().jsonObject
        assertEquals(JsonPrimitive(2), schedule["days"]!!.jsonArray[0])
        assertEquals(JsonPrimitive(6), schedule["days"]!!.jsonArray[1])
    }

    @Test
    fun taskAlertSettingsRoundTripThroughPortableBackup() {
        val settings = AppSettings(
            taskPreStartRemindersEnabled = false,
            taskAtStartReminderEnabled = false,
            taskMidSessionRemindersEnabled = false,
            taskWrapUpReminderEnabled = false,
            taskEndAlarmRings = false,
        )
        val portable = PortableSettingsPolicy.toPortableJson(settings)

        assertEquals(JsonPrimitive(false), portable["taskPreStartRemindersEnabled"])
        assertEquals(JsonPrimitive(false), portable["taskAtStartReminderEnabled"])
        assertEquals(JsonPrimitive(false), portable["taskMidSessionRemindersEnabled"])
        assertEquals(JsonPrimitive(false), portable["taskWrapUpReminderEnabled"])
        assertEquals(JsonPrimitive(false), portable["taskEndAlarmRings"])

        val writes = BackupSettingsAdapter.additionalPreferenceWrites(portable)
        assertEquals(
            BackupPreferenceValue.BooleanValue(false),
            writes[com.tbtechs.focusflow.data.repository.SettingsRepository.KEY_TASK_PRE_START_REMINDERS_ENABLED],
        )
        assertEquals(
            BackupPreferenceValue.BooleanValue(false),
            writes[com.tbtechs.focusflow.data.repository.SettingsRepository.KEY_TASK_END_ALARM_RINGS],
        )
    }

    @Test
    fun olderPortableBackupDefaultsNewTaskAlertPreferencesToEnabledAndRinging() {
        val writes = BackupSettingsAdapter.additionalPreferenceWrites(
            JsonObject(mapOf("taskRemindersEnabled" to JsonPrimitive(false))),
        )

        assertEquals(
            BackupPreferenceValue.BooleanValue(false),
            writes["task_reminders_enabled"],
        )
        assertEquals(
            BackupPreferenceValue.BooleanValue(true),
            writes[com.tbtechs.focusflow.data.repository.SettingsRepository.KEY_TASK_PRE_START_REMINDERS_ENABLED],
        )
        assertEquals(
            BackupPreferenceValue.BooleanValue(true),
            writes[com.tbtechs.focusflow.data.repository.SettingsRepository.KEY_TASK_AT_START_REMINDER_ENABLED],
        )
        assertEquals(
            BackupPreferenceValue.BooleanValue(true),
            writes[com.tbtechs.focusflow.data.repository.SettingsRepository.KEY_TASK_MID_SESSION_REMINDERS_ENABLED],
        )
        assertEquals(
            BackupPreferenceValue.BooleanValue(true),
            writes[com.tbtechs.focusflow.data.repository.SettingsRepository.KEY_TASK_WRAP_UP_REMINDER_ENABLED],
        )
        assertEquals(
            BackupPreferenceValue.BooleanValue(true),
            writes[com.tbtechs.focusflow.data.repository.SettingsRepository.KEY_TASK_END_ALARM_RINGS],
        )
    }

    @Test
    fun envelopeBuildsPresetSectionsAndSummaryCountsFromCurrentSettings() {
        val envelope = BackupSerializer.buildEnvelope(
            settings = sampleSettings(),
            tasks = listOf(sampleTask()),
            appVersion = null,
        )

        assertEquals(1, envelope.summary.taskCount)
        assertEquals(2, envelope.summary.blockedWordCount)
        assertEquals(2, envelope.summary.greyoutWindowCount)
        assertEquals(1, envelope.summary.dailyAllowanceCount)
        assertEquals(
            listOf(
                "focus-mode",
                "standalone-block",
                "always-on",
                "daily-allowance",
                "keyword-blocker",
                "block-schedules",
                "defense",
            ),
            envelope.presetSections.map { it.id },
        )
        assertTrue(envelope.presetSections.first().configured)
        assertTrue(envelope.presetSections[1].configured)
        assertEquals(1, envelope.presetSections[3].itemCount)
        assertEquals(1, envelope.presetSections.last().itemCount)
    }

    @Test
    fun parserRejectsUnsupportedKindAndMissingRequiredRootFields() {
        val validJson = BackupSerializer.serializeToJson(
            BackupSerializer.buildEnvelope(sampleSettings(), listOf(sampleTask()), "1.1.4"),
        )
        val wrongKind = validJson.replace(
            "\"kind\": \"${BackupSerializer.BACKUP_KIND}\"",
            "\"kind\": \"WrongValue\"",
        )

        val kindFailure = BackupSerializer.parseAndValidate(wrongKind)
        assertTrue(kindFailure is BackupParseResult.Failure)
        assertTrue((kindFailure as BackupParseResult.Failure).message.contains("not a supported"))

        val missingSettings = BackupSerializer.parseAndValidate(
            """{"kind":"${BackupSerializer.BACKUP_KIND}","version":1,"tasks":[]}""",
        )
        assertTrue(missingSettings is BackupParseResult.Failure)
        assertTrue((missingSettings as BackupParseResult.Failure).message.contains("settings"))

        val missingTasks = BackupSerializer.parseAndValidate(
            """{"kind":"${BackupSerializer.BACKUP_KIND}","version":1,"settings":{}}""",
        )
        assertTrue(missingTasks is BackupParseResult.Failure)
        assertTrue((missingTasks as BackupParseResult.Failure).message.contains("tasks"))
    }

    @Test
    fun parserAcceptsV1BackupWithoutDescriptiveMetadataAndWarnsForUnsupportedSettings() {
        val validJson = BackupSerializer.serializeToJson(
            BackupSerializer.buildEnvelope(sampleSettings(), listOf(sampleTask()), appVersion = null),
        )
        val root = Json.parseToJsonElement(validJson).jsonObject
        val exportedAt = root.getValue("exportedAt")
        val minimalBackup = JsonObject(
            (root - setOf("exportedAtHuman", "appVersion", "presetSections", "summary")) +
                ("settings" to JsonObject(mapOf("focusMode" to JsonPrimitive(true)))),
        )

        val result = BackupSerializer.parseAndValidate(minimalBackup.toString())

        assertTrue(result is BackupParseResult.Success)
        val parsed = result as BackupParseResult.Success
        assertEquals(listOf(sampleTask()), parsed.envelope.tasks)
        assertEquals(exportedAt, JsonPrimitive(parsed.envelope.exportedAtHuman))
        assertEquals(null, parsed.envelope.appVersion)
        assertTrue(parsed.envelope.presetSections.isEmpty())
        assertEquals(1, parsed.envelope.summary.taskCount)
        assertEquals(
            listOf(
                "Settings in this backup are not supported by this app version and will be left unchanged.",
            ),
            parsed.warnings,
        )
    }

    @Test
    fun parserSkipsMalformedTaskRowsAndKeepsValidRowsAndSettings() {
        val validJson = BackupSerializer.serializeToJson(
            BackupSerializer.buildEnvelope(sampleSettings(), listOf(sampleTask()), "1.1.4"),
        )
        val root = Json.parseToJsonElement(validJson).jsonObject
        val taskRows = root.getValue("tasks").jsonArray
        val mixedTasks = JsonArray(
            listOf(
                taskRows.single(),
                JsonNull,
                JsonObject(mapOf("id" to JsonPrimitive("incomplete-task"))),
            ),
        )
        val mixedBackup = JsonObject(root + ("tasks" to mixedTasks)).toString()

        val result = BackupSerializer.parseAndValidate(mixedBackup)

        assertTrue(result is BackupParseResult.Success)
        val parsed = result as BackupParseResult.Success
        assertEquals(listOf(sampleTask()), parsed.envelope.tasks)
        assertEquals(3, parsed.envelope.summary.taskCount)
        assertEquals(
            listOf("2 malformed task records will be skipped."),
            parsed.warnings,
        )
        assertEquals(JsonPrimitive(false), parsed.envelope.settings["darkMode"])
    }

    @Test
    fun parserAcceptsCommittedV1GoldenBackup() {
        val golden = javaClass.classLoader!!
            .getResourceAsStream("backup/v1-export-golden.json")!!
            .bufferedReader()
            .use { it.readText() }

        val result = BackupSerializer.parseAndValidate(golden)

        assertTrue(result is BackupParseResult.Success)
        val envelope = (result as BackupParseResult.Success).envelope
        assertEquals("FocusFlowBackupV1", envelope.kind)
        assertEquals(1, envelope.version)
        assertEquals(1, envelope.summary.taskCount)
        assertEquals(JsonPrimitive(false), envelope.settings["focusMirrorVpnEnabled"])
    }

    @Test
    fun suggestedFilenameUsesTimestampAndFocusflowExtension() {
        val filename = BackupSerializer.buildSuggestedFilename()

        assertTrue(filename.startsWith("focusflow-"))
        assertTrue(filename.endsWith(".focusflow"))
        assertTrue(filename.matches(Regex("""^focusflow-\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2}\.focusflow$""")))
    }

    private fun sampleSettings() = AppSettings(
        alwaysBlockPackages = listOf("com.example.blocked"),
        blockedWords = listOf("word-a", "word-b"),
        standaloneBlockActive = true,
        standaloneBlockPackages = listOf("com.example.local"),
        standaloneBlockVpnPackages = listOf("com.example.localvpn"),
        standaloneBlockUntilMs = 1234L,
        launcherPresets = listOf(
            AllowedAppPreset(
                id = "allowed",
                name = "Focus",
                packages = listOf("com.example.focus"),
            ),
        ),
        blockPresets = listOf(
            BlockPreset(
                id = "blocked",
                name = "Block",
                packages = listOf("com.example.blocked"),
            ),
        ),
        alwaysOnVpnPackages = listOf("com.example.vpn"),
        dailyAllowanceConfigJson = """[{"packageName":"com.example.timer","mode":"count","countPerDay":3,"dailyAllowanceMs":0}]""",
        recurringBlockSchedules = listOf(
            RecurringBlockSchedule(
                id = "night",
                packages = listOf("com.example.social"),
                startHour = 21,
                startMinute = 15,
                endHour = 6,
                endMinute = 30,
                daysOfWeek = listOf(1, 5),
                name = "Night",
            ),
        ),
        userGreyoutWindowsJson = """[{"pkg":"com.example.video"},{"pkg":"com.example.social"}]""",
        networkBlockEnabled = true,
        vpnSelfHealEnabled = true,
        focusMirrorVpnEnabled = true,
        pinProtectionEnabled = true,
        autoCopyToAlwaysOn = true,
        darkModeEnabled = false,
        generalTextScale = 1.15f,
        defaultDurationMinutes = 45,
        allowedFocusPackages = listOf("com.example.focus"),
        pomodoroEnabled = false,
        pomodoroWorkMinutes = 30,
        pomodoroBreakMinutes = 10,
    )

    private fun sampleTask() = Task(
        id = "task-1",
        title = "Plan the week",
        startTime = "2026-10-08T09:00:00Z",
        endTime = "2026-10-08T09:30:00Z",
        durationMinutes = 30,
        status = "scheduled",
        priority = "medium",
        color = "#22c55e",
        focusMode = true,
        createdAt = "2026-10-07T12:00:00Z",
        updatedAt = "2026-10-07T12:00:00Z",
    )
}
