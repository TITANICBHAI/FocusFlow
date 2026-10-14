# FocusFlow Fixes Phase 5 — Main Work Tracker (v9)

**Primary tracker for the plan in `fixes/PLAN.md`.** This file is the authoritative progress record for this fixes effort. The previous v8 plan and tracker are preserved under `fixes/archive/`. `allowance/TRACKER.md` belongs to a separate workstream: it may be read as a reference for tracker format and discipline, but its statuses, evidence, decisions, and progress do not apply here and must not be updated for this work.

The v9 plan supersedes `allowance/PLAN.md` (v7) for this work. The plan is the implementation contract; it is not authorization by itself. See `fixes/AGENT_PRE_PROMPT.md` before starting work.

**Progress preservation:** Existing A5, A1, A2, A3 and A4 evidence and failure history are retained below. Product decisions are transcribed from section 3 of the plan and are not independently re-confirmed here.

---

## Tracker rules

### Status vocabulary

- `NOT STARTED`
- `IN PROGRESS`
- `BLOCKED — <reason>`
- `DONE — evidence recorded`
- `OWNER-RUN PENDING`

Every task follows the dependency map below; batch letters are groupings, not gates. Each work item records its status, local blocker, evidence, and whether it is owner-run. Item-level blockers must not add a prerequisite that is absent from the map.

### Before and during a batch

- Before starting a task, confirm every prerequisite in the dependency map is `DONE`; then record `IN PROGRESS`, `Started`, and read the matching plan section in full.
- Update the relevant notes after each meaningful change; do not wait until the batch is complete.
- Follow the order and parallelism allowed by `fixes/PLAN.md`. Do not silently widen scope.
- Preserve unrelated user changes and data. No destructive task/data replacement without the user's authorization.

### Checkboxes and evidence

- Tick an item only when it is complete and verified by the evidence named for it. Never tick partial, attempted, unverified, or owner-run work.
- For each completed item, record changed files, the exact command/test/search/device report used, and the observed result.
- If a check fails, keep its item unchecked, append the observed failure, and later append the fix and successful rerun. Never erase failure history.
- If evidence is unavailable, the check remains open. Do not infer a pass from source presence or from an older tracker.

### Decisions and completion

- Record decisions made, alternatives rejected, defaults, blockers, and deferred work. An applied default that still needs the owner's answer must say `default applied, awaiting owner`.
- Mark a batch `DONE — evidence recorded` only when its “done when” condition, required tests, and gates pass. Reconcile every checkbox against current code and observed outputs first.
- Leave incomplete work unchecked and explain why. Owner-run checks remain `OWNER-RUN PENDING` until the owner supplies results; the agent never ticks them.
- At project close-out, reconcile this tracker with the code, test results, and owner reports. Preserve archived plans and tracker history; do not delete them.

---

## Dependency map and order

Copied from `fixes/PLAN.md`, section 5.0. A task may start and be marked done when everything in its `Needs` column is done. C5 owns and closes A2b; A2b does not block C5 or unrelated tasks. Update this map and the Summary with every status change.

| Task | Needs | Status |
|---|---|---|
| A5 | — | DONE — evidence recorded |
| A1 | A5 | DONE — evidence recorded |
| A2a | A5 | DONE — evidence recorded |
| A2b | C5 | BLOCKED — C5 |
| A3 | A5 | DONE — evidence recorded |
| A4 | A5 | DONE — evidence recorded |
| B1a | A4 | NOT STARTED |
| B1b | B1a, B6 | BLOCKED — B1a, B6 |
| B2 | A4 | NOT STARTED |
| B3 | B2, B4 | NOT STARTED |
| B4 | A4 | NOT STARTED |
| B5 | B2, B3, B4, B6 | NOT STARTED |
| B6 | A4 | NOT STARTED |
| B7 | B3, B4, B6 | NOT STARTED |
| C1 | A1, A4 | NOT STARTED |
| C2 | A3, C1 | NOT STARTED |
| C3 | A2a | NOT STARTED |
| C4 | A1, A2a, A3 | NOT STARTED |
| C5 | A1, A2a | NOT STARTED |
| C6a | A1, C1, C4 | NOT STARTED |
| C6b | E1, C6a | OWNER-RUN PENDING |
| D1 | B1a, B3, B4, C6a | NOT STARTED |
| D2 | B1b, B6, C6a | NOT STARTED |
| D3 | A3, D2, C6a | NOT STARTED |
| E1 | B1a, B1b, B2, B3, B4, B5, B6, B7, C1, C2, C3, C4, C5, C6a, D1, D2, D3 | NOT STARTED |
| E2 | E1 | NOT STARTED |
| E3 | E2, C6b | NOT STARTED |

One valid order is: **A5 > A1 > A2a > A3 > A4 > B1a > B2 > B4 > B6 > B3 > B1b > B5 > B7 > C1 > C2 > C3 > C4 > C5 > A2b > C6a > D1 > D2 > D3 > E1 > C6b > E2 > E3.** Other orders are valid if they respect the map. Owner-run checks do not block a task unless represented by a dependency such as C6b needing E1 logs.

---

## Decision log

Decision entries below are transcribed from `fixes/PLAN.md`, section 3.1–3.2. The plan is the recorded source; do not represent these as independently re-confirmed during implementation.

| PD | Decision recorded in the plan | Tracker status / application |
|---|---|---|
| PD-1 | Same seven-day gate for fresh and upgraded installs; no upgrade-day cutover. | RECORDED FROM PLAN; implement and verify in A1. |
| PD-2 | Allowance limits require Accessibility; when it is off, allowance apps are blocked during any active block and the UI explains why. No fallback meter. | RECORDED FROM PLAN; lifecycle/messaging in B1a; fallback resolver behavior in B1b/B6. |
| PD-3 | An empty Focus allow-list means no app is allowed; an allowance never widens Focus. | RECORDED FROM PLAN. The detail has a safe default of “No” and remains awaiting owner confirmation as specified in section 3.2. |
| PD-4 | Loosening allowance edits are locked during any active block; tightening remains allowed. | RECORDED FROM PLAN; implement and verify in B6/D2. |
| PD-5 | Usage Access is required; enabling an allowance prompts for it. | RECORDED FROM PLAN; implement and verify in D2. |
| PD-6 | Usage history is not exported; `.focusflow` format remains unchanged and backup/restore explains history is rebuilt. | RECORDED FROM PLAN; implement and verify in D2. |
| PD-7 | Remove the Today Stats fallback after the Stats fix; a failed read is unavailable, never zero. | RECORDED FROM PLAN; implement in A3/D3. |
| PD-8 | Do not fix the legacy tracker; retain its known limitations and stop its writes 14 days after cutover. Never use legacy numbers as validation reference. | RECORDED FROM PLAN; preserve in C6a. |
| PD-9 | Keep refusing usage windows that cross cutover; the data-health notice explains the blackout. No relaxation without device evidence. | RECORDED FROM PLAN; preserve and verify in C6a. |
| PD-10 | Merge same-package sessions across a 3-second gap when no other package intervenes. | RECORDED FROM PLAN; implement and verify in C1. |
| PD-11 | Compare Stats with device screen-time totals within about 20% or 15 minutes, whichever is larger; if unavailable, validate internal totals and top apps. | RECORDED FROM PLAN; owner validation in E1. |
| PD-12 | Google Play target API requirement remains an open product question; treat it as a release gate, with no Phase 5 implementation. | OPEN; keep release gate in E2 and do not change target API in this scope. |
| PD-13 | When usage data is unavailable, enforce using last known value plus projection, mark values approximate, and show revoked permission in permission health. | RECORDED FROM PLAN; implement and verify in B4/D1. |
| PD-14 | Usage is counted all day; allowances restrict only while a block is active and never open an app a block keeps closed. | RECORDED FROM PLAN; implement and verify in B6/D2. |
| Modes | Keep count, time-budget, and interval modes; time budget and interval are the main focus, count follows. | RECORDED FROM PLAN; test in B5. |
| Devices | One initial full owner session on Realme C3 (Android 10) and Samsung M31 (Android 12); only failed scenarios are retested, for at most two rounds. | RECORDED FROM PLAN; owner-run in E1. |
| Dependency repair | C5 needs A1 and A2a and owns the A2b worker-prune integration; unrelated tasks follow their individual Needs entries. | Current plan/tracker rule; task-level dependencies replace batch-wide gates. |
| Manifest evidence | Current manifest/build files are in the repository; C2 verifies package-query needs from source and records any limitation. | Repository-verified; no owner-manifest task unless C2 identifies a decision only the owner can make. |

