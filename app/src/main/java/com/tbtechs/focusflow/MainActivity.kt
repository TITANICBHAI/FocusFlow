package com.tbtechs.focusflow

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.rememberNavController
import com.tbtechs.focusflow.data.repository.AlarmCapabilitySnapshotRecord
import com.tbtechs.focusflow.data.repository.SetupPersistenceManager
import com.tbtechs.focusflow.data.repository.StartupLogger
import com.tbtechs.focusflow.data.repository.VpnRepository
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import com.tbtechs.focusflow.enforcement.LauncherActivity
import com.tbtechs.focusflow.enforcement.receivers.NotificationActionReceiver
import com.tbtechs.focusflow.ui.AppBootViewModel
import com.tbtechs.focusflow.ui.FocusSessionViewModel
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.TaskViewModel
import com.tbtechs.focusflow.ui.alwayson.VpnPermissionLostBanner
import com.tbtechs.focusflow.ui.common.AchievementCelebrationModal
import com.tbtechs.focusflow.ui.common.AppErrorEvents
import com.tbtechs.focusflow.ui.common.ErrorAlertBanner
import com.tbtechs.focusflow.ui.common.ErrorBoundary
import com.tbtechs.focusflow.ui.common.InAppNotice
import com.tbtechs.focusflow.ui.common.InAppNoticeStrip
import com.tbtechs.focusflow.ui.common.InAppNoticeTone
import com.tbtechs.focusflow.ui.navigation.FocusFlowNavGraph
import com.tbtechs.focusflow.ui.navigation.Routes
import com.tbtechs.focusflow.ui.navigation.RouteTextScaleContext
import com.tbtechs.focusflow.ui.stats.StatsViewModel
import com.tbtechs.focusflow.ui.support.DiagnosticLogEntry
import com.tbtechs.focusflow.ui.support.DiagnosticLogLevel
import com.tbtechs.focusflow.ui.support.DiagnosticsModal
import com.tbtechs.focusflow.ui.splash.FocusFlowSplashOverlay
import kotlinx.coroutines.launch

private const val STATE_DAY_RATING_REQUEST_NONCE = "focusflow.state.DAY_RATING_REQUEST_NONCE"

/**
 * Normal app activity host. LauncherActivity remains a separate CATEGORY_HOME
 * activity and is intentionally not part of this NavHost.
 */
class MainActivity : ComponentActivity() {
    private val vpnRepository by lazy {
        VpnRepository(applicationContext, AppModule.restoreGate)
    }
    private var requestedRoute by mutableStateOf(Routes.HOME)
    private var focusDayRating by mutableStateOf(false)
    private var dayRatingRequestNonce by mutableStateOf(0)
    private var notificationEventNonce by mutableStateOf(0)
    private var externalBackupImportNonce by mutableStateOf(0)
    private var resumeNonce by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StartupLogger.info("MainActivity", "Main activity created")
        requestedRoute = routeFromIntent(intent)
        focusDayRating = intent?.action == LauncherActivity.ACTION_OPEN_DAY_RATING
        if (focusDayRating) {
            dayRatingRequestNonce = savedInstanceState
                ?.getInt(STATE_DAY_RATING_REQUEST_NONCE)
                ?.takeIf { it > 0 }
                ?: 1
        }
        setTheme(R.style.Theme_FocusFlow)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            FocusFlowRoot(
                requestedRoute = requestedRoute,
                focusDayRating = focusDayRating,
                dayRatingRequestNonce = dayRatingRequestNonce,
                onDayRatingRequestHandled = ::onDayRatingRequestHandled,
                notificationEventNonce = notificationEventNonce,
                externalBackupImportNonce = externalBackupImportNonce,
                resumeNonce = resumeNonce,
                vpnRepository = vpnRepository,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        StartupLogger.info("MainActivity", "Main activity received a new intent")
        setIntent(intent)
        requestedRoute = routeFromIntent(intent)
        focusDayRating = intent.action == LauncherActivity.ACTION_OPEN_DAY_RATING
        if (focusDayRating) {
            dayRatingRequestNonce++
        }
        notificationEventNonce++
    }

    private fun onDayRatingRequestHandled(requestNonce: Int) {
        if (requestNonce != dayRatingRequestNonce) return
        focusDayRating = false
        val currentIntent = intent
        if (currentIntent.action == LauncherActivity.ACTION_OPEN_DAY_RATING) {
            setIntent(Intent(currentIntent).apply { action = null })
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_DAY_RATING_REQUEST_NONCE, dayRatingRequestNonce)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        val setupPersistence = SetupPersistenceManager(applicationContext)
        if (setupPersistence.isUserConsentedBackgroundService() &&
            setupPersistence.isOnboardingComplete()
        ) {
            AppModule.foregroundServiceController.ensureRunning()
        }
        AppModule.requestTaskAlarmReconciliation("activity_start")
    }

    override fun onResume() {
        super.onResume()
        resumeNonce++
        // Also re-check after returning from exact-alarm or notification
        // settings; Android does not broadcast exact-alarm revocation.
        AppModule.requestTaskAlarmReconciliation("activity_resume")
    }

    private fun routeFromIntent(intent: Intent?): String {
        if (intent?.action == LauncherActivity.ACTION_OPEN_DAY_RATING) {
            return Routes.STATS
        }

        val uri = intent?.data
        if (
            uri != null &&
            ExternalBackupIntentPolicy.shouldStage(
                action = intent.action,
                uriScheme = uri.scheme,
                mimeType = intent.type,
                lastPathSegment = uri.lastPathSegment,
            )
        ) {
            BackupImportIntentRelay.stage(uri)
            externalBackupImportNonce++
            return Routes.SETTINGS
        }

        return Routes.fromPath(uri?.path)
    }
}

