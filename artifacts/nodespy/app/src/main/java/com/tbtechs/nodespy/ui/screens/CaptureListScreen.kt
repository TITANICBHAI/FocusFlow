package com.tbtechs.nodespy.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShieldMoon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tbtechs.nodespy.data.CaptureStore
import com.tbtechs.nodespy.data.NodeCapture
import com.tbtechs.nodespy.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureListScreen(
    onOpenCapture: (String) -> Unit,
    onLaunchBubble: () -> Unit = {},
    onOpenPermissions: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTick by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val captures by CaptureStore.captures.collectAsState()
    val serviceRunning by CaptureStore.serviceRunning.collectAsState()
    val loggingOn by CaptureStore.loggingEnabled.collectAsState()
    val snapOn by CaptureStore.screenshotEnabled.collectAsState()
    val pinnedIds by CaptureStore.bubblePinnedIds.collectAsState()

    val allPermissionsOk = remember(refreshTick, serviceRunning) {
        val overlayOk = Settings.canDrawOverlays(context)
        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
        serviceRunning && overlayOk && notifOk
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "NodeSpy",
                            color = OnBackground,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 20.sp
                        )
                        Spacer(Modifier.width(10.dp))
                        ServiceBadge(running = serviceRunning)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
                actions = {
                    IconButton(onClick = onOpenPermissions) {
                        Icon(
                            if (allPermissionsOk) Icons.Default.Shield else Icons.Default.ShieldMoon,
                            "Permissions",
                            tint = if (allPermissionsOk) AccentGreen else AccentOrange
                        )
                    }
                    IconButton(onClick = onLaunchBubble) {
                        Icon(Icons.Default.BubbleChart, "Launch Bubble", tint = AccentBlue)
                    }
                    if (captures.isNotEmpty()) {
                        IconButton(onClick = { CaptureStore.clearAll() }) {
                            Icon(Icons.Default.DeleteSweep, "Clear all", tint = Muted)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Background)
        ) {
            if (!serviceRunning) {
                ServiceBanner(
                    message = "Accessibility service is off — NodeSpy cannot capture nodes",
                    actionLabel = "Enable",
                    color = AccentOrange
                ) {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }

            if (!Settings.canDrawOverlays(context)) {
                ServiceBanner(
                    message = "Draw over apps permission missing — floating bubble disabled",
                    actionLabel = "Fix",
                    color = AccentRed
                ) {
                    onOpenPermissions()
                }
            }

            if (loggingOn || snapOn || pinnedIds.isNotEmpty()) {
                BubbleStatusBar(
                    loggingOn = loggingOn,
                    snapOn = snapOn,
                    pinnedCount = pinnedIds.size
                )
            }

            if (captures.isEmpty()) {
                EmptyState(serviceRunning)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(captures, key = { it.id }) { capture ->
                        CaptureCard(capture, onClick = { onOpenCapture(capture.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun BubbleStatusBar(loggingOn: Boolean, snapOn: Boolean, pinnedCount: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceVar)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatusChip(label = "LOG", active = loggingOn, activeColor = AccentGreen)
        StatusChip(label = "SNAP", active = snapOn, activeColor = AccentBlue)
        if (pinnedCount > 0) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(AccentOrange.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    "$pinnedCount pinned",
                    color = AccentOrange,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, active: Boolean, activeColor: androidx.compose.ui.graphics.Color) {
    val color = if (active) activeColor else Muted
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            if (active) "● $label" else "○ $label",
            color = color,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ServiceBadge(running: Boolean) {
    val color = if (running) AccentGreen else AccentRed
    val label = if (running) "LIVE" else "OFF"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(
            if (running) Icons.Default.FiberManualRecord else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(8.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ServiceBanner(
    message: String,
    actionLabel: String,
    color: androidx.compose.ui.graphics.Color,
    onAction: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(message, color = color, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onAction) {
            Text(actionLabel, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyState(serviceRunning: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Search, contentDescription = null,
                tint = Muted, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                if (serviceRunning) "Open any app to capture its node tree"
                else "Enable the accessibility service to start",
                color = Muted, fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun CaptureCard(capture: NodeCapture, onClick: () -> Unit) {
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val shortPkg = capture.pkg.substringAfterLast('.')
    val shortCls = capture.activityClass.substringAfterLast('.')
    val hasScreenshot = capture.screenshotPath != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AccentBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    shortPkg.take(2).uppercase(),
                    color = AccentBlue,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    capture.pkg,
                    color = OnBackground,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    shortCls,
                    color = Muted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasScreenshot) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AccentBlue.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text("📷", fontSize = 10.sp)
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    NodeCountBadge(capture.nodes.size)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    fmt.format(Date(capture.timestamp)),
                    color = Muted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun NodeCountBadge(count: Int) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(SurfaceVar)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            "$count nodes",
            color = AccentGreen,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}
