package com.tbtechs.focusflow.ui.onboarding

import android.Manifest
import android.content.Intent
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.permissions.AccessibilityRestrictedRecovery
import com.tbtechs.focusflow.ui.permissions.PermissionCard
import com.tbtechs.focusflow.ui.permissions.PermissionDefinition
import com.tbtechs.focusflow.ui.permissions.PermissionId
import com.tbtechs.focusflow.ui.permissions.PermissionStatus
import com.tbtechs.focusflow.ui.permissions.checkPermission
import com.tbtechs.focusflow.ui.permissions.openPermissionSettings
import com.tbtechs.focusflow.ui.permissions.permissionDefinitions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class OnboardingStep { CORE, OPTIONAL }

@Composable
fun OnboardingScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    onFinished: () -> Unit = {},
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val settings by settingsViewModel.settings.collectAsStateCompat()
    var step by remember { mutableStateOf(OnboardingStep.CORE) }
    var statuses by remember { mutableStateOf<Map<PermissionId, PermissionStatus>>(emptyMap()) }
    var expanded by remember { mutableStateOf<PermissionId?>(null) }
    var loading by remember { mutableStateOf<PermissionId?>(null) }
    var accessibilityAttempted by remember { mutableStateOf(false) }
    var pinChoice by remember { mutableStateOf(false) }
    var pinDialog by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }

    val refresh: () -> Unit = {
        scope.launch {
            statuses = withContext(Dispatchers.IO) {
                permissionDefinitions.associate { it.id to checkPermission(context, it.id) }
            }
        }
    }
    LaunchedEffect(Unit) { refresh() }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    fun grant(permission: PermissionDefinition) {
        if (statuses[permission.id] == PermissionStatus.GRANTED) return
        loading = permission.id
        when (permission.id) {
            PermissionId.MEDIA -> {
                val manifestPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
                permissionLauncher.launch(manifestPermission)
                loading = null
            }
            PermissionId.VPN -> {
                val intent = VpnService.prepare(context)
                if (intent == null) refresh() else vpnLauncher.launch(intent)
                loading = null
            }
            PermissionId.NOTIFICATIONS -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    scope.launch { openPermissionSettings(context, permission.id) }
                }
                loading = null
            }
            PermissionId.ACCESSIBILITY -> {
                accessibilityAttempted = true
                scope.launch { openPermissionSettings(context, permission.id); loading = null }
            }
            else -> scope.launch { openPermissionSettings(context, permission.id); loading = null }
        }
    }

    val core = permissionDefinitions.filterNot { it.optional }
    val optional = permissionDefinitions.filter { it.optional }
    val requiredReady = core.filter {
        it.id == PermissionId.ACCESSIBILITY || it.id == PermissionId.USAGE || it.id == PermissionId.NOTIFICATIONS
    }.count { statuses[it.id] == PermissionStatus.GRANTED }
    val requiredTotal = 3
    val optionalReady = optional.count { statuses[it.id] == PermissionStatus.GRANTED }

    Scaffold(topBar = { TopAppBar(title = { Text(if (step == OnboardingStep.CORE) "Set up core access" else "Optional protection") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    if (step == OnboardingStep.CORE) "These permissions help FocusFlow block reliably."
                    else "Add extra protection now or come back later.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (step == OnboardingStep.CORE) {
                item {
                    Card {
                        Row(modifier = Modifier.padding(16.dp)) {
                            Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text("Why these permissions?", style = MaterialTheme.typography.titleMedium)
                                Text("FocusFlow enforces focus at the system level, not just with reminders. Android requires special access for reliable blocking.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item {
                    Text("Required access ready: $requiredReady / $requiredTotal")
                    LinearProgressIndicator(
                        progress = { requiredReady.toFloat() / requiredTotal.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (requiredReady == requiredTotal) Text("Core blocking access is ready.", color = MaterialTheme.colorScheme.primary)
                }
                item { Text("CORE ACCESS", style = MaterialTheme.typography.labelLarge) }
                items(core, key = { it.id }) { permission ->
                    PermissionCard(
                        permission = permission,
                        status = statuses[permission.id] ?: PermissionStatus.UNKNOWN,
                        expanded = expanded == permission.id,
                        busy = loading == permission.id,
                        onToggle = {
                            if (statuses[permission.id] != PermissionStatus.GRANTED) grant(permission)
                            else expanded = if (expanded == permission.id) null else permission.id
                        },
                        onGrant = { grant(permission) },
                    )
                }
                item {
                    AccessibilityRestrictedRecovery(accessibilityAttempted = accessibilityAttempted)
                }
                item {
                    Card {
                        Row(modifier = Modifier.padding(12.dp)) {
                            Icon(Icons.Outlined.Info, contentDescription = null)
                            Text("Usage Access, Accessibility Service, and Notifications can also be fixed later in Settings → Permissions.", modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                item {
                    TextButton(onClick = { step = OnboardingStep.CORE }) { Text("← Back to core setup") }
                    Text("OPTIONAL SETUP", style = MaterialTheme.typography.labelLarge)
                    Text("These features are not required to use FocusFlow and can be configured later.", style = MaterialTheme.typography.bodySmall)
                }
                items(optional, key = { it.id }) { permission ->
                    PermissionCard(
                        permission = permission,
                        status = statuses[permission.id] ?: PermissionStatus.UNKNOWN,
                        expanded = expanded == permission.id,
                        busy = loading == permission.id,
                        onToggle = {
                            if (statuses[permission.id] != PermissionStatus.GRANTED) grant(permission)
                            else expanded = if (expanded == permission.id) null else permission.id
                        },
                        onGrant = { grant(permission) },
                    )
                }
                item {
                    Card {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Icon(Icons.Outlined.Lock, contentDescription = null)
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                Text("PIN Protection", style = MaterialTheme.typography.titleMedium)
                                Text("Require a password before block protections can be disabled.", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = pinChoice, onCheckedChange = {
                                pinChoice = it
                                if (it && !settings.pinProtectionEnabled) pinDialog = true
                            })
                        }
                        if (pinChoice && settings.pinProtectionEnabled) Text("Defense Password set — your protections are locked.", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.primary)
                        if (pinChoice && !settings.pinProtectionEnabled) Text("Set your Defense Password now, or add it later from Settings.", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        if (step == OnboardingStep.CORE) {
                            step = OnboardingStep.OPTIONAL
                        } else {
                            settingsViewModel.updateSettings(settings.copy(pinProtectionEnabled = pinChoice))
                            context.getSharedPreferences("focusflow", 0).edit().putBoolean("onboarding_complete", true).apply()
                            onFinished()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (step == OnboardingStep.CORE) "Continue to optional setup →" else "$optionalReady optional permissions enabled — let's start")
                }
            }
            item {
                Text(
                    if (step == OnboardingStep.CORE) "You can manage permissions in Settings at any time." else "Optional features can be enabled later from Settings.",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (pinDialog) {
        AlertDialog(
            onDismissRequest = { pinDialog = false },
            title = { Text("Set Defense Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(pin, { pin = it }, label = { Text("Password") }, singleLine = true)
                    OutlinedTextField(pinConfirm, { pinConfirm = it }, label = { Text("Confirm password") }, singleLine = true)
                    if (pin.isNotEmpty() && pin != pinConfirm) Text("Passwords do not match", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (pin.length >= 4 && pin == pinConfirm) {
                        settingsViewModel.setPin(pin)
                        pinChoice = true
                        pinDialog = false
                        pin = ""
                        pinConfirm = ""
                    }
                }, enabled = pin.length >= 4 && pin == pinConfirm) { Text("Set Password") }
            },
            dismissButton = { TextButton(onClick = { pinChoice = false; pinDialog = false }) { Text("Set later") } },
        )
    }
}

@Composable
private fun <T> androidx.lifecycle.compose.collectAsStateCompat(flow: kotlinx.coroutines.flow.StateFlow<T>): androidx.compose.runtime.State<T> =
    androidx.compose.runtime.collectAsState(flow)
