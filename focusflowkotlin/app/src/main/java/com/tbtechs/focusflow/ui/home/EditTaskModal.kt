package com.tbtechs.focusflow.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import com.tbtechs.focusflow.data.model.Task
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

private val editDurationOptions = listOf(25, 45, 60, 90, 120)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskModal(task: Task, onDismiss: () -> Unit, onSave: (Task) -> Unit, onDelete: () -> Unit) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var notes by remember(task.id) { mutableStateOf(task.description.orEmpty()) }
    var notesExpanded by remember(task.id) { mutableStateOf(task.description != null) }
    var time by remember(task.id) { mutableStateOf(task.startTime.asEditableTime()) }
    var duration by remember(task.id) { mutableStateOf(task.durationMinutes) }
    var customDuration by remember(task.id) { mutableStateOf(task.durationMinutes !in editDurationOptions) }
    var customDurationValue by remember(task.id) { mutableStateOf(task.durationMinutes.toString()) }
    var priority by remember(task.id) { mutableStateOf(task.priority) }
    var tags by remember(task.id) { mutableStateOf(task.tags) }
    var newTag by remember(task.id) { mutableStateOf("") }
    var focusMode by remember(task.id) { mutableStateOf(task.focusMode) }
    var allowedPackages by remember(task.id) { mutableStateOf(task.focusAllowedPackages?.joinToString(", ").orEmpty()) }
    var showAllowedApps by remember(task.id) { mutableStateOf(false) }
    var showError by remember(task.id) { mutableStateOf(false) }
    val usesGlobalApps = task.focusAllowedPackages == null
    val savedTaskColor = priorityColor(priority).toArgb().toColorString()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
            Text("Edit task", style = MaterialTheme.typography.headlineSmall)
            HomeTextField(title, { title = it }, "Task title")
            Button(onClick = { notesExpanded = !notesExpanded }) { Text(if (notesExpanded) "Hide notes" else "Notes (optional)") }
            if (notesExpanded) HomeTextField(notes, { notes = it }, "Notes", singleLine = false)
            HomeTextField(time, { time = it }, "Start time (HH:mm)")
            Text("Duration", style = MaterialTheme.typography.titleSmall)
            ChoiceRow(
                choices = editDurationOptions.map(Int::asDurationLabel) + "Custom",
                selected = if (customDuration) "Custom" else duration.asDurationLabel(),
                onSelect = { selection ->
                    customDuration = selection == "Custom"
                    if (!customDuration) duration = editDurationOptions.first { it.asDurationLabel() == selection }
                },
            )
            if (customDuration) HomeTextField(customDurationValue, { customDurationValue = it }, "Custom minutes")
            Text("Priority", style = MaterialTheme.typography.titleSmall)
            ChoiceRow(priorityOptions, priority, onSelect = { priority = it })
            Text("Tags", style = MaterialTheme.typography.titleSmall)
            if (tags.isNotEmpty()) Row { tags.forEach { tag -> FilterChip(selected = false, onClick = { tags = tags - tag }, label = { Text("#$tag ×") }) } }
            HomeTextField(newTag, { newTag = it }, "Add a tag")
            Button(onClick = {
                val candidate = newTag.trim().removePrefix("#")
                if (candidate.isNotBlank() && candidate !in tags) tags = tags + candidate
                newTag = ""
            }) { Text("Add tag") }
            ToggleRow("Focus Mode", "Block distracting apps during this task", focusMode) { focusMode = it }
            if (focusMode) {
                // The source always lets existing tasks customise their allow list.
                Button(onClick = { showAllowedApps = true }) {
                    Text(if (usesGlobalApps && allowedPackages.isBlank()) "Customize allowed apps (using global list)" else "Customize allowed apps")
                }
            }
            if (showError) Text("Enter a title, valid time, and a duration of at least 5 minutes.", color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                val finalDuration = if (customDuration) customDurationValue.toIntOrNull() else duration
                val start = runCatching {
                    Instant.parse(task.startTime).atZone(ZoneId.systemDefault()).toLocalDate().atTime(LocalTime.parse(time)).atZone(ZoneId.systemDefault()).toInstant()
                }.getOrNull()
                if (title.isBlank() || start == null || finalDuration == null || finalDuration < 5) {
                    showError = true
                } else {
                    onSave(task.copy(
                        title = title.trim(),
                        description = notes.trim().ifBlank { null },
                        startTime = start.toString(),
                        endTime = start.plusSeconds(finalDuration * 60L).toString(),
                        durationMinutes = finalDuration,
                        priority = priority,
                        tags = tags,
                        color = savedTaskColor,
                        focusMode = focusMode,
                        focusAllowedPackages = if (!focusMode || (usesGlobalApps && allowedPackages.isBlank())) null else allowedPackages.toPackageList(),
                        updatedAt = Instant.now().toString(),
                    ))
                }
            }) { Text("Save") }
            Button(onClick = onDelete) { Text("Delete task") }
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    }
    if (showAllowedApps) AllowedAppsDialog(
        value = allowedPackages,
        onDismiss = { showAllowedApps = false },
        onSave = { allowedPackages = it; showAllowedApps = false },
    )
}

private fun String.asEditableTime(): String = runCatching {
    Instant.parse(this).atZone(ZoneId.systemDefault()).toLocalTime().toString().take(5)
}.getOrDefault("")
