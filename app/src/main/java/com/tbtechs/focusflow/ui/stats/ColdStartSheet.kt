package com.tbtechs.focusflow.ui.stats

import androidx.compose.ui.res.stringResource
import com.tbtechs.focusflow.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.local.entity.BehaviouralHypothesisEntity
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException

private val COLD_START_QUESTIONS = listOf(
    Triple(
        "reflex_app",
        R.string.stats_cold_q_reflex,
        R.string.stats_cold_h_reflex,
    ),
    Triple(
        "bad_day_meaning",
        R.string.stats_cold_q_bad_day,
        R.string.stats_cold_h_bad_day,
    ),
    Triple(
        "morning_phone",
        R.string.stats_cold_q_morning,
        R.string.stats_cold_h_morning,
    ),
)

// The English words are the stored answer values; only the label on screen is translated.
private val MORNING_OPTIONS = listOf("Almost always", "Sometimes", "Rarely", "No")

@Composable
private fun morningOptionLabel(option: String): String = stringResource(
    when (option) {
        "Almost always" -> R.string.stats_morning_almost_always
        "Sometimes" -> R.string.stats_morning_sometimes
        "Rarely" -> R.string.stats_morning_rarely
        else -> R.string.stats_morning_no
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColdStartSheet(
    onComplete: suspend (List<BehaviouralHypothesisEntity>) -> Boolean,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var answer by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf(false) }
    val answers = remember { mutableListOf<BehaviouralHypothesisEntity>() }

    fun recordAnswer(answerValue: String) {
        val questionId = COLD_START_QUESTIONS[step].first
        val trimmed = answerValue.trim()
        val existing = answers.firstOrNull { it.questionId == questionId }
        answers.removeAll { it.questionId == questionId }
        if (trimmed.isNotBlank()) {
            answers += BehaviouralHypothesisEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                questionId = questionId,
                answerText = trimmed,
                answerPackage = null,
                createdAt = existing?.createdAt ?: Instant.now().toString(),
            )
        }
    }

    fun submit(includeCurrentAnswer: Boolean = true, currentAnswer: String = answer) {
        if (isSubmitting) return
        if (includeCurrentAnswer) recordAnswer(currentAnswer)
        isSubmitting = true
        submitError = false
        scope.launch {
            val saved = try {
                onComplete(answers.toList())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            isSubmitting = false
            submitError = !saved
            if (!saved) sheetState.show()
        }
    }

    fun advance(answerValue: String = answer) {
        recordAnswer(answerValue)
        answer = ""
        if (step < COLD_START_QUESTIONS.lastIndex) {
            step++
        } else {
            submit(includeCurrentAnswer = false)
        }
    }

    ModalBottomSheet(
        onDismissRequest = { submit(includeCurrentAnswer = false) },
        sheetState = sheetState,
        containerColor = DarkBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding(),
        ) {
            LinearProgressIndicator(
                progress = { (step + 1).toFloat() / COLD_START_QUESTIONS.size },
                modifier = Modifier.fillMaxWidth(),
                color = BrandPrimary,
            )
            Spacer(Modifier.height(16.dp))

            val (_, question, hint) = COLD_START_QUESTIONS[step]
            Text(stringResource(question), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(hint), fontSize = 13.sp, color = DarkTextSecondary)
            Spacer(Modifier.height(16.dp))

            if (step == 2) {
                MORNING_OPTIONS.forEach { option ->
                    TextButton(
                        onClick = { answer = option },
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            morningOptionLabel(option),
                            fontSize = 14.sp,
                            color = if (answer == option) BrandPrimary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    placeholder = {
                        Text(
                            stringResource(if (step == 0) R.string.stats_cold_placeholder_app else R.string.stats_cold_placeholder_answer),
                            fontSize = 13.sp,
                            color = DarkTextSecondary,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPrimary,
                        unfocusedBorderColor = DarkBorder,
                    ),
                )
            }

            Spacer(Modifier.height(20.dp))
            Row {
                TextButton(
                    onClick = {
                        if (step < COLD_START_QUESTIONS.lastIndex) {
                            advance("")
                        } else {
                            submit(currentAnswer = "")
                        }
                    },
                    enabled = !isSubmitting,
                ) {
                    Text(
                        stringResource(if (step < COLD_START_QUESTIONS.lastIndex) R.string.stats_skip else R.string.stats_cold_submit_no_answer),
                        color = DarkTextSecondary,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (step < COLD_START_QUESTIONS.lastIndex) {
                    TextButton(
                        onClick = { advance() },
                        enabled = !isSubmitting,
                    ) {
                        Text(stringResource(R.string.stats_next), color = BrandPrimary)
                    }
                } else {
                    Button(
                        onClick = { submit() },
                        enabled = !isSubmitting,
                    ) {
                        Text(stringResource(if (isSubmitting) R.string.stats_submitting else R.string.stats_cold_submit_answers))
                    }
                }
            }
            if (submitError) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.stats_cold_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
            }
        }
    }
}