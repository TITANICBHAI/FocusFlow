package com.tbtechs.focusflow.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tbtechs.focusflow.data.model.AllowedAppPreset
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.DailyAllowanceEntry
import com.tbtechs.focusflow.data.model.FocusSession
import com.tbtechs.focusflow.data.model.StandaloneBlockAndAllowanceConfig
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.data.repository.UsageStatsRepository
import com.tbtechs.focusflow.domain.FocusPinManager
import com.tbtechs.focusflow.ui.FocusSessionViewModel
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.TaskViewModel
import com.tbtechs.focusflow.ui.common.FocusFlowTimerIcon
import com.tbtechs.focusflow.ui.common.taskTimeDisplay
import com.tbtechs.focusflow.ui.defense.BlockPresetUi
import com.tbtechs.focusflow.ui.defense.StandaloneBlockModal
import com.tbtechs.focusflow.ui.navigation.FocusTabIcons
import com.tbtechs.focusflow.ui.navigation.RouteTextScaleContext
import com.tbtechs.focusflow.ui.navigation.Routes
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowTextScale
import com.tbtechs.focusflow.ui.theme.WarningBorder
import com.tbtechs.focusflow.ui.theme.WarningIcon
import com.tbtechs.focusflow.ui.theme.WarningSurface
import com.tbtechs.focusflow.ui.theme.WarningText
import com.tbtechs.focusflow.ui.theme.scaledSp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Native Focus tab redesigned for the rich dark navy design system.
 * Matches screenshots 4b (Ready to focus), 4c (Active standalone block),
 * 6f (Scheduled focus task), and 6g (Active focus session).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    taskViewModel: TaskViewModel = viewModel(factory = TaskViewModel.Factory),
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    focusSessionViewModel: FocusSessionViewModel = viewModel(factory = FocusSessionViewModel.Factory),
    onOpenActiveBlocks: () -> Unit = {},
    onOpenSchedule: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
) {
    val dimensions = LocalFocusFlowDimensions.current
    val context = LocalContext.current
    val now by rememberClock()
    val pinManager = remember { FocusPinManager(context) }
    val tasks by taskViewModel.tasks.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()
    val session by focusSessionViewModel.focusSession.collectAsState()
    val focusBreak by focusSessionViewModel.focusBreak.collectAsState()
    val isFocusing = session?.isActive == true
    val task = resolveFocusTask(tasks, session, now)
    val standaloneActive = settings.isStandaloneActive(now)

    var accessibilityGranted by remember { mutableStateOf<Boolean?>(null) }
    var usageGranted by remember { mutableStateOf<Boolean?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showStandaloneEditor by remember { mutableStateOf(false) }
    var showExtend by remember { mutableStateOf(false) }
    var showStopConfirmation by remember { mutableStateOf(false) }
    var showEmergencyConfirmation by remember { mutableStateOf(false) }
    var showFocusPin by remember { mutableStateOf(false) }
    var focusPin by remember { mutableStateOf("") }
    var focusPinError by remember { mutableStateOf<String?>(null) }
    var pendingPinAction by remember { mutableStateOf<PendingPinAction?>(null) }
    var showCompleteConfirmation by remember { mutableStateOf(false) }
    var showSkipConfirmation by remember { mutableStateOf(false) }
    var activePanel by remember { mutableStateOf(FocusPanel.TASK) }

    LaunchedEffect(Unit) {
        while (true) {
            val permissions = withContext(Dispatchers.IO) {
                runCatching {
                    val repository = UsageStatsRepository(context)
                    repository.hasAccessibilityPermission() to repository.hasPermission()
                }.getOrDefault(false to false)
            }
            accessibilityGranted = permissions.first
            usageGranted = permissions.second
            delay(2_000)
        }
    }

    LaunchedEffect(task?.id) {
        if (task == null) activePanel = FocusPanel.TASK
    }

    fun requestStart(taskId: String) {
        if (accessibilityGranted != true || usageGranted != true) {
            showPermissionDialog = true
        } else {
            focusSessionViewModel.startFocusMode(taskId)
        }
    }

    fun requestStop() {
        if (settingsViewModel.isFocusPinSet()) {
            pendingPinAction = PendingPinAction.STOP
            focusPin = ""
            focusPinError = null
            showFocusPin = true
        } else {
            showStopConfirmation = true
        }
    }

    fun requestEmergencyOverride() {
        showEmergencyConfirmation = true
    }

    fun confirmPin() {
        if (focusPin.isBlank()) {
            focusPinError = "Enter your focus session password."
            return
        }
        if (!settingsViewModel.verifyFocusPin(focusPin)) {
            focusPin = ""
            focusPinError = "Incorrect password. Try again."
            return
        }
        val hash = pinManager.hash(focusPin)
        val action = pendingPinAction
        focusPin = ""
        focusPinError = null
        pendingPinAction = null
        showFocusPin = false
        if (action == PendingPinAction.EMERGENCY) {
            task?.id?.let { focusSessionViewModel.recordOverride(it, "manual-override") }
        }
        focusSessionViewModel.stopFocusMode(hash)
    }

    fun stopAfterConfirmation() {
        showStopConfirmation = false
        focusSessionViewModel.stopFocusMode()
    }

    fun overrideAfterConfirmation() {
        showEmergencyConfirmation = false
        if (settingsViewModel.isFocusPinSet()) {
            pendingPinAction = PendingPinAction.EMERGENCY
            focusPin = ""
            focusPinError = null
            showFocusPin = true
        } else {
            task?.id?.let { focusSessionViewModel.recordOverride(it, "manual-override") }
            focusSessionViewModel.stopFocusMode()
        }
    }

    fun saveStandalone(
        packages: List<String>,
        untilMs: Long?,
        allowances: List<DailyAllowanceEntry>,
        vpnPackages: List<String>,
        rawPin: String?,
    ) {
        val active = packages.isNotEmpty() && (untilMs ?: 0L) > now
        val pinHash = rawPin?.takeIf { it.isNotBlank() }
        settingsViewModel.setStandaloneBlockAndAllowance(
            StandaloneBlockAndAllowanceConfig(
                standaloneBlockActive = active,
                standaloneBlockPackages = packages,
                standaloneBlockUntilMs = if (active) untilMs ?: 0L else 0L,
                allowanceEntries = allowances,
                standaloneBlockVpnPackages = if (active) vpnPackages else emptyList(),
                pinHash = pinHash,
            ),
        )
        showStandaloneEditor = false
    }

    fun saveBlockPreset(preset: BlockPresetUi) {
        val next = settings.launcherPresets
            .filterNot { it.id == preset.id }
            .plus(AllowedAppPreset(preset.id, preset.name, preset.packages))
        settingsViewModel.updateSettings(settings.copy(launcherPresets = next))
    }

    fun deleteBlockPreset(id: String) {
        settingsViewModel.updateSettings(
            settings.copy(launcherPresets = settings.launcherPresets.filterNot { it.id == id }),
        )
    }

    fun addStandaloneTime(minutes: Int) {
        val until = maxOf(settings.standaloneBlockUntilMs, now) + minutes * 60_000L
        settingsViewModel.setStandaloneBlockAndAllowance(
            StandaloneBlockAndAllowanceConfig(
                standaloneBlockActive = settings.standaloneBlockPackages.isNotEmpty(),
                standaloneBlockPackages = settings.standaloneBlockPackages,
                standaloneBlockUntilMs = until,
                allowanceEntries = settings.dailyAllowanceEntries(),
                standaloneBlockVpnPackages = settings.standaloneBlockVpnPackages,
            ),
        )
    }

    fun startQuickPreset(preset: AllowedAppPreset) {
        if (accessibilityGranted != true || usageGranted != true) {
            showPermissionDialog = true
            return
        }
        val packages = (if (standaloneActive) settings.standaloneBlockPackages else emptyList()) +
            preset.packages
        val until = maxOf(settings.standaloneBlockUntilMs, now) + 60 * 60_000L
        settingsViewModel.setStandaloneBlockAndAllowance(
            StandaloneBlockAndAllowanceConfig(
                standaloneBlockActive = packages.isNotEmpty(),
                standaloneBlockPackages = packages.distinct(),
                standaloneBlockUntilMs = until,
                allowanceEntries = settings.dailyAllowanceEntries(),
                standaloneBlockVpnPackages = settings.standaloneBlockVpnPackages,
            ),
        )
    }

    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
            ) {
                ActiveStatusIndicator(
                    focusSession = session,
                    settings = settings,
                    onOpenActiveBlocks = onOpenActiveBlocks,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 16.dp),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Permission Warning Banner
            if (accessibilityGranted == false && !isFocusing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimensions.screenPadding, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(WarningSurface)
                        .border(1.dp, WarningBorder, RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenPermissions)
                        .padding(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = WarningIcon,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Accessibility permission needed",
                                fontSize = 14.scaledSp,
                                fontWeight = FontWeight.Bold,
                                color = WarningText,
                            )
                            Text(
                                "Focus Mode can't block apps without Accessibility access. Tap to open Settings.",
                                fontSize = 12.scaledSp,
                                color = WarningText.copy(alpha = 0.88f),
                                lineHeight = 16.scaledSp,
                            )
                        }
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = WarningIcon,
                        )
                    }
                }
            }

            when {
                task == null && standaloneActive -> FocusSubscreenTextScale(
                    settings = settings,
                    screenId = RouteTextScaleContext.FOCUS_STANDALONE_PANEL_SCREEN,
                ) {
                    StandaloneBlockPanel(
                        settings = settings,
                        now = now,
                        presets = settings.launcherPresets,
                        onAddTime = ::addStandaloneTime,
                        onQuickPreset = ::startQuickPreset,
                        onEdit = { showStandaloneEditor = true },
                    )
                }

                task == null && isFocusing -> OrphanedFocusPanel(onStop = ::requestStop)

                task == null -> ReadyToFocusPanel(
                    presets = settings.launcherPresets,
                    onOpenSchedule = onOpenSchedule,
                    onOpenStandalone = { showStandaloneEditor = true },
                    onQuickPreset = ::startQuickPreset,
                )

                standaloneActive && activePanel == FocusPanel.BLOCK -> {
                    FocusPanelSwitcher(activePanel) { activePanel = it }
                    StandaloneBlockPanel(
                        settings = settings,
                        now = now,
                        presets = settings.launcherPresets,
                        onAddTime = ::addStandaloneTime,
                        onQuickPreset = ::startQuickPreset,
                        onEdit = { showStandaloneEditor = true },
                    )
                }

                else -> {
                    if (standaloneActive) {
                        FocusPanelSwitcher(activePanel) { activePanel = it }
                    }
                    TaskFocusPanel(
                        task = task,
                        now = now,
                        session = session,
                        isFocusing = isFocusing,
                        settings = settings,
                        focusBreakActive = focusBreak.active,
                        focusBreakUntilMs = focusBreak.untilMs,
                        otherActiveCount = tasks.count { it.isActiveAt(now) && it.id != task.id },
                        onStart = { requestStart(task.id) },
                        onStop = ::requestStop,
                        onComplete = { showCompleteConfirmation = true },
                        onExtend = { showExtend = true },
                        onSkip = { showSkipConfirmation = true },
                        onOpenSchedule = onOpenSchedule,
                        onOpenStandalone = { showStandaloneEditor = true },
                        onEmergencyOverride = ::requestEmergencyOverride,
                        onStartBreak = {
                            focusSessionViewModel.startPomodoroBreak(settings.pomodoroBreakMinutes)
                        },
                        onEndBreak = focusSessionViewModel::endPomodoroBreak,
                        standaloneActive = standaloneActive,
                        onOpenStandalonePanel = { activePanel = FocusPanel.BLOCK },
                    )
                }
            }
        }
    }

    if (showStandaloneEditor) {
        FocusSubscreenTextScale(
            settings = settings,
            screenId = RouteTextScaleContext.FOCUS_STANDALONE_SETUP_SCREEN,
        ) {
            StandaloneBlockModal(
                visible = true,
                blockedPackages = settings.standaloneBlockPackages,
                blockUntilMs = settings.standaloneBlockUntilMs,
                locked = standaloneActive,
                dailyAllowanceEntries = settings.dailyAllowanceEntries(),
                vpnPackages = settings.standaloneBlockVpnPackages,
                presets = settings.launcherPresets.map { BlockPresetUi(it.id, it.name, it.packages) },
                onSave = { packages, untilMs, allowances, vpnPackages, rawPin ->
                    saveStandalone(packages, untilMs, allowances, vpnPackages, rawPin)
                },
                onSavePreset = ::saveBlockPreset,
                onDeletePreset = ::deleteBlockPreset,
                onClose = { showStandaloneEditor = false },
                hintDismissed = settings.standaloneBlockHintDismissed,
                onDismissHint = {
                    settingsViewModel.updateSettings(settings.copy(standaloneBlockHintDismissed = true))
                },
                verifyPin = settingsViewModel::verifyFocusPin,
                sessionPinSet = settingsViewModel.isFocusPinSet(),
                hashPin = pinManager::hash,
            )
        }
    }

    if (showExtend && task != null) {
        FocusSubscreenTextScale(
            settings = settings,
            screenId = RouteTextScaleContext.FOCUS_EXTENSION_SCREEN,
        ) {
            ExtendModal(
                taskName = task.title,
                onDismiss = { showExtend = false },
                onExtend = { minutes ->
                    taskViewModel.extendTaskTime(task.id, minutes)
                    showExtend = false
                },
            )
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Permissions Required", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "FocusFlow needs Accessibility and Usage Access to block apps during Focus Mode. Grant them in Settings, then try again.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        onOpenPermissions()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Open Permissions") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Not Now", color = DarkTextSecondary)
                }
            },
        )
    }

    if (showStopConfirmation) {
        AlertDialog(
            onDismissRequest = { showStopConfirmation = false },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Stop Focus", fontWeight = FontWeight.Bold) },
            text = { Text("End focus mode for this task? App blocking will stop.") },
            confirmButton = {
                Button(
                    onClick = ::stopAfterConfirmation,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Stop") }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmation = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }

    if (showEmergencyConfirmation) {
        AlertDialog(
            onDismissRequest = { showEmergencyConfirmation = false },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Emergency Override", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444)) },
            text = {
                Text("This will stop Focus Mode and be logged. Only use it in a genuine emergency.")
            },
            confirmButton = {
                Button(
                    onClick = ::overrideAfterConfirmation,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Override") }
            },
            dismissButton = {
                TextButton(onClick = { showEmergencyConfirmation = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }

    if (showFocusPin) {
        AlertDialog(
            onDismissRequest = {
                showFocusPin = false
                pendingPinAction = null
                focusPin = ""
                focusPinError = null
            },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Stop Focus Session", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter your focus session password to end the session and stop all blocking.")
                    OutlinedTextField(
                        value = focusPin,
                        onValueChange = { focusPin = it; focusPinError = null },
                        label = { Text("Focus session password", color = DarkTextMuted) },
                        singleLine = true,
                        isError = focusPinError != null,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DarkTextPrimary,
                            unfocusedTextColor = DarkTextPrimary,
                            focusedBorderColor = BrandPrimary,
                            unfocusedBorderColor = DarkBorder,
                        ),
                    )
                    focusPinError?.let {
                        Text(it, color = Color(0xFFF87171), fontSize = 12.scaledSp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = ::confirmPin,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFocusPin = false
                    pendingPinAction = null
                    focusPin = ""
                    focusPinError = null
                }) { Text("Cancel", color = DarkTextSecondary) }
            },
        )
    }

    if (showCompleteConfirmation && task != null) {
        AlertDialog(
            onDismissRequest = { showCompleteConfirmation = false },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Complete task?", fontWeight = FontWeight.Bold) },
            text = { Text("Mark “${task.title}” as done?") },
            confirmButton = {
                Button(
                    onClick = {
                        taskViewModel.completeTask(task.id)
                        if ((!settings.keepFocusActiveUntilTaskEnd || task.remainingMillis(now) < 0L) &&
                            focusSessionViewModel.focusSession.value?.taskId == task.id
                        ) {
                            focusSessionViewModel.stopFocusMode()
                        }
                        showCompleteConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteConfirmation = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }

    if (showSkipConfirmation && task != null) {
        AlertDialog(
            onDismissRequest = { showSkipConfirmation = false },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Skip task?", fontWeight = FontWeight.Bold) },
            text = { Text("Skip “${task.title}”?") },
            confirmButton = {
                Button(
                    onClick = {
                        taskViewModel.skipTask(task.id)
                        if ((!settings.keepFocusActiveUntilTaskEnd || task.remainingMillis(now) < 0L) &&
                            focusSessionViewModel.focusSession.value?.taskId == task.id
                        ) {
                            focusSessionViewModel.stopFocusMode()
                        }
                        showSkipConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Skip") }
            },
            dismissButton = {
                TextButton(onClick = { showSkipConfirmation = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }
}

private enum class FocusPanel { TASK, BLOCK }

private enum class PendingPinAction { STOP, EMERGENCY }

/**
 * Screenshot 4b: Empty state "Ready to focus?"
 */
@Composable
private fun ReadyToFocusPanel(
    presets: List<AllowedAppPreset>,
    onOpenSchedule: () -> Unit,
    onOpenStandalone: () -> Unit,
    onQuickPreset: (AllowedAppPreset) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        item {
            // Big elevated timer icon badge
            Box(
                modifier = Modifier
                .size(54.dp)
                    .clip(CircleShape)
                    .background(BrandPrimary.copy(alpha = 0.15f))
                    .border(1.dp, BrandPrimary.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                FocusFlowTimerIcon(
                    modifier = Modifier.size(28.dp),
                    selected = true,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Ready to focus?",
                fontSize = 22.scaledSp,
                fontWeight = FontWeight.Bold,
                color = DarkTextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Choose a task from Schedule to start a focused session.",
                fontSize = 15.scaledSp,
                color = DarkTextSecondary,
                lineHeight = 22.scaledSp,
            )
            Spacer(Modifier.height(18.dp))

            Button(
                onClick = onOpenSchedule,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                shape = RoundedCornerShape(12.dp),
            ) {
                 Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open Schedule", fontSize = 15.scaledSp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(12.dp))

            // Card: Block Apps Without a Task
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkCard)
                    .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenStandalone)
                    .padding(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Block,
                            contentDescription = null,
                            tint = BrandPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Block Apps Without a Task",
                            fontSize = 15.scaledSp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkTextPrimary,
                        )
                        Text(
                            "Start a standalone block",
                            fontSize = 13.scaledSp,
                            color = DarkTextSecondary,
                        )
                    }
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = DarkTextMuted,
                    )
                }
            }
        }

        item {
            QuickPresetStrip(presets, active = false, onClick = onQuickPreset)
        }
    }
}

@Composable
private fun OrphanedFocusPanel(onStop: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(WarningSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = WarningIcon,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            "Focus session needs attention",
            fontSize = 20.scaledSp,
            fontWeight = FontWeight.Bold,
            color = DarkTextPrimary,
        )
        Text(
            "A focus session is active, but its task is no longer available. Stop the session here to clear blocking safely.",
            fontSize = 14.scaledSp,
            color = DarkTextSecondary,
            lineHeight = 20.scaledSp,
        )
        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
            shape = RoundedCornerShape(10.dp),
        ) {
            Icon(Icons.Outlined.StopCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Stop Focus")
        }
    }
}

/**
 * Screenshots 6f (Scheduled focus task) & 6g (Active focus session)
 */
@Composable
private fun TaskFocusPanel(
    task: Task,
    now: Long,
    session: FocusSession?,
    isFocusing: Boolean,
    settings: AppSettings,
    focusBreakActive: Boolean,
    focusBreakUntilMs: Long,
    otherActiveCount: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onComplete: () -> Unit,
    onExtend: () -> Unit,
    onSkip: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenStandalone: () -> Unit,
    onEmergencyOverride: () -> Unit,
    onStartBreak: () -> Unit,
    onEndBreak: () -> Unit,
    standaloneActive: Boolean,
    onOpenStandalonePanel: () -> Unit,
) {
    val remaining = task.remainingMillis(now)
    val progress = task.progressNow(now)
    val timeDisplay = taskTimeDisplay(parseMillis(task.endTime), now)
    val displayOverdue = timeDisplay.hasEnded && task.status !in setOf("completed", "skipped")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Status indicator chip
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFocusing) Color(0xFF10B981)
                            else if (displayOverdue) Color(0xFFEF4444)
                            else DarkTextSecondary,
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        isFocusing && settings.pomodoroEnabled -> {
                            if (focusBreakActive) "Focus Mode Active · Break · apps unlocked"
                            else "Focus Mode Active · Work"
                        }
                        isFocusing -> "Focus Mode Active"
                        displayOverdue -> "Task ended — choose next action"
                        else -> "Task scheduled"
                    },
                    fontSize = 14.scaledSp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isFocusing) Color(0xFF34D399) else if (displayOverdue) Color(0xFFF87171) else DarkTextSecondary,
                )
            }
        }

        // Hero Card with Countdown & Progress
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .border(1.dp, if (isFocusing) BrandPrimary.copy(alpha = 0.4f) else DarkBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = task.remainingLabel(now),
                        fontSize = 46.scaledSp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).scaledSp,
                        color = if (displayOverdue) Color(0xFFF87171) else BrandPrimary,
                    )
                    Text(
                        text = when {
                            timeDisplay.justEnded -> "choose an action"
                            displayOverdue -> "overdue"
                            else -> "remaining"
                        },
                        fontSize = 13.scaledSp,
                        fontWeight = FontWeight.Medium,
                        color = DarkTextMuted,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = task.title,
                        fontSize = 22.scaledSp,
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary,
                    )
                    Text(
                        text = "${task.startLabel()} – ${task.endLabel()}",
                        fontSize = 15.scaledSp,
                        color = DarkTextSecondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                         color = if (displayOverdue) Color(0xFFEF4444) else BrandPrimary,
                        trackColor = DarkSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                             text = if (displayOverdue) "Overdue" else "${(progress * 100).toInt()}% complete",
                            fontSize = 12.scaledSp,
                            color = DarkTextSecondary,
                        )
                        if (task.tags.isNotEmpty()) {
                            Text(
                                text = task.tags.joinToString(" ") { "#$it" },
                                fontSize = 12.scaledSp,
                                color = BrandPrimary,
                            )
                        }
                    }
                }
            }
        }

        // Pomodoro Strip
        if (isFocusing && settings.pomodoroEnabled) {
            item {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    PomodoroStrip(
                        sessionStartedAt = session?.startedAt,
                        workMinutes = settings.pomodoroWorkMinutes,
                        breakMinutes = settings.pomodoroBreakMinutes,
                        breakActive = focusBreakActive,
                        breakUntilMs = focusBreakUntilMs,
                        onStartBreak = onStartBreak,
                        onEndBreak = onEndBreak,
                    )
                }
            }
        }

        // Multiple active tasks count
        if (otherActiveCount > 0) {
            item {
                TextButton(
                    onClick = onOpenSchedule,
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) {
                    Icon(Icons.Outlined.Layers, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("+$otherActiveCount more active task${if (otherActiveCount == 1) "" else "s"}", color = BrandPrimary)
                }
            }
        }

        // Overdue Action Box
        if (displayOverdue) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Time's up — what next?",
                            fontSize = 16.scaledSp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFCA5A5),
                        )
                        Text(
                            "This task ran past its scheduled end. Choose an action to resolve it.",
                            fontSize = 13.scaledSp,
                            color = Color(0xFFFCA5A5).copy(alpha = 0.85f),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onComplete,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 6.dp,
                                    vertical = 8.dp,
                                ),
                            ) {
                                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Done", fontSize = 12.scaledSp, maxLines = 1, softWrap = false)
                            }
                            Button(
                                onClick = onExtend,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 6.dp,
                                    vertical = 8.dp,
                                ),
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Extend", fontSize = 12.scaledSp, maxLines = 1, softWrap = false)
                            }
                            OutlinedButton(
                                onClick = onSkip,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFCA5A5)),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFEF4444))),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 6.dp,
                                    vertical = 8.dp,
                                ),
                            ) {
                                Text("Skip", fontSize = 12.scaledSp, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }

        // Action Buttons Column
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!isFocusing && !displayOverdue) {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(FocusTabIcons.Active, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Activate Focus", fontSize = 16.scaledSp, fontWeight = FontWeight.SemiBold)
                    }
                } else if (isFocusing) {
                    OutlinedButton(
                        onClick = onStop,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = DarkCard,
                            contentColor = DarkTextPrimary,
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)),
                    ) {
                        Icon(Icons.Outlined.StopCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Stop Focus", fontSize = 15.scaledSp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (!displayOverdue) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onComplete,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = DarkCard,
                                contentColor = DarkTextPrimary,
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)),
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Done")
                        }
                        OutlinedButton(
                            onClick = onExtend,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = DarkCard,
                                contentColor = DarkTextPrimary,
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)),
                        ) {
                            Icon(Icons.Outlined.Alarm, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Extend")
                        }
                    }
                }

                if (!standaloneActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkCard)
                            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                            .clickable(onClick = onOpenStandalone)
                            .padding(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Block, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Block apps while I work",
                                    fontSize = 14.scaledSp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DarkTextPrimary,
                                )
                                Text(
                                    "Run a standalone block alongside this task",
                                    fontSize = 12.scaledSp,
                                    color = DarkTextSecondary,
                                )
                            }
                            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = DarkTextMuted)
                        }
                    }
                }

                if (isFocusing) {
                    OutlinedButton(
                        onClick = onEmergencyOverride,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = DarkCard,
                            contentColor = Color(0xFFEF4444),
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFEF4444).copy(alpha = 0.5f))),
                    ) {
                        Icon(Icons.Outlined.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Emergency Override", fontWeight = FontWeight.SemiBold)
                    }
                }

                if (standaloneActive) {
                    TextButton(
                        onClick = onOpenStandalonePanel,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Block, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Block running · ${settings.standaloneBlockPackages.size} apps · tap to manage",
                            color = Color(0xFFF87171),
                            fontSize = 13.scaledSp,
                        )
                    }
                }
            }
        }

        // Allowed apps footer
        if (isFocusing) {
            item {
                Text(
                    text = if (session?.allowedPackages.isNullOrEmpty()) "Allowed: all apps"
                    else "Allowed: ${session?.allowedPackages?.joinToString()}",
                    modifier = Modifier.padding(horizontal = 24.dp),
                    fontSize = 12.scaledSp,
                    color = DarkTextMuted,
                    lineHeight = 16.scaledSp,
                )
            }
        }
    }
}

