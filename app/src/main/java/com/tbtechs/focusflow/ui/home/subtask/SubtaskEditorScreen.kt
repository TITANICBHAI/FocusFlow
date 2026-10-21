package com.tbtechs.focusflow.ui.home.subtask

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.model.Subtask
import com.tbtechs.focusflow.data.model.SubtaskGoal
import com.tbtechs.focusflow.data.model.SubtaskLimits
import com.tbtechs.focusflow.data.model.SubtaskStep
import com.tbtechs.focusflow.ui.home.RefCard
import com.tbtechs.focusflow.ui.home.RefSecondary
import com.tbtechs.focusflow.ui.home.RefText
import com.tbtechs.focusflow.ui.theme.scaledSp

private val goalKinds = listOf(
    "none" to "No goal",
    "sets" to "Sets",
    "reps" to "Reps",
    "duration" to "Duration",
    "distance" to "Distance",
    "text" to "Text goal",
)

@Composable
internal fun SubtaskEditorScreen(
    initial: Subtask,
    onCancel: () -> Unit,
    onSave: (Subtask) -> Unit,
) {
    var title by remember(initial) { mutableStateOf(initial.title) }
    var subtitle by remember(initial) { mutableStateOf(initial.subtitle.orEmpty()) }
    var explanation by remember(initial) { mutableStateOf(initial.explanation.orEmpty()) }
    var goalKind by remember(initial) {
        mutableStateOf(initial.goal?.kind?.takeIf { kind -> goalKinds.any { it.first == kind } } ?: "none")
    }
    var sets by remember(initial) { mutableStateOf(initial.goal?.sets?.toString().orEmpty()) }
    var reps by remember(initial) { mutableStateOf(initial.goal?.reps?.toString().orEmpty()) }
    var repsMax by remember(initial) { mutableStateOf(initial.goal?.repsMax?.toString().orEmpty()) }
    var seconds by remember(initial) { mutableStateOf(initial.goal?.seconds?.toString().orEmpty()) }
    var restSeconds by remember(initial) { mutableStateOf(initial.goal?.restSeconds?.toString().orEmpty()) }
    var meters by remember(initial) { mutableStateOf(initial.goal?.meters?.toString().orEmpty()) }
    var weightKg by remember(initial) { mutableStateOf(initial.goal?.weightKg?.toString().orEmpty()) }
    var goalLabel by remember(initial) { mutableStateOf(initial.goal?.label.orEmpty()) }
    var layout by remember(initial) {
        mutableStateOf(initial.layout ?: if (initial.steps.isNotEmpty()) "steps" else "overview")
    }
    var steps by remember(initial) { mutableStateOf(initial.steps) }
    var goalMenuExpanded by remember(initial) { mutableStateOf(false) }
    var error by remember(initial) { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AuthoringInput(
                label = "Title",
                value = title,
                onValueChange = { title = it; error = null },
                maxChars = SubtaskLimits.MAX_TITLE_CHARS,
            )
            AuthoringInput(
                label = "Subtitle (optional)",
                value = subtitle,
                onValueChange = { subtitle = it },
                maxChars = SubtaskLimits.MAX_SUBTITLE_CHARS,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Goal", color = RefText, fontWeight = FontWeight.SemiBold)
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { goalMenuExpanded = true }) {
                        Text(goalKinds.first { it.first == goalKind }.second)
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = goalMenuExpanded,
                        onDismissRequest = { goalMenuExpanded = false },
                    ) {
                        goalKinds.forEach { (kind, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    goalKind = kind
                                    goalMenuExpanded = false
                                    error = null
                                },
                            )
                        }
                    }
                }
                when (goalKind) {
                    "sets" -> {
                        AuthoringInput("Sets (1–99)", sets, { sets = it }, numeric = true)
                        AuthoringInput("Reps (optional)", reps, { reps = it }, numeric = true)
                        AuthoringInput("Maximum reps (optional)", repsMax, { repsMax = it }, numeric = true)
                        AuthoringInput("Seconds per set (optional)", seconds, { seconds = it }, numeric = true)
                        AuthoringInput("Rest seconds (optional)", restSeconds, { restSeconds = it }, numeric = true)
                        AuthoringInput("Weight in kg (optional)", weightKg, { weightKg = it }, numeric = true)
                        AuthoringInput("Note (optional)", goalLabel, { goalLabel = it }, maxChars = SubtaskLimits.MAX_GOAL_LABEL_CHARS)
                    }
                    "reps" -> {
                        AuthoringInput("Reps", reps, { reps = it }, numeric = true)
                        AuthoringInput("Maximum reps (optional)", repsMax, { repsMax = it }, numeric = true)
                        AuthoringInput("Weight in kg (optional)", weightKg, { weightKg = it }, numeric = true)
                        AuthoringInput("Note (optional)", goalLabel, { goalLabel = it }, maxChars = SubtaskLimits.MAX_GOAL_LABEL_CHARS)
                    }
                    "duration" -> {
                        AuthoringInput("Seconds", seconds, { seconds = it }, numeric = true)
                        AuthoringInput("Note (optional)", goalLabel, { goalLabel = it }, maxChars = SubtaskLimits.MAX_GOAL_LABEL_CHARS)
                    }
                    "distance" -> {
                        AuthoringInput("Meters", meters, { meters = it }, numeric = true)
                        AuthoringInput("Note (optional)", goalLabel, { goalLabel = it }, maxChars = SubtaskLimits.MAX_GOAL_LABEL_CHARS)
                    }
                    "text" -> AuthoringInput(
                        "Goal label",
                        goalLabel,
                        { goalLabel = it },
                        maxChars = SubtaskLimits.MAX_GOAL_LABEL_CHARS,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Display layout", color = RefText, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("steps" to "Steps", "overview" to "Overview").forEach { (value, label) ->
                        if (layout == value) {
                            Button(onClick = { layout = value }, modifier = Modifier.weight(1f)) {
                                Text(label)
                            }
                        } else {
                            OutlinedButton(onClick = { layout = value }, modifier = Modifier.weight(1f)) {
                                Text(label)
                            }
                        }
                    }
                }
                Text(
                    "Steps remain available in either layout.",
                    color = RefSecondary,
                    fontSize = 12.scaledSp,
                )
            }

            AuthoringInput(
                label = "Explanation (optional)",
                value = explanation,
                onValueChange = { explanation = it },
                multiline = true,
                maxChars = SubtaskLimits.MAX_EXPLANATION_CHARS,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Steps", color = RefText, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${steps.size} of ${SubtaskLimits.MAX_STEPS}",
                        color = RefSecondary,
                        fontSize = 12.scaledSp,
                    )
                }
                TextButton(
                    onClick = {
                        steps = steps + SubtaskStep(text = "")
                        error = null
                    },
                    enabled = steps.size < SubtaskLimits.MAX_STEPS,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("Add step")
                }
            }
            steps.forEachIndexed { index, step ->
                SubtaskStepEditor(
                    step = step,
                    index = index,
                    count = steps.size,
                    onChange = { changed ->
                        steps = steps.mapIndexed { itemIndex, item ->
                            if (itemIndex == index) changed else item
                        }
                        error = null
                    },
                    onMove = { delta ->
                        val destination = index + delta
                        if (destination in steps.indices) {
                            steps = steps.toMutableList().apply {
                                add(destination, removeAt(index))
                            }
                        }
                    },
                    onDelete = {
                        steps = steps.filterIndexed { itemIndex, _ -> itemIndex != index }
                    },
                )
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.scaledSp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    if (title.isBlank()) {
                        error = "Enter a title for this subtask."
                        return@Button
                    }
                    if (steps.any { it.text.isBlank() }) {
                        error = "Every step needs text, or remove the blank step."
                        return@Button
                    }
                    val goalResult = parseGoalForm(
                        kind = goalKind,
                        setsText = sets,
                        repsText = reps,
                        repsMaxText = repsMax,
                        secondsText = seconds,
                        restSecondsText = restSeconds,
                        metersText = meters,
                        weightKgText = weightKg,
                        labelText = goalLabel,
                    )
                    if (goalResult.error != null) {
                        error = goalResult.error
                        return@Button
                    }
                    val goal = goalResult.goal
                    onSave(
                        initial.copy(
                            title = title.trim(),
                            subtitle = subtitle.trim().takeIf(String::isNotEmpty),
                            goal = goal,
                            setsDone = if (goal?.kind == "sets" && goal.sets != null) {
                                initial.setsDone.coerceIn(0, goal.sets)
                            } else {
                                0
                            },
                            layout = layout,
                            explanation = explanation.trim().takeIf(String::isNotEmpty),
                            steps = steps.map { step ->
                                step.copy(
                                    title = step.title?.trim()?.takeIf(String::isNotEmpty),
                                    text = step.text.trim(),
                                )
                            },
                        ),
                    )
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Save item")
            }
        }
    }
}

