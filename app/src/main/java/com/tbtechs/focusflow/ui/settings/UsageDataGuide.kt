package com.tbtechs.focusflow.ui.settings

import com.tbtechs.focusflow.analytics.phone.PhoneBuckets
import com.tbtechs.focusflow.analytics.phone.PhoneThresholds
import com.tbtechs.focusflow.notifications.NudgeSchedule
import com.tbtechs.focusflow.notifications.PhoneNudgePlanner

/**
 * The one place that explains how phone-usage numbers are worked out. The Stats and Extra screens stay
 * free of "how this is calculated" text and point here instead. Pure data: every number below is read
 * from the same constants the calculations use, so this page cannot drift from the code.
 */
internal data class UsageGuideEntry(val question: String, val answer: String)

internal data class UsageGuideSection(val title: String, val entries: List<UsageGuideEntry>)

internal const val USAGE_GUIDE_TITLE = "How usage data is calculated"
internal const val USAGE_GUIDE_SUBTITLE = "Where the numbers on Stats and Extra come from"

private fun seconds(ms: Long) = "${ms / 1000} seconds"
private fun minutes(ms: Long) = "${ms / 60_000} minutes"
private fun hours(ms: Long) = "${ms / 3_600_000} hours"

internal fun usageDataGuide(): List<UsageGuideSection> = listOf(
    UsageGuideSection(
        "Where the data comes from",
        listOf(
            UsageGuideEntry(
                "What is it based on?",
                "Android's own usage records, which FocusFlow can read once you allow Usage Access. " +
                    "Everything is worked out on this phone. Nothing is uploaded.",
            ),
            UsageGuideEntry(
                "How far back does it go?",
                "The first time, FocusFlow reads up to ${PhoneThresholds.BACKFILL_DAYS} days of history, as much as Android still keeps. " +
                    "After that each finished day is saved as a small daily summary, kept for up to ${PhoneThresholds.DAY_ROW_RETENTION_DAYS} days.",
            ),
            UsageGuideEntry(
                "Why is today different from other days?",
                "Today is read live and keeps changing until midnight. Days follow your phone's own clock and time zone.",
            ),
            UsageGuideEntry(
                "What are complete and partial days?",
                "A complete day has all of its records. A partial day is one FocusFlow could not record completely. It is shown " +
                    "but left out of averages and insights so it cannot pull them down.",
            ),
        ),
    ),
    UsageGuideSection(
        "Screen time",
        listOf(
            UsageGuideEntry(
                "What is screen time?",
                "The time the screen was on, from Android's screen on and off events, split across the hours it covered.",
            ),
            UsageGuideEntry(
                "What is Observed Device Time?",
                "The time apps were open in the foreground. FocusFlow's own home screen is left out so it does not count as phone use.",
            ),
            UsageGuideEntry(
                "How are daily averages made?",
                "From complete days only, so one half-recorded day cannot skew them.",
            ),
        ),
    ),
    UsageGuideSection(
        "Unlocks, wake-ups and sessions",
        listOf(
            UsageGuideEntry(
                "What counts as an unlock?",
                "The phone being unlocked. A wake-up is the screen turning on at all, so it also counts a " +
                    "glance at a notification.",
            ),
            UsageGuideEntry(
                "What if my phone has no screen lock?",
                "Android then reports no unlocks, so wake-ups are shown instead and sessions run from screen-on to screen-off.",
            ),
            UsageGuideEntry(
                "What is a session and a quick check?",
                "A session runs from unlock to screen-off. A quick check is a session shorter than ${seconds(PhoneThresholds.QUICK_CHECK_MAX_MS)}. " +
                    "Session lengths are grouped as ${PhoneBuckets.LABELS.joinToString(", ")}.",
            ),
            UsageGuideEntry(
                "What is the first app after unlocking?",
                "The first app opened within ${seconds(PhoneThresholds.FIRST_APP_AFTER_MS)} of an unlock.",
            ),
            UsageGuideEntry(
                "How is the gap between unlocks measured?",
                "From one unlock to the next. Gaps longer than ${hours(PhoneThresholds.UNLOCK_GAP_CAP_MS)} (overnight, say) are not counted.",
            ),
        ),
    ),
    UsageGuideSection(
        "Time of day",
        listOf(
            UsageGuideEntry(
                "What are the parts of the day?",
                "Morning is 5 AM to noon, afternoon noon to 5 PM, evening 5 to 10 PM, and late night 10 PM to 5 AM. " +
                    "The busiest hour is the clock hour with the most screen time or unlocks.",
            ),
        ),
    ),
    UsageGuideSection(
        "Distraction and apps",
        listOf(
            UsageGuideEntry(
                "Which apps count as distracting?",
                "Apps in the Social media, Entertainment and Games categories, plus any app you tried to open while it was blocked. " +
                    "Categories come from a built-in list; apps it does not know are shown as Other.",
            ),
            UsageGuideEntry(
                "What is a reflex app?",
                "An app you open at least ${PhoneThresholds.REFLEX_MIN_OPENS_PER_DAY.toInt()} times on the days you use it, mostly " +
                    "for short visits, across at least ${PhoneThresholds.REFLEX_MIN_ACTIVE_DAYS} days.",
            ),
            UsageGuideEntry(
                "What is a rising app?",
                "An app whose daily average is at least ${PhoneThresholds.RISING_MIN_RATIO}× the previous period and " +
                    "${minutes(PhoneThresholds.RISING_MIN_DELTA_MS)} more, from a starting point of at least ${minutes(PhoneThresholds.RISING_MIN_PREVIOUS_MS)}.",
            ),
        ),
    ),
    UsageGuideSection(
        "Focus and blocked attempts",
        listOf(
            UsageGuideEntry(
                "What does held mean?",
                "A focus session you finished without any overrides.",
            ),
            UsageGuideEntry(
                "What is a blocked attempt?",
                "A time you tried to open an app that FocusFlow was blocking. Attempts per focus hour is attempts divided by hours of focus.",
            ),
        ),
    ),
    UsageGuideSection(
        "Patterns and records",
        listOf(
            UsageGuideEntry(
                "How are patterns found?",
                "From up to 90 days of complete days, compared with earlier weeks. A change is only reported when it is big enough to matter " +
                    "and there are enough days behind it; each pattern says how sure it is.",
            ),
            UsageGuideEntry(
                "Are these advice?",
                "No. They are observations about your own habits, not advice or a diagnosis.",
            ),
        ),
    ),
    UsageGuideSection(
        "Nudges",
        listOf(
            UsageGuideEntry(
                "What are nudges?",
                "Optional notifications, off by default. FocusFlow sends one a little before the time your own data says things " +
                    "usually slip: ${PhoneNudgePlanner.LEAD_MINUTES} minutes before the hour a distracting app is used most, or before bedtime when late-night use is rising.",
            ),
            UsageGuideEntry(
                "How many will I get?",
                "At most ${NudgeSchedule.MAX_NUDGES} a day, never closer than ${NudgeSchedule.MIN_GAP_MINUTES} minutes apart. " +
                    "They are planned again every day from your latest data, so they stop when the pattern does.",
            ),
            UsageGuideEntry(
                "Can I turn them off?",
                "Yes, any time, from the nudge switch on the Habits tab in Extra.",
            ),
        ),
    ),
)
