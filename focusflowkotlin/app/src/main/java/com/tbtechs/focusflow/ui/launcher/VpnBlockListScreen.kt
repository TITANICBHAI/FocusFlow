package com.tbtechs.focusflow.ui.launcher

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import com.tbtechs.focusflow.data.repository.InstalledAppInfo
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.data.repository.NetworkBlockSettings
import com.tbtechs.focusflow.data.repository.VpnRepository
import com.tbtechs.focusflow.ui.SettingsViewModel
import kotlinx.coroutines.launch
import java.security.MessageDigest

@Composable
fun VpnBlockListScreen(
    settingsViewModel: SettingsViewModel,
    vpnRepository: VpnRepository,
    installedAppsRepository: InstalledAppsRepository,
    isFocusActive: Boolean = false,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val settings by settingsViewModel.settings.collectAsState()
    var networkSettings by remember { mutableStateOf<NetworkBlockSettings?>(null) }
    var apps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var original by remember { mutableStateOf<Set<String>>(emptySet()) }
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pinDialog by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var clearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loading = true
        val loaded = runCatching { vpnRepository.getNetworkBlockSettings() }.getOrNull()
        networkSettings = loaded
        selected = loaded?.packages?.toSet().orEmpty()
        original = selected
        apps = runCatching {
            installedAppsRepository.getInstalledApps().sortedBy { it.appName.lowercase() }
        }.getOrDefault(emptyList())
        loading = false
    }

    val filtered = remember(apps, search) {
        val query = search.trim().lowercase()
        if (query.isBlank()) apps else apps.filter {
            it.appName.lowercase().contains(query) || it.packageName.lowercase().contains(query)
        }
    }
    val locked = isFocusActive || settings.standaloneBlockActive
    val removing = original.any { it !in selected }

    fun save() {
        if (removing && locked) {
            error = "VPN-blocked apps cannot be removed while Focus Mode or a Standalone Block is active."
            return
        }
        if (removing && settings.pinProtectionEnabled && !pinDialog) {
            pinDialog = true
            return
        }
        scope.launch {
            saving = true
            error = null
            try {
                if (removing && settings.pinProtectionEnabled && !settingsViewModel.verifyPin(pin)) {
                    error = "Incorrect defense PIN."
                    return@launch
                }
                if (selected.isNotEmpty() && !vpnRepository.isVpnPermissionGranted()) {
                    val activity = context as? Activity
                    vpnRepository.requestVpnPermission(activity)
                    error = "VPN permission is required. Grant it and tap Save again."
                    return@launch
                }
                val current = networkSettings ?: NetworkBlockSettings()
                val hasPackages = selected.isNotEmpty()
                vpnRepository.setNetworkBlockSettings(
                    current.copy(
                        enabled = hasPackages,
                        vpn = hasPackages,
                        packages = selected.toList().sorted(),
                    ),
                    defensePinHash = pin.takeIf {
                        removing && settings.pinProtectionEnabled
                    }?.let(::legacyPinHash),
                )
                vpnRepository.setVpnSelfHealEnabled(hasPackages)
                original = selected
                pinDialog = false
                pin = ""
                onBack()
            } catch (exception: Exception) {
                error = exception.message ?: "Could not save the VPN block list."
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VPN Block List") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (selected.isNotEmpty()) clearDialog = true },
                        enabled = selected.isNotEmpty() && !saving,
                    ) {
                        Icon(Icons.Outlined.ClearAll, contentDescription = "Clear all")
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = ::save,
                enabled = !loading && !saving,
                modifier = Modifier.fillMaxWidth().padding(LauncherDimensions.pagePadding),
            ) {
                if (saving) CircularProgressIndicator()
                else Text("Save")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = LauncherDimensions.pagePadding),
            verticalArrangement = Arrangement.spacedBy(LauncherDimensions.gap),
        ) {
            if (locked) {
                Card {
                    Text(
                        "Removing blocked apps is locked while Focus Mode or Standalone Block is active.",
                        modifier = Modifier.padding(LauncherDimensions.itemPadding),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Search apps") },
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (loading) {
                CircularProgressIndicator()
            } else if (apps.isEmpty()) {
                Text("No installed apps found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(LauncherDimensions.smallGap),
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        VpnAppRow(
                            app = app,
                            checked = app.packageName in selected,
                            overlayBlocked = app.packageName in settings.alwaysBlockPackages,
                            onToggle = {
                                selected = if (app.packageName in selected) {
                                    selected - app.packageName
                                } else {
                                    selected + app.packageName
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text("Clear VPN block list?") },
            text = { Text("This removes all apps from network blocking.") },
            confirmButton = {
                Button(onClick = {
                    selected = emptySet()
                    clearDialog = false
                }) { Text("Clear all") }
            },
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancel") } },
        )
    }

    if (pinDialog) {
        AlertDialog(
            onDismissRequest = {
                pinDialog = false
                pin = ""
            },
            title = { Text("Defense PIN required") },
            text = {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("PIN") },
                    singleLine = true,
                )
            },
            confirmButton = { Button(onClick = ::save) { Text("Verify and save") } },
            dismissButton = {
                TextButton(onClick = {
                    pinDialog = false
                    pin = ""
                }) { Text("Cancel") }
            },
        )
    }
}

private fun legacyPinHash(pin: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(pin.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

@Composable
private fun VpnAppRow(
    app: InstalledAppInfo,
    checked: Boolean,
    overlayBlocked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = LauncherDimensions.smallGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.icon)
        Column(Modifier.padding(start = LauncherDimensions.gap).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.appName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (overlayBlocked) {
                    Text(
                        " overlay",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}