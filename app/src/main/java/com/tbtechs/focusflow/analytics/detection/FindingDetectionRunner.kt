package com.tbtechs.focusflow.analytics.detection

import android.util.Log
import com.tbtechs.focusflow.data.local.dao.AppSessionDao
import com.tbtechs.focusflow.data.local.dao.AppUsageRangeRow
import com.tbtechs.focusflow.data.local.dao.FirstSessionRow
import com.tbtechs.focusflow.data.local.dao.SessionStatRow
import com.tbtechs.focusflow.data.local.dao.DailyAppUsageDao
import com.tbtechs.focusflow.data.local.dao.DayRatingDao
import com.tbtechs.focusflow.data.local.dao.FocusSessionDao
import com.tbtechs.focusflow.data.local.dao.TaskDao
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity
import com.tbtechs.focusflow.data.local.entity.ClarifyingQuestionEntity
import com.tbtechs.focusflow.data.repository.ClarifyingQuestionRepository
import com.tbtechs.focusflow.data.repository.FindingRepository
import com.tbtechs.focusflow.analytics.DetectorUsageHistory
import com.tbtechs.focusflow.analytics.UsageHistoryRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Runs the existing-data and manipulation detectors against their own rolling
 * windows.
 * Each detector is isolated so one malformed row cannot prevent the others
 * from producing findings.
 */
