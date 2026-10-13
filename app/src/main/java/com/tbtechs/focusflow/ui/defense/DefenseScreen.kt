package com.tbtechs.focusflow.ui.defense

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import com.tbtechs.focusflow.ui.focus.ActiveStatusIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.repository.VpnRepository
import com.tbtechs.focusflow.domain.PinReuseTracker
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.alwayson.VpnConsentModal
import com.tbtechs.focusflow.ui.alwayson.rememberVpnConsentRequester
import com.tbtechs.focusflow.ui.common.PinRotationModal
import com.tbtechs.focusflow.ui.common.PinType
import com.tbtechs.focusflow.ui.navigation.DefenseIcons
import com.tbtechs.focusflow.ui.settings.DailyAllowanceModal
import com.tbtechs.focusflow.ui.settings.dailyAllowanceEntriesFromJson
import com.tbtechs.focusflow.ui.common.FocusFlowSwitch
import com.tbtechs.focusflow.ui.common.InAppNotice
import com.tbtechs.focusflow.ui.common.InAppNoticeStrip
import com.tbtechs.focusflow.ui.common.InAppNoticeTone
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
import com.tbtechs.focusflow.ui.theme.scaledSp
import kotlinx.coroutines.launch

/**
 * The Defense destination.
 *
 * Implements the React design system with matching cards, icons, color tokens,
 * and guarded toggle security checks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefenseScreen(
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    isFocusActive: Boolean = false,
    vpnRepository: VpnRepository? = null,
    onOpenAlwaysOn: () -> Unit = {},
    onOpenKeywordBlocker: () -> Unit = {},
    onOpenVpnBlockList: () -> Unit = {},
    onOpenPasswordProtection: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
    onOpenLauncher: () -> Unit = {},
    onOpenActiveBlocks: () -> Unit = {},
) {
    val dimensions = LocalFocusFlowDimensions.current
    val settings by settingsViewModel.settings.collectAsState()
    val allowanceUsage by settingsViewModel.allowanceUsage.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var allowanceVisible by remember { mutableStateOf(false) }
    var schedulesVisible by remember { mutableStateOf(false) }
    var nuclearVisible by remember { mutableStateOf(false) }
    var pinPrompt by remember { mutableStateOf<PinAction?>(null) }
    var notice by remember { mutableStateOf<InAppNotice?>(null) }
    var noticeId by remember { mutableStateOf(0) }
    var vpnConsentVisible by remember { mutableStateOf(false) }
    var alwaysOnPinRotationVisible by remember { mutableStateOf(false) }

    fun showNotice(message: String) {
        noticeId += 1
        notice = InAppNotice(
            id = noticeId,
            message = message,
            tone = InAppNoticeTone.WARNING,
        )
    }

    val standaloneActive = settings.standaloneBlockActive &&
        settings.standaloneBlockPackages.isNotEmpty() &&
        settings.standaloneBlockUntilMs > System.currentTimeMillis()
    val blockActive = isFocusActive || standaloneActive
    val allowanceEntries = dailyAllowanceEntriesFromJson(settings.dailyAllowanceConfigJson)

    fun update(next: AppSettings) = settingsViewModel.updateSettings(next)

    fun protectedToggle(
        label: String,
        enabled: Boolean,
        change: () -> Unit,
    ) {
        if (enabled) {
            change()
        } else if (blockActive) {
            showNotice("$label can't be turned off while a Focus session or Standalone block is running.")
        } else if (settings.pinProtectionEnabled) {
            pinPrompt = PinAction(
                "Disable $label",
                "Enter your defense password to turn off $label.",
                change,
                settingsViewModel::verifyPin,
            )
        } else {
            change()
        }
    }

    fun requireFocusPin(title: String, description: String, action: () -> Unit) {
        if (settingsViewModel.isFocusPinSet()) {
            pinPrompt = PinAction(title, description, action, settingsViewModel::verifyFocusPin)
        } else {
            action()
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BrandPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                DefenseIcons.ActiveFilled,
                                contentDescription = null,
                                tint = BrandPrimary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column {
                            Text(
                                text = "Defense",
                                 fontSize = 22.scaledSp,
                                fontWeight = FontWeight.Bold,
                                color = DarkTextPrimary,
                            )
                            Text(
                                text = "Make distractions harder to reach",
                                fontSize = 13.scaledSp,
                                color = DarkTextSecondary,
                            )
                        }
                    }
                },
                actions = {
                    ActiveStatusIndicator(
                        settings = settings,
                        onOpenActiveBlocks = onOpenActiveBlocks,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkCard),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = dimensions.screenPadding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Section: ALWAYS-ON BLOCKING
                DefenseSection("ALWAYS-ON BLOCKING") {
                    SettingSwitch(
                        label = "Always-On Enforcement",
                        description = if (settings.alwaysBlockEnabled) {
                            "${settings.alwaysBlockPackages.size} app${if (settings.alwaysBlockPackages.size == 1) "" else "s"} blocked around the clock"
                        } else {
                            "Keep selected apps blocked 24/7"
                        },
                        checked = settings.alwaysBlockEnabled,
                        onChange = { enabled ->
                            if (enabled) update(settings.copy(alwaysBlockEnabled = true))
                            else protectedToggle("Always-On Enforcement", false) {
                                update(settings.copy(alwaysBlockEnabled = false))
                                alwaysOnPinRotationVisible = true
                            }
                        },
                    )
                    HorizontalDivider(color = DarkBorder)
                    SettingButton(
                        label = "Manage Always-On App List",
                        description = if (settings.alwaysBlockPackages.isEmpty()) "Choose apps that should stay blocked"
                        else "${settings.alwaysBlockPackages.size} app${if (settings.alwaysBlockPackages.size == 1) "" else "s"} selected",
                        icon = Icons.Outlined.Apps,
                        onClick = onOpenAlwaysOn,
                    )
                    HorizontalDivider(color = DarkBorder)
                    SettingButton(
                        label = "Daily Allowance",
                        description = if (allowanceEntries.isEmpty()) {
                            "Set daily count, time, or interval limits per app"
                        } else {
                            "${allowanceEntries.size} app${if (allowanceEntries.size == 1) "" else "s"} configured"
                        },
                        icon = Icons.Outlined.LightMode,
                        onClick = { allowanceVisible = true },
                    )
                }

                // Section: DEFENSE TOOLS
                DefenseSection("DEFENSE TOOLS") {
                    SettingButton(
                        label = "Keyword Blocker",
                        description = "Block keywords in URLs, searches, and on-screen text",
                        icon = Icons.Outlined.TextFields,
                        onClick = onOpenKeywordBlocker,
                    )
                    HorizontalDivider(color = DarkBorder)
                    SettingButton(
                        label = "Scheduled Blocks",
                        description = "Manage recurring time-window blocks (formerly Greyout Block)",
                        icon = Icons.Outlined.Schedule,
                        onClick = {
                            if (settings.pinProtectionEnabled) {
                                pinPrompt = PinAction(
                                    "Manage Scheduled Blocks",
                                    "Enter your defense password to add, edit, or remove schedule batches.",
                                    { schedulesVisible = true },
                                    settingsViewModel::verifyPin,
                                )
                            } else {
                                schedulesVisible = true
                            }
                        },
                    )
                    HorizontalDivider(color = DarkBorder)
                    SettingButton(
                        label = "Manage VPN App List",
                        description = "Choose which apps should have internet access blocked",
                        icon = Icons.Outlined.FormatListBulleted,
                        onClick = onOpenVpnBlockList,
                    )
                    HorizontalDivider(color = DarkBorder)
                    SettingButton(
                        label = "PIN Protection",
                        description = if (settings.pinProtectionEnabled) {
                            "Defense password required before disabling protection"
                        } else {
                            "Require a password before protections can be disabled"
                        },
                        icon = Icons.Outlined.Lock,
                        onClick = onOpenPasswordProtection,
                    )
                }

                // Section: SYSTEM GUARD
                DefenseSection("SYSTEM GUARD") {
                    SettingSwitch(
                        label = "Protect system controls",
                        description = "Block power menu, notification shade, and sensitive Settings pages",
                        checked = settings.systemGuardEnabled,
                        onChange = { enabled ->
                            if (enabled) update(settings.copy(systemGuardEnabled = true))
                            else protectedToggle("System Guard", false) {
                                update(settings.copy(systemGuardEnabled = false))
                            }
                        },
                    )
                    HorizontalDivider(color = DarkBorder)
                    SettingSwitch(
                        label = "Block YouTube Shorts",
                        description = "Redirect away from the Shorts player",
                        checked = settings.blockYoutubeShortsEnabled,
                        onChange = { enabled ->
                            if (enabled) update(settings.copy(blockYoutubeShortsEnabled = true))
                            else protectedToggle("Block YouTube Shorts", false) {
                                update(settings.copy(blockYoutubeShortsEnabled = false))
                            }
                        },
                    )
                    HorizontalDivider(color = DarkBorder)
                    SettingSwitch(
                        label = "Block Instagram Reels",
                        description = "Redirect away from the Reels viewer",
                        checked = settings.blockInstagramReelsEnabled,
                        onChange = { enabled ->
                            if (enabled) update(settings.copy(blockInstagramReelsEnabled = true))
                            else protectedToggle("Block Instagram Reels", false) {
                                update(settings.copy(blockInstagramReelsEnabled = false))
                            }
                        },
                    )
                }

                // Section: AVERSION DETERRENTS
                DefenseSection("AVERSION DETERRENTS") {
                    SettingSwitch(
                        label = "Screen Dimmer",
                        description = "Show a near-black overlay when a blocked app is open",
                        checked = settings.aversionDimmerEnabled,
                        onChange = { enabled ->
                            protectedToggle("Screen Dimmer", enabled) {
                                update(settings.copy(aversionDimmerEnabled = enabled))
                            }
                        },
                    )
                    HorizontalDivider(color = DarkBorder)
                    SettingSwitch(
                        label = "Vibration Harassment",
                        description = "Pulse vibration while a blocked app is in the foreground",
                        checked = settings.aversionVibrateEnabled,
                        onChange = { enabled ->
                            protectedToggle("Vibration Harassment", enabled) {
                                update(settings.copy(aversionVibrateEnabled = enabled))
                            }
                        },
                    )
                    HorizontalDivider(color = DarkBorder)
                    SettingSwitch(
                        label = "Sound Alert",
                        description = "Play an alert when a blocked app launches",
                        checked = settings.aversionSoundEnabled,
                        onChange = { enabled ->
                            protectedToggle("Sound Alert", enabled) {
                                update(settings.copy(aversionSoundEnabled = enabled))
                            }
                        },
                    )
                }

                // Section: FOCUS SESSION BEHAVIOUR
                DefenseSection("FOCUS SESSION BEHAVIOUR") {
                    SettingSwitch(
                        label = "Keep focus active for the full duration",
                        description = if (settings.keepFocusActiveUntilTaskEnd) {
                            "On — completing a task early keeps app-blocking running until the original end time"
                        } else {
                            "Off — completing a task immediately ends the focus session (default)"
                        },
                        checked = settings.keepFocusActiveUntilTaskEnd,
                        onChange = { enabled ->
                            if (enabled) {
                                update(settings.copy(keepFocusActiveUntilTaskEnd = true))
                            } else {
                                requireFocusPin(
                                    "Disable full-duration focus",
                                    "Enter your focus session password to allow tasks to end focus early.",
                                ) {
                                    update(settings.copy(keepFocusActiveUntilTaskEnd = false))
                                }
                            }
                        },
                    )
                    HorizontalDivider(color = DarkBorder)
                    SettingSwitch(
                        label = "Auto-reschedule freed time",
                        description = if (settings.autoRescheduleEnabled) {
                            "On — completing, skipping, or deleting a future task moves later tasks forward"
                        } else {
                            "Off — task times stay unchanged when time is freed"
                        },
                        checked = settings.autoRescheduleEnabled,
                        onChange = { enabled -> update(settings.copy(autoRescheduleEnabled = enabled)) },
                    )
                }

                // Section: ALWAYS-ON BEHAVIOR
                DefenseSection("ALWAYS-ON BEHAVIOR") {
                    SettingSwitch(
                        label = "Auto-copy from standalone block",
                        description = "Automatically add standalone-block apps to the Always-On list when the block starts",
                        checked = settings.autoCopyToAlwaysOn,
                        onChange = { enabled -> update(settings.copy(autoCopyToAlwaysOn = enabled)) },
                    )
                }

                // Section: NETWORK PROTECTION
                DefenseSection("NETWORK PROTECTION") {
                    SettingSwitch(
                        label = "Network Blocking (VPN)",
                        description = "Cut internet access for selected apps through FocusFlow’s local VPN",
                        checked = settings.networkBlockEnabled,
                        onChange = { enabled ->
                            if (!enabled && blockActive) {
                                showNotice("Network Blocking (VPN) can't be turned off while a block is running.")
                            } else if (enabled) {
                                vpnConsentVisible = true
                            } else if (settings.pinProtectionEnabled) {
                                pinPrompt = PinAction(
                                    "Disable Network Blocking",
                                    "Enter your defense password to turn off VPN blocking.",
                                    { update(settings.copy(networkBlockEnabled = false)) },
                                    settingsViewModel::verifyPin,
                                )
                            } else {
                                update(settings.copy(networkBlockEnabled = false))
                            }
                        },
                    )
                    HorizontalDivider(color = DarkBorder)
                    SettingSwitch(
                        label = "VPN Self-Healing",
                        description = "Restart the VPN if it disconnects during an active block",
                        checked = settings.vpnSelfHealEnabled,
                        onChange = { enabled ->
                            if (enabled && !settings.networkBlockEnabled) {
                                showNotice("Enable Network Blocking (VPN) first.")
                            } else {
                                protectedToggle("VPN Self-Healing", enabled) {
                                    update(settings.copy(vpnSelfHealEnabled = enabled))
                                }
                            }
                        },
                    )
                    HorizontalDivider(color = DarkBorder)
                    SettingSwitch(
                        label = "Mirror Focus blocking to VPN",
                        description = "Also block internet for apps blocked by Focus during an active session",
                        checked = settings.focusMirrorVpnEnabled,
                        onChange = { enabled ->
                            if (enabled && !settings.networkBlockEnabled) {
                                showNotice("Enable Network Blocking (VPN) first.")
                            } else {
                                update(settings.copy(focusMirrorVpnEnabled = enabled))
                            }
                        },
                    )
                }

                // Section: HOME LAUNCHER
                DefenseSection("HOME LAUNCHER") {
                    SettingSwitch(
                        label = "Lock launcher during standalone block",
                        description = "Prevent switching away from FocusFlow Launcher during a standalone block",
                        checked = settings.launcherLockDuringStandalone,
                        onChange = { enabled ->
                            if (!enabled && standaloneActive) {
                                showNotice("Home Launcher can't be turned off while a Standalone block is running.")
                            } else {
                                update(settings.copy(launcherLockDuringStandalone = enabled))
                            }
                        },
                    )
                    HorizontalDivider(color = DarkBorder)
                    SettingSwitch(
                        label = "Block uninstall from launcher long-press",
                        description = "Hide Uninstall from the launcher long-press menu",
                        checked = settings.launcherBlockUninstall,
                        onChange = { enabled ->
                            if (!enabled && blockActive) {
                                showNotice("Uninstall protection can't be turned off while a block is running.")
                            } else {
                                update(settings.copy(launcherBlockUninstall = enabled))
                            }
                        },
                    )
                    HorizontalDivider(color = DarkBorder)
                    SettingButton(
                        label = "Configure Home Launcher",
                        description = "Choose pinned apps, hidden apps, wallpaper, and clock style",
                        icon = Icons.Outlined.Home,
                        onClick = onOpenLauncher,
                    )
                }

                // Section: NUCLEAR MODE
                DefenseSection("NUCLEAR MODE") {
                    SettingButton(
                        label = "Uninstall Distracting Apps",
                        description = "Permanently remove blocked apps through Android's system uninstall dialog",
                        icon = Icons.Outlined.DeleteForever,
                        onClick = { nuclearVisible = true },
                    )
                }

                Spacer(modifier = Modifier.height(100.dp))
            }

            InAppNoticeStrip(
                notice = notice,
                onDismiss = { notice = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
            )
        }
    }

    if (allowanceVisible) {
        DailyAllowanceModal(
            visible = true,
            selectedEntries = dailyAllowanceEntriesFromJson(settings.dailyAllowanceConfigJson),
            locked = standaloneActive,
            requireDefensePin = settings.pinProtectionEnabled,
            onSave = { entries ->
                settingsViewModel.setDailyAllowanceEntries(entries)
                allowanceVisible = false
            },
            onVerifyDefensePin = settingsViewModel::verifyPin,
            onClose = { allowanceVisible = false },
            usageByPackage = allowanceUsage,
        )
    }

    val requestVpnConsent = rememberVpnConsentRequester(vpnRepository) { outcome ->
        if (outcome.isGranted) {
            scope.launch {
                update(settings.copy(networkBlockEnabled = true))
            }
        } else {
            showNotice("VPN permission was not granted. Network Blocking remains off.")
        }
    }

    VpnConsentModal(
        visible = vpnConsentVisible,
        onCancel = { vpnConsentVisible = false },
        onConfirm = {
            vpnConsentVisible = false
            requestVpnConsent()
        },
    )

    PinRotationModal(
        visible = alwaysOnPinRotationVisible,
        pinType = PinType.DEFENSE,
        reuseKey = PinReuseTracker.ReuseTrackerKey.ALWAYSON,
        actionLabel = "Update Always-On Password",
        actionDescription = "Always-On Enforcement has been paused. Set the password that will be required next time you change this setting.",
        reuseInfo = settingsViewModel.alwaysOnPinReuseInfo(),
        onKeepSame = settingsViewModel::keepAlwaysOnPin,
        onSaveNew = { newPin ->
            settingsViewModel.setPin(newPin)
            true
        },
        onComplete = { alwaysOnPinRotationVisible = false },
        onCancel = { alwaysOnPinRotationVisible = false },
    )

    if (schedulesVisible) {
        GreyoutScheduleModal(
            visible = true,
            windows = settings.recurringBlockSchedules,
            standaloneActive = blockActive,
            requireDefensePin = { title, description, action ->
                if (settings.pinProtectionEnabled) {
                    pinPrompt = PinAction(title, description, action, settingsViewModel::verifyPin)
                } else {
                    action()
                }
            },
            vpnRepository = vpnRepository,
            onNetworkProtectionRequired = {
                update(
                    settings.copy(
                        networkBlockEnabled = true,
                        vpnSelfHealEnabled = true,
                    ),
                )
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
        PinPrompt(action = action, onClose = { pinPrompt = null })
    }
}

internal data class PinAction(
    val title: String,
    val description: String,
    val action: () -> Unit,
    val verify: (String) -> Boolean,
)

@Composable
internal fun PinPrompt(action: PinAction, onClose: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = DarkCard,
        titleContentColor = DarkTextPrimary,
        textContentColor = DarkTextSecondary,
        title = { Text(action.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(action.description, fontSize = 13.scaledSp, color = DarkTextSecondary)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it; invalid = false },
                    label = { Text("Defense password") },
                    isError = invalid,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (invalid) Text("Incorrect password", color = MaterialTheme.colorScheme.error, fontSize = 12.scaledSp)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (action.verify(pin)) {
                        onClose()
                        action.action()
                    } else {
                        invalid = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
            ) { Text("Confirm", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Cancel", color = DarkTextSecondary) }
        },
    )
}

@Composable
private fun DefenseSection(title: String, content: @Composable () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 11.scaledSp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.scaledSp,
            color = DarkTextMuted,
            modifier = Modifier.padding(start = 2.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DarkCard)
                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 15.scaledSp,
                fontWeight = FontWeight.Bold,
                color = DarkTextPrimary,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                fontSize = 13.scaledSp,
                lineHeight = 18.scaledSp,
                color = DarkTextSecondary,
            )
        }
        FocusFlowSwitch(
            checked = checked,
            onCheckedChange = onChange,
        )
    }
}

@Composable
private fun SettingButton(
    label: String,
    description: String,
    onClick: () -> Unit,
    icon: ImageVector = Icons.Outlined.Shield,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = BrandPrimary,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 15.scaledSp,
                fontWeight = FontWeight.Bold,
                color = DarkTextPrimary,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                fontSize = 13.scaledSp,
                lineHeight = 18.scaledSp,
                color = DarkTextSecondary,
            )
        }
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowRight,
            contentDescription = "Open",
            tint = DarkTextMuted,
                modifier = Modifier.size(16.dp),
        )
    }
}
