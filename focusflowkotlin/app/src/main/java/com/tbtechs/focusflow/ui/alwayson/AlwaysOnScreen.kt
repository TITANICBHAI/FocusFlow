package com.tbtechs.focusflow.ui.alwayson

import android.app.Activity
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShieldMoon
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import com.tbtechs.focusflow.data.repository.InstalledAppInfo
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.data.repository.NetworkBlockSettings
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.data.repository.VpnRepository
import com.tbtechs.focusflow.ui.FocusSessionViewModel
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.launcher.AppIcon
import kotlinx.coroutines.launch
import java.security.MessageDigest

private object AlwaysOnDimensions {
    val pagePadding = androidx.compose.ui.unit.Dp(20f)
    val itemPadding = androidx.compose.ui.unit.Dp(12f)
    val gap = androidx.compose.ui.unit.Dp(12f)
    val smallGap = androidx.compose.ui.unit.Dp(6f)
    val icon = androidx.compose.ui.unit.Dp(42f)
    val radius = androidx.compose.ui.unit.Dp(16f)
}

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
    "com.google.android.gms",
    "com.android.packageinstaller",
    "com.google.android.packageinstaller",
    "com.samsung.android.packageinstaller",
    "com.samsung.android.wallet",
    "com.samsung.android.samsungpay",
    "com.google.android.apps.walletnfcrel",
    "com.tbtechs.focusflow",
)

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
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val settings by settingsViewModel.settings.collectAsState()
    val focusSession by focusSessionViewModel.focusSession.collectAsState()

    var apps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var networkSettings by remember { mutableStateOf<NetworkBlockSettings?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var vpnSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var originalSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var originalVpnSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPin by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var showConsent by remember { mutableStateOf(false) }
    var clearConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loading = true
        val storedSettings = runCatching { settingsRepository.readAppSettings() }.getOrNull()
        val storedNetwork = runCatching { vpnRepository.getNetworkBlockSettings() }.getOrNull()
        apps = runCatching {
            installedAppsRepository.getInstalledApps()
                .filterNot { it.packageName in systemNeverBlock }
                .sortedBy { it.appName.lowercase() }
        }.getOrDefault(emptyList())
        networkSettings = storedNetwork
        selected = storedSettings?.alwaysBlockPackages?.toSet().orEmpty()
        vpnSelected = storedNetwork?.packages?.toSet().orEmpty()
        originalSelected = selected
        originalVpnSelected = vpnSelected
        loading = false
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

                if (vpnSelected.isNotEmpty() && !vpnRepository.isVpnPermissionGranted()) {
                    showConsent = true
                    return@launch
                }

                vpnRepository.setNetworkBlockSettings(
                    currentNetwork.copy(
                        enabled = hasVpnPackages,
                        vpn = hasVpnPackages,
                        packages = vpnSelected.toList().sorted(),
                    ),
                    defensePinHash = defensePin?.let(::legacyPinHash),
                )
                vpnRepository.setVpnSelfHealEnabled(hasVpnPackages)
                settingsRepository.setAlwaysBlockActive(
                    active = selected.isNotEmpty(),
                    packages = selected.toList().sorted(),
                )

                // Keep the Compose settings snapshot aligned with the durable
                // SharedPreferences writes used by the enforcement services.
                settingsViewModel.updateSettings(
                    settings.copy(
                        alwaysBlockEnabled = selected.isNotEmpty(),
                        alwaysBlockPackages = selected.toList().sorted(),
                        networkBlockEnabled = hasVpnPackages,
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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Always-On Block List")
                        Text(
                            if (selected.isNotEmpty()) {
                                "${selected.size} app${if (selected.size == 1) "" else "s"} blocked 24/7"
                            } else {
                                "Tick apps to block them permanently — no timer"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        IconButton(
                            onClick = { clearConfirmation = true },
                            enabled = !saving,
                        ) {
                            Icon(Icons.Outlined.ClearAll, contentDescription = "Clear all")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = { save() },
                enabled = !loading && !saving,
                modifier = Modifier.fillMaxWidth().padding(AlwaysOnDimensions.pagePadding),
            ) {
                if (saving) CircularProgressIndicator()
                else {
                    Text(
                        if (selected.isEmpty()) "Save (no apps selected)"
                        else "Save ${selected.size} app${if (selected.size == 1) "" else "s"}",
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AlwaysOnDimensions.gap),
            ) {
                Row(
                    modifier = Modifier.padding(AlwaysOnDimensions.itemPadding),
                    horizontalArrangement = Arrangement.spacedBy(AlwaysOnDimensions.smallGap),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Outlined.ShieldMoon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "These apps are blocked continuously — no session or timer is needed. They stay blocked until you untick them here.\n\nRemoving apps requires your defense PIN when configured. Tap a blocked app to also enable network blocking (VPN).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AlwaysOnDimensions.gap),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (search.isNotBlank()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                placeholder = { Text("Search apps") },
            )
            error?.let {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = AlwaysOnDimensions.gap),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            when {
                loading -> Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Text("Loading apps…")
                }
                filteredApps.isEmpty() -> Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (search.isBlank()) "No apps found" else "No apps match your search",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AlwaysOnDimensions.smallGap),
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
                }
            }
        }
    }

    VpnConsentModal(
        visible = showConsent,
        onCancel = { showConsent = false },
        onConfirm = {
            showConsent = false
            scope.launch {
                try {
                    vpnRepository.requestVpnPermission(activity)
                    error = "Grant VPN permission, then tap Save again."
                } catch (exception: Exception) {
                    error = exception.message ?: "Could not open VPN consent."
                }
            }
        },
    )

    if (clearConfirmation) {
        AlertDialog(
            onDismissRequest = { clearConfirmation = false },
            title = { Text("Clear all?") },
            text = { Text("This removes all apps from the Always-On enforcement list. They will no longer be blocked.") },
            confirmButton = {
                Button(onClick = {
                    selected = emptySet()
                    vpnSelected = emptySet()
                    clearConfirmation = false
                }) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirmation = false }) { Text("Cancel") }
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
            title = { Text("Defense PIN required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AlwaysOnDimensions.smallGap)) {
                    Text("You are removing apps from the Always-On block list. Enter your defense PIN to confirm.")
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            pin = it
                            pinError = null
                        },
                        label = { Text("Defense PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = pinError != null,
                    )
                    pinError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (settingsViewModel.verifyPin(pin)) {
                        showPin = false
                        val verifiedPin = pin
                        pin = ""
                        save(verifiedPin)
                    } else {
                        pinError = "Incorrect defense PIN."
                    }
                }) { Text("Verify and save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPin = false
                    pin = ""
                    pinError = null
                }) { Text("Cancel") }
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
            .background(
                if (checked) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(AlwaysOnDimensions.radius),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(AlwaysOnDimensions.itemPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AlwaysOnDimensions.gap),
        ) {
            AppIcon(app.icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                if (checked) Icons.Outlined.Check else Icons.Outlined.Clear,
                contentDescription = if (checked) "Blocked" else "Not blocked",
                tint = if (checked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
            )
        }
        if (checked) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleVpn)
                    .padding(
                        horizontal = AlwaysOnDimensions.itemPadding,
                        vertical = AlwaysOnDimensions.smallGap,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AlwaysOnDimensions.smallGap),
            ) {
                Icon(
                    Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = if (vpnEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AlwaysOnDimensions.icon / 2),
                )
                Text(
                    if (vpnEnabled) "Network block (VPN): on"
                    else "Add network block (VPN)",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (vpnEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = vpnEnabled, onCheckedChange = { onToggleVpn() })
            }
        }
    }
}

private fun legacyPinHash(pin: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(pin.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }