package com.tbtechs.focusflow.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFileManagerTest {
    @Test
    fun readStreamRejectsContentOverEightMebibytes() {
        val oversized = ByteArray(BackupJsonLimits.MAX_FILE_BYTES + 1)

        val failure = runCatching {
            BackupFileManager.readStream(ByteArrayInputStream(oversized))
        }.exceptionOrNull()

        assertTrue(failure is BackupJsonFormatException)
        assertTrue(failure?.message.orEmpty().contains("8 MiB"))
    }

    @Test
    fun readStreamRejectsMalformedUtf8() {
        val malformedUtf8 = byteArrayOf(0xC3.toByte(), 0x28)

        val failure = runCatching {
            BackupFileManager.readStream(ByteArrayInputStream(malformedUtf8))
        }.exceptionOrNull()

        assertTrue(failure is BackupJsonFormatException)
        assertTrue(failure?.message.orEmpty().contains("valid UTF-8"))
    }

    @Test
    fun writeThenReadRoundTripPreservesUtf8AndUsesExistingJsonPreflight() {
        val json = """
            {
              "kind": "${BackupSerializer.BACKUP_KIND}",
              "version": 1,
              "exportedAt": "2026-10-08T12:00:00Z",
              "exportedAtHuman": "10/8/2026, 5:30:00 PM",
              "appVersion": "1.1.4",
              "platform": { "os": "android" },
              "settings": { "note": "नमस्ते 👋" },
              "tasks": [],
              "presetSections": [],
              "summary": {
                "taskCount": 0,
                "blockedWordCount": 0,
                "greyoutWindowCount": 0,
                "dailyAllowanceCount": 0
              }
            }
        """.trimIndent()
        val output = ByteArrayOutputStream()

        BackupFileManager.writeStream(output, json)
        val bytes = output.toByteArray()
        val restored = BackupFileManager.readStream(ByteArrayInputStream(bytes))

        assertEquals(json, restored)
        assertEquals(json.toByteArray(Charsets.UTF_8).toList(), bytes.toList())
        assertTrue(BackupSerializer.parseAndValidate(restored) is BackupParseResult.Success)
    }

    @Test
    fun malformedJsonIsRejectedByExistingSerializerPreflight() {
        val readText = BackupFileManager.readStream(
            ByteArrayInputStream("""{"kind":"${BackupSerializer.BACKUP_KIND}","settings":{,}}""".toByteArray()),
        )

        val result = BackupSerializer.parseAndValidate(readText)

        assertTrue(result is BackupParseResult.Failure)
        assertTrue((result as BackupParseResult.Failure).message.contains("JSON"))
    }
}
