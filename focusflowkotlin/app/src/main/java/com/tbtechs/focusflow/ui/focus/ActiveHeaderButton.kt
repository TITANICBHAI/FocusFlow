package com.tbtechs.focusflow.ui.focus

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.FocusSession

/** Shared route entry point for the live Active protections dashboard. */
@Composable
fun ActiveHeaderButton(
    focusSession: FocusSession?,
    settings: AppSettings,
    onOpenActiveBlocks: () -> Unit,
) {
    val hasActiveProtection = focusSession?.isActive == true ||
        (settings.standaloneBlockActive && settings.standaloneBlockPackages.isNotEmpty() && settings.standaloneBlockUntilMs > System.currentTimeMillis()) ||
        (settings.alwaysBlockEnabled && settings.alwaysBlockPackages.isNotEmpty()) ||
        settings.blockedWords.isNotEmpty() ||
        settings.recurringBlockSchedules.isNotEmpty() ||
        settings.networkBlockEnabled
    IconButton(onClick = onOpenActiveBlocks) {
        Icon(
            imageVector = if (hasActiveProtection) Icons.Filled.Favorite else Icons.Outlined.Favorite,
            contentDescription = "Open Active blocks",
            tint = if (hasActiveProtection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
