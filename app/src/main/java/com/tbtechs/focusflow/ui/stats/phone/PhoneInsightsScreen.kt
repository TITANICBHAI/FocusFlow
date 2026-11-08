package com.tbtechs.focusflow.ui.stats.phone

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.ui.theme.DarkTextMuted

/** The Insights destination. Stub: replaced in T20 (the signature is final). */
@Composable
internal fun PhoneInsightsScreen(
    phoneViewModel: PhoneStatsViewModel,
    onOpenUsageAccessSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text("Insights — coming soon", color = DarkTextMuted, modifier = modifier.padding(16.dp))
}
