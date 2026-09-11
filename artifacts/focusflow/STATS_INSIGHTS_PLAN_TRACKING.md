# FocusFlow Stats & Insights — Implementation Tracking

Authoritative source: `STATS_INSIGHTS_PLAN.md`

This file is the mutable execution and audit record. The source plan is kept
unchanged. Update this tracker as each slice is implemented. A checked box means
the current checkout contains the source-level implementation and it has been
reviewed against the actual code paths; it does not mean Android/device
validation has passed.

## Status legend

- `[ ]` Not started or not yet evidenced
- `[-]` Partially implemented, or implementation differs from the source plan
- `[x]` Implemented and source-reviewed
- `[!]` Blocked or needs a product/architecture decision

## Current status

- Status: `[-]` Analytics foundation is source-implemented, including native
  hourly UsageStats aggregation and bounded per-source snapshot loading.
  Notifications section, additional UX ideas, achievements, weekly standout
  persistence, and device validation remain outstanding.
- Last updated: 2026-09-11
- Validation boundary: source checks can be performed in this checkout; Android
  UsageStats permission behavior, native service behavior, and device UI require a
  generated Android project and device/emulator validation. Notification delivery
  requires a physical device — emulators suppress scheduled and broadcast
  notifications inconsistently.
- Privacy boundary: all analytics and insight generation must remain on-device;
  no API, upload, or LLM dependency is allowed. Notifications carry only
  template-rendered strings; raw analytics data never leaves the device.

---

## Execution checklist

### 1. Baseline and data-source audit

- [ ] Locate the current stats/reports screen and document which existing UI is
  replaced versus retained.
- [ ] Confirm the current database schema and query conventions for tasks,
  focus sessions, overrides, daily completions, and report notes.
- [ ] Confirm the existing temptation-log TypeScript wrapper and retention
  behavior.
- [ ] Confirm the `UsageStatsModule` permission/status API and the 3-month gate.
- [ ] Record any mismatch between the source plan and current checkout before
  implementing the affected section.

### 2. Database query layer

- [x] Add and source-review `dbGetSessionsWithOverrideCount`.
- [x] Add and source-review `dbGetEstimationErrors`.
- [x] Add and source-review `dbGetWeeklyCompletionRates`.
- [x] Add and source-review `dbGetTasksByHourOfDay`.
- [-] Add focused tests for date boundaries, empty results, incomplete sessions,
  and zero-duration/invalid data handling.

### 3. Analytics snapshot

- [x] Add the `AnalyticsSnapshot` contract with explicit window types.
- [x] Implement task totals, completion status counts, hour/day buckets,
  estimation errors, and first-task hour.
- [x] Implement session totals, clean-session counts, duration totals,
  hour buckets, and fastest-window calculation.
- [x] Implement temptation totals, hour buckets, app aggregation, peak hour,
  top app, and top-app share.
- [x] Implement weekly comparison and 12-entry trend data.
- [x] Implement UsageStats aggregation for the 3-month window only after the
  permission gate is satisfied.
- [x] Keep snapshot generation deterministic, bounded, local-only, and safe for
  empty or partial data.

### 4. Insight engine and rules

- [x] Add the `InsightCard` and `InsightRule` contracts.
- [x] Add deterministic template selection and centralized sentence variants.
- [-] Implement yesterday rules, including the baseline task-result card and
  nothing-to-report fallback.
- [-] Implement weekly rules, including presence and trend comparisons.
- [-] Implement 3-month rules, including insufficient-data handling.
- [x] Rank and cap cards according to the view-specific plan.
- [ ] Test rule thresholds, priority ordering, sentiment, formatting, and
  deterministic variant selection.

### 5. Stats screen experience

- [x] Collapse yesterday and weekly views behind the `Yesterday | This Week`
  toggle, defaulting to This Week.
- [-] Implement the yesterday vertical insight-card list and binary task results.
- [x] Implement weekly insight cards, seven-day presence strip, and task summary.
- [x] Implement the 3-month UsageStats permission gate and settings handoff.
- [-] Implement the 3-month insight cards and uncluttered 12-bar completion trend.
- [ ] Remove the superseded detailed yesterday report view.
- [ ] Remove lifetime totals and the all-time/yearly view.
- [x] Ensure every retained chart has an interpretive sentence above it.
- [-] Verify loading, empty, stale, permission-denied, and partial-data states.

### 6. Achievements

- [ ] Add `LifetimeStats` access without broad unbounded reads.
- [ ] Add `AchievementEngine.ts` and the resistance, honesty, presence,
  pattern-breaking, and hidden achievement conditions.
