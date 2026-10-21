package com.tbtechs.focusflow.ui.home.subtask

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.HorizontalScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.hapticfeedback.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import com.tbtechs.focusflow.data.images.SubtaskImageStore
import com.tbtechs.focusflow.data.model.GoalFormatter
import com.tbtechs.focusflow.data.model.Subtask
import com.tbtechs.focusflow.data.model.SubtaskImage
import com.tbtechs.focusflow.data.model.SubtaskLayoutMode
import com.tbtechs.focusflow.data.model.SubtaskLayoutPolicy
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.ui.common.FocusFlowInternalCard
import com.tbtechs.focusflow.ui.common.SubtaskImageView
import com.tbtechs.focusflow.ui.home.RefBackground
import com.tbtechs.focusflow.ui.home.RefBorder
import com.tbtechs.focusflow.ui.home.RefCard
import com.tbtechs.focusflow.ui.home.RefMuted
import com.tbtechs.focusflow.ui.home.RefSecondary
import com.tbtechs.focusflow.ui.home.RefText
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
import com.tbtechs.focusflow.ui.theme.scaledSp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun SubtaskDetailScreen(
    task: Task,
    subtask: Subtask,
    index: Int,
    imageStore: SubtaskImageStore?,
    accent: Color,
    animationsEnabled: Boolean,
    onBack: () -> Unit,
    onSelectSubtask: (String) -> Unit,
    onSetDone: (Boolean) -> Unit,
    onSetSetsDone: (Int) -> Unit,
    onCompleteTask: () -> Unit,
) {
    val mode = SubtaskLayoutPolicy.resolve(subtask)
    val imagePages = remember(subtask) { buildStepImagePages(subtask) }
    val pagerState = rememberPagerState(pageCount = { imagePages.size })
    val scope = rememberCoroutineScope()
    val dimensions = LocalFocusFlowDimensions.current
    var activeStepIndex by remember(subtask.id) { mutableIntStateOf(0) }
    var cuesExpanded by remember(subtask.id) {
        mutableStateOf(subtask.steps.size <= 3 && (subtask.explanation?.length ?: 0) <= 900)
    }
    var menuExpanded by remember(subtask.id) { mutableStateOf(false) }
    var confirmReset by remember(subtask.id) { mutableStateOf(false) }
    var advanceAfterDone by remember(subtask.id) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val currentIndex = task.subtasks.indexOfFirst { it.id == subtask.id }
        .takeIf { it >= 0 } ?: index
    val allDone = task.subtasks.isNotEmpty() && task.subtasks.all(Subtask::done)
    val isSetGoal = subtask.goal?.kind == "sets" && subtask.goal.sets != null
    val isWide = androidx.compose.ui.platform.LocalConfiguration.current.let {
        it.screenWidthDp >= WIDE_LAYOUT_WIDTH_DP
    }

    LaunchedEffect(pagerState.currentPage, imagePages) {
        imagePages.getOrNull(pagerState.currentPage)?.let { activeStepIndex = it.stepIndex }
    }
    LaunchedEffect(advanceAfterDone, subtask.done, task.subtasks) {
        if (advanceAfterDone && subtask.done) {
            delay(AUTO_ADVANCE_DELAY_MS)
            val nextUndone = task.subtasks
                .drop(currentIndex + 1)
                .firstOrNull { !it.done }
            advanceAfterDone = false
            if (nextUndone != null) onSelectSubtask(nextUndone.id)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RefBackground),
    ) {
        SubtaskDetailTopBar(
            itemNumber = currentIndex + 1,
            total = task.subtasks.size,
            onBack = onBack,
            onReset = { confirmReset = true },
        )

        if (isWide) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = dimensions.screenPadding, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.43f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    SubtaskHero(
                        subtask = subtask,
                        index = currentIndex,
                        mode = mode,
                        pages = imagePages,
                        pagerState = pagerState,
                        imageStore = imageStore,
                        accent = accent,
                        animationsEnabled = animationsEnabled,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(0.57f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(end = dimensions.screenPadding),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SubtaskDetailContent(
                        subtask = subtask,
                        index = currentIndex,
                        mode = mode,
                        imageStore = imageStore,
                        animationsEnabled = animationsEnabled,
                        activeStepIndex = activeStepIndex,
                        onStepSelected = { stepIndex ->
                            activeStepIndex = stepIndex
                            val page = imagePages.indexOfLast { it.stepIndex <= stepIndex }
                            if (page >= 0) scope.launch { pagerState.animateScrollToPage(page) }
                        },
                        cuesExpanded = cuesExpanded,
                        onCuesExpanded = { cuesExpanded = it },
                        accent = accent,
                        onSetSetsDone = onSetSetsDone,
                    )
                    if (allDone && task.status !in setOf("completed", "skipped")) {
                        AllSubtasksDoneBanner(onComplete = onCompleteTask)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = dimensions.screenPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SubtaskHero(
                    subtask = subtask,
                    index = currentIndex,
                    mode = mode,
                    pages = imagePages,
                    pagerState = pagerState,
                    imageStore = imageStore,
                    accent = accent,
                    animationsEnabled = animationsEnabled,
                )
                SubtaskDetailContent(
                    subtask = subtask,
                    index = currentIndex,
                    mode = mode,
                    imageStore = imageStore,
                    animationsEnabled = animationsEnabled,
                    activeStepIndex = activeStepIndex,
                    onStepSelected = { stepIndex ->
                        activeStepIndex = stepIndex
                        val page = imagePages.indexOfLast { it.stepIndex <= stepIndex }
                        if (page >= 0) scope.launch { pagerState.animateScrollToPage(page) }
                    },
                    cuesExpanded = cuesExpanded,
                    onCuesExpanded = { cuesExpanded = it },
                    accent = accent,
                    onSetSetsDone = onSetSetsDone,
                )
                if (allDone && task.status !in setOf("completed", "skipped")) {
                    AllSubtasksDoneBanner(onComplete = onCompleteTask)
                }
            }
        }

        SubtaskBottomBar(
            subtask = subtask,
            index = currentIndex,
            total = task.subtasks.size,
            animationsEnabled = animationsEnabled,
            onPrevious = {
                advanceAfterDone = false
                task.subtasks.getOrNull(currentIndex - 1)?.let { onSelectSubtask(it.id) }
            },
            onNext = {
                advanceAfterDone = false
                task.subtasks.getOrNull(currentIndex + 1)?.let { onSelectSubtask(it.id) }
            },
            onToggleDone = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val nextDone = !subtask.done
                onSetDone(nextDone)
                advanceAfterDone = nextDone
            },
        )
    }

    if (menuExpanded) {
        DropdownMenu(
            expanded = true,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Reset this item", fontSize = 14.scaledSp) },
                onClick = {
                    menuExpanded = false
                    confirmReset = true
                },
            )
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset this item", fontWeight = FontWeight.Bold) },
            text = { Text("Clear this subtask’s completion and set progress?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        if (isSetGoal) onSetSetsDone(0) else onSetDone(false)
                    },
                ) { Text("Reset", color = BrandPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text("Cancel", color = RefSecondary)
                }
            },
        )
    }
}

