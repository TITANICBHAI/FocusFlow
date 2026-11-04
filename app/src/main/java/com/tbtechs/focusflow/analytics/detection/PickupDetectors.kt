package com.tbtechs.focusflow.analytics.detection

import com.tbtechs.focusflow.analytics.TemptationEntry
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity
import com.tbtechs.focusflow.data.local.entity.FindingEntity
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.round

/*
 * Detectors that look at HOW the phone is picked up and used, not at tasks.
 *
 * The usage tracker ends a foreground session whenever the screen turns off or the keyguard
 * appears, so a gap of a couple of minutes between two sessions means the phone was put down.
 * The first app after such a gap is the app the person "reaches for" on picking the phone up.
 * It is an estimate from foreground sessions (there is no separate unlock event stored), and the
 * findings word it that way.
 */

private const val PICKUP_GAP_MS = 2L * 60_000L
private const val BRIEF_CHECK_MS = 30_000L
private const val HOP_GAP_MS = 15_000L
private const val ENTRY_MIN_PICKUPS = 40
private const val ENTRY_MIN_COUNT = 15
private const val ENTRY_MIN_SHARE = 0.25
private const val MIN_DAYS = 7
private const val CHECKS_MIN_PER_DAY = 40.0
private const val CHECKS_MIN_BRIEF_SHARE = 0.4
private const val HOP_MIN_COUNT = 12
private const val HOP_MIN_DAYS = 6
private const val HOP_MIN_SHARE = 0.3
private const val ATTEMPTS_MIN_TOTAL = 8
private const val ATTEMPTS_MIN_AT_PEAK = 4
private const val ATTEMPTS_MIN_PEAK_SHARE = 0.25
private const val ATTEMPTS_MIN_DAYS = 3
private const val MS_PER_DAY = 86_400_000L
private val NOT_AN_APP_HINTS = listOf(
    "launcher", "systemui", "inputmethod", "lockscreen", "keyguard", "com.tbtechs.focusflow",
)

internal data class Pickup(
    val entryPackage: String,
    val entryApp: String,
    val startMs: Long,
    val foregroundMs: Long,
)

/** Groups sessions into pickups: a new pickup starts after a break of two minutes or more. */
internal fun buildPickups(sessions: List<AppSessionEntity>): List<Pickup> {
    val usable = sessions
        .filter { session ->
            val name = session.packageName.lowercase(Locale.ROOT)
            NOT_AN_APP_HINTS.none { name.contains(it) }
        }
        .sortedBy { it.startedAt }
    val pickups = mutableListOf<Pickup>()
    var entry: AppSessionEntity? = null
    var lastEnd = 0L
    var foreground = 0L
    usable.forEach { session ->
        val current = entry
        if (current == null || session.startedAt - lastEnd >= PICKUP_GAP_MS) {
            if (current != null) {
                pickups += Pickup(current.packageName, current.appName, current.startedAt, foreground)
            }
            entry = session
            foreground = 0L
        }
        foreground += session.durationMs
        lastEnd = maxOf(lastEnd, session.endedAt)
    }
    entry?.let { pickups += Pickup(it.packageName, it.appName, it.startedAt, foreground) }
    return pickups
}

private fun distinctDays(pickups: List<Pickup>, zone: ZoneId): Int =
    pickups.map { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() }.toSet().size

