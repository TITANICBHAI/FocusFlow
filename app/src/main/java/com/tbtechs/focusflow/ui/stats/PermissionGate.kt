package com.tbtechs.focusflow.ui.stats

import androidx.compose.ui.res.stringResource
import com.tbtechs.focusflow.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Usage Access is missing — distinct from the capability-level UnavailableGate. */
@Composable
fun PermissionGate(onOpenUsageAccessSettings: () -> Unit) = Column(
    modifier = Modifier
        .fillMaxSize()
        .padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Icon(Icons.Outlined.PhoneAndroid, null, tint = MaterialTheme.colorScheme.tertiary)
    Text(stringResource(R.string.stats_unlock_the_3_month_view), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.stats_this_screen_uses_android_usagestats))
    Button(onClick = onOpenUsageAccessSettings) {
        Icon(Icons.Outlined.Settings, null)
        Text(stringResource(R.string.stats_grant_usage_access))
    }
}
