# FocusFlow Subtasks & Rich Import — Work Tracker

**Authoritative progress record for [`PLAN.md`](PLAN.md).** The plan is the implementation contract; it is not authorization to edit app code. Follow [`AGENT_PRE_PROMPT.md`](AGENT_PRE_PROMPT.md) before any work.

## Overall status

- **Status:** IN PROGRESS
- **Progress:** 27.8% (10/36 required checklist items verified)
- **Current phase:** Phase 2/3 — Additions complete; combined verification pending
- **Last updated:** 2026-10-21

## Tracker rules

- Status values: `NOT STARTED`, `IN PROGRESS`, `BLOCKED — <reason>`, `DONE — evidence recorded`.
- At the start of each phase, change its status to `IN PROGRESS` and write a dated note before doing its work.
- Update the relevant phase notes after each meaningful change; do not wait until the phase is finished.
- As soon as any checklist item or individual verification step (including each test, lint, or build check) completes or fails, update its checkbox/status and record the exact command and observed result here before starting the next item or check.
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
| 1 — Model, DB, repository | IN PROGRESS — final lint confirmation pending | 6/6 | 2026-10-20 | — |
| 2 — Format, import/export, images | IN PROGRESS — implementation only; verification deferred by owner | 0/7 | 2026-10-21 | — |
| 3 — AI prompt and format guide | IN PROGRESS — implementation only; verification deferred by owner | 0/4 | 2026-10-21 | — |
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
**Progress:** 6/6
**Plan sections:** §4.1–4.2, §5.1–5.4

- [x] Add subtask domain models, goal formatting, layout policy, and normalization rules.
- [x] Add the task/entity JSON column and Room 9→10 migration; register it and commit generated schema 10.
- [x] Implement bounded per-element tolerant decode/memoization and size guarding.
- [x] Add DAO/repository subtask reads and column-only writes; preserve task fields and `updatedAt`.
- [x] Add ViewModel subtask actions and task-edit anti-clobber behavior without alarm/scheduler side effects.
- [x] Verify the Phase 1 tests: model/normalizer, repository, ViewModel, and migration coverage; record any device-only limitation. Migration instrumentation runtime needs a connected Android device.

**Notes / progress:**
Owner authorized Phase 1 on 2026-10-20. Phase status and start date were recorded before implementation work. Current checkout and instructions were rechecked; the worktree had no unrelated changes. Scope is limited to §§4.1–4.2 and 5.1–5.4, with no UI changes.

The first implementation slice added the subtask domain types, shared limits, goal formatter, layout policy, normalizer, and model tests. The Phase 1 continuation now adds Task serialization/entity/DAO storage, the guarded Room 9→10 migration and registration, bounded tolerant subtask decoding, size-guarded repository writes, ViewModel subtask actions, and task-edit anti-clobber behavior. Repository, ViewModel, and migration tests are added. Verification is pending, so no Phase 1 checklist item is marked complete yet.

On 2026-10-20, the owner authorized a short Phase 1 status check. Code/schema/test presence was inspected; no application code was changed. The production unit-test and lint command failed during production Kotlin compilation before either tests or lint ran. Keep all checklist items unchecked pending a fix and successful verification.

On 2026-10-20, the owner authorized fixing the Phase 1 compile error and rerunning its checks. Scope remains Phase 1 only; retain the failed attempt above and update evidence after observing the rerun.

The first rerun after correcting the serialization imports compiled the production Debug and Release Kotlin sources, then ran the production JVM suite: 303 tests completed, with two failures in newly added Phase 1 tests. One fixture used a numeric `title` that the serializer accepts as text; the ViewModel test reset Main inside `runTest`, before coroutine-test cleanup. Lint, Android-test Kotlin compilation, and R8 minification did not complete in that run.

On 2026-10-20, corrected those test issues: the malformed element now has a wrong-typed `steps` value, and the ViewModel test resets Main only after `runTest` completes. Verification is pending; no checklist items are complete yet.

