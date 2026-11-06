package com.tbtechs.focusflow.ui.customrules

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.enforcement.customrules.DisableDecision
import com.tbtechs.focusflow.enforcement.customrules.RuleGuard
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.common.InAppNotice
import com.tbtechs.focusflow.ui.common.InAppNoticeStrip
import com.tbtechs.focusflow.ui.common.InAppNoticeTone
import com.tbtechs.focusflow.ui.defense.PinAction
import com.tbtechs.focusflow.ui.defense.PinPrompt
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.scaledSp

/** What the user is trying to do to an enabled rule. Each one needs the same permission. */
internal enum class GuardedChange(val title: String, val past: String) {
    DISABLE("Disable", "turned off"),
    EDIT("Edit", "edited"),
    DELETE("Delete", "deleted"),
}

/**
 * Applies the Defense tab's rule for turning a protection off (see `protectedToggle`):
 * refused while a Focus session or Standalone block runs, asks for the defense password when
 * password protection is on, otherwise allowed. Holds the PIN prompt and notice it may show.
 */
internal class DisableGuard(
    private val decision: () -> DisableDecision,
    private val verifyPin: (String) -> Boolean,
) {
    var pinPrompt by mutableStateOf<PinAction?>(null)
    var notice by mutableStateOf<InAppNotice?>(null)
    private var noticeId = 0

    fun request(change: GuardedChange, ruleName: String, action: () -> Unit) {
        when (decision()) {
            DisableDecision.ALLOW -> action()
            DisableDecision.DENIED_BLOCK_ACTIVE -> {
                noticeId += 1
                notice = InAppNotice(
                    id = noticeId,
                    message = "$ruleName can't be ${change.past} while a Focus session or Standalone block is running.",
                    tone = InAppNoticeTone.WARNING,
                )
            }
            DisableDecision.NEED_PIN -> {
                pinPrompt = PinAction(
                    title = "${change.title} $ruleName",
                    description = "Enter your defense password to ${change.title.lowercase()} $ruleName.",
                    action = action,
                    verify = verifyPin,
                )
            }
        }
    }
}

@Composable
internal fun rememberDisableGuard(
    settingsViewModel: SettingsViewModel,
    isFocusActive: Boolean,
): DisableGuard {
    val settings by settingsViewModel.settings.collectAsState()
    // Same expression as the Defense tab.
    val standaloneActive = settings.standaloneBlockActive &&
        settings.standaloneBlockPackages.isNotEmpty() &&
        settings.standaloneBlockUntilMs > System.currentTimeMillis()
    val latest = rememberUpdatedState(
        RuleGuard.decide(
            blockActive = isFocusActive || standaloneActive,
            pinProtectionEnabled = settings.pinProtectionEnabled,
        ),
    )
    return remember(settingsViewModel) {
        DisableGuard(
            decision = { latest.value },
            verifyPin = settingsViewModel::verifyPin,
        )
    }
}

/** Renders the guard's notice strip and PIN prompt. Call it inside the screen's Box. */
@Composable
internal fun DisableGuardHost(guard: DisableGuard, modifier: Modifier = Modifier) {
    InAppNoticeStrip(
        notice = guard.notice,
        onDismiss = { guard.notice = null },
        modifier = modifier,
    )
    guard.pinPrompt?.let { action ->
        PinPrompt(action = action, onClose = { guard.pinPrompt = null })
    }
}

internal val RuleDanger = Color(0xFFEF4444)

@Composable
internal fun RuleCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        content = content,
    )
}

@Composable
internal fun RuleButton(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    if (filled) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
        ) {
            Text(
                text = text,
                fontSize = 15.scaledSp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, DarkBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkTextPrimary),
        ) {
            Text(
                text = text,
                fontSize = 15.scaledSp,
                fontWeight = FontWeight.SemiBold,
                color = DarkTextPrimary,
            )
        }
    }
}

@Composable
internal fun RuleTextField(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    readOnly: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        readOnly = readOnly,
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = DarkSurfaceVariant,
            unfocusedContainerColor = DarkSurfaceVariant,
            focusedBorderColor = BrandPrimary,
            unfocusedBorderColor = DarkBorder,
            focusedTextColor = DarkTextPrimary,
            unfocusedTextColor = DarkTextPrimary,
        ),
    )
}