@Composable
private fun SubtaskStepEditor(
    step: SubtaskStep,
    index: Int,
    count: Int,
    onChange: (SubtaskStep) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RefCard, RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Step ${index + 1}",
                modifier = Modifier.weight(1f),
                color = RefText,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(
                onClick = { onMove(-1) },
                enabled = index > 0,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move step up")
            }
            IconButton(
                onClick = { onMove(1) },
                enabled = index < count - 1,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move step down")
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Remove step",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        AuthoringInput(
            label = "Step title (optional)",
            value = step.title.orEmpty(),
            onValueChange = { onChange(step.copy(title = it)) },
            maxChars = SubtaskLimits.MAX_STEP_TITLE_CHARS,
        )
        AuthoringInput(
            label = "Step instructions",
            value = step.text,
            onValueChange = { onChange(step.copy(text = it)) },
            multiline = true,
            maxChars = SubtaskLimits.MAX_STEP_TEXT_CHARS,
        )
        if (step.image != null) {
            Text(
                "This step’s attached image will be kept.",
                color = RefSecondary,
                fontSize = 12.scaledSp,
            )
        }
    }
}

@Composable
private fun AuthoringInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    multiline: Boolean = false,
    numeric: Boolean = false,
    maxChars: Int? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { changed ->
            onValueChange(if (maxChars == null) changed else changed.take(maxChars))
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
        ),
        supportingText = if (maxChars == null) null else {
            { Text("${value.length}/$maxChars") }
        },
    )
}

