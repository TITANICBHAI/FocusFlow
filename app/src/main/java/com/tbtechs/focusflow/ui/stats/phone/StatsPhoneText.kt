package com.tbtechs.focusflow.ui.stats.phone

import com.tbtechs.focusflow.analytics.ANALYTICS_TODAY
import com.tbtechs.focusflow.analytics.ANALYTICS_YESTERDAY
import com.tbtechs.focusflow.analytics.phone.AppsSection
import com.tbtechs.focusflow.analytics.phone.AverageBasis
import com.tbtechs.focusflow.analytics.phone.PhoneBehaviourSection
import com.tbtechs.focusflow.analytics.phone.PhoneCoverage
import com.tbtechs.focusflow.analytics.phone.PhoneText
import com.tbtechs.focusflow.analytics.phone.ScreenTimeSection
import kotlin.math.roundToInt

/** Wording for the phone cards on the Stats screen. Pure; every number goes through [PhoneText]. */
internal data class HeroText(val value: String, val caption: String)

internal fun isSingleDay(window: String): Boolean = window == ANALYTICS_TODAY || window == ANALYTICS_YESTERDAY

private const val NO_VALUE = "—"

internal fun screenTimeHero(s: ScreenTimeSection, window: String): HeroText {
    if (isSingleDay(window)) {
        return HeroText(
            PhoneText.duration(s.totalScreenOnMs),
            if (window == ANALYTICS_TODAY) "screen on today so far" else "screen on yesterday",
        )
    }
    val avg = s.avgScreenOnPerDayMs
    return when {
        avg == null || s.averageBasis == AverageBasis.NONE -> HeroText(NO_VALUE, "not enough complete days yet")
        s.averageBasis == AverageBasis.COMPLETE_DAYS ->
            HeroText(PhoneText.duration(avg), "a day on average · " + PhoneText.count(s.averageDays, "day"))
        else -> HeroText(PhoneText.duration(avg), "so far today")
    }
}

/** `null` means "no activity to show". */
internal fun unlocksHero(b: PhoneBehaviourSection, window: String): HeroText? {
    val single = isSingleDay(window)
    if (b.unlockSignalAvailable) {
        return if (single) {
            HeroText(b.unlocks.toString(), "unlocks")
        } else {
            HeroText(b.unlocksPerDay?.roundToInt()?.toString() ?: NO_VALUE, "unlocks a day")
        }
    }
    return if (single) {
        if (b.wakes >= 5) HeroText(b.wakes.toString(), "screen wake-ups") else null
    } else {
        val perDay = b.wakesPerDay ?: 0.0
        if (perDay >= 1.0) HeroText(perDay.roundToInt().toString(), "screen wake-ups a day") else null
    }
}

internal fun coverageNote(c: PhoneCoverage, window: String): String? {
    if (isSingleDay(window)) return null
    val n = c.partialDays + c.missingDays
    if (n == 0) return null
    return "${PhoneText.count(n, "day")} with partial or no data aren't averaged."
}

/** "In apps: …" under the hero, or `null` when there is nothing to say. */
internal fun inAppsText(s: ScreenTimeSection, window: String): String? =
    if (isSingleDay(window)) {
        s.totalAppMs.takeIf { it > 0L }?.let { "In apps: ${PhoneText.duration(it)}" }
    } else {
        s.avgAppPerDayMs?.takeIf { it > 0L }?.let { "In apps: ${PhoneText.duration(it)} a day" }
    }

internal fun weekdayWeekendNote(s: ScreenTimeSection): String? {
    val weekday = s.weekday ?: return null
    val weekend = s.weekend ?: return null
    return "Weekdays ${PhoneText.duration(weekday.avgMs)} · Weekends ${PhoneText.duration(weekend.avgMs)} a day " +
        "(${weekday.days} and ${weekend.days} days)"
}

internal fun wakeUpsStat(b: PhoneBehaviourSection, window: String): String =
    if (isSingleDay(window)) b.wakes.toString() else b.wakesPerDay?.let { "${it.roundToInt()}/day" } ?: NO_VALUE

internal fun gapStat(b: PhoneBehaviourSection): String = b.avgGapBetweenUnlocksMs?.let { PhoneText.duration(it) } ?: NO_VALUE

internal fun quickChecksStat(b: PhoneBehaviourSection): String = b.quickCheckSharePercent?.let { PhoneText.percent(it) } ?: NO_VALUE

/** Caption under the by-day chart; `null` when 7 days or fewer are shown. */
internal fun byDayCaption(dayCount: Int): String? = if (dayCount > 7) "Last ${minOf(dayCount, 30)} days" else null

internal fun busiestCaption(peakHour: Int?, window: String): String? =
    peakHour?.let { "Busiest around ${PhoneText.hour(it)}" + if (isSingleDay(window)) "" else " · average per day" }

internal fun appsFootnote(apps: AppsSection): String = "Share of time spent inside apps (${PhoneText.duration(apps.totalMs)} total)"

internal data class TopAppRow(val title: String, val subtitle: String?, val fraction: Float, val trailing: String)

internal fun topAppRows(apps: AppsSection, limit: Int = 6): List<TopAppRow> {
    val rows = apps.apps.take(limit).map { app ->
        TopAppRow(
            title = app.appName,
            subtitle = PhoneText.duration(app.totalMs) + " · " + PhoneText.count(app.opens, "open"),
            fraction = (app.sharePercent / 100.0).toFloat().coerceIn(0f, 1f),
            trailing = PhoneText.percent(app.sharePercent.roundToInt()),
        )
    }
    // Everything not listed above: the listed-but-cut apps plus the time in apps beyond the 30 the section keeps.
    var omitted = apps.apps.drop(limit).sumOf { it.sharePercent }
    if (apps.totalMs > 0L) omitted += apps.othersMs * 100.0 / apps.totalMs
    if (omitted < 0.5) return rows
    return rows + TopAppRow("Other apps", null, (omitted / 100.0).toFloat().coerceIn(0f, 1f), PhoneText.percent(omitted.roundToInt()))
}
