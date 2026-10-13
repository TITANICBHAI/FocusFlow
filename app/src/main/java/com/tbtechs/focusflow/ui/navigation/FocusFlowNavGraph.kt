package com.tbtechs.focusflow.ui.navigation

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.data.repository.LauncherController
import com.tbtechs.focusflow.data.repository.VpnRepository
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.domain.FocusPinManager
import com.tbtechs.focusflow.ui.AppBootViewModel
import com.tbtechs.focusflow.ui.FocusSessionViewModel
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.TaskViewModel
import com.tbtechs.focusflow.ui.backup.BackupViewModel
import com.tbtechs.focusflow.ui.backup.ImportConfirmScreen
import com.tbtechs.focusflow.ui.active.ActiveScreen
import com.tbtechs.focusflow.ui.alwayson.AlwaysOnScreen
import com.tbtechs.focusflow.ui.common.ErrorBoundary
import com.tbtechs.focusflow.ui.common.SideMenu
import com.tbtechs.focusflow.ui.defense.DefenseScreen
import com.tbtechs.focusflow.ui.defense.StandaloneBlockSetupScreen
import com.tbtechs.focusflow.ui.focus.ActiveBlockScreen
import com.tbtechs.focusflow.ui.focus.FocusScreen
import com.tbtechs.focusflow.ui.home.HomeScreen
import com.tbtechs.focusflow.ui.keyword.KeywordBlockerScreen
import com.tbtechs.focusflow.ui.launcher.LauncherSetupScreen
import com.tbtechs.focusflow.ui.launcher.QuickBlockSheet
import com.tbtechs.focusflow.ui.launcher.VpnBlockListScreen
import com.tbtechs.focusflow.ui.legal.PrivacyPolicyScreen
import com.tbtechs.focusflow.ui.legal.TermsOfServiceScreen
import com.tbtechs.focusflow.ui.onboarding.OnboardingScreen
import com.tbtechs.focusflow.ui.permissions.PermissionsScreen
import com.tbtechs.focusflow.ui.profile.PasswordProtectionScreen
import com.tbtechs.focusflow.ui.profile.UserProfileScreen
import com.tbtechs.focusflow.ui.settings.SettingsHowToUseScreen
import com.tbtechs.focusflow.ui.settings.SettingsScreen
import com.tbtechs.focusflow.ui.settings.TextSizeSettingsScreen
import com.tbtechs.focusflow.ui.stats.ReportScreen
import com.tbtechs.focusflow.ui.stats.ReportsScreen
import com.tbtechs.focusflow.ui.stats.StatsScreen
import com.tbtechs.focusflow.ui.support.ChangelogScreen
import com.tbtechs.focusflow.ui.support.FocusFlowFileGuideScreen
import com.tbtechs.focusflow.ui.support.HowToUseScreen
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowTextScale
import com.tbtechs.focusflow.ui.home.RefBorder
import com.tbtechs.focusflow.ui.home.RefHeader
import com.tbtechs.focusflow.ui.home.RefSecondary
import com.tbtechs.focusflow.ui.home.RefMuted
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import kotlinx.coroutines.launch

