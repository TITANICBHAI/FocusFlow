package com.tbtechs.focusflow.ui.alwayson

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.tbtechs.focusflow.data.repository.InstalledAppInfo
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.data.repository.missingInstalledAppInfo
import com.tbtechs.focusflow.data.repository.NetworkBlockSettings
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.data.repository.VpnRepository
import com.tbtechs.focusflow.data.repository.VpnListSavePolicy
import com.tbtechs.focusflow.data.repository.VpnSelfHealPolicy
import com.tbtechs.focusflow.ui.FocusSessionViewModel
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.common.FocusFlowSwitch
import com.tbtechs.focusflow.ui.common.rememberInstalledApps
import com.tbtechs.focusflow.ui.home.FocusFlowInternalHeader
import com.tbtechs.focusflow.ui.launcher.AppIcon
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.scaledSp
import kotlinx.coroutines.launch
import java.security.MessageDigest

private val systemNeverBlock = setOf(
    "com.android.launcher",
    "com.android.launcher2",
    "com.android.launcher3",
    "com.sec.android.app.launcher",
    "com.google.android.apps.nexuslauncher",
    "com.miui.launcher",
    "com.huawei.android.launcher",
    "com.coloros.launcher",
    "com.oneplus.launcher",
    "com.oppo.launcher",
    "com.motorola.launcher3",
    "com.nothing.launcher",
    "com.realme.launcher",
    "com.iqoo.launcher",
    "com.vivo.launcher",
    "com.asus.launcher",
    "com.ZenUI.launcher",
    "com.lge.launcher3",
    "com.htc.launcher",
    "com.sonyericsson.home",
    "com.tcl.launcher",
    "com.nokia.launcher",
    "com.infinix.launcher",
    "com.transsion.launcher",
    "com.hihonor.launcher",
    "com.android.systemui",
    "com.android.phone",
    "com.android.server.telecom",
    "com.android.dialer",
    "com.samsung.android.incallui",
    "com.google.android.dialer",
    "com.google.android.apps.googledialer",
    "com.whatsapp",
    "com.google.android.gms",
    "com.android.packageinstaller",
    "com.google.android.packageinstaller",
    "com.samsung.android.packageinstaller",
    "com.samsung.android.wallet",
    "com.samsung.android.samsungpay",
    "com.google.android.apps.walletnfcrel",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlwaysOnScreen(
    settingsViewModel: SettingsViewModel,
    focusSessionViewModel: FocusSessionViewModel,
    settingsRepository: SettingsRepository,
    vpnRepository: VpnRepository,
    installedAppsRepository: InstalledAppsRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsViewModel.settings.collectAsState()
    val focusSession by focusSessionViewModel.focusSession.collectAsState()

    var networkSettings by remember { mutableStateOf<NetworkBlockSettings?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var vpnSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var originalSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var originalVpnSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var search by remember { mutableStateOf("") }
    var settingsLoading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPin by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var showConsent by remember { mutableStateOf(false) }
    var clearConfirmation by remember { mutableStateOf(false) }
    var showInfo by remember(settings.alwaysOnInfoDismissed) {
        mutableStateOf(!settings.alwaysOnInfoDismissed)
    }
    var showProtectionStatus by remember(settings.protectionStatusBannerDismissed) {
        mutableStateOf(!settings.protectionStatusBannerDismissed)
    }
    val installedAppsState = rememberInstalledApps(installedAppsRepository)
    val apps = remember(
        installedAppsState.apps,
        installedAppsState.loading,
        installedAppsState.error,
        selected,
        vpnSelected,
        context.packageName,
    ) {
        val catalogPackages = installedAppsState.apps.mapTo(mutableSetOf()) { it.packageName }
        val installed = installedAppsState.apps.filterNot {
            it.packageName in systemNeverBlock || it.packageName == context.packageName
        }
        val missing = if (installedAppsState.loading) {
            emptyList()
        } else {
            (selected + vpnSelected).asSequence()
                .filterNot { it in catalogPackages }
                .distinct()
                .map { packageName ->
                    missingInstalledAppInfo(
                        packageName,
                        if (installedAppsState.error == null) {
                            "App not installed"
                        } else {
                            "App details unavailable"
                        },
                    )
                }
                .toList()
        }
        installed + missing
    }
    val loading = settingsLoading || installedAppsState.loading

    LaunchedEffect(Unit) {
        settingsLoading = true
        val storedSettings = runCatching { settingsRepository.readAppSettings() }.getOrNull()
        val storedNetwork = runCatching { vpnRepository.getNetworkBlockSettings() }.getOrNull()
        networkSettings = storedNetwork
        selected = storedSettings?.alwaysBlockPackages?.toSet().orEmpty()
        vpnSelected = storedNetwork?.packages?.toSet().orEmpty()
        originalSelected = selected
        originalVpnSelected = vpnSelected
        settingsLoading = false
    }

    val filteredApps = remember(apps, search) {
        val query = search.trim().lowercase()
        if (query.isBlank()) apps
        else apps.filter {
            it.appName.lowercase().contains(query) || it.packageName.lowercase().contains(query)
        }
    }
    val blockProtectionActive = focusSession?.isActive == true ||
        (settings.standaloneBlockActive &&
            settings.standaloneBlockUntilMs > System.currentTimeMillis())
    val isRemoving = originalSelected.any { it !in selected } ||
        originalVpnSelected.any { it !in vpnSelected }

    fun save(defensePin: String? = null) {
        if (isRemoving && blockProtectionActive) {
            error = "Always-On apps and VPN-blocked apps cannot be removed while Focus Mode or Standalone Block is active."
            return
        }
        if (isRemoving && settings.pinProtectionEnabled && defensePin == null) {
            showPin = true
            pinError = null
            return
        }

        scope.launch {
            saving = true
            error = null
            try {
                val currentNetwork = networkSettings ?: NetworkBlockSettings()
                val hasVpnPackages = vpnSelected.isNotEmpty() ||
                    currentNetwork.standalonePackages.isNotEmpty()
                val masterSwitches = VpnListSavePolicy.masterSwitchesAfterSave(
                    currentEnabled = currentNetwork.enabled,
                    currentVpn = currentNetwork.vpn,
                    hasPackages = hasVpnPackages,
                )

                if (vpnSelected.isNotEmpty() && !vpnRepository.isVpnPermissionGranted()) {
                    showConsent = true
                    return@launch
                }

                vpnRepository.setNetworkBlockSettings(
                    currentNetwork.copy(
                        enabled = masterSwitches.enabled,
                        vpn = masterSwitches.vpn,
                        packages = vpnSelected.toList().sorted(),
                    ),
                    defensePinHash = defensePin?.let(::legacyPinHash),
                )
                if (VpnSelfHealPolicy.shouldEnableFromList(hasVpnPackages)) {
                    vpnRepository.setVpnSelfHealEnabled(true)
                }
                settingsRepository.setAlwaysBlockActive(
                    active = selected.isNotEmpty(),
                    packages = selected.toList().sorted(),
                )

                settingsViewModel.refreshSettingsFromStore()
                settingsViewModel.updateSettings(
                    settingsViewModel.settings.value.copy(
                        alwaysBlockEnabled = selected.isNotEmpty(),
                        alwaysBlockPackages = selected.toList().sorted(),
                        networkBlockEnabled = masterSwitches.enabled,
                    ),
                )
                onBack()
            } catch (exception: Exception) {
                error = exception.message ?: "Could not save the Always-On list."
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            FocusFlowInternalHeader(
                title = "Always-On Block List",
                subtitle = if (selected.isNotEmpty()) {
                    "${selected.size} app${if (selected.size == 1) "" else "s"} blocked 24/7"
                } else {
                    "Tick apps to block them permanently — no timer"
                },
                onBack = onBack,
                trailing = {
                    if (selected.isNotEmpty()) {
                        IconButton(
                            onClick = { clearConfirmation = true },
                            enabled = !saving,
                        ) {
                            Icon(
                                Icons.Outlined.ClearAll,
                                contentDescription = "Clear all",
                                tint = DarkTextSecondary,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = { save() },
                    enabled = !loading && !saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandPrimary,
                        contentColor = Color.White,
                        disabledContainerColor = DarkBorder,
                        disabledContentColor = DarkTextMuted,
                    ),
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        Text(
                            text = if (selected.isEmpty()) "Save (no apps selected)"
                            else "Save ${selected.size} app${if (selected.size == 1) "" else "s"}",
                            fontSize = 15.scaledSp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (blockProtectionActive && showProtectionStatus) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BrandPrimary.copy(alpha = 0.12f))
                        .border(1.dp, BrandPrimary.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = BrandPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Protection is active",
                                fontSize = 14.scaledSp,
                                fontWeight = FontWeight.Bold,
                                color = DarkTextPrimary,
                            )
                            Text(
                                "This screen is not frozen. You can review the lists; removing protected apps is blocked until the active protection ends or is verified.",
                                fontSize = 12.scaledSp,
                                lineHeight = 17.scaledSp,
                                color = DarkTextSecondary,
                            )
                        }
                        IconButton(
                            onClick = {
                                showProtectionStatus = false
                                settingsViewModel.updateSettings(
                                    settings.copy(protectionStatusBannerDismissed = true),
                                )
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Dismiss protection status",
                                tint = DarkTextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            if (showInfo) {
                // Explanatory Info Card matching 3e_(3)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BrandPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.AllInclusive,
                                contentDescription = null,
                                tint = BrandPrimary,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "These apps are blocked continuously — no session or timer needed. They stay blocked until you untick them here.",
                                fontSize = 13.scaledSp,
                                lineHeight = 18.scaledSp,
                                color = DarkTextPrimary,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Removing apps requires your defense password (if set). Tap a blocked app to also enable network blocking (VPN).",
                                fontSize = 12.scaledSp,
                                lineHeight = 17.scaledSp,
                                color = DarkTextSecondary,
                            )
                        }
                        IconButton(
                            onClick = {
                                showInfo = false
                                settingsViewModel.updateSettings(settings.copy(alwaysOnInfoDismissed = true))
                            },
                            modifier = Modifier.size(26.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Dismiss information",
                                tint = DarkTextMuted,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                singleLine = true,
                placeholder = {
                    Text("Search apps…", color = DarkTextMuted, fontSize = 13.scaledSp)
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = DarkTextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                },
                trailingIcon = {
                    if (search.isNotBlank()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(
                                Icons.Outlined.Clear,
                                contentDescription = "Clear search",
                                tint = DarkTextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkCard,
                    unfocusedContainerColor = DarkCard,
                    focusedBorderColor = BrandPrimary,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = DarkTextPrimary,
                    unfocusedTextColor = DarkTextPrimary,
                ),
            )

            error?.let { err ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF450A0A))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                ) {
                    Text(err, color = Color(0xFFF87171), fontSize = 13.scaledSp)
                }
            }

            when {
                loading && apps.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = BrandPrimary, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Loading apps…", color = DarkTextSecondary, fontSize = 14.scaledSp)
                }
                filteredApps.isEmpty() && !loading -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (search.isBlank()) "No apps found" else "No apps match \"$search\"",
                        color = DarkTextSecondary,
                        fontSize = 14.scaledSp,
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        AlwaysOnAppRow(
                            app = app,
                            checked = app.packageName in selected,
                            vpnEnabled = app.packageName in vpnSelected,
                            onToggle = {
                                if (app.packageName in selected) {
                                    selected = selected - app.packageName
                                    vpnSelected = vpnSelected - app.packageName
                                } else {
                                    selected = selected + app.packageName
                                }
                            },
                            onToggleVpn = {
                                vpnSelected = if (app.packageName in vpnSelected) {
                                    vpnSelected - app.packageName
                                } else {
                                    vpnSelected + app.packageName
                                }
                            },
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
            if (loading && apps.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        color = BrandPrimary,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Loading more apps…", color = DarkTextMuted, fontSize = 11.scaledSp)
                }
            }
        }
    }

    val requestVpnConsent = rememberVpnConsentRequester(vpnRepository) { outcome ->
        error = if (outcome.isGranted) {
            "VPN permission granted. Tap Save again."
        } else {
            "VPN permission was not granted. Your list was not saved."
        }
    }

    VpnConsentModal(
        visible = showConsent,
        onCancel = { showConsent = false },
        onConfirm = {
            showConsent = false
            requestVpnConsent()
        },
    )

    if (clearConfirmation) {
        AlertDialog(
            onDismissRequest = { clearConfirmation = false },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Clear all?") },
            text = { Text("This removes all apps from the Always-On enforcement list. They will no longer be blocked continuously.") },
            confirmButton = {
                Button(
                    onClick = {
                        selected = emptySet()
                        vpnSelected = emptySet()
                        clearConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                ) {
                    Text("Clear all", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirmation = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }

    if (showPin) {
        AlertDialog(
            onDismissRequest = {
                showPin = false
                pin = ""
                pinError = null
            },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Defense password required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "You are removing apps from the Always-On block list. Enter your defense password to confirm.",
                        fontSize = 13.scaledSp,
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            pin = it
                            pinError = null
                        },
                        label = { Text("Defense password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = pinError != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedBorderColor = BrandPrimary,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = DarkTextPrimary,
                            unfocusedTextColor = DarkTextPrimary,
                        ),
                    )
                    pinError?.let {
                        Text(it, color = Color(0xFFF87171), fontSize = 12.scaledSp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (settingsViewModel.verifyPin(pin)) {
                            showPin = false
                            val verifiedPin = pin
                            pin = ""
                            save(verifiedPin)
                        } else {
                            pinError = "Incorrect defense password."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                ) {
                    Text("Verify and save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPin = false
                    pin = ""
                    pinError = null
                }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }
}

@Composable
private fun AlwaysOnAppRow(
    app: InstalledAppInfo,
    checked: Boolean,
    vpnEnabled: Boolean,
    onToggle: () -> Unit,
    onToggleVpn: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCard)
            .border(
                1.dp,
                if (checked) BrandPrimary.copy(alpha = 0.35f) else DarkBorder,
                RoundedCornerShape(10.dp),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppIcon(app.icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    fontSize = 13.scaledSp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    fontSize = 11.scaledSp,
                    color = DarkTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Styled rounded checkbox matching 3e_(3)
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (checked) BrandPrimary else Color.Transparent)
                    .border(
                        1.5.dp,
                        if (checked) BrandPrimary else DarkBorder,
                        RoundedCornerShape(6.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        if (checked) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                    .clickable(onClick = onToggleVpn)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = if (vpnEnabled) BrandPrimary else DarkTextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (vpnEnabled) "Network block (VPN): on" else "Add network block (VPN)",
                        fontSize = 13.scaledSp,
                        fontWeight = if (vpnEnabled) FontWeight.Medium else FontWeight.Normal,
                        color = if (vpnEnabled) BrandPrimary else DarkTextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    FocusFlowSwitch(
                        checked = vpnEnabled,
                        onCheckedChange = { onToggleVpn() },
                    )
                }
            }
        }
    }
}


private fun legacyPinHash(pin: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(pin.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
