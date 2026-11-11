package com.tbtechs.focusflow.ui.customrules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import com.tbtechs.focusflow.enforcement.customrules.CustomScreenRule
import com.tbtechs.focusflow.enforcement.customrules.CustomScreenRules
import com.tbtechs.focusflow.enforcement.customrules.RuleTestHolder
import com.tbtechs.focusflow.enforcement.customrules.RuleTestResult
import com.tbtechs.focusflow.enforcement.customrules.RuleTestText
import com.tbtechs.focusflow.enforcement.customrules.RuleTestVerdict
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.scaledSp
import kotlinx.coroutines.delay

private val testDelaysSeconds = listOf(5, 8, 12)

/**
 * "Test this rule", shown in the rule editor just above Save. It tests the editor's current rule
 * (saved or not) against whatever app is in front after a countdown, and says whether the rule
 * would block that screen and, for each matcher, what it found. Nothing is blocked during a test.
 * A result is shown only while it still matches the rule on screen; editing the rule hides it.
 */
@Composable
fun CustomRuleTestCard(rule: CustomScreenRule) {
    val context = LocalContext.current
    val result by RuleTestHolder.result.collectAsState()
    var delaySeconds by rememberSaveable { mutableStateOf(8) }
    var serviceMissing by remember { mutableStateOf(AppBlockerAccessibilityService.instance == null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var countdownToken by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableIntStateOf(0) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceMissing = AppBlockerAccessibilityService.instance == null
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(countdownToken) {
        if (countdownToken == 0) return@LaunchedEffect
        var left = delaySeconds
        while (left > 0) {
            secondsLeft = left
            delay(1_000L)
            left -= 1
        }
        secondsLeft = 0
    }

    RuleCard {
        Text(
            text = "Test this rule",
            fontSize = 15.scaledSp,
            fontWeight = FontWeight.Bold,
            color = DarkTextPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Tap Start, switch to the app, open the screen this rule should block with its tab selected, and wait. FocusFlow tells you if the rule would block it. Nothing is blocked during a test, and you can test before saving.",
            fontSize = 12.scaledSp,
            lineHeight = 18.scaledSp,
            color = DarkTextSecondary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            testDelaysSeconds.forEach { seconds ->
                FilterChip(
                    selected = delaySeconds == seconds,
                    onClick = { delaySeconds = seconds },
                    label = { Text("$seconds seconds") },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (serviceMissing) {
            Text(
                text = "Turn on the FocusFlow accessibility service first (Settings, Permissions).",
                fontSize = 12.scaledSp,
                lineHeight = 18.scaledSp,
                color = DarkTextPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        RuleButton(
            text = if (secondsLeft > 0) "Testing in $secondsLeft…" else "Start test",
            enabled = secondsLeft == 0,
            onClick = {
                val service = AppBlockerAccessibilityService.instance
                if (service == null) {
                    serviceMissing = true
                    return@RuleButton
                }
                serviceMissing = false
                // Only the package and matchers matter for a test, so a missing name is fine.
                val error = CustomScreenRules.validate(
                    rule.copy(name = rule.name.ifBlank { "Test" }),
                    context.packageName,
                )
                problem = error
                if (error == null) {
                    RuleTestHolder.result.value = null
                    service.testRuleAfter(rule, delaySeconds * 1_000L)
                    countdownToken += 1
                }
            },
        )
        problem?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = it, fontSize = 12.scaledSp, lineHeight = 18.scaledSp, color = RuleDanger)
        }

        val shown: RuleTestResult? = result?.takeIf { it.isFor(rule) && secondsLeft == 0 }
        if (shown != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = RuleTestText.headline(shown),
                fontSize = 14.scaledSp,
                lineHeight = 20.scaledSp,
                fontWeight = FontWeight.SemiBold,
                color = if (shown.verdict == RuleTestVerdict.WOULD_BLOCK) Color(0xFF10B981) else DarkTextPrimary,
            )
            val lines = RuleTestText.lines(shown)
            if (lines.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                lines.forEach { line ->
                    Text(
                        text = line,
                        fontSize = 12.scaledSp,
                        lineHeight = 17.scaledSp,
                        color = DarkTextSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (shown.verdict == RuleTestVerdict.WOULD_BLOCK || shown.verdict == RuleTestVerdict.WOULD_NOT_BLOCK) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Also test one of the app's other screens, such as home or search. It should say the rule would not block it.",
                    fontSize = 11.scaledSp,
                    lineHeight = 15.scaledSp,
                    color = DarkTextMuted,
                )
            }
        } else if (result != null && result?.testedRule?.id == rule.id && secondsLeft == 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The rule changed since the last test. Test again.",
                fontSize = 11.scaledSp,
                color = DarkTextMuted,
            )
        }
    }
}
