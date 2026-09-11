# FocusFlow — Stats & Insights Redesign Plan

## Core principle
This is not a dashboard. It is a mirror. Every screen should make the user feel like
someone has been watching them specifically and is willing to say something direct
about what they see — not a generic motivational message, not a raw chart, but a
statement about this person's actual pattern.

All insights are generated on-device from local data only. No API calls, no data
leaves the phone. The "analyst voice" is achieved through a template engine:
pre-written sentence variants selected by rule-based conditions applied to the user's
own data.

---

## Data inventory — what we actually have (confirmed from source)

### Already in the database
| Table | What it stores | Useful fields |
|---|---|---|
| `tasks` | Every task ever created | `start_time`, `end_time`, `duration_minutes`, `status` (scheduled/completed/skipped/missed), `focus_mode` |
| `focus_sessions` | Every focus session | `started_at`, `ended_at`, `task_id`, `allowed_packages` |
| `focus_overrides` | Every emergency override event | `app_name`, `overridden_at`, `reason` |
| `daily_completions` | Per-day aggregate | `date`, `completed`, `total` |
| `report_notes` | User-written notes per day/week | `ref_date`, `type`, `note` |

### Already in SharedPreferences / native
- Temptation log: JSON array of `{ pkg, appName, timestamp }` — every blocked-app
  attempt, pruned to last N days (read via existing TS wrapper)
- UsageStats: per-app screen time accessible via `UsageStatsModule` — **requires
  usage-access permission, gate all 3-month data behind this**

### Gaps that need new DB queries (no schema changes needed)
These are computable from existing tables but don't have dedicated query functions yet:
- Session quality: a session is "clean" if `focus_overrides` has zero rows for that
  `task_id` in the session's time window
- Task estimation accuracy: `(ended_at - started_at) vs duration_minutes` per session
- Hour-of-day breakdown for blocking events: group temptation log by `hour(timestamp)`
- Week-over-week completion rate: `daily_completions` already has this, just needs a
  rolling 12-week query

---

## Architecture

### New files to add

```
src/services/analytics/
  AnalyticsProcessor.ts     — queries raw data, computes derived metrics, returns AnalyticsSnapshot
  InsightEngine.ts          — takes AnalyticsSnapshot, runs rules, returns ranked InsightCard[]
  InsightRules/
    YesterdayRules.ts       — rules for the yesterday view
    WeeklyRules.ts          — rules for the weekly view
    ThreeMonthRules.ts      — rules for the 3-month view
  InsightTemplates.ts       — all sentence variants, keyed by rule ID
  AchievementEngine.ts      — evaluates achievement conditions, returns earned[]
```

### `AnalyticsSnapshot` shape
```ts
interface AnalyticsSnapshot {
  generatedAt: string;
  window: 'yesterday' | 'week' | 'three_months';

  // Task data
  tasks: {
    total: number;
    completed: number;
    skipped: number;
    missed: number;
    byHour: Record<number, { total: number; completed: number }>;  // 0–23
    byDayOfWeek: Record<number, { total: number; completed: number }>;  // 0=Sun
    estimationErrorMinutes: number[];  // (actual - scheduled) per completed session
    firstTaskHour: number | null;      // hour the earliest task was started this period
  };

  // Session data
  sessions: {
    total: number;
    cleanCount: number;          // zero override attempts
    totalFocusMinutes: number;
    byHour: Record<number, number>;  // sessions started by hour
    avgDurationMinutes: number;
    fastestWindowHour: number | null;  // hour with best completion vs estimate ratio
  };

  // Blocking / temptation data
  blocking: {
    totalAttempts: number;
    byHour: Record<number, number>;   // attempts by hour of day
    byApp: Record<string, { appName: string; count: number }>;
    peakHour: number | null;
    topApp: { appName: string; count: number } | null;
    topAppShare: number | null;       // 0–1, percentage of total attempts
  };

  // Comparisons (week view and up)
  trends?: {
    completionRatePrev: number | null;   // previous period
    completionRateCurr: number;
    blockingAttemptsPrev: number | null;
    blockingAttemptsCurr: number;
    weekByWeek: { weekStart: string; completionRate: number }[];  // 12 entries max
  };

  // Usage stats (3-month only, requires permission)
  phoneUsage?: {
    byHour: Record<number, number>;      // minutes of screen-on per hour, averaged
    peakHour: number | null;
    peakPeriod: 'morning' | 'afternoon' | 'evening' | 'night';
    heaviestApp: { appName: string; minutes: number } | null;
  };
}
```

