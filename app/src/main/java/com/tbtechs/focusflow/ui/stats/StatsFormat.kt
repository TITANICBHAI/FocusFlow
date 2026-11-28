package com.tbtechs.focusflow.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.tbtechs.focusflow.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The language the app is showing right now (follows the per-app language setting). */
@Composable
internal fun statsLocale(): Locale = LocalConfiguration.current.locales[0]

/** Which string a duration uses: only minutes, only hours, or both. Kept apart from the text so it can be unit tested. */
internal enum class DurationShape { MINUTES, HOURS, HOURS_MINUTES }

internal fun durationShape(minutes: Int): DurationShape = when {
    minutes < 60 -> DurationShape.MINUTES
    minutes % 60 == 0 -> DurationShape.HOURS
    else -> DurationShape.HOURS_MINUTES
}

/** 45 -> "45m", 120 -> "2h", 135 -> "2h 15m", written the way the current language writes it. */
@Composable
internal fun statsDuration(minutes: Int): String = when (durationShape(minutes)) {
    DurationShape.MINUTES -> stringResource(R.string.stats_dur_m, minutes)
    DurationShape.HOURS -> stringResource(R.string.stats_dur_h, minutes / 60)
    DurationShape.HOURS_MINUTES -> stringResource(R.string.stats_dur_hm, minutes / 60, minutes % 60)
}

/** 14 -> "2:00 PM" in English, "14:00" in German; null when the hour is missing or out of range. */
@Composable
internal fun statsHourLabel(hour: Int?): String? {
    if (hour == null || hour !in 0..23) return null
    return LocalTime.of(hour, 0).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(statsLocale()))
}

/** Formats [date] with the language's own pattern, e.g. R.string.stats_date_pattern_month_day. */
@Composable
internal fun statsDate(date: LocalDate, patternRes: Int): String =
    date.format(DateTimeFormatter.ofPattern(stringResource(patternRes), statsLocale()))
