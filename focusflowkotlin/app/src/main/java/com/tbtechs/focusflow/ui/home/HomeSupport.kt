package com.tbtechs.focusflow.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tbtechs.focusflow.data.model.Task

@Composable
internal fun ActiveTaskBanner(
    task: Task,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    onExtend: () -> Unit,
    onSkip: () -> Unit,
    onStartFocus: () -> Unit,
) {
    Card(onClick = onOpen, modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
        Row {
            Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                Text(if (task.isRunningNow()) "NOW" else "TIME'S UP", style = MaterialTheme.typography.labelMedium)
                Text(task.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (task.isRunningNow()) "Until ${task.endTime.asLocalTime()}" else "Ended ${task.endTime.asLocalTime()} · pick one",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onComplete) { Icon(Icons.Outlined.Check, "Complete") }
            IconButton(onClick = onExtend) { Icon(Icons.Outlined.Add, "Extend") }
            if (!task.isRunningNow()) IconButton(onClick = onSkip) { Icon(Icons.Outlined.Close, "Skip") }
            else if (task.focusMode) IconButton(onClick = onStartFocus) { Icon(Icons.Outlined.Shield, "Start focus") }
        }
    }
}

@Composable
internal fun HomeTextField(value: String, onValueChange: (String) -> Unit, label: String, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun ExtendTaskDialog(task: Task, onDismiss: () -> Unit, onExtend: (Int) -> Unit) {
    var minutes by remember { mutableStateOf("15") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extend ${task.title}") },
        text = {
            Column {
                Text("Add time to this task.")
                HomeTextField(value = minutes, onValueChange = { minutes = it }, label = "Minutes")
            }
        },
        confirmButton = { Button(onClick = { minutes.toIntOrNull()?.takeIf { it > 0 }?.let(onExtend) }) { Text("Extend") } },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } },
    )
}
