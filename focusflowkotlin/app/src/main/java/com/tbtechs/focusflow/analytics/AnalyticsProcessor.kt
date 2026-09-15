package com.tbtechs.focusflow.analytics

import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.data.repository.FocusSessionRepository
import com.tbtechs.focusflow.data.repository.GreyoutRepository
import com.tbtechs.focusflow.data.repository.HourlyUsageSummary
import com.tbtechs.focusflow.data.repository.UsageStatsRepository
import com.tbtechs.focusflow.data.repository.UsageSummary
import com.tbtechs.focusflow.data.repository.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.round

typealias AnalyticsWindow = String
typealias AnalyticsSourceState = String

const val ANALYTICS_YESTERDAY: AnalyticsWindow = "yesterday"
const val ANALYTICS_WEEK: AnalyticsWindow = "week"
const val ANALYTICS_THREE_MONTHS: AnalyticsWindow = "three_months"

const val SOURCE_LOADED: AnalyticsSourceState = "loaded"
const val SOURCE_UNAVAILABLE: AnalyticsSourceState = "unavailable"
const val SOURCE_FAILED: AnalyticsSourceState = "failed"

data class AnalyticsRange(
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val trendWeekAnchor: ZonedDateTime? = null,
)

data class TemptationEntry(
    val pkg: String,
    val appName: String?,
    val timestamp: Long,
)

data class AnalyticsSourceData(
    val tasks: List<Task>,
    val tasksThisWeek: List<Task>? = null,
    val sessions: List<com.tbtechs.focusflow.data.local.dao.SessionOverrideCountRow>,
    val estimationErrors: List<com.tbtechs.focusflow.data.local.dao.EstimationErrorRow>,
    val tasksByHour: List<com.tbtechs.focusflow.data.local.dao.TasksByHourRow>,
    val weeklyRates: List<com.tbtechs.focusflow.data.local.dao.WeeklyCompletionRateRow>,
    val temptations: List<TemptationEntry>,
    val previousTemptations: List<TemptationEntry>? = null,
    val usageSummary: UsageSummary? = null,
    val usageHourly: HourlyUsageSummary? = null,
    val health: AnalyticsSnapshot.SourceHealth? = null,
)

data class AnalyticsBuildOptions(
    val now: ZonedDateTime = ZonedDateTime.now(),
    val weekStartDay: Int = 0,
    /**
     * When null, the processor checks Usage Access itself for the only window
     * that can read device usage. Supplying this is useful for deterministic
     * tests and for callers that already performed the permission gate.
     */
    val usageStatsPermission: Boolean? = null,
)

fun getAnalyticsRange(
    window: AnalyticsWindow,
    now: ZonedDateTime = ZonedDateTime.now(),
    weekStartDay: Int = 0,
): AnalyticsRange {
    val normalizedWeekStart = weekStartDay.coerceIn(0, 6)
    val sundayIndex = now.dayOfWeek.value % 7

    return when (window) {
        ANALYTICS_YESTERDAY -> {
            val day = now.minusDays(1)
            val start = day.toLocalDate().atStartOfDay(day.zone)
            AnalyticsRange(
                start = start,
                end = start.plusDays(1).minusNanos(1),
                trendWeekAnchor = startOfSundayWeek(start),
            )
        }

        ANALYTICS_THREE_MONTHS -> {
            val start = now.minusDays(89).toLocalDate().atStartOfDay(now.zone)
            val end = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).minusNanos(1)
            AnalyticsRange(
                start = start,
                end = end,
                trendWeekAnchor = startOfSundayWeek(now),
            )
        }

        ANALYTICS_WEEK -> {
            val offset = (sundayIndex - normalizedWeekStart + 7) % 7
            val start = now.minusDays(offset.toLong()).toLocalDate().atStartOfDay(now.zone)
            AnalyticsRange(
                start = start,
                end = start.plusDays(7).minusNanos(1),
                trendWeekAnchor = if (normalizedWeekStart == 0) {
                    start
                } else {
                    startOfSundayWeek(start.minusDays(1))
                },
            )
        }

        else -> throw IllegalArgumentException("Unknown analytics window: $window")
    }
}