The next rerun passed production lint and compiled the Production Debug Android-test sources. The production unit suite then reported 303 tests with one remaining failure: the ViewModel test called `single()` on the fixture's two subtasks. R8 minification was invoked, but Gradle failed because of the unit-test failure before reporting a final R8 result.

On 2026-10-20, the owner reported that Phase 1 lint, APK build, and tests have since been run successfully. A read-only status check found the implementation, schema, and migration test in the current checkout, but no Gradle test/lint reports or build artifacts were available to confirm those later results. The migration test is present, but its execution result is not recorded. Keep Phase 1 open until the latest results, including migration coverage, are available.

On 2026-10-20, the owner asked for verification to be rerun with the tracker updated as each check completes. The tracker and pre-prompt now require immediate checkbox/status/evidence updates after each checklist item or individual check, including tests. Verification will use the production JVM test flavor only; no APK will be assembled on Replit. The release minification check will run without APK packaging, and the Room migration instrumentation test will be attempted if an emulator/device is available.

The production JVM test rerun completed on 2026-10-20. Main and unit-test Kotlin compilation succeeded, but the suite had 303 tests with one failure: `TaskViewModelSubtasksTest.subtaskActionsUseColumnWritesAndDoNotReconcileWhileTaskEditsPreserveProgress` threw `IllegalArgumentException` because `single()` received both fixture subtasks at line 50. The Phase 1 verification item remains unchecked; fix the test selection and rerun before moving to the next check.

On 2026-10-20, corrected the ViewModel test to select the `"sets"` subtask by ID rather than requiring the two-item fixture to contain only one subtask. The production unit suite rerun is pending; no implementation behavior changed.

On 2026-10-20, the owner explicitly authorized completing Phase 1. The latest checkout has no uncommitted application, test, or tracker edits; only the two user-provided status/instruction attachments are untracked. The test selector correction is present in the current source. Resuming the Phase 1 audit and verification; no Phase 1 checklist item is complete until its required evidence is observed.

The current-code audit found that `setSubtaskSetsDone` applied the requested set count to every sets-goal subtask because its transformation did not compare IDs. The existing ViewModel fixture had only one sets-goal item, so it could not detect the cross-item mutation. An initial filename-filtered inventory did not include `GoalFormatterTest`; a follow-up content search confirmed the required test already exists and needs no change.

On 2026-10-20, fixed set progress to update only the requested subtask ID and expanded the ViewModel fixture to include a second sets-goal item. The regression test now asserts the other item remains untouched before reset. `git diff --check` produced no output. Unit-test and Phase 1 gate results are pending; progress remains 0/6.

The production JVM suite completed on 2026-10-20 after the targeted set-progress fix. The exact command `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:testProductionDebugUnitTest` reported `BUILD SUCCESSFUL in 6m 4s`; Gradle said the requested test task passed. JUnit XML aggregation over `app/build/test-results/testProductionDebugUnitTest/TEST-*.xml` found 67 suites, 303 tests, 0 failures, 0 errors, and 0 skipped; all five required model/repository/ViewModel classes were present. The task/entity column, guarded migration, registration, and schema 10 are present in the checkout and the Room/Kotlin production compilation passed. Five Phase 1 implementation items are verified; migration instrumentation execution, lint, and R8 minification remain pending.

Production debug lint completed on 2026-10-20 with `BUILD SUCCESSFUL in 3m 15s` for `:app:lintProductionDebug`. The generated `app/build/reports/lint-results-productionDebug.xml` contains 0 issues (0 fatal, errors, warnings, informational, ignored, or unknown). R8 minification and Android-test source/runtime checks remain pending.

Production release R8 minification completed on 2026-10-20 with `BUILD SUCCESSFUL in 5m 48s` for `:app:minifyProductionReleaseWithR8`. The command did not request an assemble/package APK task. Android-test source compilation and migration instrumentation availability remain pending.

