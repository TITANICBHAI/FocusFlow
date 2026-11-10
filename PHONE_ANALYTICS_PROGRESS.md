# Phone Usage Analytics — progress log

Stand-in for §13 of the implementation plan (the plan file itself is not edited; the repo's root `plan.md` is an older, finished plan and is untouched).
The complete plan (`ea55b0ae-plan.md`, with §10–§13 and Appendices A–C) arrived after Phase 1; Phase 1 was re-checked against it and stays valid.

## Environment

| Item | Result |
|---|---|
| Gradle / Android SDK | **BLOCKED — environment.** `services.gradle.org`, `dl.google.com`, `maven.google.com` and Maven Central are policy-denied (HTTP 403) from the agent sandbox. No Android SDK. |
| Baseline full production unit suite (T0 step 3) | **BLOCKED — environment.** Run it once on your machine and record duration / suites / tests / failures here. |
| Substitute verification | Pure-Kotlin code and its tests are compiled with Kotlin **2.0.21** (the repo's version, JVM target 17) and run through a minimal JUnit4 shim; a JUnit-style XML is written per class. This is **not** the Gradle task and does not replace it. |

## T0 — preflight: DONE (no code changed)

All 13 checks from T0 step 4 match the plan: `UsageEventsSource`, `KEYGUARD_HIDDEN`, `ROLLUP_LOOKBACK_DAYS = 3L`,
`writeDaysFromEvents`, `UsageRollupCoverage`, `getAnalyticsRange`, `MAX_ENTRIES = 500`, `version = 10`, schemas 6–10.json,
the hard-coded `"Blocked attempts"` metric (ArchivedStatsScreen.kt:691), the AppModule assignments, the two DAO accessors,
`androidx.room.withTransaction`.
T0 step 5: `greyoutRepository =` is on line 306, **above** `usageRollupCoordinator =` (line 325), so T8 can insert its block directly after the
`usageRollupCoordinator` statement.

## Phase 1 — T1, T2, T3: DONE (harness-verified, Gradle run pending)

| Task | Files (new) | Result in harness |
|---|---|---|
| T1 | `analytics/phone/{PhoneConstants,PhoneCodec,PhoneText,PhoneModels,PhoneReportModels}.kt`, `analytics/phone/insights/{PhoneInsightModels,InsightThresholds}.kt` | `PhoneBucketsTest` 7, `PhoneCodecTest` 8, `PhonePeriodTest` 4, `PhoneTextTest` 9 — all pass |
| T2 | `analytics/phone/HourSplitter.kt` | `HourSplitterTest` 9 — all pass |
| T3 | `analytics/phone/PhoneEventReducer.kt` | `PhoneEventReducerTest` 30 (the plan's 21 cases + extra boundary cases) — all pass |

Total: 6 classes, 67 tests, 0 failures. Tests are in `app/src/test/java/com/tbtechs/focusflow/analytics/phone/`.

Mutation check: 9 deliberate code breakages (bucket bound, period boundary, minute rounding, fixed-hour stepping, carried-over unlock,
gap cap, double-counted wake, first-app look-back and look-ahead, …). The first run left two uncaught
(a carried-over midnight unlock leaking into a day with its own unlocks; the first-app window edges). Tests were added for both and every
mutation is now caught.

Existing files edited in Phase 1: **none.**

### To run on your machine (not run by me)

```
FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:compileProductionDebugKotlin
FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:testProductionDebugUnitTest --tests "com.tbtechs.focusflow.analytics.phone.*"
```
Then open `app/build/test-results/testProductionDebugUnitTest/TEST-*.xml` and confirm the six classes above are present (7 + 8 + 4 + 9 + 9 + 30 = 67 tests).

## Phase 2 — T4–T8: DONE in code (harness-verified where possible; Gradle / Room / device checks pending)

| Task | Files | Result |
|---|---|---|
| T4 | new `analytics/phone/AppCategories.kt` (`AppCategoryMap`, `AppCategoryResolver`), test `AppCategoryMapTest` | 20 tests pass. The 141-package Appendix B table was generated from the plan by script (counts 20/16/26/15/14/20/9/21, no duplicates). `AppCategoryResolver` has no unit test (needs a real `Context`). |
| T5 | new entities `PhoneDayRollupEntity`, `PhoneFirstAppDayEntity`, `BlockedAttemptDayEntity`, DAO `PhoneBehaviourDao`; **E1** `FocusFlowDatabase.kt` (v10 → v11, `AutoMigration(10, 11)`, 3 entities, DAO accessor, version-table row 11); migration test `migrateV10To11AddsPhoneTablesAndKeepsExistingRows` + `PHONE_MIGRATION_DB_NAME` | Compiles in the harness against Room-annotation stand-ins. The CREATE TABLE SQL implied by the entities matches Appendix A character for character (script check). **`11.json` NOT generated** (needs a Room build). Migration test **OWNER-RUN PENDING**. |
| T6 | new `PhoneBehaviourStore.kt` (interface, `RoomPhoneBehaviourStore`, `blockedRowsToWrite`, mappers), `PhoneBehaviourRollupWriter.kt`; tests `FakePhoneBehaviourStore`, `PhoneBehaviourRollupWriterTest` (20), `PhoneDayEntityMappingTest` (13) | 33 tests pass (the plan lists 9 cases; extras cover the fast path, the 24 h rule, version mismatch, cancellation, pruning on both paths). `RoomPhoneBehaviourStore` type-checks against the real DAO signatures but has never run on Room. |
| T7 | new `BlockedAttemptSync.kt` (`TemptationLogParser`, `BlockedAttemptSync`); tests `TemptationLogParserTest` (9), `BlockedAttemptSyncTest` (13) | 22 tests pass **against a minimal org.json stand-in**; the real `org.json:json:20240303` run is pending. |
| T8 | **E2** `di/AppModule.kt` (4 properties + construction after `usageRollupCoordinator`), **E3** `background/UsageMaintenanceWorker.kt` (two guarded calls before `return when (pass)`) | **Not compiled** (needs the Android SDK). Edits copy the plan's snippets; imports added. |

Harness total after Phase 2: 142 tests, 0 failures (Phase 1 = 67, T4 = 20, T6 = 33, T7 = 22).

Mutation check (Phase 2): ~25 deliberate breakages of the category map, writer, store helpers and sync. All caught except two that cannot be killed here:
`dayEnd - 1` in the unresolved-tail check (a 1 ms edge; equivalent for all realistic input) and the explicit `isNull("pkg")` guard in `TemptationLogParser`
(only differs on Android's own `org.json`, where `optString` of a JSON null returns the text `"null"`).

Existing files edited in Phase 2: `FocusFlowDatabase.kt` (E1), `AppModule.kt` (E2), `UsageMaintenanceWorker.kt` (E3), `FocusFlowDatabaseMigrationTest.kt` (androidTest; new test, new constant, and one assertion changed 10 → 11, see ruling 6).

### To run on your machine (not run by me)

```
FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:compileProductionDebugKotlin
```
Expected: compiles, and `app/schemas/com.tbtechs.focusflow.data.local.FocusFlowDatabase/11.json` appears; `grep -c phone_day_rollup` on it must be > 0, and it contains all three tables. **Commit that file.**
If Room reports an auto-migration error, use Appendix A (`MIGRATION_10_11`, register in `AppModule`, drop `autoMigrations`).
```
FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:compileProductionDebugAndroidTestKotlin
FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:testProductionDebugUnitTest --tests "com.tbtechs.focusflow.analytics.phone.*"
```
Expected in the XML (Phase 1+2 only): 11 classes, 142 tests, 0 failures. After Phase 3 it shows 17 classes, 269 tests; after Phase 4 (T13) **22 classes, 380 tests, 0 failures**. Then run the full unit suite once (should equal your baseline + 380 and nothing else changed), and the instrumentation test `FocusFlowDatabaseMigrationTest` on a device/emulator.

## Phase 3 — T9–T12 (Gate A): DONE in code (harness-verified; Gradle / AppModule compile pending)

| Task | Files (new unless noted) | Result in harness |
|---|---|---|
| T9 | `BuilderUtils.kt`, `ScreenTimeBuilder.kt`, `PhoneBehaviourBuilder.kt`, `PhoneTestFixtures.kt` | `ScreenTimeBuilderTest` 21, `PhoneBehaviourBuilderTest` 21 — pass |
| T10 | `AppsBuilder.kt` | `AppsBuilderTest` 21 — pass |
| T11 | `DistractionBuilder.kt`, `FocusBlockingBuilder.kt` | `DistractionBuilderTest` 16, `FocusBlockingBuilderTest` 21 — pass |
| T12 | `PhoneBehaviourRepository.kt` (`PhoneReportSource`, `resolveRange`); **E2** `di/AppModule.kt` gets `phoneBehaviourRepository` | `PhoneBehaviourRepositoryTest` 27 (the plan's 11 cases plus extras) — pass. `AppModule` wiring **not compiled**. |

Totals: harness runs 17 test classes, **269 tests, 0 failures**. Mutation checks on every Phase 3 file; the last batch (repository) found one gap (a session that started before midnight was counted as an open today) and a test now kills it.

### To run on your machine (not run by me)
```
FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:compileProductionDebugKotlin
FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:testProductionDebugUnitTest --tests "com.tbtechs.focusflow.analytics.phone.*"
```
Expected: compiles (this is the first compile of `AppModule`/worker wiring and of the Room store), 17 classes, 269 tests, 0 failures. Then the full unit suite (baseline + 380) and the commit of `11.json` from Phase 2.

## Phase 4 — T13 insight engine: DONE in code (harness-verified; Gradle pending)

| Task | Files (new) | Result in harness |
|---|---|---|
| T13 | `analytics/phone/insights/{InsightStats,InsightContext,SummaryInsights,ChangeInsights,PatternInsights,RecordInsights,PhoneInsightEngine}.kt`; constants filled into `InsightThresholds.kt` (every number is a named constant; ratios are num/den pairs so "exactly 1.4×" is exact) | `InsightStatsTest` 9, `SummaryInsightsTest` 23, `ChangeInsightsTest` 30, `PatternInsightsTest` 25, `RecordAndAssemblyTest` 24 — all pass. Harness total **380 tests, 0 failures**. |

All 13 modules, the Appendix C wording, evidence lines, the 12-insight cap plus up to 2 notes, and the priority/id sort are implemented. The plan's `PhoneInsightEngineTest.kt` is split into four files by module group (same cases, easier to read).
Mutation check: ~75 deliberate breakages of thresholds, comparison operators, window clipping, ordering and caps. All caught after adding tests, except two equivalent ones: the plan's late-night "tiny prior" alternative (it is implied by the 1.5× rule, kept as written) and the `?: return` on a zero previous attempt rate (a 0% change is below the 20% bar anyway).

### To run on your machine (not run by me)
Same two commands as Gate A. Expected: 22 classes, 380 tests, 0 failures.

## Phase 5 — T14–T16 (Gate B): DONE in code (pure logic harness-verified; **Compose UI and screen edits are NOT compiled**)

| Task | Files | Verified |
|---|---|---|
| T14 | `ui/stats/phone/{PhoneChartMath,PhoneStatsStateHolder,PhoneStatsViewModel,PhoneCharts,PhoneUiParts}.kt` | `PhoneChartMathTest` 21, `PhoneStatsStateHolderTest` 20 pass in the harness. `PhoneCharts.kt`, `PhoneUiParts.kt` (Compose) and `PhoneStatsViewModel.kt` are uncompiled. `PhoneStatsViewModelTest` (2 tests) is owner-run only. |
| T15 | `ui/stats/phone/{StatsDestination,StatsPhoneSection,ExtraPhoneContent,PhoneInsightsScreen}.kt`; **E4** `ui/stats/StatsScreen.kt` (`StatsScreen` replaced: destination / extra-tab state, loadReport / loadInsights effects, ON_RESUME refresh); **E5a** `ArchivedStatsScreen.kt` (`phoneSection` slot after the device-usage card) | Not compiled. `ExtraPhoneContent` and `PhoneInsightsScreen` are stubs with final signatures (filled in T17–T20). |
| T16 | `ui/stats/phone/StatsPhoneText.kt` (pure), `StatsPhoneSection.kt` body (six cards); **E5b** `ArchivedStatsScreen.kt` (`DeviceUsageCard` now takes `blockedAttempts`, replacing the hard-coded "0") | `StatsPhoneTextTest` 39 pass; mutation pass: all behavioural mutants killed except one unreachable clamp. Compose body not compiled. |

Harness total: **460 tests, 0 failures** (Phase 4 was 380). The harness covers only the pure Kotlin; nothing under `@Composable`, the ViewModel wrapper, `StatsScreen`, or `ArchivedStatsScreen` has been compiled or run.

### To run on your machine (not run by me)
`./gradlew :app:compileDebugKotlin` first (this is the first compile of every Compose file above and of E4/E5a/E5b), then `./gradlew :app:testDebugUnitTest --tests "com.tbtechs.focusflow.ui.stats.phone.*" --tests "com.tbtechs.focusflow.analytics.phone.*"`.
Expected: 26 classes in the phone test XML (+ `PhoneStatsViewModelTest`), 460 harness tests + 2 ViewModel tests, 0 failures. Compile errors, if any, are most likely import or modifier mismatches in the Compose files; tell me and I will fix them.

## Phase 6 — T17–T19 (Gate C): DONE in code (pure logic harness-verified; **Compose UI NOT compiled**)

| Task | Files | Verified |
|---|---|---|
| T17 | `ui/stats/phone/ExtraAppsModels.kt` (pure), `ExtraAppsTab.kt` | `ExtraAppsModelsTest` 18 pass |
| T18 | `PhoneTabModels.kt` (pure), `ExtraPhoneTab.kt`, `ExtraDistractionTab.kt` | `PhoneTabModelsTest` 18 pass |
| T19 | `FocusTabModels.kt` (pure), `ExtraFocusTab.kt` | `FocusTabModelsTest` 9 pass |
| shared | `ExtraTabParts.kt` (`ExtraTabList`, `ExtraCard`, `PhoneHeroText`, `PhoneListRow`); `ExtraPhoneContent.kt` is now the real router (no placeholder text) | — |

Harness total: **504 tests, 0 failures** (Gate B was 460). Mutation pass on the three model files: 14 of 14 behavioural mutants killed. Nothing under `@Composable` was compiled or run.

### To run on your machine (not run by me)
Same two commands as Gate B. Expected: 29 harness test classes in the phone XML (+ `PhoneStatsViewModelTest`), 504 tests + 2 ViewModel tests, 0 failures.

## Phase 7 — T20–T21 (Gate D): DONE in code (pure logic harness-verified; Gates A–C confirmed by the owner to compile and build an APK)

| Task | Files | Verified |
|---|---|---|
| T20 | `ui/stats/phone/PhoneInsightsModels.kt` (pure), `PhoneInsightsScreen.kt` (stub replaced) | `PhoneInsightsModelsTest` 9 pass; screen not compiled |
| T21 | `PhoneCharts.kt` (BucketBars font-scale fix), unused imports removed from 5 test files, `PhoneBehaviourRepositoryZoneTest` (5), `PhoneAnalyticsStressTest` (5) | pass; zone tests mutation-checked (5 of 5 UTC-instead-of-local mutants killed after adding 2 tests) |

Harness total: **524 tests in 31 classes, 0 failures** (Gate C was 504). `PhoneStatsViewModelTest` (2) is owner-run.

### T21 hardening checklist — what was done and what was not
1. **Stress**: 90 days × 20 apps × 300 sessions/day builds every section plus insights in under 20 s; 20,000 events reduce one day in under 10 s; all-zero and empty inputs give no exception or NaN; bar maths survives NaN / infinity / negative / Long.MAX. Done (timings are asserted loosely, not benchmarked on a phone).
2. **No builders in composables**: grep confirms the only reference to the engine is the ViewModel default. Done.
3. **Time zone**: repository tested in Asia/Kolkata (UTC+5:30), including local-today ≠ UTC-today (02:00 IST), spans around local midnight, partial-day detection against the local midnight, and the insight input's `today`. Done.
4. **Permission flows**: revoked → `PermissionNeeded` without touching the source or store (existing repository tests 1, 354, 361); granted later → ON_RESUME `refresh()` re-runs the last request (state-holder tests). Not tested on a device (S-checks).
5. **Low-data copy**: every card has its own empty text (no "0" / "NaN" shown as data). Verified by reading, not by screenshots.
6. **Numbers agree**: daily bars sum to the total; hour-of-day average ≈ per-day average (±24 ms rounding); listed apps + tail = total app time; category shares ≈ 100%; blocked attempts = Σ rows = Σ apps = Σ hours. **Not verified here:** that the Stats "Blocked attempts" tile (`BlockingReport.totalAttempts`, from the existing analytics) equals the Focus tab's `blockedAttempts` (durable table). Both read the same temptation log; confirm on device (S9).
7. **Accessibility**: charts carry content descriptions, headings carry heading semantics, touch targets ≥ 40 dp (existing pill / button defaults). Font scale: found and fixed one clipping risk (count text in `BucketBars` inside a fixed-height box; its reserve is now in sp) and one label risk (bucket labels limited to one line, now wrap). Not seen on a device at 130% / 200%.
8. **Cleanup**: no TODO / println / "coming soon" remains in the phone packages; unused imports removed.
9. **Schema**: `app/schemas/.../11.json` is **not** in this zip (only a real Room build writes it). Your build generated it; commit it.
10. **Diff scope**: against the original, changes are exactly E1–E5 files (`FocusFlowDatabase.kt`, its migration `androidTest`, `AppModule.kt`, `UsageMaintenanceWorker.kt`, `StatsScreen.kt`, `ArchivedStatsScreen.kt`) plus new files.

## Phase 8 — T22 (docs): DONE (documents only; no code changed)

- `app/PERSISTENCE_CONTRACT.md`: new section "Derived phone-behaviour tables (schema 11)" (three tables, derived and not in the backup, rebuilt from OS events and the temptation log, 365-day retention vs 90 for `usage_rollup_*`, `AutoMigration(10, 11)`).
- `.agents/memory/phone-usage-analytics.md` (new, one page) and one line in `.agents/memory/MEMORY.md`. No other note was touched.
- The plan's own "§13 / Definition of done" lives in the plan file, which is not in the repo; the equivalent record is this file.

### Definition of done — status
- [x] Pure core, builders, repository, insight engine: harness-verified (524 tests incl. 5 stress, 5 time-zone).
- [x] Gates A–C compile and build an APK (owner-confirmed).
- [ ] Gate D Compose changes compile (`PhoneInsightsScreen.kt`, `PhoneCharts.kt`) — pending your build.
- [ ] `PhoneStatsViewModelTest` run — pending.
- [ ] `app/schemas/.../11.json` committed — pending (generated by your build).
- [ ] T23 device checks S1–S16 — OWNER-RUN PENDING (table in the plan, section 10).

## Phase 9 — Extras (post-plan, requested by the owner): three small features on the Stats screen

All three reuse data the report already has: no new tables, no Room change, no new permission.

| Feature | Where | Files | Verified |
|---|---|---|---|
| **Screen time by weekday** — average per weekday (Mon–Sun), highest weekday called out, complete days only | New card on Stats under "Screen time by day"; shown once there are 14 complete days (Week window never qualifies, 3 Months / All do) | `StatsExtrasText.kt` (pure), `StatsPhoneSection.kt` | `StatsExtrasTextTest` (harness) |
| **Parts of the day** — screen time split into morning / afternoon / evening / late night with share and time | New card on Stats under "When your screen is on"; hidden when there is no screen time | same | same |
| **Share summary** — a plain-text summary through the system share sheet | Button at the bottom of the Stats phone section | same | text builder tested; the share intent itself is uncompiled and untested on a device |

Harness total: **543 tests in 32 classes, 0 failures** (before the extras: 524 / 31). Mutation pass on `StatsExtrasText.kt`: all behavioural mutants killed (one survivor found, fixed with a stronger test).

## Rulings

1. **Progress log location** — this file stands in for §13 (the plan file itself is left untouched). Cost if wrong: move the content.
2. **Verification path** — Gradle cannot run here, so Phase 1 is verified with the standalone compiler + JUnit shim at the repo's exact Kotlin version. Cost if wrong: a Gradle-only difference (none expected for pure Kotlin) shows up on your first compile.
3. **`PhoneText.duration`** — rounding to the nearest minute is applied before splitting into hours, so 3 599 999 ms is `"1h"`, as the plan's own test requires.
4. **T3 follows the plan's algorithm literally** in two corners, recorded so they are not mistaken for bugs: `DEVICE_STARTUP` clears the screen/unlock state without closing an open span (a missing shutdown event loses that span's time), and a zero-length screen span or unlock session (start == end) still counts as a session in bucket 0. Cost if wrong: a negligible count on rare devices; easy to guard later.
5. **T4 `mathgame`** — the plan's test example `com.example.mathgame → GAMES` contradicts the plan's own keyword rule (`game` has fewer than 5 letters, so it must equal a whole package segment; `mathgame` is one segment). The rule text was followed; the test asserts `OTHER` and is named `mathgameIsNotMatchedByTheGameKeywordRule`. Cost if wrong: a few unknown games land in OTHER instead of GAMES; relax the rule for `game` to `endsWith`/`contains` and flip that one test.
6. **Migration-test version assertion** — the existing instrumentation test asserted `assertEquals(10, migrated.version)` after opening a full chain; with v11 and an AutoMigration that is 11, so it was changed. Cost if wrong: none beyond that single line.
7. **Version-history KDoc row** — one row for version 11 was added to the table in `FocusFlowDatabase.kt`'s KDoc (the plan says "no other line", but the table would otherwise be false). The older sentence "runs the registered migrations through version 10" was left unchanged.
8. **Blocked-attempt merge as a pure function** — `blockedRowsToWrite` is shared by `RoomPhoneBehaviourStore` and the test fake, so the writer and sync tests exercise the real max rule and it has its own unit tests. Duplicate incoming keys collapse to the highest. Cost if wrong: none.
9. **`toEntity` rejects LIVE** — `PhoneDay.toEntity` throws for `PhoneDayStatus.LIVE` (live days are never stored); `toPhoneDay` reads any unknown status text as PARTIAL. Cost if wrong: none; the writer never produces LIVE.
10. **`TemptationLogParser` JSON-null guard** — an explicit `"pkg": null` is skipped on Android too (the existing private parser would produce the pkg `"null"` there). Cost if wrong: none for real logs.
11. **Live "today" only when events exist** — a live day is added only when the event read is `Available` **and** non-empty. An empty or unreadable read gives no LIVE day, so a phone with no events and no stored data is `NoData` instead of a blank report. Cost if wrong: an all-zero live day is not shown.
12. **Zero-time apps are excluded** from the Apps and Distraction lists (an app that is only blocked, never used, shows in Focus/Blocking but not in the distraction list). Cost if wrong: a blocked-but-unused app is missing from "Distraction apps".
13. **Period share** — `sharePercent` for a time-of-day period is its share of the sum of the four period totals, not of the whole range total. Cost if wrong: a few points of difference when sessions straddle periods.
14. **Opens today** — sessions that started before local midnight add time to today but are not counted as an open today. Cost if wrong: one fewer open for an app used across midnight.
15. **Coverage "since" dates** — `phoneDataSince` / `appDataSince` are the earliest loaded phone-day / app-day dates inside the requested range, not the earliest ever stored. Cost if wrong: the "data since" caption is later than the true first date on short windows.
16. **Ties** — peak hour/day picks the earliest index; highest/lowest day ties go to the later date (as the plan's T9 text says). Cost if wrong: which of two equal days is named.
17. **Persist failures are swallowed** — if writing past days or syncing blocked attempts fails (other than cancellation or lost usage access), the report is still built from what is stored. Lost usage access gives `PermissionNeeded`. Cost if wrong: a stale report instead of an error.
18. **Confidence** — "HIGH when the sample is ≥ 2× the minimum" needs a definition of sample. Used: total complete days across all windows a module reads (or the module's own unit: unlocks, sessions, minutes of focus), against the largest single minimum it states. Cost if wrong: the HIGH/MEDIUM label (shown in evidence only) is more or less generous; a few modules (e.g. consistency without a previous period) can never be HIGH.
19. **Monthly summary gaps** — the plan gives no minimum for the unlocks line or a direction rule. Used: ≥ 4 days with unlock signal in each month (same as weekly) and the same ±5% direction rule. Cost if wrong: the unlocks line appears/hides a little earlier or later.
20. **No percent when the previous value is 0** — the percent part of a line is left out (e.g. "Screen time: 2h/day."); direction is FLAT. Cost if wrong: none for real data.
21. **UNUSUAL_DAY needs a usual day above 0** — a baseline median of 0 gives no insight (a percent above 0 is undefined). Cost if wrong: none realistic.
22. **CONSISTENCY wording** — Appendix C has no text for "first unlock varies a lot"; used " Your first unlock time varies a lot from day to day." "Usually around" is the median minute of the day. The ±0.1 trend rule has a 1e-9 tolerance so exactly 0.1 counts. Cost if wrong: wording only.
23. **Records** — `isNew` = date within today−7 … today; `most_used_app` sums every given app day (including live today) and "n days" is the number of distinct dates with app rows; busiest hour and most-used app have no date, so never "new"; ties go to the later date. Cost if wrong: a card is "New" a day early/late.
24. **FOCUS_TREND** — a still-running session is counted to the end of the window (no clock); the ±20% bar is on the rounded percent; no insight when the previous rate is 0; the held-rate gap is compared on the rounded percents. Cost if wrong: an edge case flips at exactly the boundary.
25. **TIME_OF_DAY_HABIT** — uses complete days only; ties between periods go to the earlier period; the period name is lower-cased in the text ("a late night app"). Cost if wrong: wording only.
26. **DATA_NOTE unlocks** — evaluated on complete days of the last 14 (needs at least one) with average wakes ≥ 5/day. Cost if wrong: note appears on 1–2 days of data.

27. **State holder extracted** — the ViewModel's logic lives in `PhoneStatsStateHolder` (pure, takes a `CoroutineScope`) so it can be tested without androidx; `PhoneStatsViewModel` is a thin wrapper and the UI-state types are in the holder's file. Cost if wrong: none.
28. **ViewModel test is thin** — `PhoneStatsViewModelTest` (2 tests, owner-run) checks wiring only; logic is covered by `PhoneStatsStateHolderTest` and `PhoneChartMathTest`. Cost if wrong: wiring bugs surface at your first run.
29. **Hour-axis labels** use four equal quarter weights (12a/6a/12p/6p) instead of SpaceBetween so they line up with bars. Cost if wrong: cosmetic.
30. **Extra pure helpers** — `shareBarFraction`, `PhoneReportFixtures`, and the text helpers `inAppsText`, `weekdayWeekendNote`, `wakeUpsStat`, `gapStat`, `quickChecksStat`, `byDayCaption`, `busiestCaption`, `appsFootnote` were added so wording is testable. Cost if wrong: none.
31. **Source failures** — the state holder catches non-cancellation exceptions from the source into `Failed`; a cancelled load never writes state. Cost if wrong: an error message instead of a crash.
32. **"Other apps" row** includes `othersMs` (time beyond the 30 listed apps) as well as listed apps past the 6 shown; shown when ≥ 0.5%. Cost if wrong: the row's share differs by the tail's share.
33. **By-day caption** is "Last N days" (N = min(days, 30)) when more than 7 days, rather than the literal "Last 30 days". Cost if wrong: wording only.
34. **PermissionNeeded shows nothing** in the Stats phone section; the existing usage-access card already explains it. Cost if wrong: none.

35. **Window picker lives in the router** — the plan has each tab render `PhoneWindowTabs` itself; here `ExtraPhoneContent` renders it above the tab, so it stays visible while a new window loads (the tab content is replaced by the loading message). Tab composables therefore take only the report. Cost if wrong: sort / expanded-app state resets when the window changes.
36. **Held rate** is rounded to the nearest percent (2 held, 1 interrupted → 67%). Cost if wrong: ±1%.
37. **Phone-tab edge wording** — "—" when a per-day average is missing; "1 quick check" / "1 session" / "1 open a day" use the singular; the busiest hour is the earliest on a tie. Cost if wrong: wording only.
38. **Focus week bars** are padded with empty dimmed bars when fewer than 8 weeks exist, so there are always 8. The FLAT trend sentence has no "over the last two weeks" (exactly as the plan's text). Cost if wrong: cosmetic.
39. **Apps tab detail** — "Time of day" bars show whole minutes (rounded); "Last days" bars use the last ≤ 14 days and dim partial/missing days; "Show more" reveals up to 30; the by-category and by-period cards are hidden when empty. Cost if wrong: cosmetic.
40. **Distraction empty hero** — when distraction time is 0 the card shows a muted "No distraction time recorded for this period." instead of "0s". Cost if wrong: wording only.

41. **Insights screen shows what the ViewModel computed** — it never calls the engine; "How this was calculated" toggles the engine's own evidence lines unchanged. Cost if wrong: none.
42. **Insight card order** is the engine's order inside each fixed group (summary, change, pattern, record, note). Cost if wrong: none.
43. **Fonts** — `BucketBars` text room is 14 sp-as-dp (same 14 dp at default scale); bucket labels may wrap to two lines. Cost if wrong: cosmetic.
44. **Stress limits are loose** (20 s / 10 s) so they are not flaky on slow machines; they catch a hang, not a slowdown. Cost if wrong: a slowdown under the limit goes unnoticed.

45. **Weekday chart threshold** — 14 complete days and, per weekday, 2 samples for a full-strength bar (fewer is faded). A weekday tie goes to the earlier day. Cost if wrong: a noisier or emptier chart.
46. **Part-of-day boundaries** reuse `PhonePeriod` (morning 5–11, afternoon 12–16, evening 17–21, late night 22–4); the card labels show those hours. Cost if wrong: none.
47. **Share summary** includes the three most-used app names, so it is only sent when the person chooses an app in the share sheet; nothing is shared automatically. Cost if wrong: an app name goes to whoever they pick.

## Open items
- `app/schemas/.../11.json` can only be generated by a real Room build (T5). It must be kept and committed after your first compile.
- The Gradle compile, the Phase 1+2 unit tests under Gradle, and the v10 → v11 instrumentation test have not been run by me.
- T8 and T12 wiring (`AppModule`, worker) have never been compiled.
- T22 is done (see Phase 8). T23 (owner device checks S1–S16) remains. Gates A–C are confirmed to compile; the Gate D Compose files (`PhoneInsightsScreen.kt`, the `PhoneCharts.kt` edit) are not yet compiled.

## Phase 10 — UI rework (Stats / Extra / nudges / "How it's calculated")

Ruling 48: Stats returns to its original layout (period tabs, Observed Device Time, one compact "Phone habits" card, focus/blocking, one button into Extra). The 3-destination pill system is removed.
Ruling 49: Extra has three flat tabs (Overview, Apps, Habits) and one period dropdown. Distraction/Focus/Phone/Insights tabs are folded into Habits as visual cards (big numbers, bars, one-liners).
Ruling 50: Pills are replaced by flat underlined tabs (`StatsFlatTabs`) on Stats, Extra and Overview; the app sort pills became one cycling "Sort" button.
Ruling 51: All "how this is calculated" text leaves the cards and lives on one Settings page (About → "How usage data is calculated"), separate from How to Use. Content is constant-driven and tested (`UsageDataGuideTest`).
Ruling 52: Nudges now also come from phone-habit insights (surge, time-of-day habit, late night), merged with existing habit nudges under a cap of 3, opt-in. The switch + preview live in Extra · Habits; Stats shows a one-line invitation while off.
Ruling 53: Achievements removed from Overview and All-Time stats. `AchievementRow.kt` and `StatsViewModel.achievementState` are now dead code.
Ruling 54: `StatsReadinessSection` keeps permission prompts only. Stats has no 3-month tab, so leaving Extra on 3 Months falls back to Week.
Not compiled here: all Compose changes, nav route, Settings row.

## Phase 11 — Report audit + capture-screen beta
Ruling 55: The report button opens a user-reviewed email draft (mailto). Fixed: long logs dropped the NEWEST lines (now the oldest are dropped); the user's own message had its e-mail/links redacted (now only secrets are); error details are separate from the log cap. Added "Share full log as .txt" (FileProvider, cache/shared, files older than 1 day deleted).
Ruling 56: Custom-rule capture is a node scan, not a screenshot (no canTakeScreenshot capability, no new permission). Added Export scan (text share) and a Beta "Screen map" (wireframe from element bounds, tap to select) with a Map | List switch styled like the Privacy / Terms switch.
Not compiled here: manifest provider, modal button, map Canvas, pick/capture screens. Harness: 586 tests green.
Ruling 57: Choose element now has Export in its top bar, Capture again, and a Guide link, plus a "Screen view | Text list" switch (styled like Privacy / Terms) — the screen view is rebuilt from element positions and text. New Screen rules guide route (`custom_rules_guide`, Settings-owned text scale) in the FocusFlow-file-guide format: do-it-yourself steps + copyable AI prompt whose limits come from `CustomScreenRules` constants; the export header points to it. Imported rules stay disabled (existing behaviour).

## Phase 12 — Custom rules behave like Shorts / Reels, My rules screen, WhatsApp, v1.1.6
Ruling 58: Custom screen rules were firing whenever a matching node existed ANYWHERE in the tree (hidden pages, an always-present "Reels" tab button), so a rule closed the app regardless of which screen was open. Matching now ignores nodes that are not visible to the user (`isVisibleToUser`), and a matcher can carry `selectedOnly` (the node must also be `isSelected`). Capture records `selected`, drops invisible nodes, and picking a node captured in its selected state sets `selectedOnly` automatically; the editor has an "Only when selected" chip. `selectedOnly` is optional in JSON (export version stays 1; older builds ignore it). Existing rules keep working but gain the visible-only check.
Ruling 59: Guide + AI prompt now say: open the target screen and select its tab BEFORE capturing; the export marks `selected=true`; the prompt tells the assistant to use `selectedOnly` for always-present tab buttons and to ask for a new capture when the target shows no `selected=true`.
Ruling 60: Custom Screen Rules is a hub (My rules (N), Add from screen, Add manually, Import / export, "Guide (Beta) - How to use"). Rules + switches + `DisableGuardHost` moved to new route `custom_rules_list` (`CustomRulesListScreen`). The editor's `onFinished` returns to My rules if it is on the back stack, otherwise to the hub (dropping capture/pick).
Ruling 61: All custom-rule screens use `contentWindowInsets = RuleScreenInsets` (0) plus `ruleScreenBody()` (`navigationBarsPadding`), the Pick screen's bottom bar pads itself, `RuleButton` is `heightIn(min = 46.dp)` with centred text. Copy-prompt card moved directly under the Beta callout.
Ruling 62: WhatsApp / WhatsApp Business left `NEVER_BLOCK` (where blocks were silently ignored) and joined `BLOCKABLE_AFTER_WARNING` + the picker's sensitive list (warning, then the block applies). Removed from the Always-On and VPN "never block" picker filters.
Ruling 63: Version 1.1.6 (code 17), changelog entry added. `scripts/test_generate_github_release_notes.py` asserts "Improved app-list loading" / "Protection controls", which the 1.1.5 changelog already lacked (stale before this change).
Ruling 64: "Test this rule" (`CustomRuleTestCard`, in the rule editor just above Save: it tests the DRAFT, so it works before saving, and sits after the matchers it checks). One-shot after a 5/8/12 s countdown like Capture. Pure `RuleTester` reports per matcher MATCHED / NOT_SELECTED / HIDDEN / NOT_FOUND and a verdict that is tested to agree with the live `RuleEvaluator` in both ALL and ANY modes. The service reads every node (hidden ones flagged) so a hidden match can be named. A result is shown only while `isFor(rule)` holds (package, mode, matchers unchanged). While a test runs `RuleTestWindow` exempts that rule id from enforcement (countdown + 4 s grace), otherwise an enabled rule would close the app the user is switching to and the test would read the launcher. Wrong app / nothing readable (own app in front) have their own messages.
Ruling 65: Guide, AI prompt and the Beta note now point to Test this rule (guide step 3, the "Test it" tip, agent step 5, a "Deliver" line, and an "If my test fails" section mapping each outcome to a fix). "Switch it on from the Defense tab" was replaced by "My rules".
Ruling 66: `scripts/test_generate_github_release_notes.py` no longer hardcodes old changelog text; it asserts the latest entry's first heading and item.
Not compiled here: all Compose/nav edits, `AppBlockerAccessibilityService` edits, `CustomScreenRuleTest` additions (org.json). Harness: 625 green.
