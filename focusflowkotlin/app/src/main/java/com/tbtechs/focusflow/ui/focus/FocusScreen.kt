package com.tbtechs.focusflow.ui.focus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tbtechs.focusflow.data.model.StandaloneBlockConfig
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.ui.FocusSessionViewModel
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.TaskViewModel
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    taskViewModel: TaskViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    focusSessionViewModel: FocusSessionViewModel = viewModel(),
    onOpenActiveBlocks: () -> Unit = {},
    onOpenSchedule: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
) {
    val tasks by taskViewModel.tasks.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()
    val session by focusSessionViewModel.focusSession.collectAsState()
    val task = session?.taskId?.let { id -> tasks.firstOrNull { it.id == id } }
        ?: tasks.firstOrNull(Task::isRunningNow)
    val isFocusing = session?.isActive == true
    val standaloneActive = settings.standaloneBlockActive &&
        settings.standaloneBlockPackages.isNotEmpty() &&
        settings.standaloneBlockUntilMs > System.currentTimeMillis()

    var showDefenseHint by remember { mutableStateOf(true) }
    var showStandaloneEditor by remember { mutableStateOf(false) }
    var showExtend by remember { mutableStateOf(false) }
    var showStopConfirmation by remember { mutableStateOf(false) }
    var showEmergencyConfirmation by remember { mutableStateOf(false) }
    var showFocusPinUnavailable by remember { mutableStateOf(false) }
    var showCompleteConfirmation by remember { mutableStateOf(false) }
    var showSkipConfirmation by remember { mutableStateOf(false) }
    var activePanel by remember { mutableStateOf("task") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus") },
                actions = {
                    ActiveHeaderButton(session, settings, onOpenActiveBlocks)
                },
            )
        },
    ) { padding ->
        Column(
            modifier = androidx.compose.ui.Modifier.fillMaxSize().padding(padding),
        ) {
            if (showDefenseHint) {
                Card(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                    Row {
                        Icon(Icons.Outlined.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Always-On Blocking and related protection tools have moved to the Defense tab.", modifier = androidx.compose.ui.Modifier.weight(1f))
                        androidx.compose.material3.IconButton(onClick = { showDefenseHint = false }) {
                            // NEEDS: persisted focus-defense-hint dismissal preference.
                            Icon(Icons.Outlined.Close, contentDescription = "Dismiss Defense hint")
                        }
                    }
                }
            }
            if (!isFocusing) PermissionBanner(onOpenPermissions)
            when {
                task == null && standaloneActive -> StandaloneBlockPanel(
                    settings = settings,
                    onAddTime = { minutes ->
                        settingsViewModel.setStandaloneBlock(
                            StandaloneBlockConfig(true, settings.standaloneBlockPackages, settings.standaloneBlockUntilMs + minutes * 60_000L),
                        )
                    },
                    onEdit = { showStandaloneEditor = true },
                )
                task == null && isFocusing -> OrphanedFocusPanel(onStop = { showStopConfirmation = true })
                task == null -> ReadyToFocusPanel(onOpenSchedule, onOpenStandalone = { showStandaloneEditor = true })
                standaloneActive && activePanel == "block" -> {
                    FocusPanelSwitcher(activePanel, { activePanel = it })
                    StandaloneBlockPanel(
                        settings = settings,
                        onAddTime = { minutes ->
                            settingsViewModel.setStandaloneBlock(StandaloneBlockConfig(true, settings.standaloneBlockPackages, settings.standaloneBlockUntilMs + minutes * 60_000L))
                        },
                        onEdit = { showStandaloneEditor = true },
                    )
                }
                else -> {
                    if (standaloneActive) FocusPanelSwitcher(activePanel, { activePanel = it })
                    TaskFocusPanel(
                        task = task,
                        isFocusing = isFocusing,
                        allowedPackages = session?.allowedPackages.orEmpty(),
                        otherActiveCount = tasks.count(Task::isRunningNow) - if (task.isRunningNow()) 1 else 0,
                        onStart = { focusSessionViewModel.startFocusMode(task.id) },
                        onStop = { showStopConfirmation = true },
                        onComplete = { showCompleteConfirmation = true },
                        onExtend = { showExtend = true },
                        onSkip = { showSkipConfirmation = true },
                        onOpenSchedule = onOpenSchedule,
                        onOpenStandalone = { showStandaloneEditor = true },
                        onEmergencyOverride = { showEmergencyConfirmation = true },
                        standaloneActive = standaloneActive,
                        onOpenStandalonePanel = { activePanel = "block" },
                    )
                }
            }
        }
    }

    if (showStandaloneEditor) StandaloneBlockEditor(
        existingPackages = settings.standaloneBlockPackages,
        onDismiss = { showStandaloneEditor = false },
        onSave = { packages, minutes ->
            settingsViewModel.setStandaloneBlock(
                StandaloneBlockConfig(
                    active = packages.isNotEmpty(),
                    packages = packages,
                    untilMs = System.currentTimeMillis() + minutes * 60_000L,
                ),
            )
            showStandaloneEditor = false
        },
    )
    if (showExtend && task != null) ExtendModal(
        taskName = task.title,
        onDismiss = { showExtend = false },
        onExtend = { minutes -> taskViewModel.extendTaskTime(task.id, minutes); showExtend = false },
    )
    if (showStopConfirmation) AlertDialog(
        onDismissRequest = { showStopConfirmation = false },
        title = { Text("Stop Focus?") },
        text = { Text("Ending Focus stops app blocking for this task.") },
        confirmButton = {
            Button(onClick = {
                showStopConfirmation = false
                // NEEDS: focus-session PIN verification and rotation APIs; SettingsViewModel PIN is the defense PIN.
                showFocusPinUnavailable = true
            }) { Text("Stop") }
        },
        dismissButton = { Button(onClick = { showStopConfirmation = false }) { Text("Cancel") } },
    )
    if (showEmergencyConfirmation && task != null) AlertDialog(
        onDismissRequest = { showEmergencyConfirmation = false },
        title = { Text("Emergency Override") },
        text = { Text("This stops Focus Mode. Use it only in a genuine emergency.") },
        confirmButton = {
            Button(onClick = {
                showEmergencyConfirmation = false
                // NEEDS: FocusSessionViewModel.recordOverride(taskId, reason) before stopping the session.
                showFocusPinUnavailable = true
            }) { Text("Override") }
        },
        dismissButton = { Button(onClick = { showEmergencyConfirmation = false }) { Text("Cancel") } },
    )
    if (showFocusPinUnavailable) AlertDialog(
        onDismissRequest = { showFocusPinUnavailable = false },
        title = { Text("Focus session password required") },
        text = { Text("This action needs the focus-session PIN contract. It is not exposed by the current ViewModels, so FocusFlow will keep blocking rather than bypassing the gate.") },
        confirmButton = { Button(onClick = { showFocusPinUnavailable = false }) { Text("OK") } },
    )
    if (showCompleteConfirmation && task != null) AlertDialog(
        onDismissRequest = { showCompleteConfirmation = false },
        title = { Text("Complete task?") },
        text = { Text("Mark ${task.title} as done?") },
        confirmButton = { Button(onClick = { taskViewModel.completeTask(task.id); showCompleteConfirmation = false }) { Text("Done") } },
        dismissButton = { Button(onClick = { showCompleteConfirmation = false }) { Text("Cancel") } },
    )
    if (showSkipConfirmation && task != null) AlertDialog(
        onDismissRequest = { showSkipConfirmation = false },
        title = { Text("Skip task?") },
        text = { Text("Skip ${task.title}?") },
        confirmButton = { Button(onClick = { taskViewModel.skipTask(task.id); showSkipConfirmation = false }) { Text("Skip") } },
        dismissButton = { Button(onClick = { showSkipConfirmation = false }) { Text("Cancel") } },
    )
}

