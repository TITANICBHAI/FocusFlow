# Daily Allowance — Complete Technical Review
> All three parts combined into one document.
> Every claim pinned to file and line.

## Review Tracking

Use the status marker beside each item as work progresses:

- `[ ]` Pending investigation or implementation
- `[x]` Fixed and verified
- `[✗]` Rejected, not applicable, or accepted as a limitation

Keep the file/line reference with every status change so each result remains traceable to the original finding.

### Master Checklist

#### Confirmed bugs

- [x] **1.1** `ACTION_USER_PRESENT` remaining time ignores the date and window reset — `AppBlockerAccessibilityService.kt:767-774` — reset-aware remaining-time calculation added; source-verified
- [x] **1.2** `parseUsage` returns stale interval usage after window expiry — `allowanceUsageCache.ts:37-39`, `SharedPrefsModule.kt:507` — cache now reads the native config and filters expired rolling windows; source-verified
- [x] **1.3** `force=true` joins a stale in-flight request — `allowanceUsageCache.ts:57-60` — forced reads now bypass the existing in-flight request; source-verified
- [x] **1.4** `checkForegroundNow` misses an already-foreground app after restart — `AppBlockerAccessibilityService.kt:562` — one-time 60-second foreground allowance recovery added; source-verified
- [x] **1.5** `timedExpireRunnable` fires after focus or standalone mode ends — `AppBlockerAccessibilityService.kt:2650-2661` — session cleanup and fire-time enforcement guard added; source-verified
- [x] **1.6** Allowance configuration changes leave an active timer and checkpoint loop running — `DailyAllowanceModal.tsx:handleSave`, `AppBlockerAccessibilityService.kt` — config writes broadcast a native session reset; source-verified
- [x] **1.7** `isFallbackBlocked` enforces allowance without an active enforcement session — `ForegroundTaskService.kt:522-530`, `1236-1282` — fallback allowance checks now require active enforcement; source-verified
- [x] **1.8** `goIdle()` does not cancel `allowanceExpiryRunnable` — `ForegroundTaskService.kt:732-753` — idle transition now cancels the pending expiry; source-verified
- [x] **1.9** The block overlay shows the wrong reason when an allowance is exhausted during focus — `AppBlockerAccessibilityService.kt:3295` — quota reason now takes priority in the reason builder; source-verified

#### Practice and maintenance concerns

- [ ] **2.1** Interval mode has no `ForegroundTaskService` UsageStats backup — `ForegroundTaskService.kt:263-275`
- [ ] **2.2** The two services use an uncoordinated read-modify-write for usage — `AppBlockerAccessibilityService.kt:2585`, `ForegroundTaskService.kt:356`
- [ ] **2.3** Usage progress and checkpoint timestamp are written in separate operations — `AppBlockerAccessibilityService.kt:2430-2439`
- [ ] **2.4** `todayDateString()` repeatedly allocates `SimpleDateFormat` objects — `AppBlockerAccessibilityService.kt:2681`, `ForegroundTaskService.kt:299`, `398`, `1240`
- [ ] **2.5** Deferred enforcement actions do not re-check enforcement state at fire time — `AppBlockerAccessibilityService.kt:1376-1384`
- [ ] **2.6** Always-on blocking with an empty list silently becomes allowance-only enforcement — `AppBlockerAccessibilityService.kt:1328`, `1362`
- [ ] **2.7** The modal refresh timer is shorter than the usage-cache TTL — `DailyAllowanceModal.tsx:usageTimer`
- [ ] **2.8** Switching allowance modes preserves old values accidentally — `DailyAllowanceModal.tsx:updateEntry`
- [x] **2.9** `scheduleAllowanceExpiry` does not check for a fresh active allowance session — `ForegroundTaskService.kt:392-406` — expiry callback now respects the active-session heartbeat; source-verified
- [x] **2.10** `focusMirrorVpnEnabled` is missing from backup — `backupService.ts` — backup export/import already preserves this setting; source-verified

---

## 1. CONFIRMED BUGS

### 1.1 `ACTION_USER_PRESENT` remaining time ignores date and window reset
**File:** `AppBlockerAccessibilityService.kt:767-774`
**Severity:** High

`remainingMs` is computed from raw `usedMs` with no staleness check:

