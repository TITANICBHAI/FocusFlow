# Daily Allowance — Complete Bug Fix Plan

> Agent execution plan. Every claim is traced from source. No assumption is
> taken from the prior AI diagnosis without independent verification.
> Three targeted edits fix all four reported symptoms.

---

## Full system map — every file that touches allowance

| File | Role |
|---|---|
| `src/data/types.ts` | `DailyAllowanceEntry` type. Three modes: `count`, `time_budget`, `interval`. Fields: `countPerDay`, `budgetMinutes`, `intervalMinutes`, `intervalHours`. |
| `src/components/DailyAllowanceModal.tsx` | UI only. Calls `onSave → AppContext.setDailyAllowanceEntries`. Reads display data from `allowanceUsageCache` every 10 s. No enforcement logic. |
| `src/services/allowanceUsageCache.ts` | 10 s read-through cache over `SharedPrefsModule.getAllowanceSnapshot`. Parses `daily_allowance_used`. For interval, validates against `windowStartMs + windowMs` (not calendar date). |
| `src/context/AppContext.tsx` | Two write paths: `setDailyAllowanceEntries` (allowance only) and `setStandaloneBlockAndAllowance` (atomic block+allowance). Both call `SharedPrefsModule.setDailyAllowanceConfig`. Both re-sync `setAlwaysBlockActive` — daily allowance entries keep always-on enforcement armed. |
| `src/native-modules/SharedPrefsModule.ts` | TS bridge. `setDailyAllowanceConfig`, `getAllowanceSnapshot` (atomic read under lock), `resetDailyAllowanceUsage`. |
| `SharedPrefsModule.kt` | `setDailyAllowanceConfig`: writes `daily_allowance_config`, broadcasts `ACTION_ALLOWANCE_CONFIG_CHANGED`. `getAllowanceSnapshot`: reads inside `ALLOWANCE_USAGE_LOCK`. `resetDailyAllowanceUsage`: removes pkg or clears full JSON under lock. |
| `AppBlockerAccessibilityService.kt` | **Primary authority.** Live enforcement, session tracking, checkpointing, timed expiry. |
| `ForegroundTaskService.kt` | **Secondary, fallback only.** 60 s UsageStats sync. Fallback expiry timer for `time_budget` only (interval excluded by contract). |
| `BlockedAppDismissalPolicy.kt` | Pure policy. `shouldRetry` accepts `allowanceExhausted: Boolean`. No allowance state of its own. |
| `VpnPolicyCoordinator.kt` | References `allowanceExhausted` in comments only. No allowance logic. |
| `src/hooks/useTimer.ts` | **UI only.** `useTaskTimer` / `useCountdown` drive React progress bars. No SharedPreferences access, no enforcement role. Confirmed not a bug source. |
| `active.tsx` | Reads `getAllowanceUsageSnapshot` on mount and every 30 s. Display only. |
| `tests/contracts/foregroundTaskService.test.ts` | Asserts `foregroundAllowanceExpiry?.let { (pkg, expiryMs) ->` and `timeBudgetPkgs[pkg] ?: return@let` exist. Fix must preserve or update these strings. |

---

## SharedPreferences keys (all writes under `ALLOWANCE_USAGE_LOCK`)

| Key | Written by | Read by | Content |
|---|---|---|---|
| `daily_allowance_config` | JS → SharedPrefsModule.kt | AS, FTS | JSON array of `DailyAllowanceEntry` |
| `daily_allowance_used` | AS (primary), FTS (raises only) | AS, FTS, SharedPrefsModule.kt | `{pkg: {mode, date, count, usedMs, windowStartMs}}` |
| `active_session_pkg` | AS | FTS, SharedPrefsModule.kt | Package currently live-tracked |
| `active_session_last_checkpoint_ms` | AS (every 15 s) | AS, FTS | Freshness signal — FTS skips pkg when age < 2 min |
| `active_session_end_ms` | AS | AS (on restore) | Scheduled session end used by `restoreAllowanceSession` |
| `usage_stats_sync` | FTS (on stale-session handoff) | AS (in checkpoint) | Handoff timestamp so AS doesn't double-add accounted interval |

---

## AS startup sequence (verified `onServiceConnected`)

