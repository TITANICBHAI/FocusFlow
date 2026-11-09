package com.tbtechs.focusflow.ui.customrules

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.enforcement.customrules.CaptureMap
import com.tbtechs.focusflow.enforcement.customrules.toExportText
import com.tbtechs.focusflow.enforcement.customrules.CustomScreenRule
import com.tbtechs.focusflow.enforcement.customrules.CustomScreenRules
import com.tbtechs.focusflow.enforcement.customrules.NodeSnapshot
import com.tbtechs.focusflow.enforcement.customrules.hasStableHandle
import com.tbtechs.focusflow.enforcement.customrules.idSuffix
import com.tbtechs.focusflow.enforcement.customrules.toMatcher
import com.tbtechs.focusflow.ui.home.FocusFlowInternalHeader
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.scaledSp

private const val MAX_INDENT_LEVELS = 12

/** "Add rule from screen", step 2: choose which captured elements identify the screen. */
@Composable
fun CustomRulePickNodeScreen(
    viewModel: CustomRulesViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpenGuide: () -> Unit = {},
) {
    val nodes by viewModel.snapshot.collectAsState()
    val capture by viewModel.capture.collectAsState()
    var search by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf(0) }
    var mode by rememberSaveable { mutableStateOf(0) } // 0 = screen map (beta), 1 = list
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val screenBox = remember(nodes) { CaptureMap.screenBox(nodes) }
    val context = LocalContext.current

    fun exportScan() {
        val result = capture ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "FocusFlow screen scan")
            putExtra(Intent.EXTRA_TEXT, result.toExportText())
        }
        runCatching { context.startActivity(Intent.createChooser(send, "Export scan")) }
    }
    var note by remember { mutableStateOf<String?>(null) }

    val filtered = remember(nodes, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) {
            nodes
        } else {
            nodes.filter { n ->
                listOfNotNull(n.viewId, n.text, n.desc, n.className)
                    .any { it.lowercase().contains(q) }
            }
        }
    }

    fun pick(node: NodeSnapshot) {
        if (viewModel.draft.value == null) viewModel.startDraft(null)
        val matcher = node.toMatcher()
        val pkg = capture?.packageName.orEmpty()
        var added = false
        viewModel.updateDraft { rule: CustomScreenRule ->
            val already = matcher in rule.matchers
            val full = rule.matchers.size >= CustomScreenRules.MAX_MATCHERS
            if (already || full) {
                note = if (already) "Already added." else "A rule can have at most ${CustomScreenRules.MAX_MATCHERS} matchers."
                rule.copy(packageName = pkg.ifBlank { rule.packageName })
            } else {
                added = true
                note = null
                rule.copy(
                    packageName = pkg.ifBlank { rule.packageName },
                    matchers = rule.matchers + matcher,
                )
            }
        }
        if (added) picked += 1
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            FocusFlowInternalHeader(
                title = "Choose element",
                subtitle = listOfNotNull("Beta", capture?.packageName?.takeIf { it.isNotBlank() }).joinToString(" · "),
                onBack = onBack,
                trailing = {
                    TextButton(onClick = ::exportScan, enabled = nodes.isNotEmpty()) { Text("Export") }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                note?.let {
                    Text(text = it, fontSize = 12.scaledSp, color = DarkTextSecondary)
                }
                RuleButton(
                    text = "Done ($picked selected)",
                    enabled = picked > 0,
                    onClick = onDone,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(modifier = Modifier.height(2.dp))
            RuleSegments(
                labels = listOf("Screen view", "Text list"),
                selectedIndex = mode,
                onSelect = { mode = it },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack) { Text("Capture again") }
                TextButton(onClick = onOpenGuide) { Text("Guide · AI assistant") }
            }
            if (nodes.isEmpty()) {
                RuleCard {
                    Text(
                        text = "Nothing captured yet. Go back and capture a screen first.",
                        fontSize = 13.scaledSp,
                        color = DarkTextSecondary,
                    )
                }
            } else if (mode == 0) {
                if (screenBox == null) {
                    RuleCard {
                        Text(
                            text = "This scan has no usable positions, so it can't be drawn. Use the list instead.",
                            fontSize = 13.scaledSp,
                            color = DarkTextSecondary,
                        )
                    }
                } else {
                    Text(
                        text = "Beta · rebuilt from the element scan (not a screenshot). Tap anything to select it.",
                        fontSize = 12.scaledSp,
                        color = DarkTextMuted,
                    )
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CaptureScreenMap(
                            nodes = nodes,
                            screen = screenBox,
                            selectedIndex = selected,
                            onSelect = { selected = it },
                        )
                    }
                    val node = selected?.let { nodes.getOrNull(it) }
                    if (node != null) {
                        NodeRow(node = node, onClick = { pick(node) })
                        RuleButton(text = "Add this element", onClick = { pick(node) })
                    } else {
                        Text(
                            text = "Nothing selected. Elements drawn faintly have no stable id or text.",
                            fontSize = 12.scaledSp,
                            color = DarkTextMuted,
                        )
                    }
                }
            } else {
                RuleTextField(
                    value = search,
                    label = "Search id, text, description or class",
                    onValueChange = { search = it },
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items = filtered) { node ->
                        NodeRow(node = node, onClick = { pick(node) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeRow(node: NodeSnapshot, onClick: () -> Unit) {
    val stable = node.hasStableHandle()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (minOf(node.depth, MAX_INDENT_LEVELS) * 8).dp)
            .alpha(if (stable) 1f else 0.5f)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = node.className?.substringAfterLast('.') ?: "View",
                fontSize = 13.scaledSp,
                fontWeight = FontWeight.SemiBold,
                color = DarkTextPrimary,
            )
            node.idSuffix()?.let {
                Text(text = "id: $it", fontSize = 12.scaledSp, color = DarkTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val label = node.text?.takeIf { it.isNotBlank() } ?: node.desc?.takeIf { it.isNotBlank() }
            if (label != null) {
                Text(text = label.take(60), fontSize = 12.scaledSp, color = DarkTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(text = node.bounds, fontSize = 11.scaledSp, color = DarkTextMuted)
            if (!stable) {
                Text(
                    text = "no stable id — may break after app updates",
                    fontSize = 11.scaledSp,
                    color = DarkTextMuted,
                )
            }
        }
    }
}
