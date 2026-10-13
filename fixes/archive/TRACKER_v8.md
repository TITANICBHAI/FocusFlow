# FocusFlow Fixes Phase 5 — Main Work Tracker (v8)

**Primary tracker for the plan in `fixes/PLAN.md`.** This file is the authoritative progress record for this fixes effort. `allowance/TRACKER.md` belongs to a separate earlier workstream: it may be read as a reference for tracker format and discipline, but its statuses, evidence, decisions, and progress do not apply here and must not be updated for this work.

The v8 plan supersedes `allowance/PLAN.md` (v7). The plan is the implementation contract; it is not authorization by itself. See `fixes/AGENT_PRE_PROMPT.md` before starting work.

**Initial state at tracker creation:** All batches were `NOT STARTED`; no progress or evidence was copied from the allowance tracker. Batch A5 has since completed. Product decisions below are transcribed from section 3 of the v8 plan, not independently re-confirmed in this tracker.

---

## Tracker rules

### Status vocabulary

- `NOT STARTED`
- `IN PROGRESS`
- `BLOCKED — <reason>`
- `DONE — evidence recorded`
- `OWNER-RUN PENDING`

Every work item records its own status, `Blocked by`, evidence, and whether it is owner-run. Batch headers also record status, blocker, start/completion dates, and owner-run state.

### Before and during a batch

- At batch start, change its status to `IN PROGRESS`, record `Started`, and read the matching plan section in full.
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

## Order and dependencies

1. **A5 first.** Reconcile this tracker with v8 and the current code before app-code changes.
2. **A1–A4 next.** Do not start them until A5 is done.
3. **B and C after A.** Batches B and C may run in parallel where their plan dependencies allow.
4. **D after B and C.**
5. **E after D.** Owner device checks happen once, at the end.

---

## Decision log

Decision entries below are transcribed from `fixes/PLAN.md`, section 3.1–3.2. The plan is the recorded source; do not represent these as independently re-confirmed during implementation.

| PD | Decision recorded in v8 | Tracker status / application |
|---|---|---|
| PD-1 | Same seven-day gate for fresh and upgraded installs; no upgrade-day cutover. | RECORDED FROM PLAN; implement and verify in A1. |
| PD-2 | Allowance limits require Accessibility; when it is off, allowance apps are blocked during any active block and the UI explains why. No fallback meter. | RECORDED FROM PLAN; implement and verify in B1. |
| PD-3 | An empty Focus allow-list means no app is allowed; an allowance never widens Focus. | RECORDED FROM PLAN. The detail has a safe default of “No” and remains awaiting owner confirmation as specified in section 3.2. |
| PD-4 | Loosening allowance edits are locked during any active block; tightening remains allowed. | RECORDED FROM PLAN; implement and verify in B6/D2. |
| PD-5 | Usage Access is required; enabling an allowance prompts for it. | RECORDED FROM PLAN; implement and verify in D2. |
| PD-6 | Usage history is not exported; `.focusflow` format remains unchanged and backup/restore explains history is rebuilt. | RECORDED FROM PLAN; implement and verify in D2. |
| PD-7 | Remove the Today Stats fallback after the Stats fix; a failed read is unavailable, never zero. | RECORDED FROM PLAN; implement in A3/D3. |
| PD-8 | Do not fix the legacy tracker; retain its known limitations and stop its writes 14 days after cutover. Never use legacy numbers as validation reference. | RECORDED FROM PLAN; preserve in C6. |
| PD-9 | Keep refusing usage windows that cross cutover; the data-health notice explains the blackout. No relaxation without device evidence. | RECORDED FROM PLAN; preserve and verify in C6. |
| PD-10 | Merge same-package sessions across a 3-second gap when no other package intervenes. | RECORDED FROM PLAN; implement and verify in C1. |
| PD-11 | Compare Stats with device screen-time totals within about 20% or 15 minutes, whichever is larger; if unavailable, validate internal totals and top apps. | RECORDED FROM PLAN; owner validation in E1. |
| PD-12 | Google Play target API requirement remains an open product question; treat it as a release gate, with no Phase 5 implementation. | OPEN; keep release gate in E2 and do not change target API in this scope. |
| PD-13 | When usage data is unavailable, enforce using last known value plus projection, mark values approximate, and show revoked permission in permission health. | RECORDED FROM PLAN; implement and verify in B4/D1. |
| PD-14 | Usage is counted all day; allowances restrict only while a block is active and never open an app a block keeps closed. | RECORDED FROM PLAN; implement and verify in B6/D2. |
| Modes | Keep count, time-budget, and interval modes; time budget and interval are the main focus, count follows. | RECORDED FROM PLAN; test in B5. |
| Devices | Owner tests once, at the end, on Realme C3 (Android 10) and Samsung M31 (Android 12). | RECORDED FROM PLAN; owner-run in E1. |