```
1. restoreAllowanceSession()             sets currentTimedPkg from persisted signal if fresh (<2 min)
2. reconcileCountAllowances()            raises count from UsageEvents; never lowers
3. registerScreenStateReceiver()         handles SCREEN_OFF, USER_PRESENT, ACTION_ALLOWANCE_CONFIG_CHANGED
4. recoverForegroundAllowanceSession()   if currentTimedPkg still null, uses UsageStats to seed timed
                                         session; COUNT IS EXPLICITLY EXCLUDED here
5. startForegroundWatchdog()             1.5 s polling loop → checkForegroundNow()
```

---

## Confirmed bugs (source-traced)

---

### Bug 1 — COUNT: app blocked ~1.5 s after the user opens it

**Root cause: `checkForegroundNow` has no active-session guard.**

#### Trace

`onAccessibilityEvent` fires (pkg = count-allowed app, limit = 1):
```
isAllowanceAvailable → count=0 < limit=1 → TRUE
currentTimedPkg != pkg → recordAllowanceOpen()
  prevCount=0 → writes count=1 to daily_allowance_used
currentTimedPkg = pkg          ← session marked active in memory
persistActiveSessionSignal()   ← signal written to prefs
lastBlockedPkg = null; return  ← app let through
```

`startForegroundWatchdog()` runs every 1.5 s → `checkForegroundNow()`:
```kotlin
// No guard for currentTimedPkg anywhere above this point
val allowanceEntry = findAllowanceEntry(pkg)
if (allowanceEntry != null && !isAllowanceAvailable(pkg, allowanceEntry)) {
    // isAllowanceAvailable: count(1) >= limit(1) → FALSE → enters block
    // lastBlockedPkg = null → samePackage = false → 2 s cooldown does not apply
    handleBlockedApp(...)        // KILLS THE ACTIVE ALLOWED SESSION
    scheduleRetryCheck(...)      // continues re-blocking every RETRY_INTERVAL_MS
}
```

`scheduleRetryCheck` also evaluates `allowanceExhausted = !isAllowanceAvailable(pkg, entry)`,
which stays TRUE while the count is at limit, so retries perpetuate the block indefinitely.

For countPerDay = N: opens 1 through N−1 work; the N-th open is killed at T+1.5 s.

**Why the main event path doesn't have this bug:** It calls `isAllowanceAvailable` BEFORE
`recordAllowanceOpen`. The watchdog calls it AFTER, with no record that this is an active session.

#### Fix — `AppBlockerAccessibilityService.kt`, `checkForegroundNow()`

Locate the lines:
```kotlin
val allowanceEntry = findAllowanceEntry(pkg)
if (allowanceEntry != null && !isAllowanceAvailable(pkg, allowanceEntry)) {
```

Insert immediately **above** them:
```kotlin
// Skip exhaustion enforcement while an active allowance session is in progress.
// For count mode: isAllowanceAvailable returns false the instant recordAllowanceOpen
// increments the counter (that is its contract) — but the session is still valid.
// For timed modes: usedMs is only updated at checkpoint boundaries, so
// isAllowanceAvailable can transiently return false before the timer fires.
if (currentTimedPkg?.equals(pkg, ignoreCase = true) == true) {
    lastBlockedPkg = null
    lastBlockedAtMs = 0L
    return
}
```

**Thread safety:** `currentTimedPkg` and `checkForegroundNow` both run on
`Handler(Looper.getMainLooper())`. No concurrency concern.

**Correctness on true exhaustion:** When the user opens the app beyond the limit,
`currentTimedPkg` is null (cleared when they last switched away). The block fires correctly.

---

### Bug 2 — INTERVAL: premature exhaustion after closing the app early

**Root cause: `syncAllowanceFromUsageStats` in FTS uses
`queryUsageStats(INTERVAL_DAILY, windowStartMs, now)` which returns the full
calendar-day total, not window-scoped time.**

#### Trace

FTS `syncAllowanceFromUsageStats()` runs every 60 s. For interval packages:

```kotlin
val actualMs = usm.queryUsageStats(
    UsageStatsManager.INTERVAL_DAILY,  // ← cannot produce window-scoped data
    windowStartMs,                      // e.g. 10:30 AM
    now,                                // e.g. 10:35 AM
)?.firstOrNull { it.packageName.equals(pkg, ignoreCase = true) }
 ?.totalTimeInForeground ?: 0L
```