Production Debug Android-test source compilation completed on 2026-10-20 with `BUILD SUCCESSFUL in 1m 8s` for `:app:compileProductionDebugAndroidTestKotlin`. Its two non-fatal warnings identified a deprecated `MigrationTestHelper` constructor and nullable `canonicalName`; after switching to the class-based helper overload with no auto-migration specs, recompilation completed with `BUILD SUCCESSFUL in 41s` and no Kotlin source warnings. Instrumentation execution remains pending.

On 2026-10-21, `adb devices -l` returned only the device-list header and no attached devices. The instrumented Room migration test therefore was not executed; its source compiled successfully, and this is recorded as the device-only limitation. No APK was assembled or installed. All six Phase 1 checklist items are verified, but production lint will be rerun after the migration-test helper cleanup before closing the phase.

**Evidence:**
- `git status --short` before code edits showed only the Phase 1 tracker update; `replit.md` confirms normal work is limited to the Kotlin Android project.
- Current Phase 1 fix: `TaskViewModel.setSubtaskSetsDone` now leaves nonmatching IDs unchanged; `TaskViewModelSubtasksTest` includes two sets goals and asserts the untargeted item's count and done state remain unchanged. `git diff --check` returned success with no output.
- `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:testProductionDebugUnitTest` completed with `BUILD SUCCESSFUL in 6m 4s`; Gradle reported `Gradle tasks passed: :app:testProductionDebugUnitTest`. JUnit XML aggregation over `app/build/test-results/testProductionDebugUnitTest/TEST-*.xml` found 67 suites, 303 tests, 0 failures, 0 errors, 0 skipped, including `GoalFormatterTest`, `SubtaskLayoutPolicyTest`, `SubtaskNormalizerTest`, `TaskRepositorySubtasksTest`, and `TaskViewModelSubtasksTest`.
- The current checkout contains the `subtasks` entity/domain column, guarded `MIGRATION_9_10`, its `AppModule` registration, and generated schema `10.json`; the successful production Gradle build compiled the Room/Kotlin production sources. Migration instrumentation execution remains pending.
- `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:lintProductionDebug` completed with `BUILD SUCCESSFUL in 3m 15s`; Gradle reported `Gradle tasks passed: :app:lintProductionDebug`. Parsed `app/build/reports/lint-results-productionDebug.xml`: 0 total issues (0 fatal/errors/warnings/informational/ignored/unknown).
- `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:minifyProductionReleaseWithR8` completed with `BUILD SUCCESSFUL in 5m 48s`; Gradle reported the requested minification task passed. No APK assemble/package task was requested. Android-test Kotlin compilation and device availability remain pending.
- `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:compileProductionDebugAndroidTestKotlin` first completed in `1m 8s` with two non-fatal helper-constructor warnings. After switching to `FocusFlowDatabase::class.java` plus an empty auto-migration-spec list, the same Production Debug Android-test compilation passed in `41s` with no Kotlin source warnings. No APK was assembled; instrumentation runtime remains pending.
- `/home/runner/.cache/focusflow/android-sdk/platform-tools/adb devices -l` returned `List of devices attached` with no device entries. The Room migration instrumentation test was not run because no device is attached; no APK was built or installed. Migration test source compilation passed. Phase 1 checklist item 6 is complete with this device-only limitation recorded.
- Added `app/src/main/java/com/tbtechs/focusflow/data/model/Subtask.kt` and model tests in `app/src/test/java/com/tbtechs/focusflow/data/model/`. No test result observed yet; checklist item 1 remains unchecked pending tests.
- Current Phase 1 continuation changed the task model/entity, DAO, Room migration and DI registration, repository mapping/cache/size guard, and ViewModel operations. Added repository/ViewModel JVM tests and the Room 9→10 instrumented migration test. `git diff --check` was clean; build/test verification is still pending.
- Status check: schema `app/schemas/com.tbtechs.focusflow.data.local.FocusFlowDatabase/10.json`, `MIGRATION_9_10` registration, model/repository/ViewModel tests, and the instrumented migration test are present. `git diff --check` remained clean; current untracked file is the user's newly attached status note.
- Ran `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:testProductionDebugUnitTest :app:lintProductionDebug`. It failed at `:app:compileProductionDebugKotlin` with unresolved `decodeFromJsonElement` references/type mismatch in `SubtaskJsonCache.kt` and `TaskRepository.kt`; no unit tests or lint completed. No APK/release build or instrumented migration test was run.
- After correcting the two serialization imports, ran `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:testProductionDebugUnitTest :app:lintProductionDebug :app:compileProductionDebugAndroidTestKotlin :app:minifyProductionReleaseWithR8`. Production Debug and Release Kotlin compilation completed, but the unit task reported 303 tests with 2 failures: `TaskRepositorySubtasksTest.decodesValidElementsWhenAnotherStoredSubtaskIsMalformed` (expected a numeric title to be invalid) and `TaskViewModelSubtasksTest.subtaskActionsUseColumnWritesAndDoNotReconcileWhileTaskEditsPreserveProgress` (Main dispatcher reset before `runTest` cleanup). The build stopped before lint, Android-test Kotlin compilation, or R8 completion.
- Corrected the malformed JSON fixture to use a non-list `steps` value, and moved `Dispatchers.resetMain()` outside the `runTest` call so coroutine-test cleanup runs while the test Main dispatcher is installed. `git diff --check` and the rerun are pending.
- Reran the same Phase 1 command. `:app:lintProductionDebug` and `:app:compileProductionDebugAndroidTestKotlin` completed; `:app:testProductionDebugUnitTest` reported 303 tests with one failure at `TaskViewModelSubtasksTest.kt:50` (`single()` saw both fixture subtasks). `:app:minifyProductionReleaseWithR8` was invoked but had no reported final result before the build failed.
- Current status audit: the subtask model, Room 9→10 migration/schema, repository/ViewModel code, and Phase 1 JVM/instrumented test sources are present. `app/build` and generated test/lint reports or APK outputs are absent in this checkout. The owner reports subsequent successful lint, APK build, and test runs, but no command output was supplied or available to verify them; the migration instrumentation result is not known.
- Added the immediate-progress rule to `exercise/AGENT_PRE_PROMPT.md` and `exercise/TRACKER.md`: record each completed or failed checklist item/check before starting another. `git diff --check` will be recorded with the current verification batch.
- Reran `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:testProductionDebugUnitTest`. Observed `:app:compileProductionDebugKotlin` and `:app:compileProductionDebugUnitTestKotlin` complete; the test task reported 303 tests, 1 failure, 0 errors/skips. Failure: `TaskViewModelSubtasksTest.subtaskActionsUseColumnWritesAndDoNotReconcileWhileTaskEditsPreserveProgress`, `IllegalArgumentException: List has more than one element` at `TaskViewModelSubtasksTest.kt:50`. Gradle reported `BUILD FAILED in 4m 54s`.
- Updated `TaskViewModelSubtasksTest.kt` to select the set-goal item using its `"sets"` ID. This is a test-only correction; verification is pending.

