package com.tbtechs.focusflow.analytics.phone

import java.time.LocalDate
import java.time.ZoneId

/** A real (empty-data) [PhoneReport] built through the production builders, for tests that only need "some report". */
object PhoneReportFixtures {
    fun emptyReport(date: LocalDate = LocalDate.of(2026, 11, 6), window: String = "today"): PhoneReport {
        val range = PhoneDateRange(date, date)
        val zone = ZoneId.of("UTC")
        val screenTime = ScreenTimeBuilder.build(range, emptyList(), emptyList())
        val apps = AppsBuilder.build(AppsInput(range, emptyList(), emptyList(), emptyMap(), null) { UsageCategory.OTHER })
        return PhoneReport(
            window = window, range = range, screenTime = screenTime, apps = apps,
            behaviour = PhoneBehaviourBuilder.build(range, emptyList(), emptyMap(), zone),
            distraction = DistractionBuilder.build(apps, emptyList(), emptyMap(), { UsageCategory.OTHER }, null),
            focus = FocusBlockingBuilder.build(range, date, 0L, zone, emptyList(), emptyList(), emptyList(), emptyList()),
            coverage = PhoneCoverage(0, 0, 0, 1, null, null),
        )
    }
}