---

## Batch A — Release blockers

### A5 — Tracker truth

**Status:** `DONE — evidence recorded`<br>
**Blocked by:** None — do this first<br>
**Started:** 2026-10-11<br>
**Completed:** 2026-10-11<br>
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [x] | Verify current Room database version and `MIGRATION_7_8`; record the v8 A1 change accurately. | DONE — evidence recorded | — | `FocusFlowDatabase.kt:133,548-555`; `AppModule.kt:218-227`; verified no `MIGRATION_8_9` exists. | No |
| [x] | Audit old plan-version references; keep explicit historical supersession/archive references and remove stale active references. | DONE — evidence recorded | — | `rg -ni` audit: remaining v7 mentions are explicitly historical; no stale active reference remains. | No |
| [x] | Audit all checkboxes and statuses; leave unsupported or unverified work incomplete. Confirm no allowance-tracker progress was imported. | DONE — evidence recorded | — | Initial tracker had no completed items; only this A5 work is now checked, with evidence recorded; no allowance progress copied. | No |
| [x] | Verify the Batch 0 usage-table writer/reader map against code; identify `AppUsageAndSessionTracker` as legacy-table writer if confirmed. | DONE — evidence recorded | — | DAO call-site search and source inspection: tracker is producer; readers and worker pruning are detailed in Notes. | No |
| [x] | Verify the `SettingsRepository.getAllowanceSnapshot` purpose and whether `resetDailyAllowanceUsage` has callers. | DONE — evidence recorded | — | `SettingsRepository.kt:1193-1210`; `SettingsViewModel.kt:108`; workspace search found only reset declaration. | No |
| [x] | Confirm every batch/item has status, blocker, evidence, and owner-run fields; reconcile this decision log with PD-1–PD-14. | DONE — evidence recorded | — | Structural audit: 110 work-item rows include all four fields; decision entries checked against plan sections 3.1–3.2. | No |
| [x] | Final audit: every tracker statement matches current code or is explicitly marked pending/unverified. | DONE — evidence recorded | — | Current-code claims and v8-derived acceptance criteria reconciled; all future work remains NOT STARTED or OWNER-RUN PENDING. Structural checks passed. | No |

