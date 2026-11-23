package com.tbtechs.focusflow.ui.customrules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.enforcement.customrules.CustomScreenRule
import com.tbtechs.focusflow.enforcement.customrules.CustomScreenRules
import com.tbtechs.focusflow.enforcement.customrules.MatchMode
import com.tbtechs.focusflow.enforcement.customrules.NodeMatcher
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.home.FocusFlowInternalHeader
import com.tbtechs.focusflow.ui.theme.StatusReady
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.scaledSp

/** Create or edit one rule. Bound to [CustomRulesViewModel.draft]. */
@Composable
fun CustomRuleEditScreen(
    viewModel: CustomRulesViewModel,
    settingsViewModel: SettingsViewModel,
    isFocusActive: Boolean,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    val draft by viewModel.draft.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val capture by viewModel.capture.collectAsState()
    val guard = rememberDisableGuard(settingsViewModel, isFocusActive)
    var finished by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    // The draft is lost if the process was killed while this screen was open: start an empty one.
    LaunchedEffect(draft == null, finished) {
        if (draft == null && !finished) viewModel.startDraft(null)
    }

    val rule = draft
    if (rule == null) {
        Scaffold(
            containerColor = DarkBackground,
            topBar = { FocusFlowInternalHeader(title = "Rule", onBack = onBack) },
        ) { padding -> Box(modifier = Modifier.fillMaxSize().padding(padding)) }
        return
    }

    val stored = rules.firstOrNull { it.id == rule.id }
    val isExisting = stored != null
    val storedIsEnabled = stored?.enabled == true

    fun doSave() {
        val error = viewModel.save(rule)
        if (error != null) {
            saveError = error
        } else {
            finished = true
            onFinished()
        }
    }

    fun doDelete() {
        finished = true
        viewModel.delete(rule.id)
        onFinished()
    }

    fun updateMatcher(index: Int, transform: (NodeMatcher) -> NodeMatcher) {
        saveError = null
        viewModel.updateDraft { r: CustomScreenRule ->
            r.copy(matchers = r.matchers.mapIndexed { i, m -> if (i == index) transform(m) else m })
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = RuleScreenInsets,
        topBar = {
            FocusFlowInternalHeader(
                title = if (isExisting) "Edit rule" else "New rule",
                onBack = onBack,
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(modifier = Modifier.height(2.dp))

                RuleCard {
                    RuleTextField(
                        value = rule.name,
                        label = "Rule name",
                        onValueChange = { v ->
                            saveError = null
                            viewModel.updateDraft { it.copy(name = v) }
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    RuleTextField(
                        value = rule.packageName,
                        label = "App package (e.g. com.instagram.android)",
                        onValueChange = { v ->
                            saveError = null
                            viewModel.updateDraft { it.copy(packageName = v) }
                        },
                    )
                    val capturedPackage = capture?.packageName.orEmpty()
                    if (capturedPackage.isNotBlank() && capturedPackage != rule.packageName) {
                        TextButton(
                            onClick = {
                                saveError = null
                                viewModel.updateDraft { it.copy(packageName = capturedPackage) }
                            },
                        ) {
                            Text("Use captured app ($capturedPackage)", fontSize = 13.scaledSp)
                        }
                    }
                }

                RuleCard {
                    Text(
                        text = "Block when",
                        fontSize = 13.scaledSp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkTextPrimary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = rule.matchMode == MatchMode.ALL,
                            onClick = { viewModel.updateDraft { it.copy(matchMode = MatchMode.ALL) } },
                            label = { Text("All matchers match") },
                        )
                        FilterChip(
                            selected = rule.matchMode == MatchMode.ANY,
                            onClick = { viewModel.updateDraft { it.copy(matchMode = MatchMode.ANY) } },
                            label = { Text("Any matcher matches") },
                        )
                    }
                }

                rule.matchers.forEachIndexed { index, matcher ->
                    RuleCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Matcher ${index + 1}",
                                fontSize = 14.scaledSp,
                                fontWeight = FontWeight.Bold,
                                color = DarkTextPrimary,
                            )
                            IconButton(
                                onClick = {
                                    saveError = null
                                    viewModel.updateDraft { r ->
                                        r.copy(matchers = r.matchers.filterIndexed { i, _ -> i != index })
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Remove matcher ${index + 1}",
                                    tint = RuleDanger,
                                )
                            }
                        }
                        RuleTextField(
                            value = matcher.viewIdSuffix.orEmpty(),
                            label = "View id suffix",
                            onValueChange = { v -> updateMatcher(index) { it.copy(viewIdSuffix = v.ifEmpty { null }) } },
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        RuleTextField(
                            value = matcher.className.orEmpty(),
                            label = "Class name",
                            onValueChange = { v -> updateMatcher(index) { it.copy(className = v.ifEmpty { null }) } },
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        RuleTextField(
                            value = matcher.textContains.orEmpty(),
                            label = "Text contains",
                            onValueChange = { v -> updateMatcher(index) { it.copy(textContains = v.ifEmpty { null }) } },
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        RuleTextField(
                            value = matcher.descContains.orEmpty(),
                            label = "Description contains",
                            onValueChange = { v -> updateMatcher(index) { it.copy(descContains = v.ifEmpty { null }) } },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SelectedOnlyRow(
                            checked = matcher.selectedOnly,
                            onCheckedChange = { on -> updateMatcher(index) { it.copy(selectedOnly = on) } },
                        )
                    }
                }

                RuleButton(
                    text = "Add matcher",
                    filled = false,
                    enabled = rule.matchers.size < CustomScreenRules.MAX_MATCHERS,
                    onClick = {
                        saveError = null
                        viewModel.updateDraft { it.copy(matchers = it.matchers + NodeMatcher(selectedOnly = true)) }
                    },
                )
                Text(
                    text = "A matcher needs a view id, text or description. A class name alone is too broad.",
                    fontSize = 11.scaledSp,
                    color = DarkTextMuted,
                )

                CustomRuleTestCard(rule = rule)

                RuleButton(
                    text = "Save",
                    onClick = {
                        saveError = null
                        if (storedIsEnabled) {
                            guard.request(GuardedChange.EDIT, stored?.name ?: rule.name) { doSave() }
                        } else {
                            doSave()
                        }
                    },
                )
                saveError?.let {
                    Text(text = it, fontSize = 13.scaledSp, color = RuleDanger)
                }

                if (isExisting) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Delete rule", color = RuleDanger, fontSize = 14.scaledSp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            DisableGuardHost(
                guard = guard,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Delete rule?") },
            text = { Text("“${rule.name.ifBlank { "This rule" }}” will be removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        if (storedIsEnabled) {
                            guard.request(GuardedChange.DELETE, stored?.name ?: rule.name) { doDelete() }
                        } else {
                            doDelete()
                        }
                    },
                ) { Text("Delete", color = RuleDanger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }
}

/**
 * "Only when selected" as a full-width row with a switch and a plain On / Off line, so it reads as
 * a setting and not as a stray chip. The whole row is tappable.
 */
@Composable
private fun SelectedOnlyRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (checked) StatusReady.copy(alpha = 0.14f) else DarkBackground)
            .border(1.dp, if (checked) StatusReady else DarkBorder, shape)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Only when selected",
                fontSize = 14.scaledSp,
                fontWeight = FontWeight.Bold,
                color = DarkTextPrimary,
            )
            Text(
                text = if (checked) {
                    "On: counts only while this tab or button is the highlighted one."
                } else {
                    "Off: counts whenever it is on screen, highlighted or not."
                },
                fontSize = 11.scaledSp,
                color = DarkTextSecondary,
            )
        }
        OnOffToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * A switch with the state written inside it: green with ON when it is on, grey with OFF when it is
 * off. The thumb slides to the right for ON and to the left for OFF.
 */
@Composable
private fun OnOffToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val trackWidth = 64.dp
    val thumb = 24.dp
    val pad = 3.dp
    val thumbX by animateDpAsState(
        targetValue = if (checked) trackWidth - thumb - pad * 2 else 0.dp,
        label = "onOffToggle",
    )
    Box(
        modifier = Modifier
            .size(trackWidth, 30.dp)
            .clip(CircleShape)
            .background(if (checked) StatusReady else Color(0xFF475569))
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(pad),
    ) {
        Text(
            text = if (checked) "ON" else "OFF",
            fontSize = 11.scaledSp,
            fontWeight = FontWeight.Bold,
            color = if (checked) Color.White else Color(0xFFE2E8F0),
            modifier = Modifier
                .align(if (checked) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 8.dp),
        )
        Box(
            modifier = Modifier
                .offset(x = thumbX)
                .size(thumb)
                .clip(CircleShape)
                .background(Color.White)
                .align(Alignment.CenterStart),
        )
    }
}
