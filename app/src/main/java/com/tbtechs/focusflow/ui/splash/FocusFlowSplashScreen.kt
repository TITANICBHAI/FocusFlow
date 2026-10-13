package com.tbtechs.focusflow.ui.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.R
import com.tbtechs.focusflow.ui.theme.BrandPrimary

/**
 * Branded first screen shown while the app boot sequence opens the database.
 *
 * The platform splash only provides the solid-color handoff. This Compose
 * overlay owns the actual sequence: shield first, then the product name and
 * tagline. It also keeps the final frame visible until the real boot state is
 * ready, so fast and slow launches both get the same intentional transition.
 */
@Composable
fun FocusFlowSplashOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    var logoVisible by rememberSaveable { mutableStateOf(false) }
    var textVisible by rememberSaveable { mutableStateOf(false) }
    var sequenceComplete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!logoVisible) {
            logoVisible = true
            kotlinx.coroutines.delay(LOGO_DURATION_MS.toLong())
        }
        if (!textVisible) {
            textVisible = true
            kotlinx.coroutines.delay(TEXT_DURATION_MS.toLong())
        }
        if (!sequenceComplete) {
            kotlinx.coroutines.delay(HOLD_AFTER_TEXT_MS)
            sequenceComplete = true
        }
    }

    AnimatedVisibility(
        visible = visible || !sequenceComplete,
        enter = fadeIn(animationSpec = tween(160)),
        exit = fadeOut(animationSpec = tween(220)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BrandPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    visible = logoVisible,
                    enter = fadeIn(tween(LOGO_DURATION_MS)) + scaleIn(
                        initialScale = 0.72f,
                        animationSpec = tween(LOGO_DURATION_MS),
                    ),
                ) {
                    FocusFlowSplashLogo()
                }

                Spacer(modifier = Modifier.height(24.dp))

                AnimatedVisibility(
                    visible = textVisible,
                    enter = fadeIn(tween(TEXT_DURATION_MS)) + slideInVertically(
                        initialOffsetY = { 22 },
                        animationSpec = tween(TEXT_DURATION_MS),
                    ),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "FocusFlow",
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your discipline operating system",
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The archived app mark is the source of truth for the splash identity.
 * Keeping the image intact avoids a hand-drawn shield silhouette that can
 * read like an unintended chin at small sizes.
 */
@Composable
private fun FocusFlowSplashLogo(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(132.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xFF0D0E16)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.focusflow_icon),
            contentDescription = "FocusFlow",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

private const val LOGO_DURATION_MS = 400
private const val TEXT_DURATION_MS = 350
private const val HOLD_AFTER_TEXT_MS = 500L
