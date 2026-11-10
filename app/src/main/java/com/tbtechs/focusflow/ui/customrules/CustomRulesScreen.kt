package com.tbtechs.focusflow.ui.customrules

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.enforcement.customrules.CustomScreenRules
import com.tbtechs.focusflow.ui.home.FocusFlowInternalHeader
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.scaledSp

/**
 * Custom Screen Rules home. Holds the actions (my rules, add, import / export, guide); the rules
 * and their on/off switches live on [CustomRulesListScreen]. Reached from Settings and Defense.
 */
@Composable
fun CustomRulesScreen(
    viewModel: CustomRulesViewModel,
    onBack: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenCapture: () -> Unit,
    onOpenEdit: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenGuide: () -> Unit = {},
) {
    val rules by viewModel.rules.collectAsState()
    val atLimit = rules.size >= CustomScreenRules.MAX_RULES

    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = RuleScreenInsets,
        topBar = {
            FocusFlowInternalHeader(
                title = "Custom Screen Rules",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .ruleScreenBody(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            RuleCard {
                Text(
                    text = "Block one screen, not the whole app",
                    fontSize = 15.scaledSp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "A rule looks for specific elements on screen. Like Block YouTube Shorts and Block Instagram Reels, it only blocks while that screen is showing, and the rest of the app stays usable. Turn each rule on or off in My rules.",
                    fontSize = 12.scaledSp,
                    lineHeight = 18.scaledSp,
                    color = DarkTextSecondary,
                )
            }

            RuleButton(
                text = "My rules (${rules.size})",
                onClick = onOpenRules,
            )
            RuleButton(
                text = "Add rule from screen",
                filled = false,
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
                text = "Guide (Beta) - How to use",
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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
