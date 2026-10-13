package com.tbtechs.focusflow.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Theme control from the settings Appearance section.
 *
 * Theme ownership deliberately stays with the activity-level theme host. The
 * settings ViewModel has no theme field, so callers supply the current value
 * and persistence action rather than creating an unrelated ViewModel here.
 */
@Composable
fun DarkModeToggle(
    isDark: Boolean,
    onToggle: () -> Unit,
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (isDark) 28.dp else 0.dp,
        animationSpec = spring(),
        label = "darkModeThumbOffset",
    )
    val activeIcon = if (isDark) Icons.Outlined.DarkMode else Icons.Outlined.WbSunny

    Box(
        modifier = Modifier
            .size(width = 58.dp, height = 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (isDark) Color(0xFF312E81) else Color(0xFF93C5FD))
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.CenterStart,
    ) {
        Icon(
            imageVector = Icons.Outlined.Cloud,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-15).dp)
                .size(9.dp),
        )
        Icon(
            imageVector = Icons.Outlined.WbSunny,
            contentDescription = null,
            tint = Color(0xFFFBBF24),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-5).dp)
                .size(10.dp),
        )
        Icon(
            imageVector = Icons.Outlined.Star,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 7.dp, y = (-4).dp)
                .size(6.dp),
        )
        Icon(
            imageVector = Icons.Outlined.Star,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 13.dp, y = 5.dp)
                .size(4.dp),
        )
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .padding(3.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = activeIcon,
                contentDescription = if (isDark) "Dark mode enabled" else "Light mode enabled",
                tint = if (isDark) Color(0xFF6366F1) else Color(0xFF64748B),
                modifier = Modifier.size(13.dp),
            )
        }
    }
}