**Failures / blockers:**
- The initial production compilation failure was fixed by importing the JSON extension from `kotlinx.serialization.json`; its failed attempt is preserved above.
- Historical production JVM runs failed because tests selected malformed fixtures and the ViewModel action did not target a single set-goal item. These failures are resolved: the current production JVM suite passed all 303 tests with no failures, errors, or skipped tests, including the two-set-target regression.
- No Android device is attached, so the migration instrumentation test could not run. The test source compiles; this device-only runtime limitation is recorded and no APK was built or installed. Final lint confirmation after the migration-test helper cleanup remains pending.

**Decisions / deferred work:**
_None recorded._

## Phase 2 — Format, import/export, images at rest

**Status:** IN PROGRESS — implementation only; verification deferred by owner
**Blocked by:** Phase 1 final lint confirmation remains pending; owner explicitly authorized Phase 2/3 implementation before the combined verification pass.
**Started:** 2026-10-21
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
On 2026-10-21, the owner authorized adding the Phase 2 and Phase 3 implementation/test code before running tests or build/lint/R8/device verification. Those checks are deferred until both phases' additions are complete. Local Git status/diff checks only; no push or GitHub Actions. Phase 1's final lint confirmation is also deferred to that combined verification pass. Phase 2 implementation begins now; checklist items remain unchecked until the requested later verification is observed.

