package com.tbtechs.focusflow.ui.stats.phone

import com.tbtechs.focusflow.analytics.phone.DistractionApp
import com.tbtechs.focusflow.analytics.phone.DistractionSection
import com.tbtechs.focusflow.analytics.phone.FirstAppRow
import com.tbtechs.focusflow.analytics.phone.PhoneBehaviourSection
import com.tbtechs.focusflow.analytics.phone.PhoneText
import com.tbtechs.focusflow.analytics.phone.ReflexApp
import com.tbtechs.focusflow.analytics.phone.RisingApp
import com.tbtechs.focusflow.analytics.phone.SessionBasis
import java.util.Locale
import kotlin.math.roundToInt

/** Pure wording for the Extra · Phone and Extra · Distraction tabs. */
internal fun oneDecimal(value: Double): String = String.format(Locale.ENGLISH, "%.1f", value)

/** Index of the largest value (earliest on ties), or `null` when nothing is above zero. */
internal fun busiestHour(byHour: List<Int>): Int? {
    var best = -1
    var bestValue = 0
    byHour.forEachIndexed { hour, v -> if (v > bestValue) { best = hour; bestValue = v } }
    return best.takeIf { it >= 0 }
}

internal fun unlockHourCaption(b: PhoneBehaviourSection, showWakes: Boolean): String {
    val total: Int
    val perDay: Double?
    val noun: String
    val byHour: List<Int>
    if (showWakes) {
        total = b.wakes; perDay = b.wakesPerDay; noun = "wake-up"; byHour = b.wakesByHour
    } else {
        total = b.unlocks; perDay = b.unlocksPerDay; noun = "unlock"; byHour = b.unlocksByHour
    }
    val perDayText = perDay?.roundToInt()?.toString() ?: "—"
    val base = "${PhoneText.count(total, noun)} in total · $perDayText a day"
    val peak = busiestHour(byHour) ?: return base
    return "$base · busiest around ${PhoneText.hour(peak)}"
}

internal fun sessionHeadline(b: PhoneBehaviourSection): String {
    if (b.sessions <= 0) return "No sessions recorded"
    val share = b.quickCheckSharePercent ?: (b.quickChecks * 100.0 / b.sessions).roundToInt()
    return "${PhoneText.count(b.quickChecks, "quick check")} — $share% of ${PhoneText.count(b.sessions, "session")}"
}

internal fun sessionBasisNote(b: PhoneBehaviourSection): String = when (b.sessionBasis) {
    SessionBasis.UNLOCK -> "Sessions run from unlock to screen-off."
    SessionBasis.SCREEN -> "This phone doesn't report unlocks, so sessions are counted from screen-on to screen-off."
}

internal fun firstAppSubtitle(row: FirstAppRow): String =
    "${PhoneText.count(row.unlocks, "unlock")} · ${oneDecimal(row.perDay)} a day"

internal fun noAppRow(b: PhoneBehaviourSection): TopAppRow? {
    if (b.unlocksWithoutApp <= 0 || b.unlocks <= 0) return null
    val share = b.unlocksWithoutApp * 100.0 / b.unlocks
    return TopAppRow(
        title = "Nothing opened",
        subtitle = "checked the time or notifications",
        fraction = (share / 100.0).toFloat().coerceIn(0f, 1f),
        trailing = PhoneText.percent(share.roundToInt()),
    )
}

internal fun distractionHeadline(d: DistractionSection): String {
    val duration = PhoneText.duration(d.distractionMs)
    return d.sharePercent?.let { "$duration — ${PhoneText.percent(it)} of your app time" } ?: duration
}

internal fun distractionSubtitle(app: DistractionApp): String = app.reasons.joinToString(" · ")

internal fun reflexSubtitle(app: ReflexApp): String =
    "${PhoneText.count(app.opensPerActiveDay.roundToInt(), "open")} a day · ${app.shortSessionSharePercent}% under 30 s"

internal fun risingSubtitle(app: RisingApp): String =
    "${PhoneText.duration(app.currentAvgMs)}/day vs ${PhoneText.duration(app.previousAvgMs)}/day (${PhoneText.signedPercent(app.changePercent)})"

internal fun distractionChangeText(d: DistractionSection): String? =
    d.changePercent?.let { "${PhoneText.signedPercent(it)} vs the previous period" }
