package com.tbtechs.focusflow.ui.focus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.StandaloneBlockConfig
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.data.repository.AllowanceUsage
import com.tbtechs.focusflow.domain.FocusPinManager
import com.tbtechs.focusflow.ui.FocusSessionViewModel
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.TaskViewModel
import org.json.JSONArray
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveBlockScreen(
    taskViewModel: TaskViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    focusSessionViewModel: FocusSessionViewModel = viewModel(),
    onBack: () -> Unit = {},
    onOpenFocus: () -> Unit = {},
    onOpenAlwaysOn: () -> Unit = {},
    onOpenDefense: () -> Unit = {},
    onOpenKeywordBlocker: () -> Unit = {},
    onOpenVpnBlockList: () -> Unit = {},
) {
    val focusPinManager = remember { FocusPinManager(LocalContext.current) }
    val tasks by taskViewModel.tasks.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()
    val session by focusSessionViewModel.focusSession.collectAsState()
    val violation by focusSessionViewModel.focusViolationApp.collectAsState()
    val allowanceSnapshot by settingsViewModel.allowanceSnapshot.collectAsState()
    val todayFocusMinutes by focusSessionViewModel.todayFocusMinutes.collectAsState()
    val todayOverrideCount by focusSessionViewModel.todayOverrideCount.collectAsState()
    val focusTask = session?.taskId?.let { taskId -> tasks.firstOrNull { it.id == taskId } }
    val standaloneActive = settings.standaloneBlockActive && settings.standaloneBlockPackages.isNotEmpty() && settings.standaloneBlockUntilMs > System.currentTimeMillis()
    val alwaysOnActive = settings.alwaysBlockEnabled && settings.alwaysBlockPackages.isNotEmpty()
    val allowancePackages = settings.dailyAllowancePackages()
    val nothingActive = session?.isActive != true && !standaloneActive && !alwaysOnActive && allowancePackages.isEmpty() && settings.blockedWords.isEmpty() && !settings.networkBlockEnabled

    var expanded by remember { mutableStateOf("allowance") }
    var stopFocusConfirmation by remember { mutableStateOf(false) }
    var clearStandaloneConfirmation by remember { mutableStateOf(false) }
    var defensePinGate by remember { mutableStateOf(false) }
    var focusPinUnavailable by remember { mutableStateOf(false) }
    var focusPin by remember { mutableStateOf("") }

    Column(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Column { Text("Active"); Text("Live status of your protections", style = MaterialTheme.typography.labelMedium) } },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } },
            actions = { Icon(if (nothingActive) Icons.Outlined.Security else Icons.Outlined.Key, contentDescription = if (nothingActive) "No protection active" else "Protection active", tint = if (nothingActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary) },
        )
        LazyColumn(modifier = androidx.compose.ui.Modifier.weight(1f)) {
            item {
                ActiveSummary(nothingActive)
                StatusCard("Focus Session", if (session?.isActive == true) "Active" else "Not active") {
                    if (session?.isActive == true) {
                        Text("Task: ${focusTask?.title ?: "Task unavailable"}")
                        focusTask?.let { Text("Ends at ${it.endTime.activeTime()}") }
                        Button(onClick = { stopFocusConfirmation = true }) { Icon(Icons.Outlined.StopCircle, null); Text("Stop Focus") }
                    } else Text("No task-based focus session is running.")
                }
                StatusCard("Standalone Block", if (standaloneActive) "Active" else "Not active") {
                    Text("Apps: ${if (settings.standaloneBlockPackages.isEmpty()) "None selected" else "${settings.standaloneBlockPackages.size} blocked"}")
                    Text("Until: ${if (settings.standaloneBlockUntilMs > System.currentTimeMillis()) settings.standaloneBlockUntilMs.activeDateTime() else "No timer running"}")
                    when {
                        standaloneActive -> Button(onClick = onOpenFocus) { Text("Add time or apps") }
                        settings.standaloneBlockPackages.isNotEmpty() -> Button(onClick = {
                            if (settings.pinProtectionEnabled) defensePinGate = true else clearStandaloneConfirmation = true
                        }) { Text("Clear saved apps") }
                    }
                }
                ExpandableStatusCard("Always-On Apps", if (alwaysOnActive) "Active" else "Not active", settings.alwaysBlockPackages.isNotEmpty(), expanded == "always", onToggle = {
                    expanded = if (expanded == "always") "" else "always"
                }) {
                    Text(if (settings.alwaysBlockPackages.isEmpty()) "No always-on apps" else "${settings.alwaysBlockPackages.size} blocked continuously")
                    if (expanded == "always") PackageNames(settings.alwaysBlockPackages)
                    Button(onClick = onOpenAlwaysOn) { Text("Manage Always-On apps") }
                }
                ExpandableStatusCard("Daily Allowance", if (allowancePackages.isEmpty()) "Not configured" else "${allowancePackages.size} apps configured", allowancePackages.isNotEmpty(), expanded == "allowance", onToggle = {
                    expanded = if (expanded == "allowance") "" else "allowance"
                }) {
                    if (allowancePackages.isEmpty()) Text("No per-app daily limits are configured.")
                    else if (expanded == "allowance") {
                        allowancePackages.forEach { packageName ->
                            AllowancePackageStatus(
                                packageName = packageName,
                                usage = allowanceSnapshot.usageByPackage[packageName],
                                activeSession = allowanceSnapshot.activeSessionPackage == packageName,
                            )
                        }
                        allowanceSnapshot.activeSessionPackage?.let { activePackage ->
                            Text(
                                "Active allowance session: $activePackage" +
                                    if (allowanceSnapshot.activeSessionEndMs > 0L) {
                                        " · ends ${allowanceSnapshot.activeSessionEndMs.activeDateTime()}"
                                    } else {
                                        ""
                                    },
                            )
                        }
                    }
                    else Text("${allowancePackages.size} apps tracked · tap to see configured apps.")
                    Button(onClick = onOpenDefense) { Text("Manage daily allowance") }
                }
                ExpandableStatusCard("Keyword Blocker", if (settings.blockedWords.isEmpty()) "Not active" else "Active", settings.blockedWords.isNotEmpty(), expanded == "keywords", onToggle = {
                    expanded = if (expanded == "keywords") "" else "keywords"
                }) {
                    Text(if (settings.blockedWords.isEmpty()) "No keywords configured" else "${settings.blockedWords.size} active immediately")
                    if (expanded == "keywords") Text(settings.blockedWords.joinToString())
                    Button(onClick = onOpenKeywordBlocker) { Text("Manage keywords") }
                }
                ExpandableStatusCard("VPN Blocking", if (settings.networkBlockEnabled) "Configured" else "Not configured", settings.networkBlockEnabled, expanded == "vpn", onToggle = {
                    expanded = if (expanded == "vpn") "" else "vpn"
                }) {
                    // NEEDS: VpnRepository status, failed-package list, and policy generation state in a ViewModel.
                    Text(if (settings.networkBlockEnabled) "VPN blocking is configured. Live service status is unavailable here." else "No VPN apps configured.")
                    Button(onClick = onOpenVpnBlockList) { Text("Manage VPN blocking") }
                }
                val activeSchedules = settings.recurringBlockSchedules.filter { it.isActiveNow() }
                ExpandableStatusCard(
                    "Scheduled Blocks",
                    when {
                        settings.recurringBlockSchedules.isEmpty() -> "Not configured"
                        activeSchedules.isNotEmpty() -> "${activeSchedules.size} active · ${settings.recurringBlockSchedules.size} configured"
                        else -> "${settings.recurringBlockSchedules.size} configured · none active"
                    },
                    settings.recurringBlockSchedules.isNotEmpty(),
                    expanded == "schedules",
                    onToggle = { expanded = if (expanded == "schedules") "" else "schedules" },
                ) {
                    if (settings.recurringBlockSchedules.isEmpty()) Text("No recurring scheduled blocks are configured.")
                    else if (expanded == "schedules") settings.recurringBlockSchedules.forEach { schedule ->
                        Text("${schedule.packages.size} apps · ${schedule.startHour}:00–${schedule.endHour}:00 · ${schedule.daysOfWeek.joinToString()}")
                    } else Text("Tap to see configured days, times, and blocked app groups.")
                    Button(onClick = onOpenDefense) { Text("Manage scheduled blocks") }
                }
                TodaySummary(
                    tasks = tasks,
                    session = session,
                    violation = violation,
                    todayFocusMinutes = todayFocusMinutes,
                    todayOverrideCount = todayOverrideCount,
                )
            }
        }
    }

    if (defensePinGate) PinGateDialog(
        onDismiss = { defensePinGate = false },
        onVerified = { pin ->
            if (settingsViewModel.verifyPin(pin)) {
                defensePinGate = false
                clearStandaloneConfirmation = true
            }
        },
    )
    if (clearStandaloneConfirmation) AlertDialog(
        onDismissRequest = { clearStandaloneConfirmation = false },
        title = { Text("Clear standalone apps?") },
        text = { Text("Remove ${settings.standaloneBlockPackages.size} saved apps from the timed block list?") },
        confirmButton = { Button(onClick = {
            settingsViewModel.setStandaloneBlock(StandaloneBlockConfig(false, emptyList(), 0L))
            clearStandaloneConfirmation = false
        }) { Text("Clear") } },
        dismissButton = { Button(onClick = { clearStandaloneConfirmation = false }) { Text("Cancel") } },
    )
    if (stopFocusConfirmation) AlertDialog(
        onDismissRequest = { stopFocusConfirmation = false },
        title = { Text("Stop focus session?") },
        text = { Text("This ends app blocking for the current task.") },
        confirmButton = { Button(onClick = {
            stopFocusConfirmation = false
            focusPinUnavailable = true
        }) { Text("Stop") } },
        dismissButton = { Button(onClick = { stopFocusConfirmation = false }) { Text("Cancel") } },
    )
    if (focusPinUnavailable) AlertDialog(
        onDismissRequest = { focusPinUnavailable = false },
        title = { Text("Focus session password required") },
        text = {
            OutlinedTextField(
                value = focusPin,
                onValueChange = { focusPin = it },
                label = { Text("Focus session password") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = {
                if (focusPinManager.verifyPin(focusPin)) {
                    focusSessionViewModel.stopFocusMode(focusPinManager.hash(focusPin))
                    focusPin = ""
                    focusPinUnavailable = false
                }
            }) { Text("Stop Focus") }
        },
    )
}