---

## Batch A — Release blockers

### A5 — Tracker truth (v9 reconciliation)

**Status:** `DONE — evidence recorded`<br>
**Blocked by:** None — do this first<br>
**Started:** 2026-10-11<br>
**Completed:** 2026-10-12<br>
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [x] | Verify current Room database version, registered migration path, and A1 status against the current checkout. | DONE — evidence recorded | — | Current source reports Room version 9; `MIGRATION_8_9` is registered. The v8 audit note below is retained only as dated history. | No |
| [x] | Audit old plan-version references; keep explicit historical supersession/archive references and remove stale active references. | DONE — evidence recorded | — | `rg -ni` audit: remaining v7 mentions are explicitly historical; no stale active reference remains. | No |
| [x] | Audit all checkboxes and statuses; leave unsupported or unverified work incomplete. Confirm no allowance-tracker progress was imported. | DONE — evidence recorded | — | Historical initial tracker state (2026-10-11): no completed items; no allowance progress copied. The current v9 statuses are reconciled in the summary below. | No |
| [x] | Verify the Batch 0 usage-table writer/reader map against code; identify `AppUsageAndSessionTracker` as legacy-table writer if confirmed. | DONE — evidence recorded | — | DAO call-site search and source inspection: tracker is producer; readers and worker pruning are detailed in Notes. | No |
| [x] | Verify the `SettingsRepository.getAllowanceSnapshot` purpose and whether `resetDailyAllowanceUsage` has callers. | DONE — evidence recorded | — | `SettingsRepository.kt:1193-1210`; `SettingsViewModel.kt:108`; workspace search found only reset declaration. | No |
| [x] | Confirm every batch/item has status, blocker, evidence, and owner-run fields; reconcile this decision log with PD-1–PD-14. | DONE — evidence recorded | — | v9 structural audit: 114 work-item rows; all have six cells for the six declared columns. Decision log cross-checked against the active plan. | No |
| [x] | Reconcile active tracker statements with current source, v9 plan, and evidence; keep unverified work open. | DONE — evidence recorded | — | v9 updates current database/manifest facts, separates A2a/A2b, B1a/B1b and C6a/C6b, and removes unsupported owner gates. Dated v8 evidence remains labeled historical. | No |

**Done when:** active statements match current source and the v9 plan; older observations remain explicitly historical.<br>
**Notes:**
- `NOT STARTED` in later batches means the corresponding v9 acceptance work has not begun; it does not claim similar code is absent. Existing behavior must be re-verified against the acceptance criteria.
- **Historical A5 audit (2026-10-11):** the checkout was then Room v8 with no 8→9 migration; this was the baseline used to verify A1's implementation. It is not the current database state.
- **Current v9 source check (2026-10-12):** Room is version 9, the 8→9 migration exists and is registered, and the maintenance worker is scheduled. Rollup retention is still absent from the current prune adapter; C5 owns that remaining integration. The manifest and Gradle files are present; C2 must validate actual package-query requirements, not merely manifest presence.
- **v9 documentation reconciliation (2026-10-12):** Updated the active plan, tracker and agent pre-prompt; preserved the prior v8 plan/tracker under `fixes/archive/`. The attached drafts remain unmodified and are evidence inputs, not authority. No app source, Gradle, allowance-workstream or GitHub files were changed.
- `AppUsageAndSessionTracker.kt:22-29,56-80,115-159` produces legacy daily usage (launch count and foreground time) and closed session rows. `AppBlockerAccessibilityService.kt:569` creates it, and `:1018-1024` feeds foreground window changes. `BackgroundFetchWorker.kt:116-117` only prunes old legacy rows; it does not produce usage rows. Search found no usage-write calls in `AnalyticsProcessor` or the worker.
- Readers found by DAO call-site search: `UsageHistoryRepository.kt:343,359` reads legacy app-days and sessions; `FindingDetectionRunner.kt:240-241` reads both in its direct fallback; `DayRatingRepository.kt:54` reads daily usage dates; and `StatsViewModel.kt:389-394` reads daily rows for suggested chips. Other consumers use the history repository rather than writing legacy rows.
- `SettingsRepository.getAllowanceSnapshot()` builds an `AllowanceSnapshot` from the allowance ledger and preferences (`:1193-1210`); `SettingsViewModel.kt:108` consumes it. It is a UI snapshot, not a backup path. `resetDailyAllowanceUsage()` only delegates to the ledger (`:1367-1369`); repository-wide Kotlin search found only its declaration.
- **Historical audit (2026-10-11):** the v7-reference search was performed against the v8 tracker then in effect. No progress was imported from `allowance/TRACKER.md`.
**Evidence:**<br>
- **Historical A5 evidence (2026-10-11):** changed file was `fixes/TRACKER.md`; no app source or allowance tracker changed.
- **Historical source evidence (2026-10-11):** the A5-time inspection found Room v8 and no 8→9 migration. The current v9 source fact is recorded in Notes above; the original evidence is retained, not treated as current.
- `rg -n "(dailyAppUsageDao\\(\\)|appSessionDao\\(\\)|dailyUsageDao\\.|sessionDao\\.|dailyAppUsageDao\\.|appSessionDao\\.)" app/src/main/java --glob '*.kt'` plus DAO/tracker inspection: only `AppUsageAndSessionTracker` produces legacy usage/session rows, called by Accessibility; other listed DAO calls are readers or the worker's retention deletes.
- `rg -n 'getAllowanceSnapshot\\(|resetDailyAllowanceUsage\\(' app/src/main/java --glob '*.kt'`: snapshot is consumed by `SettingsViewModel`; reset has only its declaration.
- The original A5 structural/search results above describe the v8 audit and are retained as dated history.
- The earlier 110-row `awk` check is historical and predates the v9 task splits. The v9 structural audit found 114 work-item rows; every row has all six declared columns.
- **Final v9 documentation check (2026-10-12):** `git diff --check` passed; all 114 work-item rows have six cells. No application code, Gradle, tests, device runs, workflow runs, or GitHub actions were changed or started. The accidental toolchain module addition in `.replit` was restored; only the ordering of a workflow metadata table differs from the original, with no workflow or port value changed.
- `git diff --check`: passed after the whitespace correction below at the A5 checkpoint. App tests were not run because A5 was documentation-only.
**Failures and blockers:**<br>
- First `git diff --check` failed with exit 2 on trailing Markdown hard-break spaces in the A5 status metadata. Replaced those spaces with explicit `<br>` tags; the rerun passed.
- A later `git diff --check` failed with exit 2 on trailing hard-break spaces in the A1–A4 blocker metadata after updating the satisfied A5 dependency. Replaced those spaces with explicit `<br>` tags; the final rerun passed.
**Decisions and deferred work:**<br>
- No product decision changed. PD-3's detail remains the plan's default “No,” awaiting owner confirmation; PD-12 remains open. This dated A5 note's batch-start status is superseded by the current summary below.

### A1 — Database and cutover

