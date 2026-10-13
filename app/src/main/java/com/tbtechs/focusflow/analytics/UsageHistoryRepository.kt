package com.tbtechs.focusflow.analytics

import android.content.Context
import com.tbtechs.focusflow.data.local.dao.AppSessionDao
import com.tbtechs.focusflow.data.local.dao.AppUsageRangeRow
import com.tbtechs.focusflow.data.local.dao.DailyAppUsageDao
import com.tbtechs.focusflow.data.local.dao.UsageRollupDao
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity
import com.tbtechs.focusflow.data.local.entity.UsagePipelineStateEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupAppDayEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupDayEntity
import com.tbtechs.focusflow.data.local.entity.UsageRollupSessionEntity
import java.time.LocalDate
import java.time.ZoneId

data class UsageHistoryAppDay(
    val date: String,
    val packageName: String,
    val appName: String,
    val category: String?,
    val foregroundMs: Long,
    val hourlyMs: String,
    val launchCount: Int,
    val lastUsedAtMs: Long,
)

data class DetectorUsageHistory(
    val appDays: List<AppUsageRangeRow>,
    val sessions: List<AppSessionEntity>,
)

data class DeviceStatsRollupDay(
    val status: String,
    val appDays: List<UsageHistoryAppDay>,
)

data class OnDemandDetectorUsage(
    val completeDates: Set<String>,
    val appDays: List<AppUsageRangeRow>,
    val sessions: List<AppSessionEntity>,
)

/**
 * Central store boundary for legacy and pipeline history. Default pipeline
 * methods keep pure legacy test stores small; Room supplies the rollup reads.
 */
interface UsageHistoryStore {
    suspend fun legacyAppDays(startDate: String, endDate: String): List<UsageHistoryAppDay>
    suspend fun legacySessions(startDate: String, endDate: String): List<AppSessionEntity>
    suspend fun pipelineState(): UsagePipelineStateEntity? = null
    suspend fun ensurePipelineState(shadowStartedOn: String) = Unit
    suspend fun rollupDays(startDate: String, endDate: String): List<UsageRollupDayEntity> = emptyList()
    suspend fun rollupAppDays(date: String): List<UsageRollupAppDayEntity> = emptyList()
    suspend fun rollupAppDays(
        startDate: String,
        endDate: String,
    ): List<UsageRollupAppDayEntity> =
        rollupDays(startDate, endDate).flatMap { rollupAppDays(it.date) }
    suspend fun rollupSessions(startDate: String, endDate: String): List<UsageRollupSessionEntity> =
        emptyList()
    suspend fun onDemandDetectorUsage(
        startDate: String,
        endDate: String,
    ): OnDemandDetectorUsage? = null
    suspend fun liveHasSessionToday(date: String): Boolean = false
    suspend fun onDemandUsageDates(startDate: String, endDate: String): Set<String> = emptySet()
    suspend fun setCutoverDateIfShadowMature(
        cutoverDate: String,
        shadowStartedBy: String,
    ): Boolean = false
}

