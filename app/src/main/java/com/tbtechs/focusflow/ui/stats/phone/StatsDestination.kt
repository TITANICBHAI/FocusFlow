package com.tbtechs.focusflow.ui.stats.phone

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.ui.stats.StatsPeriodPill

/** Top-level switcher on the Stats tab. */
enum class StatsDestination(val label: String) { STATS("Stats"), EXTRA("Extra"), INSIGHTS("Insights") }

/** Second row shown inside Extra. */
enum class ExtraTab(val label: String) {
    OVERVIEW("Overview"), APPS("Apps"), PHONE("Phone"), DISTRACTION("Distraction"), FOCUS("Focus"),
}

@Composable
fun StatsDestinationTabs(
    selected: StatsDestination,
    onSelect: (StatsDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsDestination.values().forEach { destination ->
            StatsPeriodPill(
                label = destination.label,
                selected = destination == selected,
                onClick = { onSelect(destination) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun ExtraTabRow(
    selected: ExtraTab,
    onSelect: (ExtraTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ExtraTab.values().forEach { tab ->
            StatsPeriodPill(label = tab.label, selected = tab == selected, onClick = { onSelect(tab) })
        }
    }
}