**Status:** `DONE — evidence recorded`<br>
**Blocked by:** A5 complete<br>
**Started:** 2026-10-11<br>
**Completed:** 2026-10-11<br>
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [x] | Seed `usage_pipeline_state` on fresh database creation with local `shadow_started_on` and null `cutover_date`. | DONE — evidence recorded | A5 complete | `FocusFlowDatabase.DATABASE_CALLBACK` seeds after schema creation; the fresh-install instrumentation test compiled in the Production AndroidTest task. Runtime device check remains pending under S1/S2. | No |
| [x] | Add an idempotent read-model repair for a missing state row. | DONE — evidence recorded | A5 complete | `UsageHistoryRepositoryCutoverTest.missingPipelineStateIsSeededIdempotentlyWithTheLocalReadDate` passed; `RoomUsageHistoryStore` delegates to DAO `INSERT OR IGNORE`. | No |
| [x] | Make `MIGRATION_7_8` data-free; add version 9 `MIGRATION_8_9` to clear stale cutover dates, reset shadow start where needed, and create missing state. | DONE — evidence recorded | A5 complete | Migration implementation and upgrade-path tests inspected; `FocusFlowDatabaseMigrationTest` compiled in the Production AndroidTest task. Runtime instrumentation was not run. | No |
| [x] | Simplify `cutoverDate()` as specified; document fresh-install and upgrade behavior. | DONE — evidence recorded | A5 complete | `cutoverDate()` has no duplicate eligibility check or post-write reread; five production JVM cutover cases passed. Fresh/upgrade path documentation is in `FocusFlowDatabase.kt`. | No |
| [x] | JVM/fake-store cases: missing row repair; fewer than seven complete days; seven complete current-version days; old pipeline version excluded; repeated calls idempotent. | DONE — evidence recorded | A5 complete | All five cases in `UsageHistoryRepositoryCutoverTest` passed in the Production JVM suite (5 tests, 0 failures). | No |
| [x] | Migration coverage: 6→9, 7→9, 8→9, fresh install, and legacy `user_version = 0`; record whether tests ran or only compiled. | DONE — evidence recorded | A5 complete | `FocusFlowDatabaseMigrationTest` includes all five paths and compiled with `:app:compileProductionDebugAndroidTestKotlin`; instrumentation did not run on Replit. Owner S2 remains pending. | No |
| [ ] | Owner scenario S2 on both phones; do not tick until owner reports. | OWNER-RUN PENDING | A5 complete; E1 device session | Pending owner report | Yes |

**Done when:** fresh and upgraded installs reach the same state and behavior; automated migration/cutover evidence is recorded.  
**Notes:**<br>
- A1 implementation is complete: Room is version 9; fresh database creation and read-model repair seed the singleton row; 7→8 is data-free; 8→9 clears stale cutover state and inserts a missing row; cutover eligibility is simplified.
- Instrumented migration tests were compiled, not executed. `app/build.gradle.kts` has no Robolectric dependency, so migration coverage follows the plan's compile-only instrumentation fallback; owner scenario S2 remains pending for E1 and is not marked complete.
**Evidence:**<br>
- No application source changed during this verification pass; the A1 implementation and tests were already present in the checkout. `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:testProductionDebugUnitTest :app:compileProductionDebugAndroidTestKotlin` completed with `BUILD SUCCESSFUL`; 261 Production JVM tests passed with 0 failures, errors, or skips, and the Production AndroidTest Kotlin compile task passed.
- `UsageHistoryRepositoryCutoverTest` XML report lists all five required cases and records 5 tests, 0 failures, 0 errors, 0 skipped. `FocusFlowDatabaseMigrationTest` compiled; its migration cases were not run on a device.
- Implementation/test files verified at HEAD: `app/src/main/java/com/tbtechs/focusflow/data/local/FocusFlowDatabase.kt`, `app/src/main/java/com/tbtechs/focusflow/data/local/dao/UsageRollupDao.kt`, `app/src/main/java/com/tbtechs/focusflow/analytics/UsageHistoryRepository.kt`, `app/src/main/java/com/tbtechs/focusflow/di/AppModule.kt`, `app/src/test/java/com/tbtechs/focusflow/analytics/UsageHistoryRepositoryCutoverTest.kt`, `app/src/androidTest/java/com/tbtechs/focusflow/data/local/FocusFlowDatabaseMigrationTest.kt`, and tracked schema `app/schemas/com.tbtechs.focusflow.data.local.FocusFlowDatabase/9.json`.
- **Historical A1 verification checkpoint (2026-10-11):** the worktree was clean immediately before that tracker update; `fixes/TRACKER.md` was the only changed file then. No APK build, GitHub Actions run, or GitHub push occurred.
**Failures and blockers:**<br>
- The initial `bash scripts/test-unit.sh` run was stopped during SDK setup before Gradle tests ran: it also schedules the development flavor, contrary to the production-only test rule. The production-only unit-test task is being run separately.
- The first production-only unit-test run failed during test compilation on an unresolved helper reference inside `FakeStore`; the fake now constructs the seeded state directly, and the production-only task is being rerun.
- **Successful production-only rerun (2026-10-11):** The command above passed after the `FakeStore` correction. The instrumentation test source compiled but did not execute; S2 remains owner-run pending.
**Decisions and deferred work:**<br>
- Marked the automated A1 implementation and verification items complete using the plan's compile-only instrumentation fallback. Do not mark S2 complete until the owner reports both device results.

### A2a — Maintenance job core