First implementation slice added the Phase 2 import-size constants, root-assets-only preflight string allowance, an injectable image codec and content-addressed image store, and a fail-soft per-subtask parser. These changes are not yet verified; tests will be added but not executed until Phases 2 and 3 additions are complete.

The next implementation slice added backup assets to the envelope/parser, fail-soft task/subtask parsing and warning aggregation, constrained asset export with re-encode/omission behavior, restore-time hash remapping and opt-in enrichment, import-review counts/checkbox, and image cleanup wiring for task deletes and app startup. Phase 2 tests and Phase 3 additions are still in progress; no tests or build checks have been run.

On 2026-10-21, audited the current checkout against §§4.3–4.4 and 6. The Phase 2 implementation and test sources are present. This continuation made per-subtask parser failures isolated from sibling entries and added a parser regression test plus an exporter-to-importer image/subtask round-trip test. Per the owner's sequencing, these test sources are not being executed yet; all checklist items remain unchecked pending the combined verification pass.

**Evidence:**
- Current-source audit confirmed the Phase 2 implementation and test sources are present; this continuation changed `SubtaskParser.kt`, `SubtaskParserTest.kt`, and `SubtaskAssetExporterTest.kt`.
- Git-only review: `git diff --check` succeeded with no output. `git status --short` listed those three files and `exercise/TRACKER.md` as modified; the attached instruction file remains untracked and untouched.
- No unit/instrumented tests, build, lint, R8, APK, or device checks were run; no checklist item is verified yet.

**Failures / blockers:**
_None recorded._

**Decisions / deferred work:**
_None recorded._

## Phase 3 — AI prompt and format guide

**Status:** IN PROGRESS — implementation only; verification deferred by owner
**Blocked by:** Phase 2 verification remains pending; owner explicitly directed that Phase 2 and 3 additions be completed before the combined verification pass.
**Started:** 2026-10-21
**Completed:** —
**Progress:** 1/4
**Plan sections:** §11.1–11.2, §11.4–11.5

- [ ] Update the in-app generation prompt with the optional subtasks/assets contract and safe image guidance.
- [ ] Add the shared valid example, subtasks/goal/assets documentation, and merge behavior to the in-app format guide.
- [ ] Verify the prompt/guide example parses successfully with zero warnings.
- [x] Prepare the hosted-guide Markdown update for the owner; record that the external site itself was not edited.

**Notes / progress:**
On 2026-10-21, started Phase 3 implementation under the owner's instruction to complete both Phase 2 and Phase 3 additions before running tests/build/lint/R8/device checks. Phase 2 implementation and its tests are still being completed; no Phase 3 verification has been run. The prompt and in-app guide are the only Phase 3 implementation scope; version/changelog work remains in Phase 6.

On 2026-10-21, audited the current prompt and in-app guide against §11.1–11.2. The shared subtask example, goal/assets guidance, merge note, prompt rules, and zero-warning parser test source are present. The test has not been run; the hosted guide remains an owner-facing Markdown handoff and the external site has not been edited.

On 2026-10-21, prepared the ready-to-paste hosted-guide Markdown in the completion report. The hosted site itself was not edited. The example parse test remains unrun pending the combined verification pass.

