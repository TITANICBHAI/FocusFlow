package com.tbtechs.focusflow.ui.settings

import androidx.compose.ui.res.stringResource
import com.tbtechs.focusflow.R
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
import androidx.compose.material.icons.outlined.Analytics
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
import androidx.compose.material.icons.outlined.Language
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
import com.tbtechs.focusflow.ui.backup.BackupExportNoticeDialog
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
    onOpenUsageDataGuide: () -> Unit = {},
    onOpenFileGuide: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
    onReportIssue: (() -> Unit)? = null,
    onOpenChangelog: () -> Unit = {},
    onOpenPrivacyTerms: () -> Unit = {},
    onOpenImportConfirmation: (() -> Unit)? = null,
    externalImportEventNonce: Int = 0,
    onOpenCustomRules: () -> Unit = {},
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
            title = context.getString(if (granted) R.string.settings_notifications_enabled else R.string.settings_permission_denied),
            body = context.getString(if (granted) R.string.settings_notifications_enabled_body else R.string.settings_notifications_denied_body),
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
            backupViewModel.beginImport(context.contentResolver, uri, context.cacheDir)
        }
    }

    LaunchedEffect(externalImportEventNonce) {
        val uri = BackupImportIntentRelay.consume() ?: return@LaunchedEffect
        backupViewModel.beginImport(context.contentResolver, uri, context.cacheDir)
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
        if (exportState is ExportState.Success ||
            exportState is ExportState.Error ||
            exportState is ExportState.TooLarge
        ) {
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
            unavailable(context.getString(R.string.settings_email_unavailable), context.getString(R.string.settings_email_unavailable_body))
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
                    title = { Text(stringResource(R.string.tab_settings), color = DarkTextPrimary, fontWeight = FontWeight.Bold) },
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
                        stringResource(R.string.tab_settings),
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
                SettingsSectionHeader(stringResource(R.string.settings_section_profile))
                SettingsCard {
                    SettingsActionRow(
                        icon = Icons.Outlined.AccountCircle,
                        title = profile?.optString("name").orEmpty().ifBlank { context.getString(R.string.settings_set_up_profile) },
                        description = profile?.let {
                            listOf(
                                it.optString("occupation").takeIf(String::isNotBlank),
                                it.optInt("dailyGoalHours", 0)
                                    .takeIf { hours -> hours > 0 }
                                    ?.let { hours -> context.getString(R.string.settings_daily_goal_hours, hours) },
                                it.optString("wakeUpTime").takeIf(String::isNotBlank)
                                    ?.let { time -> context.getString(R.string.settings_wakes_at, time) },
                            ).filterNotNull().joinToString(" · ")
                                .ifBlank { context.getString(R.string.settings_tap_to_personalise) }
                        } ?: context.getString(R.string.settings_profile_hint),
                        onClick = onOpenProfile,
                    )
                }
            }

            // 2. APPEARANCE
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_appearance))
                SettingsCard {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_dark_mode),
                        description = stringResource(R.string.settings_use_a_darker_color_palette),
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
                        title = stringResource(R.string.settings_text_size),
                        description = stringResource(R.string.settings_adjust_text_size_across_focusflow),
                        onClick = onOpenTextSize,
                    )
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                        SettingsActionRow(
                            icon = Icons.Outlined.Language,
                            title = stringResource(R.string.settings_language),
                            description = stringResource(R.string.settings_language_desc),
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS)
                                            .setData(Uri.fromParts("package", context.packageName, null))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                } catch (_: Exception) {
                                    // No locale page on this device; nothing to open.
                                }
                            },
                        )
                    }
                }
            }

            // 3. NOTIFICATIONS
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_notifications))
                SettingsCard {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_scheduled_task_reminders),
                        description = stringResource(R.string.settings_before_at_and_during_tasks),
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
                        title = stringResource(R.string.settings_daily_reflection_prompts),
                        description = stringResource(R.string.settings_show_the_questionnaire_and_day),
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
                        title = stringResource(R.string.settings_request_notification_permission),
                        onClick = { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    )
                }
            }

            // 4. SCHEDULING
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_scheduling))
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.settings_default_task_duration),
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
                SettingsSectionHeader(stringResource(R.string.settings_section_focus_mode))
                SettingsCard {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_auto_enable_focus_mode),
                        description = stringResource(R.string.settings_start_with_focus_tasks),
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
                        stringResource(R.string.settings_focus_all_blocked)
                    } else if (settings.allowedFocusPackages.size == 1) {
                        stringResource(R.string.settings_focus_one_allowed)
                    } else {
                        stringResource(R.string.settings_focus_many_allowed, settings.allowedFocusPackages.size)
                    }
                    SettingsActionRow(
                        icon = Icons.Outlined.Apps,
                        title = stringResource(R.string.settings_manage_allowed_apps),
                        description = allowedSummary,
                        onClick = { allowedAppsVisible = true },
                    )
                }
            }

            // 6. BLOCK OVERLAY
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_block_overlay))
                SettingsCard {
                    SettingsActionRow(
                        icon = Icons.Outlined.Smartphone,
                        title = stringResource(R.string.settings_overlay_appearance),
                        description = stringResource(R.string.settings_customize_the_block_screen),
                        onClick = { overlayAppearanceVisible = true },
                    )
                }
            }

            // 7. POMODORO MODE
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_pomodoro_mode))
                SettingsCard {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_enable_pomodoro),
                        description = stringResource(R.string.settings_cycle_work_and_breaks),
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
                            Text(stringResource(R.string.settings_work_duration), fontSize = 14.scaledSp, color = DarkTextSecondary)
                            Text("${settings.pomodoroWorkMinutes}m", fontSize = 14.scaledSp, fontWeight = FontWeight.Bold, color = BrandPrimary)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(R.string.settings_break_duration), fontSize = 14.scaledSp, color = DarkTextSecondary)
                            Text("${settings.pomodoroBreakMinutes}m", fontSize = 14.scaledSp, fontWeight = FontWeight.Bold, color = BrandPrimary)
                        }
                    }
                }
            }

            // 8. BACKUP & DATA — keep the safety-net actions before device permissions.
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_backup_data))
                SettingsCard {
                    SettingsActionRow(
                        icon = Icons.Outlined.CloudUpload,
                        title = stringResource(R.string.settings_export_backup),
                        description = stringResource(R.string.settings_choose_a_location_to_save),
                        enabled = exportState != ExportState.Building,
                        onClick = {
                            createBackupDocument.launch(BackupSerializer.buildSuggestedFilename())
                        },
                    )
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    SettingsActionRow(
                        icon = Icons.Outlined.CloudDownload,
                        title = stringResource(R.string.settings_import_backup),
                        description = stringResource(R.string.settings_choose_a_focusflow_file_to),
                        enabled = importState != ImportState.Reading &&
                            importState != ImportState.Restoring,
                        onClick = { openBackupDocument.launch(arrayOf("*/*")) },
                    )
                }
            }

            // 9. PERMISSIONS
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_permissions))
                SettingsCard {
                    SettingsActionRow(
                        icon = Icons.Outlined.Shield,
                        title = stringResource(R.string.settings_manage_permissions),
                        description = stringResource(R.string.settings_accessibility_usage_access_battery_notifications),
                        onClick = onOpenPermissions,
                    )
                }
            }

            // 9b. SCREEN RULES
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_screen_rules))
                SettingsCard {
                    SettingsActionRow(
                        icon = Icons.Outlined.Tune,
                        title = stringResource(R.string.settings_custom_screen_rules),
                        description = stringResource(R.string.settings_block_a_specific_screen_inside),
                        onClick = onOpenCustomRules,
                    )
                }
            }

            // 10. DIAGNOSTICS
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_diagnostics))
                SettingsCard {
                    SettingsActionRow(
                        icon = Icons.Outlined.BugReport,
                        title = stringResource(R.string.settings_report_an_issue),
                        description = stringResource(R.string.settings_review_and_email_a_bug),
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
                SettingsSectionHeader(stringResource(R.string.settings_section_data))
                SettingsCard {
                    SettingsActionRow(
                        icon = Icons.Outlined.Delete,
                        title = stringResource(R.string.settings_clear_all_tasks),
                        description = stringResource(R.string.settings_permanently_delete_all_scheduled_tasks),
                        destructive = true,
                        onClick = { clearAllConfirmationVisible = true },
                    )
                }
            }

            // 12. ABOUT
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_about))
                SettingsCard {
                    SettingsActionRow(
                        icon = Icons.Outlined.Help,
                        title = stringResource(R.string.settings_how_to_use),
                        description = stringResource(R.string.settings_learn_the_modes_protections_and),
                        onClick = onOpenHowToUse,
                    )
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    SettingsActionRow(
                        icon = Icons.Outlined.Analytics,
                        title = stringResource(R.string.settings_how_usage_data_is_calculated),
                        description = stringResource(R.string.settings_where_the_numbers_on_stats),
                        onClick = onOpenUsageDataGuide,
                    )
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    SettingsActionRow(
                        icon = Icons.Outlined.CloudUpload,
                        title = stringResource(R.string.settings_focusflow_file_guide),
                        description = stringResource(R.string.settings_learn_about_focusflow_backups_and),
                        onClick = onOpenFileGuide,
                    )
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    SettingsActionRow(
                        icon = Icons.Outlined.RocketLaunch,
                        title = stringResource(R.string.settings_what_s_new),
                        description = stringResource(R.string.settings_changelog_features_fixes_and_improvements),
                        iconContainer = true,
                        onClick = onOpenChangelog,
                    )
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    SettingsActionRow(
                        icon = Icons.Outlined.Policy,
                        title = stringResource(R.string.settings_privacy_terms),
                        description = stringResource(R.string.settings_how_focusflow_handles_your_data),
                        onClick = onOpenPrivacyTerms,
                    )
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    SettingsActionRow(
                        icon = Icons.Outlined.Email,
                        title = stringResource(R.string.settings_contact_support),
                        description = stringResource(R.string.settings_email_us_at_tbtechsdev_gmail),
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
                        stringResource(R.string.settings_version_build, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        fontSize = 12.5.scaledSp,
                        color = DarkTextMuted,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.settings_all_data_local),
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
        logs = StartupLogger.recent(500).map { entry ->
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
            title = { Text(stringResource(R.string.settings_clear_all_tasks), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (tasks.size == 1) stringResource(R.string.settings_delete_all_tasks_one)
                    else stringResource(R.string.settings_delete_all_tasks_many, tasks.size),
                )
            },
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
                ) { Text(stringResource(R.string.settings_clear_all)) }
            },
            dismissButton = {
                TextButton(onClick = { clearAllConfirmationVisible = false }) {
                    Text(stringResource(R.string.home_cancel), color = DarkTextSecondary)
                }
            },
        )
    }

    PinVerifyModal(
        visible = focusPinVisible,
        pinType = PinType.FOCUS,
        title = stringResource(R.string.settings_focus_session_password_required),
        description = stringResource(R.string.settings_enter_your_focus_session_password),
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
            title = { Text(stringResource(R.string.settings_saving_backup), fontWeight = FontWeight.Bold) },
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
                    Text(stringResource(R.string.settings_writing_the_backup_file))
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
            title = { Text(stringResource(R.string.settings_reading_backup), fontWeight = FontWeight.Bold) },
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
                    Text(stringResource(R.string.settings_checking_the_selected_backup))
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = backupViewModel::cancelImport) {
                    Text(stringResource(R.string.home_cancel), color = DarkTextSecondary)
                }
            },
        )
    }

    exportNotice?.let { state ->
        BackupExportNoticeDialog(
            state = state,
            onDismiss = { exportNotice = null },
            onReset = backupViewModel::resetExport,
            onExportWithoutImages = { uriString ->
                backupViewModel.exportWithoutImages(context.contentResolver, uriString)
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
            title = { Text(stringResource(R.string.settings_import_failed), fontWeight = FontWeight.Bold) },
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
        !remindersEnabled -> stringResource(R.string.settings_reminders_off)
        enabledCount == 0 -> stringResource(R.string.settings_reminders_none)
        enabledCount == 4 -> stringResource(R.string.settings_reminders_all)
        else -> stringResource(R.string.settings_reminders_some, enabledCount)
    }
    val expandedState = stringResource(if (expanded) R.string.settings_expanded else R.string.settings_collapsed)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics {
                    stateDescription = expandedState
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_reminder_timing),
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
                    title = stringResource(R.string.settings_before_task_starts),
                    description = stringResource(R.string.settings_10_5_and_1_minute),
                    enabled = remindersEnabled,
                ) {
                    FocusFlowSwitch(
                        checked = preStartChecked,
                        enabled = remindersEnabled,
                        onCheckedChange = onPreStartChange,
                    )
                }
                SettingsToggleRow(
                    title = stringResource(R.string.settings_at_task_start),
                    enabled = remindersEnabled,
                ) {
                    FocusFlowSwitch(
                        checked = atStartChecked,
                        enabled = remindersEnabled,
                        onCheckedChange = onAtStartChange,
                    )
                }
                SettingsToggleRow(
                    title = stringResource(R.string.settings_mid_task_check_ins),
                    description = stringResource(R.string.settings_15_and_30_minute_reminders),
                    enabled = remindersEnabled,
                ) {
                    FocusFlowSwitch(
                        checked = midSessionChecked,
                        enabled = remindersEnabled,
                        onCheckedChange = onMidSessionChange,
                    )
                }
                SettingsToggleRow(
                    title = stringResource(R.string.settings_wrapping_up),
                    description = stringResource(R.string.settings_one_minute_before_a_task),
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
    val selectedTitle = stringResource(if (ringingAlarm) R.string.settings_ringing_alarm else R.string.settings_notification_only)
    val selectedDescription = stringResource(
        if (ringingAlarm) R.string.settings_alarm_short_desc else R.string.settings_quiet_short_desc,
    )

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
                    text = stringResource(R.string.settings_task_end_alert),
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
                contentDescription = stringResource(R.string.settings_choose_task_end_alert),
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
                        Text(stringResource(R.string.settings_ringing_alarm), color = DarkTextPrimary)
                        Text(
                            stringResource(R.string.settings_alarm_long_desc),
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
                        Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.settings_selected), tint = BrandPrimary)
                    }
                },
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text(stringResource(R.string.settings_notification_only), color = DarkTextPrimary)
                        Text(
                            stringResource(R.string.settings_quiet_long_desc),
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
                        Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.settings_selected), tint = BrandPrimary)
                    }
                },
            )
        }
    }
}

private data class SettingsNotice(val title: String, val body: String)
