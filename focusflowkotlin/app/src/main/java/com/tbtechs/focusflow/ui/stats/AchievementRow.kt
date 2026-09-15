package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tbtechs.focusflow.analytics.AchievementState

@Composable
fun AchievementRow(state: AchievementState) {
    if (state.definitions.isEmpty()) return
    Card {
        Column {
            Row {
                Text("ACHIEVEMENTS", style = MaterialTheme.typography.labelLarge)
                if (state.newlyEarnedIds.isNotEmpty()) Text("NEW", color = MaterialTheme.colorScheme.secondary)
            }
            state.definitions.forEach { achievement ->
                Text(achievement.title, style = MaterialTheme.typography.titleSmall)
                Text(achievement.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
