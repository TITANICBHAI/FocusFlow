package com.tbtechs.focusflow.analytics.phone

import com.tbtechs.focusflow.analytics.ForegroundSession
import com.tbtechs.focusflow.analytics.ForegroundSpanTracker
import com.tbtechs.focusflow.analytics.UsageEventRead
import com.tbtechs.focusflow.analytics.UsageEventsSource
import com.tbtechs.focusflow.analytics.UsageRollupCoverage
import com.tbtechs.focusflow.data.local.entity.PhoneDayRollupEntity
import com.tbtechs.focusflow.data.local.entity.PhoneFirstAppDayEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupDayEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed interface PhoneRollupPass {
    data class Written(val dates: List<String>) : PhoneRollupPass
    data class Unknown(val reason: UsageEventRead.Unknown.Reason) : PhoneRollupPass
}

/**
 * Persists one [PhoneDay] per past local day, backfilling whatever history the OS still holds.
 * Never writes today. Reads OS events only when some past day is actually missing or stale.
 */
class PhoneBehaviourRollupWriter(
    private val store: PhoneBehaviourStore,
    private val eventSource: UsageEventsSource,
    private val excludedPackages: Set<String>,
    private val appNameResolver: (String) -> String,
    /** Keeps the existing per-app rollups in step; AppModule passes usageRollupWriter::writeDaysFromEvents. Null in tests. */
    private val appRollupHook: (suspend (String, String, List<ForegroundSession>, Long?, Long) -> Unit)? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val mutex = Mutex()

    /** In-memory: no event at or before this time exists in the OS log, so days ending before it can never be filled. */
    private var knownLowerBoundMs: Long? = null

    suspend fun writeMissingPastDays(nowMs: Long = clock.millis()): PhoneRollupPass = mutex.withLock {
        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        val retentionCutoff = today.minusDays(PhoneThresholds.DAY_ROW_RETENTION_DAYS.toLong()).toString()
        val allDates = (PhoneThresholds.BACKFILL_DAYS downTo 1).map { today.minusDays(it.toLong()) }
        val existing = store.phoneDays(allDates.first().toString(), allDates.last().toString())
            .associateBy { it.date }

        val lowerBound = knownLowerBoundMs
        val candidates = allDates.filter { date ->
            (lowerBound == null || dayEndMs(date) > lowerBound) && needsWork(existing[date.toString()], date, today, nowMs)
        }
        if (candidates.isEmpty()) {
            store.prune(retentionCutoff)
            return@withLock PhoneRollupPass.Written(emptyList())
        }

        val firstCandidate = candidates.first()
        val readStartMs = dayStartMs(firstCandidate) - PhoneThresholds.EVENT_LOOKBACK_MS
        val read = eventSource.readForegroundEvents(readStartMs, nowMs)
        if (read is UsageEventRead.Unknown) return@withLock PhoneRollupPass.Unknown(read.reason)
        read as UsageEventRead.Available

        val earliest = read.earliestEventAtMs
        knownLowerBoundMs = earliest ?: nowMs

        val sessions = ForegroundSpanTracker(excludedPackages = excludedPackages)
            .sessions(read.events, readStartMs, nowMs, nowMs)
        val reducer = PhoneEventReducer(zoneId)
        val written = mutableListOf<String>()

        for (date in candidates) {
            val dayStart = dayStartMs(date)
            val dayEnd = dayEndMs(date)
            if (earliest == null || earliest >= dayEnd) continue // never observed: stays MISSING

            val unresolved = sessions.any {
                it.startedAtMs < dayEnd && it.endedAtMs > dayStart &&
                    it.isInferredTail &&
                    Instant.ofEpochMilli(it.startedAtMs).atZone(zoneId).toLocalDate() == date &&
                    nowMs < it.startedAtMs + ForegroundSpanTracker.DEFAULT_INFERRED_TAIL_CAP_MS
            }
            val coverage = UsageRollupCoverage.status(earliest, dayStart, unresolved)
            val status =
                if (coverage == UsageRollupDayEntity.COMPLETE) PhoneDayStatus.COMPLETE else PhoneDayStatus.PARTIAL
            val day = reducer.reduceDay(date, read.events, sessions, nowMs, status)
            store.replacePhoneDay(
                day.toEntity(nowMs, earliest),
                day.firstApps.map { (pkg, n) -> PhoneFirstAppDayEntity(date.toString(), pkg, appNameResolver(pkg), n) },
            )
            written += date.toString()
        }

        appRollupHook?.let { hook ->
            try {
                hook(firstCandidate.toString(), today.minusDays(1).toString(), sessions, earliest, nowMs)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The per-app rollups are best effort; phone rows are already saved.
            }
        }

        store.prune(retentionCutoff)
        PhoneRollupPass.Written(written)
    }

    private fun needsWork(row: PhoneDayRollupEntity?, date: LocalDate, today: LocalDate, nowMs: Long): Boolean {
        if (row == null) return true
        if (row.behaviourVersion != PhoneThresholds.BEHAVIOUR_VERSION) return true
        if (row.status != PhoneDayRollupEntity.PARTIAL) return false
        return !date.isBefore(today.minusDays(2)) || nowMs - row.computedAtMs > PARTIAL_RECOMPUTE_AFTER_MS
    }

    private fun dayStartMs(date: LocalDate): Long = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
    private fun dayEndMs(date: LocalDate): Long = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

    private companion object {
        const val PARTIAL_RECOMPUTE_AFTER_MS = 24L * 60 * 60 * 1000
    }
}
