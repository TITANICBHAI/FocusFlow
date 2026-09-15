package com.tbtechs.focusflow.ui.focus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

private val extendOptions = listOf(10, 15, 20, 30, 45, 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtendModal(taskName: String, onDismiss: () -> Unit, onExtend: (Int) -> Unit) {
    var extending by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { if (!extending) onDismiss() }) {
        Column {
            Text("Need more time?", style = MaterialTheme.typography.headlineSmall)
            Text("Choose how much extra time to add to $taskName. Subsequent tasks will shift forward.")
            Row {
                extendOptions.forEach { minutes ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            extending = true
                            onExtend(minutes)
                        },
                        enabled = !extending,
                        label = { Text("+$minutes m") },
                    )
                }
            }
            Button(onClick = onDismiss, enabled = !extending) { Text("Cancel") }
        }
    }
}
