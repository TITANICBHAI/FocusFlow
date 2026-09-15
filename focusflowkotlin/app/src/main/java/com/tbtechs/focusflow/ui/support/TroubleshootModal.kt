package com.tbtechs.focusflow.ui.support

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Dialog
import androidx.compose.material3.DialogProperties
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.ui.permissions.PermissionId

private data class TroubleshootBrand(
    val id: String,
    val label: String,
    val icon: String,
)

private val brands = listOf(
    TroubleshootBrand("samsung", "Samsung", "📱"),
    TroubleshootBrand("xiaomi", "Xiaomi / MIUI", "📱"),
    TroubleshootBrand("oneplus", "OnePlus", "📱"),
    TroubleshootBrand("realme", "Realme / Oppo", "📱"),
    TroubleshootBrand("stock", "Stock Android", "🤖"),
)

private val tips: Map<String, Map<PermissionId, List<String>>> = mapOf(
    "samsung" to mapOf(
        PermissionId.ACCESSIBILITY to listOf(
            "Go to Settings → Accessibility",
            "Tap Installed apps or Downloaded apps",
            "Find FocusFlow and toggle it ON",
            "Tap Allow on the confirmation popup",
            "If missing: Settings → General management → App info → FocusFlow → Accessibility",
        ),
        PermissionId.USAGE to listOf(
            "Go to Settings → Digital Wellbeing and parental controls",
            "OR Settings → Apps → ⋮ → Special access → Usage access",
            "Find FocusFlow and toggle it ON",
            "On One UI 5+: Settings → Privacy → Permission manager → Usage access",
        ),
        PermissionId.BATTERY to listOf(
            "Go to Settings → Battery and device care → Battery",
            "Tap Background usage limits → Never sleeping apps → Add FocusFlow",
            "Also disable Adaptive battery for FocusFlow in More battery settings",
            "If blocking still stops: Settings → Apps → FocusFlow → Battery → Unrestricted",
        ),
        PermissionId.NOTIFICATIONS to listOf(
            "Go to Settings → Notifications → App notifications",
            "Find FocusFlow and enable all notification channels",
            "Make sure the Persistent or Foreground service channel is ON",
        ),
        PermissionId.DEVICE_ADMIN to listOf(
            "Go to Settings → Biometrics and Security",
            "Open Device admin apps",
            "Find FocusFlow and tap Activate",
        ),
        PermissionId.OVERLAY to listOf(
            "Go to Settings → Apps → ⋮ → Special access → Appear on top",
            "Find FocusFlow and toggle it ON",
            "On One UI 5+: Settings → Apps → FocusFlow → Appear on top",
        ),
    ),
    "xiaomi" to mapOf(
        PermissionId.ACCESSIBILITY to listOf(
            "Go to Settings → Accessibility → Downloaded apps",
            "Find FocusFlow and enable it",
            "On MIUI 12+: Settings → Additional Settings → Accessibility → Downloaded apps",
            "If hidden, search for FocusFlow in Settings",
        ),
        PermissionId.USAGE to listOf(
            "Go to Settings → Apps → Manage apps",
            "Tap ⋮ → Special access → Usage access",
            "Find FocusFlow and toggle it ON",
            "On MIUI 12+: Settings → Privacy → Special app access → Usage data access",
        ),
        PermissionId.BATTERY to listOf(
            "Go to Settings → Apps → Manage apps → FocusFlow",
            "Tap Battery saver → No restrictions",
            "Enable Autostart for FocusFlow",
            "If needed, disable MIUI Optimization in Developer options as a last resort",
        ),
        PermissionId.NOTIFICATIONS to listOf(
            "Settings → Apps → Manage apps → FocusFlow → Notifications",
            "Enable all channels and lock-screen notifications",
        ),
        PermissionId.DEVICE_ADMIN to listOf(
            "Settings → Password & Security → Device admin apps",
            "Find FocusFlow and activate it",
        ),
        PermissionId.OVERLAY to listOf(
            "Settings → Apps → App info → FocusFlow",
            "Open Other permissions → Display pop-up windows while running in background",
            "Allow the permission for FocusFlow",
        ),
    ),
    "oneplus" to mapOf(
        PermissionId.ACCESSIBILITY to listOf(
            "Go to Settings → Accessibility → Downloaded apps",
            "Find FocusFlow and switch it ON",
            "If it stops afterward, set FocusFlow battery use to Unrestricted",
        ),
        PermissionId.USAGE to listOf(
            "Settings → Privacy → Special app access → Usage access",
            "Tap FocusFlow and enable Permit usage access",
        ),
        PermissionId.BATTERY to listOf(
            "Settings → Battery → Battery optimization",
            "Show All apps, choose FocusFlow, and select Don't optimize",
            "Also set Settings → Apps → FocusFlow → Battery to Unrestricted",
        ),
        PermissionId.NOTIFICATIONS to listOf(
            "Settings → Apps & notifications → App notifications → FocusFlow",
            "Enable all channels, especially the persistent service notification",
        ),
        PermissionId.DEVICE_ADMIN to listOf(
            "Settings → Security → Device admin apps",
            "Toggle FocusFlow and confirm",
        ),
        PermissionId.OVERLAY to listOf(
            "Settings → Apps → FocusFlow → Special app access",
            "Open Display over other apps or Appear on top",
            "Toggle FocusFlow ON",
        ),
    ),
    "realme" to mapOf(
        PermissionId.ACCESSIBILITY to listOf(
            "Settings → Additional Settings → Accessibility → Downloaded apps",
            "Enable FocusFlow",
            "On ColorOS 12+: Settings → Accessibility → Installed services",
        ),
        PermissionId.USAGE to listOf(
            "Settings → Privacy → Permission manager → Usage access",
            "OR Settings → Apps → App Management → ⋮ → Special access → Usage access",
            "Enable FocusFlow",
        ),
        PermissionId.BATTERY to listOf(
            "Settings → Battery → Battery optimization → All apps → FocusFlow",
            "Select Don't optimize",
            "Enable Auto-launch for FocusFlow in Special app access",
        ),
        PermissionId.NOTIFICATIONS to listOf(
            "Settings → Notifications & Status bar → App notifications → FocusFlow",
            "Turn on all notification categories",
            "Ensure Persistent notifications are allowed",
        ),
        PermissionId.DEVICE_ADMIN to listOf(
            "Settings → Security → Device admin apps",
            "Activate FocusFlow",
        ),
        PermissionId.OVERLAY to listOf(
            "Settings → Apps → FocusFlow → Special app access",
            "Enable Display over other apps",
        ),
    ),
    "stock" to mapOf(
        PermissionId.ACCESSIBILITY to listOf(
            "Go to Settings → Accessibility",
            "Tap Installed apps or Downloaded apps",
            "Find FocusFlow and toggle it ON",
            "Confirm the permission when asked",
        ),
        PermissionId.USAGE to listOf(
            "Go to Settings → Apps → Special app access → Usage access",
            "OR Settings → Digital Wellbeing → App permissions",
            "Find FocusFlow and enable Allow",
        ),
        PermissionId.BATTERY to listOf(
            "Go to Settings → Apps → FocusFlow",
            "Tap Battery → select Unrestricted",
            "Also check Battery optimization → All apps → FocusFlow → Don't optimize",
        ),
        PermissionId.NOTIFICATIONS to listOf(
            "Go to Settings → Apps → FocusFlow → Notifications",
            "Enable all notification channels",
            "Make sure Allow notifications is ON",
        ),
        PermissionId.DEVICE_ADMIN to listOf(
            "Go to Settings → Security → Device admin apps",
            "Toggle FocusFlow to Activate",
            "Confirm when prompted",
        ),
        PermissionId.OVERLAY to listOf(
            "Go to Settings → Apps → Special app access → Display over other apps",
            "Find FocusFlow and enable it",
        ),
    ),
)

