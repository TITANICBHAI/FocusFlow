package com.tbtechs.focusflow.ui.launcher

import android.content.Intent
import android.widget.Toast
import android.provider.Settings
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tbtechs.focusflow.data.images.BuiltinWallpapers
import com.tbtechs.focusflow.data.repository.InstalledAppInfo
import com.tbtechs.focusflow.di.AppModule
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var showFocusToolsPicker by remember { mutableStateOf(false) }
    var showHiddenPicker by remember { mutableStateOf(false) }
    var showWallpaperScreen by remember { mutableStateOf(false) }
    var pendingUnhide by remember { mutableStateOf<String?>(null) }
    var showPinGate by remember { mutableStateOf(false) }
    var pinText by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var defaultRowUnlocked by remember { mutableStateOf(false) }
    var glassDraft by remember(settings.launcherGlassLevel) {
        mutableStateOf(settings.launcherGlassLevel.toFloat())
    }
    val locked = settings.standaloneBlockActive &&
        settings.standaloneBlockPackages.isNotEmpty() &&
        settings.standaloneBlockUntilMs > System.currentTimeMillis()
    // While a block is running and "Lock launcher during block" is on, the launcher stays
    // fully configurable, but the shortcut to Android's default-home settings is hidden
    // (or put behind the password) so it can't be used to switch launchers.
    val lockActive = locked && settings.launcherLockDuringStandalone
    val hideDefaultRow = lockActive && !defaultRowUnlocked

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
                    // The launcher itself can hide apps and change Focus tools; pick that up.
                    settingsViewModel.refreshFromStore()
                }
                else -> Unit
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
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
            if (!hideDefaultRow) item {
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

            if (hideDefaultRow) {
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
                                Text("Default home app is locked", fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                            }
                            Text(
                                "A block is running, so the shortcut to Android's default home settings is hidden. Everything else here still works.",
                                fontSize = 12.sp,
                                color = Color(0xFFFDE68A),
                            )
                            if (settings.pinProtectionEnabled) {
                                TextButton(onClick = { pinText = ""; pinError = false; showPinGate = true }) {
                                    Text("Enter password to show it", color = Color(0xFFFBBF24), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Look: Glassiness, drawer layout, wallpaper
            item {
                Text(
                    "LAUNCHER LOOK",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = DarkTextSecondary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Glassiness",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = DarkTextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (glassDraft.toInt() == 0) "0% · plain black" else "${glassDraft.toInt()}%",
                                fontSize = 12.sp,
                                color = DarkTextSecondary,
                            )
                        }
                        Slider(
                            value = glassDraft,
                            onValueChange = { glassDraft = it },
                            onValueChangeFinished = {
                                val level = glassDraft.toInt().coerceIn(0, 100)
                                settingsViewModel.updateSettings { s -> s.copy(launcherGlassLevel = level) }
                            },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = BrandPrimary,
                                activeTrackColor = BrandPrimary,
                            ),
                        )
                        Text(
                            "0% is a plain black launcher. Higher values fade in your wallpaper and turn every panel into frosted glass. You can also change it from the launcher's ⋮ menu.",
                            fontSize = 11.sp,
                            color = DarkTextSecondary,
                        )
                    }
                }
            }

            item {
                Text(
                    "APP DRAWER LAYOUT",
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
                        label = "Grid",
                        selected = settings.launcherDrawerLayout != "list",
                        onClick = {
                            settingsViewModel.updateSettings { s -> s.copy(launcherDrawerLayout = "grid") }
                        },
                    )
                    LauncherSegment(
                        label = "List",
                        selected = settings.launcherDrawerLayout == "list",
                        onClick = {
                            settingsViewModel.updateSettings { s -> s.copy(launcherDrawerLayout = "list") }
                        },
                    )
                }
            }

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
                                    when {
                                        !settings.launcherWallpaperUri.isNullOrBlank() -> "Your own picture"
                                        BuiltinWallpapers.byId(settings.launcherBuiltinWallpaper) != null ->
                                            BuiltinWallpapers.byId(settings.launcherBuiltinWallpaper)!!.name
                                        else -> "Adaptive (follows your phone)"
                                    },
                                    fontSize = 11.sp,
                                    color = DarkTextSecondary,
                                )
                            }
                            Button(
                                onClick = { showWallpaperScreen = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                            ) {
                                Text("Choose", fontSize = 13.sp, color = Color.White)
                            }
                        }
                        Text(
                            "Pick one of ours or your own, and set it on your phone too so Recents and the lock screen match.",
                            fontSize = 11.sp,
                            color = DarkTextSecondary,
                        )
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
                            onCheckedChange = { enabled ->
                                if (!enabled && locked) {
                                    Toast.makeText(
                                        context,
                                        "This can't be turned off while a block is running.",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    settingsViewModel.updateSettings { s ->
                                        s.copy(launcherLockDuringStandalone = enabled)
                                    }
                                }
                            },
                        )
                    }
                }
            }

            // Focus tools and hidden apps: full lists open in a picker.
            item {
                Text(
                    "APPS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = DarkTextSecondary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LauncherPickerRow(
                        title = "Focus tools",
                        count = settings.focusToolPackages.size,
                        subtitle = "Apps you want close at hand. They get their own FOCUS TOOLS filter in the app drawer. You can also long-press any app in the launcher.",
                        onClick = { showFocusToolsPicker = true },
                    )
                    LauncherPickerRow(
                        title = "Hidden apps",
                        count = settings.launcherHiddenPackages.size,
                        subtitle = "Hidden apps stay installed but leave the drawer and search. Any app can be hidden. Long-press an app in the launcher; find them again in the drawer's ⋮ menu. Unhiding asks for your password if you set one.",
                        onClick = { showHiddenPicker = true },
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showFocusToolsPicker) {
        AppPickerDialog(
            title = "Focus tools",
            description = "Pick the apps for the Focus tools filter.",
            apps = installedApps,
            loading = appsLoading,
            error = installedAppsState.error != null,
            selected = settings.focusToolPackages.toSet(),
            onToggle = { pkg ->
                settingsViewModel.updateSettings { s ->
                    val next = s.focusToolPackages.toMutableSet()
                    if (!next.add(pkg)) next.remove(pkg)
                    s.copy(focusToolPackages = next.toList())
                }
            },
            onDismiss = { showFocusToolsPicker = false },
        )
    }
    if (showHiddenPicker) {
        AppPickerDialog(
            title = "Hidden apps",
            description = "Switch on any app to hide it from the drawer and search.",
            apps = installedApps,
            loading = appsLoading,
            error = installedAppsState.error != null,
            selected = settings.launcherHiddenPackages.toSet(),
            onToggle = { pkg ->
                // Hiding is instant. Unhiding takes a little effort on purpose: it is refused
                // while a block is running and asks for the password when one is set.
                when {
                    pkg !in settings.launcherHiddenPackages ->
                        settingsViewModel.updateSettings { s ->
                            s.copy(launcherHiddenPackages = (s.launcherHiddenPackages + pkg).distinct())
                        }
                    locked -> Toast.makeText(
                        context,
                        "Apps can't be unhidden while a block is running.",
                        Toast.LENGTH_LONG,
                    ).show()
                    settings.pinProtectionEnabled -> {
                        pinText = ""
                        pinError = false
                        pendingUnhide = pkg
                        showPinGate = true
                    }
                    else -> settingsViewModel.updateSettings { s ->
                        s.copy(launcherHiddenPackages = s.launcherHiddenPackages - pkg)
                    }
                }
            },
            onDismiss = { showHiddenPicker = false },
        )
    }
    if (showWallpaperScreen) {
        LauncherWallpaperScreen(
            settingsViewModel = settingsViewModel,
            onClose = { showWallpaperScreen = false },
        )
    }
    if (showPinGate) {
        AlertDialog(
            onDismissRequest = { showPinGate = false; pendingUnhide = null },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Password required", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = pinText,
                        onValueChange = { pinText = it; pinError = false },
                        label = { Text("Password", color = DarkTextMuted) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = pinError,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DarkTextPrimary,
                            unfocusedTextColor = DarkTextPrimary,
                            focusedBorderColor = BrandPrimary,
                            unfocusedBorderColor = DarkBorder,
                        ),
                    )
                    if (pinError) {
                        Text("That password is not right.", fontSize = 12.sp, color = Color(0xFFEF4444))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (settingsViewModel.verifyPin(pinText)) {
                            pinText = ""
                            showPinGate = false
                            val unhide = pendingUnhide
                            pendingUnhide = null
                            if (unhide != null) {
                                settingsViewModel.updateSettings { s ->
                                    s.copy(launcherHiddenPackages = s.launcherHiddenPackages - unhide)
                                }
                            } else {
                                defaultRowUnlocked = true
                            }
                        } else {
                            pinError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showPinGate = false; pendingUnhide = null }) {
                    Text("Cancel", color = DarkTextSecondary)
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

@Composable
private fun LauncherPickerRow(
    title: String,
    count: Int,
    subtitle: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (count > 0) "$title · $count" else title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, fontSize = 11.sp, color = DarkTextSecondary)
            }
            Text("Edit", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BrandPrimary)
        }
    }
}

/** Full-screen list of every installed app with a switch each (no 15-app cap). */
@Composable
private fun AppPickerDialog(
    title: String,
    description: String,
    apps: List<InstalledAppInfo>,
    loading: Boolean,
    error: Boolean,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) apps else apps.filter {
            it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                    Text(description, fontSize = 12.sp, color = DarkTextMuted)
                }
                TextButton(onClick = onDismiss) {
                    Text("Done", color = BrandPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            AppSearchField(query) { query = it }
            Spacer(modifier = Modifier.height(10.dp))
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = BrandPrimary, modifier = Modifier.size(28.dp))
                }
            }
            if (!loading && error) {
                Text(
                    "Installed apps could not be loaded. Close this and reopen to retry.",
                    modifier = Modifier.padding(vertical = 12.dp),
                    fontSize = 12.sp,
                    color = DarkTextSecondary,
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.packageName }) { app ->
                    LauncherAppRow(
                        app = app,
                        checked = app.packageName in selected,
                        onToggle = { onToggle(app.packageName) },
                    )
                }
            }
        }
    }
}
