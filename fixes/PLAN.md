# FocusFlow Fixes : final plan (v9)

**Supersedes** `FocusFlow Fixes` and v8 of this plan (preserved at `fixes/archive/PLAN_v8.md`). This plan lists what is still wrong or missing and how to finish Phase 5. The owner's recorded answers are in section 3.1. `TRACKER.md` is the authoritative work log.

**For the coding agent.** Verify every repository-dependent claim against the current checkout; paths and line numbers from earlier audits may drift.

---

## 0. Rules for this plan

1. **Scope.** Phase 5 is: one way to measure allowance usage (count, time budget, interval); the usage pipeline that Stats, rollups and detectors share; the database changes for it; and the screens, permissions and wording that describe it. Section 9 lists known problems that are **not** Phase 5. Do not mix them into Phase 5 changes.
2. **Do not rebuild what works** (section 1.2). Delete code more readily than you add it.
3. **Product decisions.** A task tagged **PD-n** depends on an owner decision (section 3). If the owner has not answered when you reach the task, implement the stated **DEFAULT**, keep the choice in one constant or branch so it is easy to change, and record in the tracker "default applied, awaiting owner". Never choose a different behavior silently.
4. **Evidence.** Tick a tracker item only with the evidence the item names (a test, a grep result, a log line). Anything that needs a real device is **owner-run** (section 7): you add the tooling and the instructions, and you never tick the result.
5. **Owner constraints.** Do not touch Gradle files, GitHub Actions or APK builds. Keep the `.focusflow` backup format unchanged. Do not touch the Linux app. Do not start a later batch before the earlier batch's automated "done when" holds, unless the batch explicitly permits it. A specific owner-authorized exception must be recorded in section 3.1 and the tracker before it is used; do not infer authorization from draft attachments.
6. **Size and loops.** New files stay under about 250 lines and must justify going over 300. No new polling loop unless it appears in the cadence table (section 4.4).
7. **Illustrative code.** Snippets are examples. Any implementation that meets the acceptance criteria is fine.
8. **Tests.** Behavior tests only. Delete any test that reads Kotlin source files as text and replace it with a behavior test. Use fake clocks and fake event sources; logic under test must not call `System.currentTimeMillis()` directly.
9. **One task, one pull request**, named by its task ID.
10. **Device testing happens at the very end (Batch E), by the owner, on a Realme C3 (Android 10) and a Samsung M31 (Android 12): one initial full session, then retests of failed scenarios only, for at most two rounds.** Everything before that must be proven by automated tests, and the agent must leave the owner a detailed step-by-step guide (section 7).
11. **Automated completion versus owner evidence.** A batch's implementation gate is determined by its automated "done when." Owner-run scenarios and owner-provided logs remain `OWNER-RUN PENDING` until supplied; they do not block Batch D or E1 unless an explicit dependency below says otherwise. E3 must resolve them or record an owner-approved deferral.
12. **Dependency direction.** Do not make a batch depend on evidence that can only be produced after that batch or a later batch starts. Cross-batch integration belongs to the later batch that provides the dependency: C5 owns the A2 rollup-prune wiring, and E3 owns replay of C6a's harness on owner logs. Every exception must name its prerequisite and closure evidence.

---

## 1. Baseline and current-state rule

The defect register below describes the v8 audit baseline, not a claim that every defect is still open. `fixes/TRACKER.md` records current implementation status and observed evidence. Re-check the current source before acting on any baseline description.

### 1.1 What exists

- **Event reading.** `UsageStatsRepository.readForegroundEvents` (IO dispatcher; a null or denied read becomes `UsageEventRead.Unknown`).
- **One event interpreter.** `ForegroundSpanTracker.sessions` is pure. It closes sessions on PAUSED, STOPPED (exact activity), screen off, keyguard and shutdown, caps an inferred open tail at 4 h, and marks `isNewOpen` and `isInferredTail`. The allowance reader, `DeviceUsageSource`, `UsageRollupWriter` and `OnDemandDetectorUsageReader` all use it.
- **Allowance meter.** `AllowanceUsageCoordinator` (created only in `AppBlockerAccessibilityService.onServiceConnected`, scope `Dispatchers.Main.immediate`), `AllowanceUsageReader` (read, then `AllowanceUsagePipeline.measure`, then ledger reconcile), `AllowanceUsageAccumulator` (estimates between reads), `AllowanceUsageStateStore` (config targets, live marker, restart recovery) and a freshness reducer (`FRESH` within 60 s of the last read, otherwise `STALE`, or `UNAVAILABLE`).
- **Ledger.** `AllowanceLedger` is the only code that reads or writes `daily_allowance_used`. Per-package JSON fields: `mode`, `date`, `count`, `usedMs`, `windowStartMs`, `confirmedUsedMs`, `confirmedCount`, `estimatedExtraMs`, `estimatedExtraOpens`, `confirmedAtMs`. It writes through to SharedPreferences (`AllowanceLedgerStore`).
- **Enforcement readers.** Accessibility `isAllowanceAvailable` reads the ledger and the accessibility service owns the exact-expiry timer (`onAllowanceUsageReconciled`). The foreground service's `isFallbackBlocked` reads the ledger only. The launcher card, `ActiveScreen` and `DailyAllowanceModal` read through `SettingsRepository.getAllowanceSnapshot`.
- **Database.** The current checkout is Room version 9 and contains `usage_pipeline_state`, `usage_rollup_day`, `usage_rollup_app_day`, and `usage_rollup_session`. A1's implementation and migration evidence are recorded in the tracker; S2 remains owner-run.
- **Maintenance.** `UsageMaintenanceWorker` is scheduled from `FocusFlowApp` and runs rollup, readiness/detection, and prune phases. Its current prune adapter deletes legacy usage/session rows and other expired records, but not the three rollup tables; C5 owns that retention and worker-invocation test.
- **Rollups and history.** `UsageRollupWriter` (mutex plus transaction; a COMPLETE day is never downgraded at the same pipeline version; today is never stored), `UsageRollupCoordinator` (app open, service start, Stats open, and date/time/zone changes), `UsageHistoryRepository` and `UsageHistorySourcePolicy` (one source per date; detectors refuse windows that cross the cutover date), `DeviceUsageSource` (Stats), and `OnDemandDetectorUsageReader`.
- **Detectors.** `FindingDetectionRunner` reads completed days only (`UsageDetectorWindows`); `FindingRepository` updates changed fingerprints quietly (owner decision).

### 1.2 What works (do not redo)

One owner for the allowance JSON; one event interpreter; null or denied reads mapped to "unknown" and never to zero; interval windows keyed by window start rather than by date; `INTERVAL_BEST`, the 60 s foreground-service sync and `reconcileCountAllowances` removed; no version checks at or below API 29; the all-day wording in `DailyAllowanceModal`; the rollup writer's guards; the calendar-change receiver; the seam predicate; quiet fingerprint updates.

---

## 2. Defect register