@Composable
private fun PomodoroStrip(
    sessionStartedAt: String?,
    workMinutes: Int,
    breakMinutes: Int,
    breakActive: Boolean,
    breakUntilMs: Long,
    onStartBreak: () -> Unit,
    onEndBreak: () -> Unit,
) {
    val now by rememberClock()
    val workMs = workMinutes.coerceAtLeast(1) * 60_000L
    val elapsed = sessionStartedAt?.let {
        runCatching {
            Duration.between(Instant.parse(it), Instant.ofEpochMilli(now)).toMillis()
        }.getOrDefault(0L)
    } ?: 0L
    val workCompleted = elapsed >= workMs
    val breakRemaining = (breakUntilMs - now).coerceAtLeast(0L)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (breakActive) "☕ BREAK" else "🎯 WORK",
                    fontSize = 13.scaledSp,
                    fontWeight = FontWeight.Bold,
                    color = if (breakActive) Color(0xFF10B981) else BrandPrimary,
                )
                Text(
                    if (breakActive) "${breakRemaining.focusDuration()} left"
                    else "${((workMs - elapsed).coerceAtLeast(0L)).focusDuration()} left",
                    fontSize = 13.scaledSp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkTextPrimary,
                )
            }
            Text(
                text = if (breakActive) {
                    "Apps unlocked · blocking resumes in ${breakRemaining.focusDuration()}"
                } else if (workCompleted) {
                    "$breakMinutes min rest available · take a break to unlock apps"
                } else {
                    "${((workMs - elapsed).coerceAtLeast(0L)).focusDuration()} left in work session · $breakMinutes min break follows"
                },
                fontSize = 12.scaledSp,
                color = DarkTextSecondary,
            )
            if (breakActive) {
                OutlinedButton(
                    onClick = onEndBreak,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = DarkCard,
                        contentColor = DarkTextPrimary,
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)),
                ) {
                    Text("End break early")
                }
            } else if (workCompleted) {
                Button(
                    onClick = onStartBreak,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Take $breakMinutes min break")
                }
            }
        }
    }
}

