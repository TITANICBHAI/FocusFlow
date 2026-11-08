package com.tbtechs.focusflow.ui.stats.phone

import com.tbtechs.focusflow.analytics.phone.FocusBlockingSection
import com.tbtechs.focusflow.analytics.phone.PhoneText
import com.tbtechs.focusflow.analytics.phone.TrendDirection
import kotlin.math.roundToInt

/** Pure wording and chart data for the Extra · Focus tab. */
internal const val FOCUS_WEEK_BARS = 8

/** Held sessions as a share of ended sessions (held + interrupted), rounded; `null` when there are none. */
internal fun heldRatePercent(f: FocusBlockingSection): Int? {
    val ended = f.heldSessions + f.interruptedSessions
    if (ended <= 0) return null
    return (f.heldSessions * 100.0 / ended).roundToInt()
}

internal fun focusDurationLabels(): List<String> = listOf("<15m", "15–30m", "30–60m", "1–2h", "2h+")

internal fun blockedAppRows(f: FocusBlockingSection): List<TopAppRow> = f.blockedApps.map { app ->
    TopAppRow(
        title = app.appName,
        subtitle = PhoneText.count(app.attempts, "attempt"),
        fraction = (app.sharePercent / 100.0).toFloat().coerceIn(0f, 1f),
        trailing = PhoneText.percent(app.sharePercent.roundToInt()),
    )
}

/** Eight weekly bars of blocked attempts, oldest first: "−7w" … "−1w", "Now". Missing older weeks are empty and dimmed. */
internal fun weekBarPoints(f: FocusBlockingSection): List<DayBarPoint> {
    val weeks = f.weeks.takeLast(FOCUS_WEEK_BARS)
    val padding = FOCUS_WEEK_BARS - weeks.size
    return (0 until FOCUS_WEEK_BARS).map { i ->
        val week = if (i < padding) null else weeks[i - padding]
        DayBarPoint(
            label = if (i == FOCUS_WEEK_BARS - 1) "Now" else "−${FOCUS_WEEK_BARS - 1 - i}w",
            value = (week?.attempts ?: 0).toFloat(),
            dimmed = week == null,
            highlighted = i == FOCUS_WEEK_BARS - 1,
        )
    }
}

internal fun focusTrendText(f: FocusBlockingSection): String {
    val trend = f.trend
        ?: return "Not enough focus time yet — this needs about an hour of focus in each two-week period."
    val before = oneDecimal(trend.attemptsPerFocusHourBefore)
    val now = oneDecimal(trend.attemptsPerFocusHourNow)
    val change = PhoneText.signedPercent(trend.changePercent)
    return when (trend.direction) {
        TrendDirection.DOWN -> "Blocked attempts per focus hour fell from $before to $now ($change) over the last two weeks."
        TrendDirection.UP -> "Blocked attempts per focus hour rose from $before to $now ($change) over the last two weeks."
        TrendDirection.FLAT -> "Blocked attempts per focus hour stayed about the same ($before → $now)."
    }
}

internal fun focusSummaryLine(f: FocusBlockingSection): String =
    "${PhoneText.count(f.sessions, "session")} · ${PhoneText.duration(f.focusMs)} of focus"
