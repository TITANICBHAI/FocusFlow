package com.tbtechs.focusflow.ui.support

import com.tbtechs.focusflow.data.backup.BackupParseResult
import com.tbtechs.focusflow.data.backup.BackupSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusFlowFormatGuideDataTest {
    @Test
    fun displayedExampleIsAcceptedByTheCurrentBackupImporter() {
        val result = BackupSerializer.parseAndValidate(FOCUSFLOW_VALID_BACKUP_EXAMPLE)

        assertTrue("The guide example must be a valid FocusFlow backup.", result is BackupParseResult.Success)
        val backup = (result as BackupParseResult.Success).envelope
        assertEquals(1, backup.tasks.size)
        assertTrue(backup.settings.isEmpty())
    }
}
