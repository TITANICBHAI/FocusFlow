package com.tbtechs.focusflow.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoSlotSerializationTest {
    // Same settings as BackupSerializer (export) and TaskRepository (database column).
    private val backupJson = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }
    private val databaseJson = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun anItemWithoutASlotWritesNoPhotoKeySoOlderBuildsSeeNothingNew() {
        val subtask = Subtask(id = "a", title = "A", steps = listOf(SubtaskStep(text = "One")))

        assertFalse(backupJson.encodeToString(subtask).contains("photo"))
        assertFalse(databaseJson.encodeToString(subtask).contains("photo"))
    }

    @Test
    fun slotsRoundTripThroughBothConfigurations() {
        val subtask = Subtask(
            id = "a",
            title = "A",
            photo = PhotoSlot("Side view", "https://example.com/p.jpg", "Alt"),
            steps = listOf(SubtaskStep(text = "One", photo = PhotoSlot(brief = "Start"))),
        )

        listOf(backupJson, databaseJson).forEach { json ->
            val text = json.encodeToString(subtask)
            assertTrue(text, text.contains("\"photo\""))
            assertEquals(subtask, json.decodeFromString<Subtask>(text))
        }
    }

    @Test
    fun rowsSavedBeforeThisFeatureStillDecode() {
        val old = """{"id":"a","title":"A","steps":[{"text":"One","image":{"asset":"abc"}}]}"""
        val decoded = databaseJson.decodeFromString<Subtask>(old)

        assertNull(decoded.photo)
        assertNull(decoded.steps.single().photo)
        assertEquals("abc", decoded.steps.single().image?.asset)
    }
}
