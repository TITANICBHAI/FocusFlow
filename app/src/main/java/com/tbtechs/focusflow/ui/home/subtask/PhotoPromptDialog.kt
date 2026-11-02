package com.tbtechs.focusflow.ui.home.subtask

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.model.SubtaskPhotoSlots
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.ui.theme.scaledSp

private const val PROMPT_PREFS = "photo_prompt_prefs"
private const val PROMPT_STYLE_KEY = "style"
private const val MAX_STYLE_CHARS = 200

/**
 * Shows the whole text that "Copy" will put on the clipboard, so the person can read it first
 * (the briefs come from a file that may have been written by someone else).
 */
@Composable
internal fun PhotoPromptDialog(task: Task, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val prefs = remember(context) { context.getSharedPreferences(PROMPT_PREFS, Context.MODE_PRIVATE) }
    var style by remember { mutableStateOf(prefs.getString(PROMPT_STYLE_KEY, "").orEmpty()) }
    var copied by remember { mutableStateOf(false) }
    val slots = remember(task.subtasks) { SubtaskPhotoSlots.collect(task.subtasks) }
    val prompt = remember(task.title, slots, style) { PhotoPromptBuilder.build(task.title, slots, style) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Picture prompts", fontSize = 20.scaledSp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Copy this list and give it to any image tool or assistant. This is everything that will be copied.",
                    fontSize = 13.scaledSp,
                )
                OutlinedTextField(
                    value = style,
                    onValueChange = { style = it.take(MAX_STYLE_CHARS); copied = false },
                    label = { Text("Picture style (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(prompt, fontSize = 12.scaledSp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    prefs.edit().putString(PROMPT_STYLE_KEY, style).apply()
                    clipboard.setText(AnnotatedString(prompt))
                    copied = true
                },
            ) { Text(if (copied) "Copied" else "Copy", fontSize = 14.scaledSp) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", fontSize = 14.scaledSp) }
        },
    )
}
