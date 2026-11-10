package com.tbtechs.focusflow.ui.customrules

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import com.tbtechs.focusflow.enforcement.customrules.toExportText
import com.tbtechs.focusflow.ui.home.FocusFlowInternalHeader
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.scaledSp
import kotlinx.coroutines.delay

private val captureDelaysSeconds = listOf(5, 8, 12)

/**
 * "Add rule from screen", step 1. The user taps Start, switches to the other app, opens the
 * screen to block, and waits. The accessibility service captures it after the countdown.
 */
@Composable
fun CustomRuleCaptureScreen(
    viewModel: CustomRulesViewModel,
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenPick: () -> Unit,
    onOpenGuide: () -> Unit = {},
) {
    val capture by viewModel.capture.collectAsState()
    val context = LocalContext.current
    var delaySeconds by rememberSaveable { mutableStateOf(8) }
    var serviceMissing by remember {
        mutableStateOf(AppBlockerAccessibilityService.instance == null)
    }
    var countdownToken by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableIntStateOf(0) }

    // The user may enable the accessibility service and come back to this screen.
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

    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = RuleScreenInsets,
        topBar = {
            FocusFlowInternalHeader(
                title = "Capture a screen",
                subtitle = "Beta",
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
                    text = "How it works",
                    fontSize = 15.scaledSp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap Start, switch to the app, open the screen you want to block, and wait. FocusFlow captures it after the countdown.",
                    fontSize = 13.scaledSp,
                    lineHeight = 18.scaledSp,
                    color = DarkTextSecondary,
                )
            }

            RuleCard {
                Text(
                    text = "Countdown",
                    fontSize = 13.scaledSp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkTextPrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    captureDelaysSeconds.forEach { seconds ->
                        FilterChip(
                            selected = delaySeconds == seconds,
                            onClick = { delaySeconds = seconds },
                            label = { Text("$seconds seconds") },
                        )
                    }
                }
            }

            if (serviceMissing) {
                RuleCard {
                    Text(
                        text = "Turn on the FocusFlow accessibility service first",
                        fontSize = 13.scaledSp,
                        lineHeight = 18.scaledSp,
                        color = DarkTextPrimary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    RuleButton(
                        text = "Open permissions",
                        filled = false,
                        onClick = onOpenPermissions,
                    )
                }
            }

            RuleButton(
                text = if (secondsLeft > 0) "Capturing in $secondsLeft…" else "Start",
                enabled = secondsLeft == 0,
                onClick = {
                    val service = AppBlockerAccessibilityService.instance
                    if (service == null) {
                        serviceMissing = true
                    } else {
                        serviceMissing = false
                        viewModel.clearCapture()
                        service.captureSnapshotAfter(delaySeconds * 1_000L)
                        countdownToken += 1
                    }
                },
            )

            val result = capture
            if (result != null && secondsLeft == 0) {
                if (result.nodes.isEmpty()) {
                    RuleCard {
                        Text(
                            text = "Nothing was captured. Switch to the other app first, then wait for the countdown to finish.",
                            fontSize = 13.scaledSp,
                            lineHeight = 18.scaledSp,
                            color = DarkTextSecondary,
                        )
                    }
                } else {
                    RuleCard {
                        Text(
                            text = "Captured ${result.nodes.size} elements from ${result.packageName}",
                            fontSize = 13.scaledSp,
                            lineHeight = 18.scaledSp,
                            color = DarkTextPrimary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        RuleButton(
                            text = "Choose element",
                            onClick = onOpenPick,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        RuleButton(
                            text = "Export scan (beta)",
                            filled = false,
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "FocusFlow screen scan")
                                    putExtra(Intent.EXTRA_TEXT, result.toExportText())
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(send, "Export scan"))
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "The export lists every element and any text that was visible on that screen. Check it before sharing.",
                            fontSize = 11.scaledSp,
                            lineHeight = 15.scaledSp,
                            color = DarkTextMuted,
                        )
                    }
                }
            }

            RuleButton(
                text = "Guide: use an AI assistant (beta)",
                filled = false,
                onClick = onOpenGuide,
            )

            Text(
                text = "Passwords and typed text are never captured.",
                fontSize = 11.scaledSp,
                color = DarkTextMuted,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
