package com.tbtechs.focusflow.ui.alwayson

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tbtechs.focusflow.data.repository.NetworkBlockStatus
import com.tbtechs.focusflow.data.repository.VpnRepository
import com.tbtechs.focusflow.enforcement.NetworkBlockerVpnService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Root-level recovery banner for a configured VPN policy whose permission or
 * tunnel health has been lost. It deliberately has no dismiss action: it hides
 * only after the policy is healthy or no longer configured.
 */
@Composable
fun VpnPermissionLostBanner(
    vpnBlockEnabled: Boolean,
    vpnPackages: List<String>,
    vpnRepository: VpnRepository,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var permissionLost by remember { mutableStateOf(false) }
    var regranting by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<NetworkBlockStatus?>(null) }

    suspend fun check() {
        if (!vpnBlockEnabled || vpnPackages.isEmpty()) {
            permissionLost = false
            status = null
            return
        }

        try {
            val granted = vpnRepository.isVpnPermissionGranted()
            val nextStatus = vpnRepository.getNetworkBlockStatus()
            status = nextStatus
            val needsAttention = !granted || nextStatus.state in setOf(
                NetworkBlockerVpnService.STATUS_PERMISSION_MISSING,
                NetworkBlockerVpnService.STATUS_ANOTHER_VPN,
                NetworkBlockerVpnService.STATUS_STARTUP_FAILED,
                NetworkBlockerVpnService.STATUS_PACKAGE_FAILURE,
            )
            permissionLost = needsAttention

            // Consent restoration and tunnel restoration are separate steps.
            if (granted &&
                nextStatus.state == NetworkBlockerVpnService.STATUS_PERMISSION_MISSING
            ) {
                runCatching {
                    vpnRepository.startNetworkBlock(vpnPackages)
                }
                val afterStart = vpnRepository.getNetworkBlockStatus()
                status = afterStart
                permissionLost = afterStart.state in setOf(
                    NetworkBlockerVpnService.STATUS_STARTUP_FAILED,
                    NetworkBlockerVpnService.STATUS_PACKAGE_FAILURE,
                    NetworkBlockerVpnService.STATUS_ANOTHER_VPN,
                    NetworkBlockerVpnService.STATUS_PERMISSION_MISSING,
                )
            }
        } catch (_: Exception) {
            status = null
            permissionLost = true
        }
    }

    LaunchedEffect(vpnBlockEnabled, vpnPackages) {
        check()
    }

    DisposableEffect(lifecycleOwner, vpnBlockEnabled, vpnPackages) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { check() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun handleRegrant() {
        scope.launch {
            regranting = true
            try {
                vpnRepository.requestVpnPermission(activity)
                // Allow the system consent Activity to finish before reading
                // VpnService.prepare() and the native health snapshot again.
                delay(800)
                check()
            } catch (_: Exception) {
                permissionLost = true
            } finally {
                regranting = false
            }
        }
    }

    if (!vpnBlockEnabled || vpnPackages.isEmpty()) return

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = permissionLost,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            statusTitle(status),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            statusMessage(status),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = ::handleRegrant,
                        enabled = !regranting,
                    ) {
                        if (regranting) {
                            CircularProgressIndicator()
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                            Text("Restore VPN")
                        }
                    }
                }
            }
        }
    }
}

private fun statusTitle(status: NetworkBlockStatus?): String =
    when (status?.state) {
        NetworkBlockerVpnService.STATUS_ANOTHER_VPN -> "Another VPN is active"
        NetworkBlockerVpnService.STATUS_PACKAGE_FAILURE -> "VPN app list needs attention"
        NetworkBlockerVpnService.STATUS_STARTUP_FAILED -> "VPN failed to start"
        else -> "VPN permission required"
    }

private fun statusMessage(status: NetworkBlockStatus?): String =
    when (status?.state) {
        NetworkBlockerVpnService.STATUS_ANOTHER_VPN ->
            "Android allows one VPN at a time. Stop the other VPN, then retry."
        NetworkBlockerVpnService.STATUS_PACKAGE_FAILURE -> {
            val count = status?.failedPackages?.size ?: 0
            "$count selected app${if (count == 1) "" else "s"} could not be registered."
        }
        NetworkBlockerVpnService.STATUS_STARTUP_FAILED ->
            "Android rejected VPN startup. Check app permissions, then retry."
        else -> "Network blocking is enabled, but Android needs VPN consent again."
    }