**Evidence:**
- Current-source audit confirmed the Phase 3 prompt, shared example, guide content, and zero-warning parser test source are present. Test execution remains deferred; see the combined Phase 2/3 Git-only evidence above.
- The ready-to-paste hosted-guide Markdown is included in the completion report; the external site was not edited.

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
| 2026-10-20 | Phase 1 status check | Confirmed the Phase 1 source, schema, and test files are present; left every checklist item unchecked. Production verification stopped at Kotlin compilation. | `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:testProductionDebugUnitTest :app:lintProductionDebug` failed at `:app:compileProductionDebugKotlin`: unresolved `decodeFromJsonElement` in `SubtaskJsonCache.kt` and `TaskRepository.kt`; tests and lint did not run. `git diff --check` was clean. No app-code changes made. |
| 2026-10-20 | Phase 1 verification rerun | Corrected the import package; production Kotlin compilation succeeded, but two Phase 1 JVM tests failed and halted later checks. | Production Debug/Release Kotlin compile tasks completed. `testProductionDebugUnitTest`: 303 tests, 2 failures (malformed JSON fixture expectation and Main-dispatcher cleanup timing). Lint, Android-test compilation, R8, and migration execution remain pending. |
| 2026-10-20 | Phase 1 test corrections | Corrected the malformed-element fixture and moved Main dispatcher cleanup after the `runTest` lifecycle; verification is pending. | Updated `TaskRepositorySubtasksTest.kt` and `TaskViewModelSubtasksTest.kt`; no rerun result observed yet. |
| 2026-10-20 | Phase 1 verification rerun | One ViewModel test assertion remained incorrect after the dispatcher-lifecycle fix; other listed checks progressed. | `testProductionDebugUnitTest`: 303 tests, 1 failure (`single()` encountered two subtasks). Production lint and Android-test Kotlin compilation completed; R8 result pending. |
| 2026-10-20 | Phase 1 assertion diagnosis | Confirmed the remaining ViewModel failure was the test's `single()` lookup on a two-item fixture. | XML failure identifies `IllegalArgumentException: List has more than one element` at `TaskViewModelSubtasksTest.kt:50`. The test will select by subtask ID; R8 result was not reported before Gradle failed. |
| 2026-10-20 | Phase 1 model tests added | Added tests for goal formatting, layout policy, IDs, progress, text cleanup, invalid data, and truncation counts. | `GoalFormatterTest`, `SubtaskLayoutPolicyTest`, and `SubtaskNormalizerTest` added; results pending. |
| 2026-10-20 | Phase 1 status audit | Owner reports successful lint, APK build, and tests after the recorded failure. Phase remains open because current-checkout verification outputs and the migration-test result are unavailable. | Source/schema/test files are present. No Gradle reports or APK artifacts were found under `app/build`; no new verification was run. |
| 2026-10-20 | Phase 1 unit-test rerun | The production test suite exposed the prior subtask-selection assertion failure; corrected the test selector and recorded the rerun as pending. | `:app:testProductionDebugUnitTest`: 303 tests, 1 failure in `TaskViewModelSubtasksTest.kt:50`; all production main and test Kotlin compilation completed. Test-only selector now targets the `"sets"` item. |
| 2026-10-20 | Phase 1 set-target correction | Fixed set progress to affect only the requested item and expanded the regression fixture to cover multiple set goals. | `TaskViewModel.kt` and `TaskViewModelSubtasksTest.kt` changed. `git diff --check` succeeded with no output; production tests are pending. |
| 2026-10-20 | Phase 1 production JVM suite | Model, repository, and ViewModel JVM test coverage passed after the set-target correction. | `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:testProductionDebugUnitTest`; `BUILD SUCCESSFUL in 6m 4s`; Gradle reported the task passed. JUnit XML totals not yet recorded. |
| 2026-10-20 | Phase 1 JVM results and schema audit | Confirmed the required JVM test classes and Room schema/migration wiring in the successful build. | JUnit XML aggregation: 67 suites, 303 tests, 0 failures/errors/skips; GoalFormatter, layout policy, normalizer, repository, and ViewModel test classes all present. Current source has DB version 10, registered 9→10 migration, and schema 10. Instrumented migration runtime remains pending. |
| 2026-10-20 | Phase 1 production lint | Production debug lint task passed. | `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:lintProductionDebug`; `BUILD SUCCESSFUL in 3m 15s`; Gradle reported the task passed. XML/report issue counts pending inspection. |
| 2026-10-20 | Phase 1 lint report audit | Confirmed Production Debug lint is clean. | Parsed `app/build/reports/lint-results-productionDebug.xml`: 0 issues; 0 fatal/errors/warnings/informational/ignored/unknown. |
| 2026-10-20 | Phase 1 production R8 minification | Production Release code/resources passed the configured R8 minification task. | `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:minifyProductionReleaseWithR8`; `BUILD SUCCESSFUL in 5m 48s`; no APK assemble/package task requested. |
| 2026-10-20 | Phase 1 migration test compilation | Android-test Kotlin sources, including the Room migration coverage, compiled successfully. | `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:compileProductionDebugAndroidTestKotlin`; `BUILD SUCCESSFUL in 1m 8s`. Two non-fatal migration-test warnings need source review; instrumentation runtime remains pending. |
| 2026-10-21 | Room migration helper warning cleanup | Replaced the deprecated nullable string-assets helper constructor with the Room database-class overload. | `FocusFlowDatabaseMigrationTest` now passes `FocusFlowDatabase::class.java` and an empty auto-migration-spec list. Production Android-test Kotlin recompilation is pending; no migration instrumentation runtime has been run. |
| 2026-10-21 | Phase 1 migration test source recompile | Confirmed the updated helper overload compiles without the previous Kotlin warnings. | `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:compileProductionDebugAndroidTestKotlin`; `BUILD SUCCESSFUL in 41s`; 2 tasks executed, 27 up-to-date. No APK assembled; instrumentation runtime still pending. |
| 2026-10-21 | Phase 2 parser/storage slice | Added bounded root asset data scanning, shared format limits, safe image storage primitives, and independent fail-soft subtask parsing. | Changed `BackupJsonLimits.kt`; added `ImageCodec.kt`, `SubtaskImageStore.kt`, and `SubtaskParser.kt`. Tests and all builds remain deferred by owner instruction. |
| 2026-10-21 | Phase 2 backup flow implementation | Added asset-aware parse/export/restore flow, image reference cleanup, and the import-review enrichment controls. | Changed `BackupEnvelope.kt`, `BackupSerializer.kt`, `BackupRestoreEngine.kt`, `BackupViewModel.kt`, `ImportConfirmScreen.kt`, `SettingsScreen.kt`, `TaskRepository.kt`, `AppModule.kt`, `SubtaskImageStore.kt`; added `SubtaskAssetExporter.kt`. Verification is deferred until Phase 3 additions are complete. |
| 2026-10-21 | Phase 3 started — implementation only | Owner directed completion of Phase 3 additions before combined verification; Phase 2 gates remain pending. | Set Phase 3 to IN PROGRESS before continuing its prompt/guide changes. No tests/build checks run. |
| 2026-10-21 | Phase 2/3 addition audit | Confirmed the planned implementation and test sources are present in the checkout; added explicit per-entry parser isolation and two regression tests. Test execution remains deferred until both phases' additions are complete. | Changed `SubtaskParser.kt`, `SubtaskParserTest.kt`, and `SubtaskAssetExporterTest.kt`. `git diff --check` succeeded with no output. `git status --short` also lists the user's attached instruction file as untracked; it was left untouched. No unit/instrumented tests, build, lint, R8, APK, or device checks run. |
| 2026-10-21 | Hosted format-guide handoff prepared | Prepared the Phase 3 Markdown block for the external guide; the hosted site was not edited. | The copy-ready block is included in the completion report. Marked the hosted-guide handoff item complete; the example parse test remains unrun. |