### `InsightCard` shape
```ts
interface InsightCard {
  id: string;
  category: 'task' | 'session' | 'resistance' | 'pattern' | 'trend' | 'positive' | 'nothing_to_report';
  priority: number;      // 0–100, engine picks top N to display
  headline: string;      // bold first line, short
  body: string;          // 1–2 sentences, analyst voice
  sentiment: 'positive' | 'neutral' | 'warning';
}
```

### How the template engine works
Each rule is a plain object:
```ts
interface InsightRule {
  id: string;
  condition: (s: AnalyticsSnapshot) => boolean;
  priority: (s: AnalyticsSnapshot) => number;  // higher = more notable
  render: (s: AnalyticsSnapshot) => InsightCard;
}
```

Each `render` function picks from multiple sentence variants using a deterministic
seed (e.g. `weekNumber % variants.length`) so the phrasing rotates across weeks
without being random in a jarring way.

---

## Yesterday screen

**Data window:** the calendar day that just ended (or today, after noon).
**Voice:** neutral observer. "Here's what I saw today."
**Layout:** vertical list of cards, no charts.

### Rules (conditions + sentence variants)

**YESTERDAY_PERFECT_DAY**
- Condition: `blocking.totalAttempts === 0 && tasks.completed === tasks.total`
- Priority: 90
- Variants:
  - "Clean day. You finished everything and never reached for a blocked app."
  - "Nothing to flag today. Every task done, zero blocked-app attempts."
- Sentiment: positive

**YESTERDAY_CLEAN_NO_BLOCKS**
- Condition: `blocking.totalAttempts === 0 && tasks.completed < tasks.total`
- Priority: 60
- Variants:
  - "You never reached for a blocked app today. That part was clean."
- Sentiment: positive

**YESTERDAY_PEAK_BLOCK_HOUR**
- Condition: `blocking.totalAttempts > 0`
- Priority: 75
- Data used: `blocking.byHour`, `blocking.peakHour`, `blocking.totalAttempts`
- Variants:
  - "You resisted {N} blocked-app attempts. {peak_hour_label} was your hardest hour, with {peak_count} of them."
  - "{N} times you reached for something blocked. Most of those — {peak_count} — happened {peak_hour_label}."
- Sentiment: neutral if N < 5, warning if N >= 10

**YESTERDAY_TASK_RESULT**
- Always shown (priority: 50, baseline card)
- Renders as a compact list: ✓ Done, — Skipped, ✗ Missed per task
- Headline: "Today's tasks"
- No analyst commentary — just the facts

**YESTERDAY_SECOND_SKIP_THIS_WEEK**
- Condition: skipped count this week > 1
- Priority: 65
- Variants:
  - "You've skipped {N} tasks this week. Not a problem if it's deliberate."
  - "{N} skips this week. If the schedule isn't working, it's worth adjusting rather than skipping."
- Sentiment: neutral

**YESTERDAY_SINGLE_HARD_SESSION**
- Condition: one or more sessions had >5 override attempts
- Priority: 80
- Variants:
  - "Your {session_time} session had {N} blocked-app attempts. That one was hard."
- Sentiment: neutral

**YESTERDAY_NOTHING_NOTABLE**
- Condition: fallback — nothing else fired or everything is moderate
- Priority: 10
- Body: "Ordinary day. You showed up, you worked, nothing unusual happened."
- Sentiment: neutral
- This card is the "nothing to report" voice — it must be allowed to exist. Not every
  day is interesting.

---

## Weekly screen

**Data window:** the last 7 calendar days.
**Voice:** starting to see a pattern. More confident than yesterday.
**Layout:** 2–4 insight cards at the top, then a presence strip (7 boxes, attended/not),
then task list summary below. No detailed daily breakdown — that's what yesterday is for.

