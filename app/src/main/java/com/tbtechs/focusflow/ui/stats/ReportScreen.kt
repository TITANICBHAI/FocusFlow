package com.tbtechs.focusflow.ui.stats

import androidx.compose.ui.res.stringResource
import com.tbtechs.focusflow.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.data.repository.ReportNotesRepository
import com.tbtechs.focusflow.ui.TaskViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ReportType { Day, Week }

private data class LoadedReport(
    val current: List<Task>,
    val previous: List<Task>,
    val notes: Map<String, String>,
    val savedNote: String,
)

/** Shared implementation for both the architecture "reports" route and the legacy "report" slug. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    reportType: ReportType = ReportType.Day,
    referenceDate: LocalDate = LocalDate.now().minusDays(1),
    taskViewModel: TaskViewModel = viewModel(factory = TaskViewModel.Factory),
    reportNotesRepository: ReportNotesRepository? = null,
    onBack: () -> Unit = {},
) {
    val range = remember(reportType, referenceDate) { reportRange(reportType, referenceDate) }
    val scope = rememberCoroutineScope()
    var reportTasks by remember(reportType, referenceDate) { mutableStateOf<List<Task>>(emptyList()) }
    var baseline by remember(reportType, referenceDate) { mutableStateOf<List<Task>>(emptyList()) }
    var weekNotes by remember(reportType, referenceDate) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember(reportType, referenceDate) { mutableStateOf(true) }
    var loadError by remember(reportType, referenceDate) { mutableStateOf<String?>(null) }
    val noteKey = remember(reportType, range.first) {
        "${reportType.name.lowercase()}_${range.first}"
    }
    var note by remember(noteKey, reportNotesRepository) { mutableStateOf("") }
    var savedNote by remember(noteKey, reportNotesRepository) { mutableStateOf("") }

    suspend fun reloadReport() {
        loading = true
        loadError = null
        runCatching {
            val current = taskViewModel.getTasksInDateRange(
                range.first.atStartOfDay(ZoneId.systemDefault()).toInstant().toString(),
                range.second.atStartOfDay(ZoneId.systemDefault()).toInstant().toString(),
            )
            val baselineStart = range.first.minusDays(if (reportType == ReportType.Week) 7 else 30)
            val baselineEnd = range.first.minusDays(1)
            LoadedReport(
                current = current,
                previous = taskViewModel.getTasksInDateRange(
                    baselineStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toString(),
                    baselineEnd.atStartOfDay(ZoneId.systemDefault()).toInstant().toString(),
                ),
                notes = reportNotesRepository
                    ?.getDailyNotes(range.first, range.second)
                    .orEmpty(),
                savedNote = reportNotesRepository?.getNote(
                    range.first,
                    if (reportType == ReportType.Week) {
                        ReportNotesRepository.TYPE_WEEK
                    } else {
                        ReportNotesRepository.TYPE_DAY
                    },
                ).orEmpty(),
            )
        }.onSuccess { loaded ->
            reportTasks = loaded.current
            baseline = loaded.previous
            weekNotes = loaded.notes
            note = loaded.savedNote
            savedNote = loaded.savedNote
        }.onFailure { failure ->
            // The detail is not shown (it is a system message in English); the screen shows its own text.
            loadError = failure.message ?: ""
        }
        loading = false
    }

    LaunchedEffect(reportType, range.first, range.second) {
        reloadReport()
    }
    val completed = reportTasks.filter { it.status == "completed" }
    val skipped = reportTasks.filter { it.status == "skipped" }
    val focusMinutes = completed.filter(Task::focusMode).sumOf(Task::durationMinutes)
    val completion = if (reportTasks.isEmpty()) 0 else completed.size * 100 / reportTasks.size
    val bestDay = if (reportType == ReportType.Week) {
        reportDays(range.first, range.second, reportTasks)
            .filter { (_, dayTasks) -> dayTasks.isNotEmpty() }
            .maxWithOrNull(
                compareBy<Pair<LocalDate, List<Task>>> { (_, dayTasks) ->
                    dayTasks.count { it.status == "completed" }.toDouble() / dayTasks.size
                }.thenBy { (date, _) -> date },
            )
            ?.first
    } else {
        null
    }
    fun saveNote() {
        val trimmed = note.trim()
        if (trimmed == savedNote) return
        scope.launch {
            reportNotesRepository?.saveNote(
                refDate = range.first,
                type = if (reportType == ReportType.Week) {
                    ReportNotesRepository.TYPE_WEEK
                } else {
                    ReportNotesRepository.TYPE_DAY
                },
                note = trimmed,
            )
            savedNote = trimmed
        }
    }
    val title = if (reportType == ReportType.Week) {
        "${statsDate(range.first, R.string.stats_date_pattern_month_day)} – ${statsDate(range.second, R.string.stats_date_pattern_month_day_year)}"
    } else {
        statsDate(referenceDate, R.string.stats_date_pattern_full)
    }
    val locale = statsLocale()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Column { Text(stringResource(if (reportType == ReportType.Week) R.string.stats_report_weekly else R.string.stats_report_daily).uppercase(), style = MaterialTheme.typography.labelLarge); Text(title) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, stringResource(R.string.go_back)) } },
            )
        },
    ) { padding ->
        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            loadError != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.stats_report_load_failed))
                        Button(onClick = { scope.launch { reloadReport() } }) {
                            Text(stringResource(R.string.stats_try_again))
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Card {
                            Text(stringResource(R.string.stats_the_takeaway), style = MaterialTheme.typography.labelLarge)
                            Text(reportHeadline(reportTasks, baseline), style = MaterialTheme.typography.headlineSmall)
                            Text(stringResource(if (reportTasks.isEmpty()) R.string.stats_report_no_pattern else R.string.stats_report_local_history))
                        }
                        Card {
                            Text(stringResource(R.string.stats_summary), style = MaterialTheme.typography.titleMedium)
                            Row {
                                ReportStat(reportTasks.size.toString(), stringResource(R.string.stats_report_tasks))
                                if (reportType == ReportType.Week) {
                                    ReportStat("$completion%", stringResource(R.string.stats_report_complete))
                                    ReportStat(statsDuration(focusMinutes), stringResource(R.string.stats_report_focus))
                                    bestDay?.let {
                                        ReportStat(it.format(DateTimeFormatter.ofPattern("EEE", locale)), stringResource(R.string.stats_report_best_day))
                                    }
                                } else {
                                    ReportStat(completed.size.toString(), stringResource(R.string.stats_report_done))
                                    ReportStat(skipped.size.toString(), stringResource(R.string.stats_report_skipped))
                                    ReportStat(statsDuration(focusMinutes), stringResource(R.string.stats_report_focus))
                                }
                            }
                        }
                        Card {
                            Text(stringResource(R.string.stats_your_note), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.stats_optional_saved_when_you_leave), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                label = { Text(stringResource(R.string.stats_what_do_you_want_to)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { state -> if (!state.isFocused) saveNote() },
                            )
                        }
                        Card {
                            Text(stringResource(R.string.stats_task_timeline), style = MaterialTheme.typography.titleMedium)
                            reportDays(range.first, range.second, reportTasks).forEach { (date, dayTasks) ->
                                if (reportType == ReportType.Week) {
                                    val completedMinutes = dayTasks
                                        .filter { it.status == "completed" && it.focusMode }
                                        .sumOf(Task::durationMinutes)
                                    Text(
                                        stringResource(
                                            R.string.stats_report_day_summary,
                                            statsDate(date, R.string.home_date_pattern_short),
                                            dayTasks.count { it.status == "completed" },
                                            dayTasks.size,
                                            statsDuration(completedMinutes),
                                        ),
                                    )
                                    weekNotes[date.toString()]?.let {
                                        Text(stringResource(R.string.stats_note, it), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (dayTasks.isEmpty()) Text(stringResource(R.string.stats_no_tasks_scheduled), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                else dayTasks.forEach { TaskTimelineRow(it) }
                            }
                        }
                        InsightsPanel(reportTasks, baseline)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportStat(value: String, label: String) = Column {
    Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    Text(label, style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun TaskTimelineRow(task: Task) = Row(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
    Text(if (task.status == "completed") "✓" else if (task.status == "skipped") "—" else "○")
    Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
        Text(task.title)
        Text("${task.reportTime(statsLocale())} · ${statsDuration(task.durationMinutes)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(taskStatusLabel(task.status))
}

private fun reportRange(type: ReportType, anchor: LocalDate): Pair<LocalDate, LocalDate> = if (type == ReportType.Day) anchor to anchor else {
    val daysFromSunday = anchor.dayOfWeek.value % 7
    val start = anchor.minusDays(daysFromSunday.toLong())
    start to start.plusDays(6)
}

private fun reportDays(start: LocalDate, end: LocalDate, tasks: List<Task>): List<Pair<LocalDate, List<Task>>> = generateSequence(start) { it.plusDays(1).takeIf { next -> next <= end } }
    .map { day -> day to tasks.filter { it.startsIn(day, day) } }.toList()

private fun Task.startsIn(start: LocalDate, end: LocalDate): Boolean = runCatching {
    val date = Instant.parse(startTime).atZone(ZoneId.systemDefault()).toLocalDate()
    date in start..end
}.getOrDefault(false)

private fun Task.reportTime(locale: java.util.Locale): String = runCatching {
    Instant.parse(startTime).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).withLocale(locale))
}.getOrDefault(startTime)

/** Task status codes are stored in English; only the label the person reads is translated. */
@Composable
private fun taskStatusLabel(status: String): String = when (status) {
    "pending" -> stringResource(R.string.task_status_pending)
    "active" -> stringResource(R.string.task_status_active)
    "completed" -> stringResource(R.string.task_status_completed)
    "skipped" -> stringResource(R.string.task_status_skipped)
    else -> status.replaceFirstChar { it.titlecase() }
}

@Composable
private fun reportHeadline(tasks: List<Task>, previous: List<Task>): String = stringResource(
    when {
        tasks.isEmpty() -> R.string.stats_report_headline_none
        tasks.count { it.status == "completed" } > previous.count { it.status == "completed" } -> R.string.stats_report_headline_more
        else -> R.string.stats_report_headline_default
    },
)
