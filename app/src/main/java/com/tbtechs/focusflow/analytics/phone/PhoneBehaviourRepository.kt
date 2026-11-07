package com.tbtechs.focusflow.analytics.phone

import com.tbtechs.focusflow.analytics.ANALYTICS_ALL_TIME
import com.tbtechs.focusflow.analytics.ANALYTICS_THREE_MONTHS
import com.tbtechs.focusflow.analytics.ANALYTICS_WEEK
import com.tbtechs.focusflow.analytics.ForegroundSpanTracker
import com.tbtechs.focusflow.analytics.UsageCalendarAggregator
import com.tbtechs.focusflow.analytics.UsageEventRead
import com.tbtechs.focusflow.analytics.UsageEventsSource
import com.tbtechs.focusflow.analytics.getAnalyticsRange
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsightInput
import com.tbtechs.focusflow.analytics.phone.insights.PhoneInsightInputResult
import com.tbtechs.focusflow.data.local.dao.SessionOverrideCountRow
import com.tbtechs.focusflow.data.local.entity.PhoneFirstAppDayEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** What the ViewModels depend on, so their tests can use a trivial fake. */
interface PhoneReportSource {
    suspend fun loadReport(window: String, weekMode: String, weekStartDay: Int): PhoneReportResult
    suspend fun loadInsightInput(): PhoneInsightInputResult
}

/**
 * Assembles stored rollups plus today's live data into a [PhoneReport] (or the input for the Insights engine).
 * Persists past days first, so the report never depends on a separate background job having run.
 */