class UsageHistoryRepository(
    private val store: UsageHistoryStore,
) {
    suspend fun shouldWriteLegacy(today: String): Boolean =
        UsageHistorySourcePolicy.shouldWriteLegacy(cutoverDate = cutoverDate(today), today = today)

    suspend fun appDays(startDate: String, endDate: String): List<UsageHistoryAppDay> =
        store.legacyAppDays(startDate, endDate)

    suspend fun sessions(startDate: String, endDate: String): List<AppSessionEntity> =
        store.legacySessions(startDate, endDate)

    suspend fun deviceStatsRollups(
        startDate: String,
        endDate: String,
    ): Map<String, DeviceStatsRollupDay> {
        val days = store.rollupDays(startDate, endDate)
        val appDaysByDate = if (days.isEmpty()) {
            emptyMap()
        } else {
            store.rollupAppDays(startDate, endDate).groupBy { it.date }
        }
        return days.associate { day ->
            day.date to DeviceStatsRollupDay(
                status = day.status,
                appDays = appDaysByDate[day.date].orEmpty().map { row ->
                    UsageHistoryAppDay(
                        date = row.date,
                        packageName = row.packageName,
                        appName = row.appName,
                        category = row.category,
                        foregroundMs = row.foregroundMs,
                        hourlyMs = row.hourlyMs,
                        launchCount = row.launchCount,
                        lastUsedAtMs = row.lastUsedAtMs,
                    )
                },
            )
        }
    }

    /**
     * Returns one source per date for a detector window. Seam-crossing windows
     * are refused; after cutover COMPLETE rollups win and only fully covered
     * on-demand pipeline dates can fill rollup gaps.
     */
    suspend fun detectorHistory(
        startDate: String,
        endDate: String,
        today: String,
    ): DetectorUsageHistory? {
        val completedEndDate = minOf(endDate, LocalDate.parse(today).minusDays(1).toString())
        if (completedEndDate < startDate) return null
        val cutoverDate = cutoverDate(today)
        if (UsageHistorySourcePolicy.crossesSeam(startDate, completedEndDate, cutoverDate)) return null
        if (cutoverDate == null || completedEndDate < cutoverDate) {
            return DetectorUsageHistory(
                appDays = store.legacyAppDays(startDate, completedEndDate).map { row ->
                    AppUsageRangeRow(
                        packageName = row.packageName,
                        appName = row.appName,
                        category = row.category,
                        date = row.date,
                        foregroundMs = row.foregroundMs,
                        hourlyMs = row.hourlyMs,
                        launchCount = row.launchCount,
                        lastUsedAt = row.lastUsedAtMs,
                    )
                },
                sessions = store.legacySessions(startDate, completedEndDate),
            )
        }
        if (startDate < cutoverDate || startDate >= today) return null

        val completeDates = store.rollupDays(startDate, completedEndDate)
            .filter { it.status == UsageRollupDayEntity.COMPLETE }
            .map { it.date }
            .toSet()
        val onDemand = if (
            generateSequence(LocalDate.parse(startDate)) { it.plusDays(1) }
                .takeWhile { it.toString() <= completedEndDate }
                .any { it.toString() !in completeDates }
        ) {
            store.onDemandDetectorUsage(startDate, completedEndDate)
        } else {
            null
        }
        val onDemandDates = onDemand?.completeDates.orEmpty() - completeDates
        val appDays = store.rollupAppDays(startDate, completedEndDate)
            .filter { it.date in completeDates }
            .map { row ->
                AppUsageRangeRow(
                    packageName = row.packageName,
                    appName = row.appName,
                    category = row.category,
                    date = row.date,
                    foregroundMs = row.foregroundMs,
                    hourlyMs = row.hourlyMs,
                    launchCount = row.launchCount,
                    lastUsedAt = row.lastUsedAtMs,
                )
            } + onDemand?.appDays.orEmpty().filter { it.date in onDemandDates }
        val sessions = store.rollupSessions(startDate, completedEndDate)
            .filter { it.localDate in completeDates }
            .map { row ->
                AppSessionEntity(
                    packageName = row.packageName,
                    appName = "",
                    startedAt = row.startedAtMs,
                    endedAt = row.endedAtMs,
                    durationMs = row.durationMs,
                    localDate = row.localDate,
                )
            } + onDemand?.sessions.orEmpty().filter { it.localDate in onDemandDates }
        val names = appDays.associate { it.packageName to it.appName }
        return DetectorUsageHistory(
            appDays = appDays,
            sessions = sessions.map { session ->
                session.copy(appName = names[session.packageName].orEmpty())
            },
        )
    }

    suspend fun groupBUsageDates(
        startDate: String,
        endDate: String,
        today: String,
        consumer: UsageHistoryConsumer,
    ): Set<String> {
        val cutoverDate = cutoverDate(today)
        val phase = if (cutoverDate == null) {
            UsageHistoryReadPhase.SHADOW
        } else {
            UsageHistoryReadPhase.CUTOVER
        }
        val legacyDates = if (cutoverDate == null || startDate < cutoverDate) {
            val legacyEnd = if (cutoverDate == null) {
                endDate
            } else {
                minOf(endDate, LocalDate.parse(cutoverDate).minusDays(1).toString())
            }
            if (legacyEnd >= startDate) {
                store.legacyAppDays(startDate, legacyEnd).map { it.date }.toSet()
            } else {
                emptySet()
            }
        } else {
            emptySet()
        }
        val rollupStart = cutoverDate?.let { maxOf(startDate, it) }
        val rollupEnd = minOf(endDate, LocalDate.parse(today).minusDays(1).toString())
        val rollupDays = if (rollupStart != null && rollupStart <= rollupEnd) {
            store.rollupDays(rollupStart, rollupEnd).associateBy { it.date }
        } else {
            emptyMap()
        }
        val onDemandDates = if (
            cutoverDate != null &&
            rollupStart != null &&
            rollupStart <= rollupEnd
        ) {
            store.onDemandUsageDates(rollupStart, rollupEnd)
        } else {
            emptySet()
        }
        val liveHasSession = if (today in startDate..endDate && cutoverDate != null) {
            store.liveHasSessionToday(today)
        } else {
            false
        }

        return generateSequence(LocalDate.parse(startDate)) { it.plusDays(1) }
            .takeWhile { it.toString() <= endDate }
            .map { it.toString() }
            .filter { date ->
                when (
                    UsageHistorySourcePolicy.selectHistory(
                        phase = phase,
                        date = date,
                        today = today,
                        cutoverDate = cutoverDate,
                        consumer = consumer,
                        rollupStatus = rollupDays[date]?.status,
                        eventsAvailable = date in onDemandDates,
                        liveHasSession = liveHasSession,
                    )
                ) {
                    UsageHistorySource.LEGACY -> date in legacyDates
                    UsageHistorySource.ROLLUP -> true
                    UsageHistorySource.LIVE_PIPELINE -> true
                    UsageHistorySource.ON_DEMAND_PIPELINE -> date in onDemandDates
                    else -> false
                }
            }
            .toSet()
    }

    fun sourceFor(
        phase: UsageHistoryReadPhase,
        date: String,
        today: String,
        cutoverDate: String?,
        consumer: UsageHistoryConsumer,
        rollupStatus: String?,
        eventsAvailable: Boolean,
        liveHasSession: Boolean = false,
    ): UsageHistorySource = UsageHistorySourcePolicy.selectHistory(
        phase = phase,
        date = date,
        today = today,
        cutoverDate = cutoverDate,
        consumer = consumer,
        rollupStatus = rollupStatus,
        eventsAvailable = eventsAvailable,
        liveHasSession = liveHasSession,
    )

    private suspend fun cutoverDate(today: String): String? {
        store.ensurePipelineState(today)
        val state = store.pipelineState() ?: return null
        state.cutoverDate?.let { return it }
        val todayDate = LocalDate.parse(today)
        val shadowStart = runCatching { LocalDate.parse(state.shadowStartedOn) }.getOrNull()
            ?: return null
        val requiredStart = todayDate.minusDays(CUTOVER_SHADOW_DAYS)
        if (shadowStart.isAfter(requiredStart)) return null
        val requiredDates = (0 until CUTOVER_SHADOW_DAYS)
            .map { requiredStart.plusDays(it).toString() }
            .toSet()
        val completeShadowDates = store.rollupDays(
            startDate = requiredStart.toString(),
            endDate = todayDate.minusDays(1).toString(),
        ).filter {
            it.status == UsageRollupDayEntity.COMPLETE &&
                it.pipelineVersion == UsagePipelineStateEntity.CURRENT_PIPELINE_VERSION
        }.map { it.date }.toSet()
        if (!completeShadowDates.containsAll(requiredDates)) return null
        return if (store.setCutoverDateIfShadowMature(
            cutoverDate = today,
            shadowStartedBy = requiredStart.toString(),
        )) {
            today
        } else {
            null
        }
    }

    private companion object {
        const val CUTOVER_SHADOW_DAYS = 7L
    }
}

