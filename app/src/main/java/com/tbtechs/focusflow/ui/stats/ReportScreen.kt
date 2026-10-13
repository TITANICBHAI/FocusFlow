package com.tbtechs.focusflow.ui.stats

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
            loadError = failure.message ?: "Could not load this report."
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
    val title = if (reportType == ReportType.Week) "${range.first.format(DateTimeFormatter.ofPattern("MMM d"))} – ${range.second.format(DateTimeFormatter.ofPattern("MMM d, uuuu"))}" else referenceDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, uuuu"))

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Column { Text(if (reportType == ReportType.Week) "WEEKLY REPORT" else "DAILY REPORT", style = MaterialTheme.typography.labelLarge); Text(title) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Go back") } },
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
                        Text(loadError ?: "Could not load this report.")
                        Button(onClick = { scope.launch { reloadReport() } }) {
                            Text("Try again")
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
                            Text("THE TAKEAWAY", style = MaterialTheme.typography.labelLarge)
                            Text(reportHeadline(reportTasks, baseline), style = MaterialTheme.typography.headlineSmall)
                            Text(if (reportTasks.isEmpty()) "Not enough variation to call out a pattern — steady as it goes." else "Your report is based entirely on this device’s local task history.")
                        }
                        Card {
                            Text("Summary", style = MaterialTheme.typography.titleMedium)
                            Row {
                                ReportStat(reportTasks.size.toString(), "tasks")
                                if (reportType == ReportType.Week) {
                                    ReportStat("$completion%", "complete")
                                    ReportStat("${focusMinutes}m", "focus")
                                    bestDay?.let {
                                        ReportStat(it.format(DateTimeFormatter.ofPattern("EEE")), "best day")
                                    }
                                } else {
                                    ReportStat(completed.size.toString(), "done")
                                    ReportStat(skipped.size.toString(), "skipped")
                                    ReportStat("${focusMinutes}m", "focus")
                                }
                            }
                        }
                        Card {
                            Text("Your note", style = MaterialTheme.typography.titleMedium)
                            Text("Optional. Saved when you leave the field.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                label = { Text("What do you want to remember?") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { state -> if (!state.isFocused) saveNote() },
                            )
                        }
                        Card {
                            Text("Task timeline", style = MaterialTheme.typography.titleMedium)
                            reportDays(range.first, range.second, reportTasks).forEach { (date, dayTasks) ->
                                if (reportType == ReportType.Week) {
                                    val completedMinutes = dayTasks
                                        .filter { it.status == "completed" && it.focusMode }
                                        .sumOf(Task::durationMinutes)
                                    Text(
                                        "${date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))} · " +
                                            "${dayTasks.count { it.status == "completed" }}/${dayTasks.size} complete · " +
                                            "${completedMinutes}m focus",
                                    )
                                    weekNotes[date.toString()]?.let {
                                        Text("Note: $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (dayTasks.isEmpty()) Text("No tasks scheduled.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text("${task.reportTime()} · ${task.durationMinutes}m", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(task.status.replaceFirstChar { it.titlecase() })
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

private fun Task.reportTime(): String = runCatching { Instant.parse(startTime).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a")) }.getOrDefault(startTime)

private fun reportHeadline(tasks: List<Task>, previous: List<Task>): String = when {
    tasks.isEmpty() -> "No activity in this period"
    tasks.count { it.status == "completed" } > previous.count { it.status == "completed" } -> "You finished more than in the comparison period"
    else -> "A clear record of this period"
}
