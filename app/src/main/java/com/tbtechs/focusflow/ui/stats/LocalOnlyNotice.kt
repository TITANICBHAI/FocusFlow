package com.tbtechs.focusflow.ui.stats

import androidx.compose.ui.res.stringResource
import com.tbtechs.focusflow.R
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
            Text(stringResource(R.string.stats_your_data_stays_here), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.stats_focusflow_does_not_collect_upload), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Dismiss privacy notice") }
    }
}
