package com.tbtechs.focusflow.analytics.detection

import com.tbtechs.focusflow.data.local.dao.AppUsageRangeRow
import com.tbtechs.focusflow.data.local.dao.FirstSessionRow
import com.tbtechs.focusflow.data.local.dao.SessionStatRow
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity
import com.tbtechs.focusflow.data.local.entity.DayRatingEntity
import com.tbtechs.focusflow.data.local.entity.FindingEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.round
import kotlin.math.sqrt

private const val MS_PER_MIN = 60_000.0
private const val MIN_DAYS_PER_WEEK = 4

private data class DurationStats(
    val mean: Double,
    val stdDev: Double,
    val max: Double,
    val count: Int,
)

private fun durationStats(sessions: List<AppSessionEntity>): DurationStats? {
    if (sessions.isEmpty()) return null
    val values = sessions.map { it.durationMs.toDouble() }
    val mean = values.average()
    val variance = values.sumOf { value -> (value - mean) * (value - mean) } / values.size
    return DurationStats(
        mean = mean,
        stdDev = sqrt(variance),
        max = values.maxOrNull() ?: return null,
        count = values.size,
    )
}

private val STREAK_ELIGIBLE_CATEGORIES =
    setOf("social", "entertainment", "communication")
private val ALLOWANCE_ELIGIBLE_CATEGORIES = setOf("social", "entertainment")

internal data class WeeklyAverages(
    val appName: String,
    val week1: Double,
    val week2: Double,
    val week3: Double,
    val week4: Double,
)

/**
 * Buckets completed-day usage into four seven-day periods relative to [today],
 * with week 4 covering D-7 through D-1. Packages with fewer than four days in
 * any week are excluded so sparse data cannot masquerade as a trend.
 */
internal fun computeWeeklyAverages(
    usageRows: List<AppUsageRangeRow>,
    today: LocalDate,
): Map<String, WeeklyAverages> {
    fun weekOf(date: LocalDate): Int {
        val daysAgo = ChronoUnit.DAYS.between(date, today.minusDays(1))
        return when {
            daysAgo < 0 -> 0
            daysAgo < 7 -> 4
            daysAgo < 14 -> 3
            daysAgo < 21 -> 2
            daysAgo < 28 -> 1
            else -> 0
        }
    }

    return usageRows.groupBy { it.packageName }.mapNotNull { (packageName, rows) ->
        val byWeek = rows.mapNotNull { row ->
            val date = runCatching { LocalDate.parse(row.date) }.getOrNull()
                ?: return@mapNotNull null
            val week = weekOf(date)
            if (week == 0) null else week to row.foregroundMs
        }.groupBy({ it.first }, { it.second })

        if ((1..4).any { (byWeek[it]?.size ?: 0) < MIN_DAYS_PER_WEEK }) {
            return@mapNotNull null
        }

        val averages = (1..4).associateWith { week -> byWeek.getValue(week).average() }
        packageName to WeeklyAverages(
            appName = rows.first().appName,
            week1 = averages.getValue(1),
            week2 = averages.getValue(2),
            week3 = averages.getValue(3),
            week4 = averages.getValue(4),
        )
    }.toMap()
}

/**
 * Finds a high-frequency app with short, highly variable sessions.
 *
 * The caller supplies raw sessions only for packages that passed the cheap
 * daily aggregate filter.
 */