```kotlin
val usedMs   = pkgUsed?.optLong("usedMs", 0L) ?: 0L
val remainingMs = when (entry.mode) {
    "time_budget" -> (entry.budgetMs - usedMs).coerceAtLeast(0L)
    "interval"    -> (entry.intervalMs - usedMs).coerceAtLeast(0L)
    else -> 0L
}
if (remainingMs <= 0L) { kick out }
```

`isAllowanceAvailable` at line 756 correctly checks the date for `time_budget` and the window expiry for `interval` and returns `true` when either has reset. The `remainingMs` block immediately below ignores both checks and produces zero, triggering an immediate kick-out despite `isAllowanceAvailable` returning true.

**Failure — `time_budget`:** Yesterday's full budget stored as `usedMs = budgetMs`. New day: `isAllowanceAvailable` returns `true`. `remainingMs = budgetMs - budgetMs = 0`. Kicked out on a fresh day.

**Failure — `interval`:** Window expired while screen was off. `isAllowanceAvailable` returns `true`. `remainingMs = intervalMs - intervalMs = 0`. Kicked out despite window reset.

**Fix:**
```kotlin
val pkgUsed = loadUsedObject().optJSONObject(pkg)
val remainingMs = when (entry.mode) {
    "time_budget" -> {
        val today    = todayDateString()
        val usedDate = pkgUsed?.optString("date", "") ?: ""
        val usedMs   = if (usedDate == today) pkgUsed?.optLong("usedMs", 0L) ?: 0L else 0L
        (entry.budgetMs - usedMs).coerceAtLeast(0L)
    }
    "interval" -> {
        val windowStartMs = pkgUsed?.optLong("windowStartMs", 0L) ?: 0L
        val windowExpired = now > windowStartMs + entry.windowMs
        if (windowExpired) entry.intervalMs
        else (entry.intervalMs - (pkgUsed?.optLong("usedMs", 0L) ?: 0L)).coerceAtLeast(0L)
    }
    else -> 0L
}
```

---

### 1.2 `parseUsage` returns stale interval usage after window expiry
**Files:** `allowanceUsageCache.ts:37-39`, `SharedPrefsModule.kt:507`
**Severity:** Medium

```typescript
if (value.mode === 'interval') {
  return [pkg, value.windowStartMs ? value : {}];
}
```

Returns the full value including stale `usedMs` from an expired window because `parseUsage` has no access to `windowMs`. `getAllowanceSnapshot` at `SharedPrefsModule.kt:504-510` does not return `configJson`.

**Fix — Step 1:** Add `configJson` to snapshot:
```kotlin
putString("usageJson",  current.getString("daily_allowance_used", null))
putString("configJson", current.getString("daily_allowance_config", null))
putString("activeSessionPackage", current.getString("active_session_pkg", null))
putDouble("activeSessionEndMs",   current.getLong("active_session_end_ms", 0L).toDouble())
```

**Fix — Step 2:** Use config in `parseUsage` to zero out expired windows:
```typescript
function parseUsage(raw, configRaw, today) {
  const config = buildWindowMsMap(configRaw); // pkg → windowMs
  return Object.fromEntries(
    Object.entries(parsed).map(([pkg, value]) => {
      if (value.mode === 'interval') {
        if (!value.windowStartMs) return [pkg, {}];
        const windowMs = config[pkg] ?? 0;
        const expired  = windowMs > 0 && Date.now() > value.windowStartMs + windowMs;
        return [pkg, expired ? {} : value];
      }
      return [pkg, value.date === today ? value : {}];
    })
  );
}
```

---

### 1.3 `force=true` in `getAllowanceUsageSnapshot` joins stale in-flight
**File:** `allowanceUsageCache.ts:57-60`
**Severity:** Medium

```typescript
if (!force && cached && cached.date === today && now - cached.fetchedAt < CACHE_TTL_MS) {
  return cached.value;
}
if (inFlight) return inFlight;   // ← no force check
```

`force=true` bypasses the cache but always joins an existing in-flight request. Config changes that need an immediate fresh read silently get stale data if a fetch is already in progress.

**Fix:**
```typescript
if (!force && inFlight) return inFlight;
```

---

### 1.4 `checkForegroundNow` 3-second window misses already-foreground apps
**File:** `AppBlockerAccessibilityService.kt:562`
**Severity:** Medium

```kotlin
val events = usm.queryEvents(now - 3_000L, now)
```

On service restart, if the user is already inside an allowance app with no new foreground transition in the last 3 seconds, this returns nothing. `currentTimedPkg` is never set and the usage timer never starts.

