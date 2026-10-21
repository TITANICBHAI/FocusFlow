package com.tbtechs.focusflow.ui.home.subtask

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tbtechs.focusflow.data.model.Subtask
import com.tbtechs.focusflow.data.model.SubtaskLimits
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.ui.home.RefBackground
import com.tbtechs.focusflow.ui.home.RefCard
import com.tbtechs.focusflow.ui.home.RefSecondary
import com.tbtechs.focusflow.ui.home.RefText
import com.tbtechs.focusflow.ui.theme.scaledSp

@Composable
internal fun SubtaskAuthoringDialog(
    task: Task,
    availableTasks: List<Task>,
    onDismiss: () -> Unit,
    onSave: (List<Subtask>) -> Unit,
) {
    var subtasks by remember(task.id) { mutableStateOf(task.subtasks) }
    var editingSubtask by remember(task.id) { mutableStateOf<Subtask?>(null) }
    var editingId by remember(task.id) { mutableStateOf<String?>(null) }
    var deleteTarget by remember(task.id) { mutableStateOf<Subtask?>(null) }
    var duplicatePickerOpen by remember(task.id) { mutableStateOf(false) }
    var duplicateSource by remember(task.id) { mutableStateOf<Task?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(RefBackground)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            AuthoringHeader(
                title = if (editingSubtask == null) "Manage subtasks" else {
                    if (editingId == null) "Add subtask" else "Edit subtask"
                },
                onCancel = {
                    if (editingSubtask != null) {
                        editingSubtask = null
                        editingId = null
                    } else {
                        onDismiss()
                    }
                },
                cancelLabel = if (editingSubtask == null) "Cancel" else "Back",
            )

            val currentEditor = editingSubtask
            if (currentEditor != null) {
                SubtaskEditorScreen(
                    initial = currentEditor,
                    onCancel = {
                        editingSubtask = null
                        editingId = null
                    },
                    onSave = { edited ->
                        val originalId = editingId
                        val saved = if (originalId != null) {
                            edited.copy(id = originalId)
                        } else {
                            edited.copy(
                                id = uniqueSubtaskId(
                                    title = edited.title,
                                    existingIds = subtasks.mapTo(mutableSetOf(), Subtask::id),
                                ),
                            )
                        }
                        subtasks = if (originalId == null) {
                            subtasks + saved
                        } else {
                            subtasks.map { if (it.id == originalId) saved else it }
                        }
                        editingSubtask = null
                        editingId = null
                    },
                )
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Changes stay in this draft until you save.",
                        color = RefSecondary,
                        fontSize = 13.scaledSp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                editingId = null
                                editingSubtask = Subtask(id = "", title = "")
                            },
                            enabled = subtasks.size < SubtaskLimits.MAX_SUBTASKS,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add")
                        }
                        OutlinedButton(
                            onClick = {
                                duplicateSource = null
                                duplicatePickerOpen = true
                            },
                            enabled = subtasks.size < SubtaskLimits.MAX_SUBTASKS &&
                                availableTasks.any { it.id != task.id && it.subtasks.isNotEmpty() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Copy from task")
                        }
                    }
                    Text(
                        text = "${subtasks.size} of ${SubtaskLimits.MAX_SUBTASKS} subtasks",
                        color = RefSecondary,
                        fontSize = 12.scaledSp,
                    )
                    if (subtasks.isEmpty()) {
                        AuthoringEmptyState(modifier = Modifier.weight(1f))
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(subtasks, key = { _, item -> item.id }) { index, item ->
                                SubtaskAuthoringRow(
                                    subtask = item,
                                    index = index,
                                    total = subtasks.size,
                                    onEdit = {
                                        editingId = item.id
                                        editingSubtask = item
                                    },
                                    onMove = { delta ->
                                        val destination = index + delta
                                        if (destination in subtasks.indices) {
                                            subtasks = subtasks.toMutableList().apply {
                                                add(destination, removeAt(index))
                                            }
                                        }
                                    },
                                    onDelete = { deleteTarget = item },
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            onSave(subtasks)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Save subtasks")
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete subtask?") },
            text = { Text("“${target.title}” and its steps will be removed when you save this list.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        subtasks = subtasks.filterNot { it.id == target.id }
                        deleteTarget = null
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }

    if (duplicatePickerOpen) {
        val sources = availableTasks.filter { it.id != task.id && it.subtasks.isNotEmpty() }
        AlertDialog(
            onDismissRequest = {
                duplicatePickerOpen = false
                duplicateSource = null
            },
            title = {
                Text(
                    duplicateSource?.let { "Copy from ${it.title}" } ?: "Choose a task",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                if (duplicateSource == null) {
                    if (sources.isEmpty()) {
                        Text("No other tasks have subtasks to copy.")
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(sources, key = Task::id) { source ->
                                TextButton(
                                    onClick = { duplicateSource = source },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(source.title, color = RefText, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${source.subtasks.size} subtasks",
                                            color = RefSecondary,
                                            fontSize = 12.scaledSp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(duplicateSource!!.subtasks, key = Subtask::id) { sourceSubtask ->
                            TextButton(
                                onClick = {
                                    if (subtasks.size < SubtaskLimits.MAX_SUBTASKS) {
                                        val id = uniqueSubtaskId(
                                            title = sourceSubtask.title,
                                            existingIds = subtasks.mapTo(mutableSetOf(), Subtask::id),
                                        )
                                        subtasks = subtasks + sourceSubtask.copy(
                                            id = id,
                                            done = false,
                                            setsDone = 0,
                                        )
                                    }
                                    duplicatePickerOpen = false
                                    duplicateSource = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(sourceSubtask.title, color = RefText, fontWeight = FontWeight.SemiBold)
                                    sourceSubtask.subtitle?.takeIf(String::isNotBlank)?.let {
                                        Text(it, color = RefSecondary, fontSize = 12.scaledSp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (duplicateSource != null) duplicateSource = null
                        else duplicatePickerOpen = false
                    },
                ) {
                    Text(if (duplicateSource == null) "Cancel" else "Back")
                }
            },
            dismissButton = {
                if (duplicateSource != null) {
                    TextButton(
                        onClick = {
                            duplicatePickerOpen = false
                            duplicateSource = null
                        },
                    ) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun AuthoringHeader(
    title: String,
    onCancel: () -> Unit,
    cancelLabel: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel) { Text(cancelLabel) }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = RefText,
            fontSize = 18.scaledSp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(52.dp))
    }
}

@Composable
private fun AuthoringEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(RefCard, RoundedCornerShape(16.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No subtasks yet", color = RefText, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(
            "Add items to break this task into smaller steps.",
            color = RefSecondary,
            fontSize = 13.scaledSp,
        )
    }
}

@Composable
private fun SubtaskAuthoringRow(
    subtask: Subtask,
    index: Int,
    total: Int,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RefCard, RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subtask.title,
                    color = RefText,
                    fontSize = 14.scaledSp,
                    fontWeight = FontWeight.SemiBold,
                )
                subtask.subtitle?.takeIf(String::isNotBlank)?.let {
                    Text(it, color = RefSecondary, fontSize = 12.scaledSp)
                }
                Text(
                    text = "${subtask.steps.size} steps",
                    color = RefSecondary,
                    fontSize = 11.scaledSp,
                )
            }
            IconButton(onClick = { onMove(-1) }, enabled = index > 0, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move ${subtask.title} up")
            }
            IconButton(onClick = { onMove(1) }, enabled = index < total - 1, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move ${subtask.title} down")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Edit")
            }
            OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Delete")
            }
        }
    }
}

private fun uniqueSubtaskId(title: String, existingIds: Set<String>): String {
    val base = title
        .trim()
        .lowercase()
        .map { char ->
            if (char in 'a'..'z' || char in '0'..'9' || char == '_' || char == '-') char else '-'
        }
        .joinToString("")
        .trim('-')
        .take(SubtaskLimits.MAX_ID_CHARS - 8)
        .ifBlank { "subtask" }
    var candidate = base
    var suffix = 2
    while (candidate in existingIds) {
        candidate = "${base.take(SubtaskLimits.MAX_ID_CHARS - suffix.toString().length - 1)}-$suffix"
        suffix++
    }
    return candidate
}
