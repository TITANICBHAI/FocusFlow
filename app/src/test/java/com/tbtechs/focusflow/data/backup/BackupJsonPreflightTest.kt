package com.tbtechs.focusflow.data.backup

import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonPreflightTest {
    @Test
    fun allowsLargeDataOnlyAtDirectTopLevelAssetData() {
        val imageData = "A".repeat(BackupJsonLimits.MAX_STRING_CHARS + 1)
        val json = """{"assets":{"hero":{"mime":"image/png","data":"$imageData"}}}"""

        assertTrue(BackupJsonPreflight.validateAndStripBom(json).contains(imageData))
    }

    @Test
    fun retainsTheStandardStringLimitForEveryOtherStringLocation() {
        val large = "A".repeat(BackupJsonLimits.MAX_STRING_CHARS + 1)
        val invalidExamples = listOf(
            """{"assets":{"hero":{"mime":"$large","data":"x"}}}""",
            """{"tasks":[{"title":"$large"}]}""",
            """{"tasks":[{"assets":{"hero":{"data":"$large"}}}]}""",
            """{"data":"$large"}""",
        )

        invalidExamples.forEach { json ->
            try {
                BackupJsonPreflight.validateAndStripBom(json)
                throw AssertionError("Expected the standard string cap to reject this value.")
            } catch (_: BackupJsonFormatException) {
                // Expected: only root.assets.<id>.data receives the larger cap.
            }
        }
    }
}