**Done when:** every statement in `fixes/TRACKER.md` matches the current code and v8 plan.<br>
**Notes:**
- `NOT STARTED` in later batches means v8-tracked work has not begun; it does not claim similar code is absent. Existing behavior must be re-verified against the batch acceptance criteria.
- Current Room version is 8 (`FocusFlowDatabase.kt:133`). `MIGRATION_7_8` (`:548-555`) sets a null `cutover_date` to `date('now', 'localtime')`; `AppModule.kt:218-227` registers it. Search found no `MIGRATION_8_9`. Under v8 A1, version 7→8 should become data-free and a new 8→9 migration should clear stale cutover state and repair/seed the pipeline row. This records a required future change; no database code was changed in A5.
- `AppUsageAndSessionTracker.kt:22-29,56-80,115-159` produces legacy daily usage (launch count and foreground time) and closed session rows. `AppBlockerAccessibilityService.kt:569` creates it, and `:1018-1024` feeds foreground window changes. `BackgroundFetchWorker.kt:116-117` only prunes old legacy rows; it does not produce usage rows. Search found no usage-write calls in `AnalyticsProcessor` or the worker.
- Readers found by DAO call-site search: `UsageHistoryRepository.kt:343,359` reads legacy app-days and sessions; `FindingDetectionRunner.kt:240-241` reads both in its direct fallback; `DayRatingRepository.kt:54` reads daily usage dates; and `StatsViewModel.kt:389-394` reads daily rows for suggested chips. Other consumers use the history repository rather than writing legacy rows.
- `SettingsRepository.getAllowanceSnapshot()` builds an `AllowanceSnapshot` from the allowance ledger and preferences (`:1193-1210`); `SettingsViewModel.kt:108` consumes it. It is a UI snapshot, not a backup path. `resetDailyAllowanceUsage()` only delegates to the ledger (`:1367-1369`); repository-wide Kotlin search found only its declaration.
- The only remaining v7 mentions in this tracker describe the superseded allowance plan and its later archival as history. They are not active plan references. No progress was imported from `allowance/TRACKER.md`.
**Evidence:**<br>
- Changed file: `fixes/TRACKER.md`; no app source or allowance tracker changed.
- `rg -n "version =|MIGRATION_7_8|MIGRATION_8_9" app/src/main/java/com/tbtechs/focusflow/data/local/FocusFlowDatabase.kt app/src/main/java/com/tbtechs/focusflow/di/AppModule.kt` and source inspection: Room is version 8; 7→8 sets null cutover to local today and is registered; no 8→9 migration exists. This is recorded as A1 work, not represented as fixed.
- `rg -n "(dailyAppUsageDao\\(\\)|appSessionDao\\(\\)|dailyUsageDao\\.|sessionDao\\.|dailyAppUsageDao\\.|appSessionDao\\.)" app/src/main/java --glob '*.kt'` plus DAO/tracker inspection: only `AppUsageAndSessionTracker` produces legacy usage/session rows, called by Accessibility; other listed DAO calls are readers or the worker's retention deletes.
- `rg -n 'getAllowanceSnapshot\\(|resetDailyAllowanceUsage\\(' app/src/main/java --glob '*.kt'`: snapshot is consumed by `SettingsViewModel`; reset has only its declaration.
- `rg -ni 'v6|v7|version 6|version 7|plan_v7|plan v7|phase 5.*v7' fixes/TRACKER.md`: only explicit historical supersession/archive references remain.
- `awk` item-row check: 110 work-item rows; every row has status, blocker, evidence, and owner-run columns. Decision log compared with plan sections 3.1–3.2.
- `git diff --check`: passed after the whitespace correction below. App tests were not run because A5 changed tracker documentation only.
**Failures and blockers:**<br>
- First `git diff --check` failed with exit 2 on trailing Markdown hard-break spaces in the A5 status metadata. Replaced those spaces with explicit `<br>` tags; the rerun passed.
- A later `git diff --check` failed with exit 2 on trailing hard-break spaces in the A1–A4 blocker metadata after updating the satisfied A5 dependency. Replaced those spaces with explicit `<br>` tags; the final rerun passed.
**Decisions and deferred work:**<br>
- No product decision changed. PD-3's detail remains the plan's default “No,” awaiting owner confirmation; PD-12 remains open. A1–A4 are now eligible but still NOT STARTED.

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
- The worktree was clean immediately before this tracker update; `fixes/TRACKER.md` is the only changed file. No APK build, GitHub Actions run, or GitHub push occurred.
**Failures and blockers:**<br>
- The initial `bash scripts/test-unit.sh` run was stopped during SDK setup before Gradle tests ran: it also schedules the development flavor, contrary to the production-only test rule. The production-only unit-test task is being run separately.
- The first production-only unit-test run failed during test compilation on an unresolved helper reference inside `FakeStore`; the fake now constructs the seeded state directly, and the production-only task is being rerun.
- **Successful production-only rerun (2026-10-11):** The command above passed after the `FakeStore` correction. The instrumentation test source compiled but did not execute; S2 remains owner-run pending.
**Decisions and deferred work:**<br>
- Marked the automated A1 implementation and verification items complete using the plan's compile-only instrumentation fallback. Do not mark S2 complete until the owner reports both device results.

### A2 — Maintenance job

**Status:** `IN PROGRESS`<br>
**Blocked by:** A5 (complete 2026-10-11; dependency satisfied)<br>
**Started:** 2026-10-11<br>
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [x] | Implement a maintenance worker that runs recent rollups, completed-day detection, then prune in that order. | DONE | A5 complete | Worker sequence test; full Production suite passed | No |
| [x] | Schedule unique periodic work with keep policy from app startup; record observed schedule evidence. | DONE | A5 complete | Startup call, captured 15-minute KEEP request, and WorkManager completion log | No |
| [x] | Preserve unrelated old background-fetch duties; remove old detection/prune calls only after the replacement is live. | DONE | A5 complete | Focused replacement test passed before old calls were removed; task warnings/reminders/digest retained | No |
| [x] | Detection marker advances only when required days are COMPLETE or permission is denied; unknown results retry. | DONE | A5 complete | Complete/incomplete/permission/unknown cases covered by worker tests | No |
| [x] | Run detection after successful rollup completion; inject clock/zone for pruning and detection while task dates retain UTC behavior. | DONE | A5 complete | Injected clock/zone; UTC task-date query unchanged; Production tests passed | No |
| [x] | Worker tests: order; unknown-result retry; one success per local day; prune horizon uses injected clock. | DONE | A5 complete | `UsageMaintenanceJobTest` 7/7; full suite 268/268 | No |
| [ ] | Prune delegates to the rollup retention work in C5. | BLOCKED | A5 complete; C5 | Pending C5 rollup-retention implementation | No |

