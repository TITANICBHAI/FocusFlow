package com.tbtechs.focusflow.analytics.phone.insights

import com.tbtechs.focusflow.analytics.phone.AppDayRow
import com.tbtechs.focusflow.analytics.phone.AppSessionRow
import com.tbtechs.focusflow.analytics.phone.BlockedDayRow
import com.tbtechs.focusflow.analytics.phone.FocusSessionRow
import com.tbtechs.focusflow.analytics.phone.PhoneDay
import com.tbtechs.focusflow.analytics.phone.PhoneDayStatus
import com.tbtechs.focusflow.analytics.phone.PhoneTestFixtures
import com.tbtechs.focusflow.analytics.phone.UsageCategory
import java.time.LocalDate
import java.time.ZoneId

/** Builders for insight-engine tests. `today` is Fri 2026-11-06; windows are "days back from today". */
object InsightFixtures {
    const val MIN = 60_000L
    const val HOUR = 60 * MIN
    val today: LocalDate = LocalDate.of(2026, 11, 6)
    val utc: ZoneId = ZoneId.of("UTC")

    fun ago(n: Int): LocalDate = today.minusDays(n.toLong())
    fun startMs(date: LocalDate): Long = date.atStartOfDay(utc).toInstant().toEpochMilli()

    fun input(
        days: List<PhoneDay> = emptyList(),
        appDays: List<AppDayRow> = emptyList(),
        appSessions: List<AppSessionRow> = emptyList(),
        blocked: List<BlockedDayRow> = emptyList(),
        focus: List<FocusSessionRow> = emptyList(),
        names: Map<String, String> = emptyMap(),
    ) = PhoneInsightInput(
        today = today, days = days, appDays = appDays, appSessions = appSessions, blockedDays = blocked,
        focusSessions = focus, appNames = names, categoryOf = { UsageCategory.OTHER }, zone = utc,
    )

    fun gen(input: PhoneInsightInput): List<PhoneInsight> = PhoneInsightEngine().generate(input)
    fun List<PhoneInsight>.ofKind(kind: InsightKind) = filter { it.kind == kind }
    fun List<PhoneInsight>.single(kind: InsightKind): PhoneInsight = ofKind(kind).single()

    /** One day per date from `fromBack` days ago to `toBack` days ago (inclusive), oldest first. */
    fun span(fromBack: Int, toBack: Int, make: (LocalDate) -> PhoneDay): List<PhoneDay> =
        (fromBack downTo toBack).map { make(ago(it)) }

    fun complete(date: LocalDate, screenMin: Int = 100, unlocks: Int = 0, hourlyMs: List<Long>? = null, wakes: Int = 0) =
        PhoneTestFixtures.day(date, PhoneDayStatus.COMPLETE, screenOnMin = screenMin, unlocks = unlocks, hourlyMs = hourlyMs, wakes = wakes)

    fun completeSpan(fromBack: Int, toBack: Int, screenMin: Int = 100, unlocks: Int = 0) =
        span(fromBack, toBack) { complete(it, screenMin, unlocks) }

    fun appSpan(fromBack: Int, toBack: Int, pkg: String, minutes: Int, name: String = pkg, hour: Int = 12): List<AppDayRow> =
        (fromBack downTo toBack).map { PhoneTestFixtures.app(ago(it), pkg, minutes * MIN, name, hour = hour) }

    /** 24 hourly values: `lateMin` minutes at 11 PM and `otherMin` minutes at noon. */
    fun hourly(lateMin: Int, otherMin: Int): List<Long> =
        List(24) { when (it) { 23 -> lateMin * MIN; 12 -> otherMin * MIN; else -> 0L } }

    fun focusSession(date: LocalDate, startHour: Int, minutes: Int, overrides: Int = 0): FocusSessionRow {
        val start = startMs(date) + startHour * HOUR
        return FocusSessionRow(date.toString(), start, start + minutes * MIN, overrides)
    }

    fun blocked(date: LocalDate, pkg: String, attempts: Int) =
        BlockedDayRow(date.toString(), pkg, pkg, attempts, List(24) { if (it == 10) attempts else 0 })
}
