package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** Build-level hourly UsageStats capability is absent — it is not a permission prompt. */
@Composable
fun UnavailableGate() = Column {
    Icon(Icons.Outlined.PhoneAndroid, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("3-Month view unavailable", style = MaterialTheme.typography.headlineSmall)
    Text("This build cannot read Android hourly UsageStats yet. Use a FocusFlow Android build with UsageStats support to unlock phone behaviour patterns.")
}
