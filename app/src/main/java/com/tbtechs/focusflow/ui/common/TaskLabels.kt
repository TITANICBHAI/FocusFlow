package com.tbtechs.focusflow.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tbtechs.focusflow.R

/**
 * Display names for the stored task priority and status codes. The codes ("low", "completed", ...)
 * are data and never change with the language; only the text shown to the person does.
 * Unknown codes fall back to the code itself with a capital letter.
 */
@Composable
fun priorityLabel(priority: String): String = when (priority.lowercase()) {
    "low" -> stringResource(R.string.task_priority_low)
    "medium" -> stringResource(R.string.task_priority_medium)
    "high" -> stringResource(R.string.task_priority_high)
    "critical" -> stringResource(R.string.task_priority_critical)
    else -> priority.replaceFirstChar(Char::titlecase)
}

@Composable
fun statusLabel(status: String): String = when (status.lowercase()) {
    "pending" -> stringResource(R.string.task_status_pending)
    "active" -> stringResource(R.string.task_status_active)
    "completed" -> stringResource(R.string.task_status_completed)
    "skipped" -> stringResource(R.string.task_status_skipped)
    else -> status.replaceFirstChar(Char::titlecase)
}