private fun startOfSundayWeek(value: ZonedDateTime): ZonedDateTime {
    val daysFromSunday = value.dayOfWeek.value % 7
    return value.toLocalDate().minusDays(daysFromSunday.toLong()).atStartOfDay(value.zone)
}

private fun emptyHourBuckets(): MutableMap<Int, Int> =
    (0..23).associateWith { 0 }.toMutableMap()

private fun emptyTaskHourBuckets(): MutableMap<Int, AnalyticsSnapshot.HourBucket> =
    (0..23).associateWith { AnalyticsSnapshot.HourBucket(total = 0) }.toMutableMap()

private fun emptyDayBuckets(): MutableMap<Int, AnalyticsSnapshot.DayBucket> =
    (0..6).associateWith { AnalyticsSnapshot.DayBucket(total = 0) }.toMutableMap()

private fun parseInstant(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrNull()

private fun rounded(value: Double, places: Double = 100.0): Double =
    round(value * places) / places

private fun buildTaskMetrics(
    tasks: List<Task>,
    estimationErrors: List<com.tbtechs.focusflow.data.local.dao.EstimationErrorRow>,
    taskHourRows: List<com.tbtechs.focusflow.data.local.dao.TasksByHourRow>,
    tasksThisWeek: List<Task>?,
): AnalyticsSnapshot.TaskMetrics {
    val byHour = emptyTaskHourBuckets()
    val byDay = emptyDayBuckets()
    val resultRows = tasks.map { AnalyticsSnapshot.TaskResultRow(it.title, it.status) }
    val taskIds = tasks.mapTo(mutableSetOf()) { it.id }
    var firstTaskHour: Int? = null
    var firstTaskAt: Instant? = null

    tasks.forEach { task ->
        val start = parseInstant(task.startTime) ?: return@forEach
        val local = start.atZone(ZoneId.systemDefault())
        val hour = local.hour
        val day = local.dayOfWeek.value % 7
        val previousHour = byHour.getValue(hour)
        byHour[hour] = previousHour.copy(
            total = previousHour.total + 1,
            completed = previousHour.completed + if (task.status == "completed") 1 else 0,
        )
        val previousDay = byDay.getValue(day)
        byDay[day] = previousDay.copy(
            total = previousDay.total + 1,
            completed = previousDay.completed + if (task.status == "completed") 1 else 0,
        )
        if (firstTaskAt == null || start.isBefore(firstTaskAt!!)) {
            firstTaskAt = start
            firstTaskHour = hour
        }
    }

    // SQLite's local-time aggregation is preferred when it is available.
    taskHourRows.forEach { row ->
        if (row.hour !in 0..23) return@forEach
        byHour[row.hour] = AnalyticsSnapshot.HourBucket(row.total, row.completed)
    }

    return AnalyticsSnapshot.TaskMetrics(
        total = tasks.size,
        completed = tasks.count { it.status == "completed" },
        skipped = tasks.count { it.status == "skipped" },
        skippedThisWeek = (tasksThisWeek ?: tasks).count { it.status == "skipped" },
        missed = tasks.count { it.status == "overdue" },
        resultRows = resultRows,
        byHour = byHour,
        byDayOfWeek = byDay,
        estimationErrorMinutes = estimationErrors
            .filter { it.taskId in taskIds }
            .map { it.actualMinutes - it.plannedMinutes }
            .filter { it.isFinite() },
        firstTaskHour = firstTaskHour,
    )
}

private fun buildSessionMetrics(
    sessions: List<com.tbtechs.focusflow.data.local.dao.SessionOverrideCountRow>,
    estimationErrors: List<com.tbtechs.focusflow.data.local.dao.EstimationErrorRow>,
    generatedAt: Instant,
): AnalyticsSnapshot.SessionMetrics {
    val byHour = emptyHourBuckets()
    val byDay = (0..6).associateWith { 0 }.toMutableMap()
    val durations = mutableListOf<Double>()
    val ratiosByHour = mutableMapOf<Int, MutableList<Double>>()
    var totalFocusMinutes = 0.0
    var hardest: AnalyticsSnapshot.HardestSession? = null

    sessions.forEach { session ->
        val start = parseInstant(session.startedAt) ?: return@forEach
        val local = start.atZone(ZoneId.systemDefault())
        byHour[local.hour] = byHour.getValue(local.hour) + 1
        byDay[local.dayOfWeek.value % 7] = byDay.getValue(local.dayOfWeek.value % 7) + 1
        if (hardest == null || session.overrideCount > hardest!!.attempts) {
            hardest = AnalyticsSnapshot.HardestSession(local.hour, session.overrideCount)
        }
        val end = session.endedAt?.let(::parseInstant) ?: generatedAt
        val duration = max(0.0, Duration.between(start, end).toMillis() / 60_000.0)
        totalFocusMinutes += duration
        durations += duration
    }

    estimationErrors.forEach { row ->
        val hour = row.startHour ?: return@forEach
        if (hour !in 0..23 || row.plannedMinutes <= 0 || row.actualMinutes < 0.0) {
            return@forEach
        }
        ratiosByHour.getOrPut(hour) { mutableListOf() }
            .add(row.actualMinutes / row.plannedMinutes)
    }

    data class Window(val hour: Int, val sampleSize: Int, val averageRatio: Double)
    val windows = ratiosByHour.map { (hour, ratios) ->
        Window(hour, ratios.size, ratios.average())
    }.sortedWith(compareBy<Window> { it.averageRatio }.thenByDescending { it.sampleSize }.thenBy { it.hour })
    val fastest = windows.firstOrNull()
    val nextFastest = windows
        .map { Window(it.hour, it.sampleSize, it.averageRatio) }
        .sortedWith(compareBy<Window> { it.averageRatio }.thenBy { it.hour })
        .getOrNull(1)

    return AnalyticsSnapshot.SessionMetrics(
        total = sessions.size,
        cleanCount = sessions.count { it.overrideCount == 0 },
        totalFocusMinutes = rounded(totalFocusMinutes),
        byHour = byHour,
        byDayOfWeek = byDay,
        avgDurationMinutes = if (durations.isEmpty()) 0.0 else rounded(durations.average()),
        fastestWindowHour = fastest?.hour,
        fastestWindowSampleSize = fastest?.sampleSize ?: 0,
        fastestWindowImprovementPercent = if (fastest != null && nextFastest != null) {
            max(0.0, round((nextFastest.averageRatio - fastest.averageRatio) * 100.0)).toInt()
        } else {
            0
        },
        hardestSession = hardest,
    )
}

private fun buildBlockingMetrics(
    temptations: List<TemptationEntry>,
): AnalyticsSnapshot.BlockingMetrics {
    val byHour = emptyHourBuckets()
    val byApp = mutableMapOf<String, AnalyticsSnapshot.AppCount>()

    temptations.forEach { entry ->
        val local = Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault())
        byHour[local.hour] = byHour.getValue(local.hour) + 1
        val app = entry.appName?.takeIf { it.isNotBlank() } ?: entry.pkg
        val current = byApp[entry.pkg]
        byApp[entry.pkg] = AnalyticsSnapshot.AppCount(
            appName = current?.appName ?: app,
            count = (current?.count ?: 0) + 1,
        )
    }

    // Kotlin's map preserves insertion order, so this stable count-only sort
    // matches JavaScript's Object.values(...).sort(...) tie behavior.
    val topApp = byApp.values.sortedByDescending { it.count }.firstOrNull()
    val peak = byHour.entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
        .firstOrNull()
    return AnalyticsSnapshot.BlockingMetrics(
        totalAttempts = temptations.size,
        byHour = byHour,
        byApp = byApp,
        peakHour = peak?.takeIf { it.value > 0 }?.key,
        topApp = topApp,
        topAppShare = topApp?.let { if (temptations.isEmpty()) null else it.count.toDouble() / temptations.size },
    )
}

