# FocusFlow Subtasks & Rich Import — Work Tracker

**Authoritative progress record for [`PLAN.md`](PLAN.md).** The plan is the implementation contract; it is not authorization to edit app code. Follow [`AGENT_PRE_PROMPT.md`](AGENT_PRE_PROMPT.md) before any work.

## Overall status

- **Status:** IN PROGRESS
- **Progress:** 0% (no implementation work has been verified)
- **Current phase:** Phase 0 — Baseline
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
| 0 — Baseline | IN PROGRESS | 1/3 | 2026-10-20 | — |
| 1 — Model, DB, repository | NOT STARTED | 0/6 | — | — |
| 2 — Format, import/export, images | NOT STARTED | 0/7 | — | — |
| 3 — AI prompt and format guide | NOT STARTED | 0/4 | — | — |
| 4 — Viewing UI | NOT STARTED | 0/6 | — | — |
| 5 — Enhancements | NOT STARTED | 0/5 | — | — |
| 6 — Help, changelog, release checks | NOT STARTED | 0/5 | — | — |
| 7 — In-app authoring (optional; only if owner asks) | NOT STARTED | 0/1 | — | — |

**Progress calculation:** Phases 0–6 contain 36 required checklist items. Report required-plan progress as completed verified items / 36. Phase 7 is optional, excluded unless explicitly authorized; if authorized, update the denominator and record that decision. The Definition of Done is separately tracked below.

---

## Phase 0 — Baseline

**Status:** IN PROGRESS
**Blocked by:** None
**Started:** 2026-10-20
**Completed:** —
**Progress:** 1/3

- [x] Inspect current working tree and relevant project instructions before changes.
- [ ] Run the baseline build/tests specified by the plan; record pre-existing failures without changing app code.
- [ ] Record exact commands, results, and any unavailable checks; report the baseline and stop at this phase boundary.

**Notes / progress:**
Phase 0 baseline authorized and started on 2026-10-20. Working-tree/project inspection is complete. `bash scripts/test-unit.sh` is running both required JVM test flavors. The bootstrap installed JDK 17, Android SDK packages, and Gradle outside the repository. Both flavor Kotlin compile tasks have proceeded into Java/KSP test compilation; the compiler emitted Material icon deprecation warnings. The SDK XML v4/v3 compatibility warning was observed during setup. The test tasks have not completed, so no test result yet. No app-code changes are planned in this phase.

**Evidence:**
- **Changed file:** `exercise/TRACKER.md` (phase status only; no app code changed).
- `git status --short`: only `exercise/TRACKER.md` is modified.
- Read `replit.md`: normal work uses the root Kotlin Android project; archived React app is out of scope.
- Inspected `scripts/test-unit.sh`: it runs `:app:testProductionDebugUnitTest` and `:app:testTbtechsdevDebugUnitTest` with `FOCUSFLOW_SKIP_APK_BUILD=1`.
- Environment probe: no Java command, `JAVA_HOME`, Android SDK environment variable, or `local.properties`; the repository bootstrap script provisions JDK 17 and Android SDK outside the repository.

**Failures / blockers:**
- The initial authorization blocker was resolved when the owner explicitly started Phase 0 on 2026-10-20. No current blocker recorded.

**Decisions / deferred work:**
- No implementation decisions have been applied.

## Phase 1 — Model, DB, repository (no UI)

**Status:** NOT STARTED
**Blocked by:** Phase 0 complete
**Started:** —
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
_No work started._

**Evidence:**
_None yet._

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
