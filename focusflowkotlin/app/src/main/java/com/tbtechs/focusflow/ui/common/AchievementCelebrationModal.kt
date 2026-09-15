package com.tbtechs.focusflow.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.analytics.AchievementDefinition

@Composable
fun AchievementCelebrationModal(
    visible: Boolean,
    achievement: AchievementDefinition?,
    onDismiss: () -> Unit,
) {
    if (!visible || achievement == null) return

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SpacerIcon()
                    Text("Achievement unlocked", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                }
                Card {
                    Text(
                        achievementEmoji(achievement.icon),
                        style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp),
                    )
                }
                Text(achievement.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    achievement.description,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Keep going")
                }
            }
        }
    }
}

@Composable
private fun SpacerIcon() {
    Icon(
        Icons.Outlined.EmojiEvents,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
    )
}

private fun achievementEmoji(icon: String): String = when {
    icon.contains("trophy") -> "🏆"
    icon.contains("shield") -> "🛡️"
    icon.contains("calendar") -> "📅"
    icon.contains("flash") -> "⚡"
    icon.contains("refresh") -> "🔄"
    else -> "✨"
}