package com.tbtechs.focusflow.ui.defense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.RecurringBlockSchedule
import com.tbtechs.focusflow.ui.SettingsViewModel

/**
 * The Defense destination.
 *
 * This screen intentionally keeps the protection gates in the UI: disabling a
 * protection while a block is active is refused, and disabling a PIN-protected
 * setting requires the defense PIN. The enforcement service remains the source
 * of truth for actual blocking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefenseScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    onOpenAlwaysOn: () -> Unit = {},
    onOpenKeywordBlocker: () -> Unit = {},
    onOpenVpnBlockList: () -> Unit = {},
    onOpenPasswordProtection: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
    onOpenHowToUse: () -> Unit = {},
    onOpenLauncher: () -> Unit = {},
) {
    val settings by settingsViewModel.settings.collectAsState()
    var showHint by rememberSaveable { mutableStateOf(true) }
    var showHelp by rememberSaveable { mutableStateOf(true) }
    var allowanceVisible by remember { mutableStateOf(false) }
    var schedulesVisible by remember { mutableStateOf(false) }
    var nuclearVisible by remember { mutableStateOf(false) }
    var pinPrompt by remember { mutableStateOf<PinAction?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    val blockActive = settings.standaloneBlockActive &&
        settings.standaloneBlockPackages.isNotEmpty() &&
        settings.standaloneBlockUntilMs > System.currentTimeMillis()

    fun update(next: AppSettings) = settingsViewModel.updateSettings(next)

    fun protectedToggle(
        label: String,
        enabled: Boolean,
        change: () -> Unit,
    ) {
        if (enabled) {
            change()
        } else if (blockActive) {
            notice = "$label can't be turned off while a block is running."
        } else if (settings.pinProtectionEnabled) {
            pinPrompt = PinAction("Disable $label", "Enter your defense password to turn off $label.", change)
        } else {
            change()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Security, contentDescription = null)
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text("Defense")
                            Text(
                                "Make distractions harder to reach",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showHint) {
                InfoBanner(
                    icon = Icons.Outlined.Info,
                    text = "Password Protection has its own page below the blocking tools, so your security settings stay easy to find.",
                    onDismiss = { showHint = false },
                )
            }
            if (showHelp) {
                InfoBanner(
                    icon = Icons.Outlined.HelpOutline,
                    title = "Not sure what to do?",
                    text = "Start with Focus to schedule a task, or open How to Use for a quick walkthrough of blocking and protection.",
                    actionLabel = "Open How to Use",
                    onAction = onOpenHowToUse,
                    onDismiss = { showHelp = false },
                )
            }

            DefenseSection("Always-On Blocking") {
                SettingSwitch(
                    "Always-On Enforcement",
                    if (settings.alwaysBlockEnabled) {
                        "${settings.alwaysBlockPackages.size} app(s) blocked around the clock"
                    } else {
                        "Keep selected apps blocked 24/7"
                    },
                    settings.alwaysBlockEnabled,
                ) { enabled ->
                    if (enabled) update(settings.copy(alwaysBlockEnabled = true))
                    else protectedToggle("Always-On Enforcement", false) {
                        update(settings.copy(alwaysBlockEnabled = false))
                    }
                }
                SettingButton("Manage Always-On App List", "Choose apps that should stay blocked", onOpenAlwaysOn)
                SettingButton(
                    "Daily Allowance",
                    "Set daily count, time, or interval limits per app",
                ) { allowanceVisible = true }
            }

            DefenseSection("Defense Tools") {
                SettingButton(
                    "Keyword Blocker",
                    "Block keywords in URLs, searches, and on-screen text",
                    onOpenKeywordBlocker,
                    icon = Icons.Outlined.TextFields,
                )
                SettingButton(
                    "Scheduled Blocks",
                    "Manage recurring time-window blocks",
                    enabled = !blockActive,
                ) { schedulesVisible = true }
                SettingButton(
                    "Manage VPN App List",
                    "Choose which apps should have internet access blocked",
                    onOpenVpnBlockList,
                )
                SettingButton(
                    "PIN Protection",
                    if (settings.pinProtectionEnabled) {
                        "Defense password required before disabling protection"
                    } else {
                        "Require a password before protections can be disabled"
                    },
                    onOpenPasswordProtection,
                    icon = Icons.Outlined.Lock,
                )
            }

            DefenseSection("System Guard") {
                SettingSwitch(
                    "Protect system controls",
                    "Block power menu, notification shade, and sensitive Settings pages",
                    settings.systemGuardEnabled,
                ) { enabled ->
                    if (enabled) update(settings.copy(systemGuardEnabled = true))
                    else protectedToggle("System Guard", false) {
                        update(settings.copy(systemGuardEnabled = false))
                    }
                }
                SettingSwitch(
                    "Block YouTube Shorts",
                    "Redirect away from the Shorts player",
                    settings.blockYoutubeShortsEnabled,
                ) { enabled ->
                    if (enabled) update(settings.copy(blockYoutubeShortsEnabled = true))
                    else protectedToggle("Block YouTube Shorts", false) {
                        update(settings.copy(blockYoutubeShortsEnabled = false))
                    }
                }
                SettingSwitch(
                    "Block Instagram Reels",
                    "Redirect away from the Reels viewer",
                    settings.blockInstagramReelsEnabled,
                ) { enabled ->
                    if (enabled) update(settings.copy(blockInstagramReelsEnabled = true))
                    else protectedToggle("Block Instagram Reels", false) {
                        update(settings.copy(blockInstagramReelsEnabled = false))
                    }
                }
                SettingSwitch(
                    "Block install actions",
                    "Block install, update, and uninstall confirmation screens",
                    settings.blockInstallActionsEnabled,
                ) { enabled ->
                    if (enabled) update(settings.copy(blockInstallActionsEnabled = true))
                    else protectedToggle("Block install actions", false) {
                        update(settings.copy(blockInstallActionsEnabled = false))
                    }
                }
            }

            DefenseSection("Aversion Deterrents") {
                UnavailableSwitch(
                    "Screen Dimmer",
                    "Show a near-black overlay when a blocked app is open",
                    false,
                    "aversion dimmer preference",
                )
                UnavailableSwitch(
                    "Vibration Harassment",
                    "Pulse vibration while a blocked app is in the foreground",
                    false,
                    "aversion vibration preference",
                )
                UnavailableSwitch(
                    "Sound Alert",
                    "Play an alert when a blocked app launches",
                    false,
                    "aversion sound preference",
                )
            }

            DefenseSection("Focus Session Behaviour") {
                UnavailableSwitch(
                    "Keep focus active for the full duration",
                    "Completing a task early keeps app-blocking running until the original end time",
                    false,
                    "keep-focus-until-task-end preference",
                )
                UnavailableSwitch(
                    "Auto-reschedule freed time",
                    "Move later tasks forward when time is freed",
                    false,
                    "auto-reschedule preference",
                )
            }

            DefenseSection("Network Protection") {
                UnavailableSwitch(
                    "Network Blocking (VPN)",
                    "Cut internet access for selected apps through FocusFlow's local VPN",
                    settings.networkBlockEnabled,
                    "selected VPN package list",
                ) { enabled ->
                    update(settings.copy(networkBlockEnabled = enabled))
                }
                UnavailableSwitch(
                    "VPN Self-Healing",
                    "Restart the VPN if it disconnects during an active block",
                    false,
                    "VPN self-healing preference",
                )
                UnavailableSwitch(
                    "Mirror Focus blocking to VPN",
                    "Also block internet for apps blocked by Focus during an active session",
                    false,
                    "focus-to-VPN mirror preference",
                )
            }

            DefenseSection("Home Launcher") {
                UnavailableSwitch(
                    "Lock launcher during standalone block",
                    "Prevent switching away from FocusFlow Launcher during a standalone block",
                    false,
                    "launcher lock preference",
                )
                UnavailableSwitch(
                    "Block uninstall from launcher long-press",
                    "Hide Uninstall from the launcher long-press menu",
                    false,
                    "launcher uninstall preference",
                )
                SettingButton("Configure Home Launcher", "Choose pinned apps, hidden apps, wallpaper, and clock style", onOpenLauncher)
            }

            DefenseSection("Nuclear Mode") {
                SettingButton(
                    "Uninstall Distracting Apps",
                    "Permanently remove blocked apps through Android's system uninstall dialog",
                    icon = Icons.Outlined.Block,
                ) { nuclearVisible = true }
            }
        }
    }

    if (allowanceVisible) {
        DailyAllowanceDefenseDialog(
            settings = settings,
            onSave = { entries ->
                settingsViewModel.setDailyAllowanceEntries(entries)
                allowanceVisible = false
            },
            onClose = { allowanceVisible = false },
        )
    }
    if (schedulesVisible) {
        GreyoutScheduleModal(
            visible = true,
            windows = settings.recurringBlockSchedules,
            standaloneActive = blockActive,
            requireDefensePin = { title, description, action ->
                if (settings.pinProtectionEnabled) {
                    pinPrompt = PinAction(title, description, action)
                } else {
                    action()
                }
            },
            onSave = { schedules ->
                settingsViewModel.setRecurringBlockSchedules(schedules)
                schedulesVisible = false
            },
            onClose = { schedulesVisible = false },
        )
    }
    if (nuclearVisible) {
        NuclearModeModal(
            visible = true,
            blockedPackages = (settings.standaloneBlockPackages + settings.alwaysBlockPackages).distinct(),
            onClose = { nuclearVisible = false },
        )
    }
    pinPrompt?.let { action ->
        PinPrompt(action = action, verify = settingsViewModel::verifyPin, onClose = { pinPrompt = null })
    }
    notice?.let {
        AlertDialog(
            onDismissRequest = { notice = null },
            title = { Text("Protection unchanged") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("OK") } },
        )
    }
}

internal data class PinAction(
    val title: String,
    val description: String,
    val action: () -> Unit,
)

@Composable
internal fun PinPrompt(action: PinAction, verify: (String) -> Boolean, onClose: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(action.title) },
        text = {
            Column {
                Text(action.description)
                androidx.compose.material3.OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it; invalid = false },
                    label = { Text("Defense password") },
                    isError = invalid,
                )
                if (invalid) Text("Incorrect password", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (verify(pin)) {
                    onClose()
                    action.action()
                } else {
                    invalid = true
                }
            }) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
private fun InfoBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String? = null,
    text: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    Card {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                Text(text, style = MaterialTheme.typography.bodySmall)
                actionLabel?.let { TextButton(onClick = onAction) { Text(it) } }
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Dismiss") }
        }
    }
}

@Composable
private fun DefenseSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card { Column(modifier = Modifier.fillMaxWidth()) { content() } }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun UnavailableSwitch(
    label: String,
    description: String,
    checked: Boolean,
    missing: String,
    onChange: (Boolean) -> Unit = {},
) {
    SettingSwitch(label, description, checked) {
        // NEEDS: $missing in AppSettings/SettingsRepository before this toggle can persist.
        onChange(it)
    }
}

@Composable
private fun SettingButton(
    label: String,
    description: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.Shield,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Open", style = MaterialTheme.typography.labelLarge)
    }
}
