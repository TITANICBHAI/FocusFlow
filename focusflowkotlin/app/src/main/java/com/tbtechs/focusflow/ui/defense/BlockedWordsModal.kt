package com.tbtechs.focusflow.ui.defense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedWordsModal(
    visible: Boolean,
    words: List<String>,
    locked: Boolean,
    requireDefensePin: Boolean,
    onSave: (List<String>) -> Unit,
    onClose: () -> Unit,
    verifyPin: ((String) -> Boolean)? = null,
) {
    if (!visible) return
    var localWords by remember(visible, words) { mutableStateOf(words) }
    var input by remember(visible) { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var pinPrompt by remember { mutableStateOf<PendingWordAction?>(null) }

    fun requestRemoval(action: () -> Unit) {
        if (!requireDefensePin || verifyPin == null) action()
        else pinPrompt = PendingWordAction(action)
    }

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.TextFields, contentDescription = null)
                    Text("Blocked Keywords", style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close") }
            }
            Text(
                if (locked) "Block is active — existing keywords are locked. You can add new keywords."
                else "If a word appears during an active block, FocusFlow sends the app home.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = if (locked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Add a keyword") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val word = input.trim().lowercase()
                        if (word.isNotEmpty() && word !in localWords) {
                            localWords = localWords + word
                            input = ""
                        }
                    },
                    enabled = input.trim().isNotEmpty(),
                ) { Icon(Icons.Outlined.Add, "Add") }
            }
            Text(
                "${localWords.size} keyword${if (localWords.size == 1) "" else "s"}",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelLarge,
            )
            LazyColumn(modifier = Modifier.weight(1f, fill = false).padding(16.dp)) {
                items(localWords, key = { it }) { word ->
                    FilterChip(
                        selected = true,
                        onClick = {
                            if (!locked) requestRemoval { localWords = localWords - word }
                        },
                        label = { Text(word) },
                        leadingIcon = {
                            Icon(
                                if (locked) Icons.Outlined.Lock else Icons.Outlined.Close,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
            if (localWords.size > 1 && !locked) {
                TextButton(onClick = { requestRemoval { confirmClear = true } }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Text("Clear All Keywords")
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) { Text("Cancel") }
                Button(onClick = { onSave(localWords); onClose() }) { Text("Save") }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear All Keywords") },
            text = { Text("Remove all blocked keywords?") },
            confirmButton = {
                Button(onClick = { localWords = emptyList(); confirmClear = false }) { Text("Clear All") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
    pinPrompt?.let { pending ->
        PinPrompt(
            action = PinAction("Defense Password Required", "Enter your defense password to remove keywords.", pending.action),
            verify = verifyPin ?: { false },
            onClose = { pinPrompt = null },
        )
    }
}

private data class PendingWordAction(val action: () -> Unit)
