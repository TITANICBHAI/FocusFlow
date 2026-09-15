package com.tbtechs.focusflow.ui.permissions

import android.Manifest
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tbtechs.focusflow.ui.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PermissionsScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    isFocusActive: Boolean = false,
    onBack: () -> Unit = {},
    onConfigureLauncher: () -> Unit = {},
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val settings by settingsViewModel.settings.collectAsStateCompat()
    var statuses by remember { mutableStateOf<Map<PermissionId, PermissionStatus>>(emptyMap()) }
    var expanded by remember { mutableStateOf<PermissionId?>(null) }
    var checking by remember { mutableStateOf(true) }
    var troubleshooting by remember { mutableStateOf<PermissionId?>(null) }
    val standaloneActive = settings.standaloneBlockActive &&
        settings.standaloneBlockPackages.isNotEmpty() &&
        settings.standaloneBlockUntilMs > System.currentTimeMillis()
    val locked = isFocusActive || standaloneActive

    fun refresh() {
        scope.launch {
            checking = true
            statuses = withContext(Dispatchers.IO) {
                permissionDefinitions.associate { it.id to checkPermission(context, it.id) }
            }
            checking = false
        }
    }
    LaunchedEffect(Unit) { refresh() }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    fun grant(id: PermissionId) {
        if (id == PermissionId.MEDIA) {
            launcher.launch(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE)
        } else if (id == PermissionId.NOTIFICATIONS && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (id == PermissionId.VPN) {
            VpnService.prepare(context)?.let(vpnLauncher::launch) ?: refresh()
        } else if (id == PermissionId.LAUNCHER) {
            scope.launch { openPermissionSettings(context, id) }
        } else {
            scope.launch { openPermissionSettings(context, id) }
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Permissions") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } },
            actions = {
                if (!locked) IconButton(onClick = ::refresh, enabled = !checking) {
                    Icon(Icons.Outlined.Refresh, "Refresh")
                }
            },
        )
    }) { padding ->
        if (locked) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Card {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Settings Locked", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            if (isFocusActive) "Permission settings are disabled while a focus session is running."
                            else "Permission settings are disabled while a standalone block is active.",
                        )
                        Text("Changing permissions during an active block could bypass app blocking — stop the block first.")
                        Button(onClick = onBack) { Text("Go Back") }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { RestrictedSettingsBanner() }
                item {
                    Card {
                        Row(modifier = Modifier.padding(16.dp)) {
                            Column {
                                Text("Why these permissions?", style = MaterialTheme.typography.titleMedium)
                                Text("FocusFlow enforces focus at the system level, not just with reminders. Android requires special access for reliable blocking.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                val required = permissionDefinitions.count { !it.optional }
                val granted = permissionDefinitions.count { !it.optional && statuses[it.id] == PermissionStatus.GRANTED }
                item {
                    Text("Required permissions granted: $granted / $required")
                    androidx.compose.material3.LinearProgressIndicator(progress = { granted.toFloat() / required.toFloat() }, modifier = Modifier.fillMaxWidth())
                    if (granted == required) Text("All required permissions granted — blocking is fully active.", color = MaterialTheme.colorScheme.primary)
                }
                items(permissionDefinitions, key = { it.id }) { permission ->
                    PermissionCard(
                        permission = permission,
                        status = statuses[permission.id] ?: PermissionStatus.UNKNOWN,
                        expanded = expanded == permission.id,
                        busy = checking,
                        showTroubleshoot = statuses[permission.id] != PermissionStatus.GRANTED,
                        onToggle = { expanded = if (expanded == permission.id) null else permission.id },
                        onGrant = { grant(permission.id) },
                        onTroubleshoot = { troubleshooting = permission.id },
                    )
                    if (permission.id == PermissionId.LAUNCHER && statuses[permission.id] == PermissionStatus.GRANTED) {
                        TextButton(onClick = onConfigureLauncher) { Text("Configure Launcher Settings →") }
                    }
                }
                item {
                    Text("Tap a card to expand details. Statuses refresh when you return to this screen.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    troubleshooting?.let { id ->
        val permission = permissionDefinitions.first { it.id == id }
        AlertDialog(
            onDismissRequest = { troubleshooting = null },
            title = { Text("Troubleshoot ${permission.title}") },
            text = { Text("If Android did not apply the change, open the system page again, confirm the toggle, then return to FocusFlow and tap Refresh. OEM battery managers may require an additional exception.") },
            confirmButton = { Button(onClick = { troubleshooting = null; grant(id) }) { Text("Open settings") } },
            dismissButton = { TextButton(onClick = { troubleshooting = null }) { Text("Close") } },
        )
    }
}