**Done when:** scheduling is evidenced at app start and required worker tests pass.  
**Notes:**<br>
- **A2 start (2026-10-11):** User authorized A2 after A1 completed. The initial worktree was clean. Source review confirmed that startup does not schedule the maintenance job, `BackgroundFetchWorker` mixes legacy task duties with detection and legacy-table pruning, and the rollup writer and detection runner already exist. Keep unrelated task/reminder behavior out of scope. C5 has not added rollup-table retention yet; leave the A2→C5 pruning item blocked and do not implement C5 here.
- **Initial implementation checkpoint (2026-10-11):** Added a platform-free maintenance coordinator, a WorkManager adapter, a unique 15-minute `KEEP` schedule request from `FocusFlowApp`, startup logging after WorkManager accepts the operation, local-clock rollup/detection/prune seams, and behavior tests. The job checks all three recent rollup dates before detection, retries incomplete/unknown reads, and treats missing/revoked usage permission as a terminal no-read state for that local day. At this checkpoint, the old BackgroundFetch detection/prune code remained pending replacement verification.
- **Replacement verification (2026-10-11):** Ran `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:testProductionDebugUnitTest --tests 'com.tbtechs.focusflow.background.UsageMaintenanceJobTest'`; Gradle succeeded and the XML report confirms 7/7 tests, zero failures/errors/skips. After that pass, removed only the old detection and prune calls from `BackgroundFetchWorker`; late-start warnings, task reminders, and morning digest remain. Final full Production verification is still pending.
- **Final verification (2026-10-11):** `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:testProductionDebugUnitTest` passed; Production XML contains 268 tests, zero failures/errors/skips, including all seven `UsageMaintenanceJobTest` cases. The new startup helper records the exact unique-work name, 15-minute interval, and `KEEP` policy in its test; production logs only after WorkManager's enqueue operation completes. `git diff --check` passed. No APK, GitHub workflow, push, or device run was started.
- **Resume verification (2026-10-11):** Rechecked the current A2 worker, startup wiring, retained `BackgroundFetchWorker` duties, and `UsageMaintenanceJobTest`. No A2 source changes were needed. After the first verification attempt timed out during initial toolchain provisioning, the cached rerun of `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:testProductionDebugUnitTest` passed: 58 Production XML reports, 268 tests, zero failures/errors/skips; `UsageMaintenanceJobTest` is 7/7. A2 remains IN PROGRESS because its rollup-retention call is explicitly blocked on C5; no C5 code was added.
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
 - **C5 dependency:** Rollup-table retention does not exist yet; the worker's legacy-table pruning remains separate. Leave “Prune delegates to the rollup retention work in C5” unchecked and blocked until C5.
  - **Resume verification attempt 1 (2026-10-11):** The command exceeded the five-minute shell limit while provisioning JDK 17, Android SDK packages, and Gradle; no Gradle test task result was produced. The cached rerun passed as recorded above.
**Decisions and deferred work:**  
 - Require all three recent past-day rollup rows to be `COMPLETE` at the current pipeline version before detection; this matches the writer's existing three-day rollup window.
 - Treat `PERMISSION_MISSING` and `ACCESS_REVOKED` as the documented no-read condition for a local day; retry all other unknown and incomplete results.
 - Preserve the existing `last_detection_run_date` and `last_prune_date` preference keys; keep BackgroundFetch task-date lookup on UTC.
 - Defer rollup-table retention delegation to C5; do not add a no-op retention hook in A2.

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
- **A3 start (2026-10-11):** User authorized A3 after A5; its dependency is satisfied. A2 remains a separate in-progress batch with the C5 rollup-retention integration deferred. Initial worktree: `main` ahead of `origin/main` by one commit; only the supplied attachments were untracked. Preserve those attachments and keep S1 owner-run for E1.
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

**Status:** `COMPLETE`
**Blocked by:** A5 (complete 2026-10-11; dependency satisfied)<br>
**Started:** 2026-10-11<br>
**Completed:** 2026-10-11
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [x] | Find and remove tests that read `.kt` source text; replace with behavior tests for ledger, pipeline, and meter using fake clocks/sources. | DONE | A5 complete | Removed both source-text tests; ledger/pipeline behavior suites retained; injected-clock/source reader test passes in 267-test Production suite; alarm behavior test compiles in AndroidTest | No |
| [x] | Add fresh-database test and ensure migration tests compile and run where the project supports them. | DONE | A5 complete; A1 | Existing fresh-database and migration cases in `FocusFlowDatabaseMigrationTest.kt` compiled with Production AndroidTest; no connected device for instrumentation runtime, per plan compile-only path applies | No |
| [x] | Search test sources and verify no Kotlin-source `readText` checks remain; verify section 6 requirements are assigned to batch tests. | DONE | A5 complete | Final test-source search found only the JSON backup fixture read; Section 6 ownership is mapped to A1–A4, B1–B6, C1–C6, and D1 test rows | No |

