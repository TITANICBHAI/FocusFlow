package com.tbtechs.focusflow.ui.support

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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

data class DiagnosticsReport(
    val description: String,
    val logs: String,
    val type: DiagnosticsReportType,
)

private const val SUPPORT_EMAIL = "tbtechsdev@gmail.com"
private const val MAX_DESCRIPTION_LENGTH = 2_000
private const val MAX_LOG_LENGTH = 18_000

fun formatDiagnosticLogs(logs: List<DiagnosticLogEntry>): String =
    logs.joinToString("\n") { entry ->
        "${entry.timestamp} [${entry.level}] [${entry.tag}] ${entry.message}"
    }

fun formatDiagnosticTime(timestamp: String): String =
    timestamp.substringAfter('T', timestamp).take(12)

fun sanitizeDiagnosticText(value: String, maxLength: Int): String =
    value
        .replace(Regex("""\bhttps?://\S+""", RegexOption.IGNORE_CASE), "[redacted-url]")
        .replace(Regex("""\bwww\.\S+""", RegexOption.IGNORE_CASE), "[redacted-url]")
        .replace(
            Regex("""\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b""", RegexOption.IGNORE_CASE),
            "[redacted-email]",
        )
        .replace(
            Regex("""\b(password|passwd|token|secret|api[_-]?key)\s*[:=]\s*\S+""", RegexOption.IGNORE_CASE),
            "$1=[redacted]",
        )
        .take(maxLength)

fun nowDiagnosticTimestamp(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())

/**
 * Opens a user-reviewed email draft. Nothing is sent automatically.
 *
 * Android's standard email intents do not guarantee support for attachments
 * across email clients, so sanitized diagnostic details are included in the
 * draft body instead of silently failing to attach them.
 */
fun openDiagnosticsEmailDraft(context: Context, report: DiagnosticsReport): Boolean {
    val description = sanitizeDiagnosticText(
        report.description.trim(),
        MAX_DESCRIPTION_LENGTH,
    ).ifBlank { "(no description provided)" }
    val logs = sanitizeDiagnosticText(report.logs, MAX_LOG_LENGTH)
    val version = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    }.getOrDefault("unknown")
    val body = buildString {
        appendLine("Hello FocusFlow team,")
        appendLine()
        appendLine(report.type.intro)
        appendLine()
        appendLine("App version: $version")
        appendLine("Android version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        appendLine("User message:")
        appendLine(description)
        if (logs.isNotBlank()) {
            appendLine()
            appendLine("Sanitized diagnostic details:")
            appendLine(logs)
        }
        appendLine()
        appendLine("Please review this draft and tap Send when you are ready.")
    }

    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$SUPPORT_EMAIL")
        putExtra(Intent.EXTRA_SUBJECT, report.type.subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }

    return try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}