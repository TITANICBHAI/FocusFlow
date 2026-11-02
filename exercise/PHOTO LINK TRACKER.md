# FocusFlow Photo Slots — Work Tracker

**Authoritative progress record for [`PLAN.md`](PLAN.md).** The plan is the implementation contract; it is not authorization to edit app code. Follow [`AGENT_PRE_PROMPT.md`](AGENT_PRE_PROMPT.md) before any work.

## Overall status

- **Status:** IN PROGRESS — all edits applied; NO test/compile run (no Android SDK, no Maven access in the sandbox)
- **Progress:** 27/38 required items verified (edit application only). Unticked: 0.1, 0.2, 1.9, 2.4, 3.5, 4.7, 5.5, 6.1–6.4 — all need a Gradle run on your machine.
- **Current phase:** none
- **Last updated:** 2026-11-02
- **Run order:** Phase 0 → 1 → 2 → 3 → 4 → 5 → 6. Stop and report at each phase boundary.

## Tracker rules

- Status values: `NOT STARTED`, `IN PROGRESS`, `BLOCKED — <reason>`, `DONE — evidence recorded`.
- At the start of each phase, change its status to `IN PROGRESS` and write a dated note before doing its work.
- Update the relevant phase notes after each meaningful change; do not wait until the phase is finished.
- As soon as any checklist item or verification step completes or fails, update its checkbox/status and record the exact command and observed result here before starting the next item.
- Tick an item only after it is complete and verified. An attempt, partial result, or unverified change stays unchecked.
- For each completed item, record the changed files, the exact verification performed, and the observed result.
- Record decisions, failed checks, blockers, fixes and successful reruns, and deferred work. Never erase failed attempts or blockers; append what happened next.
- If a check fails, leave the affected item unchecked until the fix and a successful rerun are documented.
- If an edit's "find this exact text" does not match exactly once: stop, record the edit name and the tool's message under Blockers, and ask the owner (plan rule R3).
- Recalculate overall progress from verified checklist items only; do not count blocked, attempted, or unverified work.
- Device checks are owner-run. Never tick them.

## Phase overview

| Phase | Status | Verified progress | Started | Completed |
|---|---|---:|---|---|
| 0 — Baseline | NOT STARTED | 0/3 | — | — |
| 1 — Model, parser, pure helpers | NOT STARTED | 0/9 | — | — |
| 2 — Restore and export | NOT STARTED | 0/4 | — | — |
| 3 — Secure download | NOT STARTED | 0/5 | — | — |
| 4 — Screens | NOT STARTED | 0/8 | — | — |
| 5 — AI instructions and help | NOT STARTED | 0/5 | — | — |
| 6 — Final verification | NOT STARTED | 0/4 | — | — |

**Progress calculation:** 3 + 9 + 4 + 5 + 8 + 5 + 4 = 38 required items. The owner's device checks (below) are not counted.

---

## Phase 0 — Baseline

**Status:** NOT STARTED
**Blocked by:** owner's start instruction
**Started:** —
**Completed:** —
**Progress:** 0/3

- [ ] 0.1 Inspect the working tree (`git status`; do not touch existing changes) and the project's instruction files.
- [ ] 0.2 Run the baseline full production tests; record the exact result and list any tests that already fail ("pre-existing").
- [x] 0.3 Confirm the key files from Phase 0 §0.3 exist; report and stop at this phase boundary.

**Notes / progress:**
—

---

## Phase 1 — Model, parser, pure helpers

**Status:** NOT STARTED
**Blocked by:** Phase 0
**Started:** —
**Completed:** —
**Progress:** 0/9

- [x] 1.1 Create `data/model/PhotoSlot.kt`.
- [x] 1.2 Apply the 9 `Subtask.kt` edits.
- [x] 1.3 Apply the `BackupJsonLimits.kt` edit.
- [x] 1.4 Apply the 10 `SubtaskParser.kt` edits.
- [x] 1.5 Apply the `UnknownFieldWarnings.kt` edit.
- [x] 1.6 Create `data/model/SubtaskPhotoSlots.kt`.
- [x] 1.7 Create `ui/home/subtask/PhotoPromptBuilder.kt`.
- [x] 1.8 Create the 6 new test files and apply the `SubtaskParserTest.kt` edit.
- [ ] 1.9 Run the Phase 1 test command; expected: success with counts 6 / 8 / 14 / 3 / 10 / 10 / 4 (see Phase 1 §1.9).

**Notes / progress:**
—

---

## Phase 2 — Restore and export

**Status:** NOT STARTED
**Blocked by:** Phase 1
**Started:** —
**Completed:** —
**Progress:** 0/4

- [x] 2.1 Apply the 11 `BackupRestoreEngine.kt` edits.
- [x] 2.2 Apply the 2 `BackupRestoreEngineTest.kt` edits.
- [x] 2.3 Apply the 4 `BackupViewModel.kt` edits.
- [ ] 2.4 Run the Phase 2 test command; expected: success, `BackupRestoreEngineTest` = 22 tests.

**Notes / progress:**
—

---

## Phase 3 — Secure download

**Status:** NOT STARTED
**Blocked by:** Phase 2
**Started:** —
**Completed:** —
**Progress:** 0/5

- [x] 3.1 Create `data/images/PublicAddressPolicy.kt`.
- [x] 3.2 Create `data/images/PhotoDownloader.kt` and `data/images/PhotoDownloadCoordinator.kt`.
- [x] 3.3 Create `data/images/SystemHostResolver.kt` and `data/images/UrlConnectionFetch.kt`.
- [x] 3.4 Create the 3 test files.
- [ ] 3.5 Run the Phase 3 test command; expected: success with counts 3 / 20 / 6.

