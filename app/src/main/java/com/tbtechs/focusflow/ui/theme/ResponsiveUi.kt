package com.tbtechs.focusflow.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration

@Immutable
data class FocusFlowDimensions(
    val screenPadding: Dp,
    val sectionSpacing: Dp,
    val cardPadding: Dp,
    val modalPadding: Dp,
    val contentMaxWidth: Dp,
    val drawerMaxWidth: Dp,
    val bottomContentPadding: Dp,
)

val LocalFocusFlowDimensions = staticCompositionLocalOf {
    FocusFlowDimensions(
        screenPadding = 14.dp,
        sectionSpacing = 12.dp,
        cardPadding = 12.dp,
        modalPadding = 16.dp,
        contentMaxWidth = 720.dp,
        drawerMaxWidth = 360.dp,
        bottomContentPadding = 88.dp,
    )
}

@Composable
fun focusFlowDimensions(): FocusFlowDimensions = LocalFocusFlowDimensions.current

@Composable
fun Modifier.focusFlowPagePadding(): Modifier =
    padding(horizontal = focusFlowDimensions().screenPadding)

@Composable
fun Modifier.focusFlowModalContent(): Modifier =
    fillMaxWidth()
        .widthIn(max = focusFlowDimensions().contentMaxWidth)
        .imePadding()
        .navigationBarsPadding()

@Composable
fun FocusFlowCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(focusFlowDimensions().cardPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

internal fun focusFlowDimensionsForWidth(widthDp: Int): FocusFlowDimensions = when {
    widthDp < 360 -> FocusFlowDimensions(
        screenPadding = 12.dp,
        sectionSpacing = 10.dp,
        cardPadding = 10.dp,
        modalPadding = 14.dp,
        contentMaxWidth = 680.dp,
        drawerMaxWidth = 320.dp,
        bottomContentPadding = 80.dp,
    )
    widthDp >= 600 -> FocusFlowDimensions(
        screenPadding = 20.dp,
        sectionSpacing = 16.dp,
        cardPadding = 16.dp,
        modalPadding = 24.dp,
        contentMaxWidth = 760.dp,
        drawerMaxWidth = 400.dp,
        bottomContentPadding = 104.dp,
    )
    else -> FocusFlowDimensions(
        screenPadding = 14.dp,
        sectionSpacing = 12.dp,
        cardPadding = 12.dp,
        modalPadding = 16.dp,
        contentMaxWidth = 720.dp,
        drawerMaxWidth = 360.dp,
        bottomContentPadding = 88.dp,
    )
}

@Composable
internal fun currentFocusFlowDimensions(): FocusFlowDimensions =
    focusFlowDimensionsForWidth(LocalConfiguration.current.screenWidthDp)