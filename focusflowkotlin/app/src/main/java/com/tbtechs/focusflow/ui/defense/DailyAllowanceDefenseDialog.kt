package com.tbtechs.focusflow.ui.defense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.DailyAllowanceEntry
import org.json.JSONArray

@Composable
fun DailyAllowanceDefenseDialog(
    settings: AppSettings,
    onSave: (List<DailyAllowanceEntry>) -> Unit,
    onClose: () -> Unit,
) {
    var packageName by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("30") }
    val existing = remember(settings.dailyAllowanceConfigJson) {
        parseAllowanceEntries(settings.dailyAllowanceConfigJson)
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Daily Allowance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Add a per-app time budget. The native settings contract stores the resulting allowance as milliseconds.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(packageName, { packageName = it }, label = { Text("Package name") }, singleLine = true)
                OutlinedTextField(minutes, { minutes = it }, label = { Text("Minutes per day") }, singleLine = true)
                existing.forEach { entry ->
                    Text("${entry.packageName}: ${entry.dailyAllowanceMs / 60_000L} min/day")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val value = minutes.toLongOrNull()?.coerceAtLeast(1L)
                if (packageName.contains('.') && value != null) {
                    onSave(existing.filterNot { it.packageName == packageName.trim() } +
                        DailyAllowanceEntry(packageName.trim(), value * 60_000L))
                }
            }, enabled = packageName.contains('.') && minutes.toLongOrNull() != null) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(emptyList()) }) { Text("Clear") }
                TextButton(onClick = onClose) { Text("Cancel") }
            }
        },
    )
}

private fun parseAllowanceEntries(json: String?): List<DailyAllowanceEntry> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    DailyAllowanceEntry(
                        packageName = item.optString("package"),
                        dailyAllowanceMs = item.optLong("dailyAllowanceMs"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}
