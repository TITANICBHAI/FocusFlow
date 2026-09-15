package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tbtechs.focusflow.analytics.AnalyticsSnapshot

@Composable
fun TrendChart(snapshot: AnalyticsSnapshot) = Card {
    Column {
        Text("COMPLETION OVER 12 WEEKS", style = MaterialTheme.typography.labelLarge)
        Text("The line is the pattern. The cards above explain it.")
        val points = snapshot.trends?.weekByWeek.orEmpty()
        val chartColor = MaterialTheme.colorScheme.primary
        Canvas(modifier = Modifier.fillMaxWidth()) {
            if (points.size > 1) {
                val gap = size.width / (points.size - 1)
                points.zipWithNext().forEachIndexed { index, (from, to) ->
                    drawLine(
                        color = chartColor,
                        start = androidx.compose.ui.geometry.Offset(index * gap, size.height * (1f - from.completionRate.toFloat())),
                        end = androidx.compose.ui.geometry.Offset((index + 1) * gap, size.height * (1f - to.completionRate.toFloat())),
                    )
                }
            }
        }
        Text("${snapshot.trends?.weeksWithData ?: 0} weeks with data", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
