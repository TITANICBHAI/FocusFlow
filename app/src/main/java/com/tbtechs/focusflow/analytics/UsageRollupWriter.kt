package com.tbtechs.focusflow.analytics

import android.content.Context
import androidx.room.withTransaction
import com.tbtechs.focusflow.data.local.FocusFlowDatabase
import com.tbtechs.focusflow.data.local.entity.UsageRollupAppDayEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupDayEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupSessionEntity
import com.tbtechs.focusflow.data.local.entity.UsagePipelineStateEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed interface UsageRollupPass {
    data class Written(
        val completeDates: List<String>,
        val partialDates: List<String>,
    ) : UsageRollupPass

    data class Unknown(val reason: UsageEventRead.Unknown.Reason) : UsageRollupPass
}

/**
 * Builds additive Room rollups from one bounded UsageEvents read. This writer
 * never writes today's date and never modifies the legacy usage tables.
 */
class UsageRollupWriter(
    private val context: Context,
    private val database: FocusFlowDatabase,
    private val eventSource: UsageEventsSource,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = clock.zone,
) {
    private val mutex = Mutex()
    private val tracker = ForegroundSpanTracker(
        excludedPackages = ForegroundSpanTracker.DEFAULT_EXCLUDED_PACKAGES + context.packageName,
    )

    suspend fun writeRecentPastDays(nowMs: Long = clock.millis()): UsageRollupPass =
        mutex.withLock {
            val zone = zoneId
            val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            val firstDate = today.minusDays(ROLLUP_LOOKBACK_DAYS)
            val rangeStartMs = firstDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val queryStartMs = rangeStartMs - SESSION_LOOKBACK_MS
            val read = eventSource.readForegroundEvents(queryStartMs, nowMs)
            if (read is UsageEventRead.Unknown) {
                return@withLock UsageRollupPass.Unknown(read.reason)
            }
            read as UsageEventRead.Available

            val sessions = tracker.sessions(
                events = read.events,
                windowStartMs = queryStartMs,
                windowEndMs = nowMs,
                nowMs = nowMs,
            )
            val completeDates = mutableListOf<String>()
            val partialDates = mutableListOf<String>()

            (0 until ROLLUP_LOOKBACK_DAYS).map { today.minusDays(it + 1L) }
                .asReversed()
                .forEach { date ->
                    val dateText = date.toString()
                    val dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
                    val dayEndMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    val daySessions = sessions.filter {
                        it.startedAtMs < dayEndMs && it.endedAtMs > dayStartMs
                    }
                    val unresolvedSessionOnDate = daySessions.any { session ->
                        session.isInferredTail &&
                            Instant.ofEpochMilli(session.startedAtMs).atZone(zone).toLocalDate() == date &&
                            nowMs < session.startedAtMs + ForegroundSpanTracker.DEFAULT_INFERRED_TAIL_CAP_MS
                    }
                    val hasStartCoverage = read.earliestEventAtMs?.let { it <= dayStartMs } == true
                    val status = UsageRollupCoverage.status(
                        earliestEventAtMs = read.earliestEventAtMs,
                        dayStartMs = dayStartMs,
                        unresolvedSessionStartedOnDate = unresolvedSessionOnDate,
                    )
                    val appDays = UsageCalendarAggregator.aggregate(
                        sessions = daySessions,
                        rangeStartMs = dayStartMs,
                        rangeEndMs = dayEndMs,
                        zoneId = zone,
                    )
                    val appRows = appDays.map { row ->
                        UsageRollupAppDayEntity(
                            date = row.date,
                            packageName = row.packageName,
                            appName = UsageAppMetadata.resolveAppName(context, row.packageName),
                            category = UsageAppMetadata.resolveCategory(context, row.packageName),
                            foregroundMs = row.foregroundMs,
                            hourlyMs = row.hourlyMs.joinToString(","),
                            launchCount = row.launchCount,
                            sessionCount = row.sessionCount,
                            firstStartAtMs = row.firstStartAtMs,
                            lastUsedAtMs = row.lastUsedAtMs,
                        )
                    }
                    val sessionRows = daySessions
                        .filter { session ->
                            Instant.ofEpochMilli(session.startedAtMs).atZone(zone).toLocalDate() == date &&
                                !(session.isInferredTail &&
                                    nowMs < session.startedAtMs +
                                    ForegroundSpanTracker.DEFAULT_INFERRED_TAIL_CAP_MS)
                        }
                        .map { session ->
                            UsageRollupSessionEntity(
                                packageName = session.packageName,
                                startedAtMs = session.startedAtMs,
                                endedAtMs = session.endedAtMs,
                                durationMs = session.durationMs,
                                localDate = dateText,
                            )
                        }
                    val dayRow = UsageRollupDayEntity(
                        date = dateText,
                        status = status,
                        coverageStartMs = read.earliestEventAtMs,
                        coverageEndMs = nowMs,
                        pipelineVersion = 1,
                        computedAtMs = nowMs,
                        totalForegroundMs = appRows.sumOf { it.foregroundMs },
                    )
                    val saved = replaceDateIfNeeded(
                        date = dateText,
                        day = dayRow,
                        apps = appRows,
                        sessions = sessionRows,
                    )
                    if (saved) {
                        if (status == UsageRollupDayEntity.COMPLETE) {
                            completeDates += dateText
                        } else {
                            partialDates += dateText
                        }
                    }
                }
            UsageRollupPass.Written(completeDates, partialDates)
        }

    /**
     * Persists an already-read on-demand window without issuing a second
     * UsageEvents query. Today's date is always excluded.
     */
    suspend fun writeDaysFromEvents(
        startDate: String,
        endDate: String,
        sessions: List<ForegroundSession>,
        earliestEventAtMs: Long?,
        nowMs: Long,
    ): Set<String> = mutex.withLock {
        val zone = zoneId
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val start = LocalDate.parse(startDate)
        val end = minOf(LocalDate.parse(endDate), today.minusDays(1))
        if (start > end) return@withLock emptySet()

        val savedDates = mutableSetOf<String>()
        var date = start
        while (!date.isAfter(end)) {
            val dateText = date.toString()
            val dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEndMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val daySessions = sessions.filter {
                it.startedAtMs < dayEndMs && it.endedAtMs > dayStartMs
            }
            val unresolvedSessionOnDate = daySessions.any { session ->
                session.isInferredTail &&
                    Instant.ofEpochMilli(session.startedAtMs).atZone(zone).toLocalDate() == date &&
                    nowMs < session.startedAtMs + ForegroundSpanTracker.DEFAULT_INFERRED_TAIL_CAP_MS
            }
            val status = UsageRollupCoverage.status(
                earliestEventAtMs = earliestEventAtMs,
                dayStartMs = dayStartMs,
                unresolvedSessionStartedOnDate = unresolvedSessionOnDate,
            )
            val appRows = UsageCalendarAggregator.aggregate(
                sessions = daySessions,
                rangeStartMs = dayStartMs,
                rangeEndMs = dayEndMs,
                zoneId = zone,
            ).map { row ->
                UsageRollupAppDayEntity(
                    date = row.date,
                    packageName = row.packageName,
                    appName = UsageAppMetadata.resolveAppName(context, row.packageName),
                    category = UsageAppMetadata.resolveCategory(context, row.packageName),
                    foregroundMs = row.foregroundMs,
                    hourlyMs = row.hourlyMs.joinToString(","),
                    launchCount = row.launchCount,
                    sessionCount = row.sessionCount,
                    firstStartAtMs = row.firstStartAtMs,
                    lastUsedAtMs = row.lastUsedAtMs,
                )
            }
            val sessionRows = daySessions
                .filter { session ->
                    Instant.ofEpochMilli(session.startedAtMs).atZone(zone).toLocalDate() == date &&
                        !(session.isInferredTail &&
                            nowMs < session.startedAtMs +
                            ForegroundSpanTracker.DEFAULT_INFERRED_TAIL_CAP_MS)
                }
                .map { session ->
                    UsageRollupSessionEntity(
                        packageName = session.packageName,
                        startedAtMs = session.startedAtMs,
                        endedAtMs = session.endedAtMs,
                        durationMs = session.durationMs,
                        localDate = dateText,
                    )
                }
            val dayRow = UsageRollupDayEntity(
                date = dateText,
                status = status,
                coverageStartMs = earliestEventAtMs,
                coverageEndMs = nowMs,
                pipelineVersion = UsagePipelineStateEntity.CURRENT_PIPELINE_VERSION,
                computedAtMs = nowMs,
                totalForegroundMs = appRows.sumOf { it.foregroundMs },
            )
            if (replaceDateIfNeeded(dateText, dayRow, appRows, sessionRows)) {
                savedDates += dateText
            }
            date = date.plusDays(1)
        }
        savedDates
    }

    private suspend fun replaceDateIfNeeded(
        date: String,
        day: UsageRollupDayEntity,
        apps: List<UsageRollupAppDayEntity>,
        sessions: List<UsageRollupSessionEntity>,
    ): Boolean = database.withTransaction {
        val dao = database.usageRollupDao()
        val oldDay = dao.getDay(date)
        if (
            oldDay?.status == UsageRollupDayEntity.COMPLETE &&
            oldDay.pipelineVersion == day.pipelineVersion
        ) {
            return@withTransaction false
        }
        dao.deleteAppDays(date)
        dao.deleteSessions(date)
        dao.deleteDay(date)
        if (apps.isNotEmpty()) dao.upsertAppDays(apps)
        if (sessions.isNotEmpty()) dao.upsertSessions(sessions)
        dao.upsertDay(day)
        true
    }

    companion object {
        const val ROLLUP_LOOKBACK_DAYS = 3L
        private const val SESSION_LOOKBACK_MS = 6L * 60L * 60L * 1_000L
    }
}