/** The app opened first, most often, after picking the phone up. */
fun detectEntryApp(
    sessions: List<AppSessionEntity>,
    categories: Map<String, String?>,
    windowDays: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): FindingEntity? {
    val pickups = buildPickups(sessions)
    if (pickups.size < ENTRY_MIN_PICKUPS || distinctDays(pickups, zone) < MIN_DAYS) return null

    val ranked = pickups.groupingBy { it.entryPackage }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    val top = ranked.first()
    val share = top.value.toDouble() / pickups.size
    if (top.value < ENTRY_MIN_COUNT || share < ENTRY_MIN_SHARE) return null

    val appName = pickups.first { it.entryPackage == top.key }.entryApp
    val sharePct = round(share * 100).toInt()
    val runnerUp = ranked.getOrNull(1)?.takeIf { it.value.toDouble() / pickups.size >= 0.10 }
    val runnerUpText = runnerUp?.let { entry ->
        val name = pickups.first { it.entryPackage == entry.key }.entryApp
        " Next most often: $name (${round(entry.value * 100.0 / pickups.size).toInt()}%)."
    }.orEmpty()
    val blockable = DetectorEligibility.isEngagementDesigned(top.key, categories[top.key])
    // The hour this app is reached for most often, only when it clearly stands out.
    val topPickups = pickups.filter { it.entryPackage == top.key }
    val peakEntry = topPickups
        .groupingBy { Instant.ofEpochMilli(it.startMs).atZone(zone).hour }
        .eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
        .first()
    val peakField = if (peakEntry.value.toDouble() / topPickups.size >= 0.25) {
        ""","peak_hour":${peakEntry.key}"""
    } else {
        ""
    }

    return newFinding(
        detectionType = "ENTRY_APP",
        headline = "What you open first",
        body = "When you pick up your phone, $appName is the first app you open $sharePct% of " +
            "the time (${top.value} of ${pickups.size} pickups in $windowDays days)." +
            runnerUpText +
            if (blockable) " A short block on it removes the easiest first tap." else "",
        evidenceLine = "${top.value} of ${pickups.size} pickups",
        fingerprint = evidenceFingerprint(
            detectionType = "ENTRY_APP",
            subjectPackage = top.key,
            sampleSize = pickups.size,
            keyMetric = share * 100,
            metricUnit = 5.0,
        ),
        evidenceJson = """{"pickups":${pickups.size},"entry_count":${top.value},"share_pct":$sharePct,"days":${distinctDays(pickups, zone)},"blockable":$blockable$peakField}""",
    ).copy(subjectPackage = top.key, subjectAppName = appName)
}

/** Many very short visits to the phone in a day. */
fun detectQuickCheckHabit(
    sessions: List<AppSessionEntity>,
    windowDays: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): FindingEntity? {
    if (windowDays <= 0) return null
    val pickups = buildPickups(sessions)
    val days = distinctDays(pickups, zone)
    if (pickups.isEmpty() || days < MIN_DAYS) return null

    val perDay = pickups.size.toDouble() / windowDays
    val briefShare = pickups.count { it.foregroundMs < BRIEF_CHECK_MS }.toDouble() / pickups.size
    if (perDay < CHECKS_MIN_PER_DAY || briefShare < CHECKS_MIN_BRIEF_SHARE) return null

    val perDayRounded = round(perDay).toInt()
    val briefPct = round(briefShare * 100).toInt()
    return newFinding(
        detectionType = "QUICK_CHECK_HABIT",
        headline = "Short, frequent checks",
        body = "You check your phone about $perDayRounded times a day, and $briefPct% of those " +
            "visits last under 30 seconds. Short, frequent checks are often habit rather than " +
            "need. Blocking or limiting the app you open first is the most direct way to cut them.",
        evidenceLine = "About $perDayRounded checks a day over $days days",
        fingerprint = evidenceFingerprint(
            detectionType = "QUICK_CHECK_HABIT",
            subjectPackage = null,
            sampleSize = pickups.size,
            keyMetric = perDay,
            metricUnit = 10.0,
        ),
        evidenceJson = """{"checks_per_day":$perDayRounded,"brief_pct":$briefPct,"days":$days,"window_days":$windowDays}""",
    )
}