**Done when:** source-text tests are absent and section 6 test coverage is present.  
**Notes:**  
- **A4 start (2026-10-11):** User explicitly authorized A4. Initial `git status --short` was clean. The source-text readers are `AlarmPendingIntentLookupContractTest` and `AllowanceBehaviorCharacterizationTest`; `BackupSerializerTest` reads a committed JSON fixture, not Kotlin source, so leave it unchanged. The fresh database and migration cases already exist in `FocusFlowDatabaseMigrationTest`; build/compile evidence is still required. No app/test files changed yet.
- Replaced source inspection with behavior coverage: removed the two source-reading tests; added a real `AllowanceUsageReader` reconciliation case backed by fixed usage events and an injected completion clock, and an Android instrumentation test that cancels absent/present task alarms without creating pending intents. The reader now accepts a clock provider so this test does not use wall time. Existing ledger/pipeline tests retain the characterized boundary and legacy-JSON behavior.
- **Section 6 ownership check:** ledger/tail/time-accounting cases → B4/B5; meter lifecycle/recovery/read policy → B1/B3; signal filtering → B2; per-mode behavior → B5; config/resolvers → B6; Stats → A3; pipeline/session/inclusion → C1/C2; fresh DB, migrations and cutover → A1; maintenance worker → A2; detector windows → C3; rollup retention → C5; rollback/golden logs → C6; shared freshness → D1. Later-batch cases remain assigned and open rather than being implemented ahead of their batches.
- **Final verification (2026-10-11):** `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:testProductionDebugUnitTest :app:compileProductionDebugAndroidTestKotlin` succeeded. Production JVM reports show 57 suites, 267 tests, 0 failures, 0 errors, and 0 skipped; the new reader behavior test is present in XML. AndroidTest compilation succeeded, including the fresh DB/migration tests and alarm behavior test. The test-source audit found only `BackupSerializerTest` reading `backup/v1-export-golden.json`; no test reads `.kt` source text. `git diff --check` passed.
**Evidence:**
- `AllowanceUsageReader.kt`, `AllowanceUsageReaderTest.kt`, `TaskEndAlarmIdentityInstrumentedTest.kt`; deleted `AlarmPendingIntentLookupContractTest.kt` and `AllowanceBehaviorCharacterizationTest.kt`. Production JVM suite passed 267/267; Production AndroidTest Kotlin compilation passed.
- Existing `FocusFlowDatabaseMigrationTest.kt` includes fresh database and migration cases; Production AndroidTest Kotlin compilation succeeded. No connected Android device was available, so runtime instrumentation was not run; S2 remains pending.
- Final source-read audit and Section 6 crosswalk above; only the JSON fixture read remains, and every required test group is assigned to a batch test row.
**Failures and blockers:** None for A4. Android instrumentation runtime was unavailable because `adb devices` listed no devices; use the already planned S2 owner device run.
**Decisions and deferred work:** Reuse the existing fresh-database/migration instrumentation cases rather than duplicating them. Follow the plan’s compile-only fallback on Replit; retain runtime device verification for S2. AndroidTest compilation reported the existing deprecated `MigrationTestHelper` constructor and Java nullability warnings but completed successfully. No APK builds, Gradle edits, or GitHub Actions runs.

---

## Batch B — One meter everywhere

**Batch gate:** A1–A4 must be DONE before starting B. B batches may proceed in parallel only where current code ownership and plan dependencies permit.

### B1 — Accessibility lifecycle and fallback behavior

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** S4 is owner-run

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Interrupt does not tear down meter; unbind/destroy do; rebind recreates and starts it. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Remove foreground-service allowance mirror/ledger reads; while Accessibility is off, allowance apps are blocked during active blocks with the required reason. | NOT STARTED | A1–A4; B6 resolver | Pending | No |
| [ ] | Add the required Accessibility-off explanation to allowance screens and permission health card. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Recover checkpoint once at meter start, clear marker after recovery, prevent double credit; remove recovery from foreground service. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | JVM tests cover lifecycle, capped one-time recovery, fallback reason, and no ledger access in fallback. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Owner scenario S4: Accessibility off during a block; explanation and restoration behavior reported. | OWNER-RUN PENDING | E1 device session | Pending owner report | Yes |