**Fix:** run a one-time wider query (60 seconds) in `onServiceConnected` to seed the current foreground package:
```kotlin
override fun onServiceConnected() {
    // existing init...
    detectCurrentForegroundOnConnect()  // 60s window, one-time
    startForegroundWatchdog()           // continues with 3s window
}
```

---

### 1.5 Ghost kick — `timedExpireRunnable` fires after focus or SA ends
**File:** `AppBlockerAccessibilityService.kt:2650-2661`
**Severity:** High

`timedExpireRunnable` calls `performGlobalAction(GLOBAL_ACTION_HOME)` unconditionally. When focus or SA expires while a timed allowance session is in progress, `focusActive = false` (line 828) and `saActive = false` (line 840) are set with no corresponding `handler.removeCallbacks(timedExpireRunnable)`. The runnable fires at the original session end time and kicks the user home with no active enforcement.

Same trigger when an app is removed from the allowance config while the runnable is pending — `findAllowanceEntry` returns null, accumulation is skipped, but `performGlobalAction(HOME)` still fires.

**Fix:**
```kotlin
val runnable = Runnable {
    timedExpireRunnable = null
    val entry = findAllowanceEntry(pkg)
    if (entry != null && currentTimedPkg == pkg) {
        accumulateTimedUsage(pkg, entry, currentTimedOpenAtMs)
    }
    clearActiveSessionSignal()
    currentTimedPkg = null
    currentTimedOpenAtMs = 0L
    currentTimedSessionEndMs = 0L
    val now2 = System.currentTimeMillis()
    val focusStillActive = prefs.getBoolean(PREF_FOCUS_ON, false).let { on ->
        if (!on) false
        else prefs.getLong("task_end_ms", 0L).let { end -> end <= 0L || now2 < end }
    }
    val saStillActive = prefs.getBoolean(PREF_SA_ACTIVE, false).let { on ->
        if (!on) false
        else prefs.getLong(PREF_SA_UNTIL, 0L).let { until -> until <= 0L || now2 < until }
    }
    val alwaysOn = prefs.getBoolean(PREF_ALWAYS_BLOCK, false)
    if (focusStillActive || saStillActive || alwaysOn) {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }
}
```

Apply the same guard to the `delayMs <= 0L` immediate-expiry branch at line 2638-2648.

---

### 1.6 Config change during active session leaves `timedExpireRunnable` and checkpoint loop running
**Files:** `DailyAllowanceModal.tsx:handleSave`, `AppBlockerAccessibilityService.kt`
**Severity:** Medium

`handleSave` → `onSave` → `setDailyAllowanceConfig` writes new SharedPrefs. The AccessibilityService is not notified. Three consequences:

**A — Mode changed:** Timer from old mode still fires. If mode changed to `count`, `accumulateTimedUsage` is skipped (correct) but `performGlobalAction(HOME)` still fires if enforcement is active.

**B — App removed entirely:** `findAllowanceEntry` returns null. Accumulation skipped. `performGlobalAction(HOME)` still fires (same Bug 1.5 guard fixes this).

**C — Budget lowered below already-used time:** Correct enforcement but surprising UX — app is retroactively blocked immediately. No UI warning about this.

**Fix for A and B:** broadcast a config-change intent from `setDailyAllowanceConfig`. AccessibilityService listens and re-validates `currentTimedPkg` against the new config:
```kotlin
ACTION_ALLOWANCE_CONFIG_CHANGED -> {
    val pkg = currentTimedPkg ?: return
    val newEntry = findAllowanceEntry(pkg)
    if (newEntry == null || (newEntry.mode != "time_budget" && newEntry.mode != "interval")) {
        if (newEntry == null) accumulateTimedUsage(pkg, /* last known entry */, currentTimedOpenAtMs)
        clearActiveSessionSignal()
        timedExpireRunnable?.let { handler.removeCallbacks(it) }
        timedExpireRunnable = null
        currentTimedPkg = null
        currentTimedOpenAtMs = 0L
        currentTimedSessionEndMs = 0L
    }
}
```

---

### 1.7 `isFallbackBlocked` enforces allowance without any active enforcement session
**File:** `ForegroundTaskService.kt:522-530, 1236-1282`
**Severity:** Medium

`fallbackPollRunnable` early-exit at line 522:
```kotlin
if (!focusActive && !saActive && !hasGreyout && !alwaysBlockActive && !hasAllowanceConfig) {
    handler.postDelayed(this, FALLBACK_POLL_MS)
    return
}
```

