package com.tbtechs.focusflow.ui.stats.phone

import androidx.compose.foundation.background
import com.tbtechs.focusflow.ui.theme.scaledSp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.analytics.ANALYTICS_ALL_TIME
import com.tbtechs.focusflow.analytics.ANALYTICS_TODAY
import com.tbtechs.focusflow.analytics.ANALYTICS_WEEK
import com.tbtechs.focusflow.analytics.ANALYTICS_YESTERDAY
import com.tbtechs.focusflow.ui.stats.StatsCard
import com.tbtechs.focusflow.ui.theme.DarkTextMuted

/** The window choices shown on the Stats and Extra screens, in display order. */
internal val PHONE_WINDOW_OPTIONS: List<Pair<String, String>> = listOf(
    ANALYTICS_TODAY to "Today",
    ANALYTICS_YESTERDAY to "Yesterday",
    ANALYTICS_WEEK to "Week",
    ANALYTICS_ALL_TIME to "All time",
)

/** Small upper-case heading, like the ones in `PhoneTimeCard`. */
@Composable
internal fun PhoneSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, modifier = modifier)
}

@Composable
internal fun PhoneMutedNote(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 12.scaledSp, color = DarkTextMuted, modifier = modifier)
}

/** A big number with a small label under it. */
@Composable
internal fun PhoneStatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontSize = 20.scaledSp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, fontSize = 12.scaledSp, color = DarkTextMuted)
    }
}

/** A tinted tile with a big number and a short label under it. */
@Composable
internal fun PhoneHabitTile(value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, fontSize = 20.scaledSp, fontWeight = FontWeight.ExtraBold, color = accent, maxLines = 1)
        Text(label, fontSize = 11.scaledSp, color = DarkTextMuted, maxLines = 2)
    }
}

/** Renders every report state except `Ready` (which renders nothing here). */
@Composable
internal fun PhoneStateMessage(
    state: PhoneReportUiState,
    onOpenUsageAccessSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        PhoneReportUiState.Loading -> Row(
            modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Loading phone habits…", fontSize = 13.scaledSp, color = DarkTextMuted)
        }
        PhoneReportUiState.PermissionNeeded -> StatsCard(modifier) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Usage access is needed to analyse phone habits.", fontSize = 14.scaledSp)
                Button(onClick = onOpenUsageAccessSettings) { Text("Open settings") }
            }
        }
        PhoneReportUiState.NoData -> StatsCard(modifier) {
            Text(
                "No phone activity recorded yet. FocusFlow builds this history from now on.",
                fontSize = 14.scaledSp,
                color = DarkTextMuted,
                modifier = Modifier.padding(12.dp),
            )
        }
        is PhoneReportUiState.Failed -> StatsCard(modifier) {
            Text(
                "Couldn't load phone habits right now.",
                fontSize = 14.scaledSp,
                color = DarkTextMuted,
                modifier = Modifier.padding(12.dp),
            )
        }
        is PhoneReportUiState.Ready -> Unit
    }
}