**Status:** `DONE — evidence recorded`<br>
**Blocked by:** A5 (complete 2026-10-11; dependency satisfied)<br>
**Started:** 2026-10-11<br>
**Completed:** 2026-10-11<br>
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [x] | Implement a maintenance worker that runs recent rollups, completed-day detection, then prune in that order. | DONE | A5 complete | Worker sequence test; full Production suite passed | No |
| [x] | Schedule unique periodic work with keep policy from app startup; record observed schedule evidence. | DONE | A5 complete | Startup call, captured 15-minute KEEP request, and WorkManager completion log | No |
| [x] | Preserve unrelated old background-fetch duties; remove old detection/prune calls only after the replacement is live. | DONE | A5 complete | Focused replacement test passed before old calls were removed; task warnings/reminders/digest retained | No |
| [x] | Detection marker advances only when required days are COMPLETE or permission is denied; unknown results retry. | DONE | A5 complete | Complete/incomplete/permission/unknown cases covered by worker tests | No |
| [x] | Run detection after successful rollup completion; inject clock/zone for pruning and detection while task dates retain UTC behavior. | DONE | A5 complete | Injected clock/zone; UTC task-date query unchanged; Production tests passed | No |
| [x] | Worker tests: order; unknown-result retry; one success per local day; prune horizon uses injected clock. | DONE | A5 complete | `UsageMaintenanceJobTest` 7/7; full suite 268/268 | No |
**Done when:** scheduling is evidenced at app start and A2a worker tests pass; rollup-table retention is tracked separately as A2b under C5.<br>
**Notes:**<br>
- **A2 start checkpoint (2026-10-11):** User authorized A2 after A1 completed. The initial worktree was clean. Source review confirmed that startup did not schedule the maintenance job, `BackgroundFetchWorker` mixed legacy task duties with detection and legacy-table pruning, and the rollup writer and detection runner already existed. Keep unrelated task/reminder behavior out of scope. At that checkpoint, C5 had not added rollup-table retention; the current dependency map lets C5 start after A1 and A2a and assigns it A2b.
- **Initial implementation checkpoint (2026-10-11):** Added a platform-free maintenance coordinator, a WorkManager adapter, a unique 15-minute `KEEP` schedule request from `FocusFlowApp`, startup logging after WorkManager accepts the operation, local-clock rollup/detection/prune seams, and behavior tests. The job checks all three recent rollup dates before detection, retries incomplete/unknown reads, and treats missing/revoked usage permission as a terminal no-read state for that local day. At this checkpoint, the old BackgroundFetch detection/prune code remained pending replacement verification.
- **Replacement verification (2026-10-11):** Ran `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:testProductionDebugUnitTest --tests 'com.tbtechs.focusflow.background.UsageMaintenanceJobTest'`; Gradle succeeded and the XML report confirms 7/7 tests, zero failures/errors/skips. After that pass, removed only the old detection and prune calls from `BackgroundFetchWorker`; late-start warnings, task reminders, and morning digest remain. Final full Production verification is still pending.
- **Final verification (2026-10-11):** `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:testProductionDebugUnitTest` passed; Production XML contains 268 tests, zero failures/errors/skips, including all seven `UsageMaintenanceJobTest` cases. The new startup helper records the exact unique-work name, 15-minute interval, and `KEEP` policy in its test; production logs only after WorkManager's enqueue operation completes. `git diff --check` passed. No APK, GitHub workflow, push, or device run was started. At that time A2 was one parent item; v9 now separates A2a from A2b.
- **Resume verification (2026-10-11):** Rechecked the current A2 worker, startup wiring, retained `BackgroundFetchWorker` duties, and `UsageMaintenanceJobTest`. No A2 source changes were needed. After the first verification attempt timed out during initial toolchain provisioning, the cached rerun of `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:testProductionDebugUnitTest` passed: 58 Production XML reports, 268 tests, zero failures/errors/skips; `UsageMaintenanceJobTest` is 7/7. At that checkpoint A2 remained in progress because rollup-retention integration was blocked on C5. No C5 code was added.
**Evidence:**  
 - **Rollup → readiness → detection → prune:** `UsageMaintenanceJob.kt`, `UsageMaintenanceWorker.kt`, and `UsageMaintenanceJobTest.kt`; the order case passed and the full Production suite passed (268 tests, zero failures/errors/skips).
 - **App-start scheduling:** `FocusFlowApp.kt`, `UsageMaintenanceWorker.kt`, `UsageMaintenanceJob.kt`, and `UsageMaintenanceJobTest.kt`; startup invokes the scheduler, the test captured `FOCUSDAY_USAGE_MAINTENANCE` / 15 minutes / `KEEP`, and the WorkManager operation logs only after completion; Production suite passed.
 - **Background-fetch separation:** `BackgroundFetchWorker.kt` and `UsageMaintenanceWorker.kt`; replacement was tested before moving detection/prune, then old detection/prune were removed while late warnings, task reminders, and morning digest remained; Production suite passed.
 - **Readiness and retry:** `UsageMaintenanceJob.kt`, `UsageMaintenanceWorker.kt`, and `UsageMaintenanceJobTest.kt`; incomplete required days and `EVENTS_UNAVAILABLE` leave the detection marker unchanged and return retry; `PERMISSION_MISSING` permits one detection for that local day; tests passed.
 - **Injected time and UTC task dates:** `UsageMaintenanceJob.kt`, `UsageRollupWriter.kt`, `FindingDetectionRunner.kt`, `BackgroundFetchWorker.kt`, and `UsageMaintenanceJobTest.kt`; tests verified the injected 90-day prune cutoff and local-day detection marker; `BackgroundFetchWorker` continues querying task dates in UTC; Production suite passed.
 - **Required worker cases:** `UsageMaintenanceJobTest.kt`; seven cases passed for order, unknown retry, permission-denied handling, incomplete-day retry, once-per-local-day detection, injected prune horizon, and scheduling policy.
 - **Full local verification:** `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:testProductionDebugUnitTest`; 58 XML reports, 268 tests, zero failures/errors/skips. No APK was built.
  - **Resume verification against current checkout:** the same Production-only command passed after toolchain bootstrap; XML confirms 268 tests, zero failures/errors/skips, with all seven `UsageMaintenanceJobTest` cases present. No APK was built.
 - **Diff hygiene:** `git diff --check` passed.
**Failures and blockers:**  
  - **Historical C5 dependency (2026-10-11):** Rollup-table retention did not exist; the worker's legacy-table pruning remained separate. In v9, this work is tracked as A2b and remains blocked until C5.
  - **Resume verification attempt 1 (2026-10-11):** The command exceeded the five-minute shell limit while provisioning JDK 17, Android SDK packages, and Gradle; no Gradle test task result was produced. The cached rerun passed as recorded above.
**Decisions and deferred work:**  
 - Require all three recent past-day rollup rows to be `COMPLETE` at the current pipeline version before detection; this matches the writer's existing three-day rollup window.
 - Treat `PERMISSION_MISSING` and `ACCESS_REVOKED` as the documented no-read condition for a local day; retry all other unknown and incomplete results.
 - Preserve the existing `last_detection_run_date` and `last_prune_date` preference keys; keep BackgroundFetch task-date lookup on UTC.
  - Keep rollup-table retention in C5; do not add a no-op retention hook to A2a.

### A2b — Rollup-prune integration (owned by C5)

**Status:** `BLOCKED — C5 rollup retention not implemented`<br>
**Blocked by:** C5 completion<br>
**Started:** —<br>
**Completed:** —<br>
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Add rollup-table prune calls through the existing maintenance worker prune operation; do not add a second schedule or no-op hook. | BLOCKED — C5 | C5 retention implementation | Pending | No |
| [ ] | Test the retention boundary for every rollup table and prove the scheduled maintenance worker invokes rollup pruning. | BLOCKED — C5 | C5 tests | Pending | No |

**Done when:** C5's worker-invocation test proves the maintenance worker calls rollup pruning; this closes A2b.<br>
**Notes:** C5 can begin after A1 and A2a are complete. A2b is a dependent completion condition, not a prerequisite for C5.<br>
**Evidence:** Pending C5 implementation and tests.<br>
**Failures and blockers:** C5 code/tests have not started.<br>
**Decisions and deferred work:** C5 owns this integration; do not mark it complete based on the existing legacy-table prune.

### A3 — Stats zero reading

**Status:** `DONE — evidence recorded`<br>
**Blocked by:** A5 (complete 2026-10-11; dependency satisfied)<br>
**Started:** 2026-10-11  
**Completed:** 2026-10-11<br>
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [x] | Preserve every app row per date in `DeviceUsageSource`; cache app name/category per package per read. | DONE | A5 complete | Multi-app source test; each package resolved once | No |
| [x] | Remove `mergeTodayUsageFallback` and its `getUsageSummary` dependency; represent read failure as unavailable, never zero. | DONE | A5 complete | No fallback references; unavailable-card test | No |
| [x] | Log exception type/reason and return typed failure from `readSource`; add one debug line with the plan-required read counters. | DONE | A5 complete | Unavailable/error tests; required counters asserted | No |
| [x] | Test multi-app day values (Instagram 40m, WhatsApp 15m, Maps 3m, Settings 0.4s): summary, daily, hourly, and app rows agree. | DONE | A5 complete | `DeviceUsageSourceTest` | No |
| [x] | Test four stored days plus live today total (120+90+150+60 plus today = 478 as specified). | DONE | A5 complete | `DeviceUsageSourceTest` | No |
| [x] | Test same multi-app day from live and rollup sources gives identical per-app minutes. | DONE | A5 complete | `DeviceUsageSourceTest` | No |
| [x] | Test unavailable events produce unavailable state, not zero. | DONE | A5 complete | Source-state and card tests | No |
| [ ] | Owner checks S1 and confirms Today/Week totals against daily bars and device screen-time page. | OWNER-RUN PENDING | A5 complete; E1 device session | Pending owner report | Yes |

