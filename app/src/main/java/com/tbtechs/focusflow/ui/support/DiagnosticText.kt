package com.tbtechs.focusflow.ui.support

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiagnosticLogEntry(
    val timestamp: String,
    val level: DiagnosticLogLevel,
    val tag: String,
    val message: String,
)

enum class DiagnosticLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

enum class DiagnosticsReportType(
    val label: String,
    val subject: String,
    val intro: String,
) {
    BUG("Bug report", "FocusFlow issue report", "I am reporting an issue with FocusFlow."),
    FEEDBACK("Feedback / opinion", "FocusFlow feedback", "I would like to share feedback about FocusFlow."),
    REVIEW("App review", "FocusFlow app review", "I would like to share a review of FocusFlow."),
}

/**
 * @property logs recent log lines, oldest first.
 * @property errorDetails the crash message and stack trace for a screen that failed, if any.
 */
data class DiagnosticsReport(
    val description: String,
    val logs: String,
    val type: DiagnosticsReportType,
    val errorDetails: String = "",
)

internal const val SUPPORT_EMAIL = "tbtechsdev@gmail.com"
internal const val MAX_DESCRIPTION_LENGTH = 2_000
internal const val MAX_LOG_LENGTH = 18_000
internal const val MAX_ERROR_DETAILS_LENGTH = 4_000
private const val OMITTED_MARKER = "… (older entries omitted)\n"

fun formatDiagnosticLogs(logs: List<DiagnosticLogEntry>): String =
    logs.joinToString("\n") { entry ->
        "${entry.timestamp} [${entry.level}] [${entry.tag}] ${entry.message}"
    }

fun formatDiagnosticTime(timestamp: String): String =
    timestamp.substringAfter('T', timestamp).take(12)

private val urlRegex = Regex("""\bhttps?://\S+""", RegexOption.IGNORE_CASE)
private val wwwRegex = Regex("""\bwww\.\S+""", RegexOption.IGNORE_CASE)
private val emailRegex = Regex("""\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b""", RegexOption.IGNORE_CASE)
private val secretRegex = Regex("""\b(password|passwd|token|secret|api[_-]?key|pin)\s*[:=]\s*\S+""", RegexOption.IGNORE_CASE)

/**
 * Removes links, e-mail addresses and obvious secrets from log text, then keeps the first
 * [maxLength] characters. Use [sanitizeDiagnosticLogs] for logs, where the newest lines matter.
 */
fun sanitizeDiagnosticText(value: String, maxLength: Int): String =
    redactSensitive(value).take(maxLength)

/**
 * The person's own message: only obvious secrets are removed. Links and e-mail addresses stay,
 * because the reply address or a link to a screenshot is often the point of the message.
 */
fun sanitizeDiagnosticMessage(value: String, maxLength: Int): String =
    value.replace(secretRegex, "$1=[redacted]").take(maxLength)

/**
 * Sanitizes log lines and, when they are too long, keeps the NEWEST ones (the error you are
 * reporting is at the end, not the start). The cut always lands on a line boundary.
 */
fun sanitizeDiagnosticLogs(value: String, maxLength: Int): String {
    val clean = redactSensitive(value)
    if (clean.length <= maxLength) return clean
    val budget = (maxLength - OMITTED_MARKER.length).coerceAtLeast(0)
    val tail = clean.takeLast(budget)
    val firstBreak = tail.indexOf('\n')
    val whole = if (firstBreak >= 0 && clean[clean.length - budget - 1] != '\n') tail.substring(firstBreak + 1) else tail
    return OMITTED_MARKER + whole
}

private fun redactSensitive(value: String): String =
    value
        .replace(urlRegex, "[redacted-url]")
        .replace(wwwRegex, "[redacted-url]")
        .replace(emailRegex, "[redacted-email]")
        .replace(secretRegex, "$1=[redacted]")

fun nowDiagnosticTimestamp(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())

/** The e-mail body. Pure, so it can be tested without a device. */
fun buildDiagnosticsEmailBody(
    report: DiagnosticsReport,
    appVersion: String,
    androidRelease: String,
    sdkInt: Int,
    includeLogs: Boolean = true,
    attachmentNote: String? = null,
): String {
    val description = sanitizeDiagnosticMessage(report.description.trim(), MAX_DESCRIPTION_LENGTH)
        .ifBlank { "(no description provided)" }
    val errorDetails = redactSensitive(report.errorDetails.trim()).take(MAX_ERROR_DETAILS_LENGTH)
    val logs = if (includeLogs) sanitizeDiagnosticLogs(report.logs, MAX_LOG_LENGTH) else ""
    return buildString {
        appendLine("Hello FocusFlow team,")
        appendLine()
        appendLine(report.type.intro)
        appendLine()
        appendLine("App version: $appVersion")
        appendLine("Android version: $androidRelease (API $sdkInt)")
        appendLine()
        appendLine("User message:")
        appendLine(description)
        if (errorDetails.isNotBlank()) {
            appendLine()
            appendLine("Error details:")
            appendLine(errorDetails)
        }
        if (logs.isNotBlank()) {
            appendLine()
            appendLine("Sanitized diagnostic details (newest last):")
            appendLine(logs)
        }
        if (attachmentNote != null) {
            appendLine()
            appendLine(attachmentNote)
        }
        appendLine()
        appendLine("Please review this draft and tap Send when you are ready.")
    }
}

/** The text saved in the attached log file: everything, sanitized, nothing cut. */
fun buildDiagnosticsFileText(report: DiagnosticsReport, appVersion: String, androidRelease: String, sdkInt: Int): String =
    buildString {
        appendLine("FocusFlow diagnostic log")
        appendLine("App version: $appVersion")
        appendLine("Android version: $androidRelease (API $sdkInt)")
        appendLine("Created: ${nowDiagnosticTimestamp()}")
        appendLine("Links, e-mail addresses and obvious secrets are removed.")
        appendLine("---")
        val errorDetails = redactSensitive(report.errorDetails.trim())
        if (errorDetails.isNotBlank()) {
            appendLine("Error details:")
            appendLine(errorDetails)
            appendLine("---")
        }
        appendLine(redactSensitive(report.logs))
    }
