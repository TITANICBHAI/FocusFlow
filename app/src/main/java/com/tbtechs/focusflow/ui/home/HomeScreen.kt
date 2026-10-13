package com.tbtechs.focusflow.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.tbtechs.focusflow.ui.focus.ActiveStatusIndicator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.ui.theme.scaledSp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.ui.AppBootViewModel
import com.tbtechs.focusflow.ui.FocusSessionViewModel
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.TaskViewModel
import com.tbtechs.focusflow.ui.common.PinType
import com.tbtechs.focusflow.ui.common.PinVerifyModal
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowTextScale
import com.tbtechs.focusflow.ui.navigation.RouteTextScaleContext
import com.tbtechs.focusflow.ui.navigation.Routes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Native Schedule / Tasks tab (Screens 6a, 6c).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    taskViewModel: TaskViewModel = viewModel(factory = TaskViewModel.Factory),
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    focusSessionViewModel: FocusSessionViewModel = viewModel(factory = FocusSessionViewModel.Factory),
    appBootViewModel: AppBootViewModel = viewModel(factory = AppBootViewModel.Factory),
    onOpenActiveBlocks: () -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    val dimensions = LocalFocusFlowDimensions.current
    val tasks by taskViewModel.tasks.collectAsState()
    val focusSession by focusSessionViewModel.focusSession.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()
    val isLoading by appBootViewModel.isLoading.collectAsState()
    val isDbReady by appBootViewModel.isDbReady.collectAsState()
    val isDbUnrecoverable by appBootViewModel.isDbUnrecoverable.collectAsState()
    val todayTasks = remember(tasks) { tasks.filter(Task::isToday) }
    val activeTask = remember(todayTasks, focusSession) {
        todayTasks
            .filter { it.status !in setOf("completed", "skipped") }
            .firstOrNull { it.id == focusSession?.taskId }
            ?: todayTasks.firstOrNull(Task::isRunningNow)
    }
    val bannerTask = activeTask
        ?: todayTasks
            .filter(Task::isAwaitingDecision)
            .maxByOrNull { it.endTime }

    var addOpen by remember { mutableStateOf(false) }
    var detailTask by remember { mutableStateOf<Task?>(null) }
    var editTask by remember { mutableStateOf<Task?>(null) }
    var extendTask by remember { mutableStateOf<Task?>(null) }
    var deleteTask by remember { mutableStateOf<Task?>(null) }
    var skipTask by remember { mutableStateOf<Task?>(null) }

    fun completeAndMaybeStop(task: Task) {
        taskViewModel.completeTask(task.id)
        if ((!settings.keepFocusActiveUntilTaskEnd || task.isAwaitingDecision()) &&
            focusSessionViewModel.focusSession.value?.taskId == task.id
        ) {
            focusSessionViewModel.stopFocusMode()
        }
    }

    fun skipAndMaybeStop(task: Task) {
        taskViewModel.skipTask(task.id)
        if ((!settings.keepFocusActiveUntilTaskEnd || task.isAwaitingDecision()) &&
            focusSessionViewModel.focusSession.value?.taskId == task.id
        ) {
            focusSessionViewModel.stopFocusMode()
        }
    }

    when {
        isDbUnrecoverable -> DatabaseUnavailable(onRetry = appBootViewModel::retry)
        isLoading || !isDbReady -> LoadingSchedule()
        else -> Scaffold(
            containerColor = DarkBackground,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
            topBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(RefHeader)
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                                fontSize = 22.scaledSp,
                                fontWeight = FontWeight.Bold,
                                color = RefText,
                                modifier = Modifier.weight(1f),
                            )
                            ActiveStatusIndicator(
                                focusSession = focusSession,
                                settings = settings,
                                onOpenActiveBlocks = onOpenActiveBlocks,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (todayTasks.isEmpty()) {
                                "No tasks today"
                            } else {
                                val completed = todayTasks.count { it.status == "completed" }
                                val skipped = todayTasks.count { it.status == "skipped" }
                                "$completed/${todayTasks.size} tasks done · $skipped skipped"
                            },
                            fontSize = 13.scaledSp,
                            color = RefSecondary,
                        )
                    }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { addOpen = true },
                    modifier = Modifier.size(56.dp),
                    containerColor = BrandPrimary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add task", modifier = Modifier.size(28.dp))
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                CompositionLocalProvider(
                    LocalFocusFlowTextScale provides (
                        settings.screenTextScales[
                            RouteTextScaleContext.screenScaleKey(
                                Routes.HOME,
                                RouteTextScaleContext.HOME_TASK_LIST_SCREEN,
                            )
                        ] ?: LocalFocusFlowTextScale.current
                    ),
                ) {
                    if (bannerTask != null) {
                        ActiveTaskBanner(
                            task = bannerTask,
                            onOpen = { detailTask = bannerTask },
                            onComplete = { completeAndMaybeStop(bannerTask) },
                            onExtend = { extendTask = bannerTask },
                            onSkip = { skipTask = bannerTask },
                            onStartFocus = { focusSessionViewModel.startFocusMode(bannerTask.id) },
                        )
                    }
                    if (todayTasks.isEmpty()) {
                        EmptySchedule(
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(
                                top = 4.dp,
                                bottom = dimensions.bottomContentPadding,
                            ),
                        ) {
                            items(todayTasks, key = Task::id) { task ->
                                TaskCard(
                                    task = task,
                                    isActive = task.id == activeTask?.id,
                                    onOpen = { detailTask = task },
                                    onComplete = { taskId ->
                                        todayTasks.firstOrNull { it.id == taskId }
                                            ?.let(::completeAndMaybeStop)
                                            ?: taskViewModel.completeTask(taskId)
                                    },
                                    onSkip = { skipTask = task },
                                    onExtend = { extendTask = task },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (addOpen) {
        CompositionLocalProvider(
            LocalFocusFlowTextScale provides (
                settings.screenTextScales[
                    RouteTextScaleContext.screenScaleKey(
                        Routes.HOME,
                        RouteTextScaleContext.HOME_QUICK_ADD_SCREEN,
                    )
                ] ?: LocalFocusFlowTextScale.current
                ),
        ) {
            QuickAddModal(
                onDismiss = { addOpen = false },
                onSave = taskViewModel::addTask,
                settingsViewModel = settingsViewModel,
            )
        }
    }
    detailTask?.let { task ->
        CompositionLocalProvider(
            LocalFocusFlowTextScale provides (
                settings.screenTextScales[
                    RouteTextScaleContext.screenScaleKey(
                        Routes.HOME,
                        RouteTextScaleContext.HOME_TASK_DETAILS_SCREEN,
                    )
                ] ?: LocalFocusFlowTextScale.current
                ),
        ) {
            TaskDetailModal(
                task = task,
                onDismiss = { detailTask = null },
                onComplete = { completeAndMaybeStop(task); detailTask = null },
                onSkip = { detailTask = null; skipTask = task },
                onExtend = { detailTask = null; extendTask = task },
                onStartFocus = { focusSessionViewModel.startFocusMode(task.id); detailTask = null },
                onEdit = { detailTask = null; editTask = task },
            )
        }
    }
    editTask?.let { task ->
        CompositionLocalProvider(
            LocalFocusFlowTextScale provides (
                settings.screenTextScales[
                    RouteTextScaleContext.screenScaleKey(
                        Routes.HOME,
                        RouteTextScaleContext.HOME_EDIT_TASK_SCREEN,
                    )
                ] ?: LocalFocusFlowTextScale.current
                ),
        ) {
            EditTaskModal(
                task = task,
                onDismiss = { editTask = null },
                onSave = { taskViewModel.updateTask(it); editTask = null },
                onDelete = {
                    if (settingsViewModel.isFocusPinSet()) {
                        deleteTask = task
                    } else {
                        taskViewModel.deleteTask(task.id)
                        editTask = null
                    }
                },
            )
        }
    }
    extendTask?.let { task ->
        ExtendTaskDialog(
            task = task,
            onDismiss = { extendTask = null },
            onExtend = { minutes -> taskViewModel.extendTaskTime(task.id, minutes); extendTask = null },
        )
    }
    deleteTask?.let { task ->
        PinVerifyModal(
            visible = true,
            pinType = PinType.FOCUS,
            title = "Delete Task",
            description = "Enter your focus session password to delete this task.",
            verify = settingsViewModel::verifyFocusPin,
            onVerified = {
                taskViewModel.deleteTask(task.id, it)
                deleteTask = null
                editTask = null
            },
            onCancel = { deleteTask = null },
        )
    }
    skipTask?.let { task ->
        AlertDialog(
            onDismissRequest = { skipTask = null },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Skip task?", fontWeight = FontWeight.Bold) },
            text = { Text("Skip “${task.title}”?") },
            confirmButton = {
                Button(
                    onClick = { skipAndMaybeStop(task); skipTask = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
             shape = RoundedCornerShape(14.dp),
                ) { Text("Skip") }
            },
            dismissButton = {
                TextButton(onClick = { skipTask = null }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }
}

@Composable
private fun LoadingSchedule() = Column(
    modifier = Modifier
        .fillMaxSize()
        .background(DarkBackground),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
) {
    CircularProgressIndicator(color = BrandPrimary)
    Spacer(Modifier.height(16.dp))
    Text("Loading your schedule…", fontSize = 16.scaledSp, color = DarkTextSecondary)
}

@Composable
private fun DatabaseUnavailable(onRetry: () -> Unit) = Column(
    modifier = Modifier
        .fillMaxSize()
        .background(DarkBackground)
        .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
) {
    Icon(
        Icons.Outlined.CloudOff,
        contentDescription = null,
        tint = Color(0xFFEF4444),
        modifier = Modifier.size(48.dp),
    )
    Spacer(Modifier.height(12.dp))
    Text(
        "Your schedule is unavailable",
        fontSize = 20.scaledSp,
        fontWeight = FontWeight.Bold,
        color = DarkTextPrimary,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "Your tasks are safe. FocusFlow could not open its local database.",
        fontSize = 14.scaledSp,
        color = DarkTextSecondary,
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onRetry,
        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text("Retry")
    }
}

/**
 * Screenshot 6a: Empty state
 */
@Composable
private fun EmptySchedule(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Icon(
             Icons.Outlined.CalendarMonth,
            contentDescription = null,
            tint = RefMuted,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "No tasks scheduled for today",
            fontSize = 18.scaledSp,
            fontWeight = FontWeight.SemiBold,
            color = RefSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap + to add your first task",
            fontSize = 13.scaledSp,
            color = RefMuted,
        )
    }
}

internal fun Task.isToday(): Boolean = runCatching {
    Instant.parse(startTime).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
}.getOrDefault(false)

internal fun Task.isRunningNow(): Boolean = runCatching {
    status !in setOf("completed", "skipped") &&
        Instant.parse(startTime).isBefore(Instant.now()) &&
        Instant.parse(endTime).isAfter(Instant.now())
}.getOrDefault(false)

internal fun Task.isAwaitingDecision(): Boolean = runCatching {
    status !in setOf("completed", "skipped") &&
        Instant.parse(endTime).isBefore(Instant.now())
}.getOrDefault(false)
