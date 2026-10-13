package com.tbtechs.focusflow.ui.defense

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tbtechs.focusflow.data.repository.InstalledAppInfo
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.data.repository.NuclearModeRepository
import com.tbtechs.focusflow.enforcement.receivers.FocusDayDeviceAdminReceiver
import com.tbtechs.focusflow.ui.common.rememberInstalledApps
import com.tbtechs.focusflow.ui.launcher.AppIcon
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
import com.tbtechs.focusflow.ui.theme.scaledSp
import kotlinx.coroutines.launch

/**
 * Nuclear Mode modal for uninstallation of distracting apps.
 *
 * Implements screenshot 3e_13 with dark theme styling, high-contrast warning cards,
 * and system uninstall triggers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuclearModeModal(
    visible: Boolean,
    blockedPackages: List<String>,
    onClose: () -> Unit,
) {
    if (!visible) return
    val dimensions = LocalFocusFlowDimensions.current
    val context = LocalContext.current
    var appCatalogRefreshToken by remember { mutableStateOf(0) }
    var hasPausedSinceVisible by remember { mutableStateOf(false) }
    val installedAppsState = rememberInstalledApps(
        remember(context) { InstalledAppsRepository(context) },
        refreshToken = appCatalogRefreshToken,
    )
    val apps = remember(installedAppsState.apps, blockedPackages) {
        installedAppsState.apps
            .filter { it.packageName in blockedPackages }
            .sortedBy { it.appName.lowercase() }
    }
    val loading = installedAppsState.loading
    var pending by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val devicePolicyManager = remember(context) {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
    }
    val adminComponent = remember(context) {
        ComponentName(context, FocusDayDeviceAdminReceiver::class.java)
    }
    var deviceAdminActive by remember(context) {
        mutableStateOf(devicePolicyManager?.isAdminActive(adminComponent) == true)
    }
    LaunchedEffect(visible, blockedPackages) {
        deviceAdminActive = devicePolicyManager?.isAdminActive(adminComponent) == true
    }

    DisposableEffect(lifecycleOwner, visible, blockedPackages) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> hasPausedSinceVisible = true
                Lifecycle.Event.ON_RESUME -> if (visible) {
                    deviceAdminActive = devicePolicyManager?.isAdminActive(adminComponent) == true
                    if (hasPausedSinceVisible) {
                        hasPausedSinceVisible = false
                        appCatalogRefreshToken += 1
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            val maxDialogHeight = maxHeight * 0.92f
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkCard)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
            ) {
            // Header matching 3e_13
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.Block,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = "Nuclear Mode",
                        fontSize = 15.scaledSp,
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Clear, contentDescription = "Close", tint = DarkTextSecondary)
                }
            }

            // Warning Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF450A0A))
                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Permanent action",
                            fontSize = 15.scaledSp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF87171),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Uninstalling removes all local data and accounts for that app. Android will display a confirmation dialog before proceeding.",
                            fontSize = 12.scaledSp,
                            lineHeight = 16.scaledSp,
                            color = Color(0xFFFECACA),
                        )
                    }
                }
            }

            if (deviceAdminActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF451A03))
                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                ) {
                    Text(
                        "Device Admin is active. Android may prevent uninstalling some protected apps; if needed, temporarily revoke the admin permission.",
                        color = Color(0xFFFBBF24),
                        fontSize = 12.scaledSp,
                        lineHeight = 16.scaledSp,
                    )
                }
            }

            Text(
                text = "APPS FROM YOUR BLOCK LIST",
                fontSize = 12.scaledSp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.scaledSp,
                color = DarkTextSecondary,
            )

            if (loading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = Color(0xFFEF4444), modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Loading blocked apps…", color = DarkTextSecondary, fontSize = 13.scaledSp)
                }
            } else if (installedAppsState.error != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurfaceVariant)
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        installedAppsState.error.message
                            ?: "FocusFlow couldn't read the installed app list.",
                        color = Color(0xFFEF4444),
                        fontSize = 13.scaledSp,
                    )
                }
            } else if (apps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurfaceVariant)
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "No blocked apps installed",
                            fontSize = 15.scaledSp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkTextPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (blockedPackages.isEmpty()) {
                                "Add apps to your block lists first, then return here."
                            } else {
                                "All apps in your block lists have already been uninstalled."
                            },
                            fontSize = 12.scaledSp,
                            color = DarkTextSecondary,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceVariant)
                                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                                .padding(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AppIcon(app.icon)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        fontSize = 14.scaledSp,
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
                                Button(
                                    onClick = { pending = app },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    modifier = Modifier.defaultMinSize(minHeight = 38.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Uninstall", fontSize = 12.scaledSp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            error?.let { message ->
                Text(message, color = Color(0xFFEF4444), fontSize = 12.scaledSp)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = DarkTextMuted, modifier = Modifier.size(14.dp))
                Text(
                    "To add apps here, add them to your Standalone Block or Always-On lists.",
                    fontSize = 11.scaledSp,
                    color = DarkTextMuted,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    pending?.let { app ->
        AlertDialog(
            onDismissRequest = { pending = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Uninstall ${app.appName}?", fontWeight = FontWeight.Bold) },
            text = { Text("Android will open its system uninstallation prompt for confirmation.", fontSize = 13.scaledSp, lineHeight = 18.scaledSp) },
            confirmButton = {
                Button(
                    onClick = {
                        pending = null
                        val repository = NuclearModeRepository(context)
                        scope.launch {
                            runCatching { repository.requestUninstallApp(app.packageName) }
                                .onFailure {
                                    error = it.message ?: "Android could not open the uninstall dialog."
                                }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text("Uninstall", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pending = null },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }
}