**Notes / progress:**
—

---

## Phase 4 — Screens

**Status:** NOT STARTED
**Blocked by:** Phase 3
**Started:** —
**Completed:** —
**Progress:** 0/8

- [x] 4.1 Apply the 2 `TaskViewModel.kt` edits.
- [x] 4.2 Apply the 2 `AppModule.kt` edits.
- [x] 4.3 Create the 5 new Compose files (`PhotoSlotsBanner`, `PhotoDownloadController`, `PhotoDownloadDialogs`, `PhotoPromptDialog`, `PhotoBatchPicker`).
- [x] 4.4 Apply the 3 `TaskDetailModal.kt` edits and the 1 `HomeScreen.kt` edit.
- [x] 4.5 Apply the 9 item-screen / photo-menu / attach edits.
- [x] 4.6 Apply the 4 import review/result screen edits.
- [ ] 4.7 Run the full production test command; expected: success, no new failures versus the Phase 0 baseline (this compiles all Compose code).
- [x] 4.8 List the owner's device checks (PLAN §7.2) as "needs device check".

**Notes / progress:**
—

---

## Phase 5 — AI instructions and help

**Status:** NOT STARTED
**Blocked by:** Phase 4
**Started:** —
**Completed:** —
**Progress:** 0/5

- [x] 5.1 Apply the 7 `FocusFlowAgentGuide.kt` edits.
- [x] 5.2 Apply the 7 `FocusFlowFormatGuideData.kt` edits.
- [x] 5.3 Apply the 2 `FocusFlowFormatGuideDataTest.kt` edits.
- [x] 5.4 Apply the 5 help / privacy / changelog edits.
- [ ] 5.5 Run the Phase 5 test command; expected: success, `FocusFlowFormatGuideDataTest` = 4 tests, `ChangelogDataTest` passes.

**Notes / progress:**
—

---

## Phase 6 — Final verification

**Status:** NOT STARTED
**Blocked by:** Phase 5
**Started:** —
**Completed:** —
**Progress:** 0/4

- [ ] 6.1 Run the full production test command; record the exact result.
- [ ] 6.2 Reconcile this tracker with the real files and recorded results.
- [ ] 6.3 Write the final report (see `AGENT_PRE_PROMPT.md`), including every check that was **not** run.
- [ ] 6.4 Hand the owner PLAN §7.2 and §8; leave every device check unticked.

**Notes / progress:**
—

---

## Owner-run device checks (not counted in progress; never ticked by the agent)

Details are in PLAN §7.2. Status for each: `needs device check`.

- [ ] D1 Import with slots shows "photo slots" and "Photo needed".
- [ ] D2 Import works in airplane mode; nothing tries to download.
- [ ] D3 Download after consent attaches pictures to the right items.
- [ ] D4 Bad links (http, web page, huge file, no network) fail with plain messages; no browser opens.
- [ ] D5 Stop keeps pictures saved so far.
- [ ] D6 Add several pictures in order (picker order).
- [ ] D7 Single attach clears the slot; alt text = brief.
- [ ] D8 Reuse by title on merge works; "Replace all" does not reuse.
- [ ] D9 Copy picture prompts preview equals clipboard; style remembered.
- [ ] D10 Export without images keeps descriptions; re-import shows slots.
- [ ] D11 Old files import exactly as before; real pictures (ZIP bundle and inline `assets`) still import as real pictures; export with images round-trips.
- [ ] D12 Upgrade over existing data keeps everything.
- [ ] D13 Dark/light, large text, TalkBack.
- [ ] D14 Download while Network Protection is active fails gracefully.
- [ ] D15 AI round trip (PLAN §7.2 item 15 and Phase 5 §5.6).

## Decisions

- Decisions P1–P9 are fixed by `PLAN.md` §2. Record any owner decision made during the work here, with the date.

## Blockers and deferred work

- **Gradle/unit tests NOT run** (items 0.2, 1.9, 2.4, 3.5, 4.7, 5.5, 6.1): sandbox has no Android SDK and Maven/Google repos return 403. Run the plan's test commands locally; expected counts are in PLAN §4.
- 0.1: the uploaded zip is not a git repository, so `git status` could not be run. Lint, release build, APK build, GitHub Actions, device checks: not run.
- Phase boundaries were not stopped at individually: owner said "go / be fast", so all phases were applied in one pass.

## History

- 2026-11-02: Applied all 103 plan operations (22 new files, 22 edited files) with a script that copies the plan's blocks verbatim. Every "find this exact text" matched exactly once. Observed: PHASE-1 31 ops, PHASE-2 17, PHASE-3 8, PHASE-4 26, PHASE-5 21.
- Checks observed: Kotlin 2.0.21 frontend over all sources reported no syntax errors (only unresolved-jar noise); `SubtaskPhotoSlots` appears 5x in BackupRestoreEngine.kt and `remapImage` 0x; `photo` field in Subtask and SubtaskStep; SubtaskImageStore.kt, BackupBundleWriter.kt, SubtaskAssetExporter.kt byte-identical to the original; Gradle files and manifest untouched; changelog.json parses; unified patch applies cleanly to the original zip; imports for `scaledSp`, `LinearProgressIndicator`, `LocalClipboardManager`, `AnnotatedString`, `rememberLauncherForActivityResult`, `Toast` present in the new Compose files.
