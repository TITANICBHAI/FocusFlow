package com.tbtechs.focusflow.analytics.detection

import com.tbtechs.focusflow.data.local.dao.AppUsageRangeRow
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity
import com.tbtechs.focusflow.data.local.entity.FindingEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.round

private const val MS_PER_MINUTE = 60_000.0
private const val LATE_NIGHT_START_HOUR = 23
private const val LATE_NIGHT_END_HOUR = 4
private const val LATE_NIGHT_MIN_MINUTES = 10.0
private const val LATE_NIGHT_MIN_NIGHTS = 5
private const val HEAVY_USE_MIN_AVERAGE_MINUTES = 60.0
private const val HEAVY_USE_MIN_DAYS = 10

/**
 * Finds an attention-capture app that keeps being used late at night.
 *
 * A night counts when the app had at least 10 minutes between 23:00 and 04:00 local time.
 * A session belongs to the night it started in (a session at 01:00 belongs to the evening before).
 * Minimum evidence: five such nights inside the window.
 */
fun detectLateNightUse(
    sessions: List<AppSessionEntity>,
    categories: Map<String, String?>,
    windowDays: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): FindingEntity? {
    if (windowDays <= 0) return null

    data class Candidate(
        val packageName: String,
        val appName: String,
        val nights: Int,
        val averageMinutes: Double,
        val totalMinutes: Double,
    )

    val minutesByNight = mutableMapOf<String, MutableMap<LocalDate, Double>>()
    val names = mutableMapOf<String, String>()
    sessions.forEach { session ->
        if (!DetectorEligibility.isEngagementDesigned(session.packageName, categories[session.packageName])) {
            return@forEach
        }
        val start = Instant.ofEpochMilli(session.startedAt).atZone(zone)
        val night = when {
            start.hour >= LATE_NIGHT_START_HOUR -> start.toLocalDate()
            start.hour < LATE_NIGHT_END_HOUR -> start.toLocalDate().minusDays(1)
            else -> return@forEach
        }
        names.putIfAbsent(session.packageName, session.appName)
        val perNight = minutesByNight.getOrPut(session.packageName) { mutableMapOf() }
        perNight[night] = (perNight[night] ?: 0.0) + session.durationMs / MS_PER_MINUTE
    }

    val candidates = minutesByNight.mapNotNull { (packageName, perNight) ->
        val qualifying = perNight.filterValues { it >= LATE_NIGHT_MIN_MINUTES }
        if (qualifying.size < LATE_NIGHT_MIN_NIGHTS) return@mapNotNull null
        Candidate(
            packageName = packageName,
            appName = names[packageName] ?: packageName,
            nights = qualifying.size,
            averageMinutes = qualifying.values.average(),
            totalMinutes = qualifying.values.sum(),
        )
    }
    val winner = candidates.maxWithOrNull(
        compareBy<Candidate> { it.nights }.thenBy { it.totalMinutes },
    ) ?: return null

    val average = round(winner.averageMinutes).toInt()
    val fingerprint = evidenceFingerprint(
        detectionType = "LATE_NIGHT_USE",
        subjectPackage = winner.packageName,
        sampleSize = winner.nights,
        keyMetric = winner.averageMinutes,
        metricUnit = 10.0,
    )
    return newFinding(
        detectionType = "LATE_NIGHT_USE",
        headline = "Late-night use",
        body = "On ${winner.nights} of the last $windowDays nights you used ${winner.appName} " +
            "between 11 pm and 4 am, for about $average min on those nights. Late use tends to " +
            "push bedtime back, and a block that starts before bed is an easy way to protect it.",
        evidenceLine = "Observed on ${winner.nights} of $windowDays nights",
        fingerprint = fingerprint,
        evidenceJson = """{"nights":${winner.nights},"window_days":$windowDays,"avg_min":$average}""",
    ).copy(
        subjectPackage = winner.packageName,
        subjectAppName = winner.appName,
    )
}

/**
 * Finds the attention-capture app that takes the most time every day.
 *
 * Works without any day ratings, so it helps from the first two weeks of data. The average is over
 * the whole window, days with no use included. Minimum evidence: ten days of use and an average of
 * at least an hour a day. The suggested limit is advisory and set to three quarters of the average.
 */
fun detectHeavyDailyUse(
    usageRows: List<AppUsageRangeRow>,
    windowDays: Int,
): FindingEntity? {
    if (windowDays <= 0) return null

    data class Candidate(
        val packageName: String,
        val appName: String,
        val averageMinutes: Double,
        val daysUsed: Int,
        val suggestedMinutes: Int,
        val peakHour: Int?,
    )

    val candidates = usageRows
        .filter { DetectorEligibility.isEngagementDesigned(it.packageName, it.category) }
        .groupBy { it.packageName }
        .mapNotNull { (packageName, rows) ->
            val daysUsed = rows.filter { it.foregroundMs > 0L }.map { it.date }.toSet().size
            if (daysUsed < HEAVY_USE_MIN_DAYS) return@mapNotNull null
            val average = rows.sumOf { it.foregroundMs } / MS_PER_MINUTE / windowDays
            if (average < HEAVY_USE_MIN_AVERAGE_MINUTES) return@mapNotNull null
            val suggested = (round(average * 0.75 / 5.0) * 5.0).toInt().coerceAtLeast(15)
            Candidate(packageName, rows.first().appName, average, daysUsed, suggested, peakHourOf(rows))
        }
    val winner = candidates.maxByOrNull { it.averageMinutes } ?: return null

    val average = round(winner.averageMinutes).toInt()
    val peakField = winner.peakHour?.let { ""","peak_hour":$it""" } ?: ""
    val weekly = round(winner.averageMinutes * 7.0 / 60.0 * 10.0) / 10.0
    val fingerprint = evidenceFingerprint(
        detectionType = "HEAVY_DAILY_USE",
        subjectPackage = winner.packageName,
        sampleSize = winner.daysUsed,
        keyMetric = winner.averageMinutes,
        metricUnit = 15.0,
    )
    return newFinding(
        detectionType = "HEAVY_DAILY_USE",
        headline = "Your biggest time sink",
        body = "${winner.appName} averaged $average min a day over the last $windowDays days, " +
            "about $weekly hours a week. A daily limit near ${winner.suggestedMinutes} min is a " +
            "realistic first step.",
        evidenceLine = "Used on ${winner.daysUsed} of $windowDays days",
        fingerprint = fingerprint,
        evidenceJson = """{"avg_min":$average,"days_used":${winner.daysUsed},"window_days":$windowDays,"suggested_min":${winner.suggestedMinutes}$peakField}""",
    ).copy(
        subjectPackage = winner.packageName,
        subjectAppName = winner.appName,
    )
}

/** The local hour of day (0-23) in which the app was used most, from the per-hour rollups. */
private fun peakHourOf(rows: List<AppUsageRangeRow>): Int? {
    val totals = LongArray(24)
    var found = false
    rows.forEach { row ->
        val parts = row.hourlyMs.split(",").mapNotNull { it.trim().toLongOrNull() }
        if (parts.size == 24) {
            parts.forEachIndexed { hour, value -> totals[hour] += value }
            found = true
        }
    }
    if (!found) return null
    val best = totals.indices.maxByOrNull { totals[it] } ?: return null
    return if (totals[best] > 0L) best else null
}
