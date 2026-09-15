package com.tbtechs.focusflow.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import com.tbtechs.focusflow.data.model.Task
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

private val durationOptions = listOf(15, 30, 45, 60, 90, 120)
private val priorityOptions = listOf("low", "medium", "high", "critical")
private val colorOptions = listOf("Primary", "Secondary", "Tertiary", "Error")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddModal(onDismiss: () -> Unit, onSave: (Task) -> Unit) {
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var time by remember { mutableStateOf(LocalTime.now().toString().take(5)) }
    var duration by remember { mutableStateOf(60) }
    var customDuration by remember { mutableStateOf(false) }
    var customDurationValue by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("medium") }
    var selectedColor by remember { mutableStateOf("Primary") }
    var tags by remember { mutableStateOf("") }
    var pomodoroEnabled by remember { mutableStateOf(false) }
    var focusMode by remember { mutableStateOf(false) }
    var useGlobalApps by remember { mutableStateOf(true) }
    var allowedPackages by remember { mutableStateOf("") }
    var showAllowedApps by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    val savedTaskColor = colorForName(selectedColor).toArgb().toColorString()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
            Text("New task", style = MaterialTheme.typography.headlineSmall)
            HomeTextField(title, { title = it }, "Title")
            HomeTextField(notes, { notes = it }, "Notes (optional)", singleLine = false)
            HomeTextField(date, { date = it }, "Start date (YYYY-MM-DD)")
            HomeTextField(time, { time = it }, "Start time (HH:mm)")
            Text("Duration", style = MaterialTheme.typography.titleSmall)
            ChoiceRow(
                choices = durationOptions.map(Int::asDurationLabel) + "Custom",
                selected = if (customDuration) "Custom" else duration.asDurationLabel(),
                onSelect = { selection ->
                    customDuration = selection == "Custom"
                    if (!customDuration) duration = durationOptions.first { it.asDurationLabel() == selection }
                },
            )
            if (customDuration) HomeTextField(customDurationValue, { customDurationValue = it }, "Custom minutes")
            // NEEDS: pomodoroEnabled, pomodoroDuration, and pomodoroBreak fields in AppSettings.
            ToggleRow("Pomodoro mode", "One continuous focus session when off", pomodoroEnabled) { pomodoroEnabled = it }
            Text("Priority", style = MaterialTheme.typography.titleSmall)
            ChoiceRow(priorityOptions, priority, onSelect = { priority = it })
            Text("Color", style = MaterialTheme.typography.titleSmall)
            ChoiceRow(colorOptions, selectedColor, onSelect = { selectedColor = it })
            HomeTextField(tags, { tags = it }, "Tags (comma separated)")
            ToggleRow("Enable Focus Mode", "Block distractions during this task", focusMode) { focusMode = it }
            if (focusMode) {
                // NEEDS: global allowed-app list and saved allowed-app presets in AppSettings.
                ToggleRow("Use Global Allowed List", "Use the list configured in Settings", useGlobalApps) { useGlobalApps = it }
                if (!useGlobalApps) {
                    Button(onClick = { showAllowedApps = true }) {
                        Text(if (allowedPackages.isBlank()) "Choose allowed apps" else "Edit allowed apps")
                    }
                }
            }
            if (showError) Text("Enter a title and a valid date, time, and duration.", color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                val finalDuration = if (customDuration) customDurationValue.toIntOrNull() else duration
                val start = runCatching { LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull()
                if (title.isBlank() || start == null || finalDuration == null || finalDuration <= 0) {
                    showError = true
                } else {
                    onSave(
                        Task(
                            id = UUID.randomUUID().toString(),
                            title = title.trim(),
                            description = notes.trim().ifBlank { null },
                            startTime = start.toString(),
                            endTime = start.plusSeconds(finalDuration * 60L).toString(),
                            durationMinutes = finalDuration,
                            status = "scheduled",
                            priority = priority,
                            tags = tags.split(',').map(String::trim).filter(String::isNotBlank),
                            color = savedTaskColor,
                            focusMode = focusMode,
                            focusAllowedPackages = if (!focusMode || useGlobalApps) null else allowedPackages.toPackageList(),
                            createdAt = Instant.now().toString(),
                            updatedAt = Instant.now().toString(),
                        ),
                    )
                    onDismiss()
                }
            }) { Text("Save") }
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    }
    if (showAllowedApps) AllowedAppsDialog(
        value = allowedPackages,
        onDismiss = { showAllowedApps = false },
        onSave = { allowedPackages = it; showAllowedApps = false },
    )
}

@Composable
internal fun ToggleRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
        Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun ChoiceRow(choices: List<String>, selected: String, onSelect: (String) -> Unit) = Row {
    choices.forEach { choice ->
        FilterChip(selected = selected == choice, onClick = { onSelect(choice) }, label = { Text(choice.replaceFirstChar(Char::titlecase)) })
    }
}

@Composable
internal fun colorForName(name: String) = when (name) {
    "Secondary" -> MaterialTheme.colorScheme.secondary
    "Tertiary" -> MaterialTheme.colorScheme.tertiary
    "Error" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}

internal fun Int.toColorString(): String = String.format(Locale.US, "#%08X", this)
internal fun String.toPackageList(): List<String> = split(',').map(String::trim).filter(String::isNotBlank)