### Rules

**WEEKLY_VULNERABLE_WINDOW**
- Condition: `blocking.totalAttempts > 0 && blocking.peakHour !== null`
- Priority: 85
- Data: `blocking.byHour`, `blocking.peakHour`
- Variants:
  - "{peak_hour_label} is your weakest hour this week. {peak_count} of your {total} blocked-app attempts happened then."
  - "Between {peak_hour_start} and {peak_hour_end}, you reached for a blocked app {peak_count} times — more than any other hour."
- Sentiment: warning if peak_count > 8, neutral otherwise

**WEEKLY_CLEAN_WINDOW**
- Condition: any 2-hour block in blocking.byHour has zero attempts
- Priority: 55
- Data: best clean window from byHour
- Variants:
  - "{clean_window} was consistently quiet this week. No blocked-app attempts during that window."
- Sentiment: positive

**WEEKLY_ONE_APP**
- Condition: `blocking.topAppShare !== null && blocking.topAppShare > 0.5`
- Priority: 88
- Variants:
  - "{app_name} made up {share}% of your blocked-app attempts this week. It's not a habit — it's the habit."
  - "More than half your blocked-app attempts were {app_name}. {share}% to be exact."
- Sentiment: warning

**WEEKLY_TOP_APP_MODERATE**
- Condition: `blocking.topApp !== null && blocking.topAppShare <= 0.5`
- Priority: 60
- Variants:
  - "{app_name} was your most blocked app this week — {count} attempts."
- Sentiment: neutral

**WEEKLY_ESTIMATION_IMPROVING**
- Condition: avg estimation error in range [-5, +10] minutes
- Priority: 65
- Variants:
  - "Your task estimates were solid this week. You were off by less than 10 minutes on average."
- Sentiment: positive

**WEEKLY_UNDERESTIMATING**
- Condition: avg estimation error > 20 minutes (taking longer than planned)
- Priority: 72
- Variants:
  - "Your tasks are consistently taking longer than you schedule. {avg_error} minutes over on average."
  - "You're underestimating. Tasks ran {avg_error} minutes over their scheduled time on average this week."
- Sentiment: warning

**WEEKLY_OVERESTIMATING**
- Condition: avg estimation error < -15 minutes (finishing faster than planned)
- Priority: 62
- Variants:
  - "You're finishing tasks faster than you schedule them — {abs_error} minutes ahead on average. Your buffers might be too generous."
- Sentiment: neutral

**WEEKLY_SHOWED_UP**
- Always shown (priority 40, presence card)
- Data: days where at least one task was attempted or session run
- Variants:
  - "You showed up {N} of 7 days." (just this, no elaboration)
  - If N === 7: "Every day this week. That's the whole job."
  - If N >= 5: "You showed up {N} of 7 days."
  - If N < 3: "You showed up {N} of 7 days this week."
- Sentiment: positive if ≥5, neutral if 3–4, warning if <3

**WEEKLY_BETTER_THAN_LAST**
- Condition: `trends.completionRateCurr > trends.completionRatePrev + 0.05` (5pt improvement)
- Priority: 78
- Variants:
  - "Better week than last. Completion rate up, blocking attempts {direction}."
  - "This week was cleaner than last. You're moving in the right direction."
- Sentiment: positive

**WEEKLY_WORSE_THAN_LAST**
- Condition: `trends.completionRateCurr < trends.completionRatePrev - 0.1` (10pt drop)
- Priority: 82
- Variants:
  - "Harder week than last. Completion rate dropped, more blocking attempts."
  - "This week was messier than last. Worth paying attention to."
- Sentiment: warning

**WEEKLY_FLAT**
- Condition: completion rate within 5pt of last week, no strong signal either direction
- Priority: 35
- Body: "Consistent with last week. Not better, not worse."
- Sentiment: neutral

---

## 3-Month screen

**Data window:** rolling 90 days (12 full weeks).
**Gate:** entire screen behind `UsageStatsModule` permission. If not granted, show a
single card explaining what this screen would contain and how to grant access.
**Voice:** the analyst has been watching for a while. Confident, direct, willing to
say things the user might not have articulated themselves.
**Layout:** 3–5 insight cards. No list. No raw data tables. One 12-bar trend chart
below the cards (completion rate by week). That's it.