fun detectVariableRewardLoop(
    dailyStats: List<SessionStatRow>,
    rawSessionsByPackage: Map<String, List<AppSessionEntity>>,
    appNames: Map<String, String>,
): FindingEntity? {
    data class Candidate(
        val packageName: String,
        val cv: Double,
        val meanMinutes: Double,
        val sampleDays: Int,
        val raw: List<AppSessionEntity>,
    )

    val candidates = dailyStats
        .groupBy { it.packageName }
        .mapNotNull { (packageName, days) ->
            val totalSessions = days.sumOf { it.sessionCount }
            val sampleDays = days.size
            if (sampleDays < 10 || totalSessions == 0) return@mapNotNull null
            if (totalSessions.toDouble() / sampleDays < 5.0) return@mapNotNull null

            val weightedMeanMs =
                days.sumOf { it.avgDurationMs * it.sessionCount } / totalSessions
            if (weightedMeanMs > 4 * MS_PER_MIN) return@mapNotNull null

            val raw = rawSessionsByPackage[packageName] ?: return@mapNotNull null
            val stats = durationStats(raw) ?: return@mapNotNull null
            if (stats.mean == 0.0) return@mapNotNull null
            val cv = stats.stdDev / stats.mean
            if (cv < 0.9) return@mapNotNull null

            Candidate(packageName, cv, stats.mean / MS_PER_MIN, sampleDays, raw)
        }

    val winner = candidates.maxByOrNull { it.cv } ?: return null
    val appName = appNames[winner.packageName] ?: winner.packageName
    val minMinutes = winner.raw.minOf { it.durationMs } / MS_PER_MIN
    val maxMinutes = winner.raw.maxOf { it.durationMs } / MS_PER_MIN
    val averageLaunches =
        round((winner.raw.size.toDouble() / winner.sampleDays) * 10) / 10

    val fingerprint = evidenceFingerprint(
        detectionType = "VARIABLE_REWARD_LOOP",
        subjectPackage = winner.packageName,
        sampleSize = winner.raw.size,
        keyMetric = winner.cv,
        metricUnit = 0.1,
    )

    return newFinding(
        detectionType = "VARIABLE_REWARD_LOOP",
        headline = "Variable reward loop",
        body = "You open $appName about $averageLaunches times a day. Average session: " +
            "${round(winner.meanMinutes * 10) / 10}m — but they range from " +
            "${round(minMinutes * 10) / 10}m to ${round(maxMinutes * 10) / 10}m. Sessions with " +
            "no consistent length are the signature of a feed with no designed stopping point.",
        evidenceLine = "Observed across ${winner.sampleDays} days, " +
            "${winner.raw.size} sessions",
        fingerprint = fingerprint,
        evidenceJson = """{"cv":${round(winner.cv * 100) / 100},"avg_launches_per_day":$averageLaunches,"sample_days":${winner.sampleDays}}""",
    ).copy(
        subjectPackage = winner.packageName,
        subjectAppName = appName,
    )
}

/**
 * Finds a non-utility app whose session duration has both a large spread and
 * an unusually long maximum session.
 */
