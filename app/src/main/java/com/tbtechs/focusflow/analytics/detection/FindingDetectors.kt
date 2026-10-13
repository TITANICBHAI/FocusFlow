package com.tbtechs.focusflow.analytics.detection

import com.tbtechs.focusflow.data.local.dao.EstimationErrorRow
import com.tbtechs.focusflow.data.local.dao.SessionOverrideCountRow
import com.tbtechs.focusflow.data.local.entity.FindingEntity
import com.tbtechs.focusflow.data.local.entity.TaskEntity
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.abs
import kotlin.math.round

internal val LOCAL_DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE

/**
 * Fingerprints bucketed evidence so small daily fluctuations do not reset an
 * intentional acknowledgement, while material evidence changes can resurface
 * a finding.
 */
fun evidenceFingerprint(
    detectionType: String,
    subjectPackage: String?,
    sampleSize: Int,
    keyMetric: Double,
    metricUnit: Double,
): String {
    val bucketedSample = sampleSize / 5
    val bucketedMetric = if (metricUnit == 0.0) {
        0
    } else {
        (keyMetric / metricUnit).toInt()
    }
    val raw = "$detectionType|${subjectPackage ?: ""}|$bucketedSample|$bucketedMetric"
    return MessageDigest.getInstance("SHA-1")
        .digest(raw.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

internal fun localDateOf(isoTimestamp: String): LocalDate =
    runCatching {
        Instant.parse(isoTimestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    }.getOrElse {
        LocalDateTime.parse(isoTimestamp).toLocalDate()
    }

internal fun localHourOf(isoTimestamp: String): Int =
    runCatching {
        Instant.parse(isoTimestamp).atZone(ZoneId.systemDefault()).hour
    }.getOrElse {
        LocalDateTime.parse(isoTimestamp).hour
    }

private fun instantOf(isoTimestamp: String): Instant =
    runCatching { Instant.parse(isoTimestamp) }.getOrElse {
        LocalDateTime.parse(isoTimestamp)
            .atZone(ZoneId.systemDefault())
            .toInstant()
    }

internal fun newFinding(
    detectionType: String,
    headline: String,
    body: String,
    evidenceLine: String,
    fingerprint: String,
    evidenceJson: String,
): FindingEntity {
    val now = Instant.now().toString()
    return FindingEntity(
        id = UUID.randomUUID().toString(),
        detectionType = detectionType,
        subjectPackage = null,
        subjectAppName = null,
        state = "detected",
        evidenceFingerprint = fingerprint,
        evidenceJson = evidenceJson,
        headline = headline,
        body = body,
        evidenceLine = evidenceLine,
        firstDetectedAt = now,
        lastUpdatedAt = now,
        seenAt = null,
        resolvedAt = null,
        suppressedUntil = null,
    )
}

private val UNRESOLVED_STATUSES = setOf("scheduled", "active")
private val FAILED_STATUSES = setOf("skipped", "overdue")

/**
 * Detects whether a missed/skipped first task predicts a worse rest-of-day
 * completion rate than a completed first task.
 *
 * Minimum evidence: six days in each comparison group and a 20-point gap.
 */
fun detectPostFailureCascade(tasks: List<TaskEntity>): FindingEntity? {
    val byDate = tasks
        .groupBy { localDateOf(it.startTime) }
        .filterValues { it.size >= 3 }
        .mapValues { (_, dayTasks) -> dayTasks.sortedBy { it.startTime } }

    if (byDate.size < 8) return null

    val missedFirstRates = mutableListOf<Double>()
    val completedFirstRates = mutableListOf<Double>()

    byDate.values.forEach { dayTasks ->
        val first = dayTasks.first()
        val rest = dayTasks.drop(1).filter { it.status !in UNRESOLVED_STATUSES }
        if (rest.isEmpty()) return@forEach

        val restRate = rest.count { it.status == "completed" }.toDouble() / rest.size
        when (first.status) {
            in FAILED_STATUSES -> missedFirstRates += restRate
            "completed" -> completedFirstRates += restRate
        }
    }

    if (missedFirstRates.size < 6 || completedFirstRates.size < 6) return null

    val missedAverage = missedFirstRates.average()
    val completedAverage = completedFirstRates.average()
    val gap = completedAverage - missedAverage
    if (gap < 0.20) return null

    val missedPct = round(missedAverage * 100).toInt()
    val completedPct = round(completedAverage * 100).toInt()
    val gapPct = round(gap * 100).toInt()
    val fingerprint = evidenceFingerprint(
        detectionType = "POST_FAILURE_CASCADE",
        subjectPackage = null,
        sampleSize = missedFirstRates.size,
        keyMetric = gap,
        metricUnit = 0.05,
    )

    return newFinding(
        detectionType = "POST_FAILURE_CASCADE",
        headline = "First task predicts the rest",
        body = "When your first task of the day is missed or skipped, the rest of " +
            "the day completes at $missedPct% on average. When the first task " +
            "lands, that rises to $completedPct% — a $gapPct point gap.",
        evidenceLine = "Observed across ${missedFirstRates.size} low-start days and " +
            "${completedFirstRates.size} on-track days",
        fingerprint = fingerprint,
        evidenceJson = """{"missed_first_days":${missedFirstRates.size},"ok_first_days":${completedFirstRates.size},"gap_pct":$gapPct}""",
    )
}

private data class DurationBin(
    val label: String,
    val rangeMin: Int,
    val rangeMaxExclusive: Int,
)

private val DURATION_BINS = listOf(
    DurationBin("0-15m", 0, 15),
    DurationBin("15-30m", 15, 30),
    DurationBin("30-45m", 30, 45),
    DurationBin("45-60m", 45, 60),
    DurationBin("60-90m", 60, 90),
    DurationBin("90m+", 90, Int.MAX_VALUE),
)

/**
 * Finds the duration bin with the highest clean-session rate, then detects a
 * later eligible bin whose rate is below 60% of that peak.
 *
 * Minimum evidence: 20 closed sessions and two bins with three samples each.
 */
fun detectSessionSweetSpot(sessions: List<SessionOverrideCountRow>): FindingEntity? {
    val closed = sessions.filter { it.endedAt != null }
    if (closed.size < 20) return null

    data class BinStat(var total: Int = 0, var clean: Int = 0)
    val stats = DURATION_BINS.associateWith { BinStat() }

    closed.forEach { row ->
        val startedAt = runCatching { instantOf(row.startedAt) }.getOrNull()
        val endedAt = runCatching { instantOf(row.endedAt!!) }.getOrNull()
        if (startedAt == null || endedAt == null) return@forEach

        val minutes = java.time.Duration.between(startedAt, endedAt).toMinutes().toInt()
        val bin = DURATION_BINS.firstOrNull {
            minutes >= it.rangeMin && minutes < it.rangeMaxExclusive
        } ?: return@forEach

        val stat = stats.getValue(bin)
        stat.total += 1
        if (row.overrideCount == 0) stat.clean += 1
    }

    val eligible = stats.filter { (_, stat) -> stat.total >= 3 }
    if (eligible.size < 2) return null

    val peakBin = eligible.keys.maxWithOrNull(
        compareBy<DurationBin> { bin ->
            val stat = stats.getValue(bin)
            stat.clean.toDouble() / stat.total
        }.thenBy { bin -> DURATION_BINS.indexOf(bin) },
    ) ?: return null
    val peakStat = stats.getValue(peakBin)
    val peakRate = peakStat.clean.toDouble() / peakStat.total
    val peakIndex = DURATION_BINS.indexOf(peakBin)

    val dropOff = DURATION_BINS
        .drop(peakIndex + 1)
        .firstOrNull { bin ->
            val stat = stats.getValue(bin)
            stat.total >= 3 &&
                stat.clean.toDouble() / stat.total < peakRate * 0.6
        } ?: return null

    val dropStat = stats.getValue(dropOff)
    val dropRate = dropStat.clean.toDouble() / dropStat.total
    val fingerprint = evidenceFingerprint(
        detectionType = "SESSION_SWEET_SPOT",
        subjectPackage = null,
        sampleSize = closed.size,
        keyMetric = peakRate - dropRate,
        metricUnit = 0.1,
    )

    return newFinding(
        detectionType = "SESSION_SWEET_SPOT",
        headline = "Your effective focus window",
        body = "Sessions you run for ${peakBin.label} are clean " +
            "${round(peakRate * 100).toInt()}% of the time. Beyond that, at " +
            "${dropOff.label}, clean sessions drop to " +
            "${round(dropRate * 100).toInt()}%. Your schedule may have sessions " +
            "longer than your data supports.",
        evidenceLine = "Observed across ${closed.size} sessions",
        fingerprint = fingerprint,
        evidenceJson = """{"peak_bin":"${peakBin.label}","peak_rate_pct":${round(peakRate * 100).toInt()},"dropoff_bin":"${dropOff.label}","dropoff_rate_pct":${round(dropRate * 100).toInt()}}""",
    )
}

/**
 * Compares actual-minus-planned minutes for tasks started before noon versus
 * tasks started after 2pm.
 *
 * Minimum evidence: five samples per period and a gap greater than 10 minutes.
 */
fun detectEstimationDrift(errors: List<EstimationErrorRow>): FindingEntity? {
    val morning = errors.filter { (it.startHour ?: -1) in 0 until 12 }
    val afternoon = errors.filter { (it.startHour ?: -1) >= 14 }
    if (morning.size < 5 || afternoon.size < 5) return null

    val morningError = morning.map { it.actualMinutes - it.plannedMinutes }.average()
    val afternoonError = afternoon.map { it.actualMinutes - it.plannedMinutes }.average()
    val gap = abs(morningError - afternoonError)
    if (gap < 10.0) return null

    val fingerprint = evidenceFingerprint(
        detectionType = "ESTIMATION_DRIFT",
        subjectPackage = null,
        sampleSize = morning.size + afternoon.size,
        keyMetric = gap,
        metricUnit = 5.0,
    )

    return newFinding(
        detectionType = "ESTIMATION_DRIFT",
        headline = "Your estimates shift through the day",
        body = "Tasks you start before noon run ${signedMinutes(morningError)} " +
            "vs planned, on average. Tasks starting after 2pm run " +
            "${signedMinutes(afternoonError)}. That's a consistent pattern, " +
            "not a one-off week.",
        evidenceLine = "Observed across ${morning.size} morning and " +
            "${afternoon.size} afternoon sessions",
        fingerprint = fingerprint,
        evidenceJson = """{"morning_error_min":${round(morningError).toInt()},"afternoon_error_min":${round(afternoonError).toInt()},"morning_n":${morning.size},"afternoon_n":${afternoon.size}}""",
    )
}

private fun signedMinutes(value: Double): String {
    val rounded = round(abs(value)).toInt()
    return if (value >= 0) "${rounded}m over" else "${rounded}m under"
}

/**
 * Finds the weekday furthest below the user's own completion-rate mean.
 *
 * Minimum evidence: five represented weekdays, three resolved tasks per day,
 * and a gap of at least 20 percentage points.
 */
fun detectDayOfWeekOutlier(tasks: List<TaskEntity>): FindingEntity? {
    val resolved = tasks.filter { it.status !in UNRESOLVED_STATUSES }
    val byDayOfWeek = resolved.groupBy { localDateOf(it.startTime).dayOfWeek }
    val rates = byDayOfWeek
        .filterValues { it.size >= 3 }
        .mapValues { (_, dayTasks) ->
            dayTasks.count { it.status == "completed" }.toDouble() / dayTasks.size
        }

    if (rates.size < 5) return null

    val mean = rates.values.average()
    val (worstDay, worstRate) = rates.minByOrNull { it.value } ?: return null
    val gap = mean - worstRate
    if (gap < 0.20) return null

    val dayName = worstDay.getDisplayName(
        java.time.format.TextStyle.FULL,
        java.util.Locale.getDefault(),
    )
    val meanPct = round(mean * 100).toInt()
    val worstPct = round(worstRate * 100).toInt()
    val gapPct = round(gap * 100).toInt()
    val fingerprint = evidenceFingerprint(
        detectionType = "DAY_OF_WEEK_OUTLIER",
        subjectPackage = null,
        sampleSize = byDayOfWeek.values.sumOf { it.size },
        keyMetric = gap,
        metricUnit = 0.05,
    )

    return newFinding(
        detectionType = "DAY_OF_WEEK_OUTLIER",
        headline = "$dayName is your outlier",
        body = "Your average completion rate is $meanPct%. $dayName sits at " +
            "$worstPct% — $gapPct points below your own baseline. Not occasionally. " +
            "Consistently.",
        evidenceLine = "Observed across ${byDayOfWeek.getValue(worstDay).size} " +
            "${dayName}s",
        fingerprint = fingerprint,
        evidenceJson = """{"day":"$dayName","mean_pct":$meanPct,"worst_pct":$worstPct,"gap_pct":$gapPct}""",
    )
}