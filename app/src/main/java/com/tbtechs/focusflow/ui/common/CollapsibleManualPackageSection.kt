package com.tbtechs.focusflow.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.scaledSp

private val AndroidPackageName = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

internal fun normalizeManualPackageName(value: String): String? =
    value.trim()
        .lowercase()
        .takeIf(AndroidPackageName::matches)

/**
 * Collapsible manual package entry used by block and allowance app lists.
 *
 * The caller decides whether adding a valid package selects it immediately or
 * merely makes it available in the list. Returning an error keeps the input
 * open and displays that explanation to the user.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsibleManualPackageSection(
    modifier: Modifier = Modifier,
    helperText: String? = null,
    onAddPackage: (String) -> String?,
) {
    var expanded by remember { mutableStateOf(false) }
    var packageInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = {
                expanded = !expanded
                error = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = DarkCard,
                contentColor = DarkTextPrimary,
            ),
            border = BorderStroke(1.dp, DarkBorder),
        ) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = null,
                tint = DarkTextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Advanced", fontSize = 14.scaledSp, modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) {
                    Icons.Outlined.KeyboardArrowUp
                } else {
                    Icons.Outlined.KeyboardArrowDown
                },
                contentDescription = if (expanded) "Collapse Advanced" else "Expand Advanced",
                tint = DarkTextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }

        if (expanded) {
            helperText?.let { helper ->
                Text(
                    text = helper,
                    color = DarkTextSecondary,
                    fontSize = 12.scaledSp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = packageInput,
                    onValueChange = {
                        packageInput = it
                        error = null
                    },
                    placeholder = { Text("com.example.app", color = DarkTextMuted) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DarkTextPrimary,
                        unfocusedTextColor = DarkTextPrimary,
                        focusedBorderColor = BrandPrimary,
                        unfocusedBorderColor = DarkBorder,
                    ),
                )
                Button(
                    onClick = {
                        val normalized = normalizeManualPackageName(packageInput)
                        if (normalized == null) {
                            error = "Enter a valid Android package name, such as com.example.app."
                        } else {
                            val addError = onAddPackage(normalized)
                            if (addError == null) {
                                packageInput = ""
                                error = null
                            } else {
                                error = addError
                            }
                        }
                    },
                    enabled = packageInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Text("Add")
                }
            }
            error?.let { message ->
                Text(
                    text = message,
                    color = Color(0xFFF87171),
                    fontSize = 12.scaledSp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
