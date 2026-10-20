# FocusFlow Subtasks & Rich Import — Work Tracker

**Authoritative progress record for [`PLAN.md`](PLAN.md).** The plan is the implementation contract; it is not authorization to edit app code. Follow [`AGENT_PRE_PROMPT.md`](AGENT_PRE_PROMPT.md) before any work.

## Overall status

- **Status:** IN PROGRESS
- **Progress:** 8.3% (3/36 required checklist items verified)
- **Current phase:** Phase 1 — Model, DB, repository (in progress)
- **Last updated:** 2026-10-20

## Tracker rules

- Status values: `NOT STARTED`, `IN PROGRESS`, `BLOCKED — <reason>`, `DONE — evidence recorded`.
- At the start of each phase, change its status to `IN PROGRESS` and write a dated note before doing its work.
- Update the relevant phase notes after each meaningful change; do not wait until the phase is finished.
- Tick an item only after it is complete and verified. An attempt, partial result, or unverified change stays unchecked.
- For each completed item, record the changed files, exact verification performed, and observed result.
- Record decisions, failed checks, blockers, fixes and successful reruns, and deferred work. Never erase failed attempts or blockers; append what happened next.
- If a check fails, leave the affected item unchecked until the fix and successful rerun are documented.
- Reconcile this tracker against the current code and observed verification before reporting completion. Leave unfinished items unchecked and explain why.
- Stop and report at each phase boundary, as required by the plan. Do not begin the next phase without satisfying its gates.
- Recalculate overall progress from verified checklist items only; do not count blocked, attempted, or unverified work.

## Phase overview

| Phase | Status | Verified progress | Started | Completed |
|---|---|---:|---|---|
| 0 — Baseline | DONE — evidence recorded | 3/3 | 2026-10-20 | 2026-10-20 |
| 1 — Model, DB, repository | IN PROGRESS | 0/6 | 2026-10-20 | — |
| 2 — Format, import/export, images | NOT STARTED | 0/7 | — | — |
| 3 — AI prompt and format guide | NOT STARTED | 0/4 | — | — |
| 4 — Viewing UI | NOT STARTED | 0/6 | — | — |
| 5 — Enhancements | NOT STARTED | 0/5 | — | — |
| 6 — Help, changelog, release checks | NOT STARTED | 0/5 | — | — |
| 7 — In-app authoring (optional; only if owner asks) | NOT STARTED | 0/1 | — | — |

**Progress calculation:** Phases 0–6 contain 36 required checklist items. Report required-plan progress as completed verified items / 36. Phase 7 is optional, excluded unless explicitly authorized; if authorized, update the denominator and record that decision. The Definition of Done is separately tracked below.

---

## Phase 0 — Baseline

**Status:** DONE — evidence recorded
**Blocked by:** None
**Started:** 2026-10-20
**Completed:** 2026-10-20
**Progress:** 3/3

- [x] Inspect current working tree and relevant project instructions before changes.
- [x] Run the baseline build/tests specified by the plan; record pre-existing failures without changing app code.
- [x] Record exact commands, results, and any unavailable checks; report the baseline and stop at this phase boundary.

**Notes / progress:**
Phase 0 baseline authorized and started on 2026-10-20. Working-tree/project inspection is complete. An earlier `bash scripts/test-unit.sh` invocation was recorded as running both required JVM test flavors and reaching Java/KSP test compilation, but its completion result was unavailable; it is retained as an incomplete attempt and is not counted as passing evidence.

Existing Actions were checked on 2026-10-20. The latest successful tbtechsdev APK workflow did run lint, unit tests, and APK assembly, but it was on an older commit (2026-10-13). The latest successful production debug APK workflow was on an older commit (2026-09-18) and only assembled the APK; it did not run tests. Current GitHub `main` has no Actions run, so historical runs do not establish this checkout's baseline.

The configured `Run Android unit tests` workflow completed on the current checkout on 2026-10-20. Both required unit-test tasks passed, with 285 tests each and no failures, errors, or skipped tests. Both debug-flavor lint tasks reported zero issues. Production and tbtechsdev debug APKs were assembled, and the minified production release assembly passed R8. No app-code changes were made in this phase.

The first combined local lint/APK/release command hit the shell's 300-second timeout on 2026-10-20. Its captured output reached `:app:assembleProductionDebug` but had no final result for the remaining tasks. The incomplete attempt is preserved below; a background rerun completed all requested tasks successfully.

