package com.tbtechs.focusflow.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tbtechs.focusflow.data.repository.UsageStatsRepository
import com.tbtechs.focusflow.ui.home.FocusFlowModalCard
import com.tbtechs.focusflow.ui.home.FocusFlowPrimaryButton
import com.tbtechs.focusflow.ui.home.FocusFlowSecondaryButton
import com.tbtechs.focusflow.ui.home.RefRed
import com.tbtechs.focusflow.ui.home.RefSecondary
import com.tbtechs.focusflow.ui.home.RefText
import com.tbtechs.focusflow.ui.theme.scaledSp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var dismissed by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val currentStage by rememberUpdatedState(stage)
    val currentDismissed by rememberUpdatedState(dismissed)

    DisposableEffect(owner, accessibilityAttempted) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (accessibilityAttempted) leftAfterAttempt = true
                Lifecycle.Event.ON_RESUME -> if (leftAfterAttempt) {
                    leftAfterAttempt = false
                    scope.launch {
                        val repository = UsageStatsRepository(context)
                        val stageAtReturn = currentStage
                        val (granted, stillRestricted) = withContext(Dispatchers.IO) {
                            val enabled = repository.hasAccessibilityPermission()
                            enabled to if (!enabled && stageAtReturn == RecoveryStage.CHECKING) {
                                repository.isRestrictedSettingsBlocked()
                            } else {
                                false
                            }
                        }
                        if (currentDismissed) return@launch
                        if (granted) {
                            completed = true
                        } else {
                            returned = true
                            when (stageAtReturn) {
                                RecoveryStage.CHECKING -> {
                                    restricted = stillRestricted
                                    stage = if (stillRestricted) RecoveryStage.APP_INFO else RecoveryStage.ENABLE
                                }
                                RecoveryStage.GREYED_ENTRY -> greyedReturned = true
                                RecoveryStage.FALLBACK -> {
                                    fallbackReturned = true
                                    stage = RecoveryStage.APP_INFO
                                }
                                else -> Unit
                            }
                        }
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
    fun dismissRecovery() {
        dismissed = true
        completed = true
        stage = RecoveryStage.SKIPPED
        onDismiss()
    }

    Dialog(
        onDismissRequest = ::dismissRecovery,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        FocusFlowModalCard(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            radius = 16.dp,
            contentPadding = 16.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = RefText, modifier = Modifier.size(22.dp))
                    Text("Accessibility recovery", modifier = Modifier.padding(start = 8.dp), color = RefText, fontSize = 18.scaledSp)
                }
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Dismiss",
                    tint = RefSecondary,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = ::dismissRecovery)
                        .padding(8.dp),
                )
            }
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Some Android installs need one extra step before FocusFlow can enable Accessibility.",
                    color = RefSecondary,
                    fontSize = 13.scaledSp,
                    lineHeight = 17.scaledSp,
                )
                when (stage) {
                    RecoveryStage.QUESTION -> {
                        Text("Did you tap the greyed-out FocusFlow entry in Accessibility settings?", color = RefText, fontSize = 13.scaledSp)
                        FocusFlowPrimaryButton(text = "Yes, I tapped it", onClick = { stage = RecoveryStage.APP_INFO })
                        FocusFlowSecondaryButton(text = "No, I haven't tapped it", onClick = { stage = RecoveryStage.GREYED_ENTRY })
                        FocusFlowSecondaryButton(text = "Skip Accessibility", onClick = ::dismissRecovery)
                    }
                    RecoveryStage.GREYED_ENTRY -> {
                        Text("Open Accessibility settings, tap the greyed-out FocusFlow entry, read Android's explanation, then return here.", color = RefSecondary, fontSize = 13.scaledSp, lineHeight = 17.scaledSp)
                        if (!greyedReturned) {
                            FocusFlowPrimaryButton(text = "Open Accessibility Settings", icon = Icons.Outlined.OpenInNew, onClick = { openAccessibility(RecoveryStage.GREYED_ENTRY) })
                        } else {
                            Text("Welcome back. Continue when you have tapped the greyed-out entry.", color = RefText, fontSize = 13.scaledSp)
                            FocusFlowPrimaryButton(text = "I'm done — continue", onClick = { stage = RecoveryStage.APP_INFO })
                            FocusFlowSecondaryButton(text = "Open settings again", onClick = { openAccessibility(RecoveryStage.GREYED_ENTRY) })
                        }
                    }
                    RecoveryStage.APP_INFO -> {
                        Text("In App Info, tap the three-dot menu and choose Allow restricted settings.", color = RefSecondary, fontSize = 13.scaledSp, lineHeight = 17.scaledSp)
                        if (restricted) Text("Restricted settings are still blocked. Finish the steps, then check again.", color = RefRed, fontSize = 11.scaledSp)
                        FocusFlowPrimaryButton(text = "Open FocusFlow App Info", icon = Icons.Outlined.OpenInNew, onClick = ::openAppInfo)
                        Text("1. Open the three-dot menu\n2. Tap Allow restricted settings\n3. Return and check again", color = RefSecondary, fontSize = 11.scaledSp, lineHeight = 17.scaledSp)
                        if (!fallbackExpanded) {
                            FocusFlowSecondaryButton(text = "Didn't tap the greyed-out entry?", onClick = { fallbackExpanded = true })
                        } else {
                            Text("Open Accessibility settings, tap the greyed-out FocusFlow entry, then come back.", color = RefSecondary, fontSize = 11.scaledSp, lineHeight = 17.scaledSp)
                            if (!fallbackReturned) {
                                FocusFlowPrimaryButton(text = "Open Accessibility Settings", icon = Icons.Outlined.OpenInNew, onClick = {
                                    fallbackReturned = false
                                    openAccessibility(RecoveryStage.FALLBACK)
                                })
                            } else {
                                FocusFlowSecondaryButton(text = "I tapped it — check again", icon = Icons.Outlined.CheckCircle, onClick = {
                                    fallbackReturned = false
                                    stage = RecoveryStage.CHECKING
                                    scope.launch {
                                        restricted = UsageStatsRepository(context).isRestrictedSettingsBlocked()
                                        stage = if (restricted) RecoveryStage.APP_INFO else RecoveryStage.ENABLE
                                    }
                                })
                            }
                        }
                        FocusFlowSecondaryButton(text = "I'm done — check settings", onClick = {
                            stage = RecoveryStage.CHECKING
                            scope.launch {
                                restricted = UsageStatsRepository(context).isRestrictedSettingsBlocked()
                                stage = if (restricted) RecoveryStage.APP_INFO else RecoveryStage.ENABLE
                            }
                        })
                    }
                    RecoveryStage.CHECKING -> {
                        CircularProgressIndicator(color = RefText, modifier = Modifier.size(22.dp))
                        Text("Checking Android settings…", color = RefSecondary, fontSize = 13.scaledSp)
                    }
                    RecoveryStage.ENABLE -> {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFF34D399), modifier = Modifier.size(22.dp))
                            Text("Restricted settings are ready.", modifier = Modifier.padding(start = 8.dp), color = RefText, fontSize = 13.scaledSp)
                        }
                        Text("One last step: return to Accessibility settings and enable FocusFlow.", color = RefSecondary, fontSize = 13.scaledSp)
                        FocusFlowPrimaryButton(text = "Open Accessibility Settings", icon = Icons.Outlined.OpenInNew, onClick = { openAccessibility(RecoveryStage.ENABLE) })
                        Text("The recovery stays open until Accessibility is actually enabled.", color = RefSecondary, fontSize = 11.scaledSp)
                    }
                    RecoveryStage.FALLBACK, RecoveryStage.SKIPPED -> Unit
                }
            }
        }
    }
}