class RoomUsageHistoryStore(
    private val context: Context,
    private val dailyAppUsageDao: DailyAppUsageDao,
    private val appSessionDao: AppSessionDao,
    private val usageRollupDao: UsageRollupDao,
    private val eventSource: UsageEventsSource,
    private val packageName: String,
) : UsageHistoryStore {
    private val liveTracker = ForegroundSpanTracker(
        excludedPackages = ForegroundSpanTracker.DEFAULT_EXCLUDED_PACKAGES + packageName,
    )
    private val detectorUsageReader = OnDemandDetectorUsageReader(
        eventSource = eventSource,
        packageName = packageName,
        resolveAppName = { UsageAppMetadata.resolveAppName(context, it) },
        resolveCategory = { UsageAppMetadata.resolveCategory(context, it) },
    )

    override suspend fun legacyAppDays(
        startDate: String,
        endDate: String,
    ): List<UsageHistoryAppDay> = dailyAppUsageDao.getForDateRange(startDate, endDate).map {
        UsageHistoryAppDay(
            date = it.date,
            packageName = it.packageName,
            appName = it.appName,
            category = it.category,
            foregroundMs = it.foregroundMs,
            hourlyMs = it.hourlyMs,
            launchCount = it.launchCount,
            lastUsedAtMs = it.lastUsedAt,
        )
    }

    override suspend fun legacySessions(
        startDate: String,
        endDate: String,
    ): List<AppSessionEntity> = appSessionDao.getAllSessionsInRange(startDate, endDate)

    override suspend fun pipelineState(): UsagePipelineStateEntity? =
        usageRollupDao.getPipelineState()

    override suspend fun ensurePipelineState(shadowStartedOn: String) =
        usageRollupDao.ensurePipelineState(shadowStartedOn)

    override suspend fun rollupDays(
        startDate: String,
        endDate: String,
    ): List<UsageRollupDayEntity> = usageRollupDao.getDays(startDate, endDate)

    override suspend fun rollupAppDays(date: String): List<UsageRollupAppDayEntity> =
        usageRollupDao.getAppDays(date)

    override suspend fun rollupAppDays(
        startDate: String,
        endDate: String,
    ): List<UsageRollupAppDayEntity> = usageRollupDao.getAppDays(startDate, endDate)

    override suspend fun rollupSessions(
        startDate: String,
        endDate: String,
    ): List<UsageRollupSessionEntity> = usageRollupDao.getSessions(startDate, endDate)

    override suspend fun onDemandDetectorUsage(
        startDate: String,
        endDate: String,
    ): OnDemandDetectorUsage? = detectorUsageReader.read(startDate, endDate)

    override suspend fun setCutoverDateIfShadowMature(
        cutoverDate: String,
        shadowStartedBy: String,
    ): Boolean = usageRollupDao.setCutoverDateIfShadowMature(cutoverDate, shadowStartedBy) > 0

    override suspend fun onDemandUsageDates(
        startDate: String,
        endDate: String,
    ): Set<String> {
        val zone = ZoneId.systemDefault()
        val startDateValue = LocalDate.parse(startDate)
        val endDateValue = LocalDate.parse(endDate)
        val rangeStartMs = startDateValue.atStartOfDay(zone).toInstant().toEpochMilli()
        val rangeEndMs = minOf(
            endDateValue.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            System.currentTimeMillis(),
        )
        if (rangeStartMs >= rangeEndMs) return emptySet()

        val nowMs = System.currentTimeMillis()
        val events = eventSource.readForegroundEvents(
            rangeStartMs - LIVE_SESSION_LOOKBACK_MS,
            rangeEndMs,
        ) as? UsageEventRead.Available ?: return emptySet()
        if (events.events.isEmpty() && events.earliestEventAtMs == null) return emptySet()
        val sessions = liveTracker.sessions(
            events = events.events,
            windowStartMs = rangeStartMs,
            windowEndMs = rangeEndMs,
            nowMs = nowMs,
        )
        return UsageCalendarAggregator.aggregate(
            sessions = sessions,
            rangeStartMs = rangeStartMs,
            rangeEndMs = rangeEndMs,
            zoneId = zone,
        ).filter { it.foregroundMs > 0L || it.launchCount > 0 }
            .mapTo(mutableSetOf()) { it.date }
    }

    override suspend fun liveHasSessionToday(date: String): Boolean {
        val zone = ZoneId.systemDefault()
        val localDate = LocalDate.parse(date)
        val dayStart = localDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val nowMs = System.currentTimeMillis()
        val events = eventSource.readForegroundEvents(
            dayStart - 6L * 60L * 60L * 1_000L,
            nowMs,
        )
        if (events !is UsageEventRead.Available) return false
        val sessions = liveTracker.sessions(
            events = events.events,
            windowStartMs = dayStart,
            windowEndMs = nowMs,
            nowMs = nowMs,
        )
        return sessions.any { it.endedAtMs > dayStart && it.startedAtMs < nowMs }
    }

    private companion object {
        const val LIVE_SESSION_LOOKBACK_MS = 6L * 60L * 60L * 1_000L
    }
}