private fun buildTrendMetrics(
    weeklyRates: List<com.tbtechs.focusflow.data.local.dao.WeeklyCompletionRateRow>,
    blockingAttempts: Int,
    blockingAttemptsPrev: Int?,
    range: AnalyticsRange,
    expectedWeekCount: Int,
): AnalyticsSnapshot.TrendMetrics {
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    val ratesByWeek = weeklyRates.associateBy { it.weekStart }
    val latest = range.trendWeekAnchor ?: startOfSundayWeek(range.end)
    val weeks = (0 until expectedWeekCount).map { index ->
        val date = latest.minusWeeks((expectedWeekCount - index - 1).toLong()).toLocalDate()
        val weekStart = date.format(formatter)
        val row = ratesByWeek[weekStart]
        val hasData = row != null && row.total > 0
        AnalyticsSnapshot.WeekTrend(
            weekStart = weekStart,
            completionRate = if (hasData) row!!.completed.toDouble() / row.total else 0.0,
            hasData = hasData,
        )
    }
    return AnalyticsSnapshot.TrendMetrics(
        completionRatePrev = weeks.getOrNull(weeks.lastIndex - 1)?.completionRate,
        completionRateCurr = weeks.lastOrNull()?.completionRate ?: 0.0,
        blockingAttemptsPrev = blockingAttemptsPrev,
        blockingAttemptsCurr = blockingAttempts,
        weekByWeek = weeks,
        weeksWithData = weeks.count { it.hasData },
    )
}