When `hasAllowanceConfig` is true, the poller continues even with no enforcement session active. `isFallbackBlocked` then returns `true` for exhausted `time_budget` and `count` packages and shows the overlay.

The AccessibilityService does the opposite — it returns early at line 1328 when `!focusActive && !saActive && !alwaysBlockActive`, never reaching the allowance check. Same exhausted budget, different behaviour depending on which service is running.

**Fix:** mirror the AccessibilityService guard:
```kotlin
val allowanceActive = hasAllowanceConfig && (focusActive || saActive || alwaysBlockActive)
if (!focusActive && !saActive && !hasGreyout && !alwaysBlockActive && !allowanceActive) {
    handler.postDelayed(this, FALLBACK_POLL_MS)
    return
}
```
And in `isFallbackBlocked`, wrap the allowance section:
```kotlin
if (focusActive || saActive || alwaysBlockActive) {
    // existing allowance check
}
```

---

### 1.8 `goIdle()` does not cancel `allowanceExpiryRunnable`
**File:** `ForegroundTaskService.kt:732-753`
**Severity:** Medium

```kotlin
private fun goIdle() {
    isActiveMode = false
    handler.removeCallbacks(tickRunnable)
    handler.removeCallbacks(breakTickRunnable)
    // allowanceExpiryRunnable is NOT cancelled here
    ...
}
```

When focus or SA ends and `goIdle()` is called, the budget-exhaustion runnable remains queued. When it fires, it writes `usedMs = budgetMs` and calls `handler.post(fallbackPollRunnable)`. If the AccessibilityService is alive, `fallbackPollRunnable` defers immediately (safe). If it is down, `isFallbackBlocked` may show a block overlay in a free period.

**Fix:**
```kotlin
private fun goIdle() {
    isActiveMode = false
    handler.removeCallbacks(tickRunnable)
    handler.removeCallbacks(breakTickRunnable)
    allowanceExpiryRunnable?.let { handler.removeCallbacks(it) }
    allowanceExpiryRunnable = null
    ...
}
```

---

### 1.9 Wrong block reason shown when allowance exhausted during focus mode
**File:** `AppBlockerAccessibilityService.kt:3295`
**Severity:** Low (display only)

When `focusActive=true`, `allowedList` is empty, and the allowance is exhausted, `isPackageBlocked` returns `false` (app is in the config so it passes). The subsequent allowance check blocks correctly. But `buildBlockReason` evaluates the focus-mode-list branch first, sees the app is in the config (not null), and returns "Not allowed in the current Focus Mode app list" instead of the allowance exhaustion reason.

**Fix:** in `buildBlockReason`, check `isAllowanceAvailable` and prefer the allowance exhaustion reason when `focusActive=true` and the app is in the allowance config but exhausted.

---

## 2. PRACTICES CONCERNS

### 2.1 `interval` mode has no ForegroundTaskService UsageStats backup
**File:** `ForegroundTaskService.kt:263-275`

`allowanceSyncRunnable` only processes `time_budget` entries:
```kotlin
if (obj.optString("mode", "count") == "time_budget") {
    timeBudgetPkgs[pkg] = budgetMs
}
```

`time_budget` has a double safety net: AccessibilityService checkpoints every 15 seconds + ForegroundTaskService UsageStats reconciliation every 60 seconds. If the AccessibilityService is killed, ForegroundTaskService independently reads OS foreground time and updates `usedMs`.

`interval` has no equivalent. If the AccessibilityService is killed and its 2-minute TTL lapses, elapsed time since the last checkpoint is unrecoverable. OS UsageStats reports total foreground time, not per-window time, so a direct equivalent isn't possible. Mitigation: shorten the checkpoint interval for interval-mode sessions, or raise the restore TTL above 2 minutes.

---

### 2.2 Read-modify-write on `daily_allowance_used` is uncoordinated
**Files:** `AppBlockerAccessibilityService.kt:2585, 2626`, `ForegroundTaskService.kt:356`

Both services use the same pattern:
```kotlin
val allUsed = loadUsedObject()
// modify
prefs.edit().putString(PREF_DAILY_ALLOWANCE_USED, allUsed.toString()).apply()
```

This read-modify-write is not atomic. If ForegroundTaskService reads between the AccessibilityService's read and its write, the FS write silently clobbers the checkpoint. Damage is contained (FTS never lowers `usedMs`, only raises it) but the race is real.

---