- [ ] Enforce each data-duration and UsageStats unlock gate.
- [ ] Persist/display newly earned achievements without XP, score, leaderboard,
  or volume-ladder behaviour.
- [ ] Add tests for one-time earning, repeated evaluation, streak breaks,
  insufficient history, and hidden-achievement notifications.

### 7. Insight of the week

- [ ] Select the highest-priority unique weekly signal.
- [ ] Persist enough identity/history to avoid repeating a prior week's signal.
- [ ] Place the standout card at the top of the weekly view.
- [ ] Allow the exact nothing-unusual fallback when no unique signal exists.

### 8. Notification system

#### 8a. Existing notifications — content redesign only (schedule unchanged)

- [ ] Replace `scheduleMorningDigest` body with the highest-priority `InsightCard`
  body from yesterday's `InsightEngine` run; use the nothing-to-report fallback
  sentence when no strong card exists.
- [ ] Replace `scheduleWeeklyReport` body with the highest-priority weekly
  `InsightCard` body; use the honest fallback when nothing stands out.
- [ ] Add active-session guard to both: if `focusSession.isActive` at fire time,
  delay notification by 30 minutes rather than interrupting.
- [ ] Make `morningDigestEnabled` an explicit boolean in `AppSettings` (currently
  implicit: fires whenever `wakeUpTime` is set).

#### 8b. New channels

- [ ] Add `achievements` channel — `Importance.DEFAULT`, badge=true, no
  vibration. Used for all achievement unlocks including hidden ones (hidden
  achievements use same channel, `sound: false`).
- [ ] Add `insights` channel — `Importance.LOW`, no sound, no vibration. Used
  only for one-time pattern-discovery notifications.
- [ ] Add `resistance` channel — `Importance.DEFAULT`, opt-in, default off.
  Used only for temptation-spike notifications.

#### 8c. New notifications

- [ ] Achievement unlock: fires immediately from `AchievementEngine` when an
  achievement is earned. Hidden achievements: same channel, no sound. Does not
  respect quiet hours.
- [ ] Pattern discovery: fires once per pattern when `InsightEngine` first crosses
  a sample-size confidence gate. Body never includes the actual insight (draws to
  app). Tracked via `shownPatternInsightIds: string[]` in `AppSettings` — never
  repeats. Respects quiet hours and active-session guard.
- [ ] Auto-reschedule confirmation: fires immediately when a task is moved by the
  auto-reschedule feature, only when `savedMinutes > 5`. Channel:
  `task-reminders`. Controlled by `rescheduleNotificationsEnabled` (default true).
- [ ] Arbitration notification: fires when session arbitration holds or switches
  a task. Two variants (session switched / schedule held). Channel:
  `task-reminders`. Informational, no action required.
- [ ] Temptation spike: fires when `TemptationLogManager` detects ≥threshold
  attempts in any rolling 60-minute window. Minimum 2-hour cooldown. Does NOT
  fire during active focus session. Opt-in only (`temptationSpikeEnabled: boolean`,
  default false). Requires a broadcast from `TemptationLogManager.kt` at log-write
  time.
- [ ] Block list suggestion: fires at most once per week after weekly insight is
  computed, only when top distracting app is not in the always-block list. Includes
  a deep-link action button to pre-select that app in the block list. 30-day
  per-app cooldown after a dismiss. Controlled by `blockSuggestionEnabled` (default
  true).
- [ ] Week-ahead preview: fires Sunday at 7pm (configurable). Forward-looking
  counterpart to the weekly report. Body: task count + first task name/time.
  Controlled by `weekAheadEnabled` (default true when `weeklyReportEnabled` is true).

#### 8d. New AppSettings fields

- [ ] Add and default: `morningDigestEnabled: boolean` (true), 
  `achievementNotificationsEnabled: boolean` (true),
  `patternInsightNotificationsEnabled: boolean` (see `[!]` item below),
  `rescheduleNotificationsEnabled: boolean` (true),
  `blockSuggestionEnabled: boolean` (true),
  `weekAheadEnabled: boolean` (true),
  `temptationSpikeEnabled: boolean` (false),
  `temptationSpikeThreshold: number` (8),
  `shownPatternInsightIds: string[]` ([]).
- [ ] Add `bedTime?: string` to `UserProfile` (default 22:00 if unset) for quiet
  hours upper bound.

#### 8e. Quiet hours

- [ ] Apply quiet hours (after `bedTime`, before `wakeUpTime`) to: pattern
  discovery and block-suggestion notifications.
