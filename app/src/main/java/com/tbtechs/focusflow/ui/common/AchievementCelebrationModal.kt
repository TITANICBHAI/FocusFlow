package com.tbtechs.focusflow.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
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

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        entered = true
    }
    val badgeScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.55f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 260f),
        label = "achievement-badge-scale",
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "achievement-text-alpha",
    )
    val motion = rememberInfiniteTransition(label = "achievement-celebration")
    val badgeRotation by motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "achievement-badge-rotation",
    )
    val confettiProgress by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "achievement-confetti",
    )
    val confetti = listOf("✨", "⭐", "🎉", "🎊", "💫", "🌟", "⚡")

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    repeat(confetti.size) { index ->
                        Text(
                            text = confetti[index],
                            modifier = Modifier
                                .offset(y = ((confettiProgress * 28f) + index * 3f).dp)
                                .graphicsLayer {
                                    rotationZ = (confettiProgress * 360f) + index * 22f
                                    alpha = 1f - (confettiProgress * 0.55f)
                                },
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SpacerIcon()
                    Text(
                        "Achievement unlocked",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                }
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .graphicsLayer {
                            scaleX = badgeScale
                            scaleY = badgeScale
                            rotationZ = badgeRotation
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Card(shape = CircleShape) {
                        Text(
                            achievementEmoji(achievement.icon),
                            style = MaterialTheme.typography.displayMedium,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp),
                        )
                    }
                }
                Text(
                    achievement.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.graphicsLayer { alpha = textAlpha },
                )
                Text(
                    achievement.description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.graphicsLayer { alpha = textAlpha },
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