@Composable
fun TroubleshootModal(
    visible: Boolean,
    permissionId: PermissionId,
    onClose: () -> Unit,
) {
    if (!visible) return

    var selectedBrand by remember { mutableStateOf("stock") }
    val permission = permissionInfo(permissionId)
    val selectedTips = tips[selectedBrand]?.get(permissionId).orEmpty().ifEmpty {
        listOf(
            "Open Settings and search for FocusFlow",
            "Grant the required permission",
            "Return to FocusFlow to verify the status",
        )
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(permission.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Troubleshoot: ${permission.label}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                }
                Text(
                    "Select your phone brand for step-by-step instructions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(brands) { brand ->
                        FilterChip(
                            selected = selectedBrand == brand.id,
                            onClick = { selectedBrand = brand.id },
                            label = { Text("${brand.icon} ${brand.label}") },
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(selectedTips) { tip ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                                shape = RoundedCornerShape(50),
                            ) {
                                Text(
                                    "${selectedTips.indexOf(tip) + 1}",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                            Text(tip, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(Icons.Outlined.Info, contentDescription = null)
                            Text(
                                "After granting the permission, come back here. The status refreshes automatically within a few seconds.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Button(
                    onClick = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                ) {
                    Text("Got it")
                }
            }
        }
    }
}

private data class PermissionInfo(
    val label: String,
    val icon: ImageVector,
)

private fun permissionInfo(id: PermissionId): PermissionInfo = when (id) {
    PermissionId.ACCESSIBILITY -> PermissionInfo("Accessibility Service", Icons.Outlined.Accessibility)
    PermissionId.USAGE -> PermissionInfo("Usage Access", Icons.Outlined.Analytics)
    PermissionId.BATTERY -> PermissionInfo("Battery Optimization", Icons.Outlined.BatteryChargingFull)
    PermissionId.NOTIFICATIONS -> PermissionInfo("Notifications", Icons.Outlined.Notifications)
    PermissionId.DEVICE_ADMIN -> PermissionInfo("Device Admin", Icons.Outlined.Security)
    PermissionId.OVERLAY -> PermissionInfo("Appear on Top", Icons.Outlined.Layers)
    PermissionId.MEDIA -> PermissionInfo("Media Files Access", Icons.Outlined.Layers)
    PermissionId.VPN -> PermissionInfo("VPN Network Access", Icons.Outlined.Security)
    PermissionId.LAUNCHER -> PermissionInfo("Home Launcher", Icons.Outlined.Layers)
}