**Done when:** required source tests pass; owner check remains open until reported.  
**Notes:**  
- **A3 start (2026-10-11):** User authorized A3 after A5; its dependency was satisfied. At that checkpoint A2 was tracked as one in-progress batch with C5 integration deferred. Initial worktree: `main` ahead of `origin/main` by one commit; only the supplied attachments were untracked. Preserve those attachments and keep S1 owner-run for E1.
- **A3 continuation (2026-10-11):** User explicitly authorized resuming A3. The worktree was `main` aligned with `origin/main`; the supplied instruction attachment was preserved. Automated implementation and tests are now complete; S1 remains owner-run pending for E1.
**Evidence:**  
- `AnalyticsProcessor.kt` removes the Today fallback, preserves source failure type/reason, logs unavailable vs failed reads, and keeps device-usage health unavailable rather than substituting zero.
- `PhoneUsageSummary.kt` shows an explicit unavailable message when device usage was not loaded. `DeviceUsageSourceTest.kt` covers multi-app aggregation, per-read metadata caching, the 478-minute five-day total, live/rollup parity, unavailable events, error logging, and debug counters.
- `AnalyticsProcessorPhoneUsageTest.kt` verifies unavailable device usage does not become a zero-valued card.
- **Production verification:** `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:testProductionDebugUnitTest` passed; 272 tests, zero failures/errors/skips. The 5 new source tests and 4 phone-usage tests are present in XML reports. No APK was built.
- **Diff hygiene/config:** `git diff --check` passed; `.replit` unchanged. The user-supplied attachment remains untouched.
**Failures and blockers:**  
- S1 owner confirmation remains pending until the E1 device session; automated tests and code have no open blocker.
**Decisions and deferred work:**  
- Keep the S1 Today/Week comparison owner-run; A2 and its deferred C5 integration were not changed.

### A4 — Test baseline

**Status:** `DONE — evidence recorded`
**Blocked by:** A5 (complete 2026-10-11; dependency satisfied)<br>
**Started:** 2026-10-11<br>
**Completed:** 2026-10-11
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [x] | Find and remove tests that read `.kt` source text; replace with behavior tests for ledger, pipeline, and meter using fake clocks/sources. | DONE | A5 complete | Removed both source-text tests; ledger/pipeline behavior suites retained; injected-clock/source reader test passes in 267-test Production suite; alarm behavior test compiles in AndroidTest | No |
| [x] | Add fresh-database test and ensure migration tests compile and run where the project supports them. | DONE | A5 complete; A1 | Existing fresh-database and migration cases in `FocusFlowDatabaseMigrationTest.kt` compiled with Production AndroidTest; no connected device for instrumentation runtime, per plan compile-only path applies | No |
| [x] | Search test sources and verify no Kotlin-source `readText` checks remain; verify section 6 requirements are assigned to batch tests. | DONE | A5 complete | Final test-source search found only the JSON backup fixture read; the v9 ownership crosswalk below separates C6a automation from C6b owner logs and assigns tests to their feature batch. | No |

**Done when:** source-text tests are absent and section 6 test coverage is present.  
**Notes:**  
- **A4 start (2026-10-11):** User explicitly authorized A4. Initial `git status --short` was clean. The source-text readers are `AlarmPendingIntentLookupContractTest` and `AllowanceBehaviorCharacterizationTest`; `BackupSerializerTest` reads a committed JSON fixture, not Kotlin source, so leave it unchanged. The fresh database and migration cases already exist in `FocusFlowDatabaseMigrationTest`; build/compile evidence is still required. No app/test files changed yet.
- Replaced source inspection with behavior coverage: removed the two source-reading tests; added a real `AllowanceUsageReader` reconciliation case backed by fixed usage events and an injected completion clock, and an Android instrumentation test that cancels absent/present task alarms without creating pending intents. The reader now accepts a clock provider so this test does not use wall time. Existing ledger/pipeline tests retain the characterized boundary and legacy-JSON behavior.
- **Historical Section 6 ownership check (2026-10-11):** the then-current crosswalk used the unsplit A2, B1 and C6 labels. It is superseded by the v9 mapping below; later-batch cases remain assigned and open rather than being implemented ahead of their batches.
- **Current v9 Section 6 ownership crosswalk:** ledger/tail/time accounting → B4/B5; meter lifecycle/recovery/messaging → B1a; fallback resolver → B1b/B6; read policy → B3; signal filtering → B2; per-mode behavior → B5; config/resolvers → B6; Stats → A3; pipeline/session/inclusion → C1/C2; fresh DB, migrations and cutover → A1; maintenance worker core → A2a; rollup retention and worker invocation → C5/A2b; detector windows → C3; rollback and synthetic harness → C6a; real owner-log replay → C6b; shared freshness → D1. These groups are required at their owning task's completion gate.
- **Final verification (2026-10-11):** `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:testProductionDebugUnitTest :app:compileProductionDebugAndroidTestKotlin` succeeded. Production JVM reports show 57 suites, 267 tests, 0 failures, 0 errors, and 0 skipped; the new reader behavior test is present in XML. AndroidTest compilation succeeded, including the fresh DB/migration tests and alarm behavior test. The test-source audit found only `BackupSerializerTest` reading `backup/v1-export-golden.json`; no test reads `.kt` source text. `git diff --check` passed.
**Evidence:**
- `AllowanceUsageReader.kt`, `AllowanceUsageReaderTest.kt`, `TaskEndAlarmIdentityInstrumentedTest.kt`; deleted `AlarmPendingIntentLookupContractTest.kt` and `AllowanceBehaviorCharacterizationTest.kt`. Production JVM suite passed 267/267; Production AndroidTest Kotlin compilation passed.
- Existing `FocusFlowDatabaseMigrationTest.kt` includes fresh database and migration cases; Production AndroidTest Kotlin compilation succeeded. No connected Android device was available, so runtime instrumentation was not run; S2 remains pending.
- Final source-read audit and Section 6 crosswalk above; only the JSON fixture read remains, and every required test group is assigned to a batch test row.
**Failures and blockers:** None for A4. Android instrumentation runtime was unavailable because `adb devices` listed no devices; use the already planned S2 owner device run.
**Decisions and deferred work:** Reuse the existing fresh-database/migration instrumentation cases rather than duplicating them. Follow the plan’s compile-only fallback on Replit; retain runtime device verification for S2. AndroidTest compilation reported the existing deprecated `MigrationTestHelper` constructor and Java nullability warnings but completed successfully. No APK builds, Gradle edits, or GitHub Actions runs.

---

## Batch B — One meter everywhere

**Batch note:** Batch B is not a gate. Each task starts when its prerequisites in the dependency map are complete.

### B1a — Accessibility lifecycle, recovery, and messaging

**Status:** `NOT STARTED`  
**Blocked by:** A4 (complete; task is eligible)<br>
**Started:** —  
**Completed:** —  
**Owner-run:** S4 is owner-run and does not block automated gates

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Interrupt does not tear down meter; unbind/destroy do; rebind recreates and starts it. | NOT STARTED | A4 complete | Pending | No |
| [ ] | Recover checkpoint once at meter start, clear marker after recovery, prevent double credit; remove recovery from foreground service. | NOT STARTED | A4 complete | Pending | No |
| [ ] | Add the required Accessibility-off explanation to allowance screens and permission health card. | NOT STARTED | A4 complete | Pending | No |
| [ ] | JVM tests cover lifecycle and capped one-time recovery, including marker clearing and no double credit. | NOT STARTED | A4 complete | Pending | No |
| [ ] | Owner scenario S4: Accessibility off during a block; explanation and restoration behavior reported. | OWNER-RUN PENDING | E1 device session | Pending owner report | Yes |

**Done when:** lifecycle/recovery/messaging automated tests pass. S4 remains owner-run for E1 and is not a prerequisite for B/C automation, D, or E1.<br>
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### B1b — Fallback resolver integration

