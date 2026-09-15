package com.tbtechs.focusflow

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.tbtechs.focusflow.data.repository.NetworkBlockSettings
import com.tbtechs.focusflow.data.repository.VpnRepository
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.ui.AppBootViewModel
import com.tbtechs.focusflow.ui.FocusSessionViewModel
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.TaskViewModel
import com.tbtechs.focusflow.ui.alwayson.VpnPermissionLostBanner
import com.tbtechs.focusflow.ui.backup.BackupCoordinator
import com.tbtechs.focusflow.ui.common.AchievementCelebrationModal
import com.tbtechs.focusflow.ui.common.AppErrorEvents
import com.tbtechs.focusflow.ui.common.ErrorAlertBanner
import com.tbtechs.focusflow.ui.common.ErrorBoundary
import com.tbtechs.focusflow.ui.navigation.FocusFlowNavGraph
import com.tbtechs.focusflow.ui.navigation.Routes
import com.tbtechs.focusflow.ui.stats.StatsViewModel
import com.tbtechs.focusflow.ui.support.DiagnosticLogEntry
import com.tbtechs.focusflow.ui.support.DiagnosticLogLevel
import com.tbtechs.focusflow.ui.support.DiagnosticsModal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Normal app activity host. LauncherActivity remains a separate CATEGORY_HOME
 * activity and is intentionally not part of this NavHost.
 */
class MainActivity : ComponentActivity() {
    private val vpnRepository by lazy { VpnRepository(applicationContext) }
    private var requestedRoute by mutableStateOf(Routes.HOME)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedRoute = routeFromIntent(intent)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            FocusFlowRoot(
                requestedRoute = requestedRoute,
                vpnRepository = vpnRepository,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedRoute = routeFromIntent(intent)
    }

    private fun routeFromIntent(intent: Intent?): String =
        Routes.fromPath(intent?.data?.path)
}

@Composable
private fun FocusFlowRoot(
    requestedRoute: String,
    vpnRepository: VpnRepository,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val taskViewModel = remember { TaskViewModel(AppModule.taskRepository) }
    val settingsViewModel = remember {
        SettingsViewModel(AppModule.settingsRepository, AppModule.pinManager, context)
    }
    val settings by settingsViewModel.settings.collectAsState()
    val focusSessionViewModel = remember {
        FocusSessionViewModel(
            focusSessionRepository = AppModule.focusSessionRepository,
            taskRepository = AppModule.taskRepository,
            settingsRepository = AppModule.settingsRepository,
            context = context,
        )
    }
    val appBootViewModel = remember {
        AppBootViewModel(
            settingsRepository = AppModule.settingsRepository,
            taskRepository = AppModule.taskRepository,
            focusSessionRepository = AppModule.focusSessionRepository,
        )
    }
    val statsViewModel = remember {
        StatsViewModel(
            analyticsProcessor = AppModule.analyticsProcessor,
            insightEngine = AppModule.insightEngine,
            achievementEngine = AppModule.achievementEngine,
        )
    }
    val backupCoordinator = remember {
        BackupCoordinator(
            context = context,
            taskRepository = AppModule.taskRepository,
            focusSessionRepository = AppModule.focusSessionRepository,
            settingsRepository = AppModule.settingsRepository,
            settingsViewModel = settingsViewModel,
        )
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var replaceTasksOnImport by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        result.data?.data?.let { destination ->
            scope.launch {
                val outcome = backupCoordinator.export(settings, destination)
                Toast.makeText(
                    context,
                    if (outcome.ok) "Backup exported." else "Backup export failed: ${outcome.error}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        result.data?.data?.let { source ->
            scope.launch {
                val outcome = backupCoordinator.import(
                    source = source,
                    replaceTasks = replaceTasksOnImport,
                    currentSettings = settings,
                    currentFocusActive = focusSessionViewModel.focusSession.value?.isActive == true,
                )
                val message = when (outcome) {
                    is com.tbtechs.focusflow.data.repository.RestoreResult.Success ->
                        "Backup restored: ${outcome.summary.tasksImported} tasks imported."
                    is com.tbtechs.focusflow.data.repository.RestoreResult.Error ->
                        outcome.message
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }
    var networkSettings by remember { mutableStateOf<NetworkBlockSettings?>(null) }
    var diagnosticEvents by remember { mutableStateOf(AppErrorEvents.snapshot()) }
    var diagnosticsVisible by remember { mutableStateOf(false) }
    var dismissedAchievementId by remember { mutableStateOf<String?>(null) }
    val achievementState by statsViewModel.achievementState.collectAsState()
    val newlyEarned = achievementState?.newlyEarnedIds.orEmpty()
        .firstOrNull()
        ?.let { id -> achievementState?.definitions?.firstOrNull { it.id == id } }

    // Set this synchronously after both VMs exist. AppBootViewModel starts its
    // coroutine from init, so assigning it later in LaunchedEffect could miss
    // an active-session recovery on a fast database.
    appBootViewModel.onSessionRecovered = focusSessionViewModel::loadActiveSession

    LaunchedEffect(Unit) {
        AppErrorEvents.events.collect {
            diagnosticEvents = AppErrorEvents.snapshot()
        }
    }

    LaunchedEffect(requestedRoute) {
        if (requestedRoute != Routes.HOME &&
            navController.currentBackStackEntry?.destination?.route != requestedRoute
        ) {
            navController.navigate(requestedRoute) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(vpnRepository) {
        while (true) {
            networkSettings = runCatching {
                vpnRepository.getNetworkBlockSettings()
            }.getOrNull()
            delay(1_500)
        }
    }

    MaterialTheme(
        colorScheme = if (settings.darkModeEnabled) darkColorScheme() else lightColorScheme(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ErrorBoundary(screenName = "root") {
                FocusFlowNavGraph(
                    navController = navController,
                    taskViewModel = taskViewModel,
                    settingsViewModel = settingsViewModel,
                    focusSessionViewModel = focusSessionViewModel,
                    appBootViewModel = appBootViewModel,
                    statsViewModel = statsViewModel,
                    vpnRepository = vpnRepository,
                    backupCoordinator = backupCoordinator,
                    onExportBackup = {
                        exportLauncher.launch(backupCoordinator.createExportIntent())
                    },
                    onImportBackup = { replace ->
                        replaceTasksOnImport = replace
                        importLauncher.launch(backupCoordinator.createImportIntent())
                    },
                )
            }

            networkSettings?.let { policy ->
                VpnPermissionLostBanner(
                    vpnBlockEnabled = policy.enabled && policy.vpn,
                    vpnPackages = (policy.packages + policy.standalonePackages).distinct(),
                    vpnRepository = vpnRepository,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.BottomCenter),
                contentAlignment = Alignment.BottomCenter,
            ) {
                ErrorAlertBanner(onViewLogs = { diagnosticsVisible = true })
            }

            AchievementCelebrationModal(
                visible = newlyEarned != null && newlyEarned.id != dismissedAchievementId,
                achievement = newlyEarned,
                onDismiss = { dismissedAchievementId = newlyEarned?.id },
            )
        }

        DiagnosticsModal(
            visible = diagnosticsVisible,
            logs = diagnosticEvents.map { event ->
                DiagnosticLogEntry(
                    timestamp = event.timestampMillis.toString(),
                    level = DiagnosticLogLevel.ERROR,
                    tag = event.tag,
                    message = event.message,
                )
            },
            onClose = { diagnosticsVisible = false },
        )
    }
}
