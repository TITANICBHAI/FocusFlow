package com.tbtechs.focusflow.analytics.phone

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pure English formatting helpers. The single place that turns numbers into text for people. */
object PhoneText {
    private val WEEKDAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)

    /** <60 s → "45s"; <1 h → "37m" (rounded to the nearest minute); else "4h 12m", or "4h" when the minutes are 0. */
    fun duration(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        if (safe < 60_000L) return "${safe / 1_000L}s"
        val totalMinutes = (safe + 30_000L) / 60_000L
        if (totalMinutes < 60L) return "${totalMinutes}m"
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return if (minutes == 0L) "${hours}h" else "${hours}h ${minutes}m"
    }

    /** "35%". */
    fun percent(value: Int): String = "$value%"

    /** "+35%", "−12%" (U+2212), "0%". */
    fun signedPercent(value: Int): String = when {
        value > 0 -> "+$value%"
        value < 0 -> "−${-value}%"
        else -> "0%"
    }

    /** "Sat, Oct 18". */
    fun weekdayDate(date: LocalDate): String = WEEKDAY_DATE.format(date)

    /** 0 → "12 AM", 9 → "9 AM", 12 → "12 PM", 21 → "9 PM". */
    fun hour(hour: Int): String {
        val h = Math.floorMod(hour, 24)
        val twelve = if (h % 12 == 0) 12 else h % 12
        return "$twelve ${if (h < 12) "AM" else "PM"}"
    }

    /** 495 → "8:15 AM", 0 → "12:00 AM". */
    fun clock(minuteOfDay: Int): String {
        val m = Math.floorMod(minuteOfDay, 24 * 60)
        val h = m / 60
        val minute = m % 60
        val twelve = if (h % 12 == 0) 12 else h % 12
        return "$twelve:${minute.toString().padStart(2, '0')} ${if (h < 12) "AM" else "PM"}"
    }

    /** "1 unlock", "63 unlocks". */
    fun count(n: Int, singular: String, plural: String = singular + "s"): String =
        "$n ${if (n == 1) singular else plural}"
}
