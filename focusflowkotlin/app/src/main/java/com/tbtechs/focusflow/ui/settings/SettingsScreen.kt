package com.tbtechs.focusflow.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tbtechs.focusflow.ui.AppBootViewModel
import com.tbtechs.focusflow.ui.FocusSessionViewModel
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.TaskViewModel
import com.tbtechs.focusflow.ui.support.ReportIssueModal

/** The "settings" destination from ARCHITECTURE.md §3.1. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    taskViewModel: TaskViewModel = viewModel(),
    focusSessionViewModel: FocusSessionViewModel = viewModel(),
    appBootViewModel: AppBootViewModel = viewModel(),
    isDarkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
    onExportBackup: (() -> Unit)? = null,
    onImportBackup: ((replaceTasks: Boolean) -> Unit)? = null,
    onReportIssue: (() -> Unit)? = null,
    onOpenStats: () -> Unit = {},
    onOpenChangelog: () -> Unit = {},
    onOpenPrivacyTerms: () -> Unit = {},
) {
    val settings by settingsViewModel.settings.collectAsState()
    val tasks by taskViewModel.tasks.collectAsState()
    val focusSession by focusSessionViewModel.focusSession.collectAsState()
    val isLoading by appBootViewModel.isLoading.collectAsState()
    val isDbReady by appBootViewModel.isDbReady.collectAsState()
    val context = LocalContext.current

    var overlayAppearanceVisible by remember { mutableStateOf(false) }
    var dailyAllowanceVisible by remember { mutableStateOf(false) }
    var clearAllConfirmationVisible by remember { mutableStateOf(false) }
    var importChoiceVisible by remember { mutableStateOf(false) }
    var reportIssueVisible by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<SettingsNotice?>(null) }

    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notice = SettingsNotice(
            title = if (granted) "Notifications enabled" else "Permission denied",
            body = if (granted) {
                "You can now receive task reminders."
            } else {
                "Enable notifications in your device Settings to receive task reminders."
            },
        )
    }

    fun unavailable(feature: String, detail: String) {
        notice = SettingsNotice(feature, detail)
    }

    fun contactSupport() {
        val emailIntent = Intent(
            Intent.ACTION_SENDTO,
            Uri.parse("mailto:tbtechsdev@gmail.com?subject=FocusFlow%20Support"),
        )
        if (emailIntent.resolveActivity(context.packageManager) == null) {
            unavailable("Email unavailable", "No email app is available to contact FocusFlow support.")
        } else {
            context.startActivity(emailIntent)
        }
    }

    if (isLoading || !isDbReady) {
        Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
            Column(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator()
                Text("Loading…", style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            item {
                SettingsSection("Profile") {
                    SettingsAction(
                        label = if (settings.pinProtectionEnabled) "Profile" else "Set up your profile",
                        description = "Name, occupation, daily goal and more",
                        onClick = onOpenProfile,
                    )
                }
            }
            item {
                SettingsSection("Appearance") {
                    SettingToggleRow(
                        label = "Dark Mode",
                        description = "Use a darker color palette throughout FocusFlow",
                    ) {
                        // NEEDS: app-level theme state and persistence. AppSettings has no theme field.
                        DarkModeToggle(isDark = isDarkMode, onToggle = onToggleDarkMode)
                    }
                }
            }
            item {
                SettingsSection("Notifications") {
                    SettingToggleRow(
                        label = "Enable Reminders",
                        description = "Get alerts before and during tasks",
                    ) {
                        // NEEDS: a dedicated task-reminder preference. The current model only
                        // exposes individual analytics notification categories, not this setting.
                        Switch(
                            checked = false,
                            onCheckedChange = {
                                unavailable(
                                    "Reminder setting unavailable",
                                    "The current SettingsViewModel does not expose the task-reminder preference needed to save this choice.",
                                )
                            },
                        )
                    }
                    SettingsAction(
                        label = "Request Notification Permission",
                        onClick = { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    )
                    SettingToggleRow(
                        label = "Pattern Insights",
                        description = "Allow FocusFlow to notify you once when it discovers a meaningful routine pattern. Off by default.",
                    ) {
                        Switch(
                            checked = settings.patternInsightNotificationsEnabled,
                            onCheckedChange = { enabled ->
                                settingsViewModel.updateSettings(
                                    settings.copy(patternInsightNotificationsEnabled = enabled),
                                )
                            },
                        )
                    }
                }
            }
            item {
                SettingsSection("Scheduling") {
                    Text("Default Task Duration", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Choose a default duration",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf(30, 45, 60, 90, 120).forEach { minutes ->
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    // NEEDS: default task duration in AppSettings and a matching repository setter.
                                    unavailable(
                                        "Default duration unavailable",
                                        "The current SettingsViewModel has no default task duration field to persist ${minutes} minutes.",
                                    )
                                },
                            ) { Text("${minutes}m") }
                        }
                    }
                }
            }
            item {
                SettingsSection("Focus Mode") {
                    SettingToggleRow(
                        label = "Auto-enable Focus Mode",
                        description = "Activate when a focus task starts",
                    ) {
                        // NEEDS: an auto-focus setting in AppSettings. Do not repurpose an
                        // enforcement flag because it changes a different feature.
                        Switch(
                            checked = false,
                            onCheckedChange = {
                                unavailable(
                                    "Auto-focus unavailable",
                                    "The current SettingsViewModel does not expose a persisted auto-enable Focus Mode setting.",
                                )
                            },
                        )
                    }
                    SettingsAction(
                        label = "Manage Allowed Apps",
                        description = "Choose apps permitted during Focus Mode",
                        onClick = {
                            // NEEDS: default allowed-in-focus packages and a setter. A live
                            // FocusSession exposes its own temporary packages only.
                            unavailable(
                                "Allowed apps unavailable",
                                "The current ViewModels do not expose the default allowed-app list needed to save this setting.",
                            )
                        },
                    )
                }
            }
            item {
                SettingsSection("Daily Allowance") {
                    val entryCount = remember(settings.dailyAllowanceConfigJson) {
                        dailyAllowanceEntriesFromJson(settings.dailyAllowanceConfigJson).size
                    }
                    SettingsAction(
                        label = "Daily Allowance",
                        description = if (entryCount == 0) {
                            "Set per-app daily limits"
                        } else {
                            "$entryCount app${if (entryCount == 1) "" else "s"} with a daily limit"
                        },
                        onClick = { dailyAllowanceVisible = true },
                    )
                }
            }
            item {
                SettingsSection("Block Overlay") {
                    SettingsAction(
                        label = "Overlay Appearance",
                        description = "Customise background image and quotes shown on the block screen",
                        onClick = { overlayAppearanceVisible = true },
                    )
                }
            }
            item {
                SettingsSection("Pomodoro Mode") {
                    SettingToggleRow(
                        label = "Enable Pomodoro",
                        description = "Auto-cycle work and break sessions",
                    ) {
                        // NEEDS: Pomodoro enabled/work/break fields in AppSettings plus a
                        // scheduler backed by those values.
                        Switch(
                            checked = false,
                            onCheckedChange = {
                                unavailable(
                                    "Pomodoro unavailable",
                                    "The current Kotlin settings contract does not expose Pomodoro settings.",
                                )
                            },
                        )
                    }
                }
            }
            item {
                SettingsSection("Backup & Data") {
                    SettingsAction(
                        label = "Export Backup",
                        description = "Save a .focusflow file — share to Drive, Files, or email",
                        onClick = {
                            if (onExportBackup == null) {
                                unavailable(
                                    "Backup export unavailable",
                                    "NEEDS: Kotlin backup export wiring supplied by the navigation host.",
                                )
                            } else {
                                onExportBackup()
                            }
                        },
                    )
                    SettingsAction(
                        label = "Import Backup",
                        description = "Restore from a .focusflow backup file",
                        onClick = { importChoiceVisible = true },
                    )
                }
            }
            item {
                SettingsSection("Permissions") {
                    SettingsAction(
                        label = "Manage Permissions",
                        description = "Accessibility, Usage Access, Battery, Notifications",
                        onClick = onOpenPermissions,
                    )
                }
            }
            item {
                SettingsSection("Diagnostics") {
                    SettingsAction(
                        label = "Report an Issue",
                        description = "Review and email a bug report, feedback, or app review",
                        onClick = {
                            if (onReportIssue == null) {
                                reportIssueVisible = true
                            } else {
                                onReportIssue()
                            }
                        },
                    )
                }
            }
            item {
                SettingsSection("Data") {
                    SettingsAction(
                        label = "Clear All Tasks",
                        description = "Permanently delete all scheduled tasks",
                        destructive = true,
                        onClick = { clearAllConfirmationVisible = true },
                    )
                }
            }
            item {
                SettingsSection("About") {
                    SettingsAction(
                        label = "Stats",
                        description = "Yesterday's digest, focus time, completed tasks, blocked apps, streak",
                        onClick = onOpenStats,
                    )
                    SettingsAction(
                        label = "What's New",
                        description = "Changelog — features, fixes, and improvements",
                        onClick = onOpenChangelog,
                    )
                    SettingsAction(
                        label = "Privacy & Terms",
                        description = "How FocusFlow handles your data and the rules of use",
                        onClick = onOpenPrivacyTerms,
                    )
                    SettingsAction(
                        label = "Contact Support",
                        description = "Email us at tbtechsdev@gmail.com",
                        onClick = ::contactSupport,
                    )
                }
            }
            item {
                Text(
                    "FocusFlow v1.4.0 (build 15)\nAll data stored locally on device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    OverlayAppearanceModal(
        visible = overlayAppearanceVisible,
        onClose = { overlayAppearanceVisible = false },
    )
    DailyAllowanceModal(
        visible = dailyAllowanceVisible,
        selectedEntries = dailyAllowanceEntriesFromJson(settings.dailyAllowanceConfigJson),
        locked = settings.standaloneBlockActive && settings.standaloneBlockUntilMs > System.currentTimeMillis(),
        requireDefensePin = settings.pinProtectionEnabled,
        onSave = settingsViewModel::setDailyAllowanceEntries,
        onVerifyDefensePin = settingsViewModel::verifyPin,
        onClose = { dailyAllowanceVisible = false },
    )
    ReportIssueModal(
        visible = reportIssueVisible,
        onClose = { reportIssueVisible = false },
    )

    if (importChoiceVisible) {
        AlertDialog(
            onDismissRequest = { importChoiceVisible = false },
            title = { Text("Restore from backup") },
            text = { Text("Pick how to merge the backup into this device.") },
            confirmButton = {
                Button(onClick = {
                    importChoiceVisible = false
                    if (onImportBackup == null) {
                        unavailable("Backup import unavailable", "NEEDS: Kotlin backup import wiring supplied by the navigation host.")
                    } else {
                        onImportBackup(false)
                    }
                }) { Text("Add tasks") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { importChoiceVisible = false }) { Text("Cancel") }
                    TextButton(onClick = {
                        importChoiceVisible = false
                        if (onImportBackup == null) {
                            unavailable("Backup import unavailable", "NEEDS: Kotlin backup import wiring supplied by the navigation host.")
                        } else {
                            onImportBackup(true)
                        }
                    }) { Text("Replace everything") }
                }
            },
        )
    }

    if (clearAllConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { clearAllConfirmationVisible = false },
            title = { Text("Clear All Tasks") },
            text = { Text("This will delete all ${tasks.size} scheduled task${if (tasks.size == 1) "" else "s"}. Are you sure?") },
            confirmButton = {
                Button(onClick = {
                    clearAllConfirmationVisible = false
                    if (focusSession?.isActive == true) {
                        // NEEDS: a Focus-session PIN verification API and an atomic clear that
                        // keeps the protected active task if verification is cancelled.
                        unavailable(
                            "Focus session password required",
                            "FocusFlow cannot safely clear tasks during an active focus session until the Kotlin focus-password and atomic-clear APIs are exposed.",
                        )
                    } else {
                        // NEEDS: TaskViewModel.clearAllTasks() with reminder cancellation.
                        // Do not loop over deleteTask(): that would omit the source's atomic
                        // reminder handling and its active-session protection.
                        unavailable(
                            "Clear all tasks unavailable",
                            "The current TaskViewModel has no safe bulk-delete API with reminder cancellation.",
                        )
                    }
                }) { Text("Clear all") }
            },
            dismissButton = { TextButton(onClick = { clearAllConfirmationVisible = false }) { Text("Cancel") } },
        )
    }

    notice?.let { activeNotice ->
        AlertDialog(
            onDismissRequest = { notice = null },
            title = { Text(activeNotice.title) },
            text = { Text(activeNotice.body) },
            confirmButton = { Button(onClick = { notice = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    description: String? = null,
    control: @Composable () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        control()
    }
}

@Composable
private fun SettingsAction(
    label: String,
    description: String? = null,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "Open",
            style = MaterialTheme.typography.labelLarge,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

private data class SettingsNotice(val title: String, val body: String)
