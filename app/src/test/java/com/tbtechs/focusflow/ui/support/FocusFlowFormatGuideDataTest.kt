package com.tbtechs.focusflow.ui.support

import com.tbtechs.focusflow.data.backup.BackupParseResult
import com.tbtechs.focusflow.data.backup.BackupJsonLimits
import com.tbtechs.focusflow.data.backup.BackupSerializer
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
}
