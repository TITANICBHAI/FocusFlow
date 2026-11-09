package com.tbtechs.focusflow.ui.stats

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Accent colours the Stats screens share, so they are named once instead of repeated as hex literals. */
internal val StatsBlue = Color(0xFF63A7FF)
internal val StatsGreen = Color(0xFF55C98A)
internal val StatsRed = Color(0xFFE06B6B)

/** Counts up from 0 to [target] the first time it is shown, then follows later changes. */
@Composable
internal fun rememberCountUp(target: Int): Int {
    var goal by remember { mutableStateOf(0) }
    LaunchedEffect(target) { goal = target }
    val value by animateIntAsState(
        targetValue = goal,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "stats-count-up",
    )
    return value
}

/** Grows from 0 to [target] (0..1) the first time it is shown; used for bar fills. */
@Composable
internal fun rememberGrowIn(target: Float): Float {
    var goal by remember { mutableStateOf(0f) }
    LaunchedEffect(target) { goal = target }
    val value by animateFloatAsState(
        targetValue = goal,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "stats-grow-in",
    )
    return value
}

/** A small dot that gently pulses, used to say "this number is live". */
@Composable
internal fun LiveDot(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "stats-live-dot")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "stats-live-dot-alpha",
    )
    Box(modifier.size(8.dp).alpha(alpha).clip(CircleShape).background(color))
}
