package com.tbtechs.focusflow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalBackupIntentPolicyTest {
    @Test
    fun acceptsOctetStreamContentUrisWithoutVisibleFilename() {
        assertTrue(
            ExternalBackupIntentPolicy.shouldStage(
                action = "android.intent.action.VIEW",
                uriScheme = "content",
                mimeType = "application/octet-stream",
                lastPathSegment = "document/8721",
            ),
        )
    }

    @Test
    fun acceptsFocusFlowExtensionWithOtherOrMissingMimeType() {
        assertTrue(
            ExternalBackupIntentPolicy.shouldStage(
                action = "android.intent.action.VIEW",
                uriScheme = "content",
                mimeType = "application/json",
                lastPathSegment = "FocusFlow.FOCUSFLOW",
            ),
        )
        assertTrue(
            ExternalBackupIntentPolicy.shouldStage(
                action = "android.intent.action.VIEW",
                uriScheme = "file",
                mimeType = null,
                lastPathSegment = "backup.focusflow",
            ),
        )
    }

    @Test
    fun rejectsUnrelatedFilesAndWebViews() {
        assertFalse(
            ExternalBackupIntentPolicy.shouldStage(
                action = "android.intent.action.VIEW",
                uriScheme = "content",
                mimeType = "application/pdf",
                lastPathSegment = "document.pdf",
            ),
        )
        assertFalse(
            ExternalBackupIntentPolicy.shouldStage(
                action = "android.intent.action.VIEW",
                uriScheme = "https",
                mimeType = "application/octet-stream",
                lastPathSegment = "backup.focusflow",
            ),
        )
        assertFalse(
            ExternalBackupIntentPolicy.shouldStage(
                action = "android.intent.action.SEND",
                uriScheme = "content",
                mimeType = "application/octet-stream",
                lastPathSegment = "backup.focusflow",
            ),
        )
    }
}
