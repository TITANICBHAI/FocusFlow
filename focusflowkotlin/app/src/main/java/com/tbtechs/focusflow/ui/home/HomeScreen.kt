package com.tbtechs.focusflow.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.ui.AppBootViewModel
import com.tbtechs.focusflow.ui.FocusSessionViewModel
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.TaskViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The "home" destination from ARCHITECTURE.md §3.1. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    taskViewModel: TaskViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    focusSessionViewModel: FocusSessionViewModel = viewModel(),
    appBootViewModel: AppBootViewModel = viewModel(),
    onOpenActiveBlocks: () -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    val tasks by taskViewModel.tasks.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()
    val focusSession by focusSessionViewModel.focusSession.collectAsState()
    val isLoading by appBootViewModel.isLoading.collectAsState()
    val isDbReady by appBootViewModel.isDbReady.collectAsState()
    val isDbUnrecoverable by appBootViewModel.isDbUnrecoverable.collectAsState()
    val todayTasks = remember(tasks) { tasks.filter(Task::isToday) }
    val activeTask = remember(todayTasks, focusSession) {
        todayTasks.firstOrNull { it.id == focusSession?.taskId }
            ?: todayTasks.firstOrNull(Task::isRunningNow)
    }

    var displayTimeline by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var detailTask by remember { mutableStateOf<Task?>(null) }
    var editTask by remember { mutableStateOf<Task?>(null) }
    var extendTask by remember { mutableStateOf<Task?>(null) }
    var deleteTask by remember { mutableStateOf<Task?>(null) }
    var skipTask by remember { mutableStateOf<Task?>(null) }

    when {
        isDbUnrecoverable -> DatabaseUnavailable(onRetry = appBootViewModel::retry)
        isLoading || !isDbReady -> LoadingSchedule()
        else -> Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")))
                            Text(
                                text = if (todayTasks.isEmpty()) "No tasks today" else "${todayTasks.count { it.status == "completed" }}/${todayTasks.size} tasks done",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onRefresh) {
                            // NEEDS: TaskViewModel.refresh(); tasks is currently an always-current Room StateFlow.
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh schedule")
                        }
                        IconButton(onClick = onOpenActiveBlocks) {
                            Icon(Icons.Outlined.ShowChart, contentDescription = "Open Active blocks")
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { addOpen = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add task")
                }
            },
        ) { innerPadding ->
            Column(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (activeTask != null) {
                    ActiveTaskBanner(
                        task = activeTask,
                        onOpen = { detailTask = activeTask },
                        onComplete = { taskViewModel.completeTask(activeTask.id) },
                        onExtend = { extendTask = activeTask },
                        onSkip = { skipTask = activeTask },
                        onStartFocus = { focusSessionViewModel.startFocusMode(activeTask.id) },
                    )
                }
                SingleChoiceSegmentedButtonRow(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !displayTimeline,
                        onClick = { displayTimeline = false },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("List") }
                    SegmentedButton(
                        selected = displayTimeline,
                        onClick = { displayTimeline = true },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("Timeline") }
                }
                if (displayTimeline) {
                    TimelineView(
                        tasks = todayTasks,
                        onTaskClick = { detailTask = it },
                        modifier = androidx.compose.ui.Modifier.weight(1f),
                    )
                } else if (todayTasks.isEmpty()) {
                    EmptySchedule(modifier = androidx.compose.ui.Modifier.weight(1f))
                } else {
                    LazyColumn(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                        items(todayTasks, key = Task::id) { task ->
                            TaskCard(
                                task = task,
                                isActive = task.id == activeTask?.id,
                                onOpen = { detailTask = task },
                                onComplete = taskViewModel::completeTask,
                                onSkip = { skipTask = task },
                                onExtend = { extendTask = task },
                                onStartFocus = focusSessionViewModel::startFocusMode,
                            )
                        }
                    }
                }
            }
        }
    }

    if (addOpen) QuickAddModal(onDismiss = { addOpen = false }, onSave = taskViewModel::addTask)
    detailTask?.let { task ->
        TaskDetailModal(
            task = task,
            onDismiss = { detailTask = null },
            onComplete = { taskViewModel.completeTask(task.id); detailTask = null },
            onSkip = { detailTask = null; skipTask = task },
            onExtend = { detailTask = null; extendTask = task },
            onStartFocus = { focusSessionViewModel.startFocusMode(task.id); detailTask = null },
            onEdit = { detailTask = null; editTask = task },
        )
    }
    editTask?.let { task ->
        EditTaskModal(
            task = task,
            onDismiss = { editTask = null },
            onSave = { taskViewModel.updateTask(it); editTask = null },
            onDelete = { deleteTask = task },
        )
    }
    extendTask?.let { task ->
        ExtendTaskDialog(
            task = task,
            onDismiss = { extendTask = null },
            onExtend = { minutes -> taskViewModel.extendTaskTime(task.id, minutes); extendTask = null },
        )
    }
    deleteTask?.let { task ->
        DeleteTaskDialog(
            task = task,
            pinRequired = settings.pinProtectionEnabled,
            onDismiss = { deleteTask = null },
            onConfirm = { pin ->
                // NEEDS: a focus-session PIN verifier; SettingsViewModel currently exposes only the defense PIN.
                if (!settings.pinProtectionEnabled || settingsViewModel.verifyPin(pin)) {
                    taskViewModel.deleteTask(task.id)
                    deleteTask = null
                    editTask = null
                }
            },
        )
    }
    skipTask?.let { task ->
        AlertDialog(
            onDismissRequest = { skipTask = null },
            title = { Text("Skip task?") },
            text = { Text("Skip “${task.title}”?" ) },
            confirmButton = { Button(onClick = { taskViewModel.skipTask(task.id); skipTask = null }) { Text("Skip") } },
            dismissButton = { FilledTonalButton(onClick = { skipTask = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LoadingSchedule() = Column(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
    Spacer(modifier = androidx.compose.ui.Modifier.weight(1f))
    CircularProgressIndicator()
    Text("Loading your schedule", style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = androidx.compose.ui.Modifier.weight(1f))
}

@Composable
private fun DatabaseUnavailable(onRetry: () -> Unit) = Column(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
    Spacer(modifier = androidx.compose.ui.Modifier.weight(1f))
    Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
    Text("Your schedule is unavailable", style = MaterialTheme.typography.headlineSmall)
    Text("Your tasks are safe. FocusFlow could not open its local database.")
    Button(onClick = onRetry) { Text("Retry") }
    Spacer(modifier = androidx.compose.ui.Modifier.weight(1f))
}

@Composable
private fun EmptySchedule(modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) =
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = androidx.compose.ui.Modifier.weight(1f))
        Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        Text("No tasks scheduled for today", style = MaterialTheme.typography.titleMedium)
        Text("Tap + to add your first task", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = androidx.compose.ui.Modifier.weight(1f))
    }

@Composable
private fun DeleteTaskDialog(task: Task, pinRequired: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete task?") },
        text = {
            Column {
                Text("Delete “${task.title}”? This cannot be undone.")
                if (pinRequired) HomeTextField(value = pin, onValueChange = { pin = it }, label = "Focus session password")
            }
        },
        confirmButton = { Button(onClick = { onConfirm(pin) }) { Text("Delete") } },
        dismissButton = { FilledTonalButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

internal fun Task.isToday(): Boolean = runCatching {
    Instant.parse(startTime).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
}.getOrDefault(false)

internal fun Task.isRunningNow(): Boolean = runCatching {
    status !in setOf("completed", "skipped") && Instant.parse(startTime).isBefore(Instant.now()) && Instant.parse(endTime).isAfter(Instant.now())
}.getOrDefault(false)
