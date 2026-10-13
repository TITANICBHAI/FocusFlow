package com.tbtechs.focusflow.ui.active

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.StandaloneBlockConfig
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.data.repository.AllowanceUsage
import com.tbtechs.focusflow.data.repository.InstalledAppInfo
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.data.repository.NetworkBlockSettings
import com.tbtechs.focusflow.data.repository.NetworkBlockStatus
import com.tbtechs.focusflow.data.repository.VpnRepository
import com.tbtechs.focusflow.domain.FocusPinManager
import com.tbtechs.focusflow.ui.FocusSessionViewModel
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.TaskViewModel
import com.tbtechs.focusflow.ui.focus.ActiveStatusIndicator
import com.tbtechs.focusflow.ui.home.FocusFlowInternalHeader
import com.tbtechs.focusflow.ui.common.rememberInstalledApps
import com.tbtechs.focusflow.ui.common.resolve
import com.tbtechs.focusflow.ui.launcher.AppIcon
import com.tbtechs.focusflow.ui.navigation.FocusTabIcons
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.scaledSp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Aggregated Active status screen (Screens 5a and 5b).
 * Provides a live overview of all 7 protection layers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveScreen(
    taskViewModel: TaskViewModel = viewModel(factory = TaskViewModel.Factory),
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    focusSessionViewModel: FocusSessionViewModel = viewModel(factory = FocusSessionViewModel.Factory),
    vpnRepository: VpnRepository? = null,
    onBack: () -> Unit = {},
    onOpenFocus: () -> Unit = {},
    onOpenAlwaysOn: () -> Unit = {},
    onOpenDefense: () -> Unit = {},
    onOpenKeywordBlocker: () -> Unit = {},
    onOpenVpnBlockList: () -> Unit = {},
) {
    val context = LocalContext.current
    val installedAppsRepository = remember { InstalledAppsRepository(context) }
    val resolvedVpnRepo = remember(vpnRepository) {
        vpnRepository ?: VpnRepository(context, com.tbtechs.focusflow.di.AppModule.restoreGate)
    }
    val focusPinManager = remember { FocusPinManager(context) }

    val tasks by taskViewModel.tasks.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()
    val session by focusSessionViewModel.focusSession.collectAsState()
    val allowanceSnapshot by settingsViewModel.allowanceSnapshot.collectAsState()
    val todayFocusMinutes by focusSessionViewModel.todayFocusMinutes.collectAsState()
    val todayOverrideCount by focusSessionViewModel.todayOverrideCount.collectAsState()

    val installedAppsState = rememberInstalledApps(installedAppsRepository)
    var vpnSettings by remember { mutableStateOf<NetworkBlockSettings?>(null) }
    var vpnStatus by remember { mutableStateOf<NetworkBlockStatus?>(null) }
    var initialVpnLoadComplete by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf("") }
    var stopFocusConfirmation by remember { mutableStateOf(false) }
    var clearStandaloneConfirmation by remember { mutableStateOf(false) }
    var defensePinGate by remember { mutableStateOf(false) }
    var focusPinRequired by remember { mutableStateOf(false) }
    var defensePin by remember { mutableStateOf("") }
    var focusPin by remember { mutableStateOf("") }

    LaunchedEffect(resolvedVpnRepo) {
        while (true) {
            val loadedState = withContext(Dispatchers.IO) {
                runCatching { resolvedVpnRepo.getNetworkBlockSettings() }.getOrNull() to
                    runCatching { resolvedVpnRepo.getNetworkBlockStatus() }.getOrNull()
            }
            vpnSettings = loadedState.first
            vpnStatus = loadedState.second
            initialVpnLoadComplete = true
            delay(5_000)
        }
    }

    if (installedAppsState.loading || !initialVpnLoadComplete) {
        ActiveLoadingScreen(
            onBack = onBack,
            loadedAppCount = installedAppsState.apps.size,
        )
        return
    }

    val focusTask = session?.taskId?.let { taskId ->
        tasks.firstOrNull { it.id == taskId }
    }

    val standaloneActive = settings.standaloneBlockActive &&
        settings.standaloneBlockPackages.isNotEmpty() &&
        settings.standaloneBlockUntilMs > System.currentTimeMillis()
    val alwaysOnActive = settings.alwaysBlockEnabled && settings.alwaysBlockPackages.isNotEmpty()
    val allowancePackages = settings.dailyAllowancePackages()

    val vpnPackages = (vpnSettings?.packages ?: emptyList()) + (vpnSettings?.standalonePackages ?: emptyList())
    val appInfoByPackage = remember(
        installedAppsState.apps,
        installedAppsState.loading,
        installedAppsState.error,
        settings.alwaysBlockPackages,
        vpnPackages,
        allowancePackages,
    ) {
        (settings.alwaysBlockPackages + vpnPackages + allowancePackages)
            .distinct()
            .mapNotNull { packageName ->
                installedAppsState.resolve(packageName)?.let { packageName to it }
            }
            .toMap()
    }
    val vpnConfigured = vpnPackages.isNotEmpty() || settings.networkBlockEnabled
    val vpnRunning = vpnStatus?.running == true
    val vpnNeedsAttention = vpnStatus != null && (
        vpnStatus?.failedPackages?.isNotEmpty() == true ||
            vpnStatus?.invalidPackages?.isNotEmpty() == true ||
            vpnStatus?.state in setOf(
                "permission_missing",
                "another_vpn_active",
                "package_registration_failed",
                "startup_failed",
            )
        )

    val activeSchedules = settings.recurringBlockSchedules.filter { it.isActiveNow() }

    val nothingActive = session?.isActive != true &&
        !standaloneActive &&
        !alwaysOnActive &&
        allowancePackages.isEmpty() &&
        settings.blockedWords.isEmpty() &&
        !vpnRunning &&
        activeSchedules.isEmpty()

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            FocusFlowInternalHeader(
                title = "Active",
                subtitle = "Live status of your protections",
                onBack = onBack,
                trailing = {
                    ActiveStatusIndicator(
                        focusSession = session,
                        settings = settings,
                        vpnStatus = vpnStatus,
                        onOpenActiveBlocks = {},
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Top Status Summary Banner
            item {
                ActiveSummaryBanner(nothingActive = nothingActive)
            }

            // 1. Focus Session Card
            item {
                ActiveSectionCard(
                    title = "Focus Session",
                    status = if (session?.isActive == true) "Active" else "Not active",
                    statusColor = if (session?.isActive == true) Color(0xFF10B981) else DarkTextMuted,
                    icon = FocusTabIcons.Active,
                ) {
                    if (session?.isActive == true) {
                        Text(
                            "Task: ${focusTask?.title ?: "Task in progress"}",
                            fontSize = 14.scaledSp,
                            color = DarkTextPrimary,
                            fontWeight = FontWeight.Medium,
                        )
                        focusTask?.let {
                            Text(
                                "Ends at ${it.endTime.formatActiveTime()}",
                                fontSize = 13.scaledSp,
                                color = DarkTextSecondary,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { stopFocusConfirmation = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Outlined.StopCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Stop Focus")
                        }
                    } else {
                        Text(
                            "No task-based focus session is running.",
                            fontSize = 13.scaledSp,
                            color = DarkTextSecondary,
                        )
                    }
                }
            }

            // 2. Standalone Block Card
            item {
                ActiveSectionCard(
                    title = "Standalone Block",
                    status = if (standaloneActive) "Active" else "Not active",
                    statusColor = if (standaloneActive) Color(0xFFEF4444) else DarkTextMuted,
                    icon = Icons.Outlined.Block,
                ) {
                    KeyValueRow(
                        label = "Apps",
                        value = if (settings.standaloneBlockPackages.isEmpty()) "None selected"
                        else "${settings.standaloneBlockPackages.size} blocked",
                    )
                    KeyValueRow(
                        label = "Until",
                        value = if (settings.standaloneBlockUntilMs > System.currentTimeMillis()) {
                            settings.standaloneBlockUntilMs.formatActiveDateTime()
                        } else {
                            "No timer running"
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                    when {
                        standaloneActive -> {
                            Button(
                                onClick = onOpenFocus,
                                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("Add time or apps")
                            }
                        }
                        settings.standaloneBlockPackages.isNotEmpty() -> {
                            OutlinedButton(
                                onClick = {
                                    if (settings.pinProtectionEnabled) {
                                        defensePinGate = true
                                    } else {
                                        clearStandaloneConfirmation = true
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = SolidColor(Color(0xFFEF4444).copy(alpha = 0.5f)),
                                ),
                            ) {
                                Text("Clear saved apps")
                            }
                        }
                    }
                }
            }

            // 3. Always-On Apps Card
            item {
                ActiveSectionCard(
                    title = "Always-On Apps",
                    status = if (alwaysOnActive) "Active" else "Not active",
                    statusColor = if (alwaysOnActive) Color(0xFF10B981) else DarkTextMuted,
                    icon = Icons.Outlined.AllInclusive,
                    expandable = settings.alwaysBlockPackages.isNotEmpty(),
                    expanded = expanded == "always",
                    onToggle = { expanded = if (expanded == "always") "" else "always" },
                ) {
                    KeyValueRow(
                        label = "Apps",
                        value = if (settings.alwaysBlockPackages.isEmpty()) "No always-on apps"
                        else "${settings.alwaysBlockPackages.size} blocked continuously",
                    )
                    if (expanded == "always") {
                        PackageList(
                            packages = settings.alwaysBlockPackages,
                            appInfoByPackage = appInfoByPackage,
                            loading = installedAppsState.loading,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    ManageButton(
                        icon = Icons.Outlined.Settings,
                        label = "Manage Always-On apps",
                        onClick = onOpenAlwaysOn,
                    )
                }
            }

            // 4. Daily Allowance Card
            item {
                ActiveSectionCard(
                    title = "Daily Allowance",
                    status = if (allowancePackages.isEmpty()) "Not configured"
                    else "${allowancePackages.size} apps configured",
                    statusColor = if (allowancePackages.isNotEmpty()) BrandPrimary else DarkTextMuted,
                    icon = Icons.Outlined.AccessTime,
                    expandable = allowancePackages.isNotEmpty(),
                    expanded = expanded == "allowance",
                    onToggle = { expanded = if (expanded == "allowance") "" else "allowance" },
                ) {
                    if (allowancePackages.isEmpty()) {
                        Text(
                            "No per-app daily limits are configured.",
                            fontSize = 13.scaledSp,
                            color = DarkTextSecondary,
                        )
                    } else if (expanded == "allowance") {
                        allowancePackages.forEach { pkg ->
                            AllowanceRow(
                                packageName = pkg,
                                appInfo = appInfoByPackage[pkg],
                                loading = installedAppsState.loading,
                                usage = allowanceSnapshot.usageByPackage[pkg],
                                isActiveSession = allowanceSnapshot.activeSessionPackage == pkg,
                            )
                        }
                    } else {
                        Text(
                            "${allowancePackages.size} apps tracked · tap card to see usage",
                            fontSize = 13.scaledSp,
                            color = DarkTextSecondary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    ManageButton(
                        icon = Icons.Outlined.Settings,
                        label = "Manage daily allowance",
                        onClick = onOpenDefense,
                    )
                }
            }

            // 5. Keyword Blocker Card (Screenshot 5b)
            item {
                ActiveSectionCard(
                    title = "Keyword Blocker",
                    status = if (settings.blockedWords.isEmpty()) "Not active" else "Active",
                    statusColor = if (settings.blockedWords.isNotEmpty()) Color(0xFF10B981) else DarkTextMuted,
                    icon = Icons.Outlined.TextFields,
                    expandable = settings.blockedWords.isNotEmpty(),
                    expanded = expanded == "keywords",
                    onToggle = { expanded = if (expanded == "keywords") "" else "keywords" },
                ) {
                    KeyValueRow(
                        label = "Keywords",
                        value = if (settings.blockedWords.isEmpty()) "No keywords configured"
                        else "${settings.blockedWords.size} active immediately",
                    )
                    if (expanded == "keywords" && settings.blockedWords.isNotEmpty()) {
                        Text(
                            settings.blockedWords.joinToString(", "),
                            fontSize = 13.scaledSp,
                            color = DarkTextPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceVariant)
                                .padding(10.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    ManageButton(
                        icon = Icons.Outlined.Edit,
                        label = "Manage keywords",
                        onClick = onOpenKeywordBlocker,
                    )
                }
            }

            // 6. VPN Blocking Card
            item {
                ActiveSectionCard(
                    title = "VPN Blocking",
                    status = vpnStatusTitle(vpnStatus, vpnConfigured),
                    statusColor = if (vpnNeedsAttention) Color(0xFFEF4444)
                    else if (vpnRunning) Color(0xFF10B981) else DarkTextMuted,
                    icon = Icons.Outlined.Shield,
                    warning = vpnNeedsAttention,
                    expandable = vpnPackages.isNotEmpty(),
                    expanded = expanded == "vpn",
                    onToggle = { expanded = if (expanded == "vpn") "" else "vpn" },
                ) {
                    KeyValueRow(
                        label = "Status",
                        value = vpnStatusTitle(vpnStatus, vpnConfigured),
                    )
                    val desired = vpnPackages.size
                    val applied = if (vpnRunning) desired else 0
                    KeyValueRow(
                        label = "Policy sync",
                        value = "Desired $desired · applied $applied",
                    )
                    if (vpnStatus?.failedPackages?.isNotEmpty() == true) {
                        Text(
                            "${vpnStatus?.failedPackages?.size} apps could not be registered",
                            color = Color(0xFFF87171),
                            fontSize = 12.5.scaledSp,
                        )
                    }
                    if (vpnStatus?.invalidPackages?.isNotEmpty() == true) {
                        Text(
                            "${vpnStatus?.invalidPackages?.size} selected VPN apps are not installed",
                            color = Color(0xFFF87171),
                            fontSize = 12.5.scaledSp,
                        )
                    }
                    if (expanded == "vpn" && vpnPackages.isNotEmpty()) {
                        PackageList(
                            packages = vpnPackages,
                            appInfoByPackage = appInfoByPackage,
                            loading = installedAppsState.loading,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    ManageButton(
                        icon = Icons.Outlined.Settings,
                        label = "Manage VPN blocking",
                        onClick = onOpenVpnBlockList,
                    )
                }
            }

            // 7. Scheduled Blocks Card
            item {
                ActiveSectionCard(
                    title = "Scheduled Blocks",
                    status = when {
                        settings.recurringBlockSchedules.isEmpty() -> "Not configured"
                        activeSchedules.isNotEmpty() -> "${activeSchedules.size} active · ${settings.recurringBlockSchedules.size} configured"
                        else -> "${settings.recurringBlockSchedules.size} configured · none active"
                    },
                    statusColor = if (activeSchedules.isNotEmpty()) Color(0xFF10B981) else DarkTextMuted,
                    icon = Icons.Outlined.Layers,
                    expandable = settings.recurringBlockSchedules.isNotEmpty(),
                    expanded = expanded == "schedules",
                    onToggle = { expanded = if (expanded == "schedules") "" else "schedules" },
                ) {
                    if (settings.recurringBlockSchedules.isEmpty()) {
                        Text(
                            "No recurring scheduled blocks are configured.",
                            fontSize = 13.scaledSp,
                            color = DarkTextSecondary,
                        )
                    } else if (expanded == "schedules") {
                        settings.recurringBlockSchedules.forEach { schedule ->
                            Text(
                                "Scheduled block · ${schedule.packages.size} apps · ${schedule.startHour}:00–${schedule.endHour}:00",
                                fontSize = 13.scaledSp,
                                color = DarkTextPrimary,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    } else {
                        Text(
                            "${settings.recurringBlockSchedules.size} scheduled blocks configured",
                            fontSize = 13.scaledSp,
                            color = DarkTextSecondary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    ManageButton(
                        icon = Icons.Outlined.Settings,
                        label = "Manage scheduled blocks",
                        onClick = onOpenDefense,
                    )
                }
            }

            // 8. TODAY Summary Section (Screenshot 5b footer)
            item {
                ActiveTodayFooter(
                    tasks = tasks,
                    todayFocusMinutes = todayFocusMinutes,
                    todayOverrideCount = todayOverrideCount,
                )
            }
        }
    }

    if (defensePinGate) {
        ActivePinGateDialog(
            title = "Defense Password Required",
            value = defensePin,
            onValueChange = { defensePin = it },
            onDismiss = {
                defensePin = ""
                defensePinGate = false
            },
            onVerified = {
                if (settingsViewModel.verifyPin(defensePin)) {
                    defensePin = ""
                    defensePinGate = false
                    clearStandaloneConfirmation = true
                }
            },
        )
    }

    if (clearStandaloneConfirmation) {
        AlertDialog(
            onDismissRequest = { clearStandaloneConfirmation = false },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Clear standalone apps?", fontWeight = FontWeight.Bold) },
            text = { Text("Remove ${settings.standaloneBlockPackages.size} saved apps from the timed block list?") },
            confirmButton = {
                Button(
                    onClick = {
                        settingsViewModel.setStandaloneBlock(
                            StandaloneBlockConfig(false, emptyList(), 0L),
                        )
                        clearStandaloneConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { clearStandaloneConfirmation = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }

    if (stopFocusConfirmation) {
        AlertDialog(
            onDismissRequest = { stopFocusConfirmation = false },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Stop focus session?", fontWeight = FontWeight.Bold) },
            text = { Text("This ends app blocking for the current task.") },
            confirmButton = {
                Button(
                    onClick = {
                        stopFocusConfirmation = false
                        if (settingsViewModel.isFocusPinSet()) {
                            focusPinRequired = true
                        } else {
                            focusSessionViewModel.stopFocusMode()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Stop") }
            },
            dismissButton = {
                TextButton(onClick = { stopFocusConfirmation = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }

    if (focusPinRequired) {
        ActivePinGateDialog(
            title = "Focus Session Password Required",
            value = focusPin,
            onValueChange = { focusPin = it },
            onDismiss = {
                focusPin = ""
                focusPinRequired = false
            },
            onVerified = {
                if (focusPinManager.verifyPin(focusPin)) {
                    focusSessionViewModel.stopFocusMode(focusPinManager.hash(focusPin))
                    focusPin = ""
                    focusPinRequired = false
                }
            },
        )
    }
}

@Composable
private fun ActiveLoadingScreen(
    onBack: () -> Unit,
    loadedAppCount: Int,
) {
    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            FocusFlowInternalHeader(
                title = "Active",
                subtitle = "Live status of your protections",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(color = BrandPrimary)
            Text(
                text = "Loading active protections…",
                modifier = Modifier.padding(top = 16.dp),
                color = DarkTextPrimary,
                fontSize = 16.scaledSp,
                fontWeight = FontWeight.SemiBold,
            )
            if (loadedAppCount > 0) {
                Text(
                    text = "Checking $loadedAppCount installed apps",
                    modifier = Modifier.padding(top = 6.dp),
                    color = DarkTextSecondary,
                    fontSize = 13.scaledSp,
                )
            }
        }
    }
}

@Composable
private fun ActiveSummaryBanner(nothingActive: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (nothingActive) DarkCard
                else BrandPrimary.copy(alpha = 0.12f),
            )
            .border(
                1.dp,
                if (nothingActive) DarkBorder else BrandPrimary.copy(alpha = 0.35f),
                RoundedCornerShape(10.dp),
            )
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (nothingActive) DarkSurfaceVariant
                        else BrandPrimary.copy(alpha = 0.2f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (nothingActive) Icons.Outlined.CheckCircle else Icons.Outlined.Key,
                    contentDescription = null,
                    tint = if (nothingActive) Color(0xFF10B981) else BrandPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
                    Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    if (nothingActive) "Nothing blocking right now" else "Protection is active",
                    fontSize = 15.scaledSp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
                )
                Text(
                    if (nothingActive) "Start Focus or configure a protection layer in Defense."
                    else "Protection is active, but this screen remains interactive and updates automatically.",
                    fontSize = 11.scaledSp,
                    color = DarkTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ActiveSectionCard(
    title: String,
    status: String,
    statusColor: Color,
    icon: ImageVector,
    warning: Boolean = false,
    expandable: Boolean = false,
    expanded: Boolean = false,
    onToggle: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCard)
            .border(
                1.dp,
                if (warning) Color(0xFFEF4444).copy(alpha = 0.45f) else DarkBorder,
                RoundedCornerShape(10.dp),
            )
            .then(if (expandable) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = BrandPrimary,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    fontSize = 15.scaledSp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (warning) {
                    Icon(
                        Icons.Outlined.Warning,
                        contentDescription = "Attention required",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp).padding(end = 4.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        status,
                        fontSize = 11.scaledSp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor,
                    )
                }
                if (expandable) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = DarkTextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(
         modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 11.scaledSp, color = DarkTextSecondary)
        Text(value, fontSize = 11.scaledSp, fontWeight = FontWeight.Medium, color = DarkTextPrimary)
    }
}

@Composable
private fun ManageButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = BrandPrimary,
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = SolidColor(BrandPrimary.copy(alpha = 0.35f)),
        ),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = BrandPrimary)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 11.scaledSp, fontWeight = FontWeight.Medium, color = BrandPrimary)
    }
}

@Composable
private fun PackageList(
    packages: List<String>,
    appInfoByPackage: Map<String, InstalledAppInfo>,
    loading: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(DarkSurfaceVariant)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        packages.forEach { pkg ->
            val appInfo = appInfoByPackage[pkg]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppIcon(appInfo?.icon, size = 22.dp)
                Column {
                    Text(
                        appInfo?.appName ?: if (loading) "Loading app details…" else "App details unavailable",
                        fontSize = 11.scaledSp,
                        color = DarkTextPrimary,
                    )
                    if (appInfo == null || appInfo.isInstalled.not()) {
                        Text(pkg, fontSize = 9.scaledSp, color = DarkTextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun AllowanceRow(
    packageName: String,
    appInfo: InstalledAppInfo?,
    loading: Boolean,
    usage: AllowanceUsage?,
    isActiveSession: Boolean,
) {
    val usageLabel = when (usage?.mode) {
        "count" -> "used ${usage.count} opens today"
        "interval" -> "used ${usage.usedMs / 60_000L} min in window"
        "time_budget" -> "used ${usage.usedMs / 60_000L} min today"
        else -> "no usage recorded"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppIcon(appInfo?.icon, size = 22.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                appInfo?.appName ?: if (loading) "Loading app details…" else "App details unavailable",
                fontSize = 11.scaledSp,
                fontWeight = FontWeight.Medium,
                color = DarkTextPrimary,
            )
            if (appInfo == null || appInfo.isInstalled.not()) {
                Text(packageName, fontSize = 9.scaledSp, color = DarkTextMuted)
            }
        }
        Text(
            "$usageLabel${if (isActiveSession) " · in use" else ""}",
            fontSize = 11.scaledSp,
            color = if (isActiveSession) Color(0xFF10B981) else DarkTextSecondary,
        )
    }
}

@Composable
private fun ActiveTodayFooter(
    tasks: List<Task>,
    todayFocusMinutes: Int,
    todayOverrideCount: Int,
) {
    val today = LocalDate.now()
    val todayTasks = tasks.count {
        runCatching {
            Instant.parse(it.startTime).atZone(ZoneId.systemDefault()).toLocalDate() == today
        }.getOrDefault(false)
    }
    val completedCount = tasks.count {
        it.status == "completed" && runCatching {
            Instant.parse(it.startTime).atZone(ZoneId.systemDefault()).toLocalDate() == today
        }.getOrDefault(false)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "TODAY",
                fontSize = 11.5.scaledSp,
                fontWeight = FontWeight.Bold,
                color = DarkTextMuted,
                letterSpacing = 0.8.scaledSp,
            )
            Text(
                "$completedCount/$todayTasks tasks · ${todayFocusMinutes}m focus · $todayOverrideCount blocked attempts",
                fontSize = 14.scaledSp,
                fontWeight = FontWeight.Medium,
                color = DarkTextPrimary,
            )
        }
    }
}

@Composable
private fun ActivePinGateDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        titleContentColor = DarkTextPrimary,
        textContentColor = DarkTextSecondary,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Password", color = DarkTextMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DarkTextPrimary,
                    unfocusedTextColor = DarkTextPrimary,
                    focusedBorderColor = BrandPrimary,
                    unfocusedBorderColor = DarkBorder,
                ),
            )
        },
        confirmButton = {
            Button(
                onClick = onVerified,
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                shape = RoundedCornerShape(8.dp),
            ) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DarkTextSecondary)
            }
        },
    )
}

private fun AppSettings.dailyAllowancePackages(): List<String> = runCatching {
    val entries = JSONArray(dailyAllowanceConfigJson ?: "[]")
    (0 until entries.length()).mapNotNull {
        entries.optJSONObject(it)?.optString("package")?.takeIf(String::isNotBlank)
    }
}.getOrDefault(emptyList())

private fun com.tbtechs.focusflow.data.model.RecurringBlockSchedule.isActiveNow(): Boolean {
    if (!enabled) return false
    val now = java.time.ZonedDateTime.now()
    val minute = now.hour * 60 + now.minute
    val start = startHour * 60
    val end = endHour * 60
    val day = now.dayOfWeek.value % 7
    return day in daysOfWeek && if (start <= end) {
        minute in start until end
    } else {
        minute >= start || minute < end
    }
}

private fun vpnStatusTitle(status: NetworkBlockStatus?, configured: Boolean): String {
    if (
        status != null &&
        status.invalidPackages.isNotEmpty() &&
        status.state !in setOf(
            "permission_missing",
            "another_vpn_active",
            "package_registration_failed",
            "startup_failed",
        )
    ) {
        return "Some VPN targets are unavailable"
    }
    return when (status?.state) {
        "starting" -> "Starting"
        "running" -> if (status.running) "Running normally" else "Recovery pending"
        "permission_missing" -> "Permission required"
        "another_vpn_active" -> "Another VPN is active"
        "package_registration_failed" -> "Registration issue"
        "startup_failed" -> "Startup failed"
        "disabled" -> "Disabled"
        "stopped" -> if (configured) "Configured but stopped" else "No VPN apps configured"
        else -> if (configured) "VPN status unavailable" else "No VPN apps configured"
    }
}

private fun String.formatActiveTime(): String = runCatching {
    val instant = Instant.parse(this)
    val formatter = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
    formatter.format(instant)
}.getOrDefault(this)

private fun Long.formatActiveDateTime(): String = runCatching {
    val instant = Instant.ofEpochMilli(this)
    val formatter = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
    formatter.format(instant)
}.getOrDefault(this.toString())