private data class GoalFormResult(
    val goal: SubtaskGoal? = null,
    val error: String? = null,
)

private fun parseGoalForm(
    kind: String,
    setsText: String,
    repsText: String,
    repsMaxText: String,
    secondsText: String,
    restSecondsText: String,
    metersText: String,
    weightKgText: String,
    labelText: String,
): GoalFormResult {
    if (kind == "none") return GoalFormResult()
    var error: String? = null

    fun parseInt(
        raw: String,
        label: String,
        range: IntRange,
        required: Boolean = false,
    ): Int? {
        if (raw.isBlank()) {
            if (required) error = "$label is required."
            return null
        }
        val value = raw.toIntOrNull()
        if (value == null) {
            error = "$label must be a whole number."
            return null
        }
        if (value !in range) {
            error = "$label must be between ${range.first} and ${range.last}."
            return null
        }
        return value
    }

    val goalSets = if (kind == "sets") parseInt(setsText, "Sets", 1..99, required = true) else null
    val goalReps = if (kind in setOf("sets", "reps")) {
        parseInt(repsText, "Reps", 1..9_999, required = kind == "reps")
    } else null
    val goalRepsMax = if (kind in setOf("sets", "reps")) {
        parseInt(repsMaxText, "Maximum reps", 1..9_999)
    } else null
    val goalSeconds = if (kind in setOf("sets", "duration")) {
        parseInt(secondsText, "Seconds", 1..86_400, required = kind == "duration")
    } else null
    val goalRest = if (kind == "sets") {
        parseInt(restSecondsText, "Rest seconds", 0..3_600)
    } else null
    val goalMeters = if (kind == "distance") {
        parseInt(metersText, "Meters", 1..1_000_000, required = true)
    } else null
    val goalWeight = if (kind in setOf("sets", "reps") && weightKgText.isNotBlank()) {
        weightKgText.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..1_000.0 }
            ?: run {
                error = "Weight must be a number from 0 to 1000 kg."
                null
            }
    } else null
    val cleanLabel = labelText.trim().takeIf(String::isNotEmpty)

    if (error != null) return GoalFormResult(error = error)
    if (kind == "sets" && goalReps == null && goalSeconds == null) {
        return GoalFormResult(error = "Enter reps or seconds for the sets goal.")
    }
    if (goalRepsMax != null && (goalReps == null || goalRepsMax < goalReps)) {
        return GoalFormResult(error = "Maximum reps must be at least the reps value.")
    }
    if (kind == "text" && cleanLabel == null) {
        return GoalFormResult(error = "Enter a label for the text goal.")
    }
    if (cleanLabel != null && cleanLabel.length > SubtaskLimits.MAX_GOAL_LABEL_CHARS) {
        return GoalFormResult(error = "The goal note is too long.")
    }

    return GoalFormResult(
        goal = SubtaskGoal(
            kind = kind,
            sets = goalSets,
            reps = goalReps,
            repsMax = goalRepsMax,
            seconds = goalSeconds,
            restSeconds = goalRest,
            meters = goalMeters,
            weightKg = goalWeight,
            label = cleanLabel,
        ),
    )
}
