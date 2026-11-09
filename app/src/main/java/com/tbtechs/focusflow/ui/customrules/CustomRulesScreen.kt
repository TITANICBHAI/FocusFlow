package com.tbtechs.focusflow.ui.customrules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.enforcement.customrules.CustomScreenRule
import com.tbtechs.focusflow.enforcement.customrules.CustomScreenRules
import com.tbtechs.focusflow.enforcement.customrules.MatchMode
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.common.FocusFlowSwitch
import com.tbtechs.focusflow.ui.home.FocusFlowInternalHeader
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.scaledSp

/** List of custom screen rules. Reached from Settings and from the Defense tab. */
@Composable
fun CustomRulesScreen(
    viewModel: CustomRulesViewModel,
    settingsViewModel: SettingsViewModel,
    isFocusActive: Boolean,
    onBack: () -> Unit,
    onOpenCapture: () -> Unit,
    onOpenEdit: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenGuide: () -> Unit = {},
) {
    val rules by viewModel.rules.collectAsState()
    val guard = rememberDisableGuard(settingsViewModel, isFocusActive)
    val atLimit = rules.size >= CustomScreenRules.MAX_RULES

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            FocusFlowInternalHeader(
                title = "Custom Screen Rules",
                onBack = onBack,
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer(modifier = Modifier.height(2.dp)) }

                item {
                    RuleCard {
                        Text(
                            text = "Block one screen, not the whole app",
                            fontSize = 15.scaledSp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTextPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "A rule looks for specific elements on screen. When an app shows them, FocusFlow blocks it the same way it blocks YouTube Shorts. Turn each rule on or off in the Defense tab.",
                            fontSize = 12.scaledSp,
                            lineHeight = 18.scaledSp,
                            color = DarkTextSecondary,
                        )
                    }
                }

                if (rules.isEmpty()) {
                    item {
                        RuleCard {
                            Text(
                                text = "No rules yet. Add one from a screen you captured, or build one by hand.",
                                fontSize = 13.scaledSp,
                                lineHeight = 18.scaledSp,
                                color = DarkTextSecondary,
                            )
                        }
                    }
                } else {
                    items(items = rules, key = { it.id }) { rule ->
                        RuleRow(
                            rule = rule,
                            onOpen = {
                                viewModel.startDraft(rule.id)
                                onOpenEdit()
                            },
                            onToggle = { enabled ->
                                if (enabled) {
                                    viewModel.setEnabled(rule.id, true)
                                } else {
                                    guard.request(GuardedChange.DISABLE, rule.name) {
                                        viewModel.setEnabled(rule.id, false)
                                    }
                                }
                            },
                        )
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RuleButton(
                            text = "Add rule from screen",
                            enabled = !atLimit,
                            onClick = {
                                viewModel.startDraft(null)
                                viewModel.clearCapture()
                                onOpenCapture()
                            },
                        )
                        RuleButton(
                            text = "Add rule manually",
                            filled = false,
                            enabled = !atLimit,
                            onClick = {
                                viewModel.startDraft(null)
                                onOpenEdit()
                            },
                        )
                        RuleButton(
                            text = "Import / export",
                            filled = false,
                            onClick = onOpenTransfer,
                        )
                        RuleButton(
                            text = "Guide (beta) · make rules yourself or with an AI assistant",
                            filled = false,
                            onClick = onOpenGuide,
                        )
                        if (atLimit) {
                            Text(
                                text = "You have the maximum of ${CustomScreenRules.MAX_RULES} rules. Delete one to add another.",
                                fontSize = 12.scaledSp,
                                color = DarkTextMuted,
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }

            DisableGuardHost(
                guard = guard,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
            )
        }
    }
}

@Composable
private fun RuleRow(
    rule: CustomScreenRule,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.name,
                fontSize = 15.scaledSp,
                fontWeight = FontWeight.Bold,
                color = DarkTextPrimary,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = rule.packageName,
                fontSize = 12.scaledSp,
                color = DarkTextSecondary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            val count = rule.matchers.size
            val mode = if (rule.matchMode == MatchMode.ALL) "all" else "any"
            Text(
                text = "$count matcher${if (count == 1) "" else "s"} · match $mode",
                fontSize = 11.scaledSp,
                color = DarkTextMuted,
            )
        }
        FocusFlowSwitch(
            checked = rule.enabled,
            onCheckedChange = onToggle,
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = "Edit rule",
            tint = DarkTextMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}
