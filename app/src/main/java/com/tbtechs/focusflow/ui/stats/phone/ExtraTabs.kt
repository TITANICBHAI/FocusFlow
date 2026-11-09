package com.tbtechs.focusflow.ui.stats.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.ui.stats.StatsFlatTabs
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary

/** The three tabs inside Extra. */
enum class ExtraTab(val label: String) { OVERVIEW("Overview"), APPS("Apps"), HABITS("Habits") }

/** Extra's top bar: back to Stats, the title, and one period menu (instead of a row of period buttons). */
@Composable
internal fun ExtraHeader(
    window: String,
    onSelectWindow: (String) -> Unit,
    onBack: () -> Unit,
    selectedTab: ExtraTab,
    onSelectTab: (ExtraTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth().background(DarkCard)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to Stats", tint = DarkTextPrimary)
            }
            Text(
                "Extra",
                modifier = Modifier.weight(1f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = DarkTextPrimary,
            )
            WindowMenu(window, onSelectWindow)
        }
        StatsFlatTabs(
            labels = ExtraTab.entries.map { it.label },
            selectedIndex = selectedTab.ordinal,
            onSelect = { onSelectTab(ExtraTab.entries[it]) },
        )
    }
}

@Composable
private fun WindowMenu(window: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = PHONE_WINDOW_OPTIONS.firstOrNull { it.first == window }?.second ?: PHONE_WINDOW_OPTIONS.first().second
    Box {
        TextButton(onClick = { open = true }) {
            Text(label, color = DarkTextPrimary, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Change period", tint = DarkTextMuted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PHONE_WINDOW_OPTIONS.forEach { (option, optionLabel) ->
                DropdownMenuItem(text = { Text(optionLabel) }, onClick = { open = false; onSelect(option) })
            }
        }
    }
}
