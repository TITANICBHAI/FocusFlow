package com.tbtechs.focusflow.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Compose replacement for the React Native keyboard-aware ScrollView wrapper.
 * imePadding keeps the focused field and action buttons above the IME while
 * verticalScroll preserves the expected content behavior.
 */
@Composable
fun KeyboardAwareScrollViewCompat(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .imePadding()
            .verticalScroll(scrollState),
    ) {
        content()
    }
}