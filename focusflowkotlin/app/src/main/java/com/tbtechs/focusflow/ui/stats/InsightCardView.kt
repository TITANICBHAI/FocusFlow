package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tbtechs.focusflow.analytics.InsightCard

@Composable
fun InsightCardView(insight: InsightCard) {
    val accent = when (insight.sentiment) {
        "positive" -> MaterialTheme.colorScheme.secondary
        "warning" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Card {
        Column {
            Row { Text(if (insight.category == "nothing_to_report") "OBSERVATION" else insight.category.uppercase(), color = accent, style = MaterialTheme.typography.labelLarge) }
            Text(insight.headline, style = MaterialTheme.typography.titleLarge)
            Text(insight.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