private fun inclusiveLocalDayCount(range: AnalyticsRange): Int =
    max(1, Duration.between(
        range.start.toLocalDate().atStartOfDay(range.start.zone),
        range.end.toLocalDate().atStartOfDay(range.end.zone),
    ).toDays().toInt() + 1)

private fun phoneUsagePeriod(hour: Int?): String? = when {
    hour == null -> null
    hour in 5..11 -> "morning"
    hour in 12..16 -> "afternoon"
    hour in 17..20 -> "evening"
    else -> "night"
}

private fun buildPhoneUsageMetrics(
    usageSummary: UsageSummary?,
    usageHourly: HourlyUsageSummary?,
    range: AnalyticsRange,
): AnalyticsSnapshot.PhoneUsage? {
    val milliseconds = usageHourly?.foregroundMillisecondsByHour ?: return null
    val dayCount = inclusiveLocalDayCount(range)
    val byHour = emptyHourBuckets().mapValues { 0.0 }.toMutableMap()
    (0..23).forEach { hour ->
        val value = milliseconds.getOrNull(hour) ?: 0L
        if (value > 0) byHour[hour] = rounded(value / 60_000.0 / dayCount)
    }
    val peak = byHour.entries.sortedWith(compareByDescending<Map.Entry<Int, Double>> { it.value }.thenBy { it.key })
        .firstOrNull()
    val peakHour = peak?.takeIf { it.value > 0.0 }?.key
    val app = usageSummary?.apps?.firstOrNull()
    return AnalyticsSnapshot.PhoneUsage(
        byHour = byHour,
        peakHour = peakHour,
        peakPeriod = phoneUsagePeriod(peakHour),
        heaviestApp = app?.let { AnalyticsSnapshot.HeaviestApp(it.appName.ifBlank { it.packageName }, it.foregroundMinutes.toDouble()) },
    )
}