Severity: **P0** do not release; **P1** fix in Phase 5, it affects enforcement or user data; **P2** fix in Phase 5, lower risk. Batches are in section 5.

| ID | Sev | Defect | Evidence | Batch |
|---|---|---|---|---|
| P5-01 | P0 | `MIGRATION_7_8` sets the cutover date to the upgrade day, so the 7-day gate never runs for upgraded installs; plan and tracker say it is gated | `FocusFlowDatabase.kt:548-556`, `AppModule.kt:226`, gate in `UsageHistoryRepository.cutoverDate` | A1 |
| P5-02 | P0 | Fresh installs never get the `usage_pipeline_state` row: it is seeded only by `MIGRATION_6_7`, so cutover never happens and the legacy writer never stops | `FocusFlowDatabase.kt:485-496`; `cutoverDate` returns null when the row is missing; `upsertPipelineState` has no callers; the only DB callback overrides `onOpen` | A1 |
| P5-03 | P0 | `BackgroundFetchWorker` is never scheduled and `BackgroundFetchDependencies.install` is never called, so detection, pruning and the rest of that job never run | no callers of `enqueuePeriodic` or `install` anywhere; only `TaskAlarmReconcileWorker` uses WorkManager | A2 |
| P5-04 | P0 | Stats "Observed Device Time" reads zero: per-app rows are collapsed to one row per date (the least-used app) | `DeviceUsageSource.kt:74` (`associateBy { it.date }`) and `:147-150` | A3 |
| P5-05 | P0 | While accessibility is off, the foreground-service fallback judges allowance apps from a ledger that nothing updates in that mode (the 60 s sync was deleted), so limits silently stop working. Owner decision PD-2: allowances need accessibility, so the fallback must block allowance apps during a block and say why | `ForegroundTaskService`: `fallbackPollRunnable`, `isFallbackBlocked` (allowance mirror), `onCreate` recovery; the meter exists only in the accessibility service | B1a, B1b |
| P5-06 | P0 | No migration, instrumented test or device check has ever run; some tests assert on source text | tracker Batches 1, 2, 3, 4, 5, 7 notes | A4, E1 |
| P5-07 | P1 | The "never go down" rule locks in an overcount: confirmed usage is `max(prior, pipeline)` and the pipeline value includes an inferred open tail | `AllowanceLedger.reconcileTimeUsage`; `ForegroundSpanTracker` tail | B4 |
| P5-08 | P1 | The meter refreshes on every window-state event of any non-allowance app, scans from midnight minus 6 h each time, reduces on the main thread and rewrites the ledger JSON for every target on every read | accessibility `onAccessibilityEvent` calls `onForegroundPackage`; `AllowanceUsageCoordinator.onForegroundPackage` (non-target branch) and `refresh`; `AllowanceUsageReader`; `AllowanceLedger.persistLocked` | B3 |
| P5-09 | P1 | Keyboards, system UI and our own overlay count as "leaving" the allowance app; count mode adds an estimated open on return and can flap; two separate "who is in front" states exist (`currentTimed*` and `currentTarget`) | `AllowanceUsagePipeline.dialogBridgePackages` (4 packages); `addEstimatedOpen`; accessibility `currentTimedPkg` | B2 |
| P5-10 | P1 | `onInterrupt` destroys the coordinator and nothing recreates it until the service reconnects | `AppBlockerAccessibilityService.onInterrupt` and `onServiceConnected` | B1a |
| P5-11 | P1 | An empty focus allow-list means different things in the two paths: the accessibility path allows everything when no allowance entries exist (or only allowance apps when they do), the fallback blocks everything. Owner decision PD-3: an empty list means no app is allowed | accessibility `isPackageBlocked` (focus branch) vs `ForegroundTaskService.isFallbackBlocked` | B6 |
| P5-12 | P1 | The allowance editor is locked only for standalone blocks (`locked = standaloneActive`), so budgets can be added or raised during a focus session while the modal says "A block is active" | `DefenseScreen.kt:133-136, 552`; `DailyAllowanceModal.kt:155-166, 301` | B6 |
| P5-13 | P1 | The allowance config JSON is parsed in at least 7 places with different defaults | accessibility (two sites), `ForegroundTaskService` (two), `LauncherActivity`, `ActiveScreen.dailyAllowancePackages`, `AllowanceUsageStateStore`, `SettingsRepository` | B6 |
| P5-14 | P1 | Sessions are per activity: PAUSED closes the session and the next RESUMED opens another, so session counts and lengths change meaning for detectors | `ForegroundSpanTracker` PAUSED branch; `UsageCalendarAggregator` `sessionCount` | C1 |
| P5-15 | P1 | Launcher exclusion is a hard-coded list of 7 packages (duplicated in the legacy tracker) while the accessibility service resolves home packages dynamically; other launchers appear as apps in Stats and rollups | `ForegroundSpanTracker.DEFAULT_EXCLUDED_PACKAGES`; `AppUsageAndSessionTracker.IGNORED_PACKAGES`; accessibility `isHomePackage` | C2 |
| P5-16 | P1 | Within one Stats read, the summary applies the 500 ms and launchable filter, but the hourly bars, the daily totals and the rollup rows do not | `DeviceUsageSource.kt:166-195` | C2 |
| P5-17 | P1 | The Today fallback in `AnalyticsProcessor` cannot trigger (the bug in P5-04 creates a row), uses the legacy summary path, and adds to the summary and daily list but not the hourly chart | `AnalyticsProcessor.mergeTodayUsageFallback` | A3 |
| P5-18 | P1 | The O7 change moved the shared `dateRangeEnd()` to yesterday, so two task-based detectors also lost today's tasks | `FindingDetectionRunner`: `runPostFailureCascade`, `runDayOfWeekOutlier`, `dateRangeEnd` | C3 |
| P5-19 | P1 | Detection runs and is marked done for the day even when the rollup result is unknown; the worker mixes UTC dates (tasks) with local dates (prune, detection) and uses the wall clock in `prunePhase1Tables` | `BackgroundFetchWorker.runDailyFindingDetection`, `prunePhase1Tables` | A2a |
| P5-20 | P1 | The rollup tables are never pruned | `UsageRollupDao` has only per-date deletes; `prunePhase1Tables` handles legacy tables only | C5 |
| P5-21 | P1 | Freshness is defined by the age of the last read, but reads happen mainly while an allowance app is in front, so the launcher shows "About" almost always; `AllowanceUsage` has no freshness field; `DailyAllowanceModal` accepts `usageByPackage` and ignores it | `AllowanceUsagePipeline` reducer; `LauncherActivity` `usageIsApproximate`; `DailyAllowanceModal.kt:109` | D1 |
| P5-22 | P1 | Wording and permissions: Usage Access is described only as foreground detection; the Stats screen calls it optional; "Usage Access granted" has several definitions | `PermissionSupport.kt:40`; Stats text; `UsageStatsRepository.checkUsageAccessPermission`; `LauncherActivity` copy; `ActiveHeaderButton` treats an error as granted | D2 |
| P5-23 | P2 | Read paths that write: `cutoverDate()` can persist the cutover; `getActiveFindings` runs a repair `UPDATE` on every call | `UsageHistoryRepository.cutoverDate`; `FindingRepository.getActiveFindings`, `FindingDao.restorePreviouslySeenFindings` | C4 |
| P5-24 | P1 | The cutover gate proves only that 7 complete days exist; the comparison with legacy was removed (and legacy has known biases), so nothing validates the pipeline's numbers | gate code; tracker Batch 7 | C6a |
| P5-25 | P1 | `TRACKER.md` no longer matches the code (database version, `MIGRATION_7_8`, stale v6 references, ticks without evidence, wrong reader and writer map) | `TRACKER.md` lines noted in section 8 | A5 |
| P5-26 | P2 | Ledger: about 180 lines of dead legacy API; 487 lines; whole-JSON write-through; duplicated `active_session_*` constants; `snapshot()` re-parses every package per call | `AllowanceLedger.kt`; `SettingsRepository`; `AppBlockerAccessibilityService` | B7 |
| P5-27 | P2 | The recovery marker is not cleared after recovery and both services recover at start | `AllowanceUsageStateStore.recoverPersistedCheckpoint`; `ForegroundTaskService.onCreate` | B1a |
| P5-28 | P2 | `UsageHistoryRepository.kt` (449 lines) and `AllowanceUsageCoordinator.kt` (302) exceed the size rule; `cutoverDate` repeats a check and re-reads after writing; `RoomUsageHistoryStore` uses the wall clock | those files | B7, C4 |
| P5-29 | P2 | Dead code after Phase 5: `UsageStatsRepository.getUsageSummary` (after P5-17), `PhoneUsageSummary.kt`, possibly `countDistinctDates`; `isDialogBridgeReturn` is quadratic per read; `StatsViewModel` reaches into `AppModule.database` | grep each before deleting | B3, B7, C4 |

