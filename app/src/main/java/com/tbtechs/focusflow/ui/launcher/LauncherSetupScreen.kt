package com.tbtechs.focusflow.ui.launcher

import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tbtechs.focusflow.data.repository.InstalledAppInfo
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.data.repository.LauncherController
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.common.FocusFlowSwitch
import com.tbtechs.focusflow.ui.common.rememberInstalledApps
import com.tbtechs.focusflow.ui.home.FocusFlowInternalHeader
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import kotlinx.coroutines.launch

/**
 * Home Launcher configuration screen.
 *
 * Implements screenshot 3e_1 with dark theme styling, default launcher status check,
 * focus tools selection, and hidden app toggles.
 */
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
    var appCatalogRefreshToken by remember { mutableStateOf(0) }
    var hasPausedSinceResume by remember { mutableStateOf(false) }
    val installedAppsState = rememberInstalledApps(
        installedAppsRepository,
        refreshToken = appCatalogRefreshToken,
    )
    val installedApps = installedAppsState.apps
    val appsLoading = installedAppsState.loading
    var defaultLauncher by remember { mutableStateOf<Boolean?>(null) }
    var search by remember { mutableStateOf("") }
    var hideWarning by remember { mutableStateOf<String?>(null) }
    val blockedPackages = settings.alwaysBlockPackages.toSet() + settings.standaloneBlockPackages
    val locked = settings.standaloneBlockActive &&
        settings.standaloneBlockPackages.isNotEmpty() &&
        settings.standaloneBlockUntilMs > System.currentTimeMillis()

    fun refreshDefaultLauncher() {
        scope.launch {
            defaultLauncher = runCatching { settingsRepository.isDefaultLauncher() }.getOrNull()
        }
    }

    LaunchedEffect(Unit) { refreshDefaultLauncher() }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> hasPausedSinceResume = true
                Lifecycle.Event.ON_RESUME -> {
                    if (hasPausedSinceResume) {
                        hasPausedSinceResume = false
                        appCatalogRefreshToken += 1
                    }
                    refreshDefaultLauncher()
                }
                else -> Unit
            }
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
        containerColor = DarkBackground,
        topBar = {
            FocusFlowInternalHeader(
                title = "Home Launcher",
                subtitle = "Customize your FocusFlow launcher",
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Default home app card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BrandPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Home,
                                contentDescription = null,
                                tint = BrandPrimary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Default Home App",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkTextPrimary,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when (defaultLauncher) {
                                    true -> "FocusFlow is active as your default launcher"
                                    false -> "Choose FocusFlow in Android Home settings"
                                    null -> "Checking Android Home settings…"
                                },
                                fontSize = 11.sp,
                                color = if (defaultLauncher == true) Color(0xFF34D399) else DarkTextSecondary,
                            )
                        }
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                                runCatching { context.startActivity(intent) }
                                    .onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 12.dp,
                                vertical = 6.dp,
                            ),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                        ) {
                            Text("Open", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }
            }

            if (locked) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF451A03))
                            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                                Text("Launcher Locked", fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                            }
                            Text(
                                "Launcher settings cannot be modified during an active Standalone Block.",
                                fontSize = 12.sp,
                                color = Color(0xFFFDE68A),
                            )
                        }
                    }
                }
            } else {
                // Appearance options
                item {
                    Text(
                        "LAUNCHER APPEARANCE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = DarkTextSecondary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        LauncherSegment(
                            label = "Classic",
                            selected = settings.launcherTheme == "classic",
                            onClick = {
                                settingsViewModel.updateSettings(settings.copy(launcherTheme = "classic"))
                            },
                        )
                        LauncherSegment(
                            label = "Glassy",
                            selected = settings.launcherTheme != "classic",
                            onClick = {
                                settingsViewModel.updateSettings(settings.copy(launcherTheme = "glassy"))
                            },
                        )
                    }
                }

                if (settings.launcherTheme != "classic") {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkCard)
                                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                                .padding(8.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(Icons.Outlined.Image, contentDescription = null, tint = BrandPrimary)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Wallpaper", fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                                        Text(
                                            if (settings.launcherWallpaperUri == null) "Default dynamic gradient"
                                            else "Custom wallpaper selected",
                                            fontSize = 11.sp,
                                            color = DarkTextSecondary,
                                        )
                                    }
                                    Button(
                                        onClick = { wallpaperPicker.launch("image/*") },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                                    ) {
                                        Text("Pick", fontSize = 13.sp, color = Color.White)
                                    }
                                }
                                if (settings.launcherWallpaperUri != null) {
                                    TextButton(
                                        onClick = { settingsViewModel.updateSettings(settings.copy(launcherWallpaperUri = null)) },
                                    ) {
                                        Text("Clear wallpaper", color = Color(0xFFEF4444), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Lock launcher switch card
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkCard)
                            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Lock Launcher During Block",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkTextPrimary,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Prevent switching out of FocusFlow launcher while a standalone block is active.",
                                    fontSize = 11.sp,
                                    color = DarkTextSecondary,
                                )
                            }
                            FocusFlowSwitch(
                                checked = settings.launcherLockDuringStandalone,
                                onCheckedChange = {
                                    settingsViewModel.updateSettings(settings.copy(launcherLockDuringStandalone = it))
                                },
                            )
                        }
                    }
                }

                // Focus Tools Section
                item {
                    Text(
                        "FOCUS TOOLS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = DarkTextSecondary,
                    )
                    Text(
                        "Always keep selected tools visible in the launcher during active focus sessions.",
                        fontSize = 12.sp,
                        color = DarkTextMuted,
                    )
                }

                item {
                    AppSearchField(search, { search = it })
                }

                if (appsLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = BrandPrimary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
                if (!appsLoading && installedAppsState.error != null) {
                    item {
                        Text(
                            "Installed apps could not be loaded. Return to this screen to retry.",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            fontSize = 12.sp,
                            color = DarkTextSecondary,
                        )
                    }
                }

                items(filteredApps.take(15), key = { "tool-${it.packageName}" }) { app ->
                    val checked = app.packageName in settings.focusToolPackages
                    LauncherAppRow(
                        app = app,
                        checked = checked,
                        onToggle = {
                            val next = settings.focusToolPackages.toMutableSet()
                            if (!next.add(app.packageName)) next.remove(app.packageName)
                            settingsViewModel.updateSettings(settings.copy(focusToolPackages = next.toList()))
                        },
                    )
                }

                // Hide from Launcher Section
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "HIDE FROM LAUNCHER",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = DarkTextSecondary,
                    )
                    Text(
                        "Hidden apps remain installed on Android but are removed from the launcher app drawer.",
                        fontSize = 12.sp,
                        color = DarkTextMuted,
                    )
                }

                items(filteredApps.take(15), key = { "hidden-${it.packageName}" }) { app ->
                    val checked = app.packageName in settings.launcherHiddenPackages
                    LauncherAppRow(
                        app = app,
                        checked = checked,
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
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    hideWarning?.let { name ->
        AlertDialog(
            onDismissRequest = { hideWarning = null },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("App is not blocked") },
            text = { Text("Hide $name only after adding it to a block list so that important apps remain discoverable.", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { hideWarning = null }) {
                    Text("OK", color = BrandPrimary)
                }
            },
        )
    }
}

@Composable
private fun LauncherSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) BrandPrimary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else DarkTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AppSearchField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        singleLine = true,
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = DarkTextMuted, modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            if (value.isNotBlank()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Outlined.Clear, contentDescription = "Clear search", tint = DarkTextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        },
        placeholder = { Text("Search installed apps", color = DarkTextMuted, fontSize = 13.sp) },
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = DarkSurfaceVariant,
            unfocusedContainerColor = DarkSurfaceVariant,
            focusedBorderColor = BrandPrimary,
            unfocusedBorderColor = DarkBorder,
            focusedTextColor = DarkTextPrimary,
            unfocusedTextColor = DarkTextPrimary,
        ),
    )
}

@Composable
private fun LauncherAppRow(app: InstalledAppInfo, checked: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppIcon(app.icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    fontSize = 11.sp,
                    color = DarkTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FocusFlowSwitch(
                checked = checked,
                onCheckedChange = { onToggle() },
            )
        }
    }
}