### 2.3 Two separate `.apply()` calls in `checkpointActiveTimedSession`
**File:** `AppBlockerAccessibilityService.kt:2430-2439`

```kotlin
accumulateTimedUsage(...)           // write 1: daily_allowance_used
prefs.edit()
    .putLong(PREF_ACTIVE_SESSION_LAST_CHECKPOINT_MS, now)
    .apply()                        // write 2: checkpoint timestamp
```

If the process dies between write 1 and write 2, `PREF_ACTIVE_SESSION_LAST_CHECKPOINT_MS` is stale. On `restoreAllowanceSession`, `checkpointAgeMs` may exceed `ACTIVE_SESSION_SIGNAL_TTL_MS` (2 minutes), causing `signalFresh = false`. Session signal is cleaned up even though usage is current. Active session tracking stops until the user re-opens the app.

**Fix:** inline `accumulateTimedUsage` into the same `edit()` block as the checkpoint timestamp write, making both atomic.

---

### 2.4 `todayDateString()` creates a new `SimpleDateFormat` on every call
**Files:** `AppBlockerAccessibilityService.kt:2681`, `ForegroundTaskService.kt:299, 398, 1240`

```kotlin
private fun todayDateString(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getDefault()
    }.format(java.util.Date())
```

Called from `isAllowanceAvailable`, `recordAllowanceOpen`, `accumulateTimedUsage`, `allowanceExhaustedReason`, and ForegroundTaskService sync — many times per event. `SimpleDateFormat` construction is non-trivial. The same pattern appears in three locations in ForegroundTaskService.

**Fix:** cache with a 60-second TTL:
```kotlin
private var cachedDateKey: String = ""
private var cachedDateKeyMs: Long = 0L

private fun todayDateString(): String {
    val now = System.currentTimeMillis()
    if (now - cachedDateKeyMs < 60_000L && cachedDateKey.isNotEmpty()) return cachedDateKey
    cachedDateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .apply { timeZone = TimeZone.getDefault() }
        .format(Date())
    cachedDateKeyMs = now
    return cachedDateKey
}
```

---

### 2.5 `timedExpireRunnable` and deferred enforcement actions don't re-check enforcement state at fire time
**File:** `AppBlockerAccessibilityService.kt:1376-1384`

Enforcement state is verified at schedule time (correct). Deferred runnables fire later with no re-check. Any deferred enforcement action — HOME, overlay, retry — should validate enforcement state at fire time, not just at schedule time. Bug 1.5's fix addresses the symptom. The practice is to apply this pattern to all deferred enforcement actions going forward.

---

### 2.6 `alwaysBlockActive` + empty always-on list = allowance-only enforcement (non-obvious)
**File:** `AppBlockerAccessibilityService.kt:1328, 1362`

When `alwaysBlockActive=true` but `alwaysOnPackages` is empty, `isPackageBlocked` returns false for all apps. Allowance checks run. Apps without an allowance entry are unrestricted. Apps with an allowance entry are enforced by quota. Valid design but not communicated in the UI.

---

### 2.7 Modal usage refresh timer interval is shorter than cache TTL
**File:** `DailyAllowanceModal.tsx:usageTimer`

```typescript
const usageTimer = setInterval(() => { void refreshUsage(); }, 5_000);
```

`refreshUsage` calls `getAllowanceUsageSnapshot(force = false)`. Cache TTL is 10 seconds. Every alternate timer tick returns cached data. Effective native refresh rate is once every 10 seconds, not 5. Either align the timer to 10+ seconds or call with `force = true` in the timer callback.

---

### 2.8 Mode switch in modal preserves previous mode values silently
**File:** `DailyAllowanceModal.tsx:updateEntry`

```typescript
const updateEntry = (pkg, patch) =>
  setEntriesMap(prev => {
    const next = new Map(prev);
    const existing = next.get(pkg) ?? makeDefaultEntry(pkg);
    next.set(pkg, { ...existing, ...patch });
    return next;
  });
```

Switching from `time_budget` to `count` keeps `budgetMinutes` in the entry object. Switching back restores it. This is good UX (non-destructive mode switching) but entirely accidental. If the code is ever cleaned up without knowing this, user expectations will break. Should be made explicit and documented as intentional.

---

### 2.9 `scheduleAllowanceExpiry` runnable doesn't check `hasFreshActiveAllowanceSession`
**File:** `ForegroundTaskService.kt:392-406`

