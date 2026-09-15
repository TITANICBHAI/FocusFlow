# FocusFlow Migration — Stage 4 Consolidated Handoff

This is the consolidated handoff for the 57 source/target entries listed in
Prompts 1–11 of `STAGE4_GPT_TERRA_PROMPT_1789453940535.md`.

The individual Prompt 1–10 report sections in the prompt document are empty in
the current workspace. This report therefore records the current source/Kotlin
audit, including unresolved `NEEDS` markers and all known navigation, path, and
fidelity flags. It does not claim device or Gradle verification.

`app/(tabs)/stats.tsx` is a one-line export of `StatsInsightsExperience` and is
not counted as a separate target row. `app/reports.tsx` is the older
compatibility redirect described in `ARCHITECTURE.md`; Prompt 3 separately
requires `app/report.tsx`. That route discrepancy is recorded below.

## Consolidated file report

| Screen/component | Interactive elements found | Elements matched in output | Flags |
|---|---|---|---|
| `app/(tabs)/index.tsx` → `ui/home/HomeScreen.kt` | Database retry; active-task banner; complete, skip, extend, start-focus; task selection; pull-to-refresh; add-task FAB; task modals; delete PIN | Main actions and composed modals are present | `F-HOME-1`, `F-HOME-2`, `F-DIMENSIONS` |
| `src/components/TaskCard.tsx` → `ui/home/TaskCard.kt` | Open details; complete; skip; extend; start focus | Present in the Kotlin card | Task action fidelity should be checked together with `HomeScreen`; `F-DIMENSIONS` |
| `src/components/TimelineView.tsx` → `ui/home/TimelineView.kt` | Timeline/task selection and scrolling | Present | No separate unresolved marker found |
| `src/components/QuickAddModal.tsx` → `ui/home/QuickAddModal.kt` | Title/time/date/duration inputs; focus toggle; save/cancel | Core form is present | Missing Pomodoro fields and allowed-app/preset state; `F-HOME-2`, `F-DIMENSIONS` |
| `src/components/EditTaskModal.tsx` → `ui/home/EditTaskModal.kt` | Edit task fields; save/cancel/delete-related actions | Present | Verify source delete/PIN flow against `HomeScreen`; `F-DIMENSIONS` |
| `src/components/TaskDetailModal.tsx` → `ui/home/TaskDetailModal.kt` | Close; complete; skip; extend; start focus; edit | Present | No separate unresolved marker found |
| `app/(tabs)/focus.tsx` → `ui/focus/FocusScreen.kt` | Task/session controls; focus activation; stop/break; PIN-related actions; defense hints; navigation | Main focus UI is present | Several source capabilities remain explicit `NEEDS`; `F-FOCUS-1`, `F-FOCUS-2`, `F-FOCUS-3`, `F-DIMENSIONS` |
| `app/active.tsx` → `ui/focus/ActiveBlockScreen.kt` | Active-block controls; stop/allow actions; VPN/allowance/defense navigation | Main active-block screen is present | Missing ViewModel data for allowance, VPN status, PIN verification, installed apps, and persisted focus stats; `F-ACTIVE-1`, `F-ACTIVE-2`, `F-DIMENSIONS` |
| `src/components/ActiveHeaderButton.tsx` → `ui/focus/ActiveHeaderButton.kt` | Header navigation/action button | Present | No separate unresolved marker found |
| `src/components/ExtendModal.tsx` → `ui/focus/ExtendModal.kt` | Duration selection; confirm; cancel/close | Present | No separate unresolved marker found |
| `src/components/StatsInsightsExperience.tsx` → `ui/stats/StatsInsightsExperience.kt` | Yesterday/week/3-month tabs; local notice dismissal; reload; permission settings; insight-card actions | Main experience and tab/gate structure are present | Privacy-notice dismissal is not persisted; stats route naming differs from architecture; `F-STATS-1`, `F-NAV-2`, `F-DIMENSIONS` |
| `src/components/InsightsPanel.tsx` → `ui/stats/InsightsPanel.kt` | Insight display and any card actions | Present | Confirm all source card actions against the current analytics model |
| `src/components/SessionDebriefModal.tsx` → `ui/focus/SessionDebriefModal.kt` | Save/close/debrief actions | Present | No separate unresolved marker found |
| `app/report.tsx` → `ui/stats/ReportScreen.kt` | Report period selection; note inputs; save/close/navigation | Screen exists and is used by the `reports` destination | Required Prompt 3 route `"report"` is missing; report-note repository methods are marked `NEEDS`; `F-NAV-1`, `F-REPORT-1`, `F-DIMENSIONS` |
| `app/(tabs)/settings.tsx` → `ui/settings/SettingsScreen.kt` | Settings toggles; navigation; backup export/import; PIN-gated changes; support/legal actions | Main settings surface is present | Several settings fields and backup callbacks are explicit `NEEDS`; `F-SETTINGS-1`, `F-SETTINGS-2`, `F-DIMENSIONS` |
| `src/components/DarkModeToggle.tsx` → `ui/settings/DarkModeToggle.kt` | Theme toggle | Present | App-level theme state/persistence is not exposed by the current settings model; `F-SETTINGS-1` |
| `src/components/OverlayAppearanceModal.tsx` → `ui/settings/OverlayAppearanceModal.kt` | Quote/wallpaper/custom appearance controls; save/cancel | Present | Verify native picker callback and overlay permission behavior |
| `src/components/DailyAllowanceModal.tsx` → `ui/settings/DailyAllowanceModal.kt` | Allowance mode; package selection; count/interval inputs; save/cancel/reset | Partial | Installed-app picker, allowance mode fields, and live usage state are marked `NEEDS`; `F-ALLOWANCE-1`, `F-ALLOWANCE-2`, `F-ALLOWANCE-3`, `F-DIMENSIONS` |
| `app/(tabs)/defense.tsx` → `ui/defense/DefenseScreen.kt` | Defense toggles; standalone block; nuclear mode; greyout; blocked words; navigation | Main defense surface and modals are present | At least one source setting cannot persist because the model/repository field is missing; `F-DEFENSE-1`, `F-DIMENSIONS` |
| `app/block-defense.tsx` → `ui/defense/StandaloneBlockSetupScreen.kt` | Package selection; duration/allowance setup; start/cancel | Present | High-risk standalone-block flow requires source-level fidelity review |
| `app/keyword-blocker.tsx` → `ui/keyword/KeywordBlockerScreen.kt` | Keyword input/list editing; save/delete/navigation | Present | No separate unresolved marker found |
| `src/components/StandaloneBlockModal.tsx` → `ui/defense/StandaloneBlockModal.kt` | Package selection; duration; start/cancel | Present | High-risk flow; verify every option and PIN gate |
| `src/components/NuclearModeModal.tsx` → `ui/defense/NuclearModeModal.kt` | Enable/disable; uninstall/package actions; confirm/cancel | Present | Verify native uninstall failure and confirmation behavior |
| `src/components/GreyoutScheduleModal.tsx` → `ui/defense/GreyoutScheduleModal.kt` | Schedule fields; enable/disable; save/cancel | Present | No separate unresolved marker found |
| `src/components/BlockedWordsModal.tsx` → `ui/defense/BlockedWordsModal.kt` | Add/edit/delete blocked words; save/close | Present | No separate unresolved marker found |
| `src/components/BlockedAppOverlay.tsx` → `ui/defense/BlockedAppOverlay.kt` | Dismiss/return action; overlay action controls | Target exists | Original TSX source is absent from the workspace; Kotlin explicitly says `NEEDS` original source and native dismissal actions; `F-SOURCE-1`, `F-BLOCKED-1` |
| `app/onboarding.tsx` → `ui/onboarding/OnboardingScreen.kt` | Step navigation; permission/setup actions; finish/skip | Present | High-risk first-run flow; verify all source steps and non-blocking recommendations |
| `app/permissions.tsx` → `ui/permissions/PermissionsScreen.kt` | Permission cards; system settings launchers; retry/recheck; launcher setup | Present | Native/device behavior remains source-level only; `F-VERIFY-1` |
| `src/components/RestrictedSettingsBanner.tsx` → `ui/permissions/RestrictedSettingsBanner.kt` | Open restricted settings; dismiss/retry | Present | Verify restricted-settings recovery state |
| `src/components/AccessibilityRestrictedRecovery.tsx` → `ui/permissions/AccessibilityRestrictedRecovery.kt` | Retry/open settings; recheck accessibility | Present | Must remain visible until accessibility is actually granted; `F-PERMISSION-1` |
| `app/home-launcher.tsx` → `ui/launchersetup/LauncherSetupScreen.kt` | Default-launcher setup; app search/selection; theme/dock/hidden apps; save/back | Functionally represented under `ui/launcher/` | Target package differs from Prompt 7/architecture (`launchersetup` vs `launcher`); `F-PATH-1`, `F-DIMENSIONS` |
| `app/vpn-block-list.tsx` → `ui/blocklist/VpnBlockListScreen.kt` | App search/selection; enable/disable; select all/clear; save/back | Functionally represented under `ui/launcher/` | Target package differs from Prompt 7/architecture (`blocklist` vs `launcher`); `F-PATH-2`, `F-DIMENSIONS` |
| `src/components/AppPickerSheet.tsx` → `ui/blocklist/AppPickerSheet.kt` | Search; app selection; presets; confirm/cancel | Functionally represented under `ui/launcher/` | Target package differs; verify installed-app ViewModel data and all selection presets; `F-PATH-2`, `F-DIMENSIONS` |
| `src/components/AllowedAppsModal.tsx` → `ui/blocklist/AllowedAppsModal.kt` | Allowed-app selection; presets; save/cancel | Functionally represented under `ui/launcher/` | Target package differs; source-equivalent installed-app picker is marked `NEEDS`; `F-PATH-2`, `F-HOME-2` |
| `src/components/QuickBlockSheet.tsx` → `ui/blocklist/QuickBlockSheet.kt` | Quick block duration/package actions; confirm/cancel | Functionally represented under `ui/launcher/` | Target package differs; `F-PATH-2` |
| `app/always-on.tsx` → `ui/alwayson/AlwaysOnScreen.kt` | VPN/always-on toggle; package selection; consent; permission recovery; back | Present | App search uses a normal Compose placeholder; device VPN behavior requires device verification; `F-VERIFY-1`, `F-DIMENSIONS` |
| `src/components/VpnConsentModal.tsx` → `ui/alwayson/VpnConsentModal.kt` | Consent/continue/cancel | Present | No separate unresolved marker found |
| `src/components/VpnPermissionLostBanner.tsx` → `ui/alwayson/VpnPermissionLostBanner.kt` | Open VPN settings; dismiss/recover | Present and globally hosted | Verify watchdog/recovery behavior on device; `F-VERIFY-1` |
| `app/user-profile.tsx` → `ui/profile/UserProfileScreen.kt` | Profile fields; export/import backup; save/back; PIN-related actions | Present | Default-duration fields, notification repository wiring, and backup picker callback are marked `NEEDS`; `F-PROFILE-1`, `F-PROFILE-2`, `F-PROFILE-3`, `F-DIMENSIONS` |
| `app/password-protection.tsx` → `ui/profile/PasswordProtectionScreen.kt` | Enable/disable PIN; setup/verify/rotate; save/back | Present | PIN flow is security-critical and requires device-level verification |
| `src/components/PinSetupModal.tsx` → `ui/profile/PinSetupModal.kt` | PIN/password entry; confirmation; validation; cancel/submit | Present | Verify reuse prevention and exact source validation |
| `src/components/PinVerifyModal.tsx` → `ui/profile/PinVerifyModal.kt` | PIN entry; verify; cancel/lockout behavior | Present | Security-critical; verify authorized bypass remains separate |
| `src/components/PinRotationModal.tsx` → `ui/profile/PinRotationModal.kt` | Choose generated/custom PIN; fields; confirm/back/cancel | Present | Security-critical; verify generated/custom parity |
| `app/privacy-policy.tsx` → `ui/legal/PrivacyPolicyScreen.kt` | Scroll; accept/decline/back | Present | No separate unresolved marker found |
| `app/terms-of-service.tsx` → `ui/legal/TermsOfServiceScreen.kt` | Scroll/back/accept-related navigation | Present | No separate unresolved marker found |
| `app/how-to-use.tsx` → `ui/support/HowToUseScreen.kt` | Guide navigation; get started/back | Present | No separate unresolved marker found |
| `app/changelog.tsx` → `ui/support/ChangelogScreen.kt` | Scroll/back/entry actions | Present | No separate unresolved marker found |
| `src/components/DiagnosticsModal.tsx` → `ui/support/DiagnosticsModal.kt` | Close; copy/share/export diagnostic information | Present through root error-banner callback | Verify exact source sharing/export behavior |
| `src/components/TroubleshootModal.tsx` → `ui/support/TroubleshootModal.kt` | Brand selection; step navigation; close | Present | No separate unresolved marker found |
| `src/components/ReportIssueModal.tsx` → `ui/support/ReportIssueModal.kt` | Issue text input; submit/close | Present | No separate unresolved marker found |
| `src/components/ErrorBoundary.tsx` → `ui/common/ErrorBoundary.kt` | No direct controls; fallback/report path | Partial | Compose `try/catch` is not a full React error-boundary equivalent; `F-COMMON-1` |
| `src/components/ErrorFallback.tsx` → `ui/common/ErrorFallback.kt` | Retry; view error details; copy/share logs; report; close details; back dismissal | Partial | Error-details modal is dropped; share became clipboard copy; close-app behavior is new; `F-COMMON-2`, `F-DIMENSIONS` |
| `src/components/ErrorAlertBanner.tsx` → `ui/common/ErrorAlertBanner.kt` | View logs; dismiss; Android back/request-close | Present in root integration | Internal diagnostics presentation is delegated to `MainActivity`; verify overlay behavior above every destination; `F-COMMON-3`, `F-DIMENSIONS` |
| `src/components/SideMenu.tsx` → `ui/common/SideMenu.kt` | Shared-route navigation; drawer close | Present | Source file is absent from the workspace, so exact source checklist cannot be completed; routes are hardcoded instead of using `Routes`; `F-SOURCE-2`, `F-COMMON-4`, `F-DIMENSIONS` |
| `src/components/AchievementCelebrationModal.tsx` → `ui/common/AchievementCelebrationModal.kt` | Close; keep going; system back dismissal | Core actions present | Milestone-specific copy, day label, confetti, rotation, and entrance animations are not ported; `F-COMMON-5`, `F-DIMENSIONS` |
| `src/components/withScreenErrorBoundary.tsx` → `ui/common/withScreenErrorBoundary.kt` | No direct controls; wraps a screen | Conceptually present | Kotlin wrapper is a composable function, not the source HOC API; screen graph uses `ErrorBoundary` directly |
| `components/KeyboardAwareScrollViewCompat.tsx` → `ui/common/KeyboardAwareScrollViewCompat.kt` | No direct controls; keyboard-aware scrolling | Conceptually present | Source shim is absent from the workspace, so exact behavior cannot be checked; `F-SOURCE-3` |

## Consolidated flag ledger

### `F-NAV-1` — Prompt route `"report"` is missing

Prompt 3 explicitly assigns `app/report.tsx` the route `"report"`. `Routes.kt`
and `FocusFlowNavGraph.kt` only define/register `"reports"`. The architecture
table contains `"reports"` for `app/reports.tsx`, which is a compatibility
redirect to the Stats route. Both requirements cannot be considered satisfied
until the route discrepancy is resolved.

### `F-NAV-2` — Architecture screen names and current targets differ

`ARCHITECTURE.md` names `StatsScreen` and `ReportsScreen`, while the current
graph uses `StatsInsightsExperience` and `ReportScreen`. The latter may be the
intended Prompt 3 redesign, but the naming mismatch must be explicitly accepted
by final review.

### `F-PATH-1` / `F-PATH-2` — Prompt 7 package paths differ

Prompt 7 and architecture section 4 specify `ui/launchersetup/` and
`ui/blocklist/`. The current files are under `ui/launcher/`, including both the
launcher setup screen and VPN block-list/picker components. Functionality is
wired, but the requested target paths are not exact.

### `F-SOURCE-1` / `F-SOURCE-2` / `F-SOURCE-3` — Source files unavailable

The current workspace does not contain:

- `src/components/BlockedAppOverlay.tsx`
- `src/components/SideMenu.tsx`
- `components/KeyboardAwareScrollViewCompat.tsx`

The Kotlin targets exist for the latter two and the blocked overlay, but the
mandatory source read-first comparison cannot be completed for those files.

### `F-HOME-1` / `F-HOME-2` — Home and task-model gaps

The Home implementation contains explicit `NEEDS` markers for task refresh
semantics, focus-session PIN verification, Pomodoro settings, global allowed-app
state, and installed-app-backed allowed-app selection. These are not safe to
silently treat as complete UI ports.

### `F-FOCUS-1` / `F-FOCUS-2` / `F-FOCUS-3`

The Focus screen contains unresolved markers for focus-defense hint persistence,
focus-session PIN verification/rotation, and recording focus overrides before
stopping a session. The exact source controls are rendered, but their backing
contracts are incomplete.

### `F-ACTIVE-1` / `F-ACTIVE-2`

The Active Block screen lacks ViewModel-exposed data for allowance usage,
rolling interval reset, VPN status/failed packages/policy generation,
focus-session PIN verification, installed-app labels/icons, and persisted
focus-minute/override queries.

### `F-STATS-1` / `F-REPORT-1`

Stats privacy-notice dismissal is not persisted. `ReportScreen` contains
`NEEDS` markers for report-note persistence and daily report-note lookup.

### `F-SETTINGS-1` / `F-SETTINGS-2`

Settings contains unresolved contracts for app-level theme persistence,
task-reminder preference, default task duration, auto-focus, allowed-focus
packages, Pomodoro fields, backup export wiring, and backup import wiring.

### `F-ALLOWANCE-1` / `F-ALLOWANCE-2` / `F-ALLOWANCE-3`

Daily allowance UI still needs an installed-app-backed picker, complete
count/interval entry model fields, and a live enforcement usage StateFlow.
The UI should not invent remaining allowance values.

### `F-DEFENSE-1` / `F-BLOCKED-1`

Defense contains a setting that cannot currently persist because its model or
repository field is missing. `BlockedAppOverlay.kt` explicitly requires the
original source and native dismissal actions.

### `F-PERMISSION-1`

Accessibility restricted recovery must remain visible until Accessibility is
actually granted, not merely after returning from Android settings.

### `F-PROFILE-1` / `F-PROFILE-2` / `F-PROFILE-3`

User profile still needs default-duration model fields, notification repository
wiring for morning/weekly notifications, and a connected native backup picker
callback before import can be considered complete.

### `F-COMMON-1` through `F-COMMON-5`

- `ErrorBoundary`: Compose `try/catch` is only a partial error-boundary model.
- `ErrorFallback`: source error-details modal and share behavior are missing or
  changed.
- `ErrorAlertBanner`: diagnostics are delegated externally; global overlay
  placement needs device verification.
- `SideMenu`: source is unavailable and routes are hardcoded.
- `AchievementCelebrationModal`: source milestone copy and animation behavior
  are not ported.

### `F-DIMENSIONS`

The stage rules prohibit hardcoded `dp`/`sp` values. Hardcoded dimensions remain
in the shared components and navigation graph, including `16.dp`, `24.dp`,
`12.dp`, `14.dp`, and related values.

### `F-VERIFY-1`

VPN, launcher, permissions, accessibility recovery, PIN, alarm, and overlay
flows require Android/device verification. The current shell has no Java
runtime and the Kotlin project has no `gradlew`, so Gradle or device validation
was not possible.

## Navigation cross-check

All 20 concrete route strings from `ARCHITECTURE.md` §3.1 are represented in
`Routes.kt` and registered in `FocusFlowNavGraph.kt`:

`home`, `focus`, `stats`, `settings`, `defense`, `active`, `always_on`,
`block_defense`, `changelog`, `home_launcher_setup`, `how_to_use`,
`keyword_blocker`, `onboarding`, `password_protection`, `permissions`,
`privacy_policy`, `reports`, `terms_of_service`, `user_profile`,
`vpn_block_list`.

The architecture route cross-check has no missing entries. The separate Prompt
3 route `"report"` remains missing, as recorded under `F-NAV-1`.

## Verification status

- Static file and source/Kotlin comparison: completed.
- Route registration cross-check: completed.
- Source-level `NEEDS` marker audit: completed.
- Kotlin/Gradle build: not run; Java and `gradlew` are unavailable.
- Android emulator/device verification: not run.