@Composable
private fun ActiveSummary(nothingActive: Boolean) = Card(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
    Row {
        Icon(if (nothingActive) Icons.Outlined.Security else Icons.Outlined.Key, null, tint = if (nothingActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
        Column {
            Text(if (nothingActive) "Nothing blocking right now" else "Protection is active", style = MaterialTheme.typography.titleMedium)
            Text(if (nothingActive) "Start Focus or configure a protection layer in Defense." else "This page updates as ViewModel state changes.")
        }
    }
}

@Composable
private fun StatusCard(title: String, status: String, content: @Composable () -> Unit) = Card(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
    Column { Text(title, style = MaterialTheme.typography.titleMedium); Text(status, color = MaterialTheme.colorScheme.primary); content() }
}

@Composable
private fun ExpandableStatusCard(title: String, status: String, expandable: Boolean, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) = Card(
    onClick = { if (expandable) onToggle() },
    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
) { Column { Text(title, style = MaterialTheme.typography.titleMedium); Text("$status${if (expandable) if (expanded) " · hide details" else " · show details" else ""}", color = MaterialTheme.colorScheme.primary); content() } }

@Composable
private fun PackageNames(packages: List<String>) = Column {
    // NEEDS: InstalledAppsRepository labels and icons exposed through a ViewModel.
    packages.forEach { Text(it) }
}

@Composable
private fun TodaySummary(
    tasks: List<Task>,
    session: com.tbtechs.focusflow.data.model.FocusSession?,
    violation: String?,
    todayFocusMinutes: Int,
    todayOverrideCount: Int,
) {
    val today = LocalDate.now()
    val todayTasks = tasks.filter { runCatching { Instant.parse(it.startTime).atZone(ZoneId.systemDefault()).toLocalDate() == today }.getOrDefault(false) }
    Text("TODAY", style = MaterialTheme.typography.labelLarge)
    Text(
        "${todayTasks.count { it.status == "completed" }}/${todayTasks.size} tasks · " +
            "$todayFocusMinutes m focus · $todayOverrideCount overrides" +
            if (violation != null) " · latest blocked attempt: $violation" else "",
    )
}

@Composable
private fun AllowancePackageStatus(
    packageName: String,
    usage: AllowanceUsage?,
    activeSession: Boolean,
) {
    val usageLabel = when (usage?.mode) {
        "count" -> "used ${usage.count} opens today"
        "interval" -> "used ${usage.usedMs / 60_000L} min in current window"
        "time_budget" -> "used ${usage.usedMs / 60_000L} min today"
        else -> "no usage recorded"
    }
    Text("$packageName · $usageLabel${if (activeSession) " · in use" else ""}")
}

@Composable
private fun PinGateDialog(onDismiss: () -> Unit, onVerified: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Defense Password Required") },
        text = { OutlinedTextField(pin, { pin = it }, label = { Text("Defense password") }) },
        confirmButton = { Button(onClick = { onVerified(pin) }) { Text("Continue") } },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun AppSettings.dailyAllowancePackages(): List<String> = runCatching {
    val entries = JSONArray(dailyAllowanceConfigJson ?: "[]")
    (0 until entries.length()).mapNotNull { entries.optJSONObject(it)?.optString("package")?.takeIf(String::isNotBlank) }
}.getOrDefault(emptyList())

private fun com.tbtechs.focusflow.data.model.RecurringBlockSchedule.isActiveNow(): Boolean {
    if (!enabled) return false
    val now = java.time.ZonedDateTime.now()
    val minute = now.hour * 60 + now.minute
    val start = startHour * 60
    val end = endHour * 60
    val day = now.dayOfWeek.value % 7
    return day in daysOfWeek && if (start <= end) minute in start until end else minute >= start || minute < end
}

private fun String.activeTime(): String = runCatching { Instant.parse(this).atZone(ZoneId.systemDefault()).toLocalTime().toString().take(5) }.getOrDefault(this)
private fun Long.activeDateTime(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime().toString().replace('T', ' ')