@Composable
private fun PermissionBanner(onOpenPermissions: () -> Unit) = Card(onClick = onOpenPermissions, modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
    Row {
        Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
        Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
            Text("Accessibility permission needed", style = MaterialTheme.typography.titleSmall)
            Text("Focus Mode can’t block apps without Accessibility access. Tap to open Settings.")
        }
    }
}

@Composable
private fun ReadyToFocusPanel(onOpenSchedule: () -> Unit, onOpenStandalone: () -> Unit) = Column(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
    Text("Ready to focus?", style = MaterialTheme.typography.headlineSmall)
    Text("Choose a task from Schedule to start a focused session.")
    Button(onClick = onOpenSchedule) { Icon(Icons.Outlined.CalendarToday, null); Text("Open Schedule") }
    Button(onClick = onOpenStandalone) { Icon(Icons.Outlined.Block, null); Text("Block Apps Without a Task") }
    Text("Quick block presets and recurring-schedule editing need the standalone block editor contract.", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun OrphanedFocusPanel(onStop: () -> Unit) = Column(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
    Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.tertiary)
    Text("Focus session needs attention", style = MaterialTheme.typography.headlineSmall)
    Text("A focus session is active, but its task is no longer available. Stop it here to clear blocking safely.")
    Button(onClick = onStop) { Icon(Icons.Outlined.StopCircle, null); Text("Stop Focus") }
}

@Composable
private fun TaskFocusPanel(
    task: Task,
    isFocusing: Boolean,
    allowedPackages: List<String>,
    otherActiveCount: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onComplete: () -> Unit,
    onExtend: () -> Unit,
    onSkip: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenStandalone: () -> Unit,
    onEmergencyOverride: () -> Unit,
    standaloneActive: Boolean,
    onOpenStandalonePanel: () -> Unit,
) = LazyColumn(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
    item {
        val clock by rememberClock()
        val remaining = task.remainingMillis(clock)
        val progress = task.progressNow(clock)
        Card(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
            Column {
                Text(if (isFocusing) "Focus Mode Active" else if (remaining < 0) "Task ended — choose next action" else "Task scheduled")
                Text(task.remainingLabel(clock), style = MaterialTheme.typography.displayMedium, color = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Text(task.title, style = MaterialTheme.typography.headlineSmall)
                Text("${task.startTime.focusTime()} – ${task.endTime.focusTime()}")
                androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                Text(if (remaining < 0) "Overdue" else "${(progress * 100).toInt()}% complete")
                if (task.tags.isNotEmpty()) Text(task.tags.joinToString(" ") { "#$it" })
            }
        }
        if (isFocusing) {
            Card(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                Text("Pomodoro")
                Text("Pomodoro phase and break controls are waiting for FocusSessionViewModel break-state APIs.")
                Button(onClick = {}, enabled = false) { Text("Take break") }
            }
        }
        if (otherActiveCount > 0) Button(onClick = onOpenSchedule) { Text("+$otherActiveCount more active") }
        if (remaining < 0 && task.status !in setOf("completed", "skipped")) {
            Text("Time’s up — what next?")
            Row {
                Button(onClick = onComplete) { Text("Done") }
                Button(onClick = onExtend) { Text("Extend") }
                Button(onClick = onSkip) { Text("Skip") }
            }
        }
        if (isFocusing) {
            Text(if (allowedPackages.isEmpty()) "Allowed: all apps" else "Allowed: ${allowedPackages.joinToString()}")
        }
        if (!isFocusing) {
            Button(onClick = onStart) {
                // NEEDS: focus-session PIN rotation before activation when a session PIN already exists.
                Icon(Icons.Outlined.Security, null)
                Text("Activate Focus")
            }
        } else Button(onClick = onStop) { Icon(Icons.Outlined.StopCircle, null); Text("Stop Focus") }
        Row {
            Button(onClick = onComplete) { Text("Done") }
            Button(onClick = onExtend) { Text("Extend") }
        }
        if (!standaloneActive) Button(onClick = onOpenStandalone) { Text("Block apps while I work") }
        if (isFocusing) Button(onClick = onEmergencyOverride) { Text("Emergency Override") }
        if (standaloneActive) Button(onClick = onOpenStandalonePanel) { Text("Block running · tap to manage") }
    }
}

@Composable
private fun FocusPanelSwitcher(activePanel: String, onSwitch: (String) -> Unit) = SingleChoiceSegmentedButtonRow {
    SegmentedButton(activePanel == "task", { onSwitch("task") }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Focus Task") }
    SegmentedButton(activePanel == "block", { onSwitch("block") }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Block Active") }
}

@Composable
private fun StandaloneBlockPanel(
    settings: com.tbtechs.focusflow.data.model.AppSettings,
    onAddTime: (Int) -> Unit,
    onEdit: () -> Unit,
) {
    val now by rememberClock()
    val remaining = (settings.standaloneBlockUntilMs - now).coerceAtLeast(0L)
    Column(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
        Text("Apps Blocked", style = MaterialTheme.typography.headlineSmall)
        Text("Standalone block is running. You can add apps or extend it, but cannot stop it early.")
        Text(remaining.focusDuration(), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.error)
        Text("${settings.standaloneBlockPackages.size} apps blocked · cannot stop early")
        Row { listOf(30, 60, 120, 240).forEach { minutes -> FilterChip(false, { onAddTime(minutes) }, label = { Text("+$minutes m") }) } }
        Button(onClick = onEdit) { Text("Add More Apps to Block") }
        Text("Quick presets, VPN package selection, daily allowance pairing, and schedule editing need fields not exposed by AppSettings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StandaloneBlockEditor(existingPackages: List<String>, onDismiss: () -> Unit, onSave: (List<String>, Int) -> Unit) {
    var packages by remember { mutableStateOf(existingPackages.joinToString(", ")) }
    var minutes by remember { mutableStateOf("60") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block apps without a task") },
        text = {
            Column {
                OutlinedTextField(packages, { packages = it }, label = { Text("Package names (comma separated)") })
                OutlinedTextField(minutes, { minutes = it }, label = { Text("Block for minutes") })
            }
        },
        confirmButton = { Button(onClick = { minutes.toIntOrNull()?.takeIf { it > 0 }?.let { onSave(packages.split(',').map(String::trim).filter(String::isNotBlank), it) } }) { Text("Start block") } },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun rememberClock(): androidx.compose.runtime.State<Long> {
    val clock = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { clock.longValue = System.currentTimeMillis(); delay(1_000) } }
    return clock
}

private fun Task.remainingMillis(clock: Long): Long = runCatching { Duration.between(Instant.ofEpochMilli(clock), Instant.parse(endTime)).toMillis() }.getOrDefault(0L)
private fun Task.progressNow(clock: Long): Float = runCatching {
    val total = Duration.between(Instant.parse(startTime), Instant.parse(endTime)).toMillis().coerceAtLeast(1L)
    (1f - remainingMillis(clock).toFloat() / total).coerceIn(0f, 1f)
}.getOrDefault(0f)
private fun Task.remainingLabel(clock: Long): String {
    val seconds = remainingMillis(clock) / 1_000L
    return if (seconds < 0) "+${-seconds / 60}m" else "%d:%02d".format(seconds / 60, seconds % 60)
}
private fun String.focusTime(): String = runCatching { Instant.parse(this).atZone(java.time.ZoneId.systemDefault()).toLocalTime().toString().take(5) }.getOrDefault(this)
private fun Long.focusDuration(): String = "%d:%02d".format(this / 60_000L, (this / 1_000L) % 60L)
private fun Task.isRunningNow(): Boolean = status !in setOf("completed", "skipped") && runCatching { Instant.parse(startTime).isBefore(Instant.now()) && Instant.parse(endTime).isAfter(Instant.now()) }.getOrDefault(false)