Android's `queryUsageStats(INTERVAL_DAILY, begin, end)` aggregates by **calendar day**.
When `begin` and `end` are within the same day, Android returns one record whose
`totalTimeInForeground` is cumulative foreground time since midnight — not since `begin`.

**Concrete scenario (intervalMinutes=10, intervalHours=1):**

| Time | Event | `usedMs` on disk |
|---|---|---|
| 9:00 AM | Open, use 8 min, close | AS writes 8 min |
| 10:00 AM | Window expires. Open again → new window (`windowStartMs=10:00`, `usedMs=0`) | 0 |
| 10:04 AM | Close after 4 min. AS writes 4 min | 4 min |
| 10:30 AM | FTS sync. `hasFreshActiveAllowanceSession=false`. `queryUsageStats(INTERVAL_DAILY, 10:00, 10:30)` → returns `totalTimeInForeground = 8+4 = 12 min`. `coerceAtMost(10 min)` → **writes `usedMs = 10 min`** | 10 min |
| 10:31 AM | User opens app. `isAllowanceAvailable → 10 >= 10 → exhausted`. Only 4 min of the 10-min window was used. | — |

`hasFreshActiveAllowanceSession` correctly blocks FTS during an active session. The bug
manifests after the session ends, when FTS reads a stale full-day total.

**Why time_budget is less affected:** For `time_budget`, the full-day
`totalTimeInForeground` from midnight IS the correct reference. The interval mode requires
window-scoped measurement, which `INTERVAL_DAILY` cannot provide.

#### Fix — `ForegroundTaskService.kt`, `syncAllowanceFromUsageStats()`

Replace the `val actualMs = if (intervalConfig != null) { usm.queryUsageStats(...) }` block:

```kotlin
val actualMs = if (intervalConfig != null) {
    // queryUsageStats(INTERVAL_DAILY) returns the full calendar-day total regardless of
    // the begin parameter — it cannot produce window-scoped data. Use queryEvents to
    // measure foreground time precisely within [windowStartMs, now].
    try {
        val windowEvents = usm.queryEvents(windowStartMs, now)
        val ev = android.app.usage.UsageEvents.Event()
        val resumeType =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED
            else
                android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND
        val pauseType =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED
            else
                android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND
        var segmentStart = 0L
        var windowTotal  = 0L
        while (windowEvents.hasNextEvent()) {
            windowEvents.getNextEvent(ev)
            if (!ev.packageName.equals(pkg, ignoreCase = true)) continue
            when (ev.eventType) {
                resumeType -> segmentStart = ev.timeStamp
                pauseType  -> if (segmentStart > 0L) {
                    windowTotal  += (ev.timeStamp - segmentStart).coerceAtLeast(0L)
                    segmentStart  = 0L
                }
            }
        }
        // App still foreground at query time — include the open segment.
        if (segmentStart > 0L) windowTotal += (now - segmentStart).coerceAtLeast(0L)
        windowTotal
    } catch (_: Exception) { 0L }
} else {
    statsMap[pkg]?.totalTimeInForeground ?: 0L
}.coerceAtMost(limitMs)
```

**Placement:** The `queryEvents` call runs OUTSIDE `synchronized(ALLOWANCE_USAGE_LOCK)`.
Only the `blockPrefs.edit()...apply()` write is inside the lock. This mirrors the existing
pattern where `queryUsageStats` also runs before the lock.

**API version guard:** Use the same `Build.VERSION_CODES.Q` pattern for
`ACTIVITY_RESUMED` / `ACTIVITY_PAUSED` as `reconcileCountAllowances()` in AS uses — find
that method and copy the guard exactly.

---

### Bug 3 — TIME BUDGET: enforcement fires at inconsistent times

**Root cause: AS's `scheduleTimedExpiry` (AS Handler) and FTS's
`scheduleAllowanceExpiry` (FTS Handler) are independent timers on independent loopers.
Neither can cancel the other.**

#### Trace

For `time_budget`, two timers are independently armed:

**AS timer** — `scheduleTimedExpiry(pkg, sessionEndMs)`:
- `sessionEndMs = open_time + (budgetMs − prevUsedMs_at_open)`.
- Runs on `AppBlockerAccessibilityService.handler`.
- Uses live 15 s-checkpointed data.

