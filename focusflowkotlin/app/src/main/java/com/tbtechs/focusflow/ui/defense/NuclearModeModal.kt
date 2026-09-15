package com.tbtechs.focusflow.ui.defense

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.repository.InstalledAppInfo
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.data.repository.NuclearModeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuclearModeModal(
    visible: Boolean,
    blockedPackages: List<String>,
    onClose: () -> Unit,
) {
    if (!visible) return
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var pending by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(visible, blockedPackages) {
        loading = true
        apps = withContext(Dispatchers.IO) {
            runCatching {
                InstalledAppsRepository(context).getInstalledApps()
                    .filter { it.packageName in blockedPackages }
                    .sortedBy { it.appName.lowercase() }
            }.getOrElse {
                error = "FocusFlow couldn't read the installed-app list."
                emptyList()
            }
        }
        loading = false
    }

    ModalBottomSheet(onDismissRequest = onClose) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Nuclear Mode") },
                    navigationIcon = {
                        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close") }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    OutlinedCard {
                        Row(modifier = Modifier.padding(16.dp)) {
                            Icon(Icons.Outlined.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text("Permanent action", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                                Text(
                                    "Uninstalling an app removes all its data. Android will ask you to confirm each uninstall.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    Text("APPS FROM YOUR BLOCK LIST", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "These are the apps currently in your standalone or Always-On block lists.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (loading) {
                    item { CircularProgressIndicator() }
                } else if (apps.isEmpty()) {
                    item {
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text("No blocked apps installed", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (blockedPackages.isEmpty()) {
                                        "Add apps to a block list first, then return here."
                                    } else {
                                        "All apps in your block lists have already been uninstalled."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                } else {
                    items(apps, key = { it.packageName }) { app ->
                        NuclearAppRow(app = app, onUninstall = { pending = app })
                    }
                }
                error?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.error) }
                }
                item {
                    Row(modifier = Modifier.padding(vertical = 12.dp)) {
                        Icon(Icons.Outlined.Info, contentDescription = null)
                        Text(
                            "To use Nuclear Mode, first add apps to Standalone Block or Always-On.",
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }

    pending?.let { app ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Uninstall ${app.appName}?") },
            text = { Text("Android will open its system uninstall dialog. You must confirm there.") },
            confirmButton = {
                Button(onClick = {
                    pending = null
                    val repository = NuclearModeRepository(context)
                    scope.launch { runCatching { repository.requestUninstallApp(app.packageName) } }
                }) { Text("Uninstall") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NuclearAppRow(app: InstalledAppInfo, onUninstall: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.titleSmall)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onUninstall) { Text("Uninstall") }
        }
    }
}