fun createAnalyticsSnapshot(
    window: AnalyticsWindow,
    range: AnalyticsRange,
    source: AnalyticsSourceData,
    generatedAt: String = Instant.now().toString(),
): AnalyticsSnapshot {
    val generatedInstant = parseInstant(generatedAt) ?: Instant.now()
    return AnalyticsSnapshot(
        generatedAt = generatedAt,
        window = window,
        range = SnapshotRange(
            startISO = range.start.toInstant().toString(),
            endISO = range.end.toInstant().toString(),
        ),
        tasks = buildTaskMetrics(source.tasks, source.estimationErrors, source.tasksByHour, source.tasksThisWeek),
        sessions = buildSessionMetrics(source.sessions, source.estimationErrors, generatedInstant),
        blocking = buildBlockingMetrics(source.temptations),
        trends = buildTrendMetrics(
            source.weeklyRates,
            source.temptations.size,
            source.previousTemptations?.size,
            range,
            if (window == ANALYTICS_THREE_MONTHS) 12 else 2,
        ),
        sourceHealth = source.health,
        phoneUsage = if (window == ANALYTICS_THREE_MONTHS) {
            buildPhoneUsageMetrics(source.usageSummary, source.usageHourly, range)
        } else {
            null
        },
    )
}

private data class SourceRead<T>(val value: T, val state: AnalyticsSourceState)

private suspend fun <T> readSource(read: suspend () -> T, fallback: T): SourceRead<T> =
    try {
        SourceRead(read(), SOURCE_LOADED)
    } catch (cancelled: CancellationException) {
        // A cancelled stats load must remain cancelled. Treating it as a
        // failed source lets an obsolete reload continue building a snapshot.
        throw cancelled
    } catch (_: Exception) {
        SourceRead(fallback, SOURCE_FAILED)
    }

private fun parseTemptationLog(raw: String): List<TemptationEntry> {
    val array = JSONArray(raw)
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val pkg = item.optString("pkg").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        TemptationEntry(
            pkg = pkg,
            appName = item.optString("appName").takeIf { it.isNotBlank() },
            timestamp = item.optLong("timestamp", 0L),
        )
    }
}

private fun previousAnalyticsRange(range: AnalyticsRange): AnalyticsRange {
    val durationMs = Duration.between(range.start.toInstant(), range.end.toInstant()).toMillis() + 1
    val end = range.start.toInstant().minusMillis(1).atZone(range.start.zone)
    return AnalyticsRange(
        start = end.toInstant().minusMillis(durationMs - 1).atZone(range.start.zone),
        end = end,
    )
}

