package com.tbtechs.focusflow.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Help
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tbtechs.focusflow.BackupImportIntentRelay
import com.tbtechs.focusflow.BuildConfig
import com.tbtechs.focusflow.data.backup.BackupSerializer
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.ui.backup.BackupViewModel
import com.tbtechs.focusflow.ui.backup.ExportState
import com.tbtechs.focusflow.ui.backup.ImportConfirmScreen
import com.tbtechs.focusflow.ui.backup.ImportState
import com.tbtechs.focusflow.ui.AppBootViewModel
import com.tbtechs.focusflow.ui.FocusSessionViewModel
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.TaskViewModel
import com.tbtechs.focusflow.ui.common.PinType
import com.tbtechs.focusflow.ui.common.PinVerifyModal
import com.tbtechs.focusflow.ui.common.FocusFlowSwitch
import com.tbtechs.focusflow.ui.focus.ActiveStatusIndicator
import com.tbtechs.focusflow.ui.launcher.AllowedAppsModal
import com.tbtechs.focusflow.ui.support.ReportIssueModal
import com.tbtechs.focusflow.data.repository.StartupLogger
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
import com.tbtechs.focusflow.ui.theme.scaledSp
import org.json.JSONObject

/**
 * Complete redesign of the Settings tab matching Screenshots 8a, 8b, and 8c.
 * Preserves all underlying business logic, actions, and dialogs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    backupViewModel: BackupViewModel = viewModel(factory = BackupViewModel.factory(settingsViewModel)),
    taskViewModel: TaskViewModel = viewModel(factory = TaskViewModel.Factory),
    focusSessionViewModel: FocusSessionViewModel = viewModel(factory = FocusSessionViewModel.Factory),
    appBootViewModel: AppBootViewModel = viewModel(factory = AppBootViewModel.Factory),
    onOpenActiveBlocks: () -> Unit = {},
    onOpenTextSize: () -> Unit = {},
    onOpenHowToUse: () -> Unit = {},
    onOpenFileGuide: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
    onReportIssue: (() -> Unit)? = null,
    onOpenChangelog: () -> Unit = {},
    onOpenPrivacyTerms: () -> Unit = {},
    onOpenImportConfirmation: (() -> Unit)? = null,
    externalImportEventNonce: Int = 0,
) {
    val dimensions = LocalFocusFlowDimensions.current
    val settings by settingsViewModel.settings.collectAsState()
    val tasks by taskViewModel.tasks.collectAsState()
    val focusSession by focusSessionViewModel.focusSession.collectAsState()
    val isLoading by appBootViewModel.isLoading.collectAsState()
    val isDbReady by appBootViewModel.isDbReady.collectAsState()
    val context = LocalContext.current
    val exportState by backupViewModel.exportState.collectAsState()
    val importState by backupViewModel.importState.collectAsState()
    val pendingImportEnvelope by backupViewModel.pendingImportEnvelope.collectAsState()

    var overlayAppearanceVisible by remember { mutableStateOf(false) }
    var clearAllConfirmationVisible by remember { mutableStateOf(false) }
    var reportIssueVisible by remember { mutableStateOf(false) }
    var allowedAppsVisible by remember { mutableStateOf(false) }
    var focusPinVisible by remember { mutableStateOf(false) }
    var pendingClearAll by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<SettingsNotice?>(null) }
    var exportNotice by remember { mutableStateOf<ExportState?>(null) }
    var importErrorNotice by remember { mutableStateOf<String?>(null) }
    var showInlineImportConfirmation by remember { mutableStateOf(false) }
    var navigatingToImportConfirmation by remember { mutableStateOf(false) }
    val installedAppsRepository = remember { InstalledAppsRepository(context) }
    val profile = runCatching {
        settingsViewModel.getUserProfileJson()?.let(::JSONObject)
    }.getOrNull()

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

    val createBackupDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            backupViewModel.beginExport(context.contentResolver, uri)
        }
    }

    val openBackupDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            backupViewModel.beginImport(context.contentResolver, uri)
        }
    }

    LaunchedEffect(externalImportEventNonce) {
        val uri = BackupImportIntentRelay.consume() ?: return@LaunchedEffect
        backupViewModel.beginImport(context.contentResolver, uri)
    }

    LaunchedEffect(importState) {
        when (val state = importState) {
            is ImportState.PendingConfirm -> {
                if (onOpenImportConfirmation == null) {
                    showInlineImportConfirmation = true
                } else {
                    navigatingToImportConfirmation = true
                    onOpenImportConfirmation()
                }
            }
            is ImportState.Error -> {
                if (!showInlineImportConfirmation && !navigatingToImportConfirmation) {
                    importErrorNotice = state.message
                }
            }
            ImportState.Idle -> navigatingToImportConfirmation = false
            else -> Unit
        }
    }

    LaunchedEffect(exportState) {
        if (exportState is ExportState.Success || exportState is ExportState.Error) {
            exportNotice = exportState
        }
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

    if (showInlineImportConfirmation) {
        ImportConfirmScreen(
            importState = importState,
            pendingEnvelope = pendingImportEnvelope,
            onConfirm = backupViewModel::confirmImport,
            onCancelImport = backupViewModel::cancelImport,
            onResetImport = backupViewModel::resetImport,
            onDone = { showInlineImportConfirmation = false },
            onCancel = { showInlineImportConfirmation = false },
        )
        return
    }

    if (isLoading || !isDbReady) {
        Scaffold(
            containerColor = DarkBackground,
            topBar = {
                TopAppBar(
                    title = { Text("Settings", color = DarkTextPrimary, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = BrandPrimary)
            }
        }
        return
    }

    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        fontSize = 28.scaledSp,
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary,
                    )
                },
                actions = {
                    ActiveStatusIndicator(
                        focusSession = focusSession,
                        settings = settings,
                        onOpenActiveBlocks = onOpenActiveBlocks,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = dimensions.screenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
        ) {
            // 1. PROFILE
            item {
                SettingsSectionHeader("PROFILE")
                SettingsCard {
                    SettingsActionRow(
                        icon = Icons.Outlined.AccountCircle,
                        title = profile?.optString("name").orEmpty().ifBlank { "Set up your profile" },
                        description = profile?.let {
                            listOf(
                                it.optString("occupation").takeIf(String::isNotBlank),
                                it.optInt("dailyGoalHours", 0)
                                    .takeIf { hours -> hours > 0 }
                                    ?.let { hours -> "${hours}h daily goal" },
                                it.optString("wakeUpTime").takeIf(String::isNotBlank)
                                    ?.let { time -> "Wakes at $time" },
                            ).filterNotNull().joinToString(" · ")
                                .ifBlank { "Tap to personalise your experience" }
                        } ?: "Name, occupation, daily goal and more",
                        onClick = onOpenProfile,
                    )
                }
            }

            // 2. APPEARANCE
            item {
                SettingsSectionHeader("APPEARANCE")
                SettingsCard {
                    SettingsToggleRow(
                        title = "Dark Mode",
                        description = "Use a darker color palette throughout FocusFlow",
                    ) {
                        DarkModeToggle(
                            isDark = settings.darkModeEnabled,
                            onToggle = {
                                settingsViewModel.updateSettings(
                                    settings.copy(darkModeEnabled = !settings.darkModeEnabled),
                                )
                            },
                        )
                    }
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    SettingsActionRow(
                        icon = Icons.Outlined.Tune,
                        title = "Text Size",
                        description = "Adjust text size across FocusFlow",
                        onClick = onOpenTextSize,
                    )
                }
            }

            // 3. NOTIFICATIONS
            item {
                SettingsSectionHeader("NOTIFICATIONS")
                SettingsCard {
                    SettingsToggleRow(
                        title = "Scheduled task reminders",
                        description = "Before, at, and during tasks",
                    ) {
                        FocusFlowSwitch(
                            checked = settings.taskRemindersEnabled,
                            onCheckedChange = { enabled ->
                                settingsViewModel.updateSettings(settings.copy(taskRemindersEnabled = enabled))
                            },
                        )
                    }
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    TaskReminderOptions(
                        remindersEnabled = settings.taskRemindersEnabled,
                        enabledCount = listOf(
                            settings.taskPreStartRemindersEnabled,
                            settings.taskAtStartReminderEnabled,
                            settings.taskMidSessionRemindersEnabled,
                            settings.taskWrapUpReminderEnabled,
                        ).count { it },
                        onPreStartChange = { enabled ->
                            settingsViewModel.updateSettings(
                                settings.copy(taskPreStartRemindersEnabled = enabled),
                            )
                        },
                        onAtStartChange = { enabled ->
                            settingsViewModel.updateSettings(
                                settings.copy(taskAtStartReminderEnabled = enabled),
                            )
                        },
                        onMidSessionChange = { enabled ->
                            settingsViewModel.updateSettings(
                                settings.copy(taskMidSessionRemindersEnabled = enabled),
                            )
                        },
                        onWrapUpChange = { enabled ->
                            settingsViewModel.updateSettings(
                                settings.copy(taskWrapUpReminderEnabled = enabled),
                            )
                        },
                        preStartChecked = settings.taskPreStartRemindersEnabled,
                        atStartChecked = settings.taskAtStartReminderEnabled,
                        midSessionChecked = settings.taskMidSessionRemindersEnabled,
                        wrapUpChecked = settings.taskWrapUpReminderEnabled,
                    )
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    TaskEndAlertSelector(
                        ringingAlarm = settings.taskEndAlarmRings,
                        onSelect = { ringing ->
                            settingsViewModel.updateSettings(settings.copy(taskEndAlarmRings = ringing))
                        },
                    )
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    SettingsToggleRow(
                        title = "Daily reflection prompts",
                        description = "Show the questionnaire and day-rating prompts",
                    ) {
                        FocusFlowSwitch(
                            checked = settings.reflectionPromptsEnabled,
                            onCheckedChange = { enabled ->
                                settingsViewModel.updateSettings(
                                    settings.copy(reflectionPromptsEnabled = enabled),
                                )
                            },
                        )
                    }
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    SettingsActionRow(
                        icon = Icons.Outlined.Notifications,
                        title = "Request Notification Permission",
                        onClick = { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    )
                }
            }

            // 4. SCHEDULING
            item {
                SettingsSectionHeader("SCHEDULING")
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Default Task Duration",
                                fontSize = 15.scaledSp,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkTextPrimary,
                            )
                            val durationLabel = when (settings.defaultDurationMinutes) {
                                60 -> "1h"
                                90 -> "1h 30m"
                                120 -> "2h"
                                else -> "${settings.defaultDurationMinutes}m"
                            }
                            Text(
                                durationLabel,
                                fontSize = 14.scaledSp,
                                fontWeight = FontWeight.Bold,
                                color = BrandPrimary,
                            )
                        }
                        val options = listOf(
                            30 to "30m",
                            45 to "45m",
                            60 to "1h",
                            90 to "1h 30m",
                            120 to "2h",
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            options.forEach { (minutes, label) ->
                                val isSelected = settings.defaultDurationMinutes == minutes
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) BrandPrimary else DarkSurfaceVariant)
                                        .border(
                                            1.dp,
                                            if (isSelected) BrandPrimary else DarkBorder,
                                            RoundedCornerShape(8.dp),
                                        )
                                        .clickable {
                                            settingsViewModel.updateSettings(
                                                settings.copy(defaultDurationMinutes = minutes),
                                            )
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        label,
                                        fontSize = 12.5.scaledSp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else DarkTextSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. FOCUS MODE
            item {
                SettingsSectionHeader("FOCUS MODE")
                SettingsCard {
                    SettingsToggleRow(
                        title = "Auto-enable Focus Mode",
                        description = "Start with focus tasks",
                    ) {
                        FocusFlowSwitch(
                            checked = settings.autoFocusEnabled,
                            onCheckedChange = { enabled ->
                                settingsViewModel.updateSettings(settings.copy(autoFocusEnabled = enabled))
                            },
                        )
                    }
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    val allowedSummary = if (settings.allowedFocusPackages.isEmpty()) {
                        "All apps will be blocked during Focus Mode"
                    } else {
                        "${settings.allowedFocusPackages.size} apps allowed during Focus Mode"
                    }
                    SettingsActionRow(
                        icon = Icons.Outlined.Apps,
                        title = "Manage Allowed Apps",
                        description = allowedSummary,
                        onClick = { allowedAppsVisible = true },
                    )
                }
            }

            // 6. BLOCK OVERLAY
            item {
                SettingsSectionHeader("BLOCK OVERLAY")
                SettingsCard {
                    SettingsActionRow(
                        icon = Icons.Outlined.Smartphone,
                        title = "Overlay Appearance",
                        description = "Customize the block screen",
                        onClick = { overlayAppearanceVisible = true },
                    )
                }
            }

            // 7. POMODORO MODE
            item {
                SettingsSectionHeader("POMODORO MODE")
                SettingsCard {
                    SettingsToggleRow(
                        title = "Enable Pomodoro",
                        description = "Cycle work and breaks",
                    ) {
                        FocusFlowSwitch(
                            checked = settings.pomodoroEnabled,
                            onCheckedChange = { enabled ->
                                settingsViewModel.updateSettings(settings.copy(pomodoroEnabled = enabled))
                            },
                        )
                    }
                    if (settings.pomodoroEnabled) {
                        HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Work Duration", fontSize = 14.scaledSp, color = DarkTextSecondary)
                            Text("${settings.pomodoroWorkMinutes}m", fontSize = 14.scaledSp, fontWeight = FontWeight.Bold, color = BrandPrimary)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Break Duration", fontSize = 14.scaledSp, color = DarkTextSecondary)
                            Text("${settings.pomodoroBreakMinutes}m", fontSize = 14.scaledSp, fontWeight = FontWeight.Bold, color = BrandPrimary)
                        }
                    }
                }
            }

            // 8. BACKUP & DATA — keep the safety-net actions before device permissions.
            item {
                SettingsSectionHeader("BACKUP & DATA")
                SettingsCard {
                    SettingsActionRow(
                        icon = Icons.Outlined.CloudUpload,
                        title = "Export backup",
                        description = "Choose a location to save a .focusflow backup file",
                        enabled = exportState != ExportState.Building,
                        onClick = {
                            createBackupDocument.launch(BackupSerializer.buildSuggestedFilename())
                        },
                    )
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    SettingsActionRow(
                        icon = Icons.Outlined.CloudDownload,
                        title = "Import backup",
                        description = "Choose a .focusflow file to review before restoring",
                        enabled = importState != ImportState.Reading &&
                            importState != ImportState.Restoring,
                        onClick = { openBackupDocument.launch(arrayOf("*/*")) },
                    )
                }
            }

            // 9. PERMISSIONS
            item {
                SettingsSectionHeader("PERMISSIONS")
                SettingsCard {
                    SettingsActionRow(
                        icon = Icons.Outlined.Shield,
                        title = "Manage Permissions",
                        description = "Accessibility, Usage Access, Battery, Notifications",
                        onClick = onOpenPermissions,
                    )
                }
            }

            // 10. DIAGNOSTICS
            item {
                SettingsSectionHeader("DIAGNOSTICS")
                SettingsCard {
                    SettingsActionRow(
                        icon = Icons.Outlined.BugReport,
                        title = "Report an Issue",
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

            // 11. DATA
            item {
                SettingsSectionHeader("DATA")
                SettingsCard {
                    SettingsActionRow(
                        icon = Icons.Outlined.Delete,
                        title = "Clear All Tasks",
                        description = "Permanently delete all scheduled tasks",
                        destructive = true,
                        onClick = { clearAllConfirmationVisible = true },
                    )
                }
            }

            // 12. ABOUT
            item {
                SettingsSectionHeader("ABOUT")
                SettingsCard {
                    SettingsActionRow(
                        icon = Icons.Outlined.Help,
                        title = "How to Use",
                        description = "Learn the modes, protections, and settings",
                        onClick = onOpenHowToUse,
                    )
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    SettingsActionRow(
                        icon = Icons.Outlined.CloudUpload,
                        title = "FocusFlow File Guide",
                        description = "Learn about .focusflow backups and task-only AI prompts",
                        onClick = onOpenFileGuide,
                    )
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    SettingsActionRow(
                        icon = Icons.Outlined.RocketLaunch,
                        title = "What's New",
                        description = "Changelog — features, fixes, and improvements",
                        iconContainer = true,
                        onClick = onOpenChangelog,
                    )
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    SettingsActionRow(
                        icon = Icons.Outlined.Policy,
                        title = "Privacy & Terms",
                        description = "How FocusFlow handles your data and the rules of use",
                        onClick = onOpenPrivacyTerms,
                    )
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    SettingsActionRow(
                        icon = Icons.Outlined.Email,
                        title = "Contact Support",
                        description = "Email us at tbtechsdev@gmail.com",
                        onClick = ::contactSupport,
                    )
                }
            }

            // Footer version
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "FocusFlow v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                        fontSize = 12.5.scaledSp,
                        color = DarkTextMuted,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "All data stored locally on device",
                        fontSize = 12.scaledSp,
                        color = DarkTextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    OverlayAppearanceModal(
        visible = overlayAppearanceVisible,
        onClose = { overlayAppearanceVisible = false },
    )
    ReportIssueModal(
        visible = reportIssueVisible,
        logs = StartupLogger.recent(200).map { entry ->
            com.tbtechs.focusflow.ui.support.DiagnosticLogEntry(
                timestamp = entry.timestamp,
                level = com.tbtechs.focusflow.ui.support.DiagnosticLogLevel.valueOf(entry.level.name),
                tag = entry.tag,
                message = entry.message,
            )
        },
        onClose = { reportIssueVisible = false },
    )

    AllowedAppsModal(
        visible = allowedAppsVisible,
        initialSelected = settings.allowedFocusPackages,
        presets = settings.launcherPresets,
        installedAppsRepository = installedAppsRepository,
        onSave = { packages ->
            settingsViewModel.updateSettings(settings.copy(allowedFocusPackages = packages))
            allowedAppsVisible = false
        },
        onSavePreset = { preset ->
            settingsViewModel.updateSettings(settings.copy(launcherPresets = settings.launcherPresets + preset))
        },
        onDeletePreset = { presetId ->
            settingsViewModel.updateSettings(
                settings.copy(launcherPresets = settings.launcherPresets.filterNot { it.id == presetId }),
            )
        },
        onClose = { allowedAppsVisible = false },
    )

    if (clearAllConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { clearAllConfirmationVisible = false },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Clear All Tasks", fontWeight = FontWeight.Bold) },
            text = { Text("This will delete all ${tasks.size} scheduled task${if (tasks.size == 1) "" else "s"}. Are you sure?") },
            confirmButton = {
                Button(
                    onClick = {
                        clearAllConfirmationVisible = false
                        if (focusSession?.isActive == true && settingsViewModel.isFocusPinSet()) {
                            pendingClearAll = true
                            focusPinVisible = true
                        } else {
                            taskViewModel.clearAllTasks()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = { clearAllConfirmationVisible = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }

    PinVerifyModal(
        visible = focusPinVisible,
        pinType = PinType.FOCUS,
        title = "Focus Session Password Required",
        description = "Enter your focus session password before clearing tasks and ending the active focus session.",
        verify = settingsViewModel::verifyFocusPin,
        onVerified = { hash ->
            focusPinVisible = false
            if (pendingClearAll) {
                pendingClearAll = false
                taskViewModel.clearAllTasks(hash)
            }
        },
        onCancel = {
            pendingClearAll = false
            focusPinVisible = false
            if (focusSession?.isActive == true) {
                focusSession?.let { activeSession ->
                    taskViewModel.clearAllTasksExcept(activeSession.taskId)
                }
            } else {
                taskViewModel.clearAllTasks()
            }
        },
    )

    notice?.let { activeNotice ->
        AlertDialog(
            onDismissRequest = { notice = null },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text(activeNotice.title, fontWeight = FontWeight.Bold) },
            text = { Text(activeNotice.body) },
            confirmButton = {
                Button(
                    onClick = { notice = null },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("OK") }
            },
        )
    }

    if (exportState == ExportState.Building) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Saving backup", fontWeight = FontWeight.Bold) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        color = BrandPrimary,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                    Text("Writing the backup file…")
                }
            },
            confirmButton = {},
        )
    }

    if (importState == ImportState.Reading) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Reading backup", fontWeight = FontWeight.Bold) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        color = BrandPrimary,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                    Text("Checking the selected backup…")
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = backupViewModel::cancelImport) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }

    exportNotice?.let { state ->
        val succeeded = state is ExportState.Success
        AlertDialog(
            onDismissRequest = {
                exportNotice = null
                backupViewModel.resetExport()
            },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = {
                Text(
                    if (succeeded) "Backup exported" else "Export failed",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                when (state) {
                    is ExportState.Success ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("The backup was saved successfully.")
                            state.warnings.forEach { warning ->
                                Text(
                                    text = warning,
                                    color = DarkTextSecondary,
                                    fontSize = 13.scaledSp,
                                    lineHeight = 19.scaledSp,
                                )
                            }
                        }
                    is ExportState.Error ->
                        Text(
                            text = state.message,
                            modifier = Modifier
                                .heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    else -> Unit
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        exportNotice = null
                        backupViewModel.resetExport()
                    },
                ) { Text("OK", color = BrandPrimary) }
            },
        )
    }

    importErrorNotice?.let { message ->
        AlertDialog(
            onDismissRequest = {
                importErrorNotice = null
                backupViewModel.resetImport()
            },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Import failed", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = message,
                    modifier = Modifier
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        importErrorNotice = null
                        backupViewModel.resetImport()
                    },
                ) { Text("OK", color = BrandPrimary) }
            },
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.scaledSp,
        fontWeight = FontWeight.Bold,
        color = DarkTextMuted,
        letterSpacing = 0.8.scaledSp,
        modifier = Modifier
            .padding(start = 4.dp, bottom = 6.dp)
            .semantics { heading() },
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarkCard,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    description: String? = null,
    destructive: Boolean = false,
    iconContainer: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .then(if (enabled) Modifier else Modifier.background(DarkCard.copy(alpha = 0.7f)))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconContainer) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(BrandPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (destructive) Color(0xFFEF4444) else BrandPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (destructive) Color(0xFFEF4444) else BrandPrimary,
                modifier = Modifier
                    .size(22.dp)
                    .padding(horizontal = 1.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.scaledSp,
                fontWeight = FontWeight.SemiBold,
                color = if (destructive) Color(0xFFEF4444) else DarkTextPrimary,
            )
            if (description != null) {
                Spacer(Modifier.height(1.dp))
                Text(
                    description,
                    fontSize = 13.scaledSp,
                    color = DarkTextSecondary,
                    lineHeight = 16.scaledSp,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = if (destructive) Color(0xFFEF4444).copy(alpha = 0.6f) else DarkTextMuted,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.48f)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.scaledSp,
                fontWeight = FontWeight.SemiBold,
                color = DarkTextPrimary,
            )
            if (description != null) {
                Spacer(Modifier.height(1.dp))
                Text(
                    description,
                    fontSize = 13.scaledSp,
                    color = DarkTextSecondary,
                    lineHeight = 16.scaledSp,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        control()
    }
}

@Composable
private fun TaskReminderOptions(
    remindersEnabled: Boolean,
    enabledCount: Int,
    preStartChecked: Boolean,
    atStartChecked: Boolean,
    midSessionChecked: Boolean,
    wrapUpChecked: Boolean,
    onPreStartChange: (Boolean) -> Unit,
    onAtStartChange: (Boolean) -> Unit,
    onMidSessionChange: (Boolean) -> Unit,
    onWrapUpChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val summary = when {
        !remindersEnabled -> "Reminders are off"
        enabledCount == 0 -> "No reminder types selected"
        enabledCount == 4 -> "All 4 reminder types on"
        else -> "$enabledCount of 4 reminder types on"
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics {
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Reminder timing",
                    fontSize = 15.scaledSp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkTextPrimary,
                )
                Text(
                    text = summary,
                    fontSize = 13.scaledSp,
                    color = DarkTextSecondary,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = DarkTextMuted,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.padding(start = 8.dp)) {
                SettingsToggleRow(
                    title = "Before task starts",
                    description = "10, 5, and 1 minute before",
                    enabled = remindersEnabled,
                ) {
                    FocusFlowSwitch(
                        checked = preStartChecked,
                        enabled = remindersEnabled,
                        onCheckedChange = onPreStartChange,
                    )
                }
                SettingsToggleRow(
                    title = "At task start",
                    enabled = remindersEnabled,
                ) {
                    FocusFlowSwitch(
                        checked = atStartChecked,
                        enabled = remindersEnabled,
                        onCheckedChange = onAtStartChange,
                    )
                }
                SettingsToggleRow(
                    title = "Mid-task check-ins",
                    description = "15- and 30-minute reminders",
                    enabled = remindersEnabled,
                ) {
                    FocusFlowSwitch(
                        checked = midSessionChecked,
                        enabled = remindersEnabled,
                        onCheckedChange = onMidSessionChange,
                    )
                }
                SettingsToggleRow(
                    title = "Wrapping up",
                    description = "One minute before a task ends",
                    enabled = remindersEnabled,
                ) {
                    FocusFlowSwitch(
                        checked = wrapUpChecked,
                        enabled = remindersEnabled,
                        onCheckedChange = onWrapUpChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskEndAlertSelector(
    ringingAlarm: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTitle = if (ringingAlarm) "Ringing alarm" else "Notification only"
    val selectedDescription = if (ringingAlarm) {
        "Sound + full-screen task controls"
    } else {
        "Quiet alert with task actions"
    }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button) { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Task end alert",
                    fontSize = 15.scaledSp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkTextPrimary,
                )
                Text(
                    text = selectedTitle,
                    fontSize = 14.scaledSp,
                    fontWeight = FontWeight.Medium,
                    color = BrandPrimary,
                )
                Text(
                    text = selectedDescription,
                    fontSize = 12.scaledSp,
                    color = DarkTextSecondary,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = "Choose task end alert",
                tint = DarkTextMuted,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Ringing alarm", color = DarkTextPrimary)
                        Text(
                            "Play sound and show full-screen controls",
                            fontSize = 12.scaledSp,
                            color = DarkTextSecondary,
                        )
                    }
                },
                onClick = {
                    expanded = false
                    onSelect(true)
                },
                trailingIcon = {
                    if (ringingAlarm) {
                        Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = BrandPrimary)
                    }
                },
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Notification only", color = DarkTextPrimary)
                        Text(
                            "Quiet alert; tap to open task actions",
                            fontSize = 12.scaledSp,
                            color = DarkTextSecondary,
                        )
                    }
                },
                onClick = {
                    expanded = false
                    onSelect(false)
                },
                trailingIcon = {
                    if (!ringingAlarm) {
                        Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = BrandPrimary)
                    }
                },
            )
        }
    }
}

private data class SettingsNotice(val title: String, val body: String)
