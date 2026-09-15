package com.tbtechs.focusflow.ui.alwayson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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

    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text("How FocusFlow's VPN works") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "FocusFlow uses a local VPN on your device to block selected apps from reaching the internet during a focus session.",
                )
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
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Android only allows one active VPN at a time. If you use a work or privacy VPN, FocusFlow may need to temporarily take over for the session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("I understand") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
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
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}