package com.tbtechs.focusflow.ui.customrules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.enforcement.customrules.CustomScreenRules
import com.tbtechs.focusflow.ui.home.FocusFlowInternalHeader
import com.tbtechs.focusflow.ui.support.GuideArticleSection
import com.tbtechs.focusflow.ui.support.GuideCallout
import com.tbtechs.focusflow.ui.support.GuideCodeBlock
import com.tbtechs.focusflow.ui.support.GuideFacts
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.scaledSp

/**
 * Screen rules guide (beta), laid out like the FocusFlow file guide: how to do it yourself, and a
 * prompt to copy for an AI assistant that writes importable rules from an exported scan.
 */
@Composable
fun CustomRulesGuideScreen(onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val prompt = remember {
        customRulesAgentPrompt(
            RulesGuideLimits(
                maxRules = CustomScreenRules.MAX_RULES,
                maxMatchers = CustomScreenRules.MAX_MATCHERS,
                maxFieldLength = CustomScreenRules.MAX_FIELD_LEN,
                maxImportChars = CustomScreenRules.MAX_IMPORT_CHARS,
                blockedPackages = CustomScreenRules.forbiddenPackages,
            ),
        )
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            FocusFlowInternalHeader(
                title = "Screen rules guide",
                subtitle = "Beta",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            GuideCallout("Beta", RULES_GUIDE_BETA_NOTE, highlighted = true)

            GuideArticleSection("1. Make a rule yourself") {
                GuideFacts(RULES_GUIDE_STEPS_YOU)
                Text(
                    "Choosing good elements",
                    color = DarkTextSecondary,
                    fontSize = 13.scaledSp,
                )
                GuideFacts(RULES_GUIDE_GOOD_ELEMENTS)
            }

            GuideArticleSection("2. Let an AI assistant write it") {
                Text(
                    "Handy when you do not want to pick elements by hand. The assistant reads an exported scan " +
                        "and returns rules you paste into FocusFlow.",
                    color = DarkTextSecondary,
                    fontSize = 14.scaledSp,
                    lineHeight = 21.scaledSp,
                )
                GuideFacts(RULES_GUIDE_STEPS_AGENT)
                RuleCard {
                    Text(
                        text = "AI screen-rule prompt",
                        fontSize = 15.scaledSp,
                        color = com.tbtechs.focusflow.ui.theme.DarkTextPrimary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                    RuleButton(
                        text = if (copied) "Prompt copied" else "Copy screen-rule prompt",
                        modifier = Modifier.padding(top = 8.dp),
                        onClick = {
                            clipboard.setText(AnnotatedString(prompt))
                            copied = true
                        },
                    )
                }
                GuideCallout("Optional", RULES_GUIDE_AGENT_NOTE)
                Text(
                    "The file the assistant should return looks like this:",
                    color = DarkTextSecondary,
                    fontSize = 13.scaledSp,
                )
                GuideCodeBlock(RULES_AGENT_EXAMPLE)
            }
        }
    }
}