### Rules

**THREE_MONTH_PHONE_PEAK**
- Condition: `phoneUsage.byHour` data available
- Priority: 92
- Data: `phoneUsage.peakHour`, `phoneUsage.peakPeriod`
- Variants:
  - "Your heaviest phone use is consistently between {peak_start} and {peak_end}. Every week, without exception."
  - "{peak_period_label} is when you use your phone most. That's been true across all 12 weeks."
- Sentiment: neutral (factual observation, not editorial)

**THREE_MONTH_REAL_FOCUS_WINDOW**
- Condition: `sessions.fastestWindowHour !== null` and sample size > 20 sessions
- Priority: 90
- Variants:
  - "Tasks you start around {hour_label} consistently finish faster than your estimates. That's your sharpest window."
  - "Your best work happens {hour_label}. Tasks started then run {pct}% closer to your planned time than any other slot."
- Sentiment: positive

**THREE_MONTH_SCHEDULING_HONESTY**
- Condition: any `dayOfWeek` bucket has total > 5 and completion rate < 0.4
- Priority: 95 (highest priority — this is the most confronting insight)
- Data: `tasks.byDayOfWeek`, worst performing day
- Variants:
  - "You've scheduled tasks on {day_name} for 12 weeks. Your completion rate that day is {rate}%. Consider whether {day_name} is actually available to you."
  - "{day_name} is your most scheduled day. It's also your worst completion day, at {rate}%. Something doesn't add up."
- Sentiment: warning
- Note: only fire this if the sample size is convincing (>5 tasks on that day)

**THREE_MONTH_REAL_PROBLEM_APP**
- Condition: one app represents >40% of all blocking attempts over 90 days
- Priority: 88
- Variants:
  - "In 12 weeks, {app_name} has accounted for {share}% of every blocked-app attempt. That's not a coincidence. That's the thing."
  - "{app_name} is responsible for {share}% of your blocked attempts over 3 months. One app."
- Sentiment: warning

**THREE_MONTH_IMPROVING**
- Condition: last 4 weeks of `trends.weekByWeek` show monotonically increasing or
  net +10pt completion rate vs first 4 weeks
- Priority: 85
- Variants:
  - "Your task completion rate has improved over the last 3 months. You're actually getting better at this."
  - "Slow improvement across 12 weeks. Completion rate is up {delta}pt from where you started."
- Sentiment: positive

**THREE_MONTH_PLATEAU**
- Condition: last 6 weeks of weekByWeek show < 5pt variance either way
- Priority: 75
- Variants:
  - "You've been at around {rate}% completion for 6 weeks. You've hit a ceiling at your current setup."
  - "Flat for 6 weeks. Not getting worse, but not improving. Something about the routine isn't working."
- Sentiment: neutral / mild warning

**THREE_MONTH_NIGHT_PATTERN**
- Condition: `phoneUsage.byHour` shows hours 21–23 average > 2x daytime average
- Priority: 80
- Variants:
  - "After 9pm, your phone use is consistently {mult}x higher than during the day. Every week."
  - "Your phone use triples after 9pm. That pattern has held across all 12 weeks."
- Sentiment: neutral (no judgment, just the fact)

**THREE_MONTH_INSUFFICIENT_DATA**
- Condition: fewer than 4 weeks of data exist
- Priority: 100 (always shown first if it applies)
- Body: "Not enough data yet for 3-month patterns. Come back after {weeks_remaining} more weeks."
- Sentiment: neutral

---

## Screen layout changes

### Collapse yesterday + weekly into one screen
Both are short-window, same analytical voice. Use a two-way toggle at the top:
`Yesterday | This Week`. Default to This Week. The 3-Month view stays as its own
separate screen (genuinely different data, different experience).

### 3-Month screen structure
```
[Permission gate — if no usage access]
"This screen uses Android's usage statistics to show your phone behaviour patterns.
Grant usage access to unlock it."
[Button: Grant access → jumps to settings]

[If permission granted]
[3–5 insight cards, vertically stacked]
[12-bar completion rate chart — weeks on x-axis, rate on y-axis, no label clutter]
[No other content]
```

