package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun LocalOnlyNotice(onDismiss: () -> Unit) = StatsCard {
    Row(modifier = androidx.compose.ui.Modifier.padding(12.dp)) {
        Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.secondary)
        Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
            Text("Your data stays here", style = MaterialTheme.typography.titleSmall)
            Text("FocusFlow does not collect, upload, or share your analytics. This view is calculated on this device only.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Dismiss privacy notice") }
    }
}
