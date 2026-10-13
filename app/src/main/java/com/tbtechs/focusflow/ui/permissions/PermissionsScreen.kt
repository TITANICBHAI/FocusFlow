package com.tbtechs.focusflow.ui.permissions

import android.Manifest
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.support.TroubleshootModal
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.InfoBodyText
import com.tbtechs.focusflow.ui.theme.InfoBorder
import com.tbtechs.focusflow.ui.theme.InfoSurface
import com.tbtechs.focusflow.ui.theme.InfoText
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
import com.tbtechs.focusflow.ui.theme.scaledSp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    isFocusActive: Boolean = false,
    onBack: () -> Unit = {},
    onConfigureLauncher: () -> Unit = {},
) {
    val dimensions = LocalFocusFlowDimensions.current
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val settings by settingsViewModel.settings.collectAsState()
    var statuses by remember { mutableStateOf<Map<PermissionId, PermissionStatus>>(emptyMap()) }
    var expanded by remember { mutableStateOf<PermissionId?>(null) }
    var checking by remember { mutableStateOf(true) }
    var troubleshooting by remember { mutableStateOf<PermissionId?>(null) }
    val standaloneActive = settings.standaloneBlockActive &&
        settings.standaloneBlockPackages.isNotEmpty() &&
        settings.standaloneBlockUntilMs > System.currentTimeMillis()
    val locked = isFocusActive || standaloneActive

    fun refresh() {
        scope.launch {
            checking = true
            statuses = withContext(Dispatchers.IO) {
                permissionDefinitions.associate { it.id to checkPermission(context, it.id) }
            }
            checking = false
        }
    }
    LaunchedEffect(Unit) { refresh() }
    DisposableEffect(owner) {
        var delayedRefreshOne: Job? = null
        var delayedRefreshTwo: Job? = null
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh()
                delayedRefreshOne?.cancel()
                delayedRefreshTwo?.cancel()
                delayedRefreshOne = scope.launch {
                    kotlinx.coroutines.delay(2_000)
                    refresh()
                }
                delayedRefreshTwo = scope.launch {
                    kotlinx.coroutines.delay(4_000)
                    refresh()
                }
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            delayedRefreshOne?.cancel()
            delayedRefreshTwo?.cancel()
            owner.lifecycle.removeObserver(observer)
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    fun grant(id: PermissionId) {
        if (id == PermissionId.MEDIA) {
            launcher.launch(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE)
        } else if (id == PermissionId.NOTIFICATIONS && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (id == PermissionId.VPN) {
            VpnService.prepare(context)?.let(vpnLauncher::launch) ?: refresh()
        } else if (id == PermissionId.LAUNCHER) {
            scope.launch { openPermissionSettings(context, id) }
        } else {
            scope.launch { openPermissionSettings(context, id) }
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = { Text("Permissions", color = DarkTextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = DarkTextPrimary)
                    }
                },
                actions = {
                    if (!locked) {
                        IconButton(onClick = ::refresh, enabled = !checking) {
                            Icon(Icons.Outlined.Refresh, "Refresh", tint = BrandPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
            )
        },
    ) { padding ->
        if (locked) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(dimensions.screenPadding),
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkBorder.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                        .padding(20.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, tint = BrandPrimary)
                            Text("Settings Locked", fontSize = 18.scaledSp, fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                        }
                        Text(
                            if (isFocusActive) "Permission settings are disabled while a focus session is running."
                            else "Permission settings are disabled while a standalone block is active.",
                            fontSize = 14.scaledSp,
                            color = DarkTextSecondary,
                        )
                        Text(
                            "Stop the block first to change permissions.",
                            fontSize = 13.scaledSp,
                            lineHeight = 18.scaledSp,
                            color = DarkTextMuted,
                        )
                        Button(
                            onClick = onBack,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 44.dp),
                        ) {
                            Text("Go Back", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = dimensions.screenPadding),
                verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
            ) {
                item { RestrictedSettingsBanner() }
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(InfoSurface)
                            .border(1.dp, InfoBorder.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                            .padding(12.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Shield,
                                    contentDescription = null,
                                    tint = BrandPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "Why these permissions?",
                                    fontSize = 14.scaledSp,
                                    fontWeight = FontWeight.Bold,
                                    color = InfoText,
                                )
                            }
                            Text(
                                text = "These permissions let FocusFlow block apps reliably.",
                                 fontSize = 12.scaledSp,
                                 lineHeight = 17.scaledSp,
                                 color = InfoBodyText,
                            )
                        }
                    }
                }
                val required = permissionDefinitions.count { !it.optional }
                val granted = permissionDefinitions.count { !it.optional && statuses[it.id] == PermissionStatus.GRANTED }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Required permissions granted",
                                fontSize = 12.scaledSp,
                                fontWeight = FontWeight.Medium,
                                color = DarkTextSecondary,
                            )
                            Text(
                                text = "$granted / $required",
                                fontSize = 13.scaledSp,
                                fontWeight = FontWeight.Bold,
                                color = BrandPrimary,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { if (required > 0) granted.toFloat() / required.toFloat() else 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = BrandPrimary,
                            trackColor = DarkSurfaceVariant,
                        )
                        if (granted == required) {
                             Text(
                                text = "All required permissions are ready.",
                                fontSize = 12.scaledSp,
                                fontWeight = FontWeight.SemiBold,
                                color = com.tbtechs.focusflow.ui.theme.StatusReadyText,
                            )
                        }
                    }
                }
                items(permissionDefinitions, key = { it.id }) { permission ->
                    PermissionCard(
                        permission = permission,
                        status = statuses[permission.id] ?: PermissionStatus.UNKNOWN,
                        expanded = expanded == permission.id,
                        busy = checking,
                        showTroubleshoot = statuses[permission.id] != PermissionStatus.GRANTED,
                        showOpenWhenGranted = true,
                        onToggle = { expanded = if (expanded == permission.id) null else permission.id },
                        onGrant = { grant(permission.id) },
                        onTroubleshoot = { troubleshooting = permission.id },
                    )
                    if (permission.id == PermissionId.LAUNCHER && statuses[permission.id] == PermissionStatus.GRANTED) {
                        TextButton(onClick = onConfigureLauncher) {
                            Text("Configure Launcher Settings →", color = BrandPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item {
                    Text(
                        text = "Tap a card for details. Status refreshes when you return.",
                        fontSize = 12.scaledSp,
                        color = DarkTextMuted,
                        modifier = Modifier.padding(bottom = 24.dp),
                    )
                }
            }
        }
    }

    troubleshooting?.let { id ->
        TroubleshootModal(
            visible = true,
            permissionId = id,
            onClose = { troubleshooting = null },
        )
    }
}