**Status:** `BLOCKED — B1a and B6 incomplete`<br>
**Blocked by:** B1a and B6<br>
**Started:** —<br>
**Completed:** —<br>
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Remove foreground-service allowance mirror/ledger reads; while Accessibility is off, allowance apps are blocked during active blocks with the required reason. | BLOCKED — B1a, B6 | B1a lifecycle and B6 shared resolver | Pending | No |
| [ ] | Test fallback block reason and prove fallback never reads the allowance ledger. | BLOCKED — B1a, B6 | B1a lifecycle and B6 resolver/test | Pending | No |

**Done when:** B6 resolver integration and both listed tests pass.<br>
**Notes:** No implementation progress is evidenced in the current checkout; B1a and B6 must both be complete first.<br>
**Evidence:** Pending B6 implementation.<br>
**Failures and blockers:** Wait for B6; do not duplicate its block-decision logic in the foreground service.<br>
**Decisions and deferred work:** Keep this separate from B1a lifecycle/recovery work.

### B2 — Foreground signal filter

**Status:** `NOT STARTED`  
**Blocked by:** A4 (complete; task is eligible)
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Put one `ForegroundSignalFilter` before the meter; Accessibility remains the only meter signal source. | NOT STARTED | A4 complete | Pending | No |
| [ ] | Ignore resolved input methods, system UI, app overlays, blocked screens, and accessibility overlays; only launchable apps/home cause a change. | NOT STARTED | A4 complete | Pending | No |
| [ ] | Bridge permission-controller/intent-resolver dialogs for up to three seconds; keep the bridge list evidence-based. | NOT STARTED | A4 complete | Pending | No |
| [ ] | Remove duplicate `currentTimed*` foreground state; count opens only on a filtered switch from another real app/home. | NOT STARTED | A4 complete | Pending | No |
| [ ] | Tests: keyboard, shade, permission dialog do not add opens; A→B→A adds one; no spontaneous count increase/decrease. | NOT STARTED | A4 complete | Pending | No |

**Done when:** filtered signal behavior and all listed tests pass.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### B3 — Read policy

**Status:** `NOT STARTED`  
**Blocked by:** B2 and B4<br>
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Implement the plan section 4.4 cadence; no read for non-allowance windows except one read when an allowance app leaves front; enforce 10-second minimum between non-lifecycle reads. | NOT STARTED | B2, B4 | Pending | No |
| [ ] | Keep scan cost independent of prior event count or demonstrate compliance with the plan's read budgets; reduce off main thread. | NOT STARTED | B2, B4 | Pending | No |
| [ ] | Replace timeout around blocking platform read with cancellation-aware behavior; slow reads cannot pile up. | NOT STARTED | B2, B4 | Pending | No |
| [ ] | Persist ledger only for meaningful changes; make bridge-return lookup linear; add hourly read/write and last-reason/outcome counters. | NOT STARTED | B2, B4 | Pending | No |
| [ ] | Tests: 200 non-target signals obey no-read/debounce policy; 10-minute foreground has expected ticks; read/reduction never run on main; writes only for changed targets. | NOT STARTED | B2, B4 | Pending | No |

**Done when:** cadence, dispatcher, cost, and persistence tests pass; counters are observable.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### B4 — Closed time versus open tail

**Status:** `NOT STARTED`  
**Blocked by:** A4 (complete; task is eligible)<br>
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Separate closed usage from open-tail projection; only closed time is a persistent floor; recompute tail on each read; cap tail at four hours. | NOT STARTED | A4 complete | Pending | No |
| [ ] | Decode legacy JSON by treating old `confirmedUsedMs` as closed time. | NOT STARTED | A4 complete | Pending | No |
| [ ] | Tests cover late end lowering projected value, tail growth/cap, midnight, interval, restart, and unavailable-data projection. | NOT STARTED | A4 complete | Pending | No |

**Done when:** closed-versus-tail model, compatibility, and required tests pass.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### B5 — Per-mode rules as tests

**Status:** `NOT STARTED`  
**Blocked by:** B2, B3, B4 and B6<br>
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Convert every rule in plan section 4.5 into behavior tests, implement time-budget and interval behavior first, then count. | NOT STARTED | B2, B3, B4, B6 | Pending | No |
| [ ] | Test count N−1, Nth open remains usable, and next open blocks. | NOT STARTED | B2, B3, B4, B6 | Pending | No |
| [ ] | Test exact time-budget expiry within one tick and reseeding after read. | NOT STARTED | B2, B3, B4, B6 | Pending | No |
| [ ] | Test interval start on first open, midnight crossing without reset, expiry, and restart. | NOT STARTED | B2, B3, B4, B6 | Pending | No |
| [ ] | Test unavailable/revoked data using PD-13 and config edits during a period. | NOT STARTED | B2, B3, B4, B6 | Pending | No |

**Done when:** all plan section 4.5 rows are tested and deviations are fixed.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### B6 — Config, block decision, and editor lock

**Status:** `NOT STARTED`  
**Blocked by:** A4 (complete; task is eligible)<br>
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Implement one typed `AllowanceConfig` parser; replace all listed independent parse sites; expose config flow and invalidate cache on writes. | NOT STARTED | A4 complete | Pending | No |
| [ ] | Implement one block-decision resolver for all rows of plan section 4.6 and use it in both paths. | NOT STARTED | A4 complete | Pending | No |
| [ ] | Apply empty Focus allow-list = no app allowed; allowances never widen Focus; preserve explicit defaults and decision log. | NOT STARTED | A4 complete | Pending | No |
| [ ] | Put loosening-edit lock predicate in repository/view model and apply to focus, standalone, and always-on blocks. | NOT STARTED | A4 complete | Pending | No |
| [ ] | Update Focus and allowance help wording. | NOT STARTED | A4 complete | Pending | No |
| [ ] | Tests: parser missing/wrong/unknown values; each section 4.6 row in both paths; lock predicate for each block type. | NOT STARTED | A4 complete | Pending | No |

**Done when:** config, resolver, UI wording, and lock tests meet plan requirements.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### B7 — Ledger hygiene

**Status:** `NOT STARTED`  
**Blocked by:** B3, B4 and B6<br>
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Search for callers and remove dead legacy ledger API only when no callers remain. | NOT STARTED | B3, B4, B6 | Pending | No |
| [ ] | Split ledger implementation below 300 lines; define active-session constants once; cache snapshots. | NOT STARTED | B3, B4, B6 | Pending | No |
| [ ] | UI observes ledger changes instead of one-second polling; document asynchronous-write crash window. | NOT STARTED | B3, B4, B6 | Pending | No |

**Done when:** dead API is verified unused before removal; file-size and behavior requirements are met.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

---

## Batch C — One definition of usage

**Batch note:** Batch C is not a gate. Each task starts when its prerequisites in the dependency map are complete. C5 owns A2b but does not wait for it; unrelated C tasks do not wait for C5.

### C1 — Session definition

**Status:** `NOT STARTED`  
**Blocked by:** A1 and A4 (complete; task is eligible)<br>
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Merge same-package sessions across the PD-10 three-second gap; document session definition and bump `CURRENT_PIPELINE_VERSION`. | NOT STARTED | A1, A4 complete | Pending | No |
| [ ] | Tests: A→A merges; A→B→A does not; screen-off separates; exact three-second boundary; launch counts unchanged. | NOT STARTED | A1, A4 complete | Pending | No |

**Done when:** session definition and pipeline version behavior are verified by tests.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### C2 — One inclusion policy