class PhoneBehaviourRepository(
    private val store: PhoneBehaviourStore,
    private val eventSource: UsageEventsSource,
    private val writer: PhoneBehaviourRollupWriter,
    private val blockedSync: BlockedAttemptSync,
    private val hasPermission: suspend () -> Boolean,
    private val categoryOf: (String) -> UsageCategory,
    private val appNameResolver: (String) -> String,
    private val excludedPackages: Set<String>,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PhoneReportSource {

    override suspend fun loadReport(window: String, weekMode: String, weekStartDay: Int): PhoneReportResult =
        try {
            withContext(dispatcher) { buildReport(window, weekMode, weekStartDay) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PhoneReportResult.Failed(e.message ?: "Could not load phone usage")
        }

    override suspend fun loadInsightInput(): PhoneInsightInputResult =
        try {
            withContext(dispatcher) { buildInsightInput() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PhoneInsightInputResult.Failed(e.message ?: "Could not load phone usage")
        }

    // ---------------------------------------------------------------------------------------------- report

    private suspend fun buildReport(window: String, weekMode: String, weekStartDay: Int): PhoneReportResult {
        if (!hasPermission()) return PhoneReportResult.PermissionNeeded
        val nowMs = clock.millis()
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zoneId)
        val today = now.toLocalDate()
        if (!persist(nowMs)) return PhoneReportResult.PermissionNeeded

        val earliest = store.earliestDate()?.let(LocalDate::parse)
        val range = resolveRange(window, weekMode, weekStartDay, now, earliest)

        val loaded = load(range, today, nowMs)
        val blockedRows = store.blockedDays(range.start.toString(), range.end.toString()).map {
            BlockedDayRow(it.date, it.packageName, it.appName, it.attempts, PhoneCodec.decodeInts(it.hourlyAttempts, 24))
        }
        val focusRows = focusSessions(range.start, range.end)
        val trendStart = today.minusDays(TREND_DAYS - 1L)
        val trendFocus = focusSessions(trendStart, today)
        val trendBlocked = store.blockedDays(trendStart.toString(), today.toString()).map {
            BlockedDayRow(it.date, it.packageName, it.appName, it.attempts, PhoneCodec.decodeInts(it.hourlyAttempts, 24))
        }

        val previous = if ((window == ANALYTICS_WEEK || window == ANALYTICS_THREE_MONTHS) && range.dayCount >= 7) {
            val prevEnd = range.start.minusDays(1)
            val prevStart = prevEnd.minusDays(range.dayCount - 1L)
            val rows = store.appDays(prevStart.toString(), prevEnd.toString())
            PreviousAppPeriod(
                daysWithData = rows.filter { it.foregroundMs > 0 }.map { it.date }.toSet().size,
                appMsByPackage = rows.groupBy { it.packageName }.mapValues { (_, v) -> v.sumOf { it.foregroundMs } },
            )
        } else null

        val names = LinkedHashMap<String, String>()
        loaded.appDays.forEach { if (it.appName.isNotBlank()) names.putIfAbsent(it.packageName, it.appName) }
        loaded.firstApps.forEach { if (it.appName.isNotBlank()) names.putIfAbsent(it.packageName, it.appName) }
        blockedRows.forEach { if (it.appName.isNotBlank()) names.putIfAbsent(it.packageName, it.appName) }

        if (loaded.phoneDays.isEmpty() && loaded.appDays.isEmpty() && blockedRows.isEmpty() && focusRows.isEmpty()) {
            return PhoneReportResult.NoData
        }

        val screenTime = ScreenTimeBuilder.build(range, loaded.phoneDays, loaded.appDays)
        val behaviour = PhoneBehaviourBuilder.build(range, loaded.phoneDays, names, zoneId)
        val firstAppUnlocks = HashMap<String, Int>()
        for (day in loaded.phoneDays) for ((pkg, n) in day.firstApps) firstAppUnlocks[pkg] = (firstAppUnlocks[pkg] ?: 0) + n
        val apps = AppsBuilder.build(
            AppsInput(range, loaded.appDays, loaded.appSessions, firstAppUnlocks, previous, categoryOf),
        )
        val blockedByPackage = blockedRows.groupBy { it.packageName }.mapValues { (_, v) -> v.sumOf { it.attempts } }
        val distraction = DistractionBuilder.build(apps, loaded.appDays, blockedByPackage, categoryOf, previous)
        val focus = FocusBlockingBuilder.build(range, today, nowMs, zoneId, focusRows, blockedRows, trendFocus, trendBlocked)

        val days = screenTime.days
        val coverage = PhoneCoverage(
            completeDays = days.count { it.coverage == DayCoverage.COMPLETE },
            partialDays = days.count { it.coverage == DayCoverage.PARTIAL },
            liveDays = days.count { it.coverage == DayCoverage.LIVE },
            missingDays = days.count { it.coverage == DayCoverage.MISSING },
            phoneDataSince = loaded.phoneDays.minOfOrNull { it.date }?.let(LocalDate::parse),
            appDataSince = loaded.appDays.filter { it.foregroundMs > 0 }.minOfOrNull { it.date }?.let(LocalDate::parse),
        )
        return PhoneReportResult.Ready(PhoneReport(window, range, screenTime, apps, behaviour, distraction, focus, coverage))
    }

    // ---------------------------------------------------------------------------------------------- insights

    private suspend fun buildInsightInput(): PhoneInsightInputResult {
        if (!hasPermission()) return PhoneInsightInputResult.PermissionNeeded
        val nowMs = clock.millis()
        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        if (!persist(nowMs)) return PhoneInsightInputResult.PermissionNeeded

        val range = PhoneDateRange(today.minusDays(INSIGHT_DAYS - 1L), today)
        val loaded = load(range, today, nowMs)
        val completeDays = loaded.phoneDays.count { it.status == PhoneDayStatus.COMPLETE }
        if (completeDays < MIN_INSIGHT_DAYS) return PhoneInsightInputResult.Building(completeDays)

        val blockedRows = store.blockedDays(range.start.toString(), range.end.toString()).map {
            BlockedDayRow(it.date, it.packageName, it.appName, it.attempts, PhoneCodec.decodeInts(it.hourlyAttempts, 24))
        }
        val names = LinkedHashMap<String, String>()
        loaded.appDays.forEach { if (it.appName.isNotBlank()) names.putIfAbsent(it.packageName, it.appName) }
        loaded.firstApps.forEach { if (it.appName.isNotBlank()) names.putIfAbsent(it.packageName, it.appName) }
        blockedRows.forEach { if (it.appName.isNotBlank()) names.putIfAbsent(it.packageName, it.appName) }

        return PhoneInsightInputResult.Ready(
            PhoneInsightInput(
                today = today,
                days = loaded.phoneDays,
                appDays = loaded.appDays,
                appSessions = loaded.appSessions,
                blockedDays = blockedRows,
                focusSessions = focusSessions(range.start, range.end),
                appNames = names,
                categoryOf = categoryOf,
                zone = zoneId,
            ),
        )
    }

    // ---------------------------------------------------------------------------------------------- shared

    /** Persists past days and blocked counts. Returns false when usage access is gone. Other failures are swallowed. */
    private suspend fun persist(nowMs: Long): Boolean {
        try {
            val pass = writer.writeMissingPastDays(nowMs)
            if (pass is PhoneRollupPass.Unknown &&
                (pass.reason == UsageEventRead.Unknown.Reason.PERMISSION_MISSING ||
                    pass.reason == UsageEventRead.Unknown.Reason.ACCESS_REVOKED)
            ) return false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        try {
            blockedSync.sync()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        return true
    }

    private class Loaded(
        val phoneDays: List<PhoneDay>,
        val appDays: List<AppDayRow>,
        val appSessions: List<AppSessionRow>,
        val firstApps: List<PhoneFirstAppDayEntity>,
    )

    /** Stored past days of [range] plus today's live data when the range ends today. */
    private suspend fun load(range: PhoneDateRange, today: LocalDate, nowMs: Long): Loaded {
        val phoneDays = ArrayList<PhoneDay>()
        val appDays = ArrayList<AppDayRow>()
        val appSessions = ArrayList<AppSessionRow>()
        var firstApps: List<PhoneFirstAppDayEntity> = emptyList()

        val pastEnd = minOf(range.end, today.minusDays(1))
        if (!range.start.isAfter(pastEnd)) {
            val s = range.start.toString()
            val e = pastEnd.toString()
            firstApps = store.firstApps(s, e)
            val byDate = firstApps.groupBy { it.date }.mapValues { (_, rows) -> rows.associate { it.packageName to it.unlockCount } }
            store.phoneDays(s, e).mapTo(phoneDays) { it.toPhoneDay(byDate[it.date].orEmpty()) }
            store.appDays(s, e).mapTo(appDays) {
                AppDayRow(it.date, it.packageName, it.appName, it.foregroundMs, PhoneCodec.decodeLongs(it.hourlyMs, 24), it.sessionCount)
            }
            store.appSessions(s, e).mapTo(appSessions) { AppSessionRow(it.packageName, it.localDate, it.startedAtMs, it.durationMs) }
        }

        if (range.end == today) {
            val dayStart = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val readStart = dayStart - PhoneThresholds.EVENT_LOOKBACK_MS
            val read = eventSource.readForegroundEvents(readStart, nowMs)
            if (read is UsageEventRead.Available && read.events.isNotEmpty()) {
                val sessions = ForegroundSpanTracker(excludedPackages = excludedPackages)
                    .sessions(read.events, readStart, nowMs, nowMs)
                phoneDays += PhoneEventReducer(zoneId).reduceDay(today, read.events, sessions, nowMs, PhoneDayStatus.LIVE)
                UsageCalendarAggregator.aggregate(sessions, dayStart, nowMs, zoneId).mapTo(appDays) {
                    AppDayRow(it.date, it.packageName, appNameResolver(it.packageName), it.foregroundMs, it.hourlyMs, it.sessionCount)
                }
                sessions.filter { it.startedAtMs >= dayStart }.mapTo(appSessions) {
                    AppSessionRow(it.packageName, today.toString(), it.startedAtMs, minOf(it.endedAtMs, nowMs) - it.startedAtMs)
                }
            }
        }
        return Loaded(phoneDays, appDays, appSessions, firstApps)
    }

    private suspend fun focusSessions(start: LocalDate, end: LocalDate): List<FocusSessionRow> {
        val startIso = start.atStartOfDay(zoneId).toInstant().toString()
        val endIso = end.plusDays(1).atStartOfDay(zoneId).toInstant().toString()
        return store.focusSessions(startIso, endIso).mapNotNull { it.toFocusRow() }
    }

    private fun SessionOverrideCountRow.toFocusRow(): FocusSessionRow? {
        val started = runCatching { Instant.parse(startedAt) }.getOrNull() ?: return null
        val ended = if (endedAt == null) null else (runCatching { Instant.parse(endedAt) }.getOrNull() ?: return null)
        return FocusSessionRow(
            date = started.atZone(zoneId).toLocalDate().toString(),
            startedAtMs = started.toEpochMilli(),
            endedAtMs = ended?.toEpochMilli(),
            overrideCount = overrideCount,
        )
    }

    private companion object {
        const val TREND_DAYS = 56
        const val INSIGHT_DAYS = 90
        const val MIN_INSIGHT_DAYS = 3
    }
}

/** Maps a window id to the inclusive local date range the phone report uses. Never extends past today. */
internal fun resolveRange(
    window: String, weekMode: String, weekStartDay: Int, now: ZonedDateTime, earliest: LocalDate?,
): PhoneDateRange {
    val r = getAnalyticsRange(window, now, weekStartDay, weekMode)
    val today = now.toLocalDate()
    val end = minOf(r.end.toLocalDate(), today)
    var start = if (window == ANALYTICS_ALL_TIME) (earliest ?: today) else r.start.toLocalDate()
    if (start.isAfter(end)) start = end
    return PhoneDateRange(start, end)
}
