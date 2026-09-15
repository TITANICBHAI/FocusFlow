# FocusFlow Kotlin Migration — Completion Plan (Stage 5 Review)

**Reviewed:** `work.zip` (migrated pure-Kotlin project, 163 `.kt` files / ~30,100 lines) against
`reference.zip` (RN + Kotlin hybrid, v1.1.2 / versionCode 13) and all six pipeline documents.

**Method:** structural `fun`/`class`/`override fun`/`const val` counts on every relocated file;
`@ReactMethod` → Kotlin method resolution across all 15 bridge modules; whole-project symbol
resolution of every internal import; receiver-aware resolution of every UI→ViewModel/repository
call; SharedPreferences key-graph resolution across layers (literals **and** cross-class `const val`
references); interactive-element counts per screen; schema diff against `database.ts`.

**Verdict:** the *hard* half of the migration is done and done well. The enforcement engine, the
repository layer, the Room schema design, the analytics engine and the ViewModel contract are all
sound. What remains is a **compile fix, a data-loss fix, three Android-14 crash fixes, six
unwired subsystems, and a large but mechanical UI-fidelity backlog.** Nothing in the remaining work
requires re-doing a completed stage.

---

## 0. Scorecard

| Area | State | Evidence |
|---|---|---|
| Enforcement relocation (20 files) | ✅ Complete | All 20 structurally identical to source. `AppBlockerAccessibilityService` 4,795 ln, `LauncherActivity` 1,926 ln, `ForegroundTaskService` 1,679 ln. VPN policy-generation counter intact. |
| Bridge → repository conversion (15 modules) | ✅ Complete | 107 `@ReactMethod` functions, 1 rename (`setPinHash`), 0 drops. |
| Room schema + migrations | 🟠 Correct but **points at the wrong file path** | §1.2 |
| Analytics engine (Track D) | ✅ Complete | Rules 7/11/8 verified; 10 achievements; `lastSessionAt` added to TS *and* Kotlin; all 5 DAO queries present; `resultRows` + all 3 `fastestWindow*` fields present; UsageStats gate correct. |
| ViewModel wiring | ✅ Complete | 0 unresolved member calls across all UI files. |
| Navigation | ✅ Complete | Every ARCHITECTURE §3.1 route present; 22 `composable()` entries. |
| Notification channels | ✅ Complete | All 6 (3 legacy + 3 new) created. |
| Named risks 1, 3, 6, 8, 10 | ✅ Preserved | Allowance keys match across both implementations; alarm ladder intact; clock-tamper check intact; PBKDF2+Keystore with legacy SHA-256 re-hash path implemented. |
| Compose UI fidelity | 🔴 Systematically thin | §3 |
| Subsystem wiring | 🔴 6 subsystems built but never called | §2 |
| Manifest / Android 14 | 🔴 3 crash-level defects | §1.3–1.5 |
| Tests | 🔴 None exist | §4.6 |

---

## P0 — Blocks compilation

### 0.1 `PresenceStrip.kt:16` does not compile
```kotlin
val attended = snapshot.tasks.byDayOfWeek[day]?.total ?: 0 > 0
```
Elvis binds looser than `>`, so this parses as `Int? ?: (0 > 0)` → inferred type `Any` → `if (attended)`
fails with *"condition must be Boolean"*.
```kotlin
val attended = (snapshot.tasks.byDayOfWeek[day]?.total ?: 0) > 0
```

### 0.2 Run a real build before anything else
The project has never been compiled in this container (no Maven/Google repo access here).
`./gradlew :app:assembleDebug` will surface anything my static pass could not — in particular Room's
KSP schema validation, which is the single most likely source of further errors.

Add while you're in `app/build.gradle.kts` — `exportSchema = true` is set on `@Database` but no
schema directory is configured, so KSP warns and you get no schema JSON to test migrations against:
```kotlin
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
```

---

## P1 — Blocks release

### 1.1 Every existing user loses all their data on upgrade 🔴 **highest severity**

`FocusFlowDatabase.prepareLegacyDatabase()` looks for the hybrid database at:
```kotlin
val dbFile = context.getDatabasePath(DB_NAME)   // /data/data/<pkg>/databases/focusday.db
```
expo-sqlite never wrote there. It writes to `${FileSystem.documentDirectory}SQLite/<name>` →
`/data/data/<pkg>/files/SQLite/focusday.db`.

Result: the existence check fails, the function logs *"fresh install, skipping"*, Room calls
`onCreate()` and builds an empty database. **Tasks, focus sessions, override history, streaks,
daily completions, earned achievements and weekly-insight history are all silently gone** on the
first launch after update.

The migration chain itself (`MIGRATION_0_1` … `MIGRATION_3_4`, the `user_version = 0` stamping, the
`PRAGMA table_info` guard) is well-built and correct — it is simply never reached.

**Fix**, in `prepareLegacyDatabase()` before the existence check:
1. If `getDatabasePath("focusday.db")` exists → current behaviour, proceed.
2. Else if `File(context.filesDir, "SQLite/focusday.db")` exists → copy it **plus its `-wal` and
   `-shm` sidecars** into `getDatabasePath("focusday.db")`, then proceed with the existing
   `user_version` stamping.
3. Else → genuine fresh install.

Two things to get right in the copy:
- **WAL checkpoint.** `database.ts:1302` has `dbCheckpointWal()` precisely because unreplayed WAL
  frames are not in the main `.db`. Copying the `.db` alone can drop the user's most recent writes.
  Copy all three files, or open the source read-write once and `PRAGMA wal_checkpoint(TRUNCATE)`
  before copying.
- **`focusday_recovery.db`.** `database.ts:10-11` defines a recovery database used when the primary
  fails to open (`database.ts:286-349`). A user whose app last ran off the recovery DB has their
  data *only* there. Handle it as a fallback source in step 2.

Then **verify on a real device**: install the published v1.1.2 APK, create tasks and a session,
upgrade to the Kotlin build, confirm the data is there. This cannot be validated by inspection.

### 1.2 `report_notes` changed storage engine → notes lost, weekly rollup broken

`report_notes` is a real SQLite table (`database.ts:543`) with three functions: `dbSaveReportNote`,
`dbGetReportNote`, `dbGetWeekReportNotes`. The Kotlin build moved it to SharedPreferences
(`SettingsRepository.kt:880/883`, key prefix `report_note_`) with no entity, no DAO, and no read of
the old table.

Note this was a **pipeline blind spot, not a Track A mistake** — ARCHITECTURE.md §3.3 lists only 4
tables and never mentions `report_notes`, so Track A was never told about it.