@Composable
private fun SubtaskDetailTopBar(
    itemNumber: Int,
    total: Int,
    onBack: () -> Unit,
    onReset: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp).semantics {
                contentDescription = "Back to task details"
            },
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = RefText)
        }
        Text(
            text = "Item $itemNumber of $total",
            modifier = Modifier.weight(1f),
            color = RefSecondary,
            fontSize = 14.scaledSp,
            fontWeight = FontWeight.Medium,
        )
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Subtask options", tint = RefSecondary)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Reset this item", fontSize = 14.scaledSp) },
                    onClick = {
                        menuExpanded = false
                        onReset()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubtaskHero(
    subtask: Subtask,
    index: Int,
    mode: SubtaskLayoutMode,
    pages: List<StepImagePage>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    imageStore: SubtaskImageStore?,
    accent: Color,
    animationsEnabled: Boolean,
) {
    val density = LocalDensity.current
    val isPaged = mode == SubtaskLayoutMode.STEPS && pages.isNotEmpty()
    val staticImage = if (!isPaged) {
        subtask.image ?: subtask.steps.firstNotNullOfOrNull { it.image }
    } else null
    if (!isPaged && staticImage == null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(accent.copy(alpha = 0.12f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (index + 1).toString(),
                    color = accent,
                    fontSize = 22.scaledSp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = if (mode == SubtaskLayoutMode.STEPS) "Step ${index + 1}" else "Item ${index + 1}",
                color = RefSecondary,
                fontSize = 13.scaledSp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        return
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= WIDE_LAYOUT_WIDTH_DP) 1.18f else 1.6f)
            .clip(RoundedCornerShape(20.dp)),
    ) {
        val widthPx = with(density) { maxWidth.roundToPx() }
        when {
            isPaged -> {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { pages[it].stepIndex },
                ) { page ->
                    val image = pages[page].image
                    SubtaskImageView(
                        assetId = image.asset,
                        imageStore = imageStore,
                        contentDescription = image.alt?.takeIf(String::isNotBlank)
                            ?: subtask.steps[pages[page].stepIndex].title
                            ?: subtask.title,
                        modifier = Modifier.fillMaxSize(),
                        targetWidthPx = widthPx,
                        animate = animationsEnabled,
                    )
                }
                if (pages.size > 1) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${pages.size}",
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.58f))
                            .padding(horizontal = 11.dp, vertical = 5.dp),
                        color = Color.White,
                        fontSize = 11.scaledSp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            staticImage != null -> {
                SubtaskImageView(
                    assetId = staticImage.asset,
                    imageStore = imageStore,
                    contentDescription = staticImage.alt?.takeIf(String::isNotBlank) ?: subtask.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    targetWidthPx = widthPx,
                    animate = animationsEnabled,
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(accent.copy(alpha = 0.12f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = (index + 1).toString(),
                        color = accent,
                        fontSize = 42.scaledSp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = subtask.title,
                        color = RefText,
                        fontSize = 16.scaledSp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtaskDetailContent(
    subtask: Subtask,
    index: Int,
    mode: SubtaskLayoutMode,
    imageStore: SubtaskImageStore?,
    animationsEnabled: Boolean,
    activeStepIndex: Int,
    onStepSelected: (Int) -> Unit,
    cuesExpanded: Boolean,
    onCuesExpanded: (Boolean) -> Unit,
    accent: Color,
    onSetSetsDone: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.semantics(mergeDescendants = false) { },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = subtask.title,
                color = RefText,
                fontSize = 24.scaledSp,
                lineHeight = 30.scaledSp,
                fontWeight = FontWeight.Bold,
            )
            subtask.subtitle?.takeIf(String::isNotBlank)?.let {
                Text(it, color = RefSecondary, fontSize = 14.scaledSp, lineHeight = 20.scaledSp)
            }
        }

        subtask.goal?.let { goal ->
            val formatted = GoalFormatter.format(goal)
            if (formatted != null) {
                FocusFlowInternalCard(radius = 16.dp, contentPadding = 14.dp) {
                    Text(
                        text = "Goal",
                        color = RefSecondary,
                        fontSize = 12.scaledSp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = formatted.primary,
                        color = RefText,
                        fontSize = 25.scaledSp,
                        lineHeight = 31.scaledSp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (formatted.details.isNotEmpty() || formatted.rest != null) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            (formatted.details + listOfNotNull(formatted.rest)).forEach { detail ->
                                GoalDetailPill(detail)
                            }
                        }
                    }
                    if (goal.kind == "sets" && goal.sets != null) {
                        SetProgressTracker(
                            total = goal.sets,
                            completed = subtask.setsDone.coerceIn(0, goal.sets),
                            accent = accent,
                            onSetCount = onSetSetsDone,
                        )
                    }
                }
            }
        }

        subtask.explanation?.takeIf(String::isNotBlank)?.let { explanation ->
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = "About this item",
                    color = RefText,
                    fontSize = 16.scaledSp,
                    fontWeight = FontWeight.SemiBold,
                )
                ExplanationText(explanation)
            }
        }

        if (mode == SubtaskLayoutMode.STEPS && subtask.steps.isNotEmpty()) {
            Column {
                Text("Steps", color = RefText, fontSize = 16.scaledSp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                subtask.steps.forEachIndexed { stepIndex, step ->
                    val selected = activeStepIndex == stepIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) accent.copy(alpha = 0.10f) else Color.Transparent)
                            .border(
                                width = if (selected) 1.dp else 0.dp,
                                color = if (selected) accent else Color.Transparent,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable(role = Role.Button) { onStepSelected(stepIndex) }
                            .padding(horizontal = 10.dp)
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier
                                .width(28.dp)
                                .fillMaxHeight()
                                .padding(top = 9.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) accent else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = (stepIndex + 1).toString(),
                                    color = if (selected) contentColorForAccent(accent) else RefSecondary,
                                    fontSize = 12.scaledSp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (stepIndex < subtask.steps.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .weight(1f)
                                        .background(accent.copy(alpha = 0.38f)),
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            step.title?.takeIf(String::isNotBlank)?.let {
                                Text(
                                    it,
                                    color = RefText,
                                    fontSize = 14.scaledSp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(step.text, color = RefSecondary, fontSize = 13.scaledSp, lineHeight = 19.scaledSp)
                        }
                        step.image?.let { image ->
                            Spacer(Modifier.width(8.dp))
                            val density = LocalDensity.current
                            Box(
                                modifier = Modifier
                                    .width(96.dp)
                                    .height(72.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .clickable(role = Role.Button) { onStepSelected(stepIndex) },
                            ) {
                                SubtaskImageView(
                                    assetId = image.asset,
                                    imageStore = imageStore,
                                    contentDescription = image.alt?.takeIf(String::isNotBlank)
                                        ?: step.title
                                        ?: "Step ${stepIndex + 1} image",
                                    targetWidthPx = with(density) { 96.dp.roundToPx() },
                                    modifier = Modifier.fillMaxSize(),
                                    animate = animationsEnabled,
                                )
                            }
                        }
                    }
                    if (stepIndex < subtask.steps.lastIndex) {
                        Box(
                            modifier = Modifier
                                .padding(start = 23.dp)
                                .width(1.dp)
                                .height(10.dp)
                                .background(accent.copy(alpha = 0.38f)),
                        )
                    }
                }
            }
        }

        if (subtask.tips.isNotEmpty() || subtask.avoid.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                TextButton(
                    onClick = { onCuesExpanded(!cuesExpanded) },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Text(
                        text = if (cuesExpanded) "Hide tips and watch-outs" else "Tips and watch-outs",
                        modifier = Modifier.weight(1f),
                        color = RefText,
                        fontSize = 15.scaledSp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        imageVector = if (cuesExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = RefSecondary,
                    )
                }
                if (cuesExpanded) {
                    FocusFlowInternalCard(radius = 14.dp, contentPadding = 12.dp) {
                        if (subtask.tips.isNotEmpty()) {
                            Text("Tips", color = RefText, fontSize = 14.scaledSp, fontWeight = FontWeight.SemiBold)
                            subtask.tips.forEach { BulletText(it) }
                        }
                        if (subtask.avoid.isNotEmpty()) {
                            Text("Watch out for", color = RefText, fontSize = 14.scaledSp, fontWeight = FontWeight.SemiBold)
                            subtask.avoid.forEach { BulletText(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalDetailPill(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        color = RefSecondary,
        fontSize = 12.scaledSp,
    )
}

@Composable
private fun SetProgressTracker(
    total: Int,
    completed: Int,
    accent: Color,
    onSetCount: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Sets · tap the next set to mark it, or the last completed set to undo",
            color = RefSecondary,
            fontSize = 12.scaledSp,
            lineHeight = 17.scaledSp,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(total) { setIndex ->
                val isDone = setIndex < completed
                val canToggle = setIndex == completed || setIndex == completed - 1
                val label = when {
                    isDone -> "Set ${setIndex + 1}, completed"
                    else -> "Set ${setIndex + 1}, not completed"
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isDone) accent else MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, if (isDone) accent else RefBorder, CircleShape)
                        .clickable(
                            enabled = canToggle,
                            role = Role.Checkbox,
                            onClick = {
                                onSetCount(if (isDone) setIndex else setIndex + 1)
                            },
                        )
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isDone) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = contentColorForAccent(accent),
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Text(
                            text = (setIndex + 1).toString(),
                            color = RefSecondary,
                            fontSize = 13.scaledSp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        Text(
            text = "$completed of $total sets",
            color = RefMuted,
            fontSize = 12.scaledSp,
        )
    }
}

@Composable
private fun ExplanationText(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        text.lines()
            .splitByBlankLines()
            .forEach { block ->
                val contentLines = block.filter(String::isNotBlank)
                if (contentLines.isNotEmpty() && contentLines.all { it.trimStart().startsWith("- ") }) {
                    contentLines.forEach { line -> BulletText(line.trimStart().removePrefix("- ").trim()) }
                } else {
                    block.forEach { line ->
                        when {
                            line.isBlank() -> Unit
                            line.trimStart().startsWith("- ") ->
                                BulletText(line.trimStart().removePrefix("- ").trim())
                            else -> Text(
                                text = line,
                                color = RefSecondary,
                                fontSize = 14.scaledSp,
                                lineHeight = 21.scaledSp,
                            )
                        }
                    }
                }
            }
    }
}

@Composable
private fun BulletText(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("•", color = BrandPrimary, fontSize = 16.scaledSp, fontWeight = FontWeight.Bold)
        Text(text, color = RefSecondary, fontSize = 14.scaledSp, lineHeight = 21.scaledSp)
    }
}

@Composable
private fun SubtaskBottomBar(
    subtask: Subtask,
    index: Int,
    total: Int,
    animationsEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleDone: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, RefBorder.copy(alpha = 0.5f))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = index > 0,
            modifier = Modifier.size(48.dp).semantics {
                contentDescription = "Previous subtask"
            },
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onToggleDone,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (subtask.done) MaterialTheme.colorScheme.surfaceVariant else BrandPrimary,
                contentColor = if (subtask.done) RefText else contentColorForAccent(BrandPrimary),
            ),
        ) {
            Text(
                text = if (subtask.done) "Undo done" else "Mark done",
                fontSize = 14.scaledSp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onNext,
            enabled = index < total - 1,
            modifier = Modifier.size(48.dp).semantics {
                contentDescription = "Next subtask"
            },
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
        }
    }
}

private data class StepImagePage(
    val stepIndex: Int,
    val image: SubtaskImage,
)

private fun buildStepImagePages(subtask: Subtask): List<StepImagePage> {
    var previousImage = subtask.image
    return subtask.steps.mapIndexedNotNull { index, step ->
        previousImage = step.image ?: previousImage
        previousImage?.let { StepImagePage(index, it) }
    }
}

private fun List<String>.splitByBlankLines(): List<List<String>> {
    val blocks = mutableListOf<MutableList<String>>()
    var current = mutableListOf<String>()
    forEach { line ->
        if (line.isBlank()) {
            if (current.isNotEmpty()) blocks += current
            current = mutableListOf()
        } else {
            current += line
        }
    }
    if (current.isNotEmpty()) blocks += current
    return blocks
}

@Composable
private fun contentColorForAccent(accent: Color): Color =
    if (accent.luminance() > 0.52f) Color.Black else Color.White

private const val WIDE_LAYOUT_WIDTH_DP = 600
private const val AUTO_ADVANCE_DELAY_MS = 350L
