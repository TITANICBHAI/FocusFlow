package com.tbtechs.focusflow.ui.defense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.ui.home.FocusFlowPrimaryButton
import com.tbtechs.focusflow.ui.home.RefBackground
import com.tbtechs.focusflow.ui.home.RefMuted
import com.tbtechs.focusflow.ui.home.RefSecondary
import com.tbtechs.focusflow.ui.home.RefText
import com.tbtechs.focusflow.ui.theme.scaledSp

/**
 * Compose counterpart for the mapped BlockedAppOverlay component.
 *
 * The source component is not present in the imported Expo checkout. The
 * enforcement overlay is currently owned by BlockOverlayActivity, so this is
 * deliberately a presentation-only fallback until that source contract is
 * supplied.
 */
@Composable
fun BlockedAppOverlay(
    appName: String = "This app",
    onGoHome: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().background(RefBackground).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Block,
            contentDescription = null,
            tint = Color(0xFFF87171),
            modifier = Modifier.size(56.dp),
        )
        Text(
            "$appName is blocked",
            color = RefText,
            fontSize = 22.scaledSp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "FocusFlow is protecting your current focus plan.",
            color = RefSecondary,
            fontSize = 13.scaledSp,
            lineHeight = 19.scaledSp,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        FocusFlowPrimaryButton(
            text = "Go home",
            onClick = onGoHome,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "This restriction will lift when the active focus plan ends.",
            color = RefMuted,
            fontSize = 11.scaledSp,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
