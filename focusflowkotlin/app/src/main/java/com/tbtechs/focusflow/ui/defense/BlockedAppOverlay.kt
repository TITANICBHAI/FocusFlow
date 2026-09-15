package com.tbtechs.focusflow.ui.defense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Compose counterpart for the mapped BlockedAppOverlay component.
 *
 * The source component is not present in the imported Expo checkout. The
 * enforcement overlay is currently owned by BlockOverlayActivity, so this is
 * deliberately a presentation-only fallback until that source contract is
 * supplied.
 */
@Composable
fun BlockedAppOverlay(
    appName: String = "This app",
    onGoHome: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Text("$appName is blocked", style = MaterialTheme.typography.headlineSmall)
        Text(
            "FocusFlow is protecting your current focus plan.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onGoHome) { Text("Go home") }
        Text(
            "NEEDS: original BlockedAppOverlay.tsx source and native dismissal actions.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
