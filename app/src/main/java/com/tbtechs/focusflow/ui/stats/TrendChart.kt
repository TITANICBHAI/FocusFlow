package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.analytics.AnalyticsSnapshot
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TrendChart(snapshot: AnalyticsSnapshot) = StatsCard {
    Column(modifier = Modifier.padding(12.dp)) {
        Text("COMPLETION OVER 12 WEEKS", fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Text("The line is the pattern. The cards above explain it.")
        val points = snapshot.trends?.weekByWeek.orEmpty()
        val chartPoints = points.filter { it.hasData }
        if (chartPoints.isEmpty()) {
            Text(
                "No weekly completion data yet. Finish a few tasks to see the trend.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val chartColor = MaterialTheme.colorScheme.primary
            val gridColor = MaterialTheme.colorScheme.outlineVariant
            val outlineColor = MaterialTheme.colorScheme.outline
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .padding(top = 12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .height(144.dp)
                        .width(42.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("100%", style = MaterialTheme.typography.labelSmall)
                    Text("50%", style = MaterialTheme.typography.labelSmall)
                    Text("0%", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(144.dp),
                    ) {
                        val chartHeight = size.height
                        val chartWidth = size.width
                        val yFor = { rate: Double ->
                            chartHeight * (1f - rate.coerceIn(0.0, 1.0).toFloat())
                        }
                        listOf(0f, chartHeight / 2f, chartHeight).forEach { y ->
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y),
                                end = Offset(chartWidth, y),
                            )
                        }
                        drawLine(
                            color = outlineColor,
                            start = Offset(0f, 0f),
                            end = Offset(0f, chartHeight),
                        )
                        drawLine(
                            color = outlineColor,
                            start = Offset(0f, chartHeight),
                            end = Offset(chartWidth, chartHeight),
                        )
                        if (points.size > 1) {
                            val gap = chartWidth / (points.size - 1)
                            points.zipWithNext().forEachIndexed { index, (from, to) ->
                                if (from.hasData && to.hasData) {
                                    drawLine(
                                        color = chartColor,
                                        start = Offset(index * gap, yFor(from.completionRate)),
                                        end = Offset((index + 1) * gap, yFor(to.completionRate)),
                                        strokeWidth = 4f,
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 148.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val labels = listOfNotNull(
                            points.firstOrNull()?.weekStart?.let(::weekLabel),
                            points.getOrNull(points.lastIndex / 2)?.weekStart?.let(::weekLabel),
                            points.lastOrNull()?.weekStart?.let(::weekLabel),
                        ).distinct()
                        labels.forEach { label ->
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        Text("${snapshot.trends?.weeksWithData ?: 0} weeks with data", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun weekLabel(value: String): String =
    runCatching {
        LocalDate.parse(value).format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    }.getOrDefault(value.take(10))