---

## 3. Decisions

### 3.1 Recorded owner decisions

| PD | Decision | Effect on the plan |
|---|---|---|
| PD-1 | **The same 7-day gate for everyone; no cutover on upgrade day.** Builds with database version 7 or 8 may have reached other people. | A1 removes the upgrade-day cutover and adds a one-time repair migration (harmless where nothing needs repairing). |
| PD-2 | **Allowances need the Accessibility service. There is no fallback meter.** While accessibility is off, an app that has an allowance is blocked during any block, and the UI says why. | B1a (lifecycle/messaging), B1b/B6 (shared fallback resolver), P5-05 |
| PD-3 | **An empty Focus allow-list means no app is allowed.** Allowance entries never widen Focus: an allowance only limits an app that is otherwise allowed to run. (The last sentence is my reading of the answer; section 3.2 asks the owner to confirm it.) | B6, section 4.6, P5-11 |
| PD-4 | **Loosening edits (raise a budget, add or remove an entry, change mode or window) are locked while any block is active:** focus session, standalone block or always-on. Tightening stays allowed. | B6, D2 |
| PD-5 | **Usage Access is required.** Stats wording changes to match; enabling an allowance prompts for it. | D2 |
| PD-6 | **Usage history is not exported.** `.focusflow` is unchanged; the backup and restore screens say usage history is rebuilt from the device. | D2 |
| PD-7 | **Remove the Today fallback** after the Stats fix. A failed read shows "unavailable", never zero. | A3, D3 |
| PD-8 | **Do not fix the legacy tracker.** Keep its limits (ignores same-package window changes, drops sessions outside 1 s to 4 h, hard-coded launcher list) and its stop 14 days after cutover, and never use legacy numbers as a validation reference. | C6a |
| PD-9 | **Accept the detector blackout** after cutover: windows that cross the cutover date stay refused and the data-health notice says why. No relaxation without recorded device evidence. | C6a |
| PD-10 | **Session merge gap: 3 s** (same package, no other package in between). Tune only if E1 evidence shows a need; a code change reopens affected automated tests and owner scenarios. | C1, B5, E3 |
| PD-11 | **Stats are checked against the phone's own screen-time page** (Digital Wellbeing or the manufacturer's equivalent) where one exists: within about 20 percent or 15 minutes, whichever is larger. If a phone has no such page, check that the daily bars add up to the total and that the top apps are the ones used. | E1 |
| PD-13 | **When usage data is unavailable, keep enforcing with the last known value plus a projection,** mark values approximate, and show the revoked permission in the permission health card. | B4, D1 |
| PD-14 | **What an allowance means:** usage is counted all day; an allowance restricts only while a block is active; it never opens an app that a block keeps closed. | section 4.6, D2 |
| Modes | **All three modes stay.** Time budget and interval are the main focus; count is supported and tested after them. | B5, section 7 |
| Devices | **Realme C3 (Android 10) and Samsung M31 (Android 12), one initial full owner session at the end, with only failed-scenario retests (up to two rounds).** | section 7, E1 |

### 3.2 Still open (safe defaults apply)

