package com.tbtechs.focusflow.ui.alwayson

import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tbtechs.focusflow.data.repository.VpnRepository
import com.tbtechs.focusflow.ui.home.FocusFlowModalCard
import com.tbtechs.focusflow.ui.home.FocusFlowPrimaryButton
import com.tbtechs.focusflow.ui.home.FocusFlowSecondaryButton
import com.tbtechs.focusflow.ui.home.RefBlue
import com.tbtechs.focusflow.ui.home.RefCard
import com.tbtechs.focusflow.ui.home.RefMuted
import com.tbtechs.focusflow.ui.home.RefSecondary
import com.tbtechs.focusflow.ui.home.RefText
import com.tbtechs.focusflow.ui.theme.scaledSp

internal enum class VpnConsentOutcome(val isGranted: Boolean) {
    GRANTED(true),
    NOT_GRANTED(false),
}

internal object VpnConsentPolicy {
    fun permissionGranted(
        repositoryPermission: Boolean?,
        platformPermissionCheck: () -> Boolean,
    ): Boolean {
        if (repositoryPermission != null) return repositoryPermission
        return runCatching { platformPermissionCheck() }.getOrDefault(false)
    }

    fun afterResult(permissionGranted: Boolean): VpnConsentOutcome =
        if (permissionGranted) VpnConsentOutcome.GRANTED else VpnConsentOutcome.NOT_GRANTED
}

/**
 * Starts Android's VPN consent flow and reports success only after checking the
 * actual VpnService permission state, rather than trusting the activity result.
 */
@Composable
internal fun rememberVpnConsentRequester(
    vpnRepository: VpnRepository?,
    onConsentResult: (VpnConsentOutcome) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val currentRepository by rememberUpdatedState(vpnRepository)
    val currentOnConsentResult by rememberUpdatedState(onConsentResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val permissionGranted = VpnConsentPolicy.permissionGranted(repositoryPermission = null) {
            VpnService.prepare(context) == null
        }
        currentOnConsentResult(VpnConsentPolicy.afterResult(permissionGranted))
    }

    return {
        val intentResult = runCatching {
            currentRepository?.consentIntentOrNull()
                ?: VpnService.prepare(context)
        }
        val consentIntent = intentResult.getOrNull()
        when {
            intentResult.isFailure -> {
                currentOnConsentResult(VpnConsentOutcome.NOT_GRANTED)
            }
            consentIntent == null -> {
                val permissionGranted = VpnConsentPolicy.permissionGranted(repositoryPermission = null) {
                    VpnService.prepare(context) == null
                }
                currentOnConsentResult(VpnConsentPolicy.afterResult(permissionGranted))
            }
            else -> {
                runCatching { launcher.launch(consentIntent) }
                    .onFailure { currentOnConsentResult(VpnConsentOutcome.NOT_GRANTED) }
            }
        }
    }
}

/**
 * Plain-language explanation shown immediately before Android's VPN consent
 * dialog. The native system dialog remains the authority for granting consent.
 */
@Composable
fun VpnConsentModal(
    visible: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    if (!visible) return

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        FocusFlowModalCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            radius = 24.dp,
            contentPadding = 24.dp,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(RefBlue.copy(alpha = 0.14f))
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Shield, contentDescription = null, tint = RefBlue, modifier = Modifier.size(32.dp))
            }
            Text(
                "How FocusFlow's VPN works",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                color = RefText,
                fontSize = 18.scaledSp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                "FocusFlow uses a local VPN on your device to block selected apps from reaching the internet during a focus session.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                color = RefSecondary,
                fontSize = 13.scaledSp,
                lineHeight = 20.scaledSp,
            )
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ConsentFact(
                    icon = Icons.Outlined.Lock,
                    text = "Nothing leaves your device. No traffic is sent to an external server. Packets are silently dropped inside a local tunnel that only FocusFlow can see.",
                )
                ConsentFact(
                    icon = Icons.Outlined.CheckCircle,
                    text = "Android will show its standard VPN consent dialog next. Tap OK to grant the one-time permission. It persists until you manually revoke it.",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(RefCard)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = RefMuted, modifier = Modifier.size(18.dp))
                    Text(
                        "Android only allows one active VPN at a time. If you use a work or privacy VPN, FocusFlow may need to temporarily take over for the session.",
                        color = RefSecondary,
                        fontSize = 13.scaledSp,
                        lineHeight = 20.scaledSp,
                    )
                }
            }
            FocusFlowPrimaryButton(
                text = "I understand",
                onClick = onConfirm,
                modifier = Modifier.padding(top = 16.dp),
            )
            FocusFlowSecondaryButton(
                text = "Cancel",
                onClick = onCancel,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ConsentFact(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = RefBlue,
        )
        Text(text, color = RefSecondary, fontSize = 13.scaledSp, lineHeight = 20.scaledSp)
    }
}