### Yesterday / Weekly structure
```
[Yesterday | This Week toggle]
[2–4 insight cards]
[Presence strip — 7 boxes for weekly, single row for yesterday showing task results]
[Task list — binary: done / skipped / missed, no extra detail]
```

---

## Achievement system

### Architecture
`AchievementEngine.ts` evaluates conditions against the `AnalyticsSnapshot` plus
a `LifetimeStats` object (cumulative, queried once). It returns an array of newly
earned achievements to display (badge + title + one sentence of explanation).

### Achievement categories and definitions

**RESISTANCE achievements** — about not doing the thing

| ID | Title | Condition |
|---|---|---|
| `iron_session` | Iron Session | A completed focus session with zero blocked-app attempts |
| `quiet_week` | Quiet Week | 5 sessions in a row, all clean |
| `hard_hour` | The Hard Hour | A clean session completed during the user's historically worst hour (only unlocks after 4+ weeks of data, since that window needs to be known first) |
| `ten_row` | Ten in a Row | 10 consecutive clean sessions |

**HONESTY achievements** — about self-knowledge

| ID | Title | Condition |
|---|---|---|
| `sharp_estimate` | Sharp Estimate | A task completed within 5 minutes of its scheduled duration |
| `five_sharp` | Calibrated | 5 sharp estimates in a single week |
| `self_aware` | Self-Aware | Added an app to the block list within 24 hours of that app appearing in their top 3 distractions that week — this requires comparing block-list change timestamps against temptation log data |

**PRESENCE achievements** — about showing up

| ID | Title | Condition |
|---|---|---|
| `first_week` | First Week | Showed up 7 days in a row (any activity counts) |
| `back_again` | Back Again | Returned and completed a task after a gap of 5+ days — specifically rewards return, not absence |
| `three_weeks` | Three Weeks | 21 consecutive days with at least one task attempted |

**PATTERN-BREAKING achievements** — only meaningful after enough data

| ID | Title | Condition | Unlock gate |
|---|---|---|---|
| `friday_finally` | Finally | Completed a task in their historically worst time slot | Needs 4+ weeks of data to know what "worst slot" is |
| `beat_the_habit` | Beat the Habit | A full week where top blocked app attempts dropped >50% vs their 4-week average | 4+ weeks of data |
| `night_shift_retired` | Early Night | First week where 9pm+ phone use dropped below their own 12-week average | 12 weeks of data + usage-access permission |

**HIDDEN achievements** — not listed anywhere, discovered by notification only

| ID | Title | Condition | Reveal text on unlock |
|---|---|---|---|
| `long_game` | The Long Game | Completed a task 30+ minutes before its scheduled end, 3 times in one week | "You keep finishing early. That's called being good at this." |
| `reset` | Reset | Completed their first task after a streak break | "You came back. That's the whole thing." |
| `scheduling_honesty` | Honest | Changed a recurring task's scheduled time to match when they actually complete it | "You updated the schedule to match reality. That's harder than it sounds." |