fun detectInfiniteSessionDesign(
    dailyStats: List<SessionStatRow>,
    rawSessionsByPackage: Map<String, List<AppSessionEntity>>,
    appNames: Map<String, String>,
    categories: Map<String, String?>,
): FindingEntity? {
    data class Candidate(
        val packageName: String,
        val stdDevMinutes: Double,
        val meanMinutes: Double,
        val maxMinutes: Double,
        val sampleDays: Int,
        val sessionCount: Int,
    )

    val candidates = dailyStats
        .groupBy { it.packageName }
        .mapNotNull { (packageName, days) ->
            if (categories[packageName] == "utility") return@mapNotNull null
            val sampleDays = days.size
            if (sampleDays < 14) return@mapNotNull null

            val totalSessions = days.sumOf { it.sessionCount }
            if (totalSessions == 0) return@mapNotNull null
            val weightedMeanMs =
                days.sumOf { it.avgDurationMs * it.sessionCount } / totalSessions
            if (weightedMeanMs <= 5 * MS_PER_MIN) return@mapNotNull null

            val raw = rawSessionsByPackage[packageName] ?: return@mapNotNull null
            val stats = durationStats(raw) ?: return@mapNotNull null
            val stdDevMinutes = stats.stdDev / MS_PER_MIN
            val meanMinutes = stats.mean / MS_PER_MIN
            val maxMinutes = stats.max / MS_PER_MIN
            if (stdDevMinutes <= 20.0 || maxMinutes <= 3 * meanMinutes) {
                return@mapNotNull null
            }

            Candidate(
                packageName,
                stdDevMinutes,
                meanMinutes,
                maxMinutes,
                sampleDays,
                stats.count,
            )
        }

    val winner = candidates.maxByOrNull { it.stdDevMinutes } ?: return null
    val appName = appNames[winner.packageName] ?: winner.packageName
    val fingerprint = evidenceFingerprint(
        detectionType = "INFINITE_SESSION_DESIGN",
        subjectPackage = winner.packageName,
        sampleSize = winner.sessionCount,
        keyMetric = winner.stdDevMinutes,
        metricUnit = 5.0,
    )

    return newFinding(
        detectionType = "INFINITE_SESSION_DESIGN",
        headline = "Infinite session design",
        body = "Your $appName sessions have no typical length. They've run as short as a " +
            "few minutes and as long as ${round(winner.maxMinutes).toInt()}m. That variance " +
            "isn't your preference — it's the absence of a designed stopping point. You " +
            "supply the brake each time.",
        evidenceLine = "Observed across ${winner.sampleDays} days, " +
            "${winner.sessionCount} sessions",
        fingerprint = fingerprint,
        evidenceJson = """{"stddev_min":${round(winner.stdDevMinutes)},"mean_min":${round(winner.meanMinutes)},"max_min":${round(winner.maxMinutes)}}""",
    ).copy(
        subjectPackage = winner.packageName,
        subjectAppName = appName,
    )
}

data class MorningHijackResult(
    val finding: FindingEntity,
    val qualifyingDates: List<String>,
    val packageName: String,
)

/**
 * Finds a social or entertainment app that captures the first session on at
 * least 65% of represented mornings.
 */
fun detectMorningHijack(
    firstSessions: List<FirstSessionRow>,
    categories: Map<String, String?>,
): MorningHijackResult? {
    if (firstSessions.size < 7) return null

    data class Candidate(
        val packageName: String,
        val appName: String,
        val fraction: Double,
        val dates: List<String>,
    )

    val totalDays = firstSessions.size
    val candidates = firstSessions
        .groupBy { it.packageName }
        .mapNotNull { (packageName, rows) ->
            val category = categories[packageName]
            if (category != "social" && category != "entertainment") {
                return@mapNotNull null
            }
            val fraction = rows.size.toDouble() / totalDays
            if (fraction < 0.65) return@mapNotNull null
            Candidate(
                packageName = packageName,
                appName = rows.first().appName,
                fraction = fraction,
                dates = rows.map { it.localDate },
            )
        }

    val winner = candidates.maxByOrNull { it.fraction } ?: return null
    val percentage = round(winner.fraction * 100).toInt()
    val fingerprint = evidenceFingerprint(
        detectionType = "MORNING_HIJACK",
        subjectPackage = winner.packageName,
        sampleSize = totalDays,
        keyMetric = winner.fraction,
        metricUnit = 0.05,
    )

    val finding = newFinding(
        detectionType = "MORNING_HIJACK",
        headline = "Morning hijack",
        body = "On $percentage% of recent mornings, ${winner.appName} was the first thing " +
            "you opened — before starting anything else. Apps compete for this window " +
            "specifically. Whoever captures your attention first shapes the mode you " +
            "work in for the rest of the day.",
        evidenceLine = "Observed on ${winner.dates.size} of $totalDays days",
        fingerprint = fingerprint,
        evidenceJson = """{"fraction_pct":$percentage,"sample_days":$totalDays}""",
    ).copy(
        subjectPackage = winner.packageName,
        subjectAppName = winner.appName,
    )

    return MorningHijackResult(
        finding = finding,
        qualifyingDates = winner.dates,
        packageName = winner.packageName,
    )
}

