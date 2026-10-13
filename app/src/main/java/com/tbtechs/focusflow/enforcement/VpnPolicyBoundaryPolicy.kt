package com.tbtechs.focusflow.enforcement

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Calendar
import java.util.TimeZone

internal data class VpnScheduleWindow(
    val packages: List<String>,
    val daysOfWeek: List<Int>,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val enabled: Boolean = true,
    val vpnEnabled: Boolean = false,
)

/**
 * Shared local-time interpretation for accessibility enforcement and VPN
 * schedule boundaries. Weekdays use Calendar's 1=Sunday ... 7=Saturday values.
 */
internal object GreyoutWindowMath {
    fun isActive(
        daysOfWeek: List<Int>,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        atMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Boolean {
        if (!hasValidWindow(daysOfWeek, startMinuteOfDay, endMinuteOfDay)) return false

        val now = Calendar.getInstance(timeZone).apply { timeInMillis = atMs }
        val currentDay = now.get(Calendar.DAY_OF_WEEK)
        val currentMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val overnight = startMinuteOfDay > endMinuteOfDay
        val dayForWindow = if (overnight && currentMinute < endMinuteOfDay) {
            if (currentDay == Calendar.SUNDAY) Calendar.SATURDAY else currentDay - 1
        } else {
            currentDay
        }
        if (dayForWindow !in daysOfWeek) return false

        return if (overnight) {
            currentMinute >= startMinuteOfDay || currentMinute < endMinuteOfDay
        } else {
            currentMinute in startMinuteOfDay until endMinuteOfDay
        }
    }

    fun nextBoundaryAfter(
        daysOfWeek: List<Int>,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        afterMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Long? {
        if (!hasValidWindow(daysOfWeek, startMinuteOfDay, endMinuteOfDay) ||
            startMinuteOfDay == endMinuteOfDay
        ) {
            return null
        }

        val zone = timeZone.toZoneId()
        val rules = zone.rules
        val today = Instant.ofEpochMilli(afterMs).atZone(zone).toLocalDate()
        var next: Long? = null

        // Include yesterday so an overnight window's end can be found after
        // midnight, and eight future days so every weekday can be considered.
        for (dayOffset in -1L..8L) {
            val date = today.plusDays(dayOffset)
            // Calendar.SUNDAY == 1 ... SATURDAY == 7; java.time Monday == 1 ... Sunday == 7.
            val calendarDay = date.dayOfWeek.value % 7 + 1
            if (calendarDay !in daysOfWeek) continue

            val startLocal = LocalDateTime.of(
                date,
                LocalTime.of(startMinuteOfDay / 60, startMinuteOfDay % 60),
            )
            val endAtNextMidnight = endMinuteOfDay == 24 * 60
            val endDate = if (endAtNextMidnight || endMinuteOfDay <= startMinuteOfDay) {
                date.plusDays(1)
            } else {
                date
            }
            val endMinuteOnDate = if (endAtNextMidnight) 0 else endMinuteOfDay
            val endLocal = LocalDateTime.of(
                endDate,
                LocalTime.of(endMinuteOnDate / 60, endMinuteOnDate % 60),
            )

            for (localBoundary in listOf(startLocal, endLocal)) {
                val offsets = rules.getValidOffsets(localBoundary)
                val instants = if (offsets.isEmpty()) {
                    // A wall-clock boundary inside a spring-forward gap first takes effect
                    // when the clock jumps across the gap.
                    listOfNotNull(rules.getTransition(localBoundary)?.instant?.toEpochMilli())
                } else {
                    // Include both instants when the local time repeats during fall-back.
                    offsets.map { localBoundary.toInstant(it).toEpochMilli() }
                }
                for (boundary in instants) {
                    if (boundary > afterMs && (next == null || boundary < next!!)) {
                        next = boundary
                    }
                }
            }
        }
        return next
    }

    private fun hasValidWindow(
        daysOfWeek: List<Int>,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
    ): Boolean =
        daysOfWeek.any { it in Calendar.SUNDAY..Calendar.SATURDAY } &&
            startMinuteOfDay in 0 until 24 * 60 &&
            endMinuteOfDay in 0..24 * 60
}

internal object VpnPolicyBoundaryPolicy {
    fun nextZoneTransitionMs(timeZone: TimeZone, afterMs: Long): Long? =
        timeZone.toZoneId().rules
            .nextTransition(Instant.ofEpochMilli(afterMs))
            ?.instant?.toEpochMilli()

    fun isStandaloneActive(active: Boolean, untilMs: Long, nowMs: Long): Boolean =
        active && (untilMs <= 0L || nowMs < untilMs)

    fun standaloneTargets(
        packages: List<String>,
        active: Boolean,
        untilMs: Long,
        nowMs: Long,
    ): List<String> =
        if (isStandaloneActive(active, untilMs, nowMs)) packages.filter(String::isNotBlank).distinct()
        else emptyList()

    fun scheduleTargets(
        windows: List<VpnScheduleWindow>,
        nowMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): List<String> =
        windows.asSequence()
            .filter { it.enabled && it.vpnEnabled && it.packages.isNotEmpty() }
            .filter {
                GreyoutWindowMath.isActive(
                    daysOfWeek = it.daysOfWeek,
                    startMinuteOfDay = it.startMinuteOfDay,
                    endMinuteOfDay = it.endMinuteOfDay,
                    atMs = nowMs,
                    timeZone = timeZone,
                )
            }
            .flatMap { it.packages.asSequence() }
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
            .toList()

    fun nextBoundaryMs(
        activeStandalone: Boolean,
        standaloneUntilMs: Long,
        windows: List<VpnScheduleWindow>,
        nowMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Long? {
        val boundaries = buildList {
            if (isStandaloneActive(activeStandalone, standaloneUntilMs, nowMs) &&
                standaloneUntilMs > nowMs
            ) {
                add(standaloneUntilMs)
            }
            val vpnWindows = windows.asSequence()
                .filter { it.enabled && it.vpnEnabled && it.packages.isNotEmpty() }
                .toList()
            val scheduleBoundaries = vpnWindows.asSequence()
                .mapNotNull {
                    GreyoutWindowMath.nextBoundaryAfter(
                        daysOfWeek = it.daysOfWeek,
                        startMinuteOfDay = it.startMinuteOfDay,
                        endMinuteOfDay = it.endMinuteOfDay,
                        afterMs = nowMs,
                        timeZone = timeZone,
                    )
                }
                .toList()
            addAll(scheduleBoundaries)
            if (scheduleBoundaries.isNotEmpty()) {
                // The wall-clock meaning of recurring windows can change when the zone offset
                // changes, even when no configured edge falls at that exact time.
                nextZoneTransitionMs(timeZone, nowMs)?.let(::add)
            }
        }
        return boundaries.minOrNull()
    }
}