@Composable
fun FocusFlowNavGraph(
    navController: NavHostController,
    taskViewModel: TaskViewModel,
    settingsViewModel: SettingsViewModel,
    focusSessionViewModel: FocusSessionViewModel,
    appBootViewModel: AppBootViewModel,
    statsViewModel: com.tbtechs.focusflow.ui.stats.StatsViewModel,
    vpnRepository: VpnRepository,
    externalBackupImportNonce: Int = 0,
    onOnboardingTourFinished: () -> Unit = {},
    focusDayRating: Boolean = false,
    dayRatingRequestNonce: Int = 0,
    onDayRatingRequestHandled: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installedAppsRepository = remember { InstalledAppsRepository(context) }
    val launcherController = remember { LauncherController(context) }
    val focusPinManager = remember { FocusPinManager(context) }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val currentSourceTab = currentBackStackEntry?.arguments
        ?.getString(RouteTextScaleContext.SOURCE_TAB_ARGUMENT)
    val drawerState = androidx.compose.material3.rememberDrawerState(
        androidx.compose.material3.DrawerValue.Closed,
    )
    var pendingQuickBlockPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingQuickBlockAppName by rememberSaveable { mutableStateOf("") }
    val settings by settingsViewModel.settings.collectAsState()
    val backupViewModel: BackupViewModel = viewModel(
        factory = BackupViewModel.factory(settingsViewModel),
    )

    fun navigate(route: String) {
        val destinationBase = RouteTextScaleContext.routeBase(route) ?: route
        val nextSourceTab = RouteTextScaleContext.sourceTabForDestination(
            currentRoute = currentRoute,
            currentSourceTab = currentSourceTab,
            destinationRoute = route,
        )
        val targetRoute = RouteTextScaleContext.routeWithSourceTab(route, nextSourceTab)
        val hasNonContextArguments = route.substringAfter('?', "")
            .split('&')
            .any { argument ->
                argument.isNotBlank() &&
                    argument.substringBefore('=')
                        .substringAfter('?') != RouteTextScaleContext.SOURCE_TAB_ARGUMENT
            }
        val alreadyAtDestination = RouteTextScaleContext.routeBase(currentRoute) == destinationBase &&
            !hasNonContextArguments &&
            currentSourceTab == nextSourceTab
        if (alreadyAtDestination) return

        if (destinationBase == Routes.HOME) {
            // Schedule is also the NavHost start destination. Re-enter it by
            // keeping the root entry and discarding everything above it;
            // restoring saved state here can resurrect the onboarding stack
            // during the first post-onboarding session.
            navController.navigate(destinationBase) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = false
                }
                launchSingleTop = true
            }
        } else if (destinationBase in Routes.tabRoutes) {
            navController.navigate(destinationBase) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        } else {
            navController.navigate(targetRoute) { launchSingleTop = true }
        }
    }

    fun back() {
        if (!navController.popBackStack()) navigate(Routes.HOME)
    }

    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            RouteTextScaleProvider(
                route = currentRoute ?: Routes.HOME,
                sourceTab = currentSourceTab,
                settings = settings,
            ) {
                SideMenu(
                    currentRoute = currentRoute,
                    onNavigate = ::navigate,
                    onClose = { scope.launch { drawerState.close() } },
                )
            }
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.HOME) {
                MainScaffold(currentRoute, ::navigate) {
                    CompositionLocalProvider(
                        LocalFocusFlowTextScale provides
                            (settings.homeTextScale ?: settings.generalTextScale),
                    ) {
                        ScreenBoundary(Routes.HOME) {
                            HomeScreen(
                                taskViewModel = taskViewModel,
                                settingsViewModel = settingsViewModel,
                                focusSessionViewModel = focusSessionViewModel,
                                appBootViewModel = appBootViewModel,
                                onOpenActiveBlocks = { navigate(Routes.ACTIVE) },
                            )
                        }
                    }
                }
            }
            composable(Routes.FOCUS) {
                MainScaffold(currentRoute, ::navigate) {
                    CompositionLocalProvider(
                        LocalFocusFlowTextScale provides
                            (settings.focusTextScale ?: settings.generalTextScale),
                    ) {
                        ScreenBoundary(Routes.FOCUS) {
                            FocusScreen(
                                taskViewModel = taskViewModel,
                                settingsViewModel = settingsViewModel,
                                focusSessionViewModel = focusSessionViewModel,
                                onOpenActiveBlocks = { navigate(Routes.ACTIVE) },
                                onOpenSchedule = { navigate(Routes.HOME) },
                                onOpenPermissions = { navigate(Routes.PERMISSIONS) },
                            )
                        }
                    }
                }
            }
            composable(Routes.STATS) {
                MainScaffold(currentRoute, ::navigate) {
                    CompositionLocalProvider(
                        LocalFocusFlowTextScale provides
                            (settings.statsTextScale ?: settings.generalTextScale),
                    ) {
                        ScreenBoundary(Routes.STATS) {
                            StatsScreen(
                                statsViewModel = statsViewModel,
                                onOpenUsageAccessSettings = {
                                    scope.launch {
                                        AppModule.usageStatsRepository.openUsageAccessSettings()
                                    }
                                },
                                onOpenActiveBlocks = { navigate(Routes.ACTIVE) },
                                onOpenQuickBlock = { packageName ->
                                    if (!packageName.isNullOrBlank()) {
                                        pendingQuickBlockPackage = packageName
                                        pendingQuickBlockAppName = runCatching {
                                            context.packageManager
                                                .getApplicationLabel(
                                                    context.packageManager.getApplicationInfo(packageName, 0),
                                                )
                                                .toString()
                                        }.getOrDefault(packageName)
                                    }
                                },
                                focusDayRating = focusDayRating,
                                dayRatingRequestNonce = dayRatingRequestNonce,
                                onDayRatingRequestHandled = onDayRatingRequestHandled,
                            )
                        }
                    }
                }
            }
            composable(Routes.SETTINGS) {
                MainScaffold(currentRoute, ::navigate) {
                    CompositionLocalProvider(
                        LocalFocusFlowTextScale provides
                            (settings.settingsTextScale ?: settings.generalTextScale),
                    ) {
                        ScreenBoundary(Routes.SETTINGS) {
                            SettingsScreen(
                                settingsViewModel = settingsViewModel,
                                backupViewModel = backupViewModel,
                                taskViewModel = taskViewModel,
                                focusSessionViewModel = focusSessionViewModel,
                                appBootViewModel = appBootViewModel,
                                externalImportEventNonce = externalBackupImportNonce,
                                onOpenActiveBlocks = { navigate(Routes.ACTIVE) },
                                onOpenTextSize = { navigate(Routes.TEXT_SIZE_SETTINGS) },
                                onOpenHowToUse = { navigate(Routes.SETTINGS_HOW_TO_USE) },
                                onOpenFileGuide = { navigate(Routes.FOCUSFLOW_FILE_GUIDE) },
                                onOpenProfile = { navigate(Routes.USER_PROFILE) },
                                onOpenPermissions = { navigate(Routes.PERMISSIONS) },
                                onOpenChangelog = { navigate(Routes.CHANGELOG) },
                                onOpenPrivacyTerms = {
                                    navigate("${Routes.PRIVACY_POLICY}?revisit=true")
                                },
                                onOpenImportConfirmation = {
                                    navigate(Routes.IMPORT_CONFIRM)
                                },
                            )
                        }
                    }
                }
            }
            composable(Routes.IMPORT_CONFIRM) {
                val importState by backupViewModel.importState.collectAsState()
                val pendingImportEnvelope by backupViewModel.pendingImportEnvelope.collectAsState()
                ScreenBoundary(Routes.IMPORT_CONFIRM) {
                    ImportConfirmScreen(
                        importState = importState,
                        pendingEnvelope = pendingImportEnvelope,
                        onConfirm = backupViewModel::confirmImport,
                        onCancelImport = backupViewModel::cancelImport,
                        onResetImport = backupViewModel::resetImport,
                        onDone = { navigate(Routes.SETTINGS) },
                        onCancel = ::back,
                    )
                }
            }
            composable(
                route = RouteTextScaleContext.routePattern(Routes.TEXT_SIZE_SETTINGS),
                arguments = listOf(sourceTabArgument()),
            ) { backStackEntry ->
                RouteTextScaleProvider(
                    route = Routes.TEXT_SIZE_SETTINGS,
                    sourceTab = backStackEntry.arguments
                        ?.getString(RouteTextScaleContext.SOURCE_TAB_ARGUMENT),
                    settings = settings,
                ) {
                    ScreenBoundary(Routes.TEXT_SIZE_SETTINGS) {
                        TextSizeSettingsScreen(
                            settingsViewModel = settingsViewModel,
                            onBack = ::back,
                        )
                    }
                }
            }
            composable(
                route = RouteTextScaleContext.routePattern(Routes.SETTINGS_HOW_TO_USE),
                arguments = listOf(sourceTabArgument()),
            ) { backStackEntry ->
                RouteTextScaleProvider(
                    route = Routes.SETTINGS_HOW_TO_USE,
                    sourceTab = backStackEntry.arguments
                        ?.getString(RouteTextScaleContext.SOURCE_TAB_ARGUMENT),
                    settings = settings,
                ) {
                    ScreenBoundary(Routes.SETTINGS_HOW_TO_USE) {
                        SettingsHowToUseScreen(
                            onBack = ::back,
                            onOpenRoute = ::navigate,
                        )
                    }
                }
            }
            composable(
                route = RouteTextScaleContext.routePattern(Routes.FOCUSFLOW_FILE_GUIDE),
                arguments = listOf(sourceTabArgument()),
            ) { backStackEntry ->
                RouteTextScaleProvider(
                    route = Routes.FOCUSFLOW_FILE_GUIDE,
                    sourceTab = backStackEntry.arguments
                        ?.getString(RouteTextScaleContext.SOURCE_TAB_ARGUMENT),
                    settings = settings,
                ) {
                    ScreenBoundary(Routes.FOCUSFLOW_FILE_GUIDE) {
                        FocusFlowFileGuideScreen(onBack = ::back)
                    }
                }
            }
            composable(Routes.DEFENSE) {
                MainScaffold(currentRoute, ::navigate) {
                    CompositionLocalProvider(
                        LocalFocusFlowTextScale provides
                            (settings.defenseTextScale ?: settings.generalTextScale),
                    ) {
                        ScreenBoundary(Routes.DEFENSE) {
                            DefenseScreen(
                                settingsViewModel = settingsViewModel,
                                isFocusActive = focusSessionViewModel.focusSession.value?.isActive == true,
                                vpnRepository = vpnRepository,
                                onOpenAlwaysOn = { navigate(Routes.ALWAYS_ON) },
                                onOpenKeywordBlocker = { navigate(Routes.KEYWORD_BLOCKER) },
                                onOpenVpnBlockList = { navigate(Routes.VPN_BLOCK_LIST) },
                                onOpenPasswordProtection = { navigate(Routes.PASSWORD_PROTECTION) },
                                onOpenPermissions = { navigate(Routes.PERMISSIONS) },
                                onOpenLauncher = { navigate(Routes.HOME_LAUNCHER_SETUP) },
                                onOpenActiveBlocks = { navigate(Routes.ACTIVE) },
                            )
                        }
                    }
                }
            }
            composable(
                route = RouteTextScaleContext.routePattern(Routes.ACTIVE),
                arguments = listOf(sourceTabArgument()),
            ) { backStackEntry ->
                RouteTextScaleProvider(
                    route = Routes.ACTIVE,
                    sourceTab = backStackEntry.arguments
                        ?.getString(RouteTextScaleContext.SOURCE_TAB_ARGUMENT),
                    settings = settings,
                ) {
                    ScreenBoundary(Routes.ACTIVE) {
                        ActiveScreen(
                            taskViewModel = taskViewModel,
                            settingsViewModel = settingsViewModel,
                            focusSessionViewModel = focusSessionViewModel,
                            vpnRepository = vpnRepository,
                            onBack = ::back,
                            onOpenFocus = { navigate(Routes.FOCUS) },
                            onOpenAlwaysOn = { navigate(Routes.ALWAYS_ON) },
                            onOpenDefense = { navigate(Routes.DEFENSE) },
                            onOpenKeywordBlocker = { navigate(Routes.KEYWORD_BLOCKER) },
                            onOpenVpnBlockList = { navigate(Routes.VPN_BLOCK_LIST) },
                        )
                    }
                }
            }
            composable(
                route = RouteTextScaleContext.routePattern(Routes.ALWAYS_ON),
                arguments = listOf(sourceTabArgument()),
            ) { backStackEntry ->
                RouteTextScaleProvider(
                    route = Routes.ALWAYS_ON,
                    sourceTab = backStackEntry.arguments
                        ?.getString(RouteTextScaleContext.SOURCE_TAB_ARGUMENT),
                    settings = settings,
                ) {
                    ScreenBoundary(Routes.ALWAYS_ON) {
                        AlwaysOnScreen(
                            settingsViewModel = settingsViewModel,
                            focusSessionViewModel = focusSessionViewModel,
                            settingsRepository = AppModule.settingsRepository,
                            vpnRepository = vpnRepository,
                            installedAppsRepository = installedAppsRepository,
                            onBack = ::back,
                        )
                    }
                }
            }
            composable(
                route = RouteTextScaleContext.routePattern(Routes.BLOCK_DEFENSE),
                arguments = listOf(sourceTabArgument()),
            ) { backStackEntry ->
                RouteTextScaleProvider(
                    route = Routes.BLOCK_DEFENSE,
                    sourceTab = backStackEntry.arguments
                        ?.getString(RouteTextScaleContext.SOURCE_TAB_ARGUMENT),
                    settings = settings,
                ) {
                    ScreenBoundary(Routes.BLOCK_DEFENSE) {
                        StandaloneBlockSetupScreen(
                            settingsViewModel = settingsViewModel,
                            initialPackage = pendingQuickBlockPackage,
                            onBack = {
                                pendingQuickBlockPackage = null
                                pendingQuickBlockAppName = ""
                                back()
                            },
                        )
                    }
                }
            }
            composable(
                route = RouteTextScaleContext.routePattern(Routes.CHANGELOG),
                arguments = listOf(sourceTabArgument()),
            ) { backStackEntry ->
                RouteTextScaleProvider(
                    route = Routes.CHANGELOG,
                    sourceTab = backStackEntry.arguments
                        ?.getString(RouteTextScaleContext.SOURCE_TAB_ARGUMENT),
                    settings = settings,
                ) {
                    ScreenBoundary(Routes.CHANGELOG) { ChangelogScreen(onBack = ::back) }
                }
            }
            composable(
                route = RouteTextScaleContext.routePattern(Routes.HOME_LAUNCHER_SETUP),
                arguments = listOf(sourceTabArgument()),
            ) { backStackEntry ->
                RouteTextScaleProvider(
                    route = Routes.HOME_LAUNCHER_SETUP,
                    sourceTab = backStackEntry.arguments
                        ?.getString(RouteTextScaleContext.SOURCE_TAB_ARGUMENT),
                    settings = settings,
                ) {
                    ScreenBoundary(Routes.HOME_LAUNCHER_SETUP) {
                        LauncherSetupScreen(
                            settingsViewModel = settingsViewModel,
                            settingsRepository = AppModule.settingsRepository,
                            installedAppsRepository = installedAppsRepository,
                            launcherController = launcherController,
                            onBack = ::back,
                        )
                    }
                }
            }
            composable(
                route = RouteTextScaleContext.routePattern(
                    "${Routes.HOW_TO_USE}?onboarding={onboarding}",
                ),
                arguments = listOf(
                    navArgument("onboarding") {
                        type = NavType.StringType
                        defaultValue = "false"
                    },
                    sourceTabArgument(),
                ),
            ) { backStackEntry ->
                val onboardingParam = backStackEntry.arguments?.getString("onboarding")
                val isOnboarding = onboardingParam == "true" || onboardingParam == "1"
                RouteTextScaleProvider(
                    route = Routes.HOW_TO_USE,
                    sourceTab = backStackEntry.arguments
                        ?.getString(RouteTextScaleContext.SOURCE_TAB_ARGUMENT),
                    settings = settings,
                ) {
                    ScreenBoundary(Routes.HOW_TO_USE) {
                        HowToUseScreen(
                            isOnboarding = isOnboarding,
                            onBack = {
                                if (isOnboarding) {
                                    navigate(Routes.DEFENSE)
                                } else {
                                    back()
                                }
                            },
                            onGetStarted = {
                                onOnboardingTourFinished()
                                navigate(Routes.DEFENSE)
                            },
                        )
                    }
                }
            }
            composable(
                route = RouteTextScaleContext.routePattern(Routes.KEYWORD_BLOCKER),
                arguments = listOf(sourceTabArgument()),
            ) { backStackEntry ->
                RouteTextScaleProvider(
                    route = Routes.KEYWORD_BLOCKER,
                    sourceTab = backStackEntry.arguments
                        ?.getString(RouteTextScaleContext.SOURCE_TAB_ARGUMENT),
                    settings = settings,
                ) {
                    ScreenBoundary(Routes.KEYWORD_BLOCKER) {
                        KeywordBlockerScreen(
                            settingsViewModel = settingsViewModel,
                            onBack = ::back,
                        )
                    }
                }
            }
            composable(Routes.ONBOARDING) {
                ScreenBoundary(Routes.ONBOARDING) {
                    OnboardingScreen(
                        onFinished = {
                            navController.navigate("${Routes.HOW_TO_USE}?onboarding=true") {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        },
                    )
                }
            }
            composable(
                route = RouteTextScaleContext.routePattern(Routes.PASSWORD_PROTECTION),
                arguments = listOf(sourceTabArgument()),
            ) { backStackEntry ->
                RouteTextScaleProvider(
                    route = Routes.PASSWORD_PROTECTION,
                    sourceTab = backStackEntry.arguments
                        ?.getString(RouteTextScaleContext.SOURCE_TAB_ARGUMENT),
                    settings = settings,
                ) {
                    ScreenBoundary(Routes.PASSWORD_PROTECTION) {
                        PasswordProtectionScreen(
                            settingsViewModel = settingsViewModel,
                            focusPinManager = focusPinManager,
                            onBack = { navigate(Routes.DEFENSE) },
                        )
                    }
                }
            }
            composable(
                route = RouteTextScaleContext.routePattern(Routes.PERMISSIONS),
                arguments = listOf(sourceTabArgument()),
            ) { backStackEntry ->
                RouteTextScaleProvider(
                    route = Routes.PERMISSIONS,
                    sourceTab = backStackEntry.arguments
                        ?.getString(RouteTextScaleContext.SOURCE_TAB_ARGUMENT),
                    settings = settings,
                ) {
                    ScreenBoundary(Routes.PERMISSIONS) {
                        PermissionsScreen(
                            settingsViewModel = settingsViewModel,
                            isFocusActive = focusSessionViewModel.focusSession.value?.isActive == true,
                            onBack = ::back,
                            onConfigureLauncher = { navigate(Routes.HOME_LAUNCHER_SETUP) },
                        )
                    }
                }
            }
            composable(
                route = RouteTextScaleContext.routePattern(
                    "${Routes.PRIVACY_POLICY}?revisit={revisit}",
                ),
                arguments = listOf(
                    navArgument("revisit") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    sourceTabArgument(),
                ),
            ) { backStackEntry ->
                val privacyAccepted by settingsViewModel.privacyAccepted.collectAsState()
                val revisitParam = backStackEntry.arguments?.getString("revisit")
                val isRevisit = when (revisitParam) {
                    "true", "1" -> true
                    "false", "0" -> false
                    else -> privacyAccepted
                }
                RouteTextScaleProvider(
                    route = Routes.PRIVACY_POLICY,
                    sourceTab = backStackEntry.arguments
                        ?.getString(RouteTextScaleContext.SOURCE_TAB_ARGUMENT),
                    settings = settings,
                ) {
                    ScreenBoundary(Routes.PRIVACY_POLICY) {
                        PrivacyPolicyScreen(
                            settingsRepository = AppModule.settingsRepository,
                            isRevisit = isRevisit,
                            onBack = ::back,
                            onAccepted = {
                                val currentDestinationId = navController.currentBackStackEntry
                                    ?.destination
                                    ?.id
                                    ?: navController.graph.findStartDestination().id
                                navController.navigate(Routes.ONBOARDING) {
                                    popUpTo(currentDestinationId) { inclusive = true }
                                }
                            },
                            onDeclineExit = { (context as? Activity)?.finishAndRemoveTask() },
                        )
                    }
                }
            }
            composable(
                route = RouteTextScaleContext.routePattern(Routes.REPORTS),
                arguments = listOf(sourceTabArgument()),
            ) { backStackEntry ->
                RouteTextScaleProvider(
                    route = Routes.REPORTS,
                    sourceTab = backStackEntry.arguments
                        ?.getString(RouteTextScaleContext.SOURCE_TAB_ARGUMENT),
                    settings = settings,
                ) {
                    ScreenBoundary(Routes.REPORTS) {
                        ReportsScreen(
                            taskViewModel = taskViewModel,
                            reportNotesRepository = AppModule.reportNotesRepository,
                            onBack = ::back,
                        )
                    }
                }
            }
            composable(
                route = RouteTextScaleContext.routePattern(Routes.REPORT),
                arguments = listOf(sourceTabArgument()),
            ) { backStackEntry ->
                RouteTextScaleProvider(
                    route = Routes.REPORT,
                    sourceTab = backStackEntry.arguments
                        ?.getString(RouteTextScaleContext.SOURCE_TAB_ARGUMENT),
                    settings = settings,
                ) {
                    ScreenBoundary(Routes.REPORT) {
                        ReportScreen(
                            taskViewModel = taskViewModel,
                            reportNotesRepository = AppModule.reportNotesRepository,
                            onBack = ::back,
                        )
                    }
                }
            }
            composable(
                route = RouteTextScaleContext.routePattern(Routes.TERMS_OF_SERVICE),
                arguments = listOf(sourceTabArgument()),
            ) { backStackEntry ->
                RouteTextScaleProvider(
                    route = Routes.TERMS_OF_SERVICE,
                    sourceTab = backStackEntry.arguments
                        ?.getString(RouteTextScaleContext.SOURCE_TAB_ARGUMENT),
                    settings = settings,
                ) {
                    ScreenBoundary(Routes.TERMS_OF_SERVICE) {
                        TermsOfServiceScreen(onBack = ::back)
                    }
                }
            }
            composable(
                route = RouteTextScaleContext.routePattern(Routes.USER_PROFILE),
                arguments = listOf(sourceTabArgument()),
            ) { backStackEntry ->
                RouteTextScaleProvider(
                    route = Routes.USER_PROFILE,
                    sourceTab = backStackEntry.arguments
                        ?.getString(RouteTextScaleContext.SOURCE_TAB_ARGUMENT),
                    settings = settings,
                ) {
                    ScreenBoundary(Routes.USER_PROFILE) {
                        UserProfileScreen(
                            settingsRepository = AppModule.settingsRepository,
                            isEditMode = true,
                            onBack = ::back,
                            onFinished = ::back,
                            focusSessionRepository = AppModule.focusSessionRepository,
                            settingsViewModel = settingsViewModel,
                        )
                    }
                }
            }
            composable(
                route = RouteTextScaleContext.routePattern(Routes.VPN_BLOCK_LIST),
                arguments = listOf(sourceTabArgument()),
            ) { backStackEntry ->
                RouteTextScaleProvider(
                    route = Routes.VPN_BLOCK_LIST,
                    sourceTab = backStackEntry.arguments
                        ?.getString(RouteTextScaleContext.SOURCE_TAB_ARGUMENT),
                    settings = settings,
                ) {
                    ScreenBoundary(Routes.VPN_BLOCK_LIST) {
                        VpnBlockListScreen(
                            settingsViewModel = settingsViewModel,
                            vpnRepository = vpnRepository,
                            installedAppsRepository = installedAppsRepository,
                            isFocusActive = focusSessionViewModel.focusSession.value?.isActive == true,
                            onBack = ::back,
                        )
                    }
                }
            }
            composable(Routes.NOT_FOUND) {
                ScreenBoundary(Routes.NOT_FOUND) {
                    NotFoundScreen(onBack = ::back)
                }
            }
        }
        pendingQuickBlockPackage?.let { packageName ->
            // This sheet is composed beside the NavHost, outside Stats'
            // destination provider, so explicitly restore its caller scale.
            RouteTextScaleProvider(
                route = Routes.STATS,
                sourceTab = Routes.STATS,
                settings = settings,
            ) {
                QuickBlockSheet(
                    visible = true,
                    packageName = packageName,
                    appName = pendingQuickBlockAppName.ifBlank { packageName },
                    settings = settings,
                    settingsRepository = AppModule.settingsRepository,
                    onClose = {
                        pendingQuickBlockPackage = null
                        pendingQuickBlockAppName = ""
                    },
                    onOpenActive = {
                        pendingQuickBlockPackage = null
                        pendingQuickBlockAppName = ""
                        navigate(Routes.ACTIVE)
                    },
                    onOpenAlwaysOn = {
                        pendingQuickBlockPackage = null
                        pendingQuickBlockAppName = ""
                        navigate(Routes.ALWAYS_ON)
                    },
                )
            }
        }
    }
}