/**
 * Finds a package whose average foreground time rises every week over a
 * complete 28-day window.
 */
fun detectEscalatingCapture(
    usageRows: List<AppUsageRangeRow>,
    today: LocalDate,
): FindingEntity? {
    data class Candidate(
        val packageName: String,
        val appName: String,
        val growth: Double,
        val week1Minutes: Double,
        val week4Minutes: Double,
    )

    val candidates = computeWeeklyAverages(usageRows, today).mapNotNull { (packageName, weeks) ->
        if (!(weeks.week2 > weeks.week1 &&
                weeks.week3 > weeks.week2 &&
                weeks.week4 > weeks.week3)
        ) {
            return@mapNotNull null
        }
        if (weeks.week1 <= 5 * MS_PER_MIN) return@mapNotNull null

        val growth = (weeks.week4 - weeks.week1) / weeks.week1
        if (growth < 0.40) return@mapNotNull null

        Candidate(
            packageName = packageName,
            appName = weeks.appName,
            growth = growth,
            week1Minutes = weeks.week1 / MS_PER_MIN,
            week4Minutes = weeks.week4 / MS_PER_MIN,
        )
    }

    val winner = candidates.maxByOrNull { it.growth } ?: return null
    val growthPercentage = round(winner.growth * 100).toInt()
    val fingerprint = evidenceFingerprint(
        detectionType = "ESCALATING_CAPTURE",
        subjectPackage = winner.packageName,
        sampleSize = 28,
        keyMetric = winner.growth,
        metricUnit = 0.1,
    )

    return newFinding(
        detectionType = "ESCALATING_CAPTURE",
        headline = "Escalating capture",
        body = "${winner.appName} was taking ${round(winner.week1Minutes).toInt()}m of your day " +
            "4 weeks ago. It's at ${round(winner.week4Minutes).toInt()}m now — up " +
            "$growthPercentage%. Usage that grows every week without a deliberate decision " +
            "is the algorithm improving its model of you, not you choosing to spend more " +
            "time there.",
        evidenceLine = "Growth measured over 4 weeks",
        fingerprint = fingerprint,
        evidenceJson = """{"growth_pct":$growthPercentage,"week1_min":${round(winner.week1Minutes).toInt()},"week4_min":${round(winner.week4Minutes).toInt()}}""",
    ).copy(
        subjectPackage = winner.packageName,
        subjectAppName = winner.appName,
    )
}

/**
 * Finds a pair of apps where a substantial drop in one is offset by a rise in
 * another while their combined daily usage remains nearly flat.
 */
fun detectSubstitution(
    usageRows: List<AppUsageRangeRow>,
    today: LocalDate,
): FindingEntity? {
    val weekly = computeWeeklyAverages(usageRows, today)
    if (weekly.size < 2) return null

    data class Candidate(
        val downPackage: String,
        val downName: String,
        val downPercent: Int,
        val upPackage: String,
        val upName: String,
        val upPercent: Int,
        val combinedShift: Int,
    )

    val entries = weekly.entries.toList()
    val candidates = buildList {
        for (downIndex in entries.indices) {
            for (upIndex in entries.indices) {
                if (downIndex == upIndex) continue
                val (downPackage, down) = entries[downIndex]
                val (upPackage, up) = entries[upIndex]
                if (down.week1 <= 0.0 || up.week1 <= 0.0) continue

                val downChange = (down.week4 - down.week1) / down.week1
                val upChange = (up.week4 - up.week1) / up.week1
                if (downChange > -0.30 || upChange < 0.30) continue

                val initialCombined = down.week1 + up.week1
                val recentCombined = down.week4 + up.week4
                val combinedShift =
                    kotlin.math.abs(recentCombined - initialCombined) / initialCombined
                if (combinedShift >= 0.15) continue

                val downPercent = round(-downChange * 100).toInt()
                val upPercent = round(upChange * 100).toInt()
                add(
                    Candidate(
                        downPackage = downPackage,
                        downName = down.appName,
                        downPercent = downPercent,
                        upPackage = upPackage,
                        upName = up.appName,
                        upPercent = upPercent,
                        combinedShift = downPercent + upPercent,
                    ),
                )
            }
        }
    }

    val winner = candidates.maxByOrNull { it.combinedShift } ?: return null
    val pairKey = "${winner.downPackage}|${winner.upPackage}"
    val fingerprint = evidenceFingerprint(
        detectionType = "SUBSTITUTION",
        subjectPackage = pairKey,
        sampleSize = 28,
        keyMetric = winner.combinedShift.toDouble(),
        metricUnit = 10.0,
    )

    return newFinding(
        detectionType = "SUBSTITUTION",
        headline = "Same time, different destination",
        body = "Your combined time across these two apps has barely changed over the last " +
            "4 weeks. What shifted: ${winner.downName} is down ${winner.downPercent}% while " +
            "${winner.upName} is up ${winner.upPercent}%. The time budget stayed the same — " +
            "the destination moved.",
        evidenceLine = "Compared over 4 weeks",
        fingerprint = fingerprint,
        evidenceJson = """{"down_app":${jsonString(winner.downName)},"down_pct":${winner.downPercent},"up_app":${jsonString(winner.upName)},"up_pct":${winner.upPercent}}""",
    )
}

