package com.tbtechs.focusflow.analytics.phone

import com.tbtechs.focusflow.analytics.ForegroundEventType
import com.tbtechs.focusflow.analytics.ForegroundSession
import com.tbtechs.focusflow.analytics.ForegroundUsageEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure reducer: OS screen / keyguard events plus foreground sessions become one [PhoneDay].
 * No Android classes, no clock, no logging. Definitions are in plan §4.2–§4.12.
 */
class PhoneEventReducer(private val zone: ZoneId) {

    fun reduceDay(
        date: LocalDate,
        events: List<ForegroundUsageEvent>,          // may cover several days; need not be sorted
        foregroundSessions: List<ForegroundSession>, // from ForegroundSpanTracker for the same events
        nowMs: Long,
        status: PhoneDayStatus,
    ): PhoneDay {
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val limit = minOf(dayEnd, nowMs)
        if (limit <= dayStart) return PhoneDay.empty(date.toString(), status)

        // Stable sort: events with equal timestamps keep their given order.
        val ev = events
            .filter { it.type in PHONE_EVENT_TYPES && it.timestampMs <= nowMs }
            .sortedBy { it.timestampMs }

        var screenOn = false
        var screenOnSince = 0L
        var screenStartedBeforeDay = false
        var unlocked = false
        var unlockedSince = 0L
        var unlockCounted = false

        var screenOnMs = 0L
        val screenOnHourly = LongArray(24)
        var wakeCount = 0
        val wakeHourly = IntArray(24)
        var unlockCount = 0
        val unlockHourly = IntArray(24)
        val unlockTimes = ArrayList<Long>()
        val closedUnlockedSessions = ArrayList<Pair<Long, Long>>()
        val screenSpans = ArrayList<Pair<Long, Long>>()

        fun hourOf(ts: Long): Int = Instant.ofEpochMilli(ts).atZone(zone).hour

        fun addSpan(start: Long, end: Long, startedInDay: Boolean) {
            val s = maxOf(start, dayStart)
            val e = minOf(end, limit)
            if (e > s) {
                screenOnMs += e - s
                for (slice in HourSplitter.split(s, e, zone)) {
                    if (slice.date == date) screenOnHourly[slice.hour] += slice.ms
                }
            }
            if (startedInDay) screenSpans += start to end
        }

        fun closeScreen(end: Long) {
            if (screenOn) {
                addSpan(screenOnSince, end, startedInDay = !screenStartedBeforeDay)
                screenOn = false
            }
        }

        fun closeUnlocked(end: Long) {
            if (unlocked) {
                if (unlockCounted) closedUnlockedSessions += unlockedSince to end
                unlocked = false
                unlockCounted = false
            }
        }

        // Initial state: replay everything before the day without counting anything.
        for (e in ev) {
            if (e.timestampMs >= dayStart) break
            when (e.type) {
                ForegroundEventType.SCREEN_INTERACTIVE -> screenOn = true
                ForegroundEventType.SCREEN_NON_INTERACTIVE,
                ForegroundEventType.DEVICE_SHUTDOWN,
                ForegroundEventType.DEVICE_STARTUP -> { screenOn = false; unlocked = false }
                ForegroundEventType.KEYGUARD_HIDDEN -> { unlocked = true; screenOn = true }
                ForegroundEventType.KEYGUARD_SHOWN -> unlocked = false
                else -> Unit
            }
        }
        if (!screenOn) unlocked = false
        if (screenOn) {
            screenOnSince = dayStart
            screenStartedBeforeDay = true
        }
        if (unlocked) {
            unlockedSince = dayStart
            unlockCounted = false // it began on an earlier day, so it is not counted here
        }

        // Main loop: events inside [dayStart, limit).
        for (e in ev) {
            val ts = e.timestampMs
            if (ts < dayStart) continue
            if (ts >= limit) break
            when (e.type) {
                ForegroundEventType.SCREEN_INTERACTIVE -> {
                    if (!screenOn) {
                        screenOn = true
                        screenOnSince = ts
                        screenStartedBeforeDay = false
                        wakeCount++
                        wakeHourly[hourOf(ts)]++
                    }
                }
                ForegroundEventType.SCREEN_NON_INTERACTIVE,
                ForegroundEventType.DEVICE_SHUTDOWN -> {
                    closeScreen(ts)
                    closeUnlocked(ts)
                }
                ForegroundEventType.KEYGUARD_HIDDEN -> {
                    if (!screenOn) { // a hidden keyguard implies the screen is on
                        screenOn = true
                        screenOnSince = ts
                        screenStartedBeforeDay = false
                    }
                    if (!unlocked) {
                        unlocked = true
                        unlockedSince = ts
                        unlockCounted = true
                        unlockCount++
                        unlockHourly[hourOf(ts)]++
                        unlockTimes += ts
                    }
                }
                ForegroundEventType.KEYGUARD_SHOWN -> closeUnlocked(ts)
                ForegroundEventType.DEVICE_STARTUP -> { screenOn = false; unlocked = false }
                else -> Unit
            }
        }

        // State at `limit`: account for time still running, then resolve how the open things end.
        val openScreen = screenOn
        val openScreenStartedInDay = screenOn && !screenStartedBeforeDay
        if (openScreen) addSpan(screenOnSince, limit, startedInDay = false) // time only
        val openUnlocked = unlocked

        val tail = ev.filter { it.timestampMs >= limit } // all are <= nowMs already
        if (openUnlocked && unlockCounted) {
            val end = tail.firstOrNull {
                it.type == ForegroundEventType.SCREEN_NON_INTERACTIVE ||
                    it.type == ForegroundEventType.DEVICE_SHUTDOWN ||
                    it.type == ForegroundEventType.KEYGUARD_SHOWN
            }?.timestampMs ?: minOf(nowMs, unlockedSince + PhoneThresholds.OPEN_SESSION_CAP_MS)
            closedUnlockedSessions += unlockedSince to end
        }
        if (openScreen && openScreenStartedInDay) {
            val end = tail.firstOrNull {
                it.type == ForegroundEventType.SCREEN_NON_INTERACTIVE ||
                    it.type == ForegroundEventType.DEVICE_SHUTDOWN
            }?.timestampMs ?: minOf(nowMs, screenOnSince + PhoneThresholds.OPEN_SESSION_CAP_MS)
            screenSpans += screenOnSince to end
        }

        // Sessions.
        val basis = if (unlockCount > 0) SessionBasis.UNLOCK else SessionBasis.SCREEN
        val sessions = if (unlockCount > 0) closedUnlockedSessions else screenSpans
        var sessionCount = 0
        val bucketCounts = IntArray(PhoneBuckets.COUNT)
        val bucketMs = LongArray(PhoneBuckets.COUNT)
        var longest = 0L
        for ((start, end) in sessions) {
            val len = maxOf(0L, end - start)
            sessionCount++
            val index = PhoneBuckets.indexFor(len)
            bucketCounts[index]++
            bucketMs[index] += len
            longest = maxOf(longest, len)
        }

        // Gaps between consecutive unlocks.
        unlockTimes.sort()
        var gapSum = 0L
        var gapCount = 0
        for (i in 1 until unlockTimes.size) {
            val gap = unlockTimes[i] - unlockTimes[i - 1]
            if (gap <= PhoneThresholds.UNLOCK_GAP_CAP_MS) {
                gapSum += gap
                gapCount++
            }
        }

        // First app after each unlock.
        val firstApps = LinkedHashMap<String, Int>()
        var unlocksWithoutApp = 0
        for (u in unlockTimes) {
            val first = foregroundSessions
                .filter {
                    it.endedAtMs > u - PhoneThresholds.FIRST_APP_BEFORE_MS &&
                        it.startedAtMs <= u + PhoneThresholds.FIRST_APP_AFTER_MS
                }
                .minWithOrNull(compareBy<ForegroundSession> { it.startedAtMs }.thenBy { it.packageName })
            if (first != null) {
                firstApps[first.packageName] = (firstApps[first.packageName] ?: 0) + 1
            } else {
                unlocksWithoutApp++
            }
        }

        val firstUnlockAtMs = unlockTimes.firstOrNull() ?: screenSpans.minOfOrNull { it.first }

        return PhoneDay(
            date = date.toString(),
            status = status,
            screenOnMs = screenOnMs,
            screenOnHourlyMs = screenOnHourly.toList(),
            wakeCount = wakeCount,
            wakeHourly = wakeHourly.toList(),
            unlockCount = unlockCount,
            unlockHourly = unlockHourly.toList(),
            unlockSignal = unlockCount > 0,
            sessionBasis = basis,
            sessionCount = sessionCount,
            sessionBucketCounts = bucketCounts.toList(),
            sessionBucketMs = bucketMs.toList(),
            longestSessionMs = longest,
            firstUnlockAtMs = firstUnlockAtMs,
            unlockGapSumMs = gapSum,
            unlockGapCount = gapCount,
            firstApps = firstApps,
            unlocksWithoutApp = unlocksWithoutApp,
        )
    }

    private companion object {
        val PHONE_EVENT_TYPES = setOf(
            ForegroundEventType.SCREEN_INTERACTIVE,
            ForegroundEventType.SCREEN_NON_INTERACTIVE,
            ForegroundEventType.KEYGUARD_HIDDEN,
            ForegroundEventType.KEYGUARD_SHOWN,
            ForegroundEventType.DEVICE_SHUTDOWN,
            ForegroundEventType.DEVICE_STARTUP,
        )
    }
}
