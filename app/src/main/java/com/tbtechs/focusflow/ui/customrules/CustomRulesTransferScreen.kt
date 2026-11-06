package com.tbtechs.focusflow.ui.customrules

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.enforcement.customrules.ImportResult
import com.tbtechs.focusflow.ui.home.FocusFlowInternalHeader
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.scaledSp

/** Export the rules as text and import rules from text, all on one screen. */
@Composable
fun CustomRulesTransferScreen(
    viewModel: CustomRulesViewModel,
    onBack: () -> Unit,
) {
    val rules by viewModel.rules.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val exportText = remember(rules) { viewModel.exportJson() }

    var importText by rememberSaveable { mutableStateOf("") }
    var preview by remember { mutableStateOf<ImportResult?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            FocusFlowInternalHeader(
                title = "Import / export rules",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // ── Export ──
            RuleCard {
                Text(
                    text = "EXPORT",
                    fontSize = 11.scaledSp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextMuted,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (rules.isEmpty()) {
                    Text(
                        text = "No rules to export yet.",
                        fontSize = 13.scaledSp,
                        color = DarkTextSecondary,
                    )
                } else {
                    RuleTextField(
                        value = exportText,
                        label = "Rules (JSON)",
                        singleLine = false,
                        minLines = 4,
                        maxLines = 8,
                        readOnly = true,
                        onValueChange = {},
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    RuleButton(
                        text = if (copied) "Copied" else "Copy",
                        filled = false,
                        onClick = {
                            clipboard.setText(AnnotatedString(exportText))
                            copied = true
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    RuleButton(
                        text = "Share",
                        filled = false,
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, exportText)
                            }
                            context.startActivity(Intent.createChooser(send, "Share rules"))
                        },
                    )
                }
            }

            // ── Import ──
            RuleCard {
                Text(
                    text = "IMPORT",
                    fontSize = 11.scaledSp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextMuted,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Only import rules from people you trust. Imported rules are switched off until you turn them on.",
                    fontSize = 12.scaledSp,
                    lineHeight = 18.scaledSp,
                    color = DarkTextSecondary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                RuleTextField(
                    value = importText,
                    label = "Paste rules (JSON)",
                    singleLine = false,
                    minLines = 4,
                    maxLines = 8,
                    onValueChange = {
                        importText = it
                        preview = null
                        message = null
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                RuleButton(
                    text = "Preview",
                    filled = false,
                    enabled = importText.isNotBlank(),
                    onClick = {
                        message = null
                        preview = viewModel.previewImport(importText)
                    },
                )

                val result = preview
                if (result != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (result.accepted.isNotEmpty()) {
                        Text(
                            text = "Can import:",
                            fontSize = 13.scaledSp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkTextPrimary,
                        )
                        result.accepted.forEach { r ->
                            Text(
                                text = "• ${r.name} (${r.packageName})",
                                fontSize = 12.scaledSp,
                                color = DarkTextSecondary,
                            )
                        }
                    }
                    if (result.rejected.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Can't import:",
                            fontSize = 13.scaledSp,
                            fontWeight = FontWeight.SemiBold,
                            color = RuleDanger,
                        )
                        result.rejected.forEach { reason ->
                            Text(
                                text = "• $reason",
                                fontSize = 12.scaledSp,
                                color = DarkTextSecondary,
                            )
                        }
                    }
                    if (result.accepted.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        RuleButton(
                            text = "Import ${result.accepted.size} rule${if (result.accepted.size == 1) "" else "s"}",
                            onClick = {
                                val done = viewModel.import(importText)
                                val n = done.accepted.size
                                message = if (n == 0) {
                                    "Nothing was imported."
                                } else {
                                    "Imported $n rule${if (n == 1) "" else "s"}. Turn them on in the Defense tab."
                                }
                                preview = null
                                importText = ""
                            },
                        )
                    }
                }
                message?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = it, fontSize = 13.scaledSp, color = DarkTextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
