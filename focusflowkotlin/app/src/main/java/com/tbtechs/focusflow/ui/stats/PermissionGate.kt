package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** Usage Access is missing — distinct from the capability-level UnavailableGate. */
@Composable
fun PermissionGate(onOpenUsageAccessSettings: () -> Unit) = Column {
    Icon(Icons.Outlined.PhoneAndroid, null, tint = MaterialTheme.colorScheme.tertiary)
    Text("Unlock the 3-Month view", style = MaterialTheme.typography.headlineSmall)
    Text("This screen uses Android UsageStats to show phone behaviour patterns. The data stays on this device.")
    Button(onClick = onOpenUsageAccessSettings) {
        Icon(Icons.Outlined.Settings, null)
        Text("Grant Usage Access")
    }
}