**Status:** `NOT STARTED`  
**Blocked by:** A3 and C1<br>
**Started:** —  
**Completed:** —  
**Owner-run:** No — manifest and package-query verification are repository work

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Apply one session-level inclusion policy to Stats, rollups, and detectors; dynamically resolve/cache home apps and keep static list only as fallback. | NOT STARTED | A3, C1 | Pending | No |
| [ ] | Put own package, system UI, home, launchable, and minimum-duration rules in this shared policy; do not apply it to allowance measurement. | NOT STARTED | A3, C1 | Pending | No |
| [ ] | Verify package-visibility needs against the checked-in manifest and actual package-manager queries; record the evidence and any real limitation. | NOT STARTED | A3, C1 | Manifest exists and declares `QUERY_ALL_PACKAGES`; C2 implementation/query evidence still pending | No |
| [ ] | Tests: hourly sum = daily sum = summary; dynamically identified unknown launcher excluded. | NOT STARTED | A3, C1 | Pending | No |

**Done when:** shared inclusion policy and tests pass; source-based package-query verification and any real limitation are recorded.<br>
**Notes:** The manifest and Gradle file exist in this checkout. Their presence alone does not establish whether the broad package-visibility permission is needed or whether queries behave as intended.<br>
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### C3 — Detector windows

**Status:** `NOT STARTED`  
**Blocked by:** A2a (complete; task is eligible)<br>
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Add a usage-specific end-date helper ending usage windows yesterday; restore task detectors' own date end unless owner decides otherwise. | NOT STARTED | A2a complete | Pending | No |
| [ ] | Tests prove usage detector windows exclude today and task detector windows retain intended current behavior. | NOT STARTED | A2a complete | Pending | No |

**Done when:** usage and task detector date behavior are independently verified.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### C4 — Side effects, clocks, and dead code

**Status:** `NOT STARTED`  
**Blocked by:** A1, A2a and A3 (complete; task is eligible)<br>
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Move cutover writes out of reads into maintenance path; make `getActiveFindings` read-only and perform repair once. | NOT STARTED | A1, A2a, A3 complete | Pending | No |
| [ ] | Inject clock into `RoomUsageHistoryStore`; route `StatsViewModel` through repository instead of `AppModule.database`. | NOT STARTED | A1, A2a, A3 complete | Pending | No |
| [ ] | Search callers before removing `getUsageSummary`, `PhoneUsageSummary.kt`, and unused DAO method(s). | NOT STARTED | A1, A2a, A3 complete | Pending | No |

**Done when:** reads have no repair side effects, clock/repository boundaries are in place, and deletions are proven safe by search/tests.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### C5 — Retention

**Status:** `NOT STARTED`  
**Blocked by:** A1 and A2a (complete; task is eligible)
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Add pruning for all three rollup tables (day, app-day, session) with the legacy 90-day retention horizon. | NOT STARTED | A1 complete | Pending | No |
| [ ] | Add tests proving every usage table is pruned at the boundary. | NOT STARTED | C5 implementation | Pending | No |
| [ ] | Wire rollup pruning through A2a's existing maintenance worker prune operation; close A2b with a worker-invocation test. | NOT STARTED | C5 implementation | Pending | No |

**Done when:** pruning covers every usage table, boundary tests pass, and the worker-invocation test closes A2b.<br>
**Notes:** C5 may start after A1 and A2a. It owns A2b; A2b is not a prerequisite for starting C5.<br>
**Evidence:** Repository audit (2026-10-12): `UsageRollupDao` has only per-date delete methods, and `UsageMaintenanceWorker` prunes legacy daily/session usage plus expired findings/questions but does not prune rollup tables. C5 implementation and the worker-invocation test remain unstarted; A2b remains blocked by C5.
**Failures and blockers:**  
**Decisions and deferred work:**  

### C6a — Cutover contract, rollback, and synthetic harness

**Status:** `NOT STARTED`  
**Blocked by:** A1, C1 and C4<br>
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Document and test that the gate proves completeness only; legacy values are not a correctness reference. | NOT STARTED | A1, C1, C4 | Pending | No |
| [ ] | Build and run the golden-log harness on synthetic fixtures through tracker, aggregator and `measure`; define fixture format for owner logs. | NOT STARTED | A1, C1, C4 | Pending | No |
| [ ] | Test rollback: null cutover serves legacy history and resumes legacy writer; keep legacy tracker unchanged. | NOT STARTED | A1, C1, C4 | Pending | No |
| [ ] | Preserve the refused cross-cutover detector seam and document the data-health explanation. | NOT STARTED | A1, C1, C4 | Pending | No |

**Done when:** completeness-only contract, synthetic-fixture harness, and rollback are evidenced before D.<br>
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### C6b — Owner golden-log replay

**Status:** `OWNER-RUN PENDING`<br>
**Blocked by:** E1 logs from both phones; C6a harness<br>
**Started:** —<br>
**Completed:** —<br>
**Owner-run:** Yes — owner supplies the logs; agent records replay results

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Owner exports event logs from both phones, including one non-Pixel, and the agent replays them through C6a's harness. | OWNER-RUN PENDING | E1/S11 logs and C6a | Pending owner report and replay | Yes |

**Done when:** replay results and provenance are recorded at E3, or the owner explicitly approves a documented deferral.<br>
**Notes:** This owner-dependent item does not block D or E1. Retain only the event data needed for replay and avoid including unrelated personal information.<br>
**Evidence:** Pending owner logs and harness replay.<br>
**Failures and blockers:** Await C6a and the E1 exports.<br>
**Decisions and deferred work:** Any deferral needs explicit owner approval at E3.

---

## Batch D — Screens and wording

**Batch note:** Batch D is not a gate. Each task starts when its prerequisites in the dependency map are complete. Owner S1/S2/S4 and C6b owner-log replay do not block D.

### D1 — Freshness everywhere

**Status:** `NOT STARTED`  
**Blocked by:** B1a, B3, B4 and C6a<br>
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Implement the shared freshness definition from plan section 4.3 and carry it in allowance UI models. | NOT STARTED | B1a, B3, B4, C6a | Pending | No |
| [ ] | Launcher, `ActiveScreen`, and `DailyAllowanceModal` all use shared freshness; modal displays or drops `usageByPackage` intentionally. | NOT STARTED | B1a, B3, B4, C6a | Pending | No |
| [ ] | Test reducer cases including idle-after-leaving exact, foreground tick freshness, stale read, and revoked permission unavailable. | NOT STARTED | B1a, B3, B4, C6a | Pending | No |

**Done when:** all allowance surfaces use one freshness state and reducer tests pass.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### D2 — Wording, permissions, and backup note

**Status:** `NOT STARTED`  
**Blocked by:** B1b, B6 and C6a<br>
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Update Usage Access definition and broken-without copy to cover foreground detection, allowance measurement, and history. | NOT STARTED | B1b, B6, C6a | Pending | No |
| [ ] | Update Stats, data-health, Focus/Standalone/Defense, How to Use, Changelog, and Privacy wording from the plan. | NOT STARTED | B1b, B6, C6a | Pending | No |
| [ ] | Add backup/restore notice that usage history is device-local and rebuilt; do not change `.focusflow` format. | NOT STARTED | B1b, B6, C6a | Pending | No |
| [ ] | Centralize `UsageAccess.isGranted`, including `MODE_DEFAULT`, `unsafeCheckOpNoThrow`, and failure behavior; `ActiveHeaderButton` errors are not treated as granted. | NOT STARTED | B1b, B6, C6a | Pending | No |
| [ ] | Verify permission-health and allowance editor lock text agrees with PD-2/PD-4/PD-5/PD-6/PD-14 behavior. | NOT STARTED | B1b, B6, C6a | Pending | No |

**Done when:** required screens, help, and permission paths use consistent wording and one permission definition.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### D3 — Unavailable state

**Status:** `NOT STARTED`  
**Blocked by:** A3, D2 and C6a<br>
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Stats card displays “unavailable, grant Usage Access or try again” for failed/denied reads, never `0m`. | NOT STARTED | A3, D2, C6a | Pending | No |
| [ ] | Test unavailable UI state and verify no failure path silently converts unknown to zero. | NOT STARTED | A3, D2, C6a | Pending | No |

