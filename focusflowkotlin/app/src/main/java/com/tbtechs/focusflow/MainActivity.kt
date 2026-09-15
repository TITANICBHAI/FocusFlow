package com.tbtechs.focusflow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.tbtechs.focusflow.data.repository.NetworkBlockSettings
import com.tbtechs.focusflow.data.repository.VpnRepository
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.ui.alwayson.VpnPermissionLostBanner
import com.tbtechs.focusflow.ui.profile.PasswordProtectionScreen
import com.tbtechs.focusflow.ui.profile.UserProfileScreen
import com.tbtechs.focusflow.domain.FocusPinManager
import com.tbtechs.focusflow.ui.SettingsViewModel
import kotlinx.coroutines.delay

/**
 * Native Activity host for the pure-Kotlin migration.
 *
 * The screen families and NavHost are the next UI migration layer. Keeping a
 * real Compose host here makes the Android application entry point resolvable
 * now while preserving the existing enforcement components' MainActivity
 * references.
 */
class MainActivity : ComponentActivity() {
    private val vpnRepository by lazy { VpnRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MaterialTheme {
                FocusFlowRoot(
                    route = routeFromIntent(intent),
                    vpnRepository = vpnRepository,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun routeFromIntent(intent: Intent?): String =
        intent?.data?.path?.removePrefix("/")?.ifBlank { null } ?: "home"
}

@Composable
private fun FocusFlowRoot(
    route: String,
    vpnRepository: VpnRepository,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var currentRoute by remember(route) { mutableStateOf(route) }
    val settingsViewModel = remember {
        SettingsViewModel(AppModule.settingsRepository, AppModule.pinManager, context)
    }

    if (currentRoute == "user_profile") {
        UserProfileScreen(
            settingsRepository = AppModule.settingsRepository,
            isEditMode = true,
            onBack = { currentRoute = "settings" },
            onFinished = { currentRoute = "settings" },
        )
        return
    }
    if (currentRoute == "password_protection") {
        PasswordProtectionScreen(
            settingsViewModel = settingsViewModel,
            focusPinManager = remember { FocusPinManager(context) },
            onBack = { currentRoute = "defense" },
        )
        return
    }

    var networkSettings by remember { mutableStateOf<NetworkBlockSettings?>(null) }

    // The policy is SharedPreferences-backed because native enforcement services
    // must see changes immediately. A light refresh keeps the global overlay
    // current while future routes are added to this same root host.
    LaunchedEffect(vpnRepository) {
        while (true) {
            networkSettings = runCatching {
                vpnRepository.getNetworkBlockSettings()
            }.getOrNull()
            delay(1_500)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "FocusFlow",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Native Android host ready",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "Route: $currentRoute",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }

        networkSettings?.let { policy ->
            VpnPermissionLostBanner(
                vpnBlockEnabled = policy.enabled && policy.vpn,
                vpnPackages = (policy.packages + policy.standalonePackages).distinct(),
                vpnRepository = vpnRepository,
            )
        }
    }
}