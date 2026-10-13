package com.tbtechs.focusflow.ui.onboarding

import android.Manifest
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.data.repository.SetupPersistenceManager
import com.tbtechs.focusflow.ui.permissions.AccessibilityRestrictedRecovery
import com.tbtechs.focusflow.ui.permissions.PermissionCard
import com.tbtechs.focusflow.ui.permissions.PermissionDefinition
import com.tbtechs.focusflow.ui.permissions.PermissionId
import com.tbtechs.focusflow.ui.permissions.PermissionStatus
import com.tbtechs.focusflow.ui.permissions.UsageAccessRecovery
import com.tbtechs.focusflow.ui.permissions.checkPermission
import com.tbtechs.focusflow.ui.permissions.openPermissionSettings
import com.tbtechs.focusflow.ui.permissions.permissionDefinitions
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.InfoBodyText
import com.tbtechs.focusflow.ui.theme.InfoBorder
import com.tbtechs.focusflow.ui.theme.InfoSurface
import com.tbtechs.focusflow.ui.theme.InfoText
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class OnboardingStep { CORE, OPTIONAL }

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit = {},
) {
    val dimensions = LocalFocusFlowDimensions.current
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(OnboardingStep.CORE) }
    var statuses by remember { mutableStateOf<Map<PermissionId, PermissionStatus>>(emptyMap()) }
    var expanded by remember { mutableStateOf<PermissionId?>(null) }
    var loading by remember { mutableStateOf<PermissionId?>(null) }
    var usageAttempted by remember { mutableStateOf(false) }
    var accessibilityAttempted by remember { mutableStateOf(false) }

    val onboardingPermissions = remember {
        val definitionsById = permissionDefinitions.associateBy { it.id }
        listOf(
            PermissionId.NOTIFICATIONS,
            PermissionId.BATTERY,
            PermissionId.OVERLAY,
            PermissionId.USAGE,
            PermissionId.ACCESSIBILITY,
            PermissionId.DEVICE_ADMIN,
        ).mapNotNull { definitionsById[it] }
    }

    fun refresh() {
        scope.launch {
            val updated = withContext(Dispatchers.IO) {
                onboardingPermissions.associate { it.id to checkPermission(context, it.id) }
            }
            statuses = updated
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
            PermissionId.USAGE -> {
                usageAttempted = true
                scope.launch { openPermissionSettings(context, permission.id); loading = null }
            }
            else -> scope.launch { openPermissionSettings(context, permission.id); loading = null }
        }
    }

    val core = onboardingPermissions.filterNot { it.optional }
    val optional = onboardingPermissions.filter { it.optional }
    val requiredReady = core.count { statuses[it.id] == PermissionStatus.GRANTED }
    val requiredTotal = core.size
    val optionalReady = optional.count { statuses[it.id] == PermissionStatus.GRANTED }

    Scaffold(
        containerColor = DarkBackground,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = dimensions.screenPadding),
            verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                // Title and Subtitle Header matching 1a.jpg / 1c.jpg
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (step == OnboardingStep.CORE) "Set up core access" else "Optional protection",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary,
                    )
                    Text(
                        text = if (step == OnboardingStep.CORE) "These permissions help FocusFlow block reliably."
                        else "Add extra protection now or come back later.",
                        fontSize = 14.sp,
                        color = DarkTextSecondary,
                    )
                }
            }

            if (step == OnboardingStep.CORE) {
                // "Why these permissions?" Info Callout Box matching 1a.jpg
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(InfoSurface)
                            .border(1.dp, InfoBorder.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
                            .padding(16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Shield,
                                    contentDescription = null,
                                    tint = BrandPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "Why these permissions?",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = InfoText,
                                )
                            }
                            Text(
                                text = "FocusFlow enforces focus at the system level — not just reminders. To actually block apps and keep your session running, Android requires special access that regular apps don't need.",
                                 fontSize = 13.sp,
                                 lineHeight = 20.sp,
                                 color = InfoBodyText,
                            )
                        }
                    }
                }

                // Progress Tracker
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Required access ready",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = DarkTextSecondary,
                            )
                            Text(
                                text = "$requiredReady / $requiredTotal",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandPrimary,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { if (requiredTotal > 0) requiredReady.toFloat() / requiredTotal.toFloat() else 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = BrandPrimary,
                            trackColor = DarkSurfaceVariant,
                        )
                    }
                }

                // Section Label
                item {
                    Text(
                        text = "Core access",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandPrimary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = "Tap a card to grant access.",
                        fontSize = 13.sp,
                        color = DarkTextSecondary,
                    )
                }

                // Core Cards List
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

                // Bottom setup note
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(InfoSurface)
                            .border(1.dp, InfoBorder.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
                            .padding(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                tint = BrandPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "Usage Access, Accessibility Service, and Notifications can be fixed anytime in Settings → Permissions.",
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = InfoBodyText,
                            )
                        }
                    }
                }
            } else {
                // Step 2: Optional Protection matching 1c.jpg / 1d.jpg
                item {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { step = OnboardingStep.CORE }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null,
                            tint = BrandPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Back to core setup",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandPrimary,
                        )
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                        text = "Optional access",
                            fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandPrimary,
                        )
                        Text(
                        text = "These features are not required and can be configured later.",
                            fontSize = 13.sp,
                            color = DarkTextSecondary,
                        )
                    }
                }

                // Optional Cards List
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

            }

            // Bottom CTA Button
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = {
                        if (step == OnboardingStep.CORE) {
                            step = OnboardingStep.OPTIONAL
                        } else {
                            scope.launch {
                                val setupPersistence = SetupPersistenceManager(context)
                                setupPersistence.setUserConsentedBackgroundService(true)
                                setupPersistence.setOnboardingComplete(true)
                                if (setupPersistence.isUserConsentedBackgroundService() &&
                                    setupPersistence.isOnboardingComplete()
                                ) {
                                    AppModule.foregroundServiceController.ensureRunning()
                                }
                                onFinished()
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (step == OnboardingStep.CORE && requiredReady < requiredTotal) InfoSurface else BrandPrimary,
                        contentColor = if (step == OnboardingStep.CORE && requiredReady < requiredTotal) InfoText else Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 50.dp),
                ) {
                    Text(
                        text = if (step == OnboardingStep.CORE) {
                            "Continue to optional setup →"
                        } else {
                            when {
                                optionalReady == 0 -> "Skip optional setup — let's start"
                                optionalReady == optional.size -> "All optional access ready — let's start"
                                optionalReady == 1 -> "1 optional permission enabled — let's start"
                                else -> "$optionalReady optional permissions enabled — let's start"
                            }
                        },
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Footer Subtitle Caption
            item {
                Text(
                    text = if (step == OnboardingStep.CORE) "You can manage permissions in Settings at any time."
                    else "Optional features can be enabled later from Settings.",
                    fontSize = 12.sp,
                    color = DarkTextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                )
            }
        }
    }

    AccessibilityRestrictedRecovery(accessibilityAttempted = accessibilityAttempted)
    UsageAccessRecovery(usageAccessAttempted = usageAttempted)
}