**Done when:** failed/denied reads render unavailable state and tests pass.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

---

## Batch E — Verification and release

**Batch E gate:** D is complete by its automated done-when criteria. E1 is one initial full owner session; only failed scenarios may be retested, for at most two rounds.

### E1 — Owner device session

**Status:** `NOT STARTED`  
**Blocked by:** B1a, B1b, B2–B7, C1–C5, C6a and D1–D3; owner availability and both devices<br>
**Started:** —  
**Completed:** —  
**Owner-run:** Yes — device results are never agent-ticked

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Create `OWNER_DEVICE_TEST_GUIDE.md` with setup, exact steps, pass criteria, expected numbers, and report instructions for S1–S12; protect S2 by requiring it before S1 or any uninstall/data clear on the upgrade phone. | NOT STARTED | D | Pending | No |
| [ ] | Add copyable result template for scenario, phone, pass/partial/fail, observed behavior, and requested numbers. | NOT STARTED | D | Pending | No |
| [ ] | Add debug-only usage diagnostics and one-tap Copy report with only package names and the fields in plan section 7. | NOT STARTED | D | Pending | No |
| [ ] | Use one log tag and include the exact logcat commands from plan section 7 in the guide. | NOT STARTED | D | Pending | No |
| [ ] | Include default-battery then unrestricted-battery procedure; record one initial full session and at most two failed-scenario-only retest rounds. | NOT STARTED | D | Pending | No |
| [ ] | Confirm the exported Room schema for database version 9 is committed. | NOT STARTED | A1 | Pending | No |
| [ ] | Owner runs the initial S1–S12 session on Realme C3 (Android 10) and Samsung M31 (Android 12); attach reports; only failed scenarios may be retested, for up to two rounds. | OWNER-RUN PENDING | Guide/tooling complete; owner session | Pending owner reports | Yes |
| [ ] | Owner records the PD-11 Stats comparison. | OWNER-RUN PENDING | Owner session | Pending owner report | Yes |

**Done when:** agent guide/tooling/schema work is complete and owner reports are attached. Do not mark device outcomes complete before the owner reports them.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### E2 — Release gates

**Status:** `NOT STARTED`  
**Blocked by:** E1; owner release decision  
**Started:** —  
**Completed:** —  
**Owner-run:** Release monitoring is owner-run

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Record internal-testing, closed-testing, and staged-rollout gates and halt-rollout rollback procedure. | NOT STARTED | E1 | Pending | No |
| [ ] | Resolve whether Google Play distribution applies; if it does, schedule target-API work before any update. Do not change target API as Phase 5 work. | NOT STARTED | Owner answer | Pending | No |
| [ ] | Owner performs rollout/crash/ANR monitoring if a release is authorized; record owner evidence without claiming an unrun release. | OWNER-RUN PENDING | Release authorization | Pending owner report | Yes |

**Done when:** release gates are documented and applicable target-API work is scheduled; any rollout monitoring remains owner-run.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### E3 — Close-out

**Status:** `NOT STARTED`  
**Blocked by:** E2 and C6b<br>
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Reconcile every batch/item status with current source, exact test output, and owner reports; leave incomplete items open with reasons. | NOT STARTED | E2, C6b | Pending | No |
| [ ] | Verify all P0/P1 findings have evidence and all P2 findings are done or explicitly deferred with reasons. | NOT STARTED | E2, C6b | Pending | No |
| [ ] | Verify review gates G1–G9 and required release gates; record each pass/failure/unverified result. | NOT STARTED | E2, C6b | Pending | No |
| [ ] | Replay C6a's harness on owner logs from both phones, including one non-Pixel; otherwise record explicit owner-approved deferral. | NOT STARTED | C6a; E1 logs; owner decision | Pending | No |
| [ ] | Record C2's repository-based manifest/package-query result and resolve or obtain owner-approved deferral for any actual limitation. | NOT STARTED | C2 | Pending | No |
| [ ] | After any post-E1 code change, rerun affected automated tests and owner scenarios; do not reuse stale pass evidence. | NOT STARTED | Any post-E1 change | Pending | No |
| [ ] | Reconcile every owner-run item as passed or explicitly deferred with owner approval; preserve the v8 plan/tracker and make v9 active. | NOT STARTED | E2, C6b; owner decisions | Pending | No |

**Done when:** plan section 10 is met; any permitted owner-approved deferral is fully documented; no unsupported completion claims remain.
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

---

## Summary

| Task | Status | Needs / blocker | Owner-run status |
|---|---|---|---|
| A5 — Tracker truth | DONE — evidence recorded | — | Not owner-run |
| A1 — Database and cutover | DONE — evidence recorded | A5 complete | S2 pending later |
| A2a — Maintenance job core | DONE — evidence recorded | A5 complete | Not owner-run |
| A2b — Rollup-prune integration | BLOCKED — C5 | C5 completion | Not owner-run |
| A3 — Stats zero reading | DONE — evidence recorded | A5 complete | S1 pending later |
| A4 — Test baseline | DONE — evidence recorded | A5 complete | Not owner-run |
| B1a — Lifecycle, recovery, messaging | NOT STARTED | A4 complete; eligible now | S4 pending; nonblocking for automation/D/E1 |
| B1b — Fallback resolver | BLOCKED — B1a, B6 | B1a and B6 | Not owner-run |
| B2 — Foreground signal filter | NOT STARTED | A4 complete; eligible now | Not owner-run |
| B3 — Read policy | NOT STARTED | B2, B4 | Not owner-run |
| B4 — Closed time versus open tail | NOT STARTED | A4 complete; eligible now | Not owner-run |
| B5 — Per-mode rules | NOT STARTED | B2, B3, B4, B6 | Not owner-run |
| B6 — Config and block decision | NOT STARTED | A4 complete; eligible now | Not owner-run |
| B7 — Ledger hygiene | NOT STARTED | B3, B4, B6 | Not owner-run |
| C1 — Session definition | NOT STARTED | A1, A4 complete; eligible now | Not owner-run |
| C2 — One inclusion policy | NOT STARTED | A3, C1 | Not owner-run |
| C3 — Detector windows | NOT STARTED | A2a complete; eligible now | Not owner-run |
| C4 — Side effects, clocks, dead code | NOT STARTED | A1, A2a, A3 complete; eligible now | Not owner-run |
| C5 — Retention | NOT STARTED | A1, A2a complete; eligible now; closes A2b | Not owner-run |
| C6a — Contract, rollback, synthetic harness | NOT STARTED | A1, C1, C4 | Not owner-run; required before D |
| C6b — Owner golden-log replay | OWNER-RUN PENDING | E1 logs, C6a; resolve or approve deferral at E3 | Owner logs pending; nonblocking D/E1 |
| D1 — Freshness everywhere | NOT STARTED | B1a, B3, B4, C6a | Not owner-run |
| D2 — Wording, permissions, backup note | NOT STARTED | B1b, B6, C6a | Not owner-run |
| D3 — Unavailable state | NOT STARTED | A3, D2, C6a | Not owner-run |
| E1 — Owner device session | NOT STARTED | B/C/D prerequisites in dependency map; owner/device availability; protect S2 before fresh install | OWNER-RUN PENDING; one full session + up to two failed-only retest rounds |
| E2 — Release gates | NOT STARTED | E1; owner release decision | Release monitoring OWNER-RUN PENDING |
| E3 — Close-out | NOT STARTED | E2, C6b | Not owner-run |

**Can start now:** B1a, B2, B4, B6, C1, C3, C4 and C5.<br>
**Open task blockers:** A2b waits for C5; B1b waits for B1a and B6; all other unfinished-task blockers are listed in the dependency map.

**Final status:**  
**Open blockers:**  
**Deferred work:**  
**Final evidence summary:**  