The 60-second sync loop at line 333 correctly guards with `hasFreshActiveAllowanceSession(pkg, now)` before updating `usedMs`. The `allowanceExpiryRunnable` body does not:

```kotlin
val runnable = Runnable {
    allowanceExpiryRunnable = null
    if (!pkg.equals(getFallbackForegroundPackage(), ignoreCase = true)) return@Runnable
    // ← no hasFreshActiveAllowanceSession check
    val used = allUsed.optJSONObject(pkg) ?: org.json.JSONObject()
    used.put("usedMs", budgetMs)   // writes unconditionally
    ...
}
```

If AccessibilityService has a fresh session signal when the expiry fires, both services write to `daily_allowance_used` simultaneously. Both write `budgetMs` so the outcome is correct, but the write bypasses the session ownership guard.

**Fix:**
```kotlin
val runnable = Runnable {
    allowanceExpiryRunnable = null
    if (!pkg.equals(getFallbackForegroundPackage(), ignoreCase = true)) return@Runnable
    val now2 = System.currentTimeMillis()
    if (hasFreshActiveAllowanceSession(pkg, now2)) return@Runnable
    ...
}
```

---

### 2.10 `focusMirrorVpnEnabled` missing from backup
**File:** `backupService.ts`

Every other VPN setting is backed up. `focusMirrorVpnEnabled` (added in the recent VPN coordinator update) is absent. A user who migrates devices or reinstalls silently loses this setting and defaults to `false`. Noted in VPN_V2_REVIEW.md but applies here since the setting is logically coupled with the daily allowance mirror feature.

---

## 3. COMPLETE PRIORITY TABLE

| Priority | Issue | File(s) | Severity | Type |
|---|---|---|---|---|
| 1 | Ghost kick — `timedExpireRunnable` fires after focus/SA ends | `AppBlockerAccessibilityService.kt:2650` | High | Bug |
| 2 | `USER_PRESENT` remaining time ignores date/window reset | `AppBlockerAccessibilityService.kt:767` | High | Bug |
| 3 | `isFallbackBlocked` enforces allowance without enforcement session | `ForegroundTaskService.kt:522, 1236` | Medium | Bug |
| 4 | `checkForegroundNow` 3s window misses already-foreground app | `AppBlockerAccessibilityService.kt:562` | Medium | Bug |
| 5 | `goIdle()` doesn't cancel `allowanceExpiryRunnable` | `ForegroundTaskService.kt:732` | Medium | Bug |
| 6 | Config change during session leaves stale timer and loop | `AppBlockerAccessibilityService.kt`, `DailyAllowanceModal.tsx` | Medium | Bug |
| 7 | `parseUsage` returns stale interval usage + `configJson` missing from snapshot | `allowanceUsageCache.ts:37`, `SharedPrefsModule.kt:507` | Medium | Bug |
| 8 | `force=true` joins stale in-flight instead of starting fresh | `allowanceUsageCache.ts:59` | Medium | Bug |
| 9 | `scheduleAllowanceExpiry` body missing `hasFreshActiveAllowanceSession` guard | `ForegroundTaskService.kt:392` | Low | Bug |
| 10 | Wrong block reason when exhausted allowance app blocked during focus | `AppBlockerAccessibilityService.kt:3295` | Low | Bug |
| 11 | `interval` mode has no ForegroundTaskService UsageStats backup | `ForegroundTaskService.kt:263` | — | Practice |
| 12 | Read-modify-write on `daily_allowance_used` is uncoordinated | `AppBlockerAccessibilityService.kt:2585`, `ForegroundTaskService.kt:356` | — | Practice |
| 13 | Two `.apply()` calls in checkpoint — not atomic | `AppBlockerAccessibilityService.kt:2430` | — | Practice |
| 14 | Modal refresh timer (5s) shorter than cache TTL (10s) | `DailyAllowanceModal.tsx` | — | Practice |
| 15 | Mode switch preserves old values silently — accidental good UX | `DailyAllowanceModal.tsx` | — | Practice |
| 16 | `alwaysBlockActive` + empty list = allowance-only (non-obvious) | `AppBlockerAccessibilityService.kt:1328` | — | Practice |
| 17 | `todayDateString()` / `SimpleDateFormat` allocation in hot paths | Both services | — | Performance |
| 18 | Deferred enforcement actions don't re-check enforcement state at fire time | `AppBlockerAccessibilityService.kt:1376` | — | Practice |
| 19 | `focusMirrorVpnEnabled` missing from backup | `backupService.ts` | — | Practice |