class FindingDetectionRunner(
    private val taskDao: TaskDao,
    private val focusSessionDao: FocusSessionDao,
    private val findingRepository: FindingRepository,
    private val dailyAppUsageDao: DailyAppUsageDao,
    private val appSessionDao: AppSessionDao,
    private val dayRatingDao: DayRatingDao,
    private val clarifyingQuestionRepository: ClarifyingQuestionRepository,
    private val usageHistoryRepository: UsageHistoryRepository? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = clock.zone,
) {
    private companion object {
        private const val TAG = "FindingDetectionRunner"
        private val ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE
    }

    suspend fun runAll() {
        runCatching { runPostFailureCascade() }
            .onFailure { Log.e(TAG, "postFailureCascade failed", it) }
        runCatching { runSessionSweetSpot() }
            .onFailure { Log.e(TAG, "sessionSweetSpot failed", it) }
        runCatching { runEstimationDrift() }
            .onFailure { Log.e(TAG, "estimationDrift failed", it) }
        runCatching { runDayOfWeekOutlier() }
            .onFailure { Log.e(TAG, "dayOfWeekOutlier failed", it) }
        runCatching { runMorningHijack() }
            .onFailure { Log.e(TAG, "morningHijack failed", it) }
        runCatching { runVariableRewardLoop() }
            .onFailure { Log.e(TAG, "variableRewardLoop failed", it) }
        runCatching { runEscalatingCapture() }
            .onFailure { Log.e(TAG, "escalatingCapture failed", it) }
        runCatching { runStreakLockIn() }
            .onFailure { Log.e(TAG, "streakLockIn failed", it) }
        runCatching { runInfiniteSessionDesign() }
            .onFailure { Log.e(TAG, "infiniteSessionDesign failed", it) }
        runCatching { runSubstitution() }
            .onFailure { Log.e(TAG, "substitution failed", it) }
        runCatching { runAllowanceSuggestion() }
            .onFailure { Log.e(TAG, "allowanceSuggestion failed", it) }
    }

    private suspend fun runPostFailureCascade() {
        val tasks = taskDao.getTasksInDateRange(dateRangeStart(60), dateRangeEnd())
        detectPostFailureCascade(tasks)?.let { findingRepository.submit(it) }
    }

    private suspend fun runSessionSweetSpot() {
        val sessions = focusSessionDao.getSessionsWithOverrideCount(
            startISO = isoRangeStart(90),
            endISO = isoRangeEnd(),
        )
        detectSessionSweetSpot(sessions)?.let { findingRepository.submit(it) }
    }

    private suspend fun runEstimationDrift() {
        val errors = focusSessionDao.getEstimationErrors(
            startISO = isoRangeStart(60),
            endISO = isoRangeEnd(),
        )
        detectEstimationDrift(errors)?.let { findingRepository.submit(it) }
    }

    private suspend fun runDayOfWeekOutlier() {
        val tasks = taskDao.getTasksInDateRange(dateRangeStart(42), dateRangeEnd())
        detectDayOfWeekOutlier(tasks)?.let { findingRepository.submit(it) }
    }

    private suspend fun runMorningHijack() {
        val range = UsageDetectorWindows.range(
            UsageDetectorWindow.MORNING_HIJACK,
            today = localToday(),
        )
        val history = detectorHistory(range.start.toString(), range.end.toString()) ?: return
        val firstSessions = firstSessions(history.sessions)
        if (firstSessions.isEmpty()) return

        val categories = history.appDays
            .groupBy { it.packageName }
            .mapValues { (_, rows) ->
                rows.asReversed().firstOrNull { it.category != null }?.category
            }

        val result = detectMorningHijack(firstSessions, categories) ?: return
        if (!findingRepository.submit(result.finding)) return

        maybeQueueMorningHijackQuestion(result)
    }

    private suspend fun maybeQueueMorningHijackQuestion(result: MorningHijackResult) {
        val dates = result.qualifyingDates.sorted()
        if (dates.isEmpty()) return

        val ratings = dayRatingDao.getForDateRange(
            startDate = dates.first(),
            endDate = dates.last(),
        ).filter { it.date in result.qualifyingDates }
        if (ratings.size < 3) return

        val averageRating = ratings.map { it.rating }.average()
        if (averageRating < 7.0) return

        val mostRecent = ratings.maxByOrNull { it.date } ?: return
        val appName = result.finding.subjectAppName ?: return
        val contextJson = """{"appName":"${jsonEscape(appName)}","date":"${mostRecent.date}","rating":"${mostRecent.rating}"}"""

        clarifyingQuestionRepository.submitIfAllowed(
            ClarifyingQuestionEntity(
                id = UUID.randomUUID().toString(),
                questionType = "morning_high_rating",
                dateOfConcern = mostRecent.date,
                contextJson = contextJson,
                askedAt = clock.instant().toString(),
                response = null,
                respondedAt = null,
            ),
        )
    }

    private suspend fun runVariableRewardLoop() {
        val range = UsageDetectorWindows.range(
            UsageDetectorWindow.VARIABLE_REWARD_LOOP,
            today = localToday(),
        )
        val history = detectorHistory(range.start.toString(), range.end.toString()) ?: return
        val dailyStats = sessionStats(history.sessions)
        if (dailyStats.isEmpty()) return

        val appNames = history.appDays.associate { it.packageName to it.appName }
        val roughCandidates = dailyStats
            .groupBy { it.packageName }
            .filter { (_, days) ->
                days.size >= 10 &&
                    days.sumOf { it.sessionCount }.toDouble() / days.size >= 5.0
            }
            .keys
        val rawByPackage = loadRawSessions(history.sessions, roughCandidates)

        detectVariableRewardLoop(dailyStats, rawByPackage, appNames)
            ?.let { findingRepository.submit(it) }
    }

    private suspend fun runInfiniteSessionDesign() {
        val range = UsageDetectorWindows.range(
            UsageDetectorWindow.INFINITE_SESSION_DESIGN,
            today = localToday(),
        )
        val history = detectorHistory(range.start.toString(), range.end.toString()) ?: return
        val dailyStats = sessionStats(history.sessions)
        if (dailyStats.isEmpty()) return

        val appNames = history.appDays.associate { it.packageName to it.appName }
        val categories = history.appDays
            .groupBy { it.packageName }
            .mapValues { (_, rows) ->
                rows.asReversed().firstOrNull { it.category != null }?.category
            }
        val roughCandidates = dailyStats
            .groupBy { it.packageName }
            .filter { (packageName, days) ->
                categories[packageName] != "utility" && days.size >= 14
            }
            .keys
        val rawByPackage = loadRawSessions(history.sessions, roughCandidates)

        detectInfiniteSessionDesign(dailyStats, rawByPackage, appNames, categories)
            ?.let { findingRepository.submit(it) }
    }

    private suspend fun runEscalatingCapture() {
        val range = UsageDetectorWindows.range(
            UsageDetectorWindow.ESCALATING_CAPTURE,
            today = localToday(),
        )
        val usageRows = detectorHistory(range.start.toString(), range.end.toString())?.appDays ?: return
        if (usageRows.isEmpty()) return

        detectEscalatingCapture(usageRows, localToday())
            ?.let { findingRepository.submit(it) }
    }

    private suspend fun runStreakLockIn() {
        val windowDays = 14
        val range = UsageDetectorWindows.range(
            UsageDetectorWindow.STREAK_LOCK_IN,
            today = localToday(),
        )
        val usageRows = detectorHistory(range.start.toString(), range.end.toString())?.appDays ?: return
        if (usageRows.isEmpty()) return

        val ratings = dayRatingDao.getForDateRange(range.start.toString(), range.end.toString())
        detectStreakLockIn(usageRows, ratings, windowDays)
            ?.let { findingRepository.submit(it) }
    }

    private suspend fun runSubstitution() {
        val range = UsageDetectorWindows.range(
            UsageDetectorWindow.SUBSTITUTION,
            today = localToday(),
        )
        val usageRows = detectorHistory(range.start.toString(), range.end.toString())?.appDays ?: return
        if (usageRows.isEmpty()) return

        detectSubstitution(usageRows, localToday())
            ?.let { findingRepository.submit(it) }
    }

    private suspend fun runAllowanceSuggestion() {
        val range = UsageDetectorWindows.range(
            UsageDetectorWindow.ALLOWANCE_SUGGESTION,
            today = localToday(),
        )
        val usageRows = detectorHistory(range.start.toString(), range.end.toString())?.appDays ?: return
        if (usageRows.isEmpty()) return

        val ratings = dayRatingDao.getForDateRange(range.start.toString(), range.end.toString())
        detectAllowanceSuggestion(usageRows, ratings)
            ?.let { findingRepository.submit(it) }
    }

    private suspend fun detectorHistory(
        startDate: String,
        endDate: String,
    ): DetectorUsageHistory? {
        usageHistoryRepository?.let {
            return it.detectorHistory(
                startDate = startDate,
                endDate = endDate,
                today = localToday().format(ISO_DATE),
            )
        }
        return DetectorUsageHistory(
            appDays = dailyAppUsageDao.getForDateRange(startDate, endDate),
            sessions = appSessionDao.getAllSessionsInRange(startDate, endDate),
        )
    }

    private fun firstSessions(sessions: List<AppSessionEntity>): List<FirstSessionRow> =
        sessions.groupBy { it.localDate }
            .mapNotNull { (date, rows) ->
                rows.minByOrNull { it.startedAt }?.let { first ->
                    FirstSessionRow(
                        localDate = date,
                        packageName = first.packageName,
                        appName = first.appName,
                        startedAt = first.startedAt,
                    )
                }
            }
            .sortedBy { it.localDate }

    private fun sessionStats(sessions: List<AppSessionEntity>): List<SessionStatRow> =
        sessions.filter { it.durationMs > 0L }
            .groupBy { it.packageName to it.localDate }
            .map { (key, rows) ->
                val durations = rows.map { it.durationMs }
                SessionStatRow(
                    packageName = key.first,
                    localDate = key.second,
                    sessionCount = rows.size,
                    avgDurationMs = durations.average(),
                    minDurationMs = durations.min(),
                    maxDurationMs = durations.max(),
                    totalMs = durations.sum(),
                )
            }
            .sortedBy { it.localDate }

    private fun loadRawSessions(
        sessions: List<AppSessionEntity>,
        packages: Set<String>,
    ): Map<String, List<AppSessionEntity>> =
        packages.associateWith { packageName ->
            sessions.filter { it.packageName == packageName }
        }

    private fun dateRangeStart(days: Long): String =
        localToday().minusDays(days).format(ISO_DATE)

    private fun dateRangeEnd(): String =
        localToday().minusDays(1).format(ISO_DATE)

    private fun isoRangeStart(days: Long): String =
        LocalDateTime.of(localToday().minusDays(days), LocalTime.MIDNIGHT).toString()

    private fun isoRangeEnd(): String =
        LocalDateTime.ofInstant(clock.instant(), zoneId).toString()

    private fun localToday(): LocalDate =
        LocalDate.ofInstant(clock.instant(), zoneId)

    private fun jsonEscape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}