/**
 * Compares discretionary-app usage on high-rated and low-rated days and
 * surfaces a personalized, advisory daily allowance suggestion.
 */
fun detectAllowanceSuggestion(
    usageRows: List<AppUsageRangeRow>,
    ratings: List<DayRatingEntity>,
): FindingEntity? {
    if (ratings.size < 7) return null
    val ratingByDate = ratings.associateBy { it.date }
    val byPackage = usageRows
        .filter { it.category in ALLOWANCE_ELIGIBLE_CATEGORIES }
        .groupBy { it.packageName }

    data class Candidate(
        val packageName: String,
        val appName: String,
        val highAverageMinutes: Double,
        val lowAverageMinutes: Double,
        val highDayCount: Int,
        val lowDayCount: Int,
        val suggestedMinutes: Int,
    )

    val candidates = byPackage.mapNotNull { (packageName, rows) ->
        val highDays = rows.filter { (ratingByDate[it.date]?.rating ?: -1) >= 7 }
        val lowDays = rows.filter { (ratingByDate[it.date]?.rating ?: 99) <= 4 }
        if (highDays.size < 5 || lowDays.size < 3) return@mapNotNull null

        val highAverageMinutes =
            highDays.map { it.foregroundMs }.average() / MS_PER_MIN
        val lowAverageMinutes =
            lowDays.map { it.foregroundMs }.average() / MS_PER_MIN
        if (highAverageMinutes >= lowAverageMinutes - 15.0) return@mapNotNull null

        val suggestedMinutes =
            (round(highAverageMinutes * 1.1 / 5.0) * 5.0).toInt().coerceAtLeast(5)
        Candidate(
            packageName = packageName,
            appName = rows.first().appName,
            highAverageMinutes = highAverageMinutes,
            lowAverageMinutes = lowAverageMinutes,
            highDayCount = highDays.size,
            lowDayCount = lowDays.size,
            suggestedMinutes = suggestedMinutes,
        )
    }

    val winner = candidates.maxByOrNull {
        it.lowAverageMinutes - it.highAverageMinutes
    } ?: return null

    val gap = winner.lowAverageMinutes - winner.highAverageMinutes
    val fingerprint = evidenceFingerprint(
        detectionType = "ALLOWANCE_SUGGESTION",
        subjectPackage = winner.packageName,
        sampleSize = winner.highDayCount + winner.lowDayCount,
        keyMetric = gap,
        metricUnit = 5.0,
    )

    return newFinding(
        detectionType = "ALLOWANCE_SUGGESTION",
        headline = "Based on your own data",
        body = "On days you rate 7 or higher, your ${winner.appName} use averages " +
            "${round(winner.highAverageMinutes).toInt()}m. On days you rate 4 or below, it " +
            "averages ${round(winner.lowAverageMinutes).toInt()}m. Your data suggests a " +
            "${winner.suggestedMinutes}m daily limit — close to what your best days already look like.",
        evidenceLine = "Based on ${winner.highDayCount} high-rated and " +
            "${winner.lowDayCount} low-rated days",
        fingerprint = fingerprint,
        evidenceJson = """{"high_avg_min":${round(winner.highAverageMinutes).toInt()},"low_avg_min":${round(winner.lowAverageMinutes).toInt()},"suggested_min":${winner.suggestedMinutes}}""",
    ).copy(
        subjectPackage = winner.packageName,
        subjectAppName = winner.appName,
    )
}