### Achievements NOT to include
- Any "complete your Nth session" volume ladder
- Streak count milestones beyond the initial "first week" (streak protection causes
  people to add trivial tasks to protect a number — don't incentivize that)
- Any achievement that can be gamed by creating low-effort tasks

### Display
Achievements appear as a notification when earned, and accumulate in a dedicated
"Achievements" card on the stats screen. Show: badge icon, title, the one-sentence
explanation. No score, no XP, no leaderboard. Just the fact that it happened.

---

## "Insight of the week" — the standout card

One card per week, generated as the highest-priority unique signal from that week's
rules that wasn't shown in a previous week. Sits at the very top of the weekly view.

If nothing stands out: `"Nothing unusual this week. You showed up, you finished things,
nothing spiked. Sometimes the analysis is: you did well."` This must be allowed to
say nothing, because saying nothing when there's nothing to say is the entire point.

---

## New DB queries needed

These functions need to be added to `database.ts`:

```ts
// Session quality — was a session "clean" (zero override attempts during it)?
dbGetSessionsWithOverrideCount(startISO: string, endISO: string):
  Promise<{ session_id: number; started_at: string; ended_at: string; override_count: number }[]>

// Task estimation accuracy — actual vs planned per completed session
dbGetEstimationErrors(startISO: string, endISO: string):
  Promise<{ task_id: string; planned_minutes: number; actual_minutes: number }[]>

// Weekly completion rate for trend chart
dbGetWeeklyCompletionRates(numWeeks: number):
  Promise<{ week_start: string; completed: number; total: number }[]>

// Hour-of-day task completion breakdown
dbGetTasksByHourOfDay(startISO: string, endISO: string):
  Promise<{ hour: number; total: number; completed: number }[]>
```

The temptation log (hour-of-day blocking breakdown) is read from SharedPreferences
via the existing TS wrapper and processed in `AnalyticsProcessor.ts` — no new DB
query needed for that since it already exists in memory.

---

## What to remove from the current stats screen

- Yesterday's detailed report view (the one the pill already covers) — replaced by
  the two-card yesterday insight view
- All cumulative lifetime totals (total hours focused, total sessions, total tasks)
- The all-time/yearly view entirely — replaced by the 3-month screen
- Any chart that shows a raw number without an interpretive sentence above it
EOF
echo "plan written"
wc -l /home/claude/redo_output/STATS_INSIGHTS_PLAN.md
---

## Notification system — redesign and additions

### What's wrong right now (confirmed from source)

**Morning digest (`notificationService.ts:413–474`):**
Body is a data dump: `"Yesterday: 3/5 tasks done · 2h 30m focused · ✅ Task1, Task2 · ⏭ 1 skipped"`
This is a dashboard in push form. Nobody reads a CSV in a notification.

**Weekly report (`notificationService.ts:487–533`):**
Body is generic: `"Tap to see how your week went — focus time, streaks, and completed tasks."`
This could be sent to any user of any app. It carries zero actual content.

Both need content, not structure. The schedule and channels are fine — just the body.

---

### Existing channels — keep, update content only

**`morning-digest`** — fires at `profile.wakeUpTime`, next morning
New content: run `AnalyticsProcessor` for yesterday → `InsightEngine` → take the
single highest-priority card → use its `body` as the notification body.

```ts
// Example outputs replacing the data dump:
"You resisted 8 blocked-app attempts yesterday. 5 of them were in that 3pm hour."
"Clean day. You finished everything and never reached for a blocked app."
"You've skipped 3 tasks this week. If the schedule isn't fitting, adjust it."
```

Fallback when InsightEngine returns only the `nothing_to_report` card:
`"Ordinary day yesterday. You showed up."` — short, honest, done.

**`weekly-report`** — fires on `profile.weeklyReviewDay` at `profile.wakeUpTime`
New content: run `AnalyticsProcessor` for the past 7 days → `InsightEngine` →
take the highest-priority card → its `body` is the notification body.

```ts
// Example outputs replacing the generic teaser:
"YouTube made up 63% of your blocked attempts this week. One app."
"Your 3pm hour was your weakest. 11 of your 18 blocked attempts happened then."
"Better week than last. You're moving in the right direction."
```

Fallback: `"Consistent week. Nothing stood out. Tap to see the full picture."` —
at least this is honest about the fact that there's nothing dramatic to report.

**Both notifications:** respect `focusSession.isActive` before scheduling — if a
session is somehow still running at wakeUpTime (unlikely but possible), delay the
notification by 30 minutes rather than interrupting.

---

### New channels

**`achievements`** — `Importance.DEFAULT`, badge=true, no vibration
For achievement unlocks. Fires immediately when earned, not on a schedule.
Hidden achievements: same channel, but `sound: false` and no vibration — let them
feel discovered, not announced.

**`insights`** — `Importance.LOW`, no sound, no vibration
For one-time pattern discovery notifications only. Passive, easily ignorable.

**`resistance`** — `Importance.DEFAULT`, opt-in, default OFF
For the temptation spike notification. User explicitly enables this.

---

### New notifications

**Achievement unlock** — fires from `AchievementEngine.ts` when a new achievement
is earned, immediately:
```
Title:  "Iron Session 🏅"
Body:   "A complete focus session with zero blocked-app attempts."

Title:  "The Long Game"          ← hidden achievement, revealed on unlock
Body:   "You keep finishing early. That's called being good at this."
```
Channel: `achievements`. Never batched — each achievement fires its own notification.
Does not respect quiet hours. Achievements are infrequent, positive, and deserved —
making them wait until morning would feel deflating.

**Pattern discovery** — fires once per pattern, never repeats:
```
Title:  "Something we noticed"
Body:   "We noticed something about your schedule. Open Stats to see."
```
Channel: `insights`. Fires when `InsightEngine` first becomes confident about a
named pattern (e.g. `THREE_MONTH_SCHEDULING_HONESTY` crosses its sample-size gate
for the first time). Never includes the actual insight in the notification — that's
the draw to open the app. Tracked via `shownPatternInsightIds: string[]` in
`AppSettings`; once an insight ID is in that list, it never fires again.

Respects quiet hours (after `bedTime` or 10pm default, before `wakeUpTime`). Does
not fire during active focus session.

**Auto-reschedule confirmation** — fires immediately when a task is moved by the
Auto Reschedule feature (only when `autoRescheduleEnabled` is true and
`savedMinutes > 5` to avoid noise for tiny shifts):
```
Title:  "Schedule updated"
Body:   "You finished early. [Next task title] has been moved to 7:15 AM."
```
Channel: `task-reminders` (task-related, not a separate channel). Controlled by
`rescheduleNotificationsEnabled` in `AppSettings` (default true).

**Arbitration notification** — fires immediately when session arbitration occurs
(Bug 7 / `autoRescheduleEnabled` path):
```
// Incoming task is stricter — current session ends early:
Title:  "Session switched"
Body:   "[Next task] is more focused. It's starting now."

// Current session is stricter — incoming task is deferred:
Title:  "Schedule held"
Body:   "[Next task] will start when [Current task] ends at [time]."
```
Channel: `task-reminders`. Informational only, no action required.

**Temptation spike** — opt-in, default off, `temptationSpikeEnabled` in AppSettings:
```
Title:  "Rough hour"
Body:   "You've attempted {N} blocked apps in the last 60 minutes."
```
Channel: `resistance`. Fires when `TemptationLogManager` detects ≥8 blocked-app
attempts in any rolling 60-minute window. Minimum 2-hour cooldown between firings
so it doesn't spam. Does NOT fire during an active focus session — if someone is in
a focus session, the block overlay is already handling feedback.

Technically: `TemptationLogManager.kt` already logs every blocked attempt with
a timestamp. Add a check at log-write time: if `rollingHourCount >= threshold` and
`System.currentTimeMillis() - lastSpikeFiredAt > 2h`, emit a broadcast that
`NotificationActionReceiver` picks up and fires the notification.

**Block list suggestion** — fires at most once per week, after the weekly insight
is computed, only when the top distracting app is NOT already in the always-block
list:
```
Title:  "Worth blocking?"
Body:   "YouTube made up 63% of your blocked attempts this week. Add it to your
         always-block list?"
[Action button: "Block it"] → deep-links directly to the block list with that app
                               pre-selected
```
Channel: `weekly-report` (same channel, same timing as the weekly insight).
Controlled by its own toggle `blockSuggestionEnabled` (default true). If the user
dismisses without acting, don't show it again for that specific app for 30 days.

---

### New settings fields needed

```ts
// Additions to AppSettings in types.ts:
morningDigestEnabled: boolean;               // was implicit, now explicit. default true
achievementNotificationsEnabled: boolean;    // default true
patternInsightNotificationsEnabled: boolean; // default true
rescheduleNotificationsEnabled: boolean;     // default true
blockSuggestionEnabled: boolean;             // default true
temptationSpikeEnabled: boolean;             // opt-in, default false
temptationSpikeThreshold: number;            // default 8 (attempts per 60 min)
shownPatternInsightIds: string[];            // tracks which one-time insights fired
```

---

### Smart quiet hours — apply to insight/pattern notifications only

Do not fire pattern discovery or block-suggestion notifications:
- During an active focus session (`focusSession.isActive === true`)
- After `profile.bedTime` (new UserProfile field, default 22:00) or 10pm if unset
- Before `profile.wakeUpTime` or 7am if unset

Achievement notifications ignore quiet hours — infrequent, positive, deserved.
Task-reminder notifications already have their own smart scheduling — unchanged.
Temptation spike notifications ignore quiet hours by design — if it's firing at
11pm, the 11pm behavior is the whole point.

---

### What NOT to add

- **Re-engagement notifications** ("You haven't opened FocusFlow in 3 days!") —
  dark pattern. Not appropriate for a focus app.
- **Streak-loss warnings** ("Your streak ends tonight!") — causes anxiety, trains
  users to create trivial tasks to protect a number. Not building it.
- **Comparative push notifications** ("You did better than last week!") — these
  belong on the stats screen with context, not in a notification without it.
- **Daily completion rate push** ("You're at 60% for today") — too much noise,
  nobody needs a mid-day report card.

---

## Additional ideas worth building

### 1. Week-ahead preview (Sunday evening)
A forward-looking counterpart to the backward-looking weekly report. Fires Sunday
at 7pm (configurable, separate from `weeklyReviewDay` report time):
```
Title:  "Next week"
Body:   "You have {N} tasks scheduled. First up: [Task title] at [time] on [day]."
```
Controlled by `weekAheadEnabled` in AppSettings (default true if `weeklyReportEnabled`
is true). This is genuinely useful — people plan on Sundays. A single notification
with the week's first task is a low-friction way to set the mental context.

### 2. "Good time to start" nudge (opt-in, default off)
After 4+ weeks of data, when `InsightEngine` has identified a reliable productive
window, fire a once-per-day nudge if the user hasn't started a session yet during
that window:
```
Title:  "Your best hour"
Body:   "9am is when you work fastest. You have tasks scheduled for today."
```
Opt-in only (`productiveWindowNudgeEnabled`, default false). Only fires if:
- User hasn't started a session yet today
- Current time is within 15 minutes of their identified best window
- They have at least one task scheduled today
This is the closest thing to "pushy" in this list — keep it opt-in and say so clearly
in the settings description.

### 3. In-app "debrief moment"
Not a notification — a modal or bottom sheet that appears when the user opens the
app within 30 minutes of a focus session ending. One card only. The highest-priority
insight from that specific session:
```
"Clean session. Zero blocked-app attempts."
"7 attempts in that session. The hardest stretch was the middle 20 minutes."
"You finished 12 minutes ahead of schedule."
```
Dismissible, never shown twice for the same session. This creates a natural
feedback loop tied to real behavior, without requiring the user to navigate to Stats.
Tracked via `lastShownDebriefSessionId` in AppSettings.

### 4. Focus session "intent" screen
Before a focus session starts, a 3-second micro-screen with just:
- The task name
- Its duration
- One of: "Last time you had a clean session." / "Last time you attempted [N] blocked
  apps." / "First time focusing on this task."
Tap to dismiss and start. This sets context, nothing more. No action required.
Requires storing `lastSessionResultByTaskId` in AppSettings or querying
`focus_sessions` + `focus_overrides` by task ID.

### 5. Smarter morning digest timing
Currently fires at `wakeUpTime` exactly. The problem: if the user wakes up and
immediately opens FocusFlow, the notification lands while the app is open, which is
jarring. Better: if the app has been opened within the last 10 minutes when the
notification would fire, skip it. The content is already visible.
This is a one-line condition in `scheduleMorningDigest`.

### 6. "Nothing to report" as a feature, not a fallback
The insight engine's `nothing_to_report` card should be surfaced intentionally in
the UI — not hidden or minimized. When a user has had a genuinely clean,
unremarkable week, the stats screen should lead with:
`"Nothing unusual this week. You showed up, you finished things, nothing spiked.
Sometimes the analysis is just: you did well."`
This requires confidence to implement — most analytics screens manufacture insight
where none exists. Don't. A user who sees this card knows the engine is honest, which
makes it trustworthy when it does surface something real.