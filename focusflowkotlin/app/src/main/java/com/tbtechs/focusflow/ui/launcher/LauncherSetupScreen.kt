package com.tbtechs.focusflow.ui.launcher

import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.repository.InstalledAppInfo
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.data.repository.LauncherController
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.ui.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun LauncherSetupScreen(
    settingsViewModel: SettingsViewModel,
    settingsRepository: SettingsRepository,
    installedAppsRepository: InstalledAppsRepository,
    launcherController: LauncherController,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val scope = rememberCoroutineScope()
    val settings by settingsViewModel.settings.collectAsState()
    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var defaultLauncher by remember { mutableStateOf<Boolean?>(null) }
    var search by remember { mutableStateOf("") }
    var hideWarning by remember { mutableStateOf<String?>(null) }
    val blockedPackages = settings.alwaysBlockPackages.toSet() + settings.standaloneBlockPackages
    val locked = settings.standaloneBlockActive && settings.launcherLockDuringStandalone

    fun refresh() {
        scope.launch {
            defaultLauncher = runCatching { settingsRepository.isDefaultLauncher() }.getOrNull()
            installedApps = runCatching {
                installedAppsRepository.getInstalledApps().sortedBy { it.appName.lowercase() }
            }.getOrDefault(emptyList())
        }
    }

    LaunchedEffect(Unit) { refresh() }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { settingsViewModel.updateSettings(settings.copy(launcherWallpaperUri = it.toString())) }
    }
    val filteredApps = remember(installedApps, search) {
        val query = search.trim().lowercase()
        if (query.isBlank()) installedApps else installedApps.filter {
            it.appName.lowercase().contains(query) || it.packageName.lowercase().contains(query)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home Launcher") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = LauncherDimensions.pagePadding),
            verticalArrangement = Arrangement.spacedBy(LauncherDimensions.gap),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(LauncherDimensions.itemPadding)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Home, contentDescription = null)
                            Column(Modifier.padding(start = LauncherDimensions.gap).weight(1f)) {
                                Text("Default home app", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    when (defaultLauncher) {
                                        true -> "FocusFlow is your default launcher"
                                        false -> "Choose FocusFlow in Android Home settings"
                                        null -> "Checking Android Home settings…"
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(onClick = {
                                val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                                runCatching { context.startActivity(intent) }
                                    .onFailure {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                    }
                            }) { Text("Open") }
                        }
                    }
                }
            }
            if (locked) {
                item {
                    Card {
                        Column(Modifier.padding(LauncherDimensions.itemPadding)) {
                            Text("Launcher locked during Standalone Block", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Go back to FocusFlow to change launcher settings after the block ends.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = onBack) { Text("Go back") }
                        }
                    }
                }
            } else {
                item {
                    Text("Launcher appearance", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(LauncherDimensions.smallGap)) {
                        FilterChip(
                            selected = settings.launcherTheme == "classic",
                            onClick = { settingsViewModel.updateSettings(settings.copy(launcherTheme = "classic")) },
                            label = { Text("Classic") },
                        )
                        FilterChip(
                            selected = settings.launcherTheme != "classic",
                            onClick = { settingsViewModel.updateSettings(settings.copy(launcherTheme = "glassy")) },
                            label = { Text("Glassy") },
                        )
                    }
                }
                if (settings.launcherTheme != "classic") {
                    item {
                        Card {
                            Column(Modifier.padding(LauncherDimensions.itemPadding)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Image, contentDescription = null)
                                    Column(Modifier.padding(start = LauncherDimensions.gap).weight(1f)) {
                                        Text("Wallpaper", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            if (settings.launcherWallpaperUri == null) "Use the default background"
                                            else "Custom wallpaper selected",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    OutlinedButton(onClick = { wallpaperPicker.launch("image/*") }) {
                                        Text("Pick")
                                    }
                                }
                                if (settings.launcherWallpaperUri != null) {
                                    TextButtonRow(
                                        label = "Clear wallpaper",
                                        onClick = {
                                            settingsViewModel.updateSettings(settings.copy(launcherWallpaperUri = null))
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Text("Focus Tools", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Keep selected tools visible in the launcher while FocusFlow is active.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item { AppSearchField(search, { search = it }) }
                items(filteredApps, key = { it.packageName }) { app ->
                    LauncherAppRow(
                        app = app,
                        checked = app.packageName in settings.focusToolPackages,
                        onToggle = {
                            val next = settings.focusToolPackages.toMutableSet()
                            if (!next.add(app.packageName)) next.remove(app.packageName)
                            settingsViewModel.updateSettings(settings.copy(focusToolPackages = next.toList()))
                        },
                    )
                }
                item {
                    Text("Hide from launcher", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Hidden apps stay installed but do not appear in the launcher drawer.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(filteredApps, key = { "hidden-${it.packageName}" }) { app ->
                    LauncherAppRow(
                        app = app,
                        checked = app.packageName in settings.launcherHiddenPackages,
                        onToggle = {
                            if (app.packageName !in settings.launcherHiddenPackages &&
                                app.packageName !in blockedPackages
                            ) {
                                hideWarning = app.appName
                            } else {
                                val next = settings.launcherHiddenPackages.toMutableSet()
                                if (!next.add(app.packageName)) next.remove(app.packageName)
                                settingsViewModel.updateSettings(settings.copy(launcherHiddenPackages = next.toList()))
                            }
                        },
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Lock launcher during Standalone Block", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Prevent leaving the launcher while a standalone block is active.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.launcherLockDuringStandalone,
                            onCheckedChange = {
                                settingsViewModel.updateSettings(settings.copy(launcherLockDuringStandalone = it))
                            },
                        )
                    }
                }
            }
        }
    }

    hideWarning?.let { name ->
        AlertDialog(
            onDismissRequest = { hideWarning = null },
            title = { Text("App is not blocked") },
            text = { Text("Hide $name only after it is included in a block list. This keeps important launcher actions discoverable.") },
            confirmButton = { TextButton(onClick = { hideWarning = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun AppSearchField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotBlank()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
                }
            }
        },
        placeholder = { Text("Search installed apps") },
    )
}

@Composable
private fun LauncherAppRow(app: InstalledAppInfo, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = LauncherDimensions.smallGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.icon)
        Column(Modifier.padding(start = LauncherDimensions.gap).weight(1f)) {
            Text(app.appName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun TextButtonRow(label: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) { Text(label) }
}