package com.tbtechs.focusflow.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

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
    Switch(
        checked = isDark,
        onCheckedChange = { onToggle() },
        thumbContent = {
            Text(
                text = if (isDark) "Night" else "Day",
                style = MaterialTheme.typography.labelSmall,
            )
        },
    )
}
