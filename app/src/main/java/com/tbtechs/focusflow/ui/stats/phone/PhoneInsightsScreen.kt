package com.tbtechs.focusflow.ui.stats.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.analytics.phone.insights.InsightGroup
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsight
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsightsResult
import com.tbtechs.focusflow.ui.stats.StatsCard
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextMuted

/** The Insights destination. Shows what the ViewModel already computed; nothing is recomputed here. */
@Composable
internal fun PhoneInsightsScreen(
    phoneViewModel: PhoneStatsViewModel,
    onOpenUsageAccessSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by phoneViewModel.insights.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Insights",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(INSIGHTS_SUBTITLE, fontSize = 13.sp, color = DarkTextMuted)
            }
        }

        when (val s = state) {
            PhoneInsightsUiState.Loading -> item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Looking for patterns…", fontSize = 13.sp, color = DarkTextMuted, modifier = Modifier.padding(start = 10.dp))
                }
            }
            is PhoneInsightsUiState.Loaded -> when (val r = s.result) {
                PhoneInsightsResult.PermissionNeeded -> item {
                    PhoneStateMessage(PhoneReportUiState.PermissionNeeded, onOpenUsageAccessSettings)
                }
                is PhoneInsightsResult.Building -> item {
                    StatsCard { Text(buildingMessage(r.completeDays), fontSize = 14.sp, modifier = Modifier.padding(16.dp)) }
                }
                is PhoneInsightsResult.Failed -> item {
                    StatsCard { Text(INSIGHTS_FAILED, fontSize = 14.sp, color = DarkTextMuted, modifier = Modifier.padding(16.dp)) }
                }
                is PhoneInsightsResult.Ready -> {
                    if (r.insights.isEmpty()) {
                        item {
                            StatsCard { Text(INSIGHTS_EMPTY, fontSize = 14.sp, color = DarkTextMuted, modifier = Modifier.padding(16.dp)) }
                        }
                    } else {
                        groupInsights(r.insights).forEach { section ->
                            item(key = "title:${section.group.name}") {
                                PhoneSectionTitle(section.group.title, Modifier.semantics { heading() })
                            }
                            items(section.insights, key = { it.id }) { insight ->
                                InsightCard(insight, compact = section.group == InsightGroup.RECORD)
                            }
                        }
                    }
                }
            }
        }

        item {
            StatsCard { PhoneMutedNote(INSIGHTS_FOOTER, Modifier.padding(16.dp)) }
        }
    }
}

@Composable
private fun InsightCard(insight: PhoneInsight, compact: Boolean) {
    var expanded by rememberSaveable(insight.id) { mutableStateOf(false) }
    StatsCard {
        Column(Modifier.padding(if (compact) 12.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    insightCardTitle(insight),
                    fontSize = if (compact) 14.sp else 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (insight.isNew) {
                    Text("New", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandPrimary)
                }
            }
            Text(insight.message, fontSize = 14.sp)
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide details" else "How this was calculated", fontSize = 13.sp)
            }
            if (expanded) {
                evidenceLines(insight).forEach { PhoneMutedNote(it) }
            }
        }
    }
}