- [ ] Exempt from quiet hours: achievement notifications, temptation spike,
  task-reminder-channel notifications.

### 9. Additional UX ideas

- [ ] **In-app debrief moment**: bottom sheet appearing within 30 minutes of a
  session ending. Single highest-priority insight card for that session. Tracked
  via `lastShownDebriefSessionId` in `AppSettings`.
- [ ] **Focus session intent screen**: 3-second micro-screen before session starts
  showing task name, duration, and one prior-session fact ("Clean last time" /
  "{N} attempts last time" / "First session on this task"). Tracked via
  `lastSessionResultByTaskId`.
- [ ] **Smarter morning digest timing**: if the app has been opened within the
  last 10 minutes when the notification would fire, skip it (content already
  visible). One-condition addition to `scheduleMorningDigest`.
- [ ] **Nothing-to-report as a feature**: the `nothing_to_report` card must be
  allowed to lead the weekly stats view with its exact text — do not suppress it,
  minimize it, or replace it with a generic card when no strong signal exists.
- [ ] **Week-ahead preview** — see section 8c above (notification component).
- [ ] **"Good time to start" nudge**: opt-in only (`productiveWindowNudgeEnabled`,
  default false). Fires once per day when current time is within 15 minutes of the
  user's identified best focus window, they haven't started a session yet today,
  and they have at least one task scheduled. Only unlocks after 4+ weeks of data.

### 10. Validation gates

- [ ] FocusFlow JavaScript/unit/contract checks pass for the implemented slice.
- [ ] Generated Android source is produced through the documented Expo
  prebuild/config-plugin path.
- [ ] Native/Kotlin source compiles in a generated Android project.
- [ ] UsageStats permission grant, denial, return-from-settings, and unavailable
  states are tested on a supported Android device/emulator.
- [ ] Stats screens are visually checked at relevant phone sizes.
- [ ] No analytics path sends local data over the network.
- [ ] No notification carries raw analytics data — template-rendered strings only.
- [ ] Quiet hours, active-session guard, and per-notification cooldowns verified
  on device.
- [ ] No checklist item is marked `[x]` solely because a similarly named file or
  partial implementation exists.

---

## Open product decisions

These block the marked items until resolved. Do not implement the affected slice
with an assumed default — get an explicit answer first.

| ID | Question | Affects | Decision |
|---|---|---|---|
| `D1` | **Debrief moment + active session**: if a second focus session is already active when the debrief would show (back-to-back tasks), should it check for an active session and suppress, or show anyway and let the user dismiss? | Section 9 debrief moment | Not yet decided |
| `D2` | **Pattern insight notification default**: `patternInsightNotificationsEnabled` — default true (fires automatically when a real pattern is first identified) or opt-in (default false, user enables in settings)? The opt-in approach avoids the "the app is watching me" reaction; the default-true approach means most users will actually see it. | Section 8c pattern discovery, Section 8d settings fields | Not yet decided |

---

## Agent update protocol

After each implementation slice, update:

1. The affected checklist item(s).
2. `Current status` if the validation boundary or blocker changed.
3. A dated entry in the log below with files/areas reviewed, evidence, and
   remaining validation.

Keep source implementation and device validation separate. Do not mark a source
item complete if the code path is only a stub, mock, or similarly named partial
feature.

---

## Agent update log

| Date | Slice | Status | Evidence / remaining work |
|---|---|---|---|
| 2026-09-11 | Plan and tracker imported | `[ ]` | No implementation audit performed yet. |
| 2026-09-11 | Analytics foundation and Stats route | `[-]` | Added four bounded database queries, `AnalyticsSnapshot`, local insight rules/templates, and a new Yesterday/This Week/3-Month route. Focused tests/typecheck are blocked until package dependencies are installed; native hourly UsageStats, full task-result detail, achievements, and weekly standout persistence remain. |
| 2026-09-11 | Plan expanded — notifications + additional ideas | `[-]` | Sections 8 and 9 added to tracker matching plan additions. Two product decisions (D1, D2) recorded as open blockers. No implementation performed in this update — tracker reflects plan state only. |
| 2026-09-11 | Analytics foundation completion slice | `[-]` | Added native hourly UsageStats milliseconds, 3-month permission-gated phone metrics, fastest focus-window ratios with sample size, previous blocking-period counts, stable zero-filled trend weeks with `weeksWithData`, and per-source failure health. Unit/type/native verification remains blocked by missing dependencies and generated Android/device validation. |