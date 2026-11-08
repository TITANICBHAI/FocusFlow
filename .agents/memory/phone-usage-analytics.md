---
name: Phone usage analytics
description: Package map, key definitions, how to add a statistic, and known limits of the phone-behaviour analytics (Stats, Extra, Insights).
---

Phone-behaviour analytics (screen time, unlocks, sessions, apps, distraction, focus/blocking, insights) is a pure-Kotlin core under `analytics/phone/` with a thin UI under `ui/stats/phone/`. Progress and rulings: `PHONE_ANALYTICS_PROGRESS.md`.

**Package map.** `analytics/phone/`: `PhoneEventReducer` (OS events to one `PhoneDay`), `PhoneBehaviourRollupWriter` (writes day rows), `BlockedAttemptSync`, `PhoneBehaviourStore` (Room access behind an interface), `PhoneBehaviourRepository` (loads a `PhoneReport` for a window; implements `PhoneReportSource`), the builders (`ScreenTimeBuilder`, `PhoneBehaviourBuilder`, `AppsBuilder`, `DistractionBuilder`, `FocusBlockingBuilder`), `PhoneText` (all number and time formatting), `PhoneConstants` (every threshold). `analytics/phone/insights/`: `PhoneInsightEngine` and its modules; thresholds live in `InsightThresholds`. `ui/stats/phone/`: `PhoneStatsStateHolder` (loading logic) and `PhoneStatsViewModel` (wrapper), pure text and model files (`StatsPhoneText`, `ExtraAppsModels`, `PhoneTabModels`, `FocusTabModels`, `PhoneInsightsModels`, `PhoneChartMath`), and the Compose files (`StatsPhoneSection`, `Extra*Tab`, `PhoneInsightsScreen`, `PhoneCharts`, `PhoneUiParts`).

**The rules that matter most.**
1. A day is a device-local calendar date; hours are split with `HourSplitter` (DST and half-hour zones safe). Never add 24 hours of milliseconds.
2. Only COMPLETE days feed averages and insights. Today (LIVE) counts in totals and charts; it is averaged only as "so far today" when no complete day exists. PARTIAL and MISSING days are shown dimmed and never averaged.
3. An unlock is the keyguard being dismissed after the screen turned on. If a phone never reports it, `unlockSignalAvailable` is false: show wake-ups and a note, never "0 unlocks".
4. A wake-up is the screen turning on, including for notifications.
5. Screen-on time covers the lock screen and home screen, so it is at least the time inside apps.
6. A session runs unlock to screen-off (or screen-on to screen-off without the signal). Length buckets: under 30 s, 30 s to 1 min, 1 to 5 min, 5 to 20 min, 20 min and over. A quick check is a session under 60 s. An open session is capped at 6 hours.
7. The first app after an unlock is the app opened from 2 s before to 15 s after it; an unlock with none counts as "nothing opened". Gaps between unlocks are capped at 3 hours.
8. Blocked attempts live in a durable per-day table merged with `max`, so the 500-entry temptation log can lose old entries without lowering counts.
9. A previous-period comparison needs at least 4 days of app data and only exists for the week and three-month windows.
10. Insight windows count "days back from today, inclusive" (recent = 7..1, prior = 28..8). Confidence is HIGH when the sample is at least twice the largest minimum stated for that insight. At most 12 insights and 2 notes are shown.
11. Reflex apps: at least 3 active days, 8 opens a day and half the sessions short. Rising apps: at least 1.5 times the previous average, 15 minutes more, and 5 minutes before.
12. Time-of-day periods: morning 5 to 11, afternoon 12 to 16, evening 17 to 21, late night 22 to 4.

**Adding a statistic.** (1) Write the definition and its edge cases here or in the progress file. (2) Add it to the right builder (or a new builder) and its tests, including empty and partial-day inputs. (3) Add the field to the section in `PhoneReportModels.kt`. (4) Add a pure text function to the matching `*Text`/`*Models` file with a test for each wording branch, including the empty state. (5) Show it in a card and give charts a content description. Keep numbers going through `PhoneText`.

**Known limits.** Android keeps only about a week or two of usage events, so earlier history cannot be recovered. Some phones (no screen lock, some makers) never report unlocks. The temptation log keeps 500 entries. Phone analytics are derived and not in the backup. Screen time differs from Digital Wellbeing by definition, so expect a ballpark match, not an exact one.
