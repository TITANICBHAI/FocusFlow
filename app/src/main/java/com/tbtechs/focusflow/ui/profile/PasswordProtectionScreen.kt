package com.tbtechs.focusflow.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.domain.FocusPinManager
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.common.PinSetupModal
import com.tbtechs.focusflow.ui.common.PinType
import com.tbtechs.focusflow.ui.common.PinVerifyModal
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
import com.tbtechs.focusflow.ui.theme.scaledSp

/**
 * Password Protection hub.
 *
 * Implements screenshot 3e_10 with dark theme styling, dual password configuration,
 * and security status indicators.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordProtectionScreen(
    settingsViewModel: SettingsViewModel,
    focusPinManager: FocusPinManager,
    onBack: () -> Unit,
) {
    val dimensions = LocalFocusFlowDimensions.current
    val settings by settingsViewModel.settings.collectAsState()
    var focusSet by remember { mutableStateOf(focusPinManager.isPinSet()) }
    var modal by remember { mutableStateOf<PasswordModal?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        focusSet = focusPinManager.isPinSet()
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Password Protection",
                        fontSize = 18.scaledSp,
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = DarkTextPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = dimensions.screenPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // Informative Top Card matching 3e_10
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BrandPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Key,
                            contentDescription = null,
                            tint = BrandPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dual-layer security",
                            fontSize = 15.scaledSp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTextPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Focus Session Password locks in-progress sessions. Defense Password secures your block lists, settings, and prevents disabling protection.",
                            fontSize = 13.scaledSp,
                            lineHeight = 18.scaledSp,
                            color = DarkTextSecondary,
                        )
                    }
                }
            }

            // Defense Password Card
            PasswordCard(
                title = "Defense Password",
                isSet = settings.pinProtectionEnabled,
                badge = if (settings.pinProtectionEnabled) "Active" else "Not set",
                description = if (settings.pinProtectionEnabled) {
                    "Secured — required to disable protection, clear blocks, or uninstall protected apps."
                } else {
                    "Not set — defense settings and block lists can be modified without authentication."
                },
                onSet = { modal = PasswordModal.Setup(PinType.DEFENSE) },
                onChange = {
                    modal = PasswordModal.Verify(
                        PinType.DEFENSE,
                        "Verify Current Password",
                        "Enter your current Defense Password to change it.",
                    )
                },
                onRemove = {
                    modal = PasswordModal.Verify(
                        PinType.DEFENSE,
                        "Remove Defense Password",
                        "Enter your current Defense Password to remove it.",
                    )
                },
            )

            // Focus Session Password Card
            PasswordCard(
                title = "Focus Session Password",
                isSet = focusSet,
                badge = if (focusSet) "Active" else "Not set",
                description = if (focusSet) {
                    "Secured — required to end an active focus session before the timer completes."
                } else {
                    "Not set — focus sessions can be ended freely at any time."
                },
                onSet = { modal = PasswordModal.Setup(PinType.FOCUS) },
                onChange = {
                    modal = PasswordModal.Verify(
                        PinType.FOCUS,
                        "Verify Current Password",
                        "Enter your current Focus Session Password to change it.",
                    )
                },
                onRemove = {
                    modal = PasswordModal.Verify(
                        PinType.FOCUS,
                        "Remove Focus Session Password",
                        "Enter your current Focus Session Password to remove it.",
                    )
                },
            )

            notice?.let {
                Text(
                    text = it,
                    color = Color(0xFFEF4444),
                    fontSize = 13.scaledSp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
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
            description = active.description,
            verify = { raw -> if (active.pinType == PinType.FOCUS) focusPinManager.verifyPin(raw) else settingsViewModel.verifyPin(raw) },
            onVerified = { verifiedPin ->
                modal = if (active.title.startsWith("Remove")) {
                    PasswordModal.Remove(active.pinType, verifiedPin)
                } else {
                    if (active.pinType == PinType.FOCUS) {
                        focusPinManager.clearPin()
                    } else {
                        settingsViewModel.clearPin()
                    }
                    PasswordModal.Setup(active.pinType)
                }
            },
            onCancel = { modal = null },
        )
        is PasswordModal.Remove -> {
            AlertDialog(
                onDismissRequest = { modal = null },
                containerColor = DarkCard,
                titleContentColor = DarkTextPrimary,
                textContentColor = DarkTextSecondary,
                title = { Text("Remove ${active.pinType.label} Password?") },
                text = { Text("This removes password authentication. Features will no longer be locked behind this password.", fontSize = 13.scaledSp) },
                confirmButton = {
                    Button(
                        onClick = {
                            if (active.pinType == PinType.FOCUS) {
                                focusPinManager.clearPin()
                                notice = null
                            } else {
                                settingsViewModel.clearPin()
                                notice = null
                            }
                            modal = null
                            refresh()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    ) {
                        Text("Remove", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { modal = null }) {
                        Text("Cancel", color = DarkTextSecondary)
                    }
                },
            )
        }
        null -> Unit
    }
}

private sealed interface PasswordModal {
    data class Setup(val pinType: PinType) : PasswordModal
    data class Verify(val pinType: PinType, val title: String, val description: String) : PasswordModal
    data class Remove(val pinType: PinType, val verifiedHash: String) : PasswordModal
}

@Composable
private fun PasswordCard(
    title: String,
    description: String,
    badge: String,
    isSet: Boolean,
    onSet: () -> Unit,
    onChange: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    fontSize = 16.scaledSp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSet) Color(0xFF064E3B) else DarkSurfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (isSet) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(12.dp))
                        }
                        Text(
                            text = badge,
                            fontSize = 11.scaledSp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSet) Color(0xFF34D399) else DarkTextMuted,
                        )
                    }
                }
            }

            Text(
                text = description,
                fontSize = 13.scaledSp,
                lineHeight = 18.scaledSp,
                color = DarkTextSecondary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!isSet) {
                    Button(
                        onClick = onSet,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Set Password", fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                } else {
                    Button(
                        onClick = onChange,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Change", fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                    OutlinedButton(
                        onClick = onRemove,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Remove", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private val PinType.label: String
    get() = if (this == PinType.FOCUS) "Focus Session" else "Defense"
