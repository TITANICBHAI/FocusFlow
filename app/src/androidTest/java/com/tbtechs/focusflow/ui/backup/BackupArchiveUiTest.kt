package com.tbtechs.focusflow.ui.backup

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import com.tbtechs.focusflow.data.backup.BackupSerializer
import com.tbtechs.focusflow.data.model.AppSettings
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BackupArchiveUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun importReviewIdentifiesBundleAndImageCount() {
        val envelope = BackupSerializer.buildEnvelope(
            settings = AppSettings(),
            tasks = emptyList(),
            appVersion = "test",
        )
        composeRule.setContent {
            MaterialTheme {
                ImportConfirmScreen(
                    importState = ImportState.PendingConfirm(
                        envelope = envelope,
                        isBundle = true,
                        bundleImageCount = 2,
                    ),
                    pendingEnvelope = envelope,
                    onConfirm = { _, _ -> },
                    onCancelImport = {},
                    onResetImport = {},
                    onDone = {},
                    onCancel = {},
                )
            }
        }

        composeRule.onNodeWithText("Bundle · 2 images").assertExists()
    }

    @Test
    fun tooLargeBundleOffersExplicitImageFreeExportAndCancel() {
        val fallbackUri = AtomicReference<String?>(null)
        val resetCount = AtomicInteger()
        composeRule.setContent {
            MaterialTheme {
                BackupExportNoticeDialog(
                    state = ExportState.TooLarge("content://backup/created", 70L * 1024 * 1024),
                    onDismiss = {},
                    onReset = { resetCount.incrementAndGet() },
                    onExportWithoutImages = fallbackUri::set,
                )
            }
        }

        composeRule.onNodeWithText("Bundle too large").assertExists()
        composeRule.onNodeWithText("Cancel").assertExists()
        composeRule.onNodeWithText("Export without images").performClick()

        assertEquals("content://backup/created", fallbackUri.get())
        assertEquals(0, resetCount.get())
    }

    @Test
    fun exportSuccessExplainsWhichContainerWasWritten() {
        composeRule.setContent {
            MaterialTheme {
                BackupExportNoticeDialog(
                    state = ExportState.Success(
                        uri = "content://backup/export",
                        containerDescription = "ZIP bundle (includes images)",
                    ),
                    onDismiss = {},
                    onReset = {},
                    onExportWithoutImages = {},
                )
            }
        }

        composeRule.onNodeWithText(
            "Saved as a ZIP bundle (includes images).",
        ).assertExists()
    }
}