/** An attention-capture app that is reliably followed, within seconds, by another one. */
fun detectAppHopping(
    sessions: List<AppSessionEntity>,
    categories: Map<String, String?>,
    windowDays: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): FindingEntity? {
    val feed = sessions
        .filter { DetectorEligibility.isEngagementDesigned(it.packageName, categories[it.packageName]) }
        .sortedBy { it.startedAt }
    if (feed.size < 2) return null

    val endings = feed.groupingBy { it.packageName }.eachCount()
    val names = feed.associate { it.packageName to it.appName }
    val pairDays = mutableMapOf<Pair<String, String>, MutableSet<java.time.LocalDate>>()
    val pairCounts = mutableMapOf<Pair<String, String>, Int>()
    for (i in 0 until feed.size - 1) {
        val from = feed[i]
        val to = feed[i + 1]
        if (from.packageName == to.packageName) continue
        if (to.startedAt - from.endedAt > HOP_GAP_MS) continue
        val key = from.packageName to to.packageName
        pairCounts[key] = (pairCounts[key] ?: 0) + 1
        pairDays.getOrPut(key) { mutableSetOf() }
            .add(Instant.ofEpochMilli(from.startedAt).atZone(zone).toLocalDate())
    }

    val best = pairCounts.entries
        .filter { (key, count) ->
            count >= HOP_MIN_COUNT &&
                (pairDays[key]?.size ?: 0) >= HOP_MIN_DAYS &&
                count.toDouble() / (endings[key.first] ?: Int.MAX_VALUE) >= HOP_MIN_SHARE
        }
        .sortedWith(compareByDescending<Map.Entry<Pair<String, String>, Int>> { it.value }.thenBy { it.key.first })
        .firstOrNull() ?: return null

    val (fromPkg, toPkg) = best.key
    val fromName = names[fromPkg] ?: fromPkg
    val toName = names[toPkg] ?: toPkg
    val sharePct = round(best.value * 100.0 / (endings[fromPkg] ?: 1)).toInt()
    val days = pairDays[best.key]?.size ?: 0
    return newFinding(
        detectionType = "APP_HOPPING",
        headline = "One feed leads to the next",
        body = "After $fromName you open $toName within seconds $sharePct% of the time " +
            "(${best.value} times in $windowDays days). Closing one feed often just opens " +
            "another, so blocking only one of them may not be enough.",
        evidenceLine = "${best.value} hops on $days days",
        fingerprint = evidenceFingerprint(
            detectionType = "APP_HOPPING",
            subjectPackage = fromPkg,
            sampleSize = best.value,
            keyMetric = sharePct.toDouble(),
            metricUnit = 10.0,
        ),
        evidenceJson = """{"count":${best.value},"share_pct":$sharePct,"days":$days,"to_package":"$toPkg"}""",
    ).copy(subjectPackage = fromPkg, subjectAppName = fromName)
}

/** The hour of day at which blocked-app attempts cluster. */
fun detectBlockedAttemptPeak(
    entries: List<TemptationEntry>,
    nowMs: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    windowDays: Int = 28,
): FindingEntity? {
    val since = nowMs - windowDays * MS_PER_DAY
    val recent = entries.filter { it.timestamp in since..nowMs }
    if (recent.size < ATTEMPTS_MIN_TOTAL) return null

    val byHour = recent.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).hour }
    val peak = byHour.entries
        .sortedWith(compareByDescending<Map.Entry<Int, List<TemptationEntry>>> { it.value.size }.thenBy { it.key })
        .first()
    val peakHour = peak.key
    val atPeak = peak.value
    val share = atPeak.size.toDouble() / recent.size
    val days = atPeak.map { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }.toSet().size
    if (atPeak.size < ATTEMPTS_MIN_AT_PEAK || share < ATTEMPTS_MIN_PEAK_SHARE || days < ATTEMPTS_MIN_DAYS) {
        return null
    }

    val topPackage = atPeak.groupingBy { it.pkg }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .first().key
    val topName = atPeak.firstOrNull { it.pkg == topPackage && !it.appName.isNullOrBlank() }?.appName
        ?: topPackage
    return newFinding(
        detectionType = "TEMPTATION_PEAK",
        headline = "Your hardest hour",
        body = "${atPeak.size} of your ${recent.size} blocked-app attempts in the last " +
            "$windowDays days happened around ${clockHour(peakHour)}, on $days different days. " +
            "Most were $topName. A block that starts just before that hour makes the urge " +
            "easier to pass.",
        evidenceLine = "${atPeak.size} attempts around ${clockHour(peakHour)}",
        fingerprint = evidenceFingerprint(
            detectionType = "TEMPTATION_PEAK",
            subjectPackage = topPackage,
            sampleSize = atPeak.size,
            keyMetric = peakHour.toDouble(),
            metricUnit = 1.0,
        ),
        evidenceJson = """{"attempts":${atPeak.size},"total":${recent.size},"days":$days,"peak_hour":$peakHour}""",
    ).copy(subjectPackage = topPackage, subjectAppName = topName)
}

private fun clockHour(hour: Int): String {
    val twelve = if (hour % 12 == 0) 12 else hour % 12
    return "$twelve ${if (hour < 12) "am" else "pm"}"
}
