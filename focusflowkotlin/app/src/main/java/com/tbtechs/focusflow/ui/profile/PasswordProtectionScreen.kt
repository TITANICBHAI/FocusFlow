package com.tbtechs.focusflow.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.domain.FocusPinManager
import com.tbtechs.focusflow.ui.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordProtectionScreen(
    settingsViewModel: SettingsViewModel,
    focusPinManager: FocusPinManager,
    onBack: () -> Unit,
) {
    val settings by settingsViewModel.settings.collectAsState()
    var focusSet by remember { mutableStateOf(focusPinManager.isPinSet()) }
    var modal by remember { mutableStateOf<PasswordModal?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        focusSet = focusPinManager.isPinSet()
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Password Protection") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Focus Session Password controls ending a focus session. Defense Password controls disabling protection and removing restrictions.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            PasswordCard(
                title = "Focus Session Password",
                isSet = focusSet,
                description = if (focusSet) "Set — required to end an active focus session" else "Not set — focus sessions can be ended freely",
                onSet = { modal = PasswordModal.Setup(PinType.FOCUS) },
                onChange = { modal = PasswordModal.Verify(PinType.FOCUS, "Verify Current Password") },
                onRemove = { modal = PasswordModal.Verify(PinType.FOCUS, "Remove Focus Session Password") },
            )
            PasswordCard(
                title = "Defense Password",
                isSet = settings.pinProtectionEnabled,
                description = if (settings.pinProtectionEnabled) "Set — required before disabling protection" else "Not set — protection settings can be changed freely",
                onSet = { modal = PasswordModal.Setup(PinType.DEFENSE) },
                onChange = { modal = PasswordModal.Verify(PinType.DEFENSE, "Verify Current Password") },
                onRemove = { modal = PasswordModal.Verify(PinType.DEFENSE, "Remove Defense Password") },
            )
            notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    when (val active = modal) {
        is PasswordModal.Setup -> PinSetupModal(
            visible = true,
            pinType = active.pinType,
            onSaved = { raw ->
                if (active.pinType == PinType.FOCUS) {
                    focusPinManager.setPin(raw)
                    refresh()
                } else {
                    settingsViewModel.setPin(raw)
                }
                true
            },
            onCancel = { modal = null },
        )
        is PasswordModal.Verify -> PinVerifyModal(
            visible = true,
            pinType = active.pinType,
            title = active.title,
            verify = { raw -> if (active.pinType == PinType.FOCUS) focusPinManager.verifyPin(raw) else settingsViewModel.verifyPin(raw) },
            onVerified = { verifiedPin ->
                modal = if (active.title.startsWith("Remove")) {
                    PasswordModal.Remove(active.pinType, verifiedPin)
                } else {
                    PasswordModal.Setup(active.pinType)
                }
            },
            onCancel = { modal = null },
        )
        is PasswordModal.Remove -> {
            AlertDialog(
                onDismissRequest = { modal = null },
                title = { Text("Remove ${active.pinType.label} Password?") },
                text = { Text("This removes the password gate. Protection settings will no longer require it.") },
                confirmButton = {
                    Button(onClick = {
                        if (active.pinType == PinType.FOCUS) {
                            focusPinManager.clearPin(active.verifiedPin)
                            notice = null
                        } else {
                            settingsViewModel.clearPin()
                            notice = null
                        }
                        modal = null
                        refresh()
                    }) { Text("Remove") }
                },
                dismissButton = { OutlinedButton(onClick = { modal = null }) { Text("Cancel") } },
            )
        }
        null -> Unit
    }
}

private sealed interface PasswordModal {
    data class Setup(val pinType: PinType) : PasswordModal
    data class Verify(val pinType: PinType, val title: String) : PasswordModal
    data class Remove(val pinType: PinType, val verifiedPin: String) : PasswordModal
}

@Composable
private fun PasswordCard(
    title: String,
    description: String,
    isSet: Boolean,
    onSet: () -> Unit,
    onChange: () -> Unit,
    onRemove: () -> Unit,
) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, color = if (isSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isSet) Button(onClick = onSet) { Text("Set Password") }
                else {
                    OutlinedButton(onClick = onChange) { Text("Change") }
                    OutlinedButton(onClick = onRemove) { Text("Remove") }
                }
            }
        }
    }
}

private val PinType.label: String
    get() = if (this == PinType.FOCUS) "Focus Session" else "Defense"