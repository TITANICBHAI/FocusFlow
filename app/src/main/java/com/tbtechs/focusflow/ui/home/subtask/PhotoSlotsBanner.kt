package com.tbtechs.focusflow.ui.home.subtask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.ui.theme.scaledSp

/**
 * Shown on a task while some of its items or steps still say "Photo needed".
 * It only reports counts and offers three shortcuts; it never starts a download by itself.
 */
@Composable
internal fun PhotoSlotsBanner(
    slotCount: Int,
    linkCount: Int,
    canDownload: Boolean,
    canAddInOrder: Boolean,
    onDownload: () -> Unit,
    onAddInOrder: () -> Unit,
    onCopyPrompts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (slotCount == 1) "1 picture needed" else "$slotCount pictures needed",
                fontSize = 15.scaledSp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Open an item's photo menu to add one, or use a shortcut below.",
                fontSize = 13.scaledSp,
            )
            if (linkCount > 0 && canDownload) {
                OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (linkCount == 1) "Download 1 picture from its link" else "Download $linkCount pictures from their links",
                        fontSize = 13.scaledSp,
                    )
                }
            }
            if (slotCount >= 2 && canAddInOrder) {
                OutlinedButton(onClick = onAddInOrder, modifier = Modifier.fillMaxWidth()) {
                    Text("Add several pictures in order", fontSize = 13.scaledSp)
                }
            }
            OutlinedButton(onClick = onCopyPrompts, modifier = Modifier.fillMaxWidth()) {
                Text("Copy picture prompts", fontSize = 13.scaledSp)
            }
        }
    }
}