@Composable
private fun FocusFlowRoot(
    requestedRoute: String,
    focusDayRating: Boolean,
    dayRatingRequestNonce: Int,
    onDayRatingRequestHandled: (Int) -> Unit,
    notificationEventNonce: Int,
    externalBackupImportNonce: Int,
    resumeNonce: Int,
    vpnRepository: VpnRepository,
) {
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    val navController = rememberNavController()
    val settingsViewModel = remember {
        SettingsViewModel(
            AppModule.settingsRepository,
            AppModule.pinManager,
            context,
            AppModule.restoreGate,
        )
    }
    val focusSessionViewModel = remember {
        FocusSessionViewModel(
            focusSessionRepository = AppModule.focusSessionRepository,
            taskRepository = AppModule.taskRepository,
            settingsRepository = AppModule.settingsRepository,
            context = context,
            foregroundServiceController = AppModule.foregroundServiceController,
            taskAlarmReconciler = AppModule.taskAlarmReconciler,
            restoreGate = AppModule.restoreGate,
        )
    }
    val taskViewModel = remember {
        TaskViewModel(
            taskRepository = AppModule.taskRepository,
            alarmRepository = AppModule.alarmRepository,
            taskAlarmReconciler = AppModule.taskAlarmReconciler,
            focusSessionRepository = AppModule.focusSessionRepository,
            foregroundServiceController = AppModule.foregroundServiceController,
            settingsRepository = AppModule.settingsRepository,
            schedulerEngine = AppModule.schedulerEngine,
            beforeTaskDelete = { taskId, pinHash ->
                focusSessionViewModel.stopFocusModeForTaskAwait(taskId, pinHash)
            },
            beforeClearTasks = { pinHash ->
                focusSessionViewModel.stopFocusModeAwait(pinHash)
            },
        )
    }
    val appBootViewModel = remember {
        AppBootViewModel(
            settingsRepository = AppModule.settingsRepository,
            taskRepository = AppModule.taskRepository,
            focusSessionRepository = AppModule.focusSessionRepository,
        )
    }
    val settings by settingsViewModel.settings.collectAsState()
    val privacyAccepted by settingsViewModel.privacyAccepted.collectAsState()
    val onboardingComplete by settingsViewModel.onboardingComplete.collectAsState()
    val isLoading by appBootViewModel.isLoading.collectAsState()
    val isDbReady by appBootViewModel.isDbReady.collectAsState()
    val statsViewModel = remember {
        StatsViewModel(
            analyticsProcessor = AppModule.analyticsProcessor,
            insightEngine = AppModule.insightEngine,
            achievementEngine = AppModule.achievementEngine,
            dayRatingRepository = AppModule.dayRatingRepository,
            findingRepository = AppModule.findingRepository,
            hypothesisRepository = AppModule.behaviouralHypothesisRepository,
            clarifyingQuestionRepository = AppModule.clarifyingQuestionRepository,
            settingsRepository = AppModule.settingsRepository,
        )
    }
    var noticeId by remember { mutableStateOf(0) }
    var inAppNotice by remember { mutableStateOf<InAppNotice?>(null) }

    fun showInAppNotice(
        message: String,
        tone: InAppNoticeTone = InAppNoticeTone.INFO,
        dismissAfterMillis: Long? = 4_500,
    ) {
        noticeId += 1
        inAppNotice = InAppNotice(
            id = noticeId,
            message = message,
            tone = tone,
            dismissAfterMillis = dismissAfterMillis,
        )
    }

    var diagnosticsVisible by remember { mutableStateOf(false) }
    var diagnosticEvents by remember { mutableStateOf(startupDiagnosticEntries()) }
    var alarmCapabilitySnapshots by remember {
        mutableStateOf(AppModule.alarmRepository.capabilitySnapshots())
    }
    var showFullScreenIntentPrompt by remember { mutableStateOf(false) }
    var dismissedAchievementId by remember { mutableStateOf<String?>(null) }
    var handledExternalBackupImportNonce by remember { mutableStateOf(0) }
    val achievementState by statsViewModel.achievementState.collectAsState()
    val newlyEarned = achievementState?.newlyEarnedIds.orEmpty()
        .firstOrNull()
        ?.let { id -> achievementState?.definitions?.firstOrNull { it.id == id } }

    // Set this synchronously after both VMs exist. AppBootViewModel starts its
    // coroutine from init, so assigning it later in LaunchedEffect could miss
    // an active-session recovery on a fast database.
    appBootViewModel.onSessionRecovered = focusSessionViewModel::loadActiveSession

    LaunchedEffect(
        resumeNonce,
        isDbReady,
        privacyAccepted,
        onboardingComplete,
    ) {
        if (
            resumeNonce > 0 &&
            isDbReady &&
            privacyAccepted &&
            onboardingComplete &&
            !diagnosticsVisible &&
            Build.VERSION.SDK_INT >= 34 &&
            !AppModule.alarmRepository.canUseFullScreenIntent() &&
            AppModule.alarmRepository.markFullScreenIntentPromptShownOnce()
        ) {
            showFullScreenIntentPrompt = true
        }
    }

    LaunchedEffect(diagnosticsVisible) {
        if (diagnosticsVisible) {
            diagnosticEvents = startupDiagnosticEntries()
            alarmCapabilitySnapshots = AppModule.alarmRepository.capabilitySnapshots()
        }
    }

    LaunchedEffect(Unit) {
        AppErrorEvents.events.collect {
            diagnosticEvents = startupDiagnosticEntries()
        }
    }

    LaunchedEffect(
        requestedRoute,
        isDbReady,
        privacyAccepted,
        onboardingComplete,
        externalBackupImportNonce,
    ) {
        if (!isDbReady) return@LaunchedEffect
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        val isCurrentlyInHowToUse =
            RouteTextScaleContext.routeBase(currentRoute) == Routes.HOW_TO_USE

        val guardedRoute = when {
            !privacyAccepted && requestedRoute != Routes.PRIVACY_POLICY -> Routes.PRIVACY_POLICY
            privacyAccepted && !onboardingComplete &&
                requestedRoute != Routes.PRIVACY_POLICY &&
                requestedRoute != Routes.ONBOARDING -> Routes.ONBOARDING
            else -> requestedRoute
        }

        if (
            externalBackupImportNonce != handledExternalBackupImportNonce &&
            guardedRoute == Routes.SETTINGS
        ) {
            handledExternalBackupImportNonce = externalBackupImportNonce
            navController.navigate(Routes.SETTINGS) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = false
                }
                launchSingleTop = true
            }
            return@LaunchedEffect
        }

        // When user just completed onboarding and is viewing the How-To-Use onboarding tour,
        // do not yank them away to HOME.
        if (isCurrentlyInHowToUse && onboardingComplete) {
            return@LaunchedEffect
        }

        if (
            RouteTextScaleContext.routeBase(guardedRoute) != Routes.HOME &&
            RouteTextScaleContext.routeBase(currentRoute) !=
            RouteTextScaleContext.routeBase(guardedRoute)
        ) {
            navController.navigate(guardedRoute) {
                launchSingleTop = true
            }
        }
    }

    // NotificationActionReceiver persists actions before launching this
    // activity, so notification actions survive a cold process start.
    LaunchedEffect(isDbReady, notificationEventNonce) {
        if (!isDbReady) return@LaunchedEffect
        val prefs = context.getSharedPreferences(
            AppBlockerAccessibilityService.PREFS_NAME,
            android.content.Context.MODE_PRIVATE,
        )
        val action = prefs.getString(NotificationActionReceiver.PREF_PENDING_ACTION, null)
        val taskId = prefs.getString(NotificationActionReceiver.PREF_PENDING_TASK_ID, null)
        val minutes = prefs.getInt(NotificationActionReceiver.PREF_PENDING_MINUTES, 15)
        val timestamp = prefs.getLong(NotificationActionReceiver.PREF_PENDING_TIME_MS, 0L)
        if (action.isNullOrBlank() || taskId.isNullOrBlank()) {
            return@LaunchedEffect
        }
        val expired = timestamp <= 0L ||
            System.currentTimeMillis() - timestamp > 5 * 60 * 1_000L
        if (expired) {
            prefs.edit()
                .remove(NotificationActionReceiver.PREF_PENDING_ACTION)
                .remove(NotificationActionReceiver.PREF_PENDING_TASK_ID)
                .remove(NotificationActionReceiver.PREF_PENDING_MINUTES)
                .remove(NotificationActionReceiver.PREF_PENDING_TIME_MS)
                .apply()
            Toast.makeText(
                context,
                "That action expired — open the task to try again.",
                Toast.LENGTH_LONG,
            ).show()
            return@LaunchedEffect
        }
        prefs.edit()
            .remove(NotificationActionReceiver.PREF_PENDING_ACTION)
            .remove(NotificationActionReceiver.PREF_PENDING_TASK_ID)
            .remove(NotificationActionReceiver.PREF_PENDING_MINUTES)
            .remove(NotificationActionReceiver.PREF_PENDING_TIME_MS)
            .apply()
        when (action) {
            NotificationActionReceiver.ACTION_COMPLETE -> taskViewModel.completeTask(taskId)
            NotificationActionReceiver.ACTION_EXTEND -> taskViewModel.extendTaskTime(taskId, minutes)
            NotificationActionReceiver.ACTION_SKIP -> taskViewModel.skipTask(taskId)
        }
    }

    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            // The app draws edge-to-edge. Transparent system bars let the
            // active Material theme continue behind the clock/status strip
            // and behind both gesture and 3-button navigation.
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            // Keep the navigation bar's own surface in sync with the theme.
            // Transparent bars do not reliably refresh on 3-button navigation
            // when the user changes Dark Mode while the activity is alive.
            window.navigationBarColor = if (settings.darkModeEnabled) {
                android.graphics.Color.rgb(13, 19, 34)
            } else {
                android.graphics.Color.rgb(240, 242, 255)
            }
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !settings.darkModeEnabled
                isAppearanceLightNavigationBars = !settings.darkModeEnabled
            }
        }
    }

    com.tbtechs.focusflow.ui.theme.FocusFlowTheme(
        darkTheme = settings.darkModeEnabled,
        generalTextScale = settings.generalTextScale,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            ErrorBoundary(screenName = "root") {
                FocusFlowNavGraph(
                    navController = navController,
                    taskViewModel = taskViewModel,
                    settingsViewModel = settingsViewModel,
                    focusSessionViewModel = focusSessionViewModel,
                    appBootViewModel = appBootViewModel,
                    statsViewModel = statsViewModel,
                    vpnRepository = vpnRepository,
                    externalBackupImportNonce = externalBackupImportNonce,
                    onOnboardingTourFinished = {
                        // A normal cold launch starts on Schedule, but the
                        // first post-onboarding handoff intentionally lands on
                        // Defense. FocusFlowNavGraph performs that navigation
                        // immediately after this callback.
                    },
                    focusDayRating = focusDayRating,
                    dayRatingRequestNonce = dayRatingRequestNonce,
                    onDayRatingRequestHandled = onDayRatingRequestHandled,
                )
            }
            FocusFlowSplashOverlay(
                visible = isLoading || !isDbReady,
                modifier = Modifier.fillMaxSize(),
            )

            VpnPermissionLostBanner(vpnRepository)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.BottomCenter),
                contentAlignment = Alignment.BottomCenter,
            ) {
                ErrorAlertBanner(onViewLogs = { diagnosticsVisible = true })
            }

            InAppNoticeStrip(
                notice = inAppNotice,
                onDismiss = { inAppNotice = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 92.dp),
            )

            AchievementCelebrationModal(
                visible = newlyEarned != null && newlyEarned.id != dismissedAchievementId,
                achievement = newlyEarned,
                onDismiss = { dismissedAchievementId = newlyEarned?.id },
            )
        }

        DiagnosticsModal(
            visible = diagnosticsVisible,
            logs = diagnosticEvents,
            alarmCapabilitySnapshots = alarmCapabilitySnapshots,
            onRefresh = {
                diagnosticEvents = startupDiagnosticEntries()
                alarmCapabilitySnapshots = AppModule.alarmRepository.capabilitySnapshots()
            },
            onClearLogs = {
                StartupLogger.clear()
                diagnosticEvents = emptyList()
            },
            onClose = { diagnosticsVisible = false },
        )

        if (showFullScreenIntentPrompt) {
            AlertDialog(
                onDismissRequest = { showFullScreenIntentPrompt = false },
                title = { Text("Allow full-screen task alarms?") },
                text = {
                    Text(
                        "On Android 14 and later, task-end notifications may appear without " +
                            "opening the alarm screen unless full-screen alarm access is allowed.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showFullScreenIntentPrompt = false
                            uiScope.launch {
                                if (!AppModule.alarmRepository.requestFullScreenIntentPermission()) {
                                    AppErrorEvents.report(
                                        tag = "Task alarms",
                                        message = "Could not open full-screen alarm settings. Open Permissions to review this access.",
                                    )
                                }
                            }
                        },
                    ) {
                        Text("Open settings")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFullScreenIntentPrompt = false }) {
                        Text("Not now")
                    }
                },
            )
        }
    }
}

private fun startupDiagnosticEntries(): List<DiagnosticLogEntry> =
    StartupLogger.recent(200).map { entry ->
        DiagnosticLogEntry(
            timestamp = entry.timestamp,
            level = DiagnosticLogLevel.valueOf(entry.level.name),
            tag = entry.tag,
            message = entry.message,
        )
    }
