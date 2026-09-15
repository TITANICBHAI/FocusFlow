package com.tbtechs.focusflow.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tbtechs.focusflow.data.repository.UsageStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private enum class RecoveryStage { QUESTION, GREYED_ENTRY, APP_INFO, FALLBACK, ENABLE, CHECKING, SKIPPED }

@Composable
fun AccessibilityRestrictedRecovery(
    accessibilityAttempted: Boolean,
    onDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var leftAfterAttempt by remember { mutableStateOf(false) }
    var returned by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf(RecoveryStage.QUESTION) }
    var greyedReturned by remember { mutableStateOf(false) }
    var fallbackReturned by remember { mutableStateOf(false) }
    var fallbackExpanded by remember { mutableStateOf(false) }
    var restricted by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val currentStage by rememberUpdatedState(stage)

    DisposableEffect(owner, accessibilityAttempted) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (accessibilityAttempted) leftAfterAttempt = true
                Lifecycle.Event.ON_RESUME -> if (leftAfterAttempt) {
                    returned = true
                    leftAfterAttempt = false
                    if (currentStage == RecoveryStage.CHECKING) {
                        scope.launch {
                            val granted = UsageStatsRepository(context).hasAccessibilityPermission()
                            val stillRestricted = UsageStatsRepository(context).isRestrictedSettingsBlocked()
                            if (granted) completed = true
                            else {
                                restricted = stillRestricted
                                stage = if (stillRestricted) RecoveryStage.APP_INFO else RecoveryStage.ENABLE
                            }
                        }
                    } else if (currentStage == RecoveryStage.GREYED_ENTRY) {
                        greyedReturned = true
                    } else if (currentStage == RecoveryStage.FALLBACK) {
                        fallbackReturned = true
                        stage = RecoveryStage.APP_INFO
                    }
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    if (!accessibilityAttempted || !returned || completed || stage == RecoveryStage.SKIPPED) return

    fun openAccessibility(pendingStage: RecoveryStage) {
        stage = if (pendingStage == RecoveryStage.ENABLE) RecoveryStage.CHECKING else pendingStage
        scope.launch { UsageStatsRepository(context).openAccessibilitySettings() }
    }
    fun openAppInfo() {
        stage = RecoveryStage.APP_INFO
        scope.launch { UsageStatsRepository(context).openAppInfoSettings() }
    }

    AlertDialog(
        onDismissRequest = {
            completed = true
            stage = RecoveryStage.SKIPPED
            onDismiss()
        },
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row {
                    Icon(Icons.Outlined.Lock, contentDescription = null)
                    Text(" Accessibility recovery", modifier = Modifier.padding(start = 8.dp))
                }
                IconButton(onClick = {
                    completed = true
                    stage = RecoveryStage.SKIPPED
                    onDismiss()
                }) { Icon(Icons.Outlined.Close, "Dismiss") }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Some Android installs need one extra step before FocusFlow can enable Accessibility.")
                when (stage) {
                    RecoveryStage.QUESTION -> {
                        Text("Did you tap the greyed-out FocusFlow entry in Accessibility settings?")
                        Button(onClick = { stage = RecoveryStage.APP_INFO }) { Text("Yes, I tapped it") }
                        OutlinedButton(onClick = { stage = RecoveryStage.GREYED_ENTRY }) { Text("No, I haven't tapped it") }
                        TextButton(onClick = {
                            completed = true
                            stage = RecoveryStage.SKIPPED
                            onDismiss()
                        }) { Text("Skip Accessibility") }
                    }
                    RecoveryStage.GREYED_ENTRY -> {
                        Text("Open Accessibility settings, tap the greyed-out FocusFlow entry, read Android's explanation, then return here.")
                        if (!greyedReturned) {
                            Button(onClick = { openAccessibility(RecoveryStage.GREYED_ENTRY) }) {
                                Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                                Text("Open Accessibility Settings")
                            }
                        } else {
                            Text("Welcome back. Continue when you have tapped the greyed-out entry.")
                            Button(onClick = { stage = RecoveryStage.APP_INFO }) { Text("I'm done — continue") }
                            TextButton(onClick = { openAccessibility(RecoveryStage.GREYED_ENTRY) }) { Text("Open settings again") }
                        }
                    }
                    RecoveryStage.APP_INFO -> {
                        Text("In App Info, tap the three-dot menu and choose Allow restricted settings.")
                        if (restricted) Text("Restricted settings are still blocked. Finish the steps, then check again.", color = MaterialTheme.colorScheme.error)
                        Button(onClick = ::openAppInfo) {
                            Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                            Text("Open FocusFlow App Info")
                        }
                        Text("1. Open the three-dot menu\n2. Tap Allow restricted settings\n3. Return and check again")
                        if (!fallbackExpanded) {
                            TextButton(onClick = { fallbackExpanded = true }) { Text("Didn't tap the greyed-out entry?") }
                        } else {
                            Text("Open Accessibility settings, tap the greyed-out FocusFlow entry, then come back.")
                            if (!fallbackReturned) {
                                Button(onClick = {
                                    fallbackReturned = false
                                    openAccessibility(RecoveryStage.FALLBACK)
                                }) {
                                    Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                                    Text("Open Accessibility Settings")
                                }
                            } else {
                                OutlinedButton(onClick = {
                                    fallbackReturned = false
                                    stage = RecoveryStage.CHECKING
                                    scope.launch {
                                        restricted = UsageStatsRepository(context).isRestrictedSettingsBlocked()
                                        stage = if (restricted) RecoveryStage.APP_INFO else RecoveryStage.ENABLE
                                    }
                                }) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                                    Text("I tapped it — check again")
                                }
                            }
                        }
                        OutlinedButton(onClick = {
                            stage = RecoveryStage.CHECKING
                            scope.launch {
                                restricted = UsageStatsRepository(context).isRestrictedSettingsBlocked()
                                stage = if (restricted) RecoveryStage.APP_INFO else RecoveryStage.ENABLE
                            }
                        }) { Text("I'm done — check settings") }
                    }
                    RecoveryStage.CHECKING -> {
                        CircularProgressIndicator()
                        Text("Checking Android settings…")
                    }
                    RecoveryStage.ENABLE -> {
                        Row {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(" Restricted settings are ready.", modifier = Modifier.padding(start = 8.dp))
                        }
                        Text("One last step: return to Accessibility settings and enable FocusFlow.")
                        Button(onClick = { openAccessibility(RecoveryStage.ENABLE) }) {
                            Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                            Text("Open Accessibility Settings")
                        }
                        Text("The recovery stays open until Accessibility is actually enabled.")
                    }
                }
            }
        },
        confirmButton = {},
    )
}
