package com.tbtechs.focusflow.enforcement

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.TimeZone

/**
 * Words for "when does this allowance come back", for the interval mode.
 *
 * A daily allowance simply runs out and comes back tomorrow, so "time's up" says enough. An
 * interval allowance ("5 minutes every hour") comes back at a specific moment, which is the
 * thing the person actually wants to know. That moment is the start of the current window plus
 * the window length, both already kept by the allowance ledger.
 *
 * Pure and Android-free: the caller passes in how to print a clock time, so it follows the
 * phone's 12 or 24 hour setting and can be unit tested.
 */
object AllowanceResetText {

    /** The moment the current window ends, or 0 when the window has not started or is unknown. */
    fun resetAtMs(windowStartMs: Long, windowMs: Long): Long =
        if (windowStartMs <= 0L || windowMs <= 0L) 0L else windowStartMs + windowMs

    /**
     * "back at 3:40 PM", "back tomorrow 8:10 AM" or "back Mon 8:10 AM". Null when the reset
     * moment is not known, so the caller can fall back to its plain wording.
     */
    fun backText(
        resetAtMs: Long,
        nowMs: Long,
        zone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault(),
        clockText: (Long) -> String,
    ): String? {
        if (resetAtMs <= 0L) return null
        val clock = clockText(resetAtMs)
        return when (dayGap(resetAtMs, nowMs, zone)) {
            in Int.MIN_VALUE..0 -> "back at $clock"
            1 -> "back tomorrow $clock"
            else -> "back ${weekday(resetAtMs, zone, locale)} $clock"
        }
    }

    /** Calendar days from [nowMs] to [resetAtMs] in [zone]: 0 same day, 1 tomorrow, and so on. */
    internal fun dayGap(resetAtMs: Long, nowMs: Long, zone: TimeZone): Int {
        val id = zone.toZoneId()
        val resetDay = Instant.ofEpochMilli(resetAtMs).atZone(id).toLocalDate()
        val today = Instant.ofEpochMilli(nowMs).atZone(id).toLocalDate()
        return ChronoUnit.DAYS.between(today, resetDay).toInt()
    }

    private fun weekday(ms: Long, zone: TimeZone, locale: Locale): String =
        DateTimeFormatter.ofPattern("EEE", locale)
            .format(Instant.ofEpochMilli(ms).atZone(ZoneId.of(zone.id)))
}