private fun jsonString(value: String): String = buildString {
    append('"')
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character < ' ') {
                append("\\u%04x".format(character.code))
            } else {
                append(character)
            }
        }
    }
    append('"')
}

/**
 * Finds an eligible app opened on nearly every day, including days the user
 * rated poorly.
 */
fun detectStreakLockIn(
    usageRows: List<AppUsageRangeRow>,
    ratings: List<DayRatingEntity>,
    windowDays: Int,
): FindingEntity? {
    if (ratings.size < 3 || windowDays <= 0) return null
    val ratingByDate = ratings.associateBy { it.date }

    data class Candidate(
        val packageName: String,
        val appName: String,
        val consistency: Double,
        val openDates: Set<String>,
        val lowRatedDates: List<String>,
        val averageLowRating: Double,
    )

    val candidates = usageRows
        .filter {
            it.launchCount > 0 &&
                it.category in STREAK_ELIGIBLE_CATEGORIES
        }
        .groupBy { it.packageName }
        .mapNotNull { (packageName, rows) ->
            val openDates = rows.map { it.date }.toSet()
            val consistency = openDates.size.toDouble() / windowDays
            if (consistency < 0.90) return@mapNotNull null

            val lowRatedDates = openDates.filter {
                (ratingByDate[it]?.rating ?: 99) <= 4
            }
            if (lowRatedDates.size < 3) return@mapNotNull null

            val averageLowRating = lowRatedDates
                .mapNotNull { ratingByDate[it]?.rating }
                .average()
            Candidate(
                packageName = packageName,
                appName = rows.first().appName,
                consistency = consistency,
                openDates = openDates,
                lowRatedDates = lowRatedDates,
                averageLowRating = averageLowRating,
            )
        }

    val winner = candidates.maxByOrNull { it.consistency } ?: return null
    val fingerprint = evidenceFingerprint(
        detectionType = "STREAK_LOCK_IN",
        subjectPackage = winner.packageName,
        sampleSize = winner.openDates.size,
        keyMetric = winner.consistency,
        metricUnit = 0.05,
    )

    return newFinding(
        detectionType = "STREAK_LOCK_IN",
        headline = "Streak lock-in",
        body = "You've opened ${winner.appName} on ${winner.openDates.size} of the last " +
            "$windowDays days — including ${winner.lowRatedDates.size} days you rated " +
            "${round(winner.averageLowRating * 10) / 10}/10 or lower. Streak mechanics " +
            "use loss aversion — the fear of losing what you've built — to require daily " +
            "engagement regardless of whether the day is going well.",
        evidenceLine = "Observed across ${winner.openDates.size} days, " +
            "${winner.lowRatedDates.size} low-rated",
        fingerprint = fingerprint,
        evidenceJson = """{"consistency_pct":${round(winner.consistency * 100).toInt()},"low_rated_days":${winner.lowRatedDates.size},"avg_low_rating":${round(winner.averageLowRating * 10) / 10}}""",
    ).copy(
        subjectPackage = winner.packageName,
        subjectAppName = winner.appName,
    )
}