Two acceptable fixes:
- **Preferred:** add `ReportNoteEntity` + `ReportNoteDao` + a `MIGRATION_4_5` that leaves the
  existing table in place (it already exists in every user's DB), and port `dbGetWeekReportNotes`'s
  aggregation — `ReportScreen.kt:108` currently reads a single day note where the TS read the week.
- **Minimum:** keep SharedPreferences but add a one-time read of the SQLite `report_notes` table on
  first launch and copy rows across.

### 1.3 `ForegroundTaskService` crashes on Android 14+ 🔴

```xml
android:foregroundServiceType="dataSync|specialUse"
```
`FOREGROUND_SERVICE_DATA_SYNC` is **not** declared in the manifest. On API 34+, `startForeground()`
with a type the app lacks permission for throws `SecurityException` — the core enforcement service
dies at start, every time.

The reference declared `specialUse` only (`manifest_additions.xml:65`, and the Expo plugin at
`withFocusDayAndroid.js:185`). `dataSync` was added during the migration and buys nothing here.

**Fix:** drop `dataSync`, leaving `android:foregroundServiceType="specialUse"` with the existing
`<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="productivity" />`.
(If you genuinely want `dataSync`, you must also add `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />` — but match the source and don't.)

### 1.4 `NetworkBlockerVpnService` crashes on Android 14+ 🔴

`NetworkBlockerVpnService.kt:163` calls `startForeground(...)`, but its `<service>` block
(`AndroidManifest.xml:121-127`) has **no `android:foregroundServiceType` at all** →
`MissingForegroundServiceTypeException` on API 34+. Network-level blocking never starts.

The Expo plugin explicitly patched this onto the VPN service
(`withFocusDayAndroid.js:518-524`); the hand-written manifest dropped it.

**Fix:** add `android:foregroundServiceType="specialUse"` plus the same `<property>` subtype tag to
the VPN service block.

### 1.5 `accessibility_service_description` was reworded → Play Store policy risk 🔴

ARCHITECTURE §7 lists this string as **port verbatim** because its wording is written to satisfy
Play's accessibility-service data-handling disclosure.

Reference:
> …detecting which app you open. When you start a focus session, any app not on your allowed list is
> immediately redirected back to the home screen.\n\n This service reads only the package name of
> the active window — it cannot …

Current:
> FocusFlow monitors the foreground app during an active focus session and applies the configured
> blocking rules.

The "reads only the package name — it cannot …" disclosure is gone entirely. Restore the original
string byte-for-byte from `reference/android-native/app/src/main/res/values/strings.xml`.

### 1.6 `versionCode` is still 13

`app/build.gradle.kts` carries `versionCode = 13` / `versionName = "1.1.2"` — identical to the
published hybrid. Play will reject the upload. Bump to 14 / "1.2.0" (or your scheme).

### 1.7 Onboarding writes to the wrong SharedPreferences file

`OnboardingScreen.kt:268`:
```kotlin
context.getSharedPreferences("focusflow", 0).edit().putBoolean("onboarding_complete", true).apply()
```
Every other component in the app — enforcement, repositories, widget — uses `focusday_prefs`. This
is the only `"focusflow"` reference in the project. Onboarding completion is written somewhere
nothing reads, so onboarding will re-show.

This also intersects **Risk 4** (onboarding flags reconciled across SharedPreferences /
AsyncStorage / SQLite in `AppContext.init()`). There is currently **no one-time migration read** of
the legacy onboarding/privacy flags, so upgrading users get re-onboarded regardless. Write both:
the correct prefs file, and a first-launch reconciliation read.

### 1.8 Launcher wallpaper key mismatch

`SettingsRepository.setLauncherWallpaperUri()` writes `"launcher_wallpaper_uri"`.
`LauncherActivity.kt:78/1744` reads `PREF_LAUNCHER_WALLPAPER = "launcher_wallpaper"`.
The custom launcher wallpaper the user picks is never applied.

Also: `launcher_pinned_packages` (`LauncherActivity.kt:76`) has **no writer anywhere** in the
Kotlin project — pinned apps can be read by the launcher but never set from the setup screen.

Both constants are `private` in `LauncherActivity`, which is why the repository couldn't reference
them the way it correctly does for `AppBlockerAccessibilityService.PREF_*`. Promote them to
`internal`/`const val` in a companion and have `SettingsRepository` reference the constant, not a
string literal — that's the pattern that kept the other 40+ keys correct.

---

## P2 — Built but never wired (dead subsystems)

Each of these is finished code with zero call sites. They are cheap to fix and each one is a
user-visible feature that currently does not exist.

| Subsystem | Lines | Status | Wire-up |
|---|---|---|---|
| `SchedulerEngine.kt` | 443 | **Never called** | ARCHITECTURE §3.2 requires `addTask`/`updateTask`/`deleteTask` to re-run the conflict check "matching current behavior". `TaskViewModel` doesn't. Task conflict detection *and* auto-rebalancing are gone from the app. Call it from `TaskViewModel.add/update/delete`. |
| `NotificationRepository.kt` | 598 | **Never instantiated** | The entire analytics-driven notification content system (Track D Prompt 4). Add to `AppModule.init()`, then call `buildMorningDigestBody()` / `buildWeeklyReportBody()` / `buildWeekAheadBody()` from the alarm/service scheduling path. |
| `BackgroundFetchWorker.kt` | 147 | **Never enqueued** | No `WorkManager.enqueueUniquePeriodicWork` anywhere. The 15-minute `BACKGROUND_FETCH` reconciliation the hybrid ran does not run. Enqueue in `FocusFlowApp.onCreate()` with `ExistingPeriodicWorkPolicy.KEEP`. |
| Standalone-block expiry check | — | **Missing** | ARCHITECTURE §7 says the 30-second `AppContext` tick becomes "a WorkManager or repeating-alarm-driven check". Nothing implements it. An expired standalone block stays enforced until something else happens to run. |
| `import-confirm` screen | 335 (TS) | **No Kotlin counterpart** | `app/import-confirm.tsx` appears in **no stage prompt**. The backup-import confirmation flow is unreachable; `BackupCoordinator` has no confirmation UI to hand off to. |
| `VpnRepository` / 6 other repos | — | **Not in `AppModule`** | `MainActivity:55` creates its own `VpnRepository` instance; `InstalledAppsRepository`, `NuclearModeRepository`, `LauncherController`, `AversionsController`, `BackupManager`, `UsageStatsRepository` are constructed ad hoc at 20+ call sites. `UsageStatsRepository(context)` is constructed **inside composable bodies without `remember`** at `PermissionSupport.kt:47/48/76-79`, `RestrictedSettingsBanner.kt:45/86`, `AccessibilityRestrictedRecovery.kt:70/71/96/100/174/186` — a new instance on every recomposition. VpnRepository holding policy state in two instances is a correctness risk, not just a performance one. Move all of them into `AppModule`. |

**Stale comments to delete** once verified: `FocusSessionRepository.kt:65/70/89` and
`AppModule.kt:85` still carry `TODO (Track C)` notes about mirroring enforcement prefs and pushing
widget updates. `FocusSessionViewModel.startFocusMode()` (lines 195-250) **does** both correctly.
The TODOs are misleading — remove them so the next reader doesn't "fix" working code.

---

## P3 — UI fidelity backlog

I counted interactive elements (buttons, toggles, text inputs, sliders, modal triggers, navigation
actions) in each TS source and its Compose counterpart. A shortfall here is a dropped feature, not
a style choice — the enforcement logic still works, the control that reaches it is gone.

### Tier A — more than half the interactions missing

| Screen | TS src | Elements found → matched | Notes |
|---|---:|---|---|
| `defense.tsx` → `DefenseScreen.kt` | 938 | **50 → 11** | Worst in the app. Core defense configuration surface. |
| `user-profile.tsx` → `UserProfileScreen.kt` | 1,108 | **43 → 15** | Also carries two `NEEDS:` markers (lines 148, 227). |
| `TaskDetailModal.tsx` | 166 | **10 → 2** | |
| `StatsInsightsExperience.tsx` | 648 | **6 → 2** | See Tier A-stats below. |
| `EditTaskModal.tsx` | 537 | **31 → 12** | |
| `TroubleshootModal.tsx` | 469 | **10 → 4** | |
| `onboarding.tsx` → `OnboardingScreen.kt` | 1,126 | **22 → 9** | First thing every install sees. |
| `permissions.tsx` → `PermissionsScreen.kt` | 903 | **14 → 6** | |
| `QuickAddModal.tsx` | 572 | **32 → 14** | Carries `NEEDS:` for pomodoro fields + allowed-app presets. |
| `PinVerifyModal.tsx` | 316 | **11 → 5** | Security-relevant — a dropped confirmation step here matters. |

### Tier A-stats — the whole `ui/stats/` family is skeleton-grade

`src/components/StatsInsightsExperience.tsx` is 648 lines. The entire Kotlin `ui/stats/` package is
793 lines *including* `StatsViewModel` and `ReportScreen`. The sub-components:

| File | Lines | Problem |
|---|---:|---|
| `UnavailableGate.kt` | 17 | |
| `EmptyStatsState.kt` | 21 | |
| `PermissionGate.kt` | 23 | |
| `PresenceStrip.kt` | 24 | **+ compile error (P0.1)** |
| `InsightCardView.kt` | 25 | No padding, no spacing, no `Modifier` |
| `LocalOnlyNotice.kt` | 25 | |
| `AchievementRow.kt` | 26 | Not a row — no horizontal layout |
| `TaskResultList.kt` | 26 | |
| `TaskSummary.kt` | 28 | |
| `TrendChart.kt` | 34 | Raw `Canvas`, no axes, labels, or empty state |
| `PhoneUsageSummary.kt` | 36 | |
| `DataHealthNotice.kt` | 37 | |

The *logic* is right — window gating, `nothing_to_report` → `"OBSERVATION"`, `PermissionGate` vs
`UnavailableGate` kept distinct, `LocalOnlyNotice` dismiss persisted. What is missing is every
`Modifier.padding`, `Arrangement.spacedBy`, `Card` elevation and layout decision. These will render
as unstyled stacked text. The Stage 4 rule "no hardcoded `dp`" was read as "no spacing at all."

Budget this as a rebuild of the stats screen against the 648-line source, not a polish pass.

### Tier B — thin (50-75% coverage)

`focus.tsx` (52→34) · `StandaloneBlockModal.tsx` (50→27) · `GreyoutScheduleModal.tsx` (33→24) ·
`DailyAllowanceModal.tsx` (31→22) · `AccessibilityRestrictedRecovery.tsx` (22→14) ·
`PinRotationModal.tsx` (30→19) · `QuickBlockSheet.tsx` (17→9) · `BlockedWordsModal.tsx` (15→11) ·
`privacy-policy.tsx` (21→14) · `ErrorFallback.tsx` (10→7) · `how-to-use.tsx` (8→5) ·
`report.tsx` (6→4) · `ExtendModal.tsx` (4→2) · `VpnConsentModal.tsx` (4→2) ·
`RestrictedSettingsBanner.tsx` (4→2) · `terms-of-service.tsx` (6→4) ·
`VpnPermissionLostBanner.tsx` (2→1)

### Tier C — invented, not ported

Four files the Stage 4 prompt instructed GPT Terra to port **do not exist in `reference.zip`**:
`TimelineView.tsx`, `block-defense.tsx`, `KeyboardAwareScrollViewCompat.tsx`,
`BlockedAppOverlay.tsx`. Compose versions were invented instead. You need to decide, per file,
whether the source was lost or never existed:

- `TimelineView.kt` (37 ln) — invented from "scheduling intent"; `HomeScreen` renders it.
- `StandaloneBlockSetupScreen.kt` (85 ln) — invented; it is the `block_defense` route target.
- `KeyboardAwareScrollViewCompat.kt` (28 ln) — a reasonable `imePadding()` replacement; fine as-is.
- `BlockedAppOverlay.kt` (49 ln) — 🔴 **renders a literal developer string to the user**:
  `"NEEDS: original BlockedAppOverlay.tsx source and native dismissal actions."` (line 44).
  Remove that `Text` before any build ships, regardless of what else you do with the file.

### Tier D — good coverage, spot-check only

`index.tsx` (16→14) · `active.tsx` (22→21) · `TaskCard.tsx` (9→10) · `settings.tsx` (29→34) ·
`keyword-blocker.tsx` (10→10) · `AppPickerSheet.tsx` (26→27) · `vpn-block-list.tsx` (13→18) ·
`always-on.tsx` (15→22) · `home-launcher.tsx` (31→24) · `NuclearModeModal.tsx` (5→5) ·
`DiagnosticsModal.tsx` (11→10) · `password-protection.tsx` (8→7) · `PinSetupModal.tsx` (22→18)

---

## P4 — Infrastructure, theming, hardening

### 4.1 No theme package
There is no `ui/theme/` at all — no `Color.kt`, `Type.kt`, or `Theme.kt`, contrary to ARCHITECTURE
§4. The color scheme is inlined in `MainActivity`, typography is Material defaults, and
`src/styles/theme.ts` (89 lines) was never ported. Create the package and port the design tokens.

### 4.2 Dark mode is non-functional
- `darkMode` is one of the 13 `AppSettings` fields with **no trace anywhere** in the Kotlin project.
- `DarkModeToggle.kt` is 30 lines against a 122-line source.
- `res/values/themes.xml` hardcodes `android:windowLightStatusBar="true"` and
  `android:windowLightNavigationBar="true"` unconditionally, and sets `statusBarColor` /
  `navigationBarColor` to the **light** background colour. In dark mode the status-bar icons will be
  light-on-light and invisible.
- There is no `values-night/` variant.

Fix: add `darkModeEnabled` → persisted setting → `MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme())`, plus `values-night/themes.xml` and a `WindowCompat.getInsetsController(...).isAppearanceLightStatusBars` toggle.

### 4.3 The 13 settings fields with no counterpart anywhere
```
darkMode                          defaultDuration              defaultReminderOffsets
protectionMode                    lastShownStreakMilestone     pendingAchievementCelebration
threeMonthPrivacyNoticeDismissed  keepFocusActiveUntilTaskEnd  autoRescheduleEnabled
beginnerMode                      tipsCardDismissed            tipsCardFirstShownAt
pendingPresets
```
Each is a feature that silently no longer exists: streak-milestone celebration gating, the
achievement celebration queue, the beginner-mode UI, the tips card, the "keep focus active until
task end" behaviour, and auto-reschedule.

**Backup impact:** `darkMode`, `defaultDuration` and `defaultReminderOffsets` are all present in the
`settings` block of real `.focusflow` backup files. `BackupManager` parses the `FocusFlowBackupV1`
envelope faithfully (verified: `kind`, `version`, `exportedAt`, `exportedAtHuman`, `appVersion`,
`platform`, `summary` all match), but any key absent from `AppSettings.kt` is dropped on the next
settings write. Either add the fields, or make the settings store preserve unknown keys verbatim.

### 4.4 ViewModels are not lifecycle-scoped
`MainActivity` builds all five ViewModels with `remember { }`, not `viewModel()`. They are
recreated on every configuration change, `onCleared()` never runs, and `viewModelScope` coroutines
leak. `FocusSessionViewModel.onCleared()` unregisters a SharedPreferences listener that therefore
never gets unregistered.

Separately, **21 composables declare `= viewModel()` defaults** for ViewModels that all require
constructor arguments (`HomeScreen.kt:52-55`, `SettingsScreen.kt:47-50`, `FocusScreen.kt:53-55`,
`ActiveBlockScreen.kt:50-52`, `StatsInsightsExperience.kt:36`, and others). Any call site that omits
the argument throws *"Cannot create an instance of class …"* at runtime. Today `MainActivity` always
passes them explicitly, so this is latent — but it is a trap for the next screen anyone adds.

Fix both with a single `ViewModelProvider.Factory` backed by `AppModule`, then use `viewModel(factory = …)` everywhere and delete the defaults.

### 4.5 Never-assigned source files
These appear in **no stage prompt** and have no Kotlin equivalent. Decide per file: port, or record
as a deliberate drop.

**Services:** `taskService.ts` (208) · `focusService.ts` (176) · `startupLogger.ts` (357) ·
`diagnosticsReporter.ts` (128) · `allowanceUsageCache.ts` (111) · `setupPersistence.ts` (96) ·
`pendingBackupImport.ts` (34) · `protectedApps.ts` (31) · `taskOperationQueue.ts` (25) ·
`eventBridge.ts` (104, likely obsolete) · `insightEngine.ts` (189, legacy — superseded by
`analytics/InsightEngine.ts`, which *was* correctly ported; confirm before deleting)

⚠️ **`protectedApps.ts` is safety-relevant** — it is the hardcoded never-blockable package
allowlist (Phone, Settings, etc.). Without it the app can block the dialer or lock a user out of
system settings. Port this one first.

**Utils:** `recurringScheduleUtils.ts` (44) · `withTimeout.ts` (24) · `weekUtils.ts` (11) ·
`nav.ts` (17) · `navigationRef.ts` (50)
**Hooks:** `usePomodoro.ts` (174) · `useTimer.ts` (61) · `useTheme.ts` (39) · `useNavPress.ts` (34)
**Layouts:** `app/_layout.tsx` (551 — global overlays, deep-link handling, notification-response
listener, `navigateToTask`/`consumePendingTaskNavigation`) · `app/(tabs)/_layout.tsx` (166 — the
bottom-nav scaffold / `MainScaffold`) · `app/+not-found.tsx`

`withTimeout.ts` matters specifically for **Risk 7**: `AppContext.init()` had an 8-second
settings-load timeout that fell back to `DEFAULT_SETTINGS`. Check whether `AppBootViewModel`
reproduces that fallback; if not, a malformed settings blob is now a boot hang rather than a
graceful degrade.

### 4.6 No tests exist
There is no `app/src/test/` or `app/src/androidTest/` directory. `build.gradle.kts` declares JUnit,
`kotlinx-coroutines-test`, `room-testing`, Espresso and Compose UI test deps — all unused.

ARCHITECTURE §3.4 explicitly says to preserve `BlockedAppDismissalPolicyTest.kt` (105 lines, present
in `reference.zip`). It was not carried over.

Minimum viable suite:
1. Port `BlockedAppDismissalPolicyTest.kt` verbatim.
2. A Room migration test (`MigrationTestHelper`) that opens a v0 `focusday.db` fixture from the
   *expo path* and asserts the rows survive — this is what would have caught §1.1.
3. `SchedulerEngine` unit tests — it is pure logic with zero Android dependency, exactly as the
   pipeline intended, and currently has no coverage and no callers.
4. `PinManager` legacy-upgrade test: store a SHA-256 hash, verify, assert it was rewritten to v2.
5. `AnalyticsProcessor` arithmetic tests against the TS original — Stage 3 required 2-decimal-place
   parity, which nothing currently verifies.

### 4.7 Resources and packaging
- `drawable/widget_add_task_bg.xml` missing (present in reference).
- Launcher icon is a generic lock glyph (`ic_lock_lock.xml`). The app's real icon
  (`assets/images/icon.png`, 665 KB) was never brought over. Generate proper adaptive-icon
  foreground/background assets.
- `file://` deep-link intent filters for `.focusflow` import are missing. Reference
  `manifest_additions.xml:215-223` has both `scheme="file"` variants; the Kotlin manifest has only
  `content://`. Importing a backup handed over as a `file://` URI by a file manager will fail.
- No `gradle/libs.versions.toml` — ARCHITECTURE §5 specified a version catalog; versions are
  hardcoded inline instead. Cosmetic, but worth doing before the dependency list grows.
- `isMinifyEnabled = false` and `proguard-rules.pro` is a single comment line. If you ever enable
  R8 you will need keep rules for `kotlinx.serialization` and Room. Fine to defer — just don't flip
  the flag without writing them.
- No `signingConfigs` block.

### 4.8 Hardening: overlay permission guard
Stage 2 Prompt 3 told Replit to preserve a `Settings.canDrawOverlays()` check in
`BlockOverlayController`, and ARCHITECTURE §3.11 states the overlay is "gated by
`Settings.canDrawOverlays()` before every overlay show, per `BlockOverlayModule`".

**That claim is wrong about the source** — the reference `BlockOverlayModule.kt` contains no such
check. So this is not a regression. But it is still a real gap: launching `BlockOverlayActivity`
without the permission fails silently, and the block simply doesn't appear. Add the guard (with a
user-facing prompt on failure) as a deliberate improvement.

---

## 5. Suggested order of work

1. **P0.1** compile fix, then `./gradlew assembleDebug` — get a green build before anything else.
2. **P1.3, P1.4** manifest FGS fixes — without these nothing runs on a modern device, so every
   subsequent test would be invalid.
3. **P1.1** the expo→Room database path fix, with a real device upgrade test. Highest severity item
   in this document.
4. **P1.5, P1.6, P1.7, P1.2, P1.8** — the remaining release blockers.
5. **P2** wire the six dead subsystems. Each is small and each restores a whole feature.
6. **P4.4** ViewModel factory — do this before UI work, because the UI backlog will otherwise
   propagate the `= viewModel()` pattern further.
7. **P4.2 / P4.1** theme + dark mode — also before UI work, so screens are rebuilt against real
   tokens.
8. **P3 Tier A**, stats family first (it is closest to a rebuild), then Defense, User Profile,
   Onboarding, Permissions.
9. **P4.5** decide port-or-drop on the 25 unassigned files. `protectedApps.ts` first.
10. **P3 Tier B**, then **P4.6** tests, then **P4.7** packaging.

---

## 6. Two corrections to the pipeline documents

Worth fixing before anyone runs these prompts again:

1. **ARCHITECTURE.md §3.3** lists 4 Room tables (+2 new). The real schema has **7**: it omits
   `report_notes` entirely. This is what caused §1.2.
2. **STAGE3 Track D Prompt 3** says `InsightEngine.ts` is 189 lines. That is
   `src/services/insightEngine.ts` — a *different, legacy* file. The correct source,
   `src/services/analytics/InsightEngine.ts`, is 84 lines. The port used the right file anyway, but
   the instruction pointed at the wrong one.
3. **ARCHITECTURE.md §3.11** claims `BlockOverlayModule` gates on `canDrawOverlays()`. It does not
   (see §4.8).
4. **STAGE4 Prompt 1/5/11** list four source files that do not exist in this snapshot
   (`TimelineView.tsx`, `block-defense.tsx`, `KeyboardAwareScrollViewCompat.tsx`,
   `BlockedAppOverlay.tsx`) and omit one that does (`app/import-confirm.tsx`, 335 lines).
