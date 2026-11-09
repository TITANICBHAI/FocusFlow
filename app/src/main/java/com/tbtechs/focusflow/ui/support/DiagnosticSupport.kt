package com.tbtechs.focusflow.ui.support

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

private const val SHARED_DIR = "shared"
private const val FILE_MAX_AGE_MS = 24L * 60 * 60 * 1000

private fun appVersionName(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    }.getOrDefault("unknown")

/**
 * Opens a user-reviewed email draft. Nothing is sent automatically.
 *
 * Standard `mailto:` drafts cannot carry attachments, so the newest sanitized log lines go in the
 * body (older ones are dropped, never the newest). Use [shareDiagnosticsFile] for the whole log.
 */
fun openDiagnosticsEmailDraft(context: Context, report: DiagnosticsReport): Boolean {
    val body = buildDiagnosticsEmailBody(
        report = report,
        appVersion = appVersionName(context),
        androidRelease = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
    )
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$SUPPORT_EMAIL")
        putExtra(Intent.EXTRA_SUBJECT, report.type.subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    return startSafely(context, intent)
}

/**
 * Writes the whole sanitized log to a .txt in the app's cache and opens the share sheet with it
 * attached and the support address pre-filled. Nothing is sent until the person picks an app and
 * taps Send. Files older than a day are deleted first.
 */
fun shareDiagnosticsFile(context: Context, report: DiagnosticsReport): Boolean {
    val version = appVersionName(context)
    return try {
        val dir = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { if (now - it.lastModified() > FILE_MAX_AGE_MS) it.delete() }
        val file = File(dir, "focusflow-diagnostics-$now.txt")
        file.writeText(buildDiagnosticsFileText(report, version, Build.VERSION.RELEASE, Build.VERSION.SDK_INT))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val body = buildDiagnosticsEmailBody(
            report = report,
            appVersion = version,
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            includeLogs = false,
            attachmentNote = "The full diagnostic log is attached as a .txt file.",
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, report.type.subject)
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("diagnostic log", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startSafely(context, Intent.createChooser(send, "Send diagnostic log"))
    } catch (_: Exception) {
        false
    }
}

private fun startSafely(context: Context, intent: Intent): Boolean =
    try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
