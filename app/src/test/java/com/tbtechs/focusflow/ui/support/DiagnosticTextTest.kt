package com.tbtechs.focusflow.ui.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticTextTest {
    private fun report(
        description: String = "It crashed",
        logs: String = "",
        errorDetails: String = "",
    ) = DiagnosticsReport(description, logs, DiagnosticsReportType.BUG, errorDetails)

    private fun body(r: DiagnosticsReport, includeLogs: Boolean = true, note: String? = null) =
        buildDiagnosticsEmailBody(r, "1.2.3", "14", 34, includeLogs, note)

    @Test
    fun longLogsKeepTheNewestLinesAndCutOnALineBoundary() {
        val lines = (1..2_000).map { "2026-11-09T10:00:00.${"%03d".format(it % 1000)}Z [ERROR] [Tag] entry number $it" }
        val out = sanitizeDiagnosticLogs(lines.joinToString("\n"), 3_000)
        assertTrue(out.length <= 3_000)
        assertTrue(out.startsWith("… (older entries omitted)\n"))
        assertTrue(out.trimEnd().endsWith("entry number 2000"))
        assertFalse(out.contains("entry number 1\n"))
        // every kept line is whole
        out.lines().drop(1).filter { it.isNotBlank() }.forEach { assertTrue(it, it.startsWith("2026-11-09")) }
    }

    @Test
    fun shortLogsAreUntouchedApartFromRedaction() {
        val out = sanitizeDiagnosticLogs("a https://x.test/p b\nuser me@example.com\npin=1234", 1_000)
        assertEquals("a [redacted-url] b\nuser [redacted-email]\npin=[redacted]", out)
    }

    @Test
    fun theOldHeadTruncationBugIsGone() {
        val logs = "old line\n".repeat(5_000) + "THE ERROR YOU REPORT"
        val out = sanitizeDiagnosticLogs(logs, 18_000)
        assertTrue(out.contains("THE ERROR YOU REPORT"))
    }

    @Test
    fun theUsersOwnMessageKeepsItsContactAddressAndLinks() {
        val msg = "Reach me at me@example.com or see https://img.example/shot.png. password: hunter2"
        val out = sanitizeDiagnosticMessage(msg, 2_000)
        assertTrue(out.contains("me@example.com"))
        assertTrue(out.contains("https://img.example/shot.png"))
        assertTrue(out.contains("password=[redacted]"))
        assertFalse(out.contains("hunter2"))
    }

    @Test
    fun bodyHasMessageErrorDetailsAndNewestLogs() {
        val r = report(
            description = "Reply to me@example.com",
            logs = "line1\nline2",
            errorDetails = "Error message: boom\nStack trace:\nat a.b.C(c.kt:1) see https://x.test",
        )
        val text = body(r)
        assertTrue(text.contains("App version: 1.2.3"))
        assertTrue(text.contains("Android version: 14 (API 34)"))
        assertTrue(text.contains("User message:\nReply to me@example.com"))
        assertTrue(text.contains("Error details:\nError message: boom"))
        assertTrue(text.contains("[redacted-url]"))
        assertTrue(text.contains("Sanitized diagnostic details (newest last):\nline1\nline2"))
        assertTrue(text.indexOf("Error details:") < text.indexOf("Sanitized diagnostic details"))
    }

    @Test
    fun errorDetailsSurviveAHugeLogBecauseTheyAreSeparate() {
        val r = report(logs = "x\n".repeat(50_000), errorDetails = "Error message: keep me")
        val text = body(r)
        assertTrue(text.contains("Error message: keep me"))
        assertTrue(text.contains("older entries omitted"))
    }

    @Test
    fun blankMessageAndNoLogsAreHandled() {
        val text = body(report(description = "   ", logs = ""))
        assertTrue(text.contains("(no description provided)"))
        assertFalse(text.contains("Sanitized diagnostic details"))
        assertFalse(text.contains("Error details:"))
    }

    @Test
    fun attachmentVariantOmitsLogsAndNotesTheFile() {
        val text = body(report(logs = "secret-ish line"), includeLogs = false, note = "The full diagnostic log is attached as a .txt file.")
        assertFalse(text.contains("secret-ish line"))
        assertTrue(text.contains("attached as a .txt file"))
    }

    @Test
    fun fileTextHoldsEverythingSanitized() {
        val logs = (1..3_000).joinToString("\n") { "line $it https://x.test" }
        val text = buildDiagnosticsFileText(report(logs = logs, errorDetails = "Error message: boom"), "1.2.3", "14", 34)
        assertTrue(text.startsWith("FocusFlow diagnostic log\n"))
        assertTrue(text.contains("line 1 [redacted-url]"))
        assertTrue(text.contains("line 3000 [redacted-url]"))
        assertTrue(text.contains("Error message: boom"))
        assertFalse(text.contains("https://x.test"))
    }

    @Test
    fun logFormattingAndTime() {
        val entries = listOf(
            DiagnosticLogEntry("2026-11-09T12:28:06.269Z", DiagnosticLogLevel.ERROR, "Task alarms", "failed"),
        )
        assertEquals("2026-11-09T12:28:06.269Z [ERROR] [Task alarms] failed", formatDiagnosticLogs(entries))
        assertEquals("12:28:06.269", formatDiagnosticTime("2026-11-09T12:28:06.269Z"))
    }
}
