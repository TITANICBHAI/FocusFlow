package com.tbtechs.focusflow.analytics

import com.tbtechs.focusflow.data.local.dao.AppUsageRangeRow
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads event-backed detector history for dates without a complete persisted
 * rollup. Only fully covered, completed local dates are returned.
 */
internal class OnDemandDetectorUsageReader(
    private val eventSource: UsageEventsSource,
    packageName: String,
    private val resolveAppName: (String) -> String,
    private val resolveCategory: (String) -> String?,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
) {
    private val tracker = ForegroundSpanTracker(
        excludedPackages = ForegroundSpanTracker.DEFAULT_EXCLUDED_PACKAGES + packageName,
    )

    suspend fun read(
        startDate: String,
        endDate: String,
        nowMs: Long = System.currentTimeMillis(),
    ): OnDemandDetectorUsage? {
        val zone = zoneId()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val start = LocalDate.parse(startDate)
        val end = minOf(LocalDate.parse(endDate), today.minusDays(1))
        if (start > end) return null

        val rangeStartMs = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val rangeEndMs = minOf(
            end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            nowMs,
        )
        if (rangeStartMs >= rangeEndMs) return null

        val read = eventSource.readForegroundEvents(
            startMs = rangeStartMs - SESSION_LOOKBACK_MS,
            endMs = rangeEndMs,
        ) as? UsageEventRead.Available ?: return null
        if (read.events.isEmpty() && read.earliestEventAtMs == null) return null

        val sessions = tracker.sessions(
            events = read.events,
            windowStartMs = rangeStartMs,
            windowEndMs = rangeEndMs,
            nowMs = nowMs,
        )
        val completeDates = mutableSetOf<String>()
        var date = start
        while (!date.isAfter(end)) {
            val dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEndMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            if (dayEndMs <= nowMs) {
                val unresolvedSession = sessions.any { session ->
                    session.isInferredTail &&
                        Instant.ofEpochMilli(session.startedAtMs).atZone(zone).toLocalDate() == date &&
                        nowMs < session.startedAtMs + ForegroundSpanTracker.DEFAULT_INFERRED_TAIL_CAP_MS
                }
                if (
                    UsageRollupCoverage.status(
                        earliestEventAtMs = read.earliestEventAtMs,
                        dayStartMs = dayStartMs,
                        unresolvedSessionStartedOnDate = unresolvedSession,
                    ) == com.tbtechs.focusflow.data.local.entity.UsageRollupDayEntity.COMPLETE
                ) {
                    completeDates += date.toString()
                }
            }
            date = date.plusDays(1)
        }

        if (completeDates.isEmpty()) {
            return OnDemandDetectorUsage(emptySet(), emptyList(), emptyList())
        }

        val appDays = UsageCalendarAggregator.aggregate(
            sessions = sessions,
            rangeStartMs = rangeStartMs,
            rangeEndMs = rangeEndMs,
            zoneId = zone,
        ).filter { it.date in completeDates }
            .map { row ->
                AppUsageRangeRow(
                    packageName = row.packageName,
                    appName = resolveAppName(row.packageName),
                    category = resolveCategory(row.packageName),
                    date = row.date,
                    foregroundMs = row.foregroundMs,
                    hourlyMs = row.hourlyMs.joinToString(","),
                    launchCount = row.launchCount,
                    lastUsedAt = row.lastUsedAtMs,
                )
            }

        val sessionRows = sessions.asSequence()
            .filter { session ->
                Instant.ofEpochMilli(session.startedAtMs).atZone(zone).toLocalDate().toString() in
                    completeDates
            }
            .filterNot { session ->
                session.isInferredTail &&
                    nowMs < session.startedAtMs + ForegroundSpanTracker.DEFAULT_INFERRED_TAIL_CAP_MS
            }
            .map { session ->
                AppSessionEntity(
                    packageName = session.packageName,
                    appName = resolveAppName(session.packageName),
                    startedAt = session.startedAtMs,
                    endedAt = session.endedAtMs,
                    durationMs = session.durationMs,
                    localDate = Instant.ofEpochMilli(session.startedAtMs)
                        .atZone(zone).toLocalDate().toString(),
                )
            }
            .toList()

        return OnDemandDetectorUsage(completeDates, appDays, sessionRows)
    }

    private companion object {
        const val SESSION_LOOKBACK_MS = 6L * 60L * 60L * 1_000L
    }
}
