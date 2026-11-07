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
Expected in the XML (Phase 1+2 only): 11 classes, 142 tests, 0 failures. After Phase 3 the same command should show **17 classes, 269 tests, 0 failures**. Then run the full unit suite once (should equal your baseline + 269 and nothing else changed), and the instrumentation test `FocusFlowDatabaseMigrationTest` on a device/emulator.

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
Expected: compiles (this is the first compile of `AppModule`/worker wiring and of the Room store), 17 classes, 269 tests, 0 failures. Then the full unit suite (baseline + 269) and the commit of `11.json` from Phase 2.

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

## Open items
- `app/schemas/.../11.json` can only be generated by a real Room build (T5). It must be kept and committed after your first compile.
- The Gradle compile, the Phase 1+2 unit tests under Gradle, and the v10 → v11 instrumentation test have not been run by me.
- T8 and T12 wiring (`AppModule`, worker) have never been compiled.
- Phase 4 onwards (T13–T23: insight engine, Stats / Extra UI, docs, device checks) not started. UI tasks cannot be compiled in my environment; each gate will say so.