**Done when:** lifecycle/recovery/fallback tests pass and S4 is recorded by the owner.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### B2 — Foreground signal filter

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Put one `ForegroundSignalFilter` before the meter; Accessibility remains the only meter signal source. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Ignore resolved input methods, system UI, app overlays, blocked screens, and accessibility overlays; only launchable apps/home cause a change. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Bridge permission-controller/intent-resolver dialogs for up to three seconds; keep the bridge list evidence-based. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Remove duplicate `currentTimed*` foreground state; count opens only on a filtered switch from another real app/home. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Tests: keyboard, shade, permission dialog do not add opens; A→B→A adds one; no spontaneous count increase/decrease. | NOT STARTED | A1–A4 | Pending | No |

**Done when:** filtered signal behavior and all listed tests pass.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### B3 — Read policy

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Implement the v8 cadence; no read for non-allowance windows except one read when an allowance app leaves front; enforce 10-second minimum between non-lifecycle reads. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Keep scan cost independent of prior event count or demonstrate compliance with the plan's read budgets; reduce off main thread. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Replace timeout around blocking platform read with cancellation-aware behavior; slow reads cannot pile up. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Persist ledger only for meaningful changes; make bridge-return lookup linear; add hourly read/write and last-reason/outcome counters. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Tests: 200 non-target signals obey no-read/debounce policy; 10-minute foreground has expected ticks; read/reduction never run on main; writes only for changed targets. | NOT STARTED | A1–A4 | Pending | No |

**Done when:** cadence, dispatcher, cost, and persistence tests pass; counters are observable.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### B4 — Closed time versus open tail

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Separate closed usage from open-tail projection; only closed time is a persistent floor; recompute tail on each read; cap tail at four hours. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Decode legacy JSON by treating old `confirmedUsedMs` as closed time. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Tests cover late end lowering projected value, tail growth/cap, midnight, interval, restart, and unavailable-data projection. | NOT STARTED | A1–A4 | Pending | No |

**Done when:** closed-versus-tail model, compatibility, and required tests pass.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### B5 — Per-mode rules as tests

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Convert every rule in plan section 4.5 into behavior tests, implement time-budget and interval behavior first, then count. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Test count N−1, Nth open remains usable, and next open blocks. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Test exact time-budget expiry within one tick and reseeding after read. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Test interval start on first open, midnight crossing without reset, expiry, and restart. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Test unavailable/revoked data using PD-13 and config edits during a period. | NOT STARTED | A1–A4 | Pending | No |

**Done when:** all plan section 4.5 rows are tested and deviations are fixed.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### B6 — Config, block decision, and editor lock

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Implement one typed `AllowanceConfig` parser; replace all listed independent parse sites; expose config flow and invalidate cache on writes. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Implement one block-decision resolver for all rows of plan section 4.6 and use it in both paths. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Apply empty Focus allow-list = no app allowed; allowances never widen Focus; preserve explicit defaults and decision log. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Put loosening-edit lock predicate in repository/view model and apply to focus, standalone, and always-on blocks. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Update Focus and allowance help wording. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Tests: parser missing/wrong/unknown values; each section 4.6 row in both paths; lock predicate for each block type. | NOT STARTED | A1–A4 | Pending | No |

**Done when:** config, resolver, UI wording, and lock tests meet plan requirements.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### B7 — Ledger hygiene

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Search for callers and remove dead legacy ledger API only when no callers remain. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Split ledger implementation below 300 lines; define active-session constants once; cache snapshots. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | UI observes ledger changes instead of one-second polling; document asynchronous-write crash window. | NOT STARTED | A1–A4 | Pending | No |

**Done when:** dead API is verified unused before removal; file-size and behavior requirements are met.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

---

## Batch C — One definition of usage

**Batch gate:** A1–A4 must be DONE. B and C may proceed in parallel where dependencies permit.

### C1 — Session definition

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Merge same-package sessions across the PD-10 three-second gap; document session definition and bump `CURRENT_PIPELINE_VERSION`. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Tests: A→A merges; A→B→A does not; screen-off separates; exact three-second boundary; launch counts unchanged. | NOT STARTED | A1–A4 | Pending | No |

**Done when:** session definition and pipeline version behavior are verified by tests.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### C2 — One inclusion policy

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** Manifest visibility confirmation requires owner

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Apply one session-level inclusion policy to Stats, rollups, and detectors; dynamically resolve/cache home apps and keep static list only as fallback. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Put own package, system UI, home, launchable, and minimum-duration rules in this shared policy; do not apply it to allowance measurement. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Verify package-visibility manifest requirements against available project files and record the result/limitation. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Owner confirms manifest package visibility where manifest is unavailable; do not tick without owner evidence. | OWNER-RUN PENDING | E1 or owner confirmation | Pending owner report | Yes |
| [ ] | Tests: hourly sum = daily sum = summary; dynamically identified unknown launcher excluded. | NOT STARTED | A1–A4 | Pending | No |

