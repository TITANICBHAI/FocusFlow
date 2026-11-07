package com.tbtechs.focusflow.analytics.phone

import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** Builds [PhoneBehaviourSection] (wakes, unlocks, sessions, first apps). Pure: no clock, no Android. */
object PhoneBehaviourBuilder {
    private const val MAX_FIRST_APPS = 8
    private const val MIN_MEDIAN_SAMPLES = 5

    fun build(
        range: PhoneDateRange,
        phoneDays: List<PhoneDay>,
        appNames: Map<String, String>,
        zone: ZoneId,
    ): PhoneBehaviourSection {
        val inRange = range.dates().map { it.toString() }.toSet()
        val measured = phoneDays.filter { it.date in inRange }
        val complete = measured.filter { it.status == PhoneDayStatus.COMPLETE }
        val live = measured.filter { it.status == PhoneDayStatus.LIVE }
        val baseline = if (complete.isNotEmpty()) complete else live

        val signal = measured.any { it.unlockSignal }
        val unlocks = measured.sumOf { it.unlockCount }
        val wakes = measured.sumOf { it.wakeCount }
        val unlocksPerDay = if (signal && baseline.isNotEmpty()) baseline.sumOf { it.unlockCount }.toDouble() / baseline.size else null
        val wakesPerDay = if (baseline.isNotEmpty()) baseline.sumOf { it.wakeCount }.toDouble() / baseline.size else null

        val gapCount = measured.sumOf { it.unlockGapCount }
        val avgGap = if (gapCount > 0) measured.sumOf { it.unlockGapSumMs } / gapCount else null

        val sessions = measured.sumOf { it.sessionCount }
        val bucketCounts = BuilderUtils.sumInts(measured.map { it.sessionBucketCounts }, PhoneBuckets.COUNT)
        val bucketMs = BuilderUtils.sumLongs(measured.map { it.sessionBucketMs }, PhoneBuckets.COUNT)
        val quick = bucketCounts[0] + bucketCounts[1]

        val signalDays = maxOf(1, measured.count { it.unlockSignal })
        val firstAppTotals = HashMap<String, Int>()
        for (day in measured) for ((pkg, n) in day.firstApps) firstAppTotals[pkg] = (firstAppTotals[pkg] ?: 0) + n
        val firstApps = firstAppTotals.map { (pkg, n) ->
            FirstAppRow(
                packageName = pkg,
                appName = appNames[pkg] ?: pkg,
                unlocks = n,
                shareOfUnlocksPercent = if (unlocks == 0) 0.0 else n * 100.0 / unlocks,
                perDay = n.toDouble() / signalDays,
            )
        }.sortedWith(compareByDescending<FirstAppRow> { it.unlocks }.thenBy { it.appName }).take(MAX_FIRST_APPS)

        val minutes = complete.mapNotNull { it.firstUnlockAtMs }.map { minuteOfDay(it, zone) }.sorted()
        val median = if (minutes.size < MIN_MEDIAN_SAMPLES) null else {
            val mid = minutes.size / 2
            if (minutes.size % 2 == 1) minutes[mid] else ((minutes[mid - 1] + minutes[mid]) / 2.0).roundToInt()
        }

        return PhoneBehaviourSection(
            daysWithData = measured.size,
            unlockSignalAvailable = signal,
            unlocks = unlocks,
            unlocksPerDay = unlocksPerDay,
            wakes = wakes,
            wakesPerDay = wakesPerDay,
            avgGapBetweenUnlocksMs = avgGap,
            sessionBasis = if (signal) SessionBasis.UNLOCK else SessionBasis.SCREEN,
            sessions = sessions,
            sessionBucketCounts = bucketCounts,
            sessionBucketMs = bucketMs,
            quickChecks = quick,
            quickCheckSharePercent = if (sessions == 0) null else (quick * 100.0 / sessions).roundToInt(),
            longestSessionMs = measured.maxOfOrNull { it.longestSessionMs } ?: 0L,
            unlocksByHour = BuilderUtils.sumInts(measured.map { it.unlockHourly }, 24),
            wakesByHour = BuilderUtils.sumInts(measured.map { it.wakeHourly }, 24),
            firstApps = firstApps,
            unlocksWithoutApp = measured.sumOf { it.unlocksWithoutApp },
            firstUnlockMedianMinuteOfDay = median,
        )
    }

    private fun minuteOfDay(ms: Long, zone: ZoneId): Int {
        val t = Instant.ofEpochMilli(ms).atZone(zone)
        return t.hour * 60 + t.minute
    }
}