class AnalyticsProcessor(
    private val taskRepository: TaskRepository,
    private val focusSessionRepository: FocusSessionRepository,
    private val greyoutRepository: GreyoutRepository,
    private val usageStatsRepository: UsageStatsRepository,
) {
    suspend fun hasUsageStatsPermission(): Boolean = usageStatsRepository.hasPermission()

    suspend fun buildAnalyticsSnapshot(
        window: AnalyticsWindow,
        options: AnalyticsBuildOptions = AnalyticsBuildOptions(),
    ): AnalyticsSnapshot {
        val range = getAnalyticsRange(window, options.now, options.weekStartDay)
        val startISO = range.start.toInstant().toString()
        val endISO = range.end.toInstant().toString()
        val previousRange = previousAnalyticsRange(range)
        val expectedWeekCount = if (window == ANALYTICS_THREE_MONTHS) 12 else 2
        val weekForSkipComparison = if (window == ANALYTICS_YESTERDAY) {
            getAnalyticsRange(ANALYTICS_WEEK, options.now, options.weekStartDay)
        } else {
            null
        }

        val usagePermission = if (window == ANALYTICS_THREE_MONTHS) {
            options.usageStatsPermission ?: usageStatsRepository.hasPermission()
        } else {
            false
        }
        val usageReads = if (window == ANALYTICS_THREE_MONTHS && usagePermission) {
            coroutineScope {
                val summary = async { readSource({ usageStatsRepository.getUsageSummary(range.start.toInstant().toEpochMilli(), range.end.toInstant().toEpochMilli()) }, null) }
                val hourly = async { readSource({ usageStatsRepository.getHourlyUsageSummary(range.start.toInstant().toEpochMilli(), range.end.toInstant().toEpochMilli()) }, null) }
                summary.await() to hourly.await()
            }
        } else {
            SourceRead<UsageSummary?>(null, SOURCE_UNAVAILABLE) to
                SourceRead<HourlyUsageSummary?>(null, SOURCE_UNAVAILABLE)
        }

        return coroutineScope {
            // These independent reads intentionally remain concurrent. There are
            // five analytics DB query families plus the week-only comparison read.
            val tasks = async { readSource({ taskRepository.getTasksInDateRange(startISO, endISO) }, emptyList()) }
            val tasksThisWeek = async {
                weekForSkipComparison?.let { week ->
                    val result = readSource(
                        { taskRepository.getTasksInDateRange(week.start.toInstant().toString(), week.end.toInstant().toString()) },
                        emptyList(),
                    )
                    SourceRead<List<Task>?>(result.value, result.state)
                } ?: SourceRead<List<Task>?>(null, SOURCE_UNAVAILABLE)
            }
            val sessions = async { readSource({ focusSessionRepository.getSessionsWithOverrideCount(startISO, endISO) }, emptyList()) }
            val estimationErrors = async { readSource({ focusSessionRepository.getEstimationErrors(startISO, endISO) }, emptyList()) }
            val tasksByHour = async { readSource({ taskRepository.getTasksByHourOfDay(startISO, endISO) }, emptyList()) }
            val weeklyRates = async {
                readSource(
                    { focusSessionRepository.getWeeklyCompletionRates(expectedWeekCount, options.now.toInstant()) },
                    emptyList(),
                )
            }
            val temptations = async {
                readSource({ parseTemptationLog(greyoutRepository.getTemptationLog()) }, emptyList())
            }

            val tasksResult = tasks.await()
            val tasksThisWeekResult = tasksThisWeek.await()
            val sessionsResult = sessions.await()
            val estimationResult = estimationErrors.await()
            val taskHourResult = tasksByHour.await()
            val weeklyResult = weeklyRates.await()
            val temptationResult = temptations.await()
            val allTemptations = temptationResult.value
            val currentStart = range.start.toInstant().toEpochMilli()
            val currentEnd = range.end.toInstant().toEpochMilli()
            val previousStart = previousRange.start.toInstant().toEpochMilli()
            val previousEnd = previousRange.end.toInstant().toEpochMilli()
            val currentTemptations = allTemptations.filter { it.timestamp in currentStart..currentEnd }
            val previousTemptations = allTemptations.filter { it.timestamp in previousStart..previousEnd }

            createAnalyticsSnapshot(
                window = window,
                range = range,
                source = AnalyticsSourceData(
                    tasks = tasksResult.value,
                    tasksThisWeek = tasksThisWeekResult.value,
                    sessions = sessionsResult.value,
                    estimationErrors = estimationResult.value,
                    tasksByHour = taskHourResult.value,
                    weeklyRates = weeklyResult.value,
                    temptations = currentTemptations,
                    previousTemptations = previousTemptations,
                    usageSummary = usageReads.first.value,
                    usageHourly = usageReads.second.value,
                    health = AnalyticsSnapshot.SourceHealth(
                        tasks = tasksResult.state,
                        sessions = sessionsResult.state,
                        estimationErrors = estimationResult.state,
                        tasksByHour = taskHourResult.state,
                        weeklyRates = weeklyResult.state,
                        temptations = temptationResult.state,
                        usageSummary = usageReads.first.state,
                        usageHourly = usageReads.second.state,
                    ),
                ),
                generatedAt = options.now.toInstant().toString(),
            )
        }
    }
}