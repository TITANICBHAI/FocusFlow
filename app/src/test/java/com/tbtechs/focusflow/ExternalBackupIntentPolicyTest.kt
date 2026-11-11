package com.tbtechs.focusflow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalBackupIntentPolicyTest {
    private val view = "android.intent.action.VIEW"

    @Test
    fun acceptsFocusFlowExtensionInAnyCase() {
        assertTrue(ExternalBackupIntentPolicy.shouldStage(view, "content", "FocusFlow.FOCUSFLOW"))
        assertTrue(ExternalBackupIntentPolicy.shouldStage(view, "file", "backup.focusflow"))
        assertTrue(ExternalBackupIntentPolicy.shouldStage(view, "content", "my.backup.2026.focusflow"))
    }

    @Test
    fun rejectsOtherFilesAndOpaqueNames() {
        assertFalse(ExternalBackupIntentPolicy.shouldStage(view, "content", "document.pdf"))
        assertFalse(ExternalBackupIntentPolicy.shouldStage(view, "content", "document/8721"))
        assertFalse(ExternalBackupIntentPolicy.shouldStage(view, "content", "backup.focusflow.txt"))
        assertFalse(ExternalBackupIntentPolicy.shouldStage(view, "content", null))
    }

    @Test
    fun rejectsWebLinksAndOtherActions() {
        assertFalse(ExternalBackupIntentPolicy.shouldStage(view, "https", "backup.focusflow"))
        assertFalse(ExternalBackupIntentPolicy.shouldStage("android.intent.action.SEND", "content", "backup.focusflow"))
    }

    @Test
    fun fileViewIsOnlyContentOrFileUrisWithView() {
        assertTrue(ExternalBackupIntentPolicy.isFileView(view, "content"))
        assertTrue(ExternalBackupIntentPolicy.isFileView(view, "FILE"))
        assertFalse(ExternalBackupIntentPolicy.isFileView(view, "focusflow"))
        assertFalse(ExternalBackupIntentPolicy.isFileView(null, "content"))
    }
}
