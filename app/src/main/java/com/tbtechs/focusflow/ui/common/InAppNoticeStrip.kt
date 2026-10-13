package com.tbtechs.focusflow.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.scaledSp
import kotlinx.coroutines.delay

enum class InAppNoticeTone {
    INFO,
    SUCCESS,
    WARNING,
    LOADING,
}

data class InAppNotice(
    val id: Int,
    val message: String,
    val tone: InAppNoticeTone = InAppNoticeTone.INFO,
    val dismissAfterMillis: Long? = 4_500,
)

@Composable
fun InAppNoticeStrip(
    notice: InAppNotice?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(notice?.id, notice?.dismissAfterMillis) {
        val dismissAfterMillis = notice?.dismissAfterMillis ?: return@LaunchedEffect
        delay(dismissAfterMillis)
        currentOnDismiss()
    }

    AnimatedVisibility(
        visible = notice != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        modifier = modifier.fillMaxWidth(),
    ) {
        val currentNotice = notice ?: return@AnimatedVisibility
        val (icon, tint) = when (currentNotice.tone) {
            InAppNoticeTone.INFO -> Icons.Outlined.Info to Color(0xFF93C5FD)
            InAppNoticeTone.SUCCESS -> Icons.Outlined.CheckCircle to Color(0xFF34D399)
            InAppNoticeTone.WARNING -> Icons.Outlined.Lock to Color(0xFFFBBF24)
            InAppNoticeTone.LOADING -> null to BrandPrimary
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(
                        text = currentNotice.message,
                        modifier = Modifier.weight(1f),
                        fontSize = 13.scaledSp,
                        color = Color.White,
                        lineHeight = 18.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (icon == null) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = tint,
                        trackColor = tint.copy(alpha = 0.2f),
                    )
                }
            }
        }
    }
}