@Composable
private fun RouteTextScaleProvider(
    route: String,
    sourceTab: String?,
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalFocusFlowTextScale provides RouteTextScaleContext.scaleFor(route, sourceTab, settings),
    ) {
        content()
    }
}

private fun sourceTabArgument() = navArgument(RouteTextScaleContext.SOURCE_TAB_ARGUMENT) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

@Composable
private fun ScreenBoundary(
    route: String,
    content: @Composable () -> Unit,
) {
    ErrorBoundary(screenName = route, content = content)
}

@Composable
fun MainScaffold(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val dimensions = LocalFocusFlowDimensions.current
    val tabs = listOf(
        MainTab(Routes.FOCUS, "Focus", MainTabIcon.FOCUS_TIMER),
        MainTab(Routes.HOME, "Schedule", MainTabIcon.SCHEDULE),
        MainTab(Routes.DEFENSE, "Defense", MainTabIcon.DEFENSE),
        MainTab(Routes.STATS, "Stats", MainTabIcon.STATS),
        MainTab(Routes.SETTINGS, "Settings", MainTabIcon.SETTINGS),
    )
    Scaffold(
        containerColor = com.tbtechs.focusflow.ui.theme.DarkBackground,
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RefHeader)
                    .border(1.dp, RefBorder)
                    .navigationBarsPadding()
                    .padding(top = 8.dp, bottom = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            ) {
                tabs.forEach { tab ->
                    val route = tab.route
                    val label = tab.label
                    val isSelected = currentRoute == route
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigate(route) }
                            .padding(vertical = 2.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    ) {
                        MainNavigationTabIcon(
                            icon = tab.icon,
                            label = label,
                            selected = isSelected,
                        )
                        Spacer(Modifier.size(2.dp))
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) BrandPrimary else RefSecondary,
                        )
                    }
                }
            }
        },
    ) { padding ->
        var dragDistance = 0f
        val swipeModifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .pointerInput(currentRoute) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, amount ->
                        dragDistance += amount
                        change.consume()
                    },
                    onDragEnd = {
                        val index = tabs.indexOfFirst { it.route == currentRoute }
                        when {
                            index >= 0 && dragDistance <= -60f && index < tabs.lastIndex ->
                                onNavigate(tabs[index + 1].route)
                            index > 0 && dragDistance >= 60f ->
                                onNavigate(tabs[index - 1].route)
                        }
                        dragDistance = 0f
                    },
                    onDragCancel = { dragDistance = 0f },
                )
            }
        Box(modifier = swipeModifier) {
            content()
        }
    }
}

private enum class MainTabIcon {
    FOCUS_TIMER,
    SCHEDULE,
    DEFENSE,
    STATS,
    SETTINGS,
}

private data class MainTab(
    val route: String,
    val label: String,
    val icon: MainTabIcon,
)

@Composable
private fun MainNavigationTabIcon(
    icon: MainTabIcon,
    label: String,
    selected: Boolean,
) {
    val tint = if (selected) BrandPrimary else RefMuted
    when (icon) {
        MainTabIcon.FOCUS_TIMER -> Icon(
            imageVector = if (selected) FocusTabIcons.Active else FocusTabIcons.Inactive,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        MainTabIcon.DEFENSE -> Icon(
            imageVector = if (selected) DefenseIcons.ActiveFilled else DefenseIcons.Outline,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        MainTabIcon.SCHEDULE -> Icon(
            imageVector = Icons.Outlined.CalendarMonth,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        MainTabIcon.STATS -> Icon(
            imageVector = Icons.Outlined.BarChart,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        MainTabIcon.SETTINGS -> Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun NotFoundScreen(onBack: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text("Screen not found", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        androidx.compose.material3.Text("This FocusFlow destination is not available.")
        androidx.compose.material3.Button(onClick = onBack) { Text("Go back") }
    }
}