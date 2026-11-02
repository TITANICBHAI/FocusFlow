package com.tbtechs.focusflow.ui.support

import com.tbtechs.focusflow.data.backup.BackupParseResult
import com.tbtechs.focusflow.data.backup.BackupJsonLimits
import com.tbtechs.focusflow.data.backup.BackupSerializer
import com.tbtechs.focusflow.data.model.PhotoLinkCheck
import com.tbtechs.focusflow.data.model.PhotoLinkPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusFlowFormatGuideDataTest {
    @Test
    fun displayedExampleIsAcceptedByTheCurrentBackupImporter() {
        val result = BackupSerializer.parseAndValidate(FOCUSFLOW_VALID_BACKUP_EXAMPLE)

        assertTrue("The guide example must be a valid FocusFlow backup.", result is BackupParseResult.Success)
        val success = result as BackupParseResult.Success
        assertTrue(success.warnings.isEmpty())
        val backup = success.envelope
        assertEquals(1, backup.tasks.size)
        assertTrue(backup.settings.isEmpty())
    }

    @Test
    fun subtaskPromptAndGuideExampleParsesWithoutWarnings() {
        val result = BackupSerializer.parseAndValidate(FOCUSFLOW_SUBTASK_EXAMPLE)

        assertTrue(
            "The subtask example must be a valid FocusFlow backup.",
            result is BackupParseResult.Success,
        )
        val success = result as BackupParseResult.Success
        val backup = success.envelope
        assertEquals(1, backup.tasks.size)
        assertEquals(2, backup.tasks.single().subtasks.size)
        assertTrue(success.warnings.isEmpty())
    }

    @Test
    fun promptAndGuideUseCentralImageBudgetAndSupportedVersionGuidance() {
        val prompt = FOCUSFLOW_TASK_BACKUP_PROMPT
        val byteBudgetText = BackupJsonLimits.assetBudgetGuidance()

        assertTrue(prompt.contains(byteBudgetText))
        assertTrue(FOCUSFLOW_ASSETS_NOTE.contains(byteBudgetText))
        assertTrue(prompt.contains("version 2"))
        assertTrue(FOCUSFLOW_VERSION_NOTE.contains("1 and 2"))
    }

    @Test
    fun promptTeachesPhotoSlotsAndTheExampleCarriesThem() {
        val prompt = FOCUSFLOW_TASK_BACKUP_PROMPT

        assertTrue(prompt.contains("## Pictures: describe them, FocusFlow attaches them"))
        assertTrue(
            prompt.contains(
                BackupJsonLimits.formatByteLimit(BackupJsonLimits.MAX_SUBTASK_JSON_BYTES_PER_TASK),
            ),
        )
        assertTrue(prompt.contains("`brief`"))
        assertTrue(prompt.contains("`url`"))
        assertTrue(!prompt.contains("Remote image URLs are not supported"))
        assertTrue(!FOCUSFLOW_ASSETS_NOTE.contains("Remote image links are not supported"))
        val placeholder = "https://images.example.com/pushup-side.jpg"
        assertTrue(prompt.contains(placeholder))
        assertTrue(PhotoLinkPolicy.check(placeholder) is PhotoLinkCheck.Accepted)

        val parsed = BackupSerializer.parseAndValidate(FOCUSFLOW_SUBTASK_EXAMPLE) as BackupParseResult.Success
        val subtasks = parsed.envelope.tasks.single().subtasks
        assertEquals(placeholder, subtasks[0].photo?.url)
        assertTrue(subtasks[0].steps[1].photo?.brief?.isNotBlank() == true)
        assertTrue(subtasks[1].photo?.url == null)
        assertTrue(subtasks[1].photo?.brief?.isNotBlank() == true)
        assertTrue(subtasks.all { it.image == null })
        assertTrue(parsed.warnings.isEmpty())
    }
}