@Composable
private fun FocusPanelSwitcher(activePanel: FocusPanel, onSwitch: (FocusPanel) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        SegmentedButton(
            selected = activePanel == FocusPanel.TASK,
            onClick = { onSwitch(FocusPanel.TASK) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
            colors = SegmentedButtonDefaults.colors(
                activeContainerColor = BrandPrimary,
                activeContentColor = Color.White,
                inactiveContainerColor = DarkCard,
                inactiveContentColor = DarkTextSecondary,
            ),
        ) { Text("Focus Task", fontWeight = FontWeight.SemiBold) }
        SegmentedButton(
            selected = activePanel == FocusPanel.BLOCK,
            onClick = { onSwitch(FocusPanel.BLOCK) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
            colors = SegmentedButtonDefaults.colors(
                activeContainerColor = BrandPrimary,
                activeContentColor = Color.White,
                inactiveContainerColor = DarkCard,
                inactiveContentColor = DarkTextSecondary,
            ),
        ) { Text("Block Active", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun FocusSubscreenTextScale(
    settings: AppSettings,
    screenId: String,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalFocusFlowTextScale provides RouteTextScaleContext.screenScaleFor(
            tabRoute = Routes.FOCUS,
            screenId = screenId,
            settings = settings,
            inheritedScale = LocalFocusFlowTextScale.current,
        ),
        content = content,
    )
}

/**
 * Screenshot 4c: Active standalone block with countdown and blocked-app state
 */
@Composable
private fun StandaloneBlockPanel(
    settings: AppSettings,
    now: Long,
    presets: List<AllowedAppPreset>,
    onAddTime: (Int) -> Unit,
    onQuickPreset: (AllowedAppPreset) -> Unit,
    onEdit: () -> Unit,
) {
    val remaining = (settings.standaloneBlockUntilMs - now).coerceAtLeast(0L)
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val blockAccent = if (isLightTheme) Color(0xFFB91C1C) else Color(0xFFEF4444)
    val blockDetailAccent = if (isLightTheme) Color(0xFF7F1D1D) else Color(0xFFEF4444)
    val timerCardSurface = if (isLightTheme) Color(0xFFFFF7F7) else DarkCard
    val timerCardBorder = if (isLightTheme) {
        Color(0xFFFCA5A5)
    } else {
        Color(0xFFEF4444).copy(alpha = 0.55f)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Block,
                    contentDescription = null,
                    tint = blockAccent,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Apps Blocked",
                fontSize = 22.scaledSp,
                fontWeight = FontWeight.Bold,
                color = DarkTextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Standalone block is running. You can add more apps or extend the time, but cannot stop the block early.",
                fontSize = 14.scaledSp,
                color = DarkTextSecondary,
                lineHeight = 20.scaledSp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(12.dp))

            // Giant Countdown Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(timerCardSurface)
                    .border(1.dp, timerCardBorder, RoundedCornerShape(18.dp))
                    .padding(22.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "BLOCK EXPIRES IN",
                        fontSize = 12.scaledSp,
                        fontWeight = FontWeight.Bold,
                        color = blockAccent,
                        letterSpacing = 1.scaledSp,
                    )
                    Text(
                        text = remaining.focusDuration(),
                        fontSize = 48.scaledSp,
                        fontWeight = FontWeight.Bold,
                        color = blockAccent,
                        letterSpacing = (-1).scaledSp,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = blockDetailAccent,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            "${settings.standaloneBlockPackages.size} app${if (settings.standaloneBlockPackages.size == 1) "" else "s"} blocked · cannot stop early",
                            fontSize = 12.scaledSp,
                            color = blockDetailAccent,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ADD TIME row
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "ADD TIME",
                    fontSize = 12.scaledSp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextMuted,
                    letterSpacing = 0.8.scaledSp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(30, 60, 120, 240).forEach { minutes ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceVariant)
                                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                                .clickable { onAddTime(minutes) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (minutes >= 60) "+${minutes / 60}h" else "+${minutes}m",
                                fontSize = 14.scaledSp,
                                fontWeight = FontWeight.SemiBold,
                                color = BrandPrimary,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Quick presets
            QuickPresetStrip(presets, active = true, onClick = onQuickPreset)

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = DarkCard,
                    contentColor = DarkTextPrimary,
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(DarkBorder),
                ),
            ) {
                Icon(Icons.Outlined.Block, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add More Apps to Block", fontSize = 15.scaledSp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun QuickPresetStrip(
    presets: List<AllowedAppPreset>,
    active: Boolean,
    onClick: (AllowedAppPreset) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "QUICK PRESETS",
                fontSize = 12.scaledSp,
                fontWeight = FontWeight.Bold,
                color = DarkTextMuted,
                letterSpacing = 0.8.scaledSp,
            )
            if (active) {
                Text(
                    "adds apps +1h",
                    fontSize = 11.scaledSp,
                    color = DarkTextMuted,
                )
            }
        }
        if (presets.isEmpty()) {
            Text(
                "No saved presets yet. Use Add apps to create one.",
                fontSize = 13.scaledSp,
                color = DarkTextMuted,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                presets.forEach { preset ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkCard)
                            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                            .clickable { onClick(preset) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Column {
                            Text(
                                preset.name,
                                fontSize = 14.scaledSp,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkTextPrimary,
                            )
                            Text(
                                "${preset.packages.size} app${if (preset.packages.size == 1) "" else "s"} · ${if (active) "add +1h" else "starts 1 hour"}",
                                fontSize = 11.scaledSp,
                                color = DarkTextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun resolveFocusTask(
    tasks: List<Task>,
    session: FocusSession?,
    now: Long,
): Task? {
    if (session != null) return tasks.firstOrNull { it.id == session.taskId }
    return tasks
        .filter { it.status !in setOf("completed", "skipped") && it.hasStarted(now) }
        .sortedWith(
            compareByDescending<Task> { it.isActiveAt(now) }
                .thenByDescending { parseMillis(it.endTime) },
        )
        .firstOrNull()
}

private fun Task.hasStarted(now: Long): Boolean = parseMillis(startTime) <= now

private fun Task.isActiveAt(now: Long): Boolean =
    status !in setOf("completed", "skipped") &&
        parseMillis(startTime) <= now &&
        parseMillis(endTime) > now

private fun Task.remainingMillis(now: Long): Long = parseMillis(endTime) - now

private fun Task.progressNow(now: Long): Float {
    val start = parseMillis(startTime)
    val end = parseMillis(endTime)
    return ((now - start).toFloat() / (end - start).coerceAtLeast(1L)).coerceIn(0f, 1f)
}

private fun Task.remainingLabel(now: Long): String {
    val remaining = remainingMillis(now)
    val timeDisplay = taskTimeDisplay(parseMillis(endTime), now)
    return when {
        timeDisplay.justEnded -> "Just ended"
        timeDisplay.overdueMinutes != null -> "+${timeDisplay.overdueMinutes}m"
        timeDisplay.secondsRemaining != null -> {
            val seconds = timeDisplay.secondsRemaining
            "%d:%02d".format(seconds / 60, seconds % 60)
        }
        else -> remaining.focusDuration()
    }
}

private fun Task.startLabel(): String = formatTimestamp(startTime)

private fun Task.endLabel(): String = formatTimestamp(endTime)

private fun parseMillis(value: String): Long =
    runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)

private fun formatTimestamp(value: String): String =
    runCatching {
        DateTimeFormatter.ofPattern("h:mm a").format(
            Instant.parse(value).atZone(ZoneId.systemDefault()),
        )
    }.getOrDefault(value)

private fun Long.focusDuration(): String {
    val totalSeconds = (this / 1_000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

private fun AppSettings.isStandaloneActive(now: Long): Boolean =
    standaloneBlockActive && standaloneBlockPackages.isNotEmpty() && standaloneBlockUntilMs > now

private fun AppSettings.dailyAllowanceEntries(): List<DailyAllowanceEntry> {
    val raw = dailyAllowanceConfigJson ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val packageName = item.optString("package").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            DailyAllowanceEntry(
                packageName = packageName,
                dailyAllowanceMs = item.optLong("dailyAllowanceMs", 0L),
                mode = item.optString("mode", "time_budget"),
                countPerDay = item.optInt("countPerDay", 1),
                budgetMinutes = item.optInt("budgetMinutes", 1),
                intervalMinutes = item.optInt("intervalMinutes", 5),
                intervalHours = item.optInt("intervalHours", 1),
            )
        }
    }.getOrDefault(emptyList())
}

@Composable
private fun rememberClock(): State<Long> {
    val clock = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock.longValue = System.currentTimeMillis()
            delay(1_000)
        }
    }
    return clock
}