**FTS timer** — `scheduleAllowanceExpiry(pkg, expiryMs, budgetMs)`:
- `expiryMs = fts_sync_time + (budgetMs − usageStats_total_at_sync)`.
- Runs on `ForegroundTaskService.handler` — a completely different object.
- Uses 60 s-stale `queryUsageStats(INTERVAL_DAILY)` from midnight.

When FTS's UsageStats total is higher than AS's checkpointed total (e.g. UsageStats
includes pre-allowance foreground time from earlier in the day), FTS calculates `remaining
< actual`, schedules expiry earlier than AS → premature block. When UsageStats lags (up to
30 min on some devices), FTS schedules later → double or missed enforcement.

For `interval`, FTS does NOT schedule `scheduleAllowanceExpiry` (confirmed:
`timeBudgetPkgs[pkg] ?: return@let` guard prevents it). The contract test asserts this.
Interval enforcement unreliability is caused by Bug 2 (wrong `usedMs`), not two timers.

#### Fix — `ForegroundTaskService.kt`, end of `syncAllowanceFromUsageStats()`

Locate:
```kotlin
foregroundAllowanceExpiry?.let { (pkg, expiryMs) ->
    scheduleAllowanceExpiry(pkg, expiryMs, timeBudgetPkgs[pkg] ?: return@let)
} ?: run {
    allowanceExpiryRunnable?.let { handler.removeCallbacks(it) }
    allowanceExpiryRunnable = null
}
```

Replace with:
```kotlin
foregroundAllowanceExpiry?.let { expiry ->
    val budgetMs = timeBudgetPkgs[expiry.pkg] ?: return@let
    // Only arm the FTS fallback timer when the AS session signal is absent or stale.
    // When the signal is fresh (checkpoint < 2 min ago), AS's scheduleTimedExpiry()
    // is already running with live checkpoint data and is more accurate than
    // 60 s-stale UsageStats. A second independent timer against stale data causes
    // early or double enforcement.
    if (!hasFreshActiveAllowanceSession(expiry.pkg, System.currentTimeMillis())) {
        scheduleAllowanceExpiry(expiry.pkg, expiry.expiryMs, budgetMs)
    } else {
        // AS owns the session — cancel any leftover FTS timer so it doesn't fire
        // after AS has already handled enforcement.
        allowanceExpiryRunnable?.let { handler.removeCallbacks(it) }
        allowanceExpiryRunnable = null
    }
} ?: run {
    allowanceExpiryRunnable?.let { handler.removeCallbacks(it) }
    allowanceExpiryRunnable = null
}
```

**Contract test note:** The existing test asserts
`'foregroundAllowanceExpiry?.let { (pkg, expiryMs) ->'`. This string changes to
`{ expiry ->`. Update `tests/contracts/foregroundTaskService.test.ts` to match:
```typescript
expect(foregroundTaskService).toContain('foregroundAllowanceExpiry?.let { expiry ->');
```

---

## Claims from prior analysis — debunked after tracing

| Claim | Verdict |
|---|---|
| "UsageStats double-counts the active session" | **False.** `hasFreshActiveAllowanceSession` (checkpoint every 15 s, TTL = 2 min) correctly blocks FTS while AS is live. |
| "Pre-midnight probe bleeds into count" | **False.** `reconcileCountAllowances` requires `event.timeStamp >= midnightMs` before incrementing. Pre-midnight events only set `lastForegroundPackage` for continuity. |
| "Interval has no explicit expiry timer" | **False.** `scheduleTimedExpiry` is called for interval at onAccessibilityEvent line ~1481, identically to time_budget. |
| "`useTimer.ts` is part of allowance accounting" | **False.** Confirmed: React hook only, no SharedPreferences access. |
| "FTS scheduleAllowanceExpiry fires for interval" | **False.** The `timeBudgetPkgs[pkg] ?: return@let` guard prevents it. Contract test asserts this. Fix must preserve this guard. |
| "System needs architectural replacement" | **False.** Three targeted edits fix all reported symptoms. |

---

## Exact reproduction sequences

### Count — "blocked after a few seconds"
1. Add any app, mode = Count, limit = 1. Start any block session.
2. Open the allowed app.
3. Wait 1.5–3 s without touching anything.
4. **Observe:** app dismissed. For limit = N, the N-th open is killed at ~1.5 s.