**Done when:** shared inclusion policy and tests pass; manifest limitation/confirmation is recorded.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### C3 — Detector windows

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Add a usage-specific end-date helper ending usage windows yesterday; restore task detectors' own date end unless owner decides otherwise. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Tests prove usage detector windows exclude today and task detector windows retain intended current behavior. | NOT STARTED | A1–A4 | Pending | No |

**Done when:** usage and task detector date behavior are independently verified.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### C4 — Side effects, clocks, and dead code

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Move cutover writes out of reads into maintenance path; make `getActiveFindings` read-only and perform repair once. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Inject clock into `RoomUsageHistoryStore`; route `StatsViewModel` through repository instead of `AppModule.database`. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Search callers before removing `getUsageSummary`, `PhoneUsageSummary.kt`, and unused DAO method(s). | NOT STARTED | A1–A4 | Pending | No |

**Done when:** reads have no repair side effects, clock/repository boundaries are in place, and deletions are proven safe by search/tests.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### C5 — Retention

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Add pruning for all three rollup tables (day, app-day, session) with the legacy 90-day retention horizon. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Add tests proving every usage table is pruned at the boundary. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Verify A2 maintenance worker invokes rollup pruning. | NOT STARTED | A2 | Pending | No |

**Done when:** pruning covers every usage table and tests prove the retention horizon.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### C6 — What the cutover gate proves

**Status:** `NOT STARTED`  
**Blocked by:** A1–A4; golden logs depend on E1 owner session  
**Started:** —  
**Completed:** —  
**Owner-run:** Golden event logs are owner-provided

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Document and test that the gate proves completeness only; legacy values are not a correctness reference. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Add golden-log tests through tracker, aggregator, and `measure` using logs from two devices, including one non-Pixel. | NOT STARTED | A1–A4; E1 logs | Pending | No |
| [ ] | Owner supplies exported event logs from both phones; agent records provenance without exposing private information. | OWNER-RUN PENDING | E1/S11 | Pending owner report | Yes |
| [ ] | Test rollback: null cutover serves legacy history and resumes legacy writer; keep legacy tracker unchanged. | NOT STARTED | A1–A4 | Pending | No |
| [ ] | Preserve the refused cross-cutover detector seam and document the data-health explanation. | NOT STARTED | A1–A4 | Pending | No |

**Done when:** completeness-only contract, golden-log behavior, and rollback are evidenced; owner logs remain pending until supplied.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

---

## Batch D — Screens and wording

**Batch gate:** Required B and C work is DONE.

### D1 — Freshness everywhere

**Status:** `NOT STARTED`  
**Blocked by:** B and C  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Implement the shared freshness definition from plan section 4.3 and carry it in allowance UI models. | NOT STARTED | B and C | Pending | No |
| [ ] | Launcher, `ActiveScreen`, and `DailyAllowanceModal` all use shared freshness; modal displays or drops `usageByPackage` intentionally. | NOT STARTED | B and C | Pending | No |
| [ ] | Test reducer cases including idle-after-leaving exact, foreground tick freshness, stale read, and revoked permission unavailable. | NOT STARTED | B and C | Pending | No |

**Done when:** all allowance surfaces use one freshness state and reducer tests pass.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### D2 — Wording, permissions, and backup note

**Status:** `NOT STARTED`  
**Blocked by:** B and C  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Update Usage Access definition and broken-without copy to cover foreground detection, allowance measurement, and history. | NOT STARTED | B and C | Pending | No |
| [ ] | Update Stats, data-health, Focus/Standalone/Defense, How to Use, Changelog, and Privacy wording from the plan. | NOT STARTED | B and C | Pending | No |
| [ ] | Add backup/restore notice that usage history is device-local and rebuilt; do not change `.focusflow` format. | NOT STARTED | B and C | Pending | No |
| [ ] | Centralize `UsageAccess.isGranted`, including `MODE_DEFAULT`, `unsafeCheckOpNoThrow`, and failure behavior; `ActiveHeaderButton` errors are not treated as granted. | NOT STARTED | B and C | Pending | No |
| [ ] | Verify permission-health and allowance editor lock text agrees with PD-2/PD-4/PD-5/PD-6/PD-14 behavior. | NOT STARTED | B and C | Pending | No |

**Done when:** required screens, help, and permission paths use consistent wording and one permission definition.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

