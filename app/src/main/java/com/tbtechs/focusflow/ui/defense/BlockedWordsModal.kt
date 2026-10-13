package com.tbtechs.focusflow.ui.defense

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
import com.tbtechs.focusflow.ui.theme.scaledSp
import com.tbtechs.focusflow.ui.home.FocusFlowModalField

private class PendingWordAction(val action: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val dimensions = LocalFocusFlowDimensions.current
    var localWords by remember(visible, words) { mutableStateOf(words) }
    var input by remember(visible) { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var pinPrompt by remember { mutableStateOf<PendingWordAction?>(null) }
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<Pair<String, String>?>(null) }
    fun requestRemoval(action: () -> Unit) {
        when {
            !requireDefensePin -> action()
            verifyPin != null -> {
                pin = ""
                pinError = null
                pinPrompt = PendingWordAction(action)
            }
            else -> message = "Protection unavailable" to "A defense password is required, but no verifier is available."
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = DarkBackground,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = dimensions.modalPadding, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
            ) {
            // Header: Cancel / Aa Blocked Keywords / Save
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) {
                    Text("Cancel", color = DarkTextSecondary, fontSize = 13.scaledSp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.TextFields,
                        contentDescription = null,
                        tint = BrandPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Blocked Keywords",
                        fontSize = 15.scaledSp,
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary,
                    )
                }
                TextButton(onClick = { onSave(localWords); onClose() }) {
                    Text(
                        text = "Save",
                        color = BrandPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.scaledSp,
                    )
                }
            }

            if (locked) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF451A03))
                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                        Text(
                            "Block is active — existing keywords are locked. You can add new keywords.",
                            color = Color(0xFFFBBF24),
                            fontSize = 11.scaledSp,
                        )
                    }
                }
            } else {
                Text(
                    text = "If a blocked word appears in a URL, search, or on-screen text during an active block, FocusFlow redirects away.",
                    fontSize = 12.scaledSp,
                    lineHeight = 16.scaledSp,
                    color = DarkTextSecondary,
                )
            }

            // Keyword Input Row matching 3e_7
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Add word or phrase…", color = DarkTextMuted, fontSize = 14.scaledSp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        focusedBorderColor = BrandPrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = DarkTextPrimary,
                        unfocusedTextColor = DarkTextPrimary,
                    ),
                )
                Button(
                    onClick = {
                        val word = input.trim().lowercase()
                        if (word.isNotEmpty()) {
                            if (word in localWords) {
                                message = "Already added" to "\"$word\" is already in the list."
                            } else {
                                localWords = localWords + word
                                input = ""
                            }
                        }
                    },
                    enabled = input.trim().isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add keyword", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }

            // Keyword count label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (localWords.isEmpty()) "No keywords added" else "${localWords.size} keyword${if (localWords.size == 1) "" else "s"}",
                    fontSize = 13.scaledSp,
                    fontWeight = FontWeight.Medium,
                    color = DarkTextSecondary,
                )
                if (localWords.isNotEmpty() && !locked) {
                    TextButton(onClick = { requestRemoval { confirmClear = true } }) {
                        Text("Clear all", color = Color(0xFFEF4444), fontSize = 12.scaledSp)
                    }
                }
            }

            // Keyword Tag Chips matching 3e_7
            if (localWords.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.TextFields,
                            contentDescription = null,
                            tint = DarkTextMuted,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No keywords yet",
                        fontSize = 15.scaledSp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkTextPrimary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add trigger words or phrases above to block them.",
                        fontSize = 12.scaledSp,
                        color = DarkTextMuted,
                    )
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    localWords.forEach { word ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceVariant)
                                .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    if (!locked) {
                                        requestRemoval { localWords = localWords - word }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = word,
                                    fontSize = 13.scaledSp,
                                    color = DarkTextPrimary,
                                )
                                Icon(
                                    imageVector = if (locked) Icons.Outlined.Lock else Icons.Outlined.Clear,
                                    contentDescription = "Remove",
                                    tint = if (locked) Color(0xFFF59E0B) else DarkTextSecondary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Clear All Keywords", fontWeight = FontWeight.Bold) },
            text = { Text("Remove all blocked keywords from this list?", fontSize = 13.scaledSp, lineHeight = 18.scaledSp) },
            confirmButton = {
                Button(
                    onClick = {
                        localWords = emptyList()
                        confirmClear = false
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text("Clear All", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmClear = false },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }

    pinPrompt?.let { pending ->
        AlertDialog(
            onDismissRequest = { pinPrompt = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Defense Password Required", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter your defense password to remove keywords.", fontSize = 13.scaledSp, lineHeight = 18.scaledSp)
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it; pinError = null },
                        label = { Text("Defense Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedBorderColor = BrandPrimary,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = DarkTextPrimary,
                            unfocusedTextColor = DarkTextPrimary,
                        ),
                    )
                    pinError?.let { Text(it, color = Color(0xFFF87171), fontSize = 12.scaledSp) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (verifyPin?.invoke(pin) == true) {
                            pinPrompt = null
                            pending.action()
                        } else {
                            pinError = "Incorrect defense password."
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text("Confirm", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pinPrompt = null },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }

    message?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { message = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(body, fontSize = 13.scaledSp, lineHeight = 18.scaledSp) },
            confirmButton = {
                Button(
                    onClick = { message = null },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text("OK", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }
}