### Interval — "closing early exhausts the allowance"
1. Set interval allowance: intervalMinutes = 10, intervalHours = 1.
2. Open app, use all 10 minutes (intentionally exhaust first window).
3. Wait 1 hour for the window to expire.
4. Open app (new window). Use for 3–4 minutes, then close.
5. Wait 60–90 s for FTS sync.
6. Open app. **Observe:** blocked immediately. `daily_allowance_used.usedMs` equals or exceeds `intervalMs` because FTS added the 8 min from step 2 to the 4 min from step 4.

### Time Budget — "enforcement fires at wrong time"
1. Set time_budget: budgetMinutes = 10. Start block session.
2. Open app, use continuously.
3. Repeat across different FTS sync timings.
4. **Observe:** enforcement fires anywhere from ~9 min (FTS inflated) to ~10:30 min (FTS lagging). Variance is the FTS timer competing with the AS timer.

---

## Regression tests to add

### Count
1. With `currentTimedPkg = pkg`, `checkForegroundNow` must not call `handleBlockedApp`.
2. With countPerDay = 1, after switching away (currentTimedPkg cleared), next open must block.
3. With countPerDay = 2, two opens + two switch-aways, third open must block immediately.

### Interval
4. FTS sync with mock UsageEvents showing 4 min in `[windowStartMs, now]` must write `usedMs = 4`, not 10 (capped full-day), even if `totalTimeInForeground` = 12 min.
5. Two separate interval windows (8 min + 4 min used) — sync during second window must write `usedMs = 4`.
6. `now > windowStartMs + windowMs` (expired window) — FTS must skip the pkg; `usedMs` unchanged.

### Time Budget timer coordination
7. When `hasFreshActiveAllowanceSession(pkg) = true`, `syncAllowanceFromUsageStats` must NOT call `scheduleAllowanceExpiry`. Assert `allowanceExpiryRunnable` remains null.
8. When signal is stale and app is foreground, FTS must call `scheduleAllowanceExpiry`. Assert `allowanceExpiryRunnable` is non-null.

### General
9. `BlockedAppDismissalPolicy.shouldRetry` with `allowanceExhausted=true` and `lastSeenPackage=blockedPackage` returns true. (Guard against regression.)
10. `recoverForegroundAllowanceSession` with a count-mode app foreground must NOT set `currentTimedPkg` (count is excluded from recovery path).

---

## Validation checklist for agent

- [ ] Bug 1 guard uses `.equals(pkg, ignoreCase = true)`, not `==`.
- [ ] Bug 1 guard is inserted **before** `val allowanceEntry = findAllowanceEntry(pkg)` — avoids the lookup entirely during an active session.
- [ ] Bug 2 `queryEvents` call is **outside** `synchronized(ALLOWANCE_USAGE_LOCK)`. Only `blockPrefs.edit()` is inside.
- [ ] Bug 2 API guard matches the pattern in `reconcileCountAllowances()` exactly (`Build.VERSION_CODES.Q`, `ACTIVITY_RESUMED` / `ACTIVITY_PAUSED` vs `MOVE_TO_FOREGROUND` / `MOVE_TO_BACKGROUND`).
- [ ] Bug 3 preserves `timeBudgetPkgs[expiry.pkg] ?: return@let` inside the let-block.
- [ ] `hasFreshActiveAllowanceSession` called with `System.currentTimeMillis()` at call time, not the `now` captured at the top of `syncAllowanceFromUsageStats`.
- [ ] Contract test `foregroundTaskService.test.ts` updated if the lambda parameter rename `{ (pkg, expiryMs) ->` → `{ expiry ->` is made.
- [ ] Brace balance after edits:
  ```
  python3 -c "s=open('AppBlockerAccessibilityService.kt').read(); print(s.count('{') - s.count('}'))"
  python3 -c "s=open('ForegroundTaskService.kt').read(); print(s.count('{') - s.count('}'))"
  ```
  Both → `0`.
- [ ] All writes to `PREF_DAILY_ALLOWANCE_USED` still occur inside `ALLOWANCE_USAGE_LOCK`.
- [ ] TypeScript typecheck passes. No TS changes required.