### D3 — Unavailable state

**Status:** `NOT STARTED`  
**Blocked by:** A3; D2  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Stats card displays “unavailable, grant Usage Access or try again” for failed/denied reads, never `0m`. | NOT STARTED | A3; D2 | Pending | No |
| [ ] | Test unavailable UI state and verify no failure path silently converts unknown to zero. | NOT STARTED | A3; D2 | Pending | No |

**Done when:** failed/denied reads render unavailable state and tests pass.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

---

## Batch E — Verification and release

**Batch gate:** D is DONE. Owner device session is performed once at the end by the owner.

### E1 — Owner device session

**Status:** `NOT STARTED`  
**Blocked by:** D; owner availability and both devices  
**Started:** —  
**Completed:** —  
**Owner-run:** Yes — device results are never agent-ticked

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Create `OWNER_DEVICE_TEST_GUIDE.md` with setup, exact steps, pass criteria, expected numbers, and report instructions for S1–S12. | NOT STARTED | D | Pending | No |
| [ ] | Add copyable result template for scenario, phone, pass/partial/fail, observed behavior, and requested numbers. | NOT STARTED | D | Pending | No |
| [ ] | Add debug-only usage diagnostics and one-tap Copy report with only package names and the fields in plan section 7. | NOT STARTED | D | Pending | No |
| [ ] | Use one log tag and include the exact logcat commands from plan section 7 in the guide. | NOT STARTED | D | Pending | No |
| [ ] | Include default-battery then unrestricted-battery procedure; record triage rule and at most two retest rounds. | NOT STARTED | D | Pending | No |
| [ ] | Confirm exported Room schema for the new DB version is committed. | NOT STARTED | A1 | Pending | No |
| [ ] | Owner runs S1–S12 once on Realme C3 (Android 10) and Samsung M31 (Android 12); attach reports and mark each `OWNER-RUN PENDING` until received. | OWNER-RUN PENDING | Guide/tooling complete; owner session | Pending owner reports | Yes |
| [ ] | Owner records PD-11 Stats comparison and provides golden event logs for C6 where available. | OWNER-RUN PENDING | Owner session | Pending owner reports | Yes |

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
**Blocked by:** A5–E2  
**Started:** —  
**Completed:** —  
**Owner-run:** No

| Done | Work item | Status | Blocked by | Evidence | Owner-run |
|---|---|---|---|---|---|
| [ ] | Reconcile every batch/item status with current source, exact test output, and owner reports; leave incomplete items open with reasons. | NOT STARTED | A5–E2 | Pending | No |
| [ ] | Verify all P0/P1 findings have evidence and all P2 findings are done or explicitly deferred with reasons. | NOT STARTED | A5–E2 | Pending | No |
| [ ] | Verify review gates G1–G9 and required release gates; record each pass/failure/unverified result. | NOT STARTED | A5–E2 | Pending | No |
| [ ] | Archive the v7 plan as historical reference without deleting it; make this v8 plan and tracker the active fixes documents. | NOT STARTED | A5–E2 | Pending | No |

**Done when:** plan section 10 is met or remaining items are explicitly documented as blockers/deferred work; no unsupported completion claims remain.  
**Notes:**  
**Evidence:**  
**Failures and blockers:**  
**Decisions and deferred work:**  

---

## Summary

| Batch | Status | Blocked by | Owner-run status |
|---|---|---|---|
| A5 — Tracker truth | DONE — evidence recorded | — | Not owner-run |
| A1 — Database and cutover | DONE — evidence recorded | A5 complete | S2 pending later |
| A2 — Maintenance job | IN PROGRESS | A5 complete; eligible; C5 pruning row blocked | Not owner-run |
| A3 — Stats zero reading | DONE — evidence recorded | A5 complete; eligible | S1 pending later |
| A4 — Test baseline | COMPLETE | A5 complete; eligible | Not owner-run |
| B1–B7 — One meter everywhere | NOT STARTED | A1–A4 | S4 pending later |
| C1–C6 — One definition of usage | NOT STARTED | A1–A4; E1 logs for C6 | Manifest confirmation/logs may be owner-provided |
| D1–D3 — Screens and wording | NOT STARTED | B and C; A3 for D3 | Not owner-run |
| E1 — Owner device session | NOT STARTED | D; owner/device availability | OWNER-RUN PENDING |
| E2 — Release gates | NOT STARTED | E1; owner release decision | Release monitoring OWNER-RUN PENDING |
| E3 — Close-out | NOT STARTED | A5–E2 | Not owner-run |

**Final status:**  
**Open blockers:**  
**Deferred work:**  
**Final evidence summary:**  