| Item | Question | DEFAULT until answered |
|---|---|---|
| PD-3 detail | With an empty Focus allow-list, should an app that has an allowance still open until its budget is used (today's behavior when allowance entries exist)? | **No.** Empty means nothing opens, literally, and an allowance never widens Focus. One constant switches it. |
| PD-12 | Does the app ship through Google Play? Play requires new apps and updates to target API 36 or higher, and the tracker records 35. | Treated as a release gate (E2); no work in Phase 5. |
| Build files | `app/src/main/AndroidManifest.xml` and `app/build.gradle.kts` are present. The manifest declares `QUERY_ALL_PACKAGES` and `dataSync|specialUse`; the app target SDK is 35. C2 must verify actual package-query needs against code rather than treating a broad declaration as proof of correctness. | Repository inspection recorded for v9; PD-12 remains open for distribution/release planning. Do not change Gradle or manifest as part of this documentation repair. |
| Old background jobs | Late-start warnings, reminders and the morning digest are never scheduled (P5-03). Should they run? | Not in Phase 5; backlog item AD-6. |

No pre-A-gate B1a/B1b exception is recorded in the current plan/tracker. Until a specific owner authorization is recorded in both, these tasks follow the normal Batch B gate.

---

## 4. Target design: what "one way" means

### 4.1 Principles

1. **One meter.** Platform events go through `ForegroundSpanTracker`, then `AllowanceUsagePipeline.measure`, then the ledger. Nothing else produces a stored duration or open count.
2. **Estimates are projections, not a second meter.** Between reads, the live estimate is added for the app in front. It is never ratcheted, and the next successful read replaces it.
3. **One owner for each thing.** Numbers: the ledger. "Who is in front": the meter. "Is this window a foreground change": one signal filter. "What counts as usage": one inclusion policy. Config: one parser. Focus allow-list: one resolver. Freshness: one function.
4. **Unknown is not zero.** A failed or denied read is shown as unavailable and logged, never as 0.
5. **Closed time is exact, open time is provisional.** Only closed sessions may be remembered as a floor.

### 4.2 Meter contract

- **Inputs.** `onForegroundSignal(package, atMs)` from the accessibility service (window-state events after the signal filter), plus `onConfigChanged`, `onUsageAccessChanged`, `onDateChanged`, `onUserPresent` and the tick. The accessibility service is the only signal source (PD-2).
- **Outputs.** Ledger writes; freshness per target; callbacks to the accessibility service's enforcer: `onReconciled(package, remaining)` and `onExhausted(package)`. The accessibility service owns the exact-expiry timer.
- **Lifecycle.** Created with the accessibility service and started whenever it is connected. `onInterrupt` never stops it; only unbind and destroy do, and a rebind recreates and starts it. While accessibility is off there is no meter, and the foreground service does not read the ledger (PD-2).
- **Threading.** The platform read and the reduction (sessions, measurement, JSON parsing) run off the main thread. Only small state updates run on main.
- **Persistence.** Ledger JSON and the live marker. Nothing else.

### 4.3 Freshness (one definition)

- **UNAVAILABLE:** Usage Access is missing or revoked, or no read has ever succeeded since start although reads were due.
- **STALE:** a read was due and failed, or is late.
- **FRESH:** otherwise.
- **A read is "due"** when an allowance app is in front (every tick), once when one has just left, and after service start, a date change, a config change or a permission change.
- **Idle is exact.** If no allowance app is in front and the last successful read is newer than the moment the app left, the value is exact and FRESH. If an allowance app is in front, FRESH means a successful read within two tick periods.
- The "about" marker appears only for STALE and UNAVAILABLE. The launcher, `ActiveScreen`, `DailyAllowanceModal` and the meter all call the same function.

### 4.4 Cadence table (the only recurring work allowed)

| Work | Cadence | Owner | Notes |
|---|---|---|---|
| Meter tick | 30 s when FRESH, 15 s when STALE, only while an allowance app is in front | meter | |
| Meter read triggers | meter start, local date change, config change, permission change, allowance app comes to front, allowance app leaves front (once), each tick | meter | At least 10 s between non-lifecycle reads (constant). No read for windows of non-allowance apps. |
| Foreground probe | 1 s, only while accessibility is off and a block is active | foreground service | Detects which app is in front so that blocking still works. It never produces durations and never reads the ledger. The one sanctioned second event reader. |
| UI refresh | observe ledger changes; no 1 s polling | view models | |
| Rollup triggers | app open, service start, Stats open, date/time/zone change, maintenance worker | rollup coordinator | |
| Maintenance worker | unique periodic job (15 min requested; the OS may delay it) | worker | rollup, then detection, then prune |

**Budgets (defaults, confirm by measurement):** with one allowance configured and ordinary use, at most 30 platform event reads per hour when no allowance app is in front, and at most 120 per hour while one is continuously in front.

### 4.5 Per-mode rules

| | Count (opens per local day) | Time budget (minutes per local day) | Interval (minutes per window of H hours) |
|---|---|---|---|
| **Source** | pipeline sessions with `isNewOpen` starting in `[local midnight, now)`, minus dialog-bridge returns | pipeline foreground time in `[local midnight, now)`, all day (D1) | pipeline foreground time in `[windowStart, min(now, windowStart + window)]` |
| **What is an open** | a new session of the package after a different real app or home; not after screen off or keyguard; permission or intent-resolver dialogs within 3 s are bridged; keyboards and system UI are ignored by the signal filter | n/a | the first open when no window is active starts the window |
| **Live estimate** | one estimated open per distinct open signal that passes the filter; replaced by the pipeline count at the next read | open session time since the last read, for the package in front; replaced at the next read | same as time budget, clipped to the window |
| **Floor (ratchet)** | confirmed count never decreases within the day | closed-session time never decreases within the day; the open tail is volatile (B4) | closed-session time never decreases within the window; tail volatile |
| **Exhausted when** | count is at least N after an open completed; the Nth open stays usable until the user leaves, the (N+1)th is blocked | used is at least M; the exact-expiry timer fires at `remaining` and is re-seeded after every read | used is at least M and the window has not expired |
| **Rollover** | local midnight; a session that spans midnight is not a new open | local midnight with calendar arithmetic (DST safe); a session spanning midnight is split | by window only: a window that crosses midnight is not reset; after expiry the next open starts a new window |
| **Config edit mid-period** | new limit applies immediately | new limit applies immediately; removing then re-adding cannot reset usage (it comes from OS events) | new limit applies immediately; a changed window length re-evaluates expiry |
| **Accessibility off** | allowance apps are blocked during any block; nothing is measured (PD-2) | same | same |
| **Usage unavailable** | per PD-13 | per PD-13 | per PD-13 |
| **Clock changes** | day key recomputed; no negative values | day key recomputed | a window start in the future is treated as no window |

### 4.6 How blocks and allowances combine (owner decisions PD-3 and PD-14)

One resolver implements this table and both the accessibility path and the foreground-service fallback call it.

| Situation | Result |
|---|---|
| A greyout window covers the app | Blocked, independent of everything below |
| No block is active (no focus session, standalone block or always-on) | Nothing is restricted. Usage is still counted all day. |
| Focus session; the app is on the explicit allow-list | Allowed. If it has an allowance, the budget applies and the app is blocked when it is used up. |
| Focus session; the app is not on the allow-list, or the list is empty | Blocked. An allowance never opens it (PD-3). |
| Standalone block or always-on; the app is on that block's list | Blocked. An allowance never overrides it. |
| Standalone block or always-on; the app is not on the list and has an allowance | Allowed within the budget; blocked when it is used up. |
| Standalone block or always-on; the app is not on the list and has no allowance | Allowed. |
| Accessibility off, a block is active, the app has an allowance | Blocked, with the reason "Allowance limits need the Accessibility service" (PD-2). |
| Accessibility off, any other case | The same rows apply, without budgets. |

---

## 5. Batches

Order: **A first** (release blockers), then **B and C** (they can run in parallel where dependencies permit), then **D**, then **E**. Do **A5 (tracker truth) first**. The foundation gate is A1, A2a, A3, and A4. C5 may start once that gate holds; it owns and closes A2b. All other Batch B/C work waits until C5 closes A2b and Batch A is complete. Owner-run evidence is collected at E1 and resolved at E3.

### Batch A: release blockers

**A5 · tracker truth (S; completed).** A5's evidence is preserved in the tracker. The v9 repair must keep that evidence, reconcile it to the current checkout, and correct summaries/statuses without copying progress from `allowance/TRACKER.md`. Current verified facts include Room version 9, the registered 8→9 migration, the scheduled maintenance worker, and the manifest/build files listed in section 3.2. No A5 reimplementation is implied.

**A1 · database and cutover (M).** Fixes P5-01, P5-02.
- Make sure a `usage_pipeline_state` row exists on every install: seed it when the database is created and, as a repair, create it if missing when the read model starts (idempotent insert). Seed with `shadow_started_on = today (local)`, `cutover_date = NULL`.
- PD-1: remove the upgrade-day cutover. Builds with database version 7 or 8 may be in other people's hands, so make `MIGRATION_7_8` a data-free step and add `MIGRATION_8_9` (database version 9) that, once, sets `cutover_date = NULL` and `shadow_started_on = today` wherever a cutover date exists, and creates the state row where it is missing. It is harmless on installs that need no repair.
- Simplify `cutoverDate()`: remove the duplicated check and the re-read after the write; return the value it computed when the update changed a row. Fix the comment above the migration; document the rule for both install paths.
- *Tests (JVM, fake store):* missing row is created; fewer than 7 complete days gives no cutover; 7 complete days at the current pipeline version gives a cutover; an older pipeline version does not count; repeat calls are idempotent. *Migration tests:* 6 to 9, 7 to 9 and 8 to 9, a fresh install, and the legacy `user_version = 0` path. Run them as JVM or Robolectric tests if the project can; otherwise write them as instrumented tests that compile, and rely on scenario S2 on the owner's phones (E1).
- *Done when:* a brand-new install and an upgraded install reach the same state and behavior.

**A2a · maintenance job core (M).** Fixes P5-03, P5-19.
- Create a worker that does not need the old gateway (for example `UsageMaintenanceWorker`) and runs, in order: rollup of recent past days, detection for completed days, prune. Schedule it as unique periodic work (keep policy) from `FocusFlowApp.onCreate`. Leave the old `BackgroundFetchWorker` duties (late-start warnings, reminders, morning digest) alone; they are not Phase 5. Remove its dead detection and prune calls only once the new worker is live, so nothing runs twice.
- Detection gate: write `last_detection_run_date` only when the days detection needs are COMPLETE (or when permission is denied and nothing can be read); otherwise leave the marker and retry at the next run. Also run detection once after a successful rollup that completes the needed days.
- Inject the clock and zone into prune and detection. Analytics use local dates; tasks keep their current UTC handling.
- *Tests (fake clock and stores):* order is rollup, detection, prune; an unknown rollup result does not mark the day and retries; success marks it once per local day; the prune horizon uses the injected clock.
- *Done when:* a log or counter shows the job scheduled at app start, and the tests pass. This is the A2 core only; the C5 rollup-prune integration is tracked separately as A2b.

**A2b · rollup-prune integration (owned by C5).** This is the one permitted cross-batch dependency that completes A2. It is blocked by C5; C5 may start after A1, A2a, A3, and A4 are complete. C5 adds and tests pruning for all rollup tables and wires it through the maintenance worker's existing prune operation. A2b is complete only when C5's worker-invocation test passes. This item does not block C5 from starting.

**A3 · Stats zero reading (S to M).** Fixes P5-04, P5-17; PD-7.
- In `DeviceUsageSource.read`, keep every app of the day:

  ```kotlin
  // line 74 was: ).associateBy { it.date }
  ).groupBy { it.date }

  // lines 147-150 were: listOfNotNull(pipelineDays[dateText]?.toHistoryAppDay())
  UsageHistorySource.LIVE_PIPELINE,
  UsageHistorySource.ON_DEMAND_PIPELINE -> selectedRows +=
      pipelineDays[dateText].orEmpty().map { it.toHistoryAppDay() }
  ```
  Cache app name and category per package inside one read. Do not change the aggregator or the rollup writer.
- Remove `mergeTodayUsageFallback` and its use of `getUsageSummary` (PD-7). Give `PhoneUsage` or the card an explicit "unavailable" state used when events cannot be read; never show 0 for a failed read.
- `readSource` currently swallows every exception with no log: log the exception type and the reason, and return a typed failure.
- Add one debug-level line per read: event count, session count, rows per date before grouping, each date's source and status, selected and displayed row counts.
- *Tests (through `DeviceUsageSource.read`, fake event source and fake history store):* one day with Instagram 40 min, WhatsApp 15, Maps 3 and Settings 0.4 s gives summary 58, daily 58, hourly sum about 58 and three apps; a week of four stored rollup days (120, 90, 150, 60) plus a live today gives 478; the same multi-app day served live and from a rollup gives identical per-app minutes; unavailable events give the unavailable state, not zero.
- *Owner check (E1):* Today and Week show non-zero totals that match their daily bars.

**A4 · test baseline (M).** Fixes P5-06 (the test half).
- Remove every test that reads `.kt` files as text (the Batch 1 and Batch 2 characterization tests). Replace them with baseline behavior tests of behavior that exists at this stage, using fake clocks and sources.
- Add the fresh-database test and keep the migration tests compiling (and running wherever the project can run them).
- Record a crosswalk in the tracker assigning every section 6 test group to the batch that introduces or owns it; A4 does not implement later batches' tests.
- *Done when:* no test reads a `.kt` file as text, A4's baseline behavior tests pass, and all section 6 test groups are assigned to their owning batches in the tracker.

### Batch B: one meter everywhere

**Batch B gate.** A1, A2a, A3, A4 and A2b are complete. B and the remaining C work may then proceed in parallel where their task dependencies permit. No B1a/B1b early-start exception is recorded in v9.

**B1a · accessibility lifecycle, recovery and messaging (M).** Fixes the independent portions of P5-05, P5-10 and P5-27; PD-2.
- Keep the meter in the accessibility service. `onInterrupt` must not tear it down; teardown happens only on unbind and destroy, and a rebind recreates and starts the meter (a start is a "read due" event).
- Apply the persisted checkpoint once when the meter starts, clear the marker after applying, prevent double credit, and remove recovery from the foreground service.
- In allowance screens and the permission health card say: "Allowance limits only work while the Accessibility service is on. While it is off, apps with an allowance are blocked during blocks."
- *Tests (JVM):* interrupt does not stop the meter; rebind starts it again; recovery credits at most the cap once; marker clearing and no-double-credit behavior are verified.
- *Done when:* lifecycle, recovery and messaging behavior tests pass. S4 remains owner-run for E1 and does not block the B or D automated gate.

**B1b · fallback resolver integration (M).** Fixes the fallback portion of P5-05; PD-2. Depends on B6.
- Remove the foreground service's allowance mirror: `isFallbackBlocked` stops reading the ledger. While Accessibility is off, an app with an allowance is blocked during an active block with the reason "Allowance limits need the Accessibility service". Implement this through B6's shared resolver so both paths agree.
- *Tests (JVM):* fallback blocks an allowance app during a block with the required reason and never reads the ledger (G2).
- *Done when:* B6 resolver integration and the listed tests pass.

**B2 · foreground signal filter (M).** Fixes P5-09.
- One `ForegroundSignalFilter` in front of the meter; the accessibility service is the only signal source (PD-2). Ignore non-app windows: input-method packages (resolved from the system, not hard-coded), system UI, our own overlay and blocked screens, accessibility overlays. Dialog-bridge packages (permission controller, intent resolver; extend after the device matrix) bridge for up to 3 s. Only a launchable app or home counts as a foreground change.
- Remove the accessibility service's own `currentTimed*` state; it reads the meter's current target.
- Count mode adds an estimated open only for a signal the filter classifies as a switch from a different real app or home.
- *Tests:* a scripted keyboard, notification shade or permission dialog over an allowance app adds no opens; A, B, A adds exactly one; the count never goes up and then down by itself.

**B3 · read policy (L).** Fixes P5-08 and the quadratic bridge check in P5-29.
- Implement the cadence table (4.4). No read for windows of non-allowance apps except one read when an allowance app leaves the front. At least 10 s between non-lifecycle reads.
- Make a read's cost independent of how many events happened earlier in the day (carry the last processed time and tracker state, with a full re-scan only at start and at date change), or stay inside the budgets of 4.4 with the reduction off the main thread.
- The reduction (sessions, measurement, JSON) runs on a background dispatcher. Replace `withTimeout` around the blocking read with a cancellation-aware read so a slow read cannot pile up.
- Write ledger JSON only when a value changed meaningfully; do not rewrite every target on every read.
- Make `isDialogBridgeReturn` linear.
- Add debug counters: reads per hour, writes per hour, last read reason and outcome.
- *Tests:* 200 scripted non-allowance window signals cause no reads when no allowance app was in front and at most one per debounce window otherwise; an allowance app in front for 10 minutes produces the expected tick count; the platform read and the reduction never run on the main dispatcher (fake dispatcher assertion); ledger writes per read are at most the number of targets whose value changed.

**B4 · closed time versus open tail (M).** Fixes P5-07; PD-13.
- `measure` returns closed usage and open-tail usage separately. The ledger remembers only closed usage as a floor. The tail is recomputed on every read and is never stored as confirmed. A tail older than the 4 h cap counts as closed at the cap.
- The JSON codec still reads old records (treat the old `confirmedUsedMs` as closed time).
- *Tests:* a late end event after a read that included a tail lowers the effective value to the true one; tail growth over consecutive reads; the cap; midnight; an interval window; restart.

**B5 · per-mode rules as tests (M).** Turn every row of 4.5 into a test and fix any deviation. Do time budget and interval first (the owner's main modes), then count. Include: count N-1 then an open (allowed, stays usable) then the next open (blocked); time budget exhaustion within one tick of the timer; interval window starting at the first open, crossing midnight without reset, expiring and restarting; usage revoked (PD-13); config edit mid-period.

**B6 · config, block decision, editor lock (L).** Fixes P5-11, P5-12, P5-13; PD-3, PD-4, PD-14.
- One typed `AllowanceConfig` and one parser, with documented defaults (budget minutes as an integer, interval minutes, interval hours, mode enum). Replace all parse sites (accessibility service, foreground service, launcher, `ActiveScreen`, `AllowanceUsageStateStore`, `SettingsRepository`). Expose it as a flow and invalidate any cache on a config write.
- One block-decision resolver that implements the table in 4.6 and is used by both the accessibility path and the fallback. It replaces the focus branch of `isPackageBlocked` (an empty explicit list now blocks everything), the allowance check order in the event handler, and `isFallbackBlocked`. Update the Focus screens' wording ("Apps allowed during Focus. If you add none, no app is allowed.") and the allowance help text.
- The lock predicate (PD-4) lives in the repository or view model, not only in the UI.
- *Tests:* parser table tests (missing fields, wrong types, unknown mode); one test per row of 4.6 run against both paths with identical inputs; the lock predicate for each block type.

**B7 · ledger hygiene (M).** Fixes P5-26 and part of P5-28.
- Delete the dead legacy API (`recordOpen`, `accumulateTimedUsage`, `raiseCountUsage`, `raiseTimeUsage`) after a search shows no callers. Split the file below 300 lines. Define the `active_session_*` constants once. Cache `snapshot()`. Make the UI observe changes instead of polling every second. Document the crash window of an asynchronous write.

### Batch C: one definition of usage

**Batch C gate.** The Foundation Gate is A1, A2a, A3 and A4 complete. C5 may start at that point and is responsible for completing A2b; it must not depend on A2b being complete. All other C work waits for C5 to close A2b and for Batch A to be complete. After that, B and C may proceed in parallel where dependencies permit.

**C1 · session definition (M).** Fixes P5-14; PD-10.
- Merge same-package sessions per PD-10. Define "session" in the code documentation. Bump `CURRENT_PIPELINE_VERSION`, so stored days are recomputed and the cutover gate counts only current-version days.
- *Tests:* A then A merges; A, B, A does not; A, screen off, A does not; the 3 s boundary; launch counts are unchanged.

**C2 · one inclusion policy (M).** Fixes P5-15, P5-16.
- One `UsageInclusionPolicy` applied at session level for Stats, rollups and detectors: own package, system UI and home are excluded, with home resolved dynamically (a home-intent query, cached and refreshed on package changes) plus the current static list as a fallback; the launchable rule and the minimum-duration rule live in the same place. The allowance measurement does not use it (it measures the explicit package).
- Verify package visibility against the current manifest and actual package-manager queries; record the source evidence and any limitation. The current checkout contains `app/src/main/AndroidManifest.xml`, including `QUERY_ALL_PACKAGES`; do not defer this repository-verifiable check to an owner device session.
- *Tests:* for one range the hourly sum, the daily sum and the summary total agree; an unknown launcher package in the event log is excluded when the system says it is home.

**C3 · detector windows (S).** Fixes P5-18. Add a usage-specific end-date helper; give the task-based detectors back their own end date unless the owner decides otherwise; test both.

**C4 · side effects, clocks, dead code (M).** Fixes P5-23, P5-28, P5-29.
- Move the cutover write out of read calls into the maintenance path. Make `getActiveFindings` read-only and run the one-off repair once (a stored flag or a migration). Inject the clock into `RoomUsageHistoryStore`. `StatsViewModel` uses the repository instead of `AppModule.database`. Delete `getUsageSummary`, `PhoneUsageSummary.kt` and any unused DAO method after a search.

**C5 · retention (S).** Fixes P5-20 and owns A2b.
- Add prune methods for all three rollup tables with the same 90-day horizon as the legacy tables.
- Wire them into the A2 maintenance worker through its existing prune operation; do not add a no-op hook or duplicate schedule.
- *Tests:* prove the retention boundary for each rollup table and prove the maintenance worker invokes rollup pruning.
- *Done when:* every usage table is pruned at the required horizon (G8), the tests pass, and A2b is closed by the worker-invocation evidence.

**C6a · cutover contract and harness (M).** Fixes P5-24; PD-8, PD-9.
- Document that the cutover gate proves completeness only; legacy values are not a correctness reference.
- Document and test rollback: set `cutover_date` to NULL, serve history from legacy tables again, and resume the legacy writer.
- Build a golden-log harness and run it on synthetic fixtures through the tracker, aggregator and `measure`; define the fixture format for owner logs. This automated evidence is required before D.
- Keep the legacy tracker unchanged (PD-8).
- *Done when:* completeness-only contract, rollback behavior and synthetic-fixture harness are evidenced.

**C6b · owner golden-log replay (E3).** The owner supplies event logs from both phones, including one non-Pixel device. Run C6a's harness on those logs and record the result at E3. This owner-dependent replay does not block D or E1; it must be completed or explicitly deferred with owner approval before close-out.

### Batch D: screens and wording

**Batch D gate.** B1a, B1b, B2–B7 and C1–C6a must meet their automated done-when criteria. Owner-run S1, S2 and S4, and C6b's owner-log replay, do not block D; their status remains pending until evidence or owner-approved deferral.

**D1 · freshness everywhere (M).** Fixes P5-21. Expose the one freshness function (4.3) and carry it in the allowance UI model. The launcher, `ActiveScreen` and `DailyAllowanceModal` use it. `DailyAllowanceModal` either shows `usageByPackage` or drops the parameter. *Tests:* reducer tables, including "idle after the app left is exact".

**D2 · wording, permissions, backup note (M).** Fixes P5-22; PD-4, PD-5, PD-6. Update this checklist together:
- `PermissionSupport` Usage Access definition (foreground detection, allowance measurement, history) and its "broken without" lines.
- Stats screen text and `DataHealthNotice` copy (required, not optional).
- `FocusScreen`, `StandaloneBlockModal` (allowance labels and help), `DefenseScreen`, `SettingsHowToUseScreen`, `ChangelogScreen`, `PrivacyPolicyScreen` (the rollup stores per-session package names and times on the device).
- Backup and restore screens: the usage-history note (PD-6).
- One `UsageAccess.isGranted` definition (including the `MODE_DEFAULT` handling and the failure behavior) used by every caller; `ActiveHeaderButton` must not treat an error as granted. Make the `unsafeCheckOpNoThrow` change inside that one function.

**D3 · unavailable state (S).** With A3: the Stats card shows "unavailable, grant Usage Access or try again", never 0m, for a failed or denied read.

### Batch E: verification and release

**Batch E gate.** D is complete by its automated done-when criteria.

**E1 · owner device session (M).** The agent writes everything section 7 lists (the guide, the result template, diagnostics, log commands and triage rule) and puts them in the repository. The owner runs one initial full session at the end, then reports results; only failed scenarios may be retested, for at most two rounds. Collect S1–S12 and the C6b event logs. Evidence is attached to the tracker. Also confirm that the exported Room schema for database version 9 is committed.

**E2 · release gates (S).** Internal testing, then closed testing, then a staged rollout, watching crash and ANR rates; the rollback is to halt the rollout. If the app ships through Google Play, schedule the target-API work package (PD-12) before any update.

**E3 · close-out (S).** Run the C6b harness on the owner logs; record C2 manifest/package-query verification or its repository-evidenced limitation; reconcile every owner-run item as passed or explicitly deferred with owner approval; rerun affected automated/device checks after any post-E1 code change; final tracker and G1–G9 evidence pass; preserve v8 as an archive.

---

## 6. Review gates and required tests

### 6.1 Review gates (each must be checkable by search or by a test)

- **G1.** Only `ForegroundSpanTracker` and its adapter touch platform usage events (`queryEvents`, `queryUsageStats`, `UsageEvents`), apart from the foreground probe in the foreground service, which must not produce durations.
- **G2.** Only `AllowanceLedger` reads or writes `daily_allowance_used` and the `active_session_*` keys. The foreground service does not read them.
- **G3.** Only the typed config parser reads `daily_allowance_config`.
- **G4.** No `associateBy` or `toMap` over a list that has one row per (date, package).
- **G5.** No test reads Kotlin source files as text.
- **G6.** The platform read and the reduction never run on the main dispatcher (asserted in a test).
- **G7.** No direct wall-clock call (`System.currentTimeMillis`, `LocalDate.now`, `Instant.now`) in analytics or enforcement logic outside a clock provider.
- **G8.** Every usage table (legacy and rollup) is pruned, and a test proves it.
- **G9.** No catch-all converts a failure into an empty or zero result without a log line and a typed state.

### 6.2 Required tests (names are suggestions)

- **Ledger and measure:** closed-versus-tail ratchet (late end event lowers the effective value); tail cap; midnight split; interval window across midnight; interval expiry and restart; count rollover; old JSON records still decode.
- **Meter:** an interrupt does not stop it and a rebind restarts it; recovery credits at most the cap once; no reads for non-allowance windows; debounce; reads off the main dispatcher; freshness table (idle after leaving is exact; in front needs a read within two ticks; revoked is unavailable).
- **Signal filter:** keyboard, shade, permission dialog and own overlay add no opens; A, B, A adds one.
- **Per-mode behavior:** every row of 4.5 (section B5).
- **Config and resolvers:** parser table tests; allow-list parity between the accessibility path and the fallback; lock predicate for focus, standalone and always-on.
- **Stats:** the four `DeviceUsageSource` tests in A3; hourly sum equals daily sum equals summary total for one range; unavailable state instead of zero.
- **Pipeline:** session merging (C1); inclusion policy (C2); launch counts unchanged by merging.
- **Database and history:** fresh database; migrations 6 to 9, 7 to 9 and 8 to 9 and the legacy `user_version = 0` path (JVM/Robolectric preferred; if runtime instrumentation is unavailable, instrumented-test compilation plus S2 on both owner's phones and committed schema is the accepted fallback); cutover gate cases (A1); rollback to legacy (C6a); pruning for every usage table (C5).
- **Worker:** order, marker rules, retry, once per local day, injected clock.
- **Detectors:** usage windows end yesterday; task detectors unchanged unless the owner decided otherwise.

**Ownership rule.** A4 assigns each test group to its owning batch in the tracker crosswalk; the group is required at that batch's automated gate, not in A4 before the feature exists.

---

## 7. Owner device protocol

**How testing works here.** The owner runs **one initial full session at the very end** (Batch E), on a **Realme C3 (Android 10)** and a **Samsung M31 (Android 12)**. Only failed scenarios are retested, for at most two rounds. The owner is not a developer: they can say in plain words how well each scenario worked and can capture `adb logcat` output when asked. Everything before Batch E must be proven by automated tests.

**Protect the upgrade check.** S2 requires an existing pre-v9 installation with data. On any phone needed for S2, run and record S2 before uninstalling, clearing app data, or performing S1's fresh install. Confirm the starting app/database version and that data worth preserving exists. A fresh install or compile-only instrumentation is not proof that an upgrade migration ran.

**What the agent must produce (E1)**
1. **`OWNER_DEVICE_TEST_GUIDE.md`**, written for a non-developer: one short section per scenario below, each with preconditions, exact steps (what to tap, how long to wait), what a pass looks like (one sentence and, where it applies, one number) and what to write back.
2. **A result template** the owner copies for each scenario: scenario, phone, pass / partly / fail, what happened in one or two lines, and the numbers the guide asked for.
3. **Debug-only usage diagnostics** (debug builds only, package names only): reads and writes per hour; last read time, reason and outcome; freshness per allowance app; a ledger snapshot per package; rollup status and source per date; pipeline version, `shadow_started_on` and `cutover_date`; earliest available event time. A one-tap "Copy report" puts the report on the clipboard so the owner can paste it into a message without adb.
4. **One log tag** for every new diagnostics line (for example `FFUsage`), so one command is enough. The guide gives these commands exactly:
   - before a scenario: `adb logcat -c`
   - after it: `adb logcat -d -v time -s FFUsage > ffusage.txt`
   - after a crash: `adb logcat -d -b crash > crash.txt`
   - only if the agent asks: `adb shell dumpsys usagestats > usagestats.txt`
5. **A triage rule.** After the initial full session, the owner reports results. The agent fixes failures and asks for retests of only affected scenarios, at most two rounds. Any post-E1 code change reopens the affected automated tests and device scenarios; old pass results cannot be carried forward for affected behavior.

**Phone setup the guide must include.** Both phones come from manufacturers with aggressive background management. The guide tells the owner to run each scenario first with FocusFlow on the phone's default battery setting and note whether the accessibility service or the foreground service was stopped, then again with the battery setting on unrestricted (or "never sleeping"), and to report the difference.

| # | Scenario (on both phones) | Pass when |
|---|---|---|
| S1 | Fresh install, grant Usage Access, open Stats Today and Week | non-zero totals; top apps and hourly bars present; the daily total is close to the phone's own screen-time page (PD-11) |
| S2 | Upgrade over an install that already has data | no crash; old history still there; the rest of the app works; the diagnostics show no upgrade-day cutover |
| S3 | **Time budget (5 min) and interval (5 min per 1 h) first**, then count (2 opens). Script: use the app, open the keyboard, pull the notification shade, trigger a permission dialog, move between screens in the app, turn the screen off and on, switch apps | opens within one of the script, time within about 30 s, the app is blocked or closed when the limit is reached, counts do not jump by themselves |
| S4 | Turn the Accessibility service off and start a block | apps that have an allowance are blocked during the block and the notice explains why; turning the service back on restores normal behavior |
| S5 | Turn the accessibility service off and on, force-stop the app, reboot | usage so far is kept, nothing is counted twice, the meter resumes |
| S6 | An allowance app in front across midnight for at least 2 minutes | the time budget splits at midnight, count does not add an open, an interval window is not reset |
| S7 | Revoke and re-grant Usage Access while an allowance app is in use | the card says the number is approximate, enforcement continues, the permission health card shows the problem |
| S8 | Edit a budget, change mode, remove and re-add an app; try the editor during a focus session, a standalone block and always-on | the new limit applies at once; removing and re-adding does not reset usage; loosening edits are refused during a block |
| S9 | One ordinary day (about 24 h) with one allowance configured | the diagnostics counters stay inside the 4.4 budgets; no "not responding" dialog |
| S10 | Use an app with several screens for 5 minutes | the session count matches the C1 definition (one visit, not one per screen) |
| S11 | Export one day's event log from each phone with the debug tool | the logs are attached for the golden-log replay (C6b) |
| S12 | Leave the phone idle overnight with the screen off, then check that allowances still enforce | the services are still running; if they were stopped, report which phone and which battery setting |

---

## 8. Tracker rules

- Every item carries `status` (NOT STARTED, IN PROGRESS, BLOCKED with reason, DONE with evidence, OWNER-RUN PENDING), `blocked by`, `evidence` and `owner-run`.
- Never tick an item without the evidence it names. Never tick an owner-run item.
- A PD answer goes into the decision log. An applied default is marked "default applied, awaiting owner".
- Report line counts as numbers; they are not a pass or fail gate. A new file over 300 lines needs a one-line reason.
- A batch is DONE only when its "done when" holds and its tests and gates pass. At the end of each batch, list what remains.

---

## 9. Adjacent backlog (not Phase 5; separate pull requests)

| ID | Item | Note |
|---|---|---|
| AD-1 | `NotificationActionReceiver` starts an activity from a broadcast receiver after a notification action | Android 12 and later restrict notification trampolines. Verified in code. Route the action so the activity is started by the notification's own intent. |
| AD-2 | Foreground-service start from boot and time-limited foreground-service types | Current manifest declares `dataSync|specialUse`; verify service-start behavior and applicable platform requirements separately. Keep this outside Phase 5. |
| AD-3 | Target API level | See PD-12. |
| AD-4 | The permission health card can show "needs attention" for a moment before its first read | Reported by other reviews; small UI fix. |
| AD-5 | Hard-coded zero for blocked attempts in Stats, and the foreground-service consent gate | Reported by other reviews; not verified by me. Check before fixing. |
| AD-6 | The old background-fetch duties (late-start warnings, reminders, morning digest) are never scheduled | Follows from P5-03. Product decision whether they should run; separate task. |
| AD-7 | Crash when tapping the "rate your day" notification, and it does not open Stats Extra on Today | Separate write-up `DAY_RATING_NOTIFICATION_CRASH_FIX.md`. |

---

## 10. Definition of done

1. Every P0 and P1 item is DONE with evidence; every P2 item is done or deferred with a reason.
2. A fresh install and an upgraded install reach the same state and behave the same (automated evidence plus S1/S2).
3. The owner has completed the initial full device session on both specified phones; failed scenarios were retested for at most two rounds. Each remaining failure is summarized and explicitly accepted or deferred by the owner.
4. Migration tests ran wherever the available environment supports runtime execution. If runtime instrumentation is unavailable, the accepted fallback is: the named instrumented tests compile, S2 passes on both phones over data-bearing pre-v9 installs, and the exported Room schema for version 9 is committed. Compile-only is never described as runtime execution or a runtime pass.
5. C6a's synthetic-fixture harness passes before D; E3 replays it on logs from both phones (including one non-Pixel), or the owner explicitly approves a documented deferral.
6. C2's manifest/package-visibility check is recorded from repository evidence; any actual limitation is documented and resolved or explicitly deferred with owner approval.
7. Review gates G1 to G9 pass, and `TRACKER.md` matches the current code, exact test results, owner reports and recorded decisions.
8. E2 release gates are in place, and the target-API work package is scheduled if Google Play distribution applies.

**What this plan deliberately does not do:** restore the old accumulators or the 60 s sync; change the `.focusflow` format; touch the old background-fetch duties; relax the cutover seam without device evidence; or require a second measuring path.
