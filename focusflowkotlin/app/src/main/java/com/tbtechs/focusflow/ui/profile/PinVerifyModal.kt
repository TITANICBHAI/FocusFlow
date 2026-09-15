package com.tbtechs.focusflow.ui.profile

import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun PinVerifyModal(
    visible: Boolean,
    pinType: PinType,
    title: String = "Verify Password",
    description: String = "Enter your password to continue.",
    verify: (String) -> Boolean,
    onVerified: (String) -> Unit,
    onCancel: () -> Unit,
) {
    if (!visible) return
    var password by remember(visible) { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember(visible) { mutableStateOf(0) }

    fun confirm() {
        if (password.isBlank()) {
            error = "Enter your password to continue."
            return
        }
        if (verify(password)) {
            val verifiedPassword = password
            password = ""
            attempts = 0
            onVerified(verifiedPassword)
        } else {
            attempts += 1
            password = ""
            error = if (attempts >= 3) {
                "Incorrect password. Check where you stored it when you set it up."
            } else {
                "Incorrect password. ${3 - attempts} attempt${if (3 - attempts == 1) "" else "s"} left before hint."
            }
        }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        icon = { androidx.compose.material3.Icon(Icons.Outlined.Lock, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description, style = MaterialTheme.typography.bodySmall)
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        modifier = androidx.compose.ui.Modifier.weight(1f),
                        label = { Text(if (pinType == PinType.FOCUS) "Focus password" else "Defense password") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        isError = error != null,
                    )
                    TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Hide" else "Show") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = ::confirm) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}