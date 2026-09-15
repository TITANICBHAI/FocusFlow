# FocusFlow Migration — Stage 3 (Claude / Sonnet 5)
## Pipeline position: Gemini (done) → Replit (done) → Claude (you, now) → GPT Terra → Claude (final review)

This stage has **3 tracks**. Two of them are genuinely independent of each other and
can run in **parallel, in two separate Claude conversations**. The third depends on
both and must run after.

```
Track A (Room database)  ─┐
                           ├──→  Track C (ViewModels) ──→ hand off to GPT Terra
Track B (PIN system)     ─┘
```

**Why A and B can run in parallel**: Room is SQLite-backed, PIN storage is
SharedPreferences-backed. They share no files, no naming, no dependency in either
direction. There's no coordination risk in doing them at the same time.

**Why C can't join them**: the ViewModels call real methods on the Room repositories
(Track A) and the PIN manager (Track B), plus Replit's and Gemini's already-real
repositories from Stages 1–2. It needs actual signatures to wire against, not a
description of what they'll probably look like.

Set effort to **High** for Tracks A and B. Set effort to **Max** for Track C — it's
the integration point where three other stages' worth of real code all has to line
up correctly at once.

---

## TRACK A — Room database layer (run in its own conversation)

### Context
FocusFlow is a real, published app; this database layer is what everything else in
the app reads and writes through. Read `focusflowkotlin/ARCHITECTURE.md` section 3.3
in full before starting, and the original data shapes in
`artifacts/focusflow/src/data/types.ts` and the existing schema in
`artifacts/focusflow/src/data/database.ts` (the current `CREATE TABLE` statements and
CRUD functions — your Room entities and DAOs should map onto this shape faithfully,
not redesign it).

### What NOT to build here
The `settings` table in the current schema is a single serialized JSON blob, not
structured rows — it does **not** get a Room entity. Settings persistence is handled
separately (SharedPreferences/DataStore facade), not part of this track.

### Target
```
focusflowkotlin/app/src/main/java/com/tbtechs/focusflow/data/local/
  FocusFlowDatabase.kt
  dao/TaskDao.kt
  dao/FocusSessionDao.kt
  dao/FocusOverrideDao.kt
  dao/DailyCompletionDao.kt
  entity/TaskEntity.kt
  entity/FocusSessionEntity.kt
  entity/FocusOverrideEntity.kt
  entity/DailyCompletionEntity.kt
focusflowkotlin/app/src/main/java/com/tbtechs/focusflow/data/repository/
  TaskRepository.kt
  FocusSessionRepository.kt
```

### Build
1. Four Room entities, one per table in the existing schema (`tasks`,
   `focus_sessions`, `focus_overrides`, `daily_completions`) — column-for-column
   faithful to `types.ts`'s field definitions for each shape, not a reinterpretation.
2. Matching DAOs exposing `Flow<List<T>>` queries where the current code does a
   fetch-all, plus the specific lookups the existing CRUD functions in `database.ts`
   perform (don't invent new query shapes it doesn't need).
3. `FocusFlowDatabase.kt` wiring the four DAOs together.
4. `TaskRepository` and `FocusSessionRepository` wrapping the DAOs — these are what
   Track C's ViewModels will call.

### Rules
- If a field's exact type or nullability in `types.ts` is ambiguous, don't guess —
  flag it rather than silently picking one.
- Don't add fields, tables, or indices that aren't implied by the current schema.

### Report
```
| Entity/DAO/Repository | Source shape it maps to | Public methods (repository only) | Flags |
|---|---|---|---|
```

---

## TRACK B — PIN / Keystore system (run in its own conversation, parallel to Track A)

### Context
This gates every settings change in the app. Read `focusflowkotlin/ARCHITECTURE.md`
sections 3.9 and 6 (the PIN-hash-upgrade risk item) in full before starting, plus the
original `artifacts/focusflow/src/utils/pinCrypto.ts`,
`artifacts/focusflow/src/utils/pinReuseTracker.ts`, and
`artifacts/focusflow/android-native/app/src/main/java/com/tbtechs/focusflow/modules/SessionPinModule.kt`.

### Target
```
focusflowkotlin/app/src/main/java/com/tbtechs/focusflow/domain/
  PinManager.kt
  PinReuseTracker.kt
  PinSessionState.kt
```

### Build
1. **`PinManager.kt`**: hash a PIN using `SecretKeyFactory` with
   `PBKDF2WithHmacSHA256`, wrap the derived key with an Android Keystore AES key
   (the Keystore key wraps the hash — it never stores the PIN or the raw hash
   itself). Store the wrapped hash and salt in SharedPreferences.
2. **Legacy migration, non-negotiable**: existing users have a plain SHA-256 hash
   from the current app (`pinCrypto.ts`'s algorithm — read it to get this exactly
   right). You cannot upgrade a hash without the plaintext PIN, so: on the next
   successful unlock attempt, verify against the *old* SHA-256 hash first; if it
   matches, immediately re-hash with the new PBKDF2+Keystore scheme and overwrite the
   stored value. Do not force a PIN reset on existing users.
3. **`PinReuseTracker.kt`**: direct port of the last-5-hash reuse check from
   `pinReuseTracker.ts` — same window size, same comparison logic.
4. **`PinSessionState.kt`**: holds an in-memory unlocked-until-timestamp, mirroring
   what `SessionPinModule.kt` does today (read it for the exact timeout value and
   whether it persists across process death or not — don't assume, check).

### Rules
- Never log a PIN, a hash, or a salt, at any verbosity level.
- If you're unsure whether a value should persist across process death or not:
  check `SessionPinModule.kt`'s actual behavior rather than picking whichever seems
  more robust.

### Report
```
| File | What it does | Legacy-hash migration path confirmed? (Y/N) | Flags |
|---|---|---|---|
```

---

## TRACK C — ViewModels (run after both A and B are complete and verified)

### Before starting
Bring into this conversation: Track A's real output, Track B's real output, Replit's
Stage 2 report + real repository files, and Gemini's Stage 1 report + real
enforcement files. Open the actual files — don't work from memory of what they were
supposed to contain.

### Target
```
focusflowkotlin/app/src/main/java/com/tbtechs/focusflow/ui/
  TaskViewModel.kt
  SettingsViewModel.kt
  FocusSessionViewModel.kt
  AppBootViewModel.kt
```

### Build — this is the contract GPT Terra will code against, so get names exact
```
TaskViewModel
  tasks: StateFlow<List<Task>>                           ← backed by Track A's TaskRepository
  addTask(), updateTask(), deleteTask(), completeTask(taskId),
  skipTask(taskId), extendTaskTime(taskId, minutes)

SettingsViewModel
  settings: StateFlow<AppSettings>                       ← backed by Replit's SettingsRepository
  updateSettings(partial), setDailyAllowanceEntries(entries), setBlockedWords(words),
  setRecurringBlockSchedules(schedules), setStandaloneBlock(config),
  setQuickBlockTemporary(config), setStandaloneBlockAndAllowance(config)
  isPinSessionActive: StateFlow<Boolean>                 ← backed by Track B's PinSessionState
  verifyPin(pin): Boolean, setPin(newPin), rotatePin(oldPin, newPin)
                                                          ← backed by Track B's PinManager + PinReuseTracker

FocusSessionViewModel
  focusSession: StateFlow<FocusSession?>                 ← backed by Track A's FocusSessionRepository
  focusViolationApp: StateFlow<String?>
  startFocusMode(taskId), stopFocusMode()                ← also calls Replit's ForegroundServiceController

AppBootViewModel
  isLoading, isDbReady, isDbUnrecoverable: StateFlow<Boolean>
```
Note that PIN methods live on `SettingsViewModel`, not a separate ViewModel — this
keeps the "four ViewModels only" contract GPT Terra was given intact while still
exposing PIN functionality somewhere reachable.

### Rules
- Every method above must call a method that actually exists on the real repository
  or manager it's wired to — not a plausible-sounding one. If a needed method is
  missing from Track A, B, Replit's, or Gemini's output, stop and say exactly what's
  missing rather than stubbing it silently.
- Preserve `AppContext.init()`'s boot-sequence order where it matters: settings load
  (with its timeout-then-default fallback) before task refresh, before active-session
  recovery. `ARCHITECTURE.md` section 2 documents this sequence — don't reorder it
  without a reason you can name.

### Report
```
| ViewModel | StateFlow fields (name, backing source) | Methods (name, backing call) | Flags |
|---|---|---|---|
```
This report is what GPT Terra's Stage 4 prompt needs handed to it, alongside the
real ViewModel files themselves.

---

## TRACK D — Analytics engine (4 prompts, run after Tracks A+B+C)

Track D is split into 4 prompts to keep each one focused. These can be run in their
own conversation after Track C is done — they don't depend on Track C's output, only
on the real source files in the zip. The analytics layer is mostly built in TS; your
job here is to complete what's partial, then produce clean Kotlin equivalents. Don't
port incomplete TS and call it done — complete it first.

**Source files are in `updated.zip`. Read each one before working on it.**

---

### TRACK D — PROMPT 1 of 4: Complete AchievementEngine, then port it

**Read first:** `src/services/analytics/AchievementEngine.ts` in full (103 lines).

**The 5 achievements that already exist — do not recreate or duplicate these:**

| ID | Category | Condition (exact, from source) |
|---|---|---|
| `RESISTANCE_10_CLEAN_SESSIONS` | resistance | `lifetime.cleanSessions >= 10` |
| `HONEST_ESTIMATOR` | honesty | 5+ entries in `snapshot.tasks.estimationErrorMinutes`, average absolute error `<= 15` |
| `PRESENCE_7_DAYS` | presence | `snapshot.window === 'week'` and all 7 entries in `tasks.byDayOfWeek` have `total > 0` |
| `PATTERN_BREAKER` | pattern_breaking | `blocking.totalAttempts >= 10` and `topAppShare < 0.5` |
| `QUIET_WIN` | hidden | `lifetime.totalFocusMinutes >= 60`, `sessions.cleanCount > 0`, `blocking.totalAttempts === 0` |

**`LifetimeStats` currently has exactly these 6 fields — nothing else:**
`completedTasks, totalSessions, cleanSessions, totalFocusMinutes,
totalOverrideAttempts, currentStreakDays`. Any new achievement condition below that
needs something else must add that field explicitly — it is real new work, not a
missing lookup.

**Step 1 — Add these new achievements to the `ACHIEVEMENTS` array** (edit TS, not
Kotlin yet). These are chosen specifically to not overlap the 5 that already exist:

| ID | Category | Title | Condition |
|---|---|---|---|
| `IRON_SESSION` | resistance | Iron Session | A single session in the current snapshot with `override_count === 0` — this is a *per-session* achievement, distinct from `RESISTANCE_10_CLEAN_SESSIONS` which is lifetime cumulative. Check `snapshot.sessions.cleanCount > 0` for the current window. |
| `THREE_WEEKS` | presence | Three Weeks | `lifetime.currentStreakDays >= 21` — this field already exists, no new field needed. |
| `LONG_GAME` | hidden | The Long Game | 3 or more entries in `snapshot.tasks.estimationErrorMinutes` are `<= -30` (finished 30+ minutes early, 3 times in the current snapshot). No new field needed — this reads `estimationErrorMinutes`, which already exists. |
| `SELF_AWARE` | pattern_breaking | Self-Aware | Requires knowing when an app was added to the block list. Check `src/data/types.ts` for a `changed_at` or similar timestamp on block-list entries before writing this condition — if no such timestamp exists on the block list data structure, **do not implement this achievement**; flag it in your report instead of inventing a field for it. |
| `BACK_AGAIN` | presence | Back Again | Requires "days since last session." **New field needed**: add `lastSessionAt: string \| null` to `LifetimeStats` in `database.ts`, and update `dbGetLifetimeStats()` to compute it (most recent `focus_sessions.started_at`). Condition: `lastSessionAt` is more than 5 days before `snapshot.generatedAt`, evaluated at the moment a new session starts. |
| `RESET` | hidden | Reset | Same new field as `BACK_AGAIN` (`lastSessionAt`). Condition: gap was more than 5 days AND `snapshot.tasks.completed >= 1` in the current window — came back after a gap and completed something. |

**Do not add** `SHARP_ESTIMATE` or `FIVE_SHARP` as separate achievements — they
would duplicate `HONEST_ESTIMATOR`'s ground (average estimation accuracy). If you
want a distinct honesty achievement, it needs to test something `HONEST_ESTIMATOR`
doesn't — e.g., a single estimate within 5 minutes, as a "first sign of it" moment
distinct from the sustained-accuracy achievement. Use your judgment, but don't ship
two achievements that fire at effectively the same time for the same reason.

For each hidden achievement: `hidden: true`, and the description should only say what
happened, not what to do to earn it.

**Step 2 — Port `AchievementEngine.ts` → `analytics/AchievementEngine.kt`:**
Direct TypeScript-to-Kotlin port of the completed file. Preserve all condition logic
exactly. `AchievementDefinition`, `AchievementState`, `evaluateAchievements`, and
`syncAchievements` map 1:1. The `condition` lambda signature in Kotlin:
`(lifetime: LifetimeStats, snapshot: AnalyticsSnapshot) -> Boolean`. The DB-backed
functions `dbGetEarnedAchievementIds()`/`dbRecordEarnedAchievements()` in Kotlin
read/write the `achievements` table (`id TEXT PRIMARY KEY, earned_at TEXT NOT NULL`)
in Room — confirm this table exists from Track A before this port, don't assume.

**Report:**
```
| Achievement ID | New or existing | LifetimeStats fields added | Flags |
|---|---|---|---|
```
List all 11 achievements (5 existing + 6 new, or 5 existing + 5 new if `SELF_AWARE`
was flagged and skipped).

---

### TRACK D — PROMPT 2 of 4: Port InsightTemplates + all three rule files

**Read first:** `src/services/analytics/InsightTemplates.ts` (147 lines),
`YesterdayRules.ts` (123 lines), `WeeklyRules.ts` (262 lines), `ThreeMonthRules.ts`
(230 lines). Read all four before writing any Kotlin.

These files are pure logic with no Android dependency. Direct port, syntax translation
only. The key patterns to preserve:

- `weekSeed(generatedAt: string): number` — deterministic week-based seed, used in
  every rule's `render` function to select sentence variants without randomness.
  Port the exact algorithm, not a reimplementation.
- `renderInsightVariant(id, seed, values)` — **takes three parameters, not two.**
  `values` is a `Record<string, TemplateValue>` used to fill `{placeholder}` tokens
  in the selected sentence via string replacement. Port both the deterministic
  variant selection (`seed % variants.length`) AND the placeholder substitution —
  dropping the third parameter will compile but silently produce sentences with
  literal `{peak_hour}` text still in them instead of real values.
- Each rule's `condition` lambda takes an `AnalyticsSnapshot` and returns `Boolean`.
- Each rule's `priority` lambda takes an `AnalyticsSnapshot` and returns a `Float` or `Int`.
- Each rule's `render` lambda takes `(snapshot, seed)` and returns an `InsightCard`.

Kotlin target paths:
- `analytics/InsightTemplates.kt`
- `analytics/rules/YesterdayRules.kt`
- `analytics/rules/WeeklyRules.kt`
- `analytics/rules/ThreeMonthRules.kt`

**Exact rule counts, confirmed by direct count in source — your ported files must
match these exactly:**
- `YesterdayRules.ts`: 7 rules
- `WeeklyRules.ts`: 11 rules
- `ThreeMonthRules.ts`: 8 rules

If your Kotlin port has a different count, you dropped or duplicated a rule — find
it before moving on, don't just note the mismatch and continue.

**Report:**
```
| Source file | Rule count (TS) | Rule count (Kotlin) | Flags |
|---|---|---|---|
```

---

### TRACK D — PROMPT 3 of 4: Port AnalyticsProcessor and InsightEngine

**Read first:** `src/services/analytics/AnalyticsProcessor.ts` (541 lines) and
`src/services/analytics/InsightEngine.ts` (189 lines). These are the largest files
in this track — read them fully before writing a line of Kotlin.

**AnalyticsProcessor:** computes an `AnalyticsSnapshot` from Room DAOs and
`UsageStatsRepository`. Key concerns during port:

- `buildAnalyticsSnapshot(window, options)` is the main entry point — in Kotlin this
  becomes a `suspend fun` in a `CoroutineScope`. It reads from **five** DB queries in
  parallel, not four: `dbGetTasksInDateRange`, `dbGetSessionsWithOverrideCount`,
  `dbGetEstimationErrors`, `dbGetTasksByHourOfDay`, `dbGetWeeklyCompletionRates`. All
  five need Room DAO equivalents from Track A before this port can compile.
- **Blocking/temptation data comes from `GreyoutModule.getTemptationLog()`** — not a
  separate temptation-log wrapper. In Kotlin this is `GreyoutRepository.getTemptationLog()`
  (Replit's Stage 2 port). If that method isn't on `GreyoutRepository` yet, this whole
  file can't compile — check before starting.
- The `UsageStats` gate (`window === 'three_months' && usageStatsPermission`) must be
  preserved exactly — the `phoneUsage` field must remain `null`/absent for any window
  that isn't `three_months`.
- `sessions.fastestWindowHour` **is fully implemented, confirmed by direct read** —
  it groups estimation-error ratios by start hour, averages them, and picks the
  lowest-ratio hour with the most samples as a tiebreak. Port this exactly as
  written; there is nothing to complete here, only to translate faithfully. It also
  returns `fastestWindowSampleSize` and `fastestWindowImprovementPercent` — both
  real fields, port all three together, not just the hour.
- `tasks.missed` counts tasks where `status === 'overdue'`, not a literal `'missed'`
  status string. Check the actual `Task.status` union in `types.ts` before assuming
  the string value — don't guess based on the field name `missed`.
- `tasks.resultRows` is a `{ title: string; status: Task['status'] }[]` array — this
  is what `TaskResultList` (Stage 4, GPT Terra) renders directly. Make sure it's
  populated in the Kotlin port, it's easy to overlook since it's a convenience field
  alongside the bucketed data.
- All arithmetic (completion rates, averages, percentages) must match the TS original
  to 2 decimal places — don't simplify fractions or change rounding.
- `AnalyticsSourceHealth` has exactly these fields — port all of them, not a subset:
  `tasks, sessions, estimationErrors, tasksByHour, weeklyRates, temptations,
  usageSummary?, usageHourly?`. Each is `'loaded' | 'unavailable' | 'failed'`.

**InsightEngine:** `buildInsights`, `selectWeeklyStandout`, `syncWeeklyStandout`.
- `syncWeeklyStandout` writes to the `weekly_insights` table (`week_start TEXT
  PRIMARY KEY, insight_id TEXT NOT NULL, selected_at TEXT NOT NULL`) — in Kotlin
  it's a `suspend fun` calling the Room DAO directly. Confirm this table exists from
  Track A before this port; don't assume a different name.
- The deduplication logic (`previousInsightIds` filter) must be preserved.
- `buildInsights` limit: `5` for `three_months`, `4` for other windows — this is a
  default parameter value in TS (`limit = snapshot.window === 'three_months' ? 5 : 4`),
  make sure the Kotlin default matches, not just the call sites you happen to find.

Kotlin target paths:
- `analytics/AnalyticsProcessor.kt`
- `analytics/InsightEngine.kt`

**Also build `ui/stats/StatsViewModel.kt` in this same prompt — this is confirmed,
not optional.** GPT Terra's Stage 4 Prompt 3 already assumes this ViewModel exists
and codes the stats screen against it; nothing else in the pipeline produces it. It
belongs here rather than in Track C specifically so Track D can keep running
independently of Track C, without a new cross-track dependency.

```kotlin
class StatsViewModel(
    private val analyticsProcessor: AnalyticsProcessor,
    private val insightEngine: InsightEngine,
    private val achievementEngine: AchievementEngine,   // from Prompt 1
) : ViewModel() {
    val analyticsSnapshot: StateFlow<AnalyticsSnapshot?>
    val insightCards: StateFlow<List<InsightCard>>
    val weeklyStandout: StateFlow<InsightCard?>
    val achievementState: StateFlow<AchievementState?>
    val loadState: StateFlow<StatsLoadState>   // Loading | Ready | PermissionNeeded | Unavailable | Error
    val activeWindow: StateFlow<AnalyticsWindow>

    fun setWindow(window: AnalyticsWindow)   // triggers a fresh buildAnalyticsSnapshot + buildInsights + syncAchievements
    fun reload()                              // re-runs the same pipeline for the current window
}
```
`loadState` must distinguish "waiting on UsageStats permission" from "genuine error"
from "not enough data yet" — these map to `PermissionGate`, `UnavailableGate`, and
`EmptyStatsState` respectively on the Stage 4 side, and GPT Terra's prompt expects
all three to be distinguishable, not collapsed into one generic error state.

**Report:**
```
| Issue | File | How resolved |
|---|---|---|
```
(One row per anything that differed from a direct port — missing TS features in Kotlin
stdlib, type mismatches, null safety changes, etc. Include `StatsViewModel.kt` in
this same report, not a separate one.)

---

### TRACK D — PROMPT 4 of 4: Add missing AppSettings fields + notification content scaffolding

This prompt adds the remaining settings fields that support the notification system,
and builds `NotificationRepository.kt` — the class that generates analytics-driven
notification content. This does NOT replace the existing notification scheduling in
`ForegroundTaskService` or `NotificationActionReceiver` — it adds content generation
only.

**Part A — Add missing AppSettings fields:**

In `AppSettings.kt` (the Kotlin data class from Track A), add these fields with their
defaults. Cross-check `src/data/defaultSettings.ts` for each default value — confirmed
absent from the current codebase by direct grep, all eight are real new work:

```kotlin
val morningDigestEnabled: Boolean = true,
val achievementNotificationsEnabled: Boolean = true,
val rescheduleNotificationsEnabled: Boolean = true,
val blockSuggestionEnabled: Boolean = true,
val weekAheadEnabled: Boolean = true,
val temptationSpikeEnabled: Boolean = false,
val temptationSpikeThreshold: Int = 8,
val bedTime: String = "22:00",   // pairs with wakeUpTime for quiet hours
```

Also add these two — they support the "additional ideas" section of
`STATS_INSIGHTS_PLAN.md` (the productive-window nudge and the focus-intent screen)
and are confirmed absent as well:
```kotlin
val productiveWindowNudgeEnabled: Boolean = false,   // opt-in, per the plan
val lastSessionResultByTaskId: Map<String, String> = emptyMap(),  // taskId -> last session outcome summary
```

**Already present — do not re-add, verify instead:** `patternInsightNotificationsEnabled`,
`shownPatternInsightIds`, and `lastShownDebriefSessionId` all already exist in the TS
codebase (`types.ts`/`defaultSettings.ts`). Confirm Track A's `AppSettings.kt` carried
them over correctly rather than adding duplicates.

**Part B — Create `NotificationRepository.kt`:**

```kotlin
class NotificationRepository(
    private val analyticsProcessor: AnalyticsProcessor,
    private val insightEngine: InsightEngine,
    private val settingsRepository: SettingsRepository,
) {
    // Returns the body string for the morning digest notification
    // Runs AnalyticsProcessor for yesterday, takes top InsightCard body
    // Falls back to "Ordinary day yesterday. You showed up." if only nothing_to_report fires
    suspend fun buildMorningDigestBody(): String

    // Returns the body string for the weekly report notification
    // Calls syncWeeklyStandout, returns its body
    // Falls back to "Consistent week. Nothing stood out."
    suspend fun buildWeeklyReportBody(): String

    // Returns the body for the week-ahead preview notification
    // Reads tomorrow+6 days of scheduled tasks, returns count + first task name/time
    // Returns null if no tasks scheduled next week
    suspend fun buildWeekAheadBody(): String?
}
```

Implement all three methods. Each one is a `suspend fun` that reads from repositories
and runs the analytics engine — no Android Context needed, no notification scheduling.
The actual notification scheduling stays in the existing alarm/service infrastructure.

**Update `NotificationChannels.kt`:** Add the three new channels:
- `achievements`: `NotificationManager.IMPORTANCE_DEFAULT`, badge=true
- `insights`: `NotificationManager.IMPORTANCE_LOW`, no sound
- `resistance`: `NotificationManager.IMPORTANCE_DEFAULT`

**Report:**
```
| Part | Files changed | Fields added / methods implemented | Flags |
|---|---|---|---|
```
