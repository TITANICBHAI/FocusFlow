package com.tbtechs.focusflow.ui.navigation

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.data.repository.LauncherController
import com.tbtechs.focusflow.data.repository.VpnRepository
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.domain.FocusPinManager
import com.tbtechs.focusflow.ui.AppBootViewModel
import com.tbtechs.focusflow.ui.FocusSessionViewModel
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.TaskViewModel
import com.tbtechs.focusflow.ui.alwayson.AlwaysOnScreen
import com.tbtechs.focusflow.ui.backup.BackupCoordinator
import com.tbtechs.focusflow.ui.common.ErrorBoundary
import com.tbtechs.focusflow.ui.common.SideMenu
import com.tbtechs.focusflow.ui.defense.DefenseScreen
import com.tbtechs.focusflow.ui.defense.StandaloneBlockSetupScreen
import com.tbtechs.focusflow.ui.focus.ActiveBlockScreen
import com.tbtechs.focusflow.ui.focus.FocusScreen
import com.tbtechs.focusflow.ui.home.HomeScreen
import com.tbtechs.focusflow.ui.keyword.KeywordBlockerScreen
import com.tbtechs.focusflow.ui.launcher.LauncherSetupScreen
import com.tbtechs.focusflow.ui.launcher.VpnBlockListScreen
import com.tbtechs.focusflow.ui.legal.PrivacyPolicyScreen
import com.tbtechs.focusflow.ui.legal.TermsOfServiceScreen
import com.tbtechs.focusflow.ui.onboarding.OnboardingScreen
import com.tbtechs.focusflow.ui.permissions.PermissionsScreen
import com.tbtechs.focusflow.ui.profile.PasswordProtectionScreen
import com.tbtechs.focusflow.ui.profile.UserProfileScreen
import com.tbtechs.focusflow.ui.settings.SettingsScreen
import com.tbtechs.focusflow.ui.stats.ReportScreen
import com.tbtechs.focusflow.ui.stats.StatsInsightsExperience
import com.tbtechs.focusflow.ui.support.ChangelogScreen
import com.tbtechs.focusflow.ui.support.HowToUseScreen
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
    backupCoordinator: BackupCoordinator? = null,
    onExportBackup: () -> Unit = {},
    onImportBackup: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installedAppsRepository = remember { InstalledAppsRepository(context) }
    val launcherController = remember { LauncherController(context) }
    val focusPinManager = remember { FocusPinManager(context) }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val drawerState = androidx.compose.material3.rememberDrawerState(
        androidx.compose.material3.DrawerValue.Closed,
    )

    fun navigate(route: String) {
        if (route == currentRoute) return
        if (route in Routes.tabRoutes) {
            navController.navigate(route) {
                popUpTo(Routes.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        } else {
            navController.navigate(route) { launchSingleTop = true }
        }
    }

    fun back() {
        if (!navController.popBackStack()) navigate(Routes.HOME)
    }

    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SideMenu(
                currentRoute = currentRoute,
                onNavigate = ::navigate,
                onClose = { scope.launch { drawerState.close() } },
            )
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.HOME) {
                MainScaffold(currentRoute, ::navigate) {
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
            composable(Routes.FOCUS) {
                MainScaffold(currentRoute, ::navigate) {
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
            composable(Routes.STATS) {
                MainScaffold(currentRoute, ::navigate) {
                    ScreenBoundary(Routes.STATS) {
                        StatsInsightsExperience(
                            statsViewModel = statsViewModel,
                            onOpenUsageAccessSettings = {
                                scope.launch {
                                    AppModule.usageStatsRepository.openUsageAccessSettings()
                                }
                            },
                        )
                    }
                }
            }
            composable(Routes.SETTINGS) {
                MainScaffold(currentRoute, ::navigate) {
                    ScreenBoundary(Routes.SETTINGS) {
                        SettingsScreen(
                            settingsViewModel = settingsViewModel,
                            taskViewModel = taskViewModel,
                            focusSessionViewModel = focusSessionViewModel,
                            appBootViewModel = appBootViewModel,
                            onExportBackup = backupCoordinator?.let { onExportBackup },
                            onImportBackup = backupCoordinator?.let { onImportBackup },
                            onOpenProfile = { navigate(Routes.USER_PROFILE) },
                            onOpenPermissions = { navigate(Routes.PERMISSIONS) },
                            onOpenStats = { navigate(Routes.STATS) },
                            onOpenChangelog = { navigate(Routes.CHANGELOG) },
                            onOpenPrivacyTerms = { navigate(Routes.PRIVACY_POLICY) },
                        )
                    }
                }
            }
            composable(Routes.DEFENSE) {
                MainScaffold(currentRoute, ::navigate) {
                    ScreenBoundary(Routes.DEFENSE) {
                        DefenseScreen(
                            settingsViewModel = settingsViewModel,
                            onOpenAlwaysOn = { navigate(Routes.ALWAYS_ON) },
                            onOpenKeywordBlocker = { navigate(Routes.KEYWORD_BLOCKER) },
                            onOpenVpnBlockList = { navigate(Routes.VPN_BLOCK_LIST) },
                            onOpenPasswordProtection = { navigate(Routes.PASSWORD_PROTECTION) },
                            onOpenPermissions = { navigate(Routes.PERMISSIONS) },
                            onOpenHowToUse = { navigate(Routes.HOW_TO_USE) },
                            onOpenLauncher = { navigate(Routes.HOME_LAUNCHER_SETUP) },
                        )
                    }
                }
            }
            composable(Routes.ACTIVE) {
                ScreenBoundary(Routes.ACTIVE) {
                    ActiveBlockScreen(
                        taskViewModel = taskViewModel,
                        settingsViewModel = settingsViewModel,
                        focusSessionViewModel = focusSessionViewModel,
                        onBack = ::back,
                        onOpenFocus = { navigate(Routes.FOCUS) },
                        onOpenAlwaysOn = { navigate(Routes.ALWAYS_ON) },
                        onOpenDefense = { navigate(Routes.DEFENSE) },
                        onOpenKeywordBlocker = { navigate(Routes.KEYWORD_BLOCKER) },
                        onOpenVpnBlockList = { navigate(Routes.VPN_BLOCK_LIST) },
                    )
                }
            }
            composable(Routes.ALWAYS_ON) {
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
            composable(Routes.BLOCK_DEFENSE) {
                ScreenBoundary(Routes.BLOCK_DEFENSE) {
                    StandaloneBlockSetupScreen(
                        settingsViewModel = settingsViewModel,
                        onBack = ::back,
                    )
                }
            }
            composable(Routes.CHANGELOG) {
                ScreenBoundary(Routes.CHANGELOG) { ChangelogScreen(onBack = ::back) }
            }
            composable(Routes.HOME_LAUNCHER_SETUP) {
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
            composable(Routes.HOW_TO_USE) {
                ScreenBoundary(Routes.HOW_TO_USE) {
                    HowToUseScreen(
                        onBack = ::back,
                        onGetStarted = { navigate(Routes.FOCUS) },
                    )
                }
            }
            composable(Routes.KEYWORD_BLOCKER) {
                ScreenBoundary(Routes.KEYWORD_BLOCKER) {
                    KeywordBlockerScreen(
                        settingsViewModel = settingsViewModel,
                        onBack = ::back,
                    )
                }
            }
            composable(Routes.ONBOARDING) {
                ScreenBoundary(Routes.ONBOARDING) {
                    OnboardingScreen(
                        settingsViewModel = settingsViewModel,
                        onFinished = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        },
                    )
                }
            }
            composable(Routes.PASSWORD_PROTECTION) {
                ScreenBoundary(Routes.PASSWORD_PROTECTION) {
                    PasswordProtectionScreen(
                        settingsViewModel = settingsViewModel,
                        focusPinManager = focusPinManager,
                        onBack = { navigate(Routes.DEFENSE) },
                    )
                }
            }
            composable(Routes.PERMISSIONS) {
                ScreenBoundary(Routes.PERMISSIONS) {
                    PermissionsScreen(
                        settingsViewModel = settingsViewModel,
                        isFocusActive = focusSessionViewModel.focusSession.value?.isActive == true,
                        onBack = ::back,
                        onConfigureLauncher = { navigate(Routes.HOME_LAUNCHER_SETUP) },
                    )
                }
            }
            composable(Routes.PRIVACY_POLICY) {
                ScreenBoundary(Routes.PRIVACY_POLICY) {
                    PrivacyPolicyScreen(
                        settingsRepository = AppModule.settingsRepository,
                        isRevisit = true,
                        onBack = ::back,
                        onAccepted = ::back,
                        onDeclineExit = { (context as? Activity)?.finishAndRemoveTask() },
                    )
                }
            }
            composable(Routes.REPORTS) {
                ScreenBoundary(Routes.REPORTS) {
                    ReportScreen(
                        taskViewModel = taskViewModel,
                        settingsRepository = AppModule.settingsRepository,
                        onBack = ::back,
                    )
                }
            }
            composable(Routes.REPORT) {
                ScreenBoundary(Routes.REPORT) {
                    ReportScreen(
                        taskViewModel = taskViewModel,
                        settingsRepository = AppModule.settingsRepository,
                        onBack = ::back,
                    )
                }
            }
            composable(Routes.TERMS_OF_SERVICE) {
                ScreenBoundary(Routes.TERMS_OF_SERVICE) {
                    TermsOfServiceScreen(onBack = ::back)
                }
            }
            composable(Routes.USER_PROFILE) {
                ScreenBoundary(Routes.USER_PROFILE) {
                    UserProfileScreen(
                        settingsRepository = AppModule.settingsRepository,
                        isEditMode = true,
                        onBack = ::back,
                        onFinished = ::back,
                        onImportBackup = backupCoordinator?.let {
                            { onImportBackup(false) }
                        },
                        focusSessionRepository = AppModule.focusSessionRepository,
                        settingsViewModel = settingsViewModel,
                    )
                }
            }
            composable(Routes.VPN_BLOCK_LIST) {
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
            composable(Routes.NOT_FOUND) {
                ScreenBoundary(Routes.NOT_FOUND) {
                    NotFoundScreen(onBack = ::back)
                }
            }
        }
    }
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
    val tabs = listOf(
        Triple(Routes.HOME, "Home", Icons.Outlined.Home),
        Triple(Routes.FOCUS, "Focus", Icons.Outlined.CalendarMonth),
        Triple(Routes.STATS, "Stats", Icons.Outlined.Analytics),
        Triple(Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
        Triple(Routes.DEFENSE, "Defense", Icons.Outlined.Shield),
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        selected = currentRoute == route,
                        onClick = { onNavigate(route) },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            content()
        }
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