# FocusFlow — Full Parity Audit (Pass 2)

**Question asked:** is `work.zip` a complete, faithful pure-Kotlin equivalent of the `reference.zip`
hybrid?

**Answer:** **No — not yet.** The *ported* code is excellent: where a file was carried across, it is
almost always faithful down to the function. The failures are almost entirely at the **seams** —
things that were built correctly but never connected, things the pipeline never assigned to anyone,
and three resource/config files that were rewritten rather than copied.

This pass ran **12 new check categories** on top of pass 1. Net new findings: **21**, of which
**3 are compile errors**, **1 is a second data-loss path**, and **2 are Play-policy violations**.

---

## Verdict table

| | Count |
|---|---|
| Compile errors | **4** (1 from pass 1 + 3 new) |
| Data-loss-on-upgrade paths | **3** |
| Android 14 crash paths | **2** |
| Play-policy violations | **3** |
| Complete subsystems with zero call sites | **7** |
| Features silently absent | **6** |
| Behavioural/default divergences | **9** |
| Checks that came back clean | **31** |

---

# PART 1 — What is verifiably correct

Do not re-do any of this. Each was checked mechanically, not by eye.

| Check | Result |
|---|---|
| 20 enforcement files — `fun`/`class`/`override fun`/`const val` counts | ✅ exact match, all 20 |
| `Task` — 15 fields, types **and** nullability | ✅ identical, field for field |
| `FocusSession` — 4 fields | ✅ identical |
| `SchedulerEngine.ts` — all 8 exports | ✅ all ported (`detectConflicts`, `findNextAvailableSlot`, `rebalanceAfterOverrun`, `insertTaskSafe`, `compressSchedule`, `compressDeletedTaskGap`, `getUnfinishedOverdueTasks`, `analyzeScheduleHealth`) |
| `AnalyticsProcessor.ts` — 3 exports | ✅ all ported |
| `notificationService.ts` — all 16 exports | ✅ all ported into `NotificationRepository` |
| Insight rule IDs — literal comparison, all 3 files | ✅ byte-identical sets |
| `weekSeed()` algorithm | ✅ faithful (`floorDiv(ms, 7d)`), plus extra date-format tolerance |
| `renderInsightVariant` | ✅ 4 params — the `values` placeholder arg was not dropped |
| PIN crypto | ✅ legacy `SHA-256`, new `PBKDF2WithHmacSHA256` @ 310 000 iterations |
| 107 `@ReactMethod` bridge functions | ✅ 106 exact-name matches, 1 rename |
| Room indices — all 6 | ✅ declared via `@Entity(indices=…)` with matching names |
| `WeeklyInsightDao` / `AchievementDao` | ✅ read **and** write present; dedup persists |
| `AppContext` public actions | ✅ 15/16 (`refreshTasks` correctly dropped — Room `Flow` supersedes it) |
| `isDbUnrecoverable` + orphaned-session recovery | ✅ both implemented (`AppBootViewModel`, `FocusScreen:123`) |
| UI → ViewModel call resolution | ✅ 0 unresolved members across all 163 files |
| Internal import resolution | ✅ 0 broken |
| Manifest components | ✅ all 16 present; attributes match except 3 noted below |
| Intent-filter actions | ✅ no action in reference missing from work |
| `runBlocking` on UI path | ✅ none |
| `GlobalScope` | ✅ none |
| PIN / hash / salt logging | ✅ none |
| `PendingIntent` mutability flags | ✅ all 19 sites explicit |
| Duplicate declarations | ✅ all in distinct packages — not compile errors |
| Named Risk 1 (dual allowance) | ✅ both implementations use matching keys |
| Named Risk 3 (VPN generation counter) | ✅ intact |
| Named Risk 6 (alarm ladder) | ✅ all 3 rungs, `canScheduleExactAlarms()` gate intact |
| Named Risk 8 (clock tamper) | ✅ intact |
| Named Risk 10 (PIN upgrade) | ✅ implemented |
| SharedPreferences key graph | ✅ repositories correctly reference `AppBlockerAccessibilityService.PREF_*` constants rather than duplicating literals — better than the source |

---

# PART 2 — Compile errors (4)

| # | File:line | Error |
|---|---|---|
| 1 | `ui/stats/PresenceStrip.kt:17` | `?: 0 > 0` — elvis binds looser than `>`, infers `Any`, `if` fails. Wrap: `(… ?: 0) > 0` |
| 2 | `ui/stats/StatsInsightsExperience.kt:75` | `weeklyStandout?.let(::InsightCardView)` |
| 3 | `ui/stats/StatsInsightsExperience.kt:80` | `.forEach(::InsightCardView)` |
| 4 | `ui/stats/StatsInsightsExperience.kt:92` | `achievements?.let(::AchievementRow)` |

**2–4:** the Compose compiler does not support **function references to `@Composable` functions**.
Confirmed that all 9 stats sub-components are annotated `@Composable`. Replace with lambdas:
```kotlin
weeklyStandout?.let { InsightCardView(it) }
insights.filter { … }.forEach { InsightCardView(it) }
achievements?.let { AchievementRow(it) }
```
(The other 7 `::` references in the codebase — `::hourLabel`, `::legacyPinHash`, `::parseTime`,
`::parseInstant`, `::JSONArray`, `::parseSchedules` — are plain functions and are fine.)

---

# PART 3 — Data loss on upgrade (3 paths)

### 3.1 Room opens the wrong directory *(pass 1, restated — still the top item)*
`prepareLegacyDatabase()` checks `getDatabasePath("focusday.db")`; expo-sqlite wrote to
`filesDir/SQLite/focusday.db`. Tasks, sessions, overrides, streaks, completions, achievements — all
gone. Fix requires copying the file **plus `-wal`/`-shm`**, and handling `focusday_recovery.db`.

### 3.2 The legacy SQLite `settings` blob is never read 🆕
`database.ts:981` reads `SELECT value FROM settings WHERE key = 'app_settings'` — the entire
64-field `AppSettings` JSON. **`dbGetSettings` has no Kotlin counterpart**, and nothing anywhere
reads that table.

Precise impact — this splits in two:
- **Survives:** anything the hybrid's `_sync*` functions mirrored into `focusday_prefs` (block
  lists, allowance config, always-block, blocked words, greyout schedule, VPN policy). The Kotlin
  app reads those with the same key names, so enforcement config carries over.
- **Lost:** everything that lived *only* in the SQLite blob — `darkMode`, `defaultDuration`,
  `defaultReminderOffsets`, `userProfile`, `blockPresets`, `weekStartDay`, `protectionMode`,
  `beginnerMode`, `tipsCard*`, `weeklyReportEnabled`, `notificationsEnabled`, onboarding state.

**Fix:** a one-time read of the legacy `settings` row during `prepareLegacyDatabase`, merged into
SharedPreferences before first use.

### 3.3 `report_notes` *(pass 1)*
Moved SQLite → SharedPreferences with no migration read. `dbGetWeekReportNotes` has no counterpart
at all, so the weekly rollup of daily notes is gone as a feature, not just as data.

---

# PART 4 — Play-policy violations (3)

### 4.1 `accessibility_service_config.xml` was rewritten, not ported 🆕 🔴
This is the most serious new finding. It is simultaneously a policy problem **and** a broken feature.

| Attribute | Reference | Work | Consequence |
|---|---|---|---|
| `accessibilityEventTypes` | `typeWindowStateChanged\|typeViewTextChanged\|typeWindowContentChanged` | **`typeAllMask`** | Requests *every* accessibility event — scrolls, clicks, focus changes. Large battery/CPU cost, and a far broader data-access surface than Play's accessibility policy tolerates. |
| `accessibilityFlags` | `flagIncludeNotImportantViews\|flagReportViewIds` | `flagDefault\|flagRetrieveInteractiveWindows\|flagRequestFilterKeyEvents` | **`flagReportViewIds` dropped** → `getViewIdResourceName()` returns null → **YouTube Shorts / Instagram Reels resource-ID matching silently stops working** (ARCHITECTURE §6 Risk 2). **`flagIncludeNotImportantViews` dropped** → URL-bar and text scanning loses coverage. **`flagRequestFilterKeyEvents` added** → requests key-event interception the app never needed. |
| `canPerformGestures` | *(absent)* | `true` | Another capability expansion not in source. |

And it contradicts §4.2 below: the description now claims the service only reads the foreground app,
while the config requests everything. A reviewer comparing the two will fail the submission.

**Fix:** restore the reference file verbatim. The only defensible addition is `settingsActivity`.

### 4.2 `accessibility_service_description` reworded *(pass 1)*
The "reads only the package name of the active window — it cannot …" disclosure was deleted.

### 4.3 `device_admin.xml` adds three excluded policies 🆕 🔴
The reference file carries an explicit comment: *"Intentionally NOT declared: reset-password /
limit-password / watch-login — Not needed."* ARCHITECTURE §7 repeats it: *"force-lock policy only —
do not add … any policy beyond what's declared."*

The work version adds:
```xml
<limit-password />
<watch-login />
<disable-keyguard-features />
```
`watch-login` means the app is notified of failed device-unlock attempts. For a productivity app
that is both unjustifiable to a reviewer and a privacy expansion the source deliberately refused.

**Fix:** reduce to `<force-lock />` only.

---

# PART 5 — Android 14 crash paths (2) *(pass 1)*

- `ForegroundTaskService`: `dataSync|specialUse` declared without `FOREGROUND_SERVICE_DATA_SYNC` →
  `SecurityException`. Reference used `specialUse` alone.
- `NetworkBlockerVpnService`: calls `startForeground()` with **no** `foregroundServiceType` →
  `MissingForegroundServiceTypeException`.

**Also worth testing** (pre-existing, *not* a migration regression — identical in the reference):
`AppBlockerAccessibilityService:938` calls `registerReceiver()` with no
`RECEIVER_NOT_EXPORTED` flag on a filter containing a custom app action. On API 34+ that throws.
`TaskAlarmActivity:110` does it correctly, so the pattern is known — this one site was never
updated in either codebase. Verify on a real Android 14 device before release.

---

# PART 6 — Complete subsystems with zero call sites (7)

Each is finished, correct code that nothing ever invokes.

| Subsystem | Lines | What is dark |
|---|---:|---|
| `NotificationRepository` 🆕 | 598 | **All 16 `notificationService.ts` exports are ported here.** Nothing constructs it. So: task reminders (pre-start −10/−5/−1/0, mid-session, almost-done, overrun), standalone-block expiry, morning digest, weekly report and late-start warning **never fire**. The only notifications the app posts are the ones the enforcement services build directly. This is the single largest functional gap in the build. |
| `SchedulerEngine` | 443 | Conflict detection, rebalancing, compression, schedule-health. ARCHITECTURE §3.2 requires `TaskViewModel.add/update/delete` to call it. They don't. |
| `BackgroundFetchWorker` | 147 | Never enqueued. The 15-minute reconciliation job does not run. |
| `AversionsController` 🆕 | 69 | **Zero call sites.** `AversiveActionsManager` itself *is* wired (fires from the accessibility service on a blocked-app hit), but the settings→enforcement push is missing, so the dimmer/vibrate/sound toggles in the UI never reach it. `_syncAversions` has no equivalent anywhere. |
| `VpnRepository` et al. | — | Not in `AppModule`; `MainActivity:55` creates a second instance. `UsageStatsRepository(context)` is constructed inside composable bodies without `remember` at 4 sites. |
| `EnforcementSyncManager` | 0 | ARCHITECTURE §3.2 specified this class. It does not exist; the 8 `_sync*` behaviours are scattered across `SettingsRepository`/`SettingsViewModel`, with 2 of the 8 having no home at all. |
| `import-confirm` flow | 335 (TS) | See §7.2. |

---

# PART 7 — Features silently absent (6)

### 7.1 `UserProfile` has no model and no persistence 🆕
`types.ts:103` defines 13 fields (`name`, `occupation`, `dailyGoalHours`, `wakeUpTime`, `sleepTime`,
`chronotype`, `focusSessionLength`, `breakStyle`, `focusGoals`, `distractionTriggers`,
`motivationStyle`, `weeklyReviewDay`). In Kotlin:
- no `data/model/UserProfile.kt` (ARCHITECTURE §4 listed it)
- no `userProfile` key anywhere in `SettingsRepository`
- `UserProfileScreen.kt` (565 lines) has nowhere to save to
- `NotificationRepository` defines a *local* `NotificationUserProfile` that nothing populates
- the `userProfile` object in a real `.focusflow` backup is parsed and discarded

### 7.2 `PendingPresets` — zero references 🆕
`types.ts:311` defines a four-part import payload (`blockApps`, `dailyAllowance`, `deterrents`,
`enforcement`). **Not one reference anywhere in the Kotlin project.** Combined with the missing
`import-confirm.tsx` screen, this means the entire "import someone else's preset bundle" feature
does not exist. This is a whole user-facing feature, not a screen.

### 7.3 `BlockPreset` has no persistence 🆕
`BackupManager:286` parses `blockPresets` out of a backup, but `SettingsRepository` only persists
`allowed_app_presets` (mapped to `launcherPresets`). Block presets are read and thrown away.

### 7.4 `GreyoutWindow` has no model 🆕
ARCHITECTURE §4 listed `data/model/GreyoutWindow.kt`. Absent — greyout JSON is hand-built in
`GreyoutScheduleModal.kt` (302 lines vs an 892-line source) and hand-parsed in the accessibility
service.

### 7.5 Five `db*` functions have no counterpart 🆕
`dbGetWeekReportNotes` · `dbGetRecentDayCompletions` (feeds completion-history charting) ·
`dbGetAllTimeFocusMinutes` · `dbDeleteAllTasksExcept` · `dbCheckpointWal`.

`dbCheckpointWal` deserves its own note: `database.ts:1291-1296` explains it exists because Android
Auto Backup copies the `.db` without unreplayed WAL frames. You need it for the §3.1 migration fix
too.

### 7.6 The 13 orphaned `AppSettings` fields *(pass 1)*
`darkMode`, `defaultDuration`, `defaultReminderOffsets`, `protectionMode`,
`lastShownStreakMilestone`, `pendingAchievementCelebration`, `threeMonthPrivacyNoticeDismissed`,
`keepFocusActiveUntilTaskEnd`, `autoRescheduleEnabled`, `beginnerMode`, `tipsCardDismissed`,
`tipsCardFirstShownAt`, `pendingPresets`.

---

# PART 8 — Behavioural & default divergences (9)

### 8.1 Two defaults were flipped 🆕
| Setting | TS default | Kotlin default | Effect |
|---|---|---|---|
| `focusModeEnabled` → `autoFocusEnabled` | `true` | **`false`** | Focus mode is off by default for every new install. Core behaviour change. |
| `darkMode` → `darkModeEnabled` | `Appearance.getColorScheme() === 'dark'` | **`true`** | New installs force dark regardless of system setting. |

(`lastShownDebriefSessionId: undefined → null` is a correct idiom translation.)

### 8.2 `widget_info.xml` regressions 🆕
| Attribute | Reference | Work |
|---|---|---|
| `minHeight` | `50dp` | `100dp` — widget now occupies double the height |
| `targetCellWidth` / `targetCellHeight` | `4` / `1` | **dropped** — Android 12+ sizing hints gone |
| `minResizeWidth` / `minResizeHeight` | `180dp` / `50dp` | **dropped** |
| `resizeMode` | `horizontal` | `horizontal\|vertical` |
| preview | `previewLayout=@layout/widget_focusflow` | `previewImage=@mipmap/ic_launcher` — a static launcher icon instead of a live layout preview |

`updatePeriodMillis` is unchanged. ✅

### 8.3 `TaskAlarmActivity` theme changed 🆕
`@android:style/Theme.NoTitleBar.Fullscreen` → `@android:style/Theme.DeviceDefault.NoActionBar.Fullscreen`.
Minor, but this is the full-screen alarm surface; verify it still renders edge-to-edge.

### 8.4 `TaskStatus` / `TaskPriority` / `ReminderType` are `typealias X = String` 🆕
Faithful to a TS string union, but it gives up all compile-time safety. A typo in a status string
writes a row that no query matches and no compiler catches. Sealed classes or enums with a Room
`TypeConverter` would be a genuine improvement over the source here.

### 8.5 `Reminder` is declared twice 🆕
`data.model.Reminder` and `domain.Reminder` (`SchedulerEngine.kt:372`). Different packages, so it
compiles — but task reminders can't round-trip between the scheduler and the data layer without a
manual conversion that doesn't exist.

### 8.6 No `<queries>` element 🆕
The reference declares package visibility via the Expo plugin. The Kotlin manifest relies solely on
`QUERY_ALL_PACKAGES`, which Play requires a separate policy declaration for and increasingly
rejects. Add a `<queries>` block for the intents you actually resolve.

### 8.7 Launcher wallpaper key mismatch *(pass 1)*
`launcher_wallpaper_uri` written, `launcher_wallpaper` read. `launcher_pinned_packages` has no
writer at all.

### 8.8 Onboarding writes to a different prefs file *(pass 1)*
`getSharedPreferences("focusflow")` vs `focusday_prefs` everywhere else.

### 8.9 ViewModels not lifecycle-scoped *(pass 1)*
`remember { }` instead of `viewModel()`; 21 composables carry `= viewModel()` defaults that would
crash if ever used.

---

# PART 9 — Code-quality observations

None of these block release. They are the things a reviewer would raise.

| Finding | Count | Note |
|---|---:|---|
| Hardcoded user-facing strings in `ui/` | **546** | `strings.xml` holds 4 entries. No translation, no RTL, no per-device string overrides possible. The hybrid had the same problem, so it is parity — but the migration was the moment to fix it. |
| Hardcoded `.dp` literals in `ui/` | **258** | Stage 4's rule was "no hardcoded `dp`". The stats package went the other way and uses *no* spacing at all (§Tier A-stats in the completion plan). Both extremes are wrong; a spacing token object fixes both. |
| Hardcoded `Color(0x…)` / `Color.Red` etc. | 6 | Small enough to fix in one pass. |
| Empty `catch (_: Exception) { }` | **33** | Several are ported verbatim from source and should stay. New ones in the repository layer should at least `Log.w`. |
| `runCatching` with no `onFailure` in `ui/` | 61 | Same concern — failures render as silently missing data. |
| Repositories constructed inside `@Composable` without `remember` | 4 | `RestrictedSettingsBanner:45`, `AccessibilityRestrictedRecovery:70,71`, `NuclearModeModal:157` — new instance per recomposition. |
| `LazyColumn items()` without `key =` | 3 of 17 | Causes needless recomposition and loses scroll position on data change. |
| R8 / ProGuard | — | `isMinifyEnabled = false`, rules file is one comment. Fine to defer; do not flip the flag without writing `kotlinx.serialization` and Room keep rules. |

---

# PART 10 — Feature-by-feature map

Against ARCHITECTURE §1's 22 confirmed features.

| Feature | State |
|---|---|
| Focus sessions | ✅ working — start/stop mirrors enforcement prefs correctly |
| App blocking (accessibility) | ⚠️ works, but **Shorts/Reels detection broken** by the `flagReportViewIds` drop (§4.1) |
| App blocking (UsageStats fallback poller) | ✅ ported |
| Network blocking (VPN) | 🔴 crashes on API 34+ (§5) |
| Daily allowance (count + interval) | ✅ both implementations, matching keys |
| Greyout mode | ⚠️ enforcement works; no typed model, UI is 302/892 lines |
| Nuclear mode | ⚠️ works, but device-admin policy set expanded (§4.3) |
| Blocked keywords/URLs | ⚠️ enforcement works; text scanning degraded by `flagIncludeNotImportantViews` drop |
| Standalone blocks | ⚠️ config works; **expiry never fires** (lives in the dead `NotificationRepository`) |
| PIN protection | ✅ complete, including legacy upgrade |
| Alarms | ✅ full fallback ladder |
| **Notifications** | 🔴 **entirely dark** — the port is complete but never instantiated (§6) |
| Home-screen widget | ⚠️ works; `widget_info.xml` regressions (§8.2) |
| Custom home launcher | ⚠️ works; wallpaper + pinned-apps keys broken (§8.7) |
| Aversive actions | ⚠️ fires on block; **UI toggles don't reach it** (§6) |
| Backup / restore | ⚠️ envelope correct; `userProfile`, `blockPresets`, 13 settings fields dropped on import |
| Preset import (`import-confirm`) | 🔴 **absent entirely** (§7.2) |
| Diagnostics / troubleshoot | ⚠️ present; `TroubleshootModal` at 4/10 interactions |
| Achievements / streaks | ✅ engine complete, 10 achievements, persistence working |
| Analytics / insights | ✅ engine complete — but the **UI** is skeleton-grade |
| Boot resilience | ✅ ported verbatim incl. clock-tamper |
| Task conflict / auto-reschedule | 🔴 **`SchedulerEngine` never called** (§6) |

---

# PART 11 — Revised order of work

Merging both passes. Items marked 🆕 are new this round.

**Phase 0 — make it build (hours)**
1. Fix 4 compile errors (§2) 🆕
2. `ksp { arg("room.schemaLocation", …) }`, then `./gradlew assembleDebug`

**Phase 1 — make it not destroy anything (days)**
3. `ForegroundTaskService` FGS type; `NetworkBlockerVpnService` FGS type (§5)
4. Restore `accessibility_service_config.xml` verbatim 🆕 — this one also *restores a broken feature*
5. Reduce `device_admin.xml` to `force-lock` only 🆕
6. Restore `accessibility_service_description` verbatim
7. Database path migration + WAL + recovery DB (§3.1)
8. Legacy `settings` blob one-time read 🆕 (§3.2)
9. `report_notes` migration (§3.3)
10. `versionCode` → 14; onboarding prefs file; launcher key mismatch

**Phase 2 — connect what's already built (days)**
11. `AppModule` gains `NotificationRepository`, `VpnRepository`, `AversionsController`, `InstalledAppsRepository`, `NuclearModeRepository`, `LauncherController`, `BackupManager`, `UsageStatsRepository`
12. Wire `NotificationRepository` into the alarm/service scheduling path 🆕 — biggest single functional win
13. Wire `SchedulerEngine` into `TaskViewModel.add/update/delete`
14. Enqueue `BackgroundFetchWorker` in `FocusFlowApp.onCreate()`
15. Wire `AversionsController` to settings changes 🆕
16. `ViewModelProvider.Factory`; delete the 21 `= viewModel()` defaults

**Phase 3 — restore missing features (1–2 weeks)**
17. `UserProfile` model + persistence 🆕
18. `PendingPresets` + `import-confirm` screen 🆕
19. `BlockPreset` persistence; `GreyoutWindow` model 🆕
20. The 5 missing `db*` functions 🆕
21. The 13 orphaned `AppSettings` fields; fix the 2 flipped defaults 🆕
22. `ui/theme/` package + dark mode + `values-night/`

**Phase 4 — UI fidelity (2–4 weeks)**
23. Rebuild the stats family against the 648-line source
24. Tier A screens: Defense (11/50), User Profile (15/43), Onboarding (9/22), Permissions (6/14), Task Detail (2/10), Edit Task (12/31), Troubleshoot (4/10), Quick Add (14/32), PIN Verify (5/11)
25. Remove the `"NEEDS: …"` string rendered to users in `BlockedAppOverlay.kt:44`
26. Tier B screens

**Phase 5 — hardening**
27. Tests: port `BlockedAppDismissalPolicyTest`, add a migration test against an *expo-path* fixture, `SchedulerEngine` unit tests, PIN upgrade test, `AnalyticsProcessor` 2-dp parity tests
28. `widget_info.xml` restore 🆕; `<queries>` element 🆕; launcher icon; `file://` deep links; `widget_add_task_bg.xml`
29. `protectedApps.ts` (never-blockable allowlist — safety-relevant) and the other 24 unassigned files
30. Externalise the 546 UI strings 🆕; spacing tokens

---

## Honest bottom line

The parts of this migration that were *assigned* to someone came out well — in several places
better than the original. What went wrong is almost entirely **unassigned work** and
**unconnected work**:

- three config files were rewritten from memory instead of copied, and two of them expand the app's
  permissions while a third silently breaks Shorts/Reels blocking;
- a 598-line notification system, a 443-line scheduler, a 147-line worker and a 69-line aversion
  controller were all built to spec and never given a call site;
- and two whole features (`UserProfile`, `PendingPresets`/`import-confirm`) appear in no stage
  prompt at all, so nobody was ever asked to build them.

None of that requires re-doing a stage. It is roughly a week to get to "safe to install over the
old app", and four to six weeks to reach genuine parity — with the stats UI as the long pole.
