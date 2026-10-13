package com.tbtechs.focusflow.ui.home

import com.tbtechs.focusflow.data.backup.BackupJsonLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskTagsTest {
    @Test
    fun addTaskTagsFromDraft_trimsTagsAndSplitsCommaSeparatedValues() {
        val result = addTaskTagsFromDraft(
            tags = emptyList(),
            draft = "  #work, study \n #personal ",
        )

        assertEquals(listOf("work", "study", "personal"), result)
    }

    @Test
    fun addTaskTagsFromDraft_ignoresBlankAndCaseInsensitiveDuplicates() {
        val result = addTaskTagsFromDraft(
            tags = listOf("Work", "home"),
            draft = " work, , HOME, reading ",
        )

        assertEquals(listOf("Work", "home", "reading"), result)
    }

    @Test
    fun addTaskTagsFromDraft_preservesExistingTagsWhenDraftIsBlank() {
        val existing = listOf("work", "personal")

        assertEquals(existing, addTaskTagsFromDraft(existing, " , \n "))
    }

    @Test
    fun addTaskTagsFromDraft_enforcesBackupCompatibleTagLimits() {
        val longestAllowed = "x".repeat(BackupJsonLimits.MAX_TAG_CHARS)
        val tagsAtLimit = List(BackupJsonLimits.MAX_TAGS_PER_TASK - 1) { "existing-$it" }
        val atLimit = addTaskTagsFromDraft(tagsAtLimit, longestAllowed)
        assertEquals(BackupJsonLimits.MAX_TAGS_PER_TASK, atLimit.size)
        assertEquals(longestAllowed, atLimit.last())

        val longTagError = runCatching {
            addTaskTagsFromDraft(emptyList(), "x".repeat(BackupJsonLimits.MAX_TAG_CHARS + 1))
        }.exceptionOrNull()
        assertTrue(longTagError is IllegalArgumentException)

        val tooManyTagsError = runCatching {
            addTaskTagsFromDraft(
                List(BackupJsonLimits.MAX_TAGS_PER_TASK) { "existing-$it" },
                "one-more",
            )
        }.exceptionOrNull()
        assertTrue(tooManyTagsError is IllegalArgumentException)
    }
}
