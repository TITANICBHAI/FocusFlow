package com.tbtechs.focusflow.ui.stats

import androidx.compose.ui.res.stringResource
import com.tbtechs.focusflow.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.local.entity.ClarifyingQuestionEntity
import com.tbtechs.focusflow.data.local.entity.FindingEntity
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun FindingsSection(
    activeFindings: List<FindingEntity>,
    pendingQuestion: ClarifyingQuestionEntity?,
    dayCount: Int,
    ratingCount: Int,
    onMarkSeen: (String) -> Unit,
    onIntentional: (String, String) -> Unit,
    onAware: (String, String) -> Unit,
    onAnswerQuestion: suspend (id: String, response: String) -> Boolean,
    onBlockApp: (String?) -> Unit = {},
    onSetLimit: (String, Int) -> Unit = { _, _ -> },
) {
    Text(
        stringResource(R.string.stats_findings_header),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )

    if (dayCount < 14) {
        StatsCard {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.stats_building_your_baseline), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.stats_findings_after_14_days),
                    fontSize = 13.sp,
                    color = DarkTextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                BaselineRow(stringResource(R.string.stats_days_of_history), dayCount, 14)
                BaselineRow(stringResource(R.string.stats_days_rated_optional), ratingCount, 7)
            }
        }
        Spacer(Modifier.height(12.dp))
        return
    }

    pendingQuestion?.let { question ->
        ClarifyingQuestionCard(
            question = question,
            onAnswer = onAnswerQuestion,
        )
        Spacer(Modifier.height(4.dp))
    }

    activeFindings
        .filter { it.state == "detected" || it.state == "seen" }
        .forEach { finding ->
            FindingCardView(finding, onMarkSeen, onIntentional, onAware, onBlockApp, onSetLimit)
            Spacer(Modifier.height(4.dp))
        }

    val watching = activeFindings.filter { it.state == "aware" }
    if (watching.isNotEmpty()) {
        Text(
            "WATCHING",
            style = MaterialTheme.typography.labelLarge,
            color = DarkTextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        )
        watching.forEach { finding ->
            FindingCardView(finding, onMarkSeen, onIntentional, onAware, onBlockApp, onSetLimit)
            Spacer(Modifier.height(4.dp))
        }
    }

    if (activeFindings.isEmpty() && pendingQuestion == null) {
        StatsCard {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.stats_nothing_to_flag), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.stats_no_unusual_patterns),
                    fontSize = 13.sp,
                    color = DarkTextSecondary,
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun BaselineRow(label: String, current: Int, target: Int) {
    val complete = current >= target
    Text(
        if (complete) "✓ $label" else "$label: $current / $target",
        fontSize = 12.sp,
        color = if (complete) MaterialTheme.colorScheme.secondary else DarkTextSecondary,
    )
}

@Composable
private fun ClarifyingQuestionCard(
    question: ClarifyingQuestionEntity,
    onAnswer: suspend (id: String, response: String) -> Boolean,
) {
    val scope = rememberCoroutineScope()
    var selectedResponse by remember(question.id) { mutableStateOf<String?>(null) }
    var isSubmitting by remember(question.id) { mutableStateOf(false) }
    var submitError by remember(question.id) { mutableStateOf(false) }

    StatsCard {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "A question",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                renderQuestion(question),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            listOf(
                "yes_intentional" to stringResource(R.string.stats_answer_yes_chose),
                "not_really" to stringResource(R.string.stats_answer_not_really),
                "skipped" to stringResource(R.string.stats_answer_skip_question),
            ).forEach { (value, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedResponse == value,
                        onClick = { selectedResponse = value },
                        enabled = !isSubmitting,
                    )
                    Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    val response = selectedResponse ?: return@Button
                    scope.launch {
                        isSubmitting = true
                        submitError = false
                        val saved = try {
                            onAnswer(question.id, response)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            false
                        }
                        isSubmitting = false
                        submitError = !saved
                    }
                },
                enabled = selectedResponse != null && !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (isSubmitting) R.string.stats_submitting else R.string.stats_submit_response))
            }
            if (submitError) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.stats_response_save_failed),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun renderQuestion(question: ClarifyingQuestionEntity): String {
    val context = runCatching {
        Json.parseToJsonElement(question.contextJson) as? JsonObject
    }.getOrNull()
    val appName = context?.get("appName")?.jsonPrimitive?.content ?: stringResource(R.string.stats_that_app)
    val date = context?.get("date")?.jsonPrimitive?.content ?: stringResource(R.string.stats_that_day)
    val rating = context?.get("rating")?.jsonPrimitive?.content ?: ""
    return when (question.questionType) {
        "morning_high_rating" ->
            stringResource(R.string.stats_question_morning_high_rating, date, rating, appName)
        "productive_high_usage" ->
            stringResource(R.string.stats_question_productive_high_usage, appName)
        "skip_despite_completion" ->
            stringResource(R.string.stats_question_skip_despite_completion, date, rating)
        else -> question.contextJson
    }
}