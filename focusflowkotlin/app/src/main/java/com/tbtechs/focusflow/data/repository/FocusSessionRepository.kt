package com.tbtechs.focusflow.data.repository

import com.tbtechs.focusflow.data.local.dao.DailyCompletionDao
import com.tbtechs.focusflow.data.local.dao.FocusOverrideDao
import com.tbtechs.focusflow.data.local.dao.FocusSessionDao
import com.tbtechs.focusflow.data.local.dao.SessionOverrideCountRow
import com.tbtechs.focusflow.data.local.dao.EstimationErrorRow
import com.tbtechs.focusflow.data.local.dao.RecentSessionSummaryRow
import com.tbtechs.focusflow.data.local.dao.LifetimeStatsRow
import com.tbtechs.focusflow.data.local.dao.TaskDao
import com.tbtechs.focusflow.data.local.entity.DailyCompletionEntity
import com.tbtechs.focusflow.data.local.entity.FocusOverrideEntity
import com.tbtechs.focusflow.data.local.entity.FocusSessionEntity
import com.tbtechs.focusflow.data.model.FocusSession
import com.tbtechs.focusflow.analytics.LifetimeStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * FocusSessionRepository
 *
 * The single point of access for:
 *  - Focus session lifecycle (start / end / query active)
 *  - Temptation override logging and counting
 *  - Daily completion ledger (streak source of truth)
 *  - Analytics aggregations that cross focus_sessions and focus_overrides
 *
 * Called by: `FocusSessionViewModel`, `AnalyticsProcessor`.
 *
 * **SharedPreferences mirror**: Per ARCHITECTURE.md §3.3 and Risk 9, methods
 * that start/end a focus session must also write the enforcement-layer
 * SharedPreferences keys (`focus_active`, `task_id`, `task_end_ms`, etc.) so
 * that `ForegroundTaskService` and `AppBlockerAccessibilityService` pick up
 * the change synchronously. This is the join point between the Room layer and
 * the enforcement layer — implement via [SettingsRepository] in Track C when
 * wiring the ViewModel. A TODO is left here as a reminder.
 *
 * **Widget push**: per Risk 9, [startFocusSession] and [endFocusSession] must
 * call `AppWidgetManager.updateAppWidget()` after writing to Room. Implement
 * in Track C when [FocusSessionViewModel] is wired.
 *
 * Constructor injection keeps the repository unit-testable with fake DAOs.
 * All four DAOs are obtained from the database singleton in `di/AppModule.kt`.
 */
class FocusSessionRepository(
    private val focusSessionDao: FocusSessionDao,
    private val focusOverrideDao: FocusOverrideDao,
    private val dailyCompletionDao: DailyCompletionDao,
    /** Injected for [backfillDayCompletions] — reads task start_time/status. */
    private val taskDao: TaskDao,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ─── Focus Session lifecycle ──────────────────────────────────────────────

    /**
     * Inserts a new active focus session row. Maps to `dbStartFocusSession`.
     *
     * TODO (Track C): after the Room write, mirror enforcement state to
     * SharedPreferences via SettingsRepository (keys: `focus_active`, `task_id`,
     * `task_end_ms`, `allowed_packages`, etc.) so ForegroundTaskService picks
     * up the change without a restart.
     *
     * TODO (Track C): push widget update via AppWidgetManager.
     */
    suspend fun startFocusSession(session: FocusSession) {
        focusSessionDao.insertSession(
            FocusSessionEntity(
                taskId          = session.taskId,
                startedAt       = session.startedAt,
                isActive        = true,
                allowedPackages = json.encodeToString(session.allowedPackages),
            )
        )
    }

    /**
     * Marks the active session for [taskId] as ended. Maps to `dbEndFocusSession`.
     * Stamps [endedAt] with the current UTC instant.
     *
     * Returns the number of rows affected (0 if no active session found).
     *
     * TODO (Track C): clear enforcement SharedPreferences keys and push widget.
     */
    suspend fun endFocusSession(taskId: String): Int =
        focusSessionDao.endSession(taskId = taskId, endedAt = Instant.now().toString())

    /**
     * Returns the currently active session, or null if none is running.
     * Maps to `dbGetActiveFocusSession`.
     */
    suspend fun getActiveFocusSession(): FocusSession? =
        focusSessionDao.getActiveSession()?.toDomain()

    /** Emits the active session whenever Room observes a lifecycle change. */
    fun observeActiveFocusSession(): Flow<FocusSession?> =
        focusSessionDao.observeActiveSession().map { it?.toDomain() }

    /**
     * Returns the total focus minutes logged today (local calendar day).
     * Maps to `dbGetTodayFocusMinutes`.
     *
     * The JS implementation caps each session at 6 hours and sums the rest
     * in memory. This is replicated here because the capping arithmetic
     * cannot be expressed in plain SQLite without a custom function.
     *
     * Sessions still active at call time use [System.currentTimeMillis] as
     * their end point, matching the JS `const end = row.ended_at ? … : now`.
     */
    suspend fun getTodayFocusMinutes(): Int {
        val startOfDay = LocalDate.now(ZoneId.systemDefault())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
        val sessions = focusSessionDao.getSessionsFrom(startOfDay.toString())
        val now = System.currentTimeMillis()
        val maxSessionMs = 6L * 60 * 60 * 1_000
        var totalMs = 0L
        for (s in sessions) {
            val start = Instant.parse(s.startedAt).toEpochMilli()
            val end   = s.endedAt?.let { Instant.parse(it).toEpochMilli() } ?: now
            totalMs  += minOf(maxOf(0L, end - start), maxSessionMs)
        }
        return (totalMs / 60_000L).toInt()
    }

    /**
     * Returns the longest consecutive run of days with at least 50% completion.
     * The profile journey uses this instead of presenting the current streak as
     * a best-ever value.
     */
    suspend fun getBestStreakDays(): Int {
        val rows = dailyCompletionDao.getAllCompletionsAsc()
        var best = 0
        var current = 0
        var previousDate: LocalDate? = null
        for (row in rows) {
            val date = runCatching { LocalDate.parse(row.date) }.getOrNull() ?: continue
            val successful = row.total > 0 && row.completed.toDouble() / row.total >= 0.5
            if (!successful) {
                current = 0
                previousDate = date
                continue
            }
            current = if (previousDate != null &&
                ChronoUnit.DAYS.between(previousDate, date) == 1L
            ) {
                current + 1
            } else {
                1
            }
            best = maxOf(best, current)
            previousDate = date
        }
        return best
    }

    /**
     * Returns the most recently completed session within [maxAgeMinutes] of now,
     * with its override count and joined task fields.
     * Maps to `dbGetRecentCompletedFocusSession`.
     *
     * [maxAgeMinutes] is clamped to [1, 120] matching the JS guard.
     * Returns null if no matching session exists.
     *
     * [RecentSessionSummaryRow.taskTitle] defaults to "Focus session" when null
     * (task was deleted), mirroring the JS fallback.
     * [RecentSessionSummaryRow.overrideCount] is clamped to ≥ 0.
     */
    suspend fun getRecentCompletedSession(
        maxAgeMinutes: Int = 30,
    ): RecentSessionSummaryRow? {
        val bounded = maxAgeMinutes.coerceIn(1, 120)
        val now     = Instant.now()
        val cutoff  = now.minusMillis(bounded * 60_000L).toString()
        return focusSessionDao.getRecentCompletedSession(
            cutoff = cutoff,
            now    = now.toString(),
        )
    }

    // ─── Override logging ─────────────────────────────────────────────────────

    /**
     * Logs a temptation override event. Maps to `dbLogFocusOverride`.
     * Failures are silently swallowed (matching the JS try/catch wrapper).
     */
    suspend fun logFocusOverride(taskId: String, appName: String, reason: String? = null) {
        runCatching {
            focusOverrideDao.insertOverride(
                FocusOverrideEntity(
                    taskId      = taskId,
                    appName     = appName,
                    overriddenAt = Instant.now().toString(),
                    reason      = reason,
                )
            )
        }
        // Failure is non-fatal — mirrors the JS `catch (e) { logger.error(…) }`.
    }

    /**
     * Returns the number of override events logged since the start of the
     * current local calendar day. Maps to `dbGetTodayOverrideCount`.
     *
     * Uses local-midnight ISO timestamp (device timezone), matching the JS
     * `startOfDay.setHours(0, 0, 0, 0)` pattern.
     */
    suspend fun getTodayOverrideCount(): Int {
        val startOfDay = LocalDate.now(ZoneId.systemDefault())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toString()
        return focusOverrideDao.getOverrideCountFrom(startOfDay)
    }

    /**
     * Returns the number of override events within [[startISO], [endISO]].
     * Maps to `dbGetOverrideCountInRange`.
     */
    suspend fun getOverrideCountInRange(startISO: String, endISO: String): Int =
        focusOverrideDao.getOverrideCountInRange(startISO, endISO)

    // ─── Daily completion ledger ──────────────────────────────────────────────

    /**
     * Reactive stream of all daily completion rows. Used by a streak widget
     * or analytics chart that needs live updates as tasks complete.
     */
    fun observeAllCompletions(): Flow<List<DailyCompletionEntity>> =
        dailyCompletionDao.observeAllCompletions()

    /**
     * Records or overwrites today's task-completion counts.
     * Maps to `dbRecordDayCompletion`.
     *
     * [date] is derived from the current local calendar day using
     * [LocalDate.now(ZoneId.systemDefault())] to match `localDateString()` in
     * `database.ts`. UTC midnight must NOT be used — it breaks the streak for
     * users in UTC-N timezones whose evening tasks cross the UTC date boundary.
     */
    suspend fun recordDayCompletion(completed: Int, total: Int) {
        val today = LocalDate.now(ZoneId.systemDefault()).toString()
        dailyCompletionDao.upsertCompletion(
            DailyCompletionEntity(date = today, completed = completed, total = total)
        )
    }

    /**
     * Rebuilds the [daily_completions] ledger from the raw [tasks] table for
     * the last [daysBack] days. Maps to `dbBackfillDayCompletions`.
     *
     * Called on app startup so the streak doesn't break just because the user
     * never opened the Stats screen on a given day. Existing rows are overwritten
     * (INSERT OR REPLACE) so the derived value always reflects current statuses.
     * Failures are silently swallowed, matching the JS pattern.
     *
     * Note: this method crosses DAOs (reads [TaskDao], writes [DailyCompletionDao]).
     * That's intentional — it's a cross-table computation that belongs at the
     * repository layer, not inside a single DAO.
     */
    suspend fun backfillDayCompletions(daysBack: Int = 30) {
        runCatching {
            val cutoff = LocalDate.now(ZoneId.systemDefault())
                .minusDays((daysBack - 1).toLong())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toString()

            val rows = taskDao.getTaskStartStatusFrom(cutoff)

            // Build date → (completed, total) buckets using local calendar dates,
            // matching the JS `localDateString(new Date(r.start_time))` logic.
            val buckets = mutableMapOf<String, Pair<Int, Int>>()
            val tz = ZoneId.systemDefault()
            for (row in rows) {
                val date      = Instant.parse(row.startTime).atZone(tz).toLocalDate().toString()
                val (c, t)    = buckets.getOrDefault(date, 0 to 0)
                val completed = if (row.status == "completed") c + 1 else c
                buckets[date] = completed to (t + 1)
            }

            val entities = buckets.map { (date, counts) ->
                DailyCompletionEntity(
                    date      = date,
                    completed = counts.first,
                    total     = counts.second,
                )
            }
            dailyCompletionDao.upsertCompletions(entities)
        }
        // Failure is non-fatal — mirrors `logger.error('database', …)` in JS.
    }

    // ─── Analytics (called by AnalyticsProcessor) ─────────────────────────────

    /**
     * Returns sessions overlapping [[startISO], [endISO]) with their override
     * counts. Maps to `dbGetSessionsWithOverrideCount`.
     */
    suspend fun getSessionsWithOverrideCount(
        startISO: String,
        endISO: String,
    ): List<SessionOverrideCountRow> =
        focusSessionDao.getSessionsWithOverrideCount(startISO, endISO)

    /**
     * Returns estimation-error rows for completed sessions in [[startISO], [endISO]].
     * Maps to `dbGetEstimationErrors`.
     */
    suspend fun getEstimationErrors(
        startISO: String,
        endISO: String,
    ): List<EstimationErrorRow> =
        focusSessionDao.getEstimationErrors(startISO, endISO)

    /**
     * Aggregates [daily_completions] into weekly buckets (Sunday anchor) for
     * the last [numWeeks] weeks. Maps to `dbGetWeeklyCompletionRates`.
     *
     * [numWeeks] is clamped to [1, 12] matching the JS `Math.max(1, Math.min(12, …))`.
     */
    suspend fun getWeeklyCompletionRates(
        numWeeks: Int,
        now: Instant = Instant.now(),
    ): List<com.tbtechs.focusflow.data.local.dao.WeeklyCompletionRateRow> {
        val weeks = numWeeks.coerceIn(1, 12)
        // Sunday anchor matching dayjs().startOf('week').subtract(weeks-1, 'week')
        val firstWeek = now.atZone(ZoneId.systemDefault()).toLocalDate()
            .with(java.time.DayOfWeek.SUNDAY)
            .minusWeeks((weeks - 1).toLong())
            .toString()
        return dailyCompletionDao.getWeeklyCompletionRates(firstWeek)
    }

    /** Returns bounded lifetime aggregates for Stats and AchievementEngine. */
    suspend fun getLifetimeStats(): LifetimeStats {
        val aggregate = focusSessionDao.getLifetimeStatsAggregate(Instant.now().toString())
            ?: LifetimeStatsRow(0, 0, 0, 0.0, 0, null)
        return LifetimeStats(
            completedTasks = aggregate.completedTasks,
            totalSessions = aggregate.totalSessions,
            cleanSessions = aggregate.cleanSessions,
            totalFocusMinutes = (aggregate.totalFocusMinutes * 100.0).toInt() / 100.0,
            totalOverrideAttempts = aggregate.totalOverrideAttempts,
            currentStreakDays = computeStreak(),
            lastSessionAt = aggregate.lastSessionAt,
        )
    }

    private suspend fun computeStreak(): Int {
        val rows = dailyCompletionDao.getRecentCompletionsDesc()
        var streak = 0
        var checkDate = LocalDate.now(ZoneId.systemDefault())
        for (row in rows) {
            val rowDate = runCatching { LocalDate.parse(row.date) }.getOrNull() ?: continue
            val gapDays = ChronoUnit.DAYS.between(rowDate, checkDate)
            if (gapDays > 1) break
            if (row.total > 0 && row.completed.toDouble() / row.total >= 0.5) {
                streak += 1
                checkDate = rowDate
            } else {
                break
            }
        }
        return streak
    }

    // ─── Entity → Domain mapper ───────────────────────────────────────────────

    private fun FocusSessionEntity.toDomain(): FocusSession = FocusSession(
        taskId          = taskId,
        startedAt       = startedAt,
        isActive        = isActive,
        allowedPackages = runCatching {
            json.decodeFromString<List<String>>(allowedPackages)
        }.getOrDefault(emptyList()),
    )
}