**Evidence:**
- **Changed file:** `exercise/TRACKER.md` (baseline evidence only; no app code or test code changed).
- `git status --short`: only `exercise/TRACKER.md` is modified; `git diff --check` produced no output.
- Read `replit.md`: normal work uses the root Kotlin Android project; archived React app is out of scope.
- Inspected `scripts/test-unit.sh`: it runs `:app:testProductionDebugUnitTest` and `:app:testTbtechsdevDebugUnitTest` with `FOCUSFLOW_SKIP_APK_BUILD=1`.
- Environment probe: no Java command, `JAVA_HOME`, Android SDK environment variable, or `local.properties`; the repository bootstrap script provisions JDK 17 and Android SDK outside the repository.
- Current checkout before this tracker update: `HEAD=7a3410274fb2d1d34fb9f0bb78b696d1f69f9e32`; working tree clean. GitHub `main` was `97e2f69cdcd4a583b26b8bf6e5482315cf8d9615`, with zero Actions runs for that SHA.
- GitHub run `38430758585` (`Build tbtechsdev Debug APK`, run 10) succeeded on `47d5f3407a711fe245d0c93cd314bbbcfb32094b` on 2026-10-13. Its `Run tbtechsdev NewApi lint, unit tests and assemble APK` step succeeded; that revision's workflow specifies `lintTbtechsdevDebug`, `testTbtechsdevDebugUnitTest`, and `assembleTbtechsdevDebug`. [Run](https://github.com/TITANICBHAI/FocusFlow/actions/runs/38430758585)
- GitHub run `35345435400` (`Build Native Kotlin APK`, run 10) succeeded on `80f7cb2590fc9e84d6d753d4132d58242a815438` on 2026-09-18. Its only build step was `:app:assembleDebug`; it is not evidence of production unit tests. [Run](https://github.com/TITANICBHAI/FocusFlow/actions/runs/35345435400)
- Configured workflow `Run Android unit tests` ran `bash scripts/test-unit.sh`; the script used `FOCUSFLOW_SKIP_APK_BUILD=1` and invoked `:app:testProductionDebugUnitTest` and `:app:testTbtechsdevDebugUnitTest`. Observed `BUILD SUCCESSFUL in 5m 46s`, 52 actionable tasks. JUnit XML summaries: production 285 tests / 0 failures / 0 errors / 0 skipped; tbtechsdev 285 / 0 / 0 / 0. SDK XML v4/v3 and existing Kotlin/Gradle deprecation warnings appeared; no test failure was observed.
- First remaining-gates attempt: `bash scripts/build-apk-with-java.sh :app:lintProductionDebug :app:lintTbtechsdevDebug :app:assembleTbtechsdevDebug :app:assembleProductionRelease` (the helper also requests `:app:assembleProductionDebug` by default). The shell timed out at 300 seconds. Observed `:app:assembleProductionDebug` complete and its APK file exist; no final Gradle result was captured for the requested lint, tbtechsdev debug APK, or production release compile tasks.
- Successful background rerun used the same command. Observed `BUILD SUCCESSFUL in 5m 21s`; 151 actionable tasks (69 executed, 82 up-to-date). `:app:lintProductionDebug`, `:app:lintTbtechsdevDebug`, `:app:assembleTbtechsdevDebug`, `:app:lintVitalProductionRelease`, `:app:minifyProductionReleaseWithR8`, and `:app:assembleProductionRelease` completed. Both debug lint XML reports contain zero issues.
- APK outputs verified: `app/build/outputs/apk/production/debug/app-production-debug.apk` (SHA-256 `0488f6d35a8038245810887ebe3bfcbce874931418826b4e864d24a82cb0ba5c`); `app/build/outputs/apk/tbtechsdev/debug/app-tbtechsdev-debug.apk`; `app/build/outputs/apk/production/release/app-production-release-unsigned.apk`.

**Failures / blockers:**
- The initial authorization blocker was resolved when the owner explicitly started Phase 0 on 2026-10-20. No current blocker recorded.
- Historical Actions runs are supporting history only: no run exists for current GitHub `main`, the tbtechsdev test run is on an older commit, and the older production APK run did not execute tests. Current-checkout baseline evidence is recorded above.
- The first combined local lint/APK/release command timed out at the tool's five-minute limit without reporting a Gradle failure. The successful rerun below completed all gates; no baseline test, lint, or build failure remains.
- Non-fatal warnings observed: SDK XML v4/v3 compatibility; Kotlin/Android deprecations and serialization opt-in; `libandroidx.graphics.path.so` was packaged without stripping; Gradle reports deprecated features for Gradle 9.0.

**Decisions / deferred work:**
- No implementation decisions or app-code changes were made.
- Phase 1 has not been started; stop at the Phase 0 boundary pending the owner's next instruction. UI/device verification was not part of this baseline phase and was not run.

## Phase 1 — Model, DB, repository (no UI)

**Status:** IN PROGRESS
**Blocked by:** Phase 0 complete
**Started:** 2026-10-20
**Completed:** —
**Progress:** 0/6
**Plan sections:** §4.1–4.2, §5.1–5.4

- [ ] Add subtask domain models, goal formatting, layout policy, and normalization rules.
- [ ] Add the task/entity JSON column and Room 9→10 migration; register it and commit generated schema 10.
- [ ] Implement bounded per-element tolerant decode/memoization and size guarding.
- [ ] Add DAO/repository subtask reads and column-only writes; preserve task fields and `updatedAt`.
- [ ] Add ViewModel subtask actions and task-edit anti-clobber behavior without alarm/scheduler side effects.
- [ ] Verify the Phase 1 tests: model/normalizer, repository, ViewModel, and migration coverage; record any device-only limitation.

**Notes / progress:**
Owner authorized Phase 1 on 2026-10-20. Phase status and start date were recorded before implementation work. Current checkout and instructions were rechecked; the worktree had no unrelated changes. Scope is limited to §§4.1–4.2 and 5.1–5.4, with no UI changes.

The first implementation slice added the subtask domain types, shared limits, goal formatter, layout policy, normalizer, and model tests. The Phase 1 continuation now adds Task serialization/entity/DAO storage, the guarded Room 9→10 migration and registration, bounded tolerant subtask decoding, size-guarded repository writes, ViewModel subtask actions, and task-edit anti-clobber behavior. Repository, ViewModel, and migration tests are added. Verification is pending, so no Phase 1 checklist item is marked complete yet.

**Evidence:**
- `git status --short` before code edits showed only the Phase 1 tracker update; `replit.md` confirms normal work is limited to the Kotlin Android project.
- Added `app/src/main/java/com/tbtechs/focusflow/data/model/Subtask.kt` and model tests in `app/src/test/java/com/tbtechs/focusflow/data/model/`. No test result observed yet; checklist item 1 remains unchecked pending tests.
- Current Phase 1 continuation changed the task model/entity, DAO, Room migration and DI registration, repository mapping/cache/size guard, and ViewModel operations. Added repository/ViewModel JVM tests and the Room 9→10 instrumented migration test. `git diff --check` was clean; build/test verification is still pending.

**Failures / blockers:**
_None recorded._

**Decisions / deferred work:**
_None recorded._

## Phase 2 — Format, import/export, images at rest

**Status:** NOT STARTED
**Blocked by:** Phase 1 complete
**Started:** —
**Completed:** —
**Progress:** 0/7
**Plan sections:** §4.3–4.4, §6

- [ ] Add bounded image storage, format/dimension validation, re-encoding, atomic writes, and safe content-addressed paths.
- [ ] Add orphan cleanup with reference collection and the 10-minute grace period.
- [ ] Extend backup models/limits and context-aware JSON preflight validation.
- [ ] Parse subtasks and assets fail-soft, preserving valid tasks and aggregating import warnings.
- [ ] Export subtasks/assets with deterministic references and size-control warnings; retain byte-identical exports for tasks without subtasks.
- [ ] Restore/remap assets and support the opt-in enrich-existing-tasks behavior without overwriting existing subtasks.
- [ ] Add import-review summary, warnings, and the conditional enrich checkbox; verify Phase 2 JVM and instrumented cases as permitted by the plan.

**Notes / progress:**
_No work started._

**Evidence:**
_None yet._

**Failures / blockers:**
_None recorded._

**Decisions / deferred work:**
_None recorded._

## Phase 3 — AI prompt and format guide

**Status:** NOT STARTED
**Blocked by:** Phase 2 complete
**Started:** —
**Completed:** —
**Progress:** 0/4
**Plan sections:** §11.1–11.2, §11.4–11.5

- [ ] Update the in-app generation prompt with the optional subtasks/assets contract and safe image guidance.
- [ ] Add the shared valid example, subtasks/goal/assets documentation, and merge behavior to the in-app format guide.
- [ ] Verify the prompt/guide example parses successfully with zero warnings.
- [ ] Prepare the hosted-guide Markdown update for the owner; record that the external site itself was not edited.

**Notes / progress:**
_No work started._

**Evidence:**
_None yet._

**Failures / blockers:**
_None recorded._

**Decisions / deferred work:**
_None recorded._

## Phase 4 — Viewing UI

**Status:** NOT STARTED
**Blocked by:** Phase 3 complete
**Started:** —
**Completed:** —
**Progress:** 0/6
**Plan sections:** §7–8

- [ ] Replace the stale task-detail snapshot with live task lookup and close safely if the task is deleted.
- [ ] Add subtask progress to Home task cards and a subtasks section to task details, only when subtasks exist.
- [ ] Implement the subtask screen, Overview/Steps modes, goals/set tracking, navigation, and all-done prompt.
- [ ] Add accessible image display/loading, placeholder behavior, theme-correct styling, and text-scale integration.
- [ ] Preserve existing task actions and prove subtask ticks do not trigger alarm, notification, scheduler, or task-field mutations.
- [ ] Run and record the planned Compose UI tests; list any device-only visual/interaction checks as “needs device check”.

**Notes / progress:**
_No work started._

**Evidence:**
_None yet._

**Failures / blockers:**
_None recorded._

**Decisions / deferred work:**
_None recorded._

## Phase 5 — Enhancements

**Status:** NOT STARTED
**Blocked by:** Phase 4 complete
**Started:** —
**Completed:** —
**Progress:** 0/5
**Plan section:** §10

- [ ] Add optional photo attach/replace/remove using the Photo Picker and the secure image store.
- [ ] Add the full-screen zoom viewer.
- [ ] Add local rest/duration timers and keep-screen-on behavior from the main plan decisions.
- [ ] Add confirmed progress reset and, if implemented, active/focus-surface entry points without changing enforcement/session behavior.
- [ ] Add the stated privacy-policy line and verify applicable behavior; record device-only checks honestly.

**Notes / progress:**
_No work started._

**Evidence:**
_None yet._

**Failures / blockers:**
_None recorded._

**Decisions / deferred work:**
_None recorded._

## Phase 6 — Help, changelog, release checks

**Status:** NOT STARTED
**Blocked by:** Phase 5 complete
**Started:** —
**Completed:** —
**Progress:** 0/5
**Plan sections:** §11.3, §11.5; release gates from §0 and §13

- [ ] Update both how-to help screens with subtask, set/rest, photo, and task-completion behavior.
- [ ] Update the changelog and version as specified by the plan.
- [ ] Run the required unit-test suites and record exact commands/results.
- [ ] Run lint and the Production release compile check; confirm new serialization survives R8.
- [ ] Reconcile all Definition of Done items, unresolved failures, deferred work, and device checks; stop and report.

**Notes / progress:**
_No work started._

**Evidence:**
_None yet._

**Failures / blockers:**
_None recorded._

**Decisions / deferred work:**
_None recorded._

## Phase 7 — In-app authoring (optional; only if the owner asks)

**Status:** NOT STARTED
**Blocked by:** Explicit owner request; otherwise out of scope
**Started:** —
**Completed:** —
**Progress:** 0/1

- [ ] Only if explicitly authorized, implement and verify in-app add/edit/reorder/delete/duplicate authoring described in §13.

**Notes / progress:**
_Not authorized; out of scope unless requested._

**Evidence:**
_None._

**Failures / blockers:**
_None recorded._

**Decisions / deferred work:**
_None recorded._

---

## Definition of Done

Tick each item only after the evidence below it has been observed and recorded.

- [ ] A `.focusflow` file with subtasks/assets imports, shows review summary, and opens with working subtask rows; ticks persist after app restart.
- [ ] A task without subtasks is visually/behaviorally unchanged and exports byte-identically.
- [ ] Export then import reproduces subtasks and images, including tick state.
- [ ] Tests prove ticking causes no alarm/notification/scheduler side effects.
- [ ] Tests prove task title/time edits preserve subtasks and ticks.
- [ ] Hostile-file cases are rejected or warned without crashes as specified in §15.
- [ ] Prompt, in-app format guide, help, changelog, and hosted-guide Markdown handoff are complete.
- [ ] Required unit tests, migration coverage, lint, release compile, and honest device-check reporting are complete.

## Batch / phase history

Append dated entries; do not replace earlier history.

| Date | Phase / work item | Progress, decision, failure, blocker, or deferred work | Evidence / outcome |
|---|---|---|---|
| 2026-10-20 | Tracker initialized | No implementation work has started. Waiting for explicit owner authorization. | Plan copied unchanged from the supplied attachment to `exercise/PLAN.md`; no app code or tests changed. |
| 2026-10-20 | Phase 0 started | Owner explicitly authorized baseline work. | Tracker set to IN PROGRESS before inspection or checks; no app code changed. |
| 2026-10-20 | Phase 0 inspection | Identified project instructions, current tracked changes, test script behavior, and missing direct Java/SDK prerequisites. | `replit.md`, `git status --short`, `scripts/test-unit.sh`, `scripts/android-ui-verification.sh`, and environment probe inspected; no app code changed. |
| 2026-10-20 | Phase 0 unit tests started | Ran the project bootstrap-backed unit-test script; environment setup is still in progress. | `bash scripts/test-unit.sh`; JDK 17 and Android SDK were provisioned outside the repository; Gradle initialization began; no test result observed yet. |
| 2026-10-20 | Phase 0 unit-test tasks started | SDK setup completed and Gradle began the first flavor's test task. | Gradle output shows `:app:generateProductionDebugBuildConfig`; SDK XML v4/v3 warning observed, not treated as failure; test result pending. |
| 2026-10-20 | Phase 0 Kotlin compilation | Production and Tbtechsdev debug Kotlin compilation proceeded to Java/KSP unit-test compilation; compiler warnings appeared. | Gradle output reached `:app:compileProductionDebugJavaWithJavac`, `:app:compileTbtechsdevDebugJavaWithJavac`, and both unit-test KSP tasks; Material icon deprecation warnings observed; unit-test result pending. |
| 2026-10-20 | Phase 0 unit tests verified | Both required JVM test suites passed on the current checkout. | `Run Android unit tests` (`bash scripts/test-unit.sh`); both Gradle test tasks passed; XML totals 285 each, zero failures/errors/skips. |
| 2026-10-20 | Phase 0 lint/APK/release attempt | The first combined command reached production debug APK assembly but timed out before final results for the remaining tasks. | `bash scripts/build-apk-with-java.sh :app:lintProductionDebug :app:lintTbtechsdevDebug :app:assembleTbtechsdevDebug :app:assembleProductionRelease`; 300-second shell timeout; no Gradle failure observed; background rerun pending. |
| 2026-10-20 | Phase 0 complete | Baseline unit tests, debug lint, APK builds, and minified production release compile all verified; no app-code changes. | Both unit-test variants: 285 tests each, zero failures/errors/skips. Background build command completed successfully in 5m 21s; both lint reports had zero issues; R8 minification and production release assembly passed. |
| 2026-10-20 | Phase 1 started | Owner authorized implementation; no implementation changes made yet. | Tracker set to IN PROGRESS before code inspection; Phase 1 scope limited to model, Room/repository, and ViewModel behavior; UI and later phases excluded. |
| 2026-10-20 | Phase 1 model slice | Added domain types, validation limits, normalization, goal formatting, and layout policy; verification is pending. | `data/model/Subtask.kt` added. Phase 1 progress remains 0/6 until item 1 tests pass. |
| 2026-10-20 | Phase 1 storage and ViewModel implementation | Added task/entity/DAO persistence, guarded 9→10 migration registration, tolerant memoized decoding, subtask-only repository mutations with a size limit, ViewModel subtask actions, and edit anti-clobber behavior. Added corresponding repository, ViewModel, and migration tests. | `git diff --check` completed with no output. Required JVM tests, lint, release compile, and instrumented migration test have not yet run; Phase 1 remains 0/6. |
| 2026-10-20 | Phase 1 model tests added | Added tests for goal formatting, layout policy, IDs, progress, text cleanup, invalid data, and truncation counts. | `GoalFormatterTest`, `SubtaskLayoutPolicyTest`, and `SubtaskNormalizerTest` added; results pending. |
