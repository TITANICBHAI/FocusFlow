# FocusFlow Migration — Stage 4 (GPT Terra)
## Pipeline position: Gemini (done) → Replit (done) → Claude (done) → GPT Terra (you, now) → Claude (final review)

> **Hold this whole stage until Claude confirms Stage 3 is complete.** The ViewModel
> contract in Prompt 1 is a preview — verify against the real files before building
> against them.

This stage is split into **12 prompts** — 57 files is too much to track reliably in
one pass. Paste them one at a time, in order, into the same conversation. Wait for
each prompt's report before moving to the next. Prompt 1 carries the full context,
rules, and ViewModel contract; prompts 2 onward assume that's already live and just
give you the next screen family.

## Model setting
Set reasoning effort to **High** for this entire stage. Bump to **xhigh** specifically
where a prompt below flags it — those are the largest and most interaction-dense
source files, where a dropped feature is easiest to miss on a casual pass.

## You have direct file access — use it
You're running in Codex, with real read/write access to the project on disk. Don't
paste code into chat for the user to copy — write each Composable directly to its
target path. Read each source `.tsx` file directly from disk before converting it,
not from a pasted excerpt. The "paste this prompt into the conversation" framing
below refers to the 12 prompts in this document being fed to you one at a time, not
to the code you produce — that part goes straight to disk.

---

## PROMPT 1 of 12 — Read this in full, then build the Home screen family

### What this app is, and why fidelity matters here
FocusFlow is a published Android app that helps people break compulsive phone and app
use — enforced focus sessions, app blocking, a daily allowance, VPN-based network
blocking, a PIN gate on settings. Real users depend on this working. You're rebuilding
the UI, not the enforcement logic underneath it, but the UI is how a user actually
sets a block, starts a session, or confirms a PIN — if a screen silently drops a
toggle or a confirmation step that exists today, the feature it controlled becomes
unreachable even though the logic underneath still works. That failure is invisible
in a code review and only shows up when a real person can't find a setting they used
to have.

### Before you do anything
Read `focusflowkotlin/ARCHITECTURE.md`, sections 3.1 and 3.2, in full.

### What already exists in your workspace
Three real stages of work exist by now: `enforcement/` (Gemini), `data/repository/` +
`background/` + `notifications/` + `domain/` (Replit), and ViewModels wired to those
repositories (Claude). **Open the actual ViewModel files before writing a single
Composable** — the contract below is what to expect, not what to trust blindly.

### ViewModel contract (preview — verify against real files first)
```
TaskViewModel
  tasks: StateFlow<List<Task>>
  addTask(), updateTask(), deleteTask(), completeTask(taskId), skipTask(taskId),
  extendTaskTime(taskId, minutes)

SettingsViewModel
  settings: StateFlow<AppSettings>
  updateSettings(partial), setDailyAllowanceEntries(entries), setBlockedWords(words),
  setRecurringBlockSchedules(schedules), setStandaloneBlock(config),
  setQuickBlockTemporary(config), setStandaloneBlockAndAllowance(config)

FocusSessionViewModel
  focusSession: StateFlow<FocusSession?>
  focusViolationApp: StateFlow<String?>
  startFocusMode(taskId), stopFocusMode()

AppBootViewModel
  isLoading, isDbReady, isDbUnrecoverable: StateFlow<Boolean>
```
Obtain each via `androidx.lifecycle.viewmodel.compose.viewModel()`. Don't invent a
fifth ViewModel. If a screen needs data none of these expose, write
`// NEEDS: <what's missing>` and use a reasonable placeholder.

### The read-first gate — mandatory, per file, every prompt in this stage
Line-count matching doesn't apply here — you're rewriting, not copying. Instead:
1. Read the entire source file.
2. Before writing the Compose version, list every interactive element in the source:
   every button, toggle, text input, slider, modal trigger, navigation action. This
   is your own checklist, built before you start.
3. Once built, go back through your checklist and confirm each item has a
   corresponding element in your output. An unmatched item is a dropped feature, not
   a stylistic simplification — flag it if you believe it's genuinely redundant, but
   don't silently omit it.

### Rules — apply for the rest of this stage
- Material3 + `MaterialTheme.colorScheme` / `.typography` only. No hardcoded colors,
  `dp`, or `sp` values.
- Read for layout intent and information hierarchy, not to mirror `useState` 1:1.
- If a screen calls a native module you don't recognize, leave a TODO naming the call
  needed — don't fabricate what it does.
- Components are grouped below by which screen(s) they belong to, based on import
  patterns — verify against the actual import list in each source file, since a
  component used by more than one screen belongs in `ui/common/` instead of inside
  a single screen's file.

### Target
```
focusflowkotlin/app/src/main/java/com/tbtechs/focusflow/ui/
```
See `ARCHITECTURE.md` section 4 for the exact subfolder each item belongs in.

### This prompt's files — Home
Source (root-level paths, no `artifacts/` prefix):
```
app/(tabs)/index.tsx                    → ui/home/HomeScreen.kt        route: "home"
src/components/TaskCard.tsx             → ui/home/TaskCard.kt
src/components/TimelineView.tsx         → ui/home/TimelineView.kt
src/components/QuickAddModal.tsx        → ui/home/QuickAddModal.kt
src/components/EditTaskModal.tsx        → ui/home/EditTaskModal.kt
src/components/TaskDetailModal.tsx      → ui/home/TaskDetailModal.kt
```

### Report for Prompt 1
```
| Screen/component | Interactive elements found | Elements matched in output | Flags |
|---|---|---|---|
```

---

## PROMPT 2 of 12 — Focus screen family

Same rules and read-first gate as Prompt 1.
```
app/(tabs)/focus.tsx                    → ui/focus/FocusScreen.kt      route: "focus"
app/active.tsx                          → ui/focus/ActiveBlockScreen.kt route: "active"
src/components/ActiveHeaderButton.tsx   → ui/focus/ActiveHeaderButton.kt
src/components/ExtendModal.tsx          → ui/focus/ExtendModal.kt
```
### Report for Prompt 2
```
| Screen/component | Interactive elements found | Elements matched in output | Flags |
|---|---|---|---|
```

---

## PROMPT 3 of 12 — Stats screen family

**Set effort to xhigh for this entire prompt.** This is the most architecturally
different screen in the migration — the TS codebase has a full analytics engine and
a redesigned stats experience that is nothing like a simple data screen.

**Critical: read these source files before writing a single line of Kotlin:**
- `src/components/StatsInsightsExperience.tsx` (648 lines) — the main component
- `src/services/analytics/AnalyticsProcessor.ts` — the data shape it consumes
- `src/services/analytics/InsightEngine.ts` — how InsightCard[] is produced
- `src/components/SessionDebriefModal.tsx` (149 lines)
- `src/components/InsightsPanel.tsx` (104 lines)
- `app/report.tsx` (342 lines)

**What exists in the TS codebase (port these, don't rebuild from scratch):**

The stats screen is NOT `app/(tabs)/stats.tsx` using `UsageInsights.tsx` or
`WeeklyReportModal.tsx` — those are superseded. The real implementation is in
`StatsInsightsExperience.tsx`, which is a self-contained component with internal
sub-components. Port each sub-component as a separate Composable:

```
src/components/StatsInsightsExperience.tsx → ui/stats/
  StatsInsightsExperience (outer shell)  → StatsInsightsExperience.kt
  InsightCardView (internal)             → InsightCardView.kt
  PresenceStrip (internal)               → PresenceStrip.kt
  TrendChart (internal)                  → TrendChart.kt
  AchievementRow (internal)              → AchievementRow.kt
  TaskResultList (internal)              → TaskResultList.kt
  TaskSummary (internal)                 → TaskSummary.kt
  PhoneUsageSummary (internal)           → PhoneUsageSummary.kt
  DataHealthNotice (internal)            → DataHealthNotice.kt
  EmptyStatsState (internal)             → EmptyStatsState.kt
  PermissionGate (internal)              → PermissionGate.kt
  UnavailableGate (internal)             → UnavailableGate.kt
  LocalOnlyNotice (internal)             → LocalOnlyNotice.kt

src/components/InsightsPanel.tsx         → ui/stats/InsightsPanel.kt
src/components/SessionDebriefModal.tsx   → ui/focus/SessionDebriefModal.kt  (lives in focus/, it's triggered from focus sessions)
app/report.tsx                           → ui/stats/ReportScreen.kt         route: "report"
```

**ViewModel the stats screen consumes (from Track C, STAGE3):**
```
StatsViewModel
  analyticsSnapshot: StateFlow<AnalyticsSnapshot?>
  insightCards: StateFlow<List<InsightCard>>
  weeklyStandout: StateFlow<InsightCard?>
  achievementState: StateFlow<AchievementState?>
  loadState: StateFlow<StatsLoadState>   // loading | ready | permission | unavailable | error
  activeWindow: StateFlow<AnalyticsWindow>
  setWindow(window: AnalyticsWindow)
  reload()
```

**Key behaviors to preserve from the TS source:**
- Three-tab toggle: Yesterday / This Week / 3 Months (default: This Week)
- 3-Month tab: shows `PermissionGate` if `UsageStatsRepository.hasPermission()` is false
- Weekly standout card appears above the other insight cards only in the week view
- `DataHealthNotice` shows when `snapshot.sourceHealth` has degraded sources
- `AchievementRow` only shown for yesterday and week views, not 3-month
- `PresenceStrip` only shown in week view
- `TaskResultList` only shown in yesterday view
- `TaskSummary` only shown in week view
- `PhoneUsageSummary` and `TrendChart` only shown in 3-month view
- `InsightCard` with category `nothing_to_report` must render with label "OBSERVATION"
  not the category name (confirmed in source at line 349)

**Two gates that are easy to miss and both matter:**
- `PermissionGate` fires when the user hasn't granted UsageStats permission yet —
  it shows a button that opens settings.
- `UnavailableGate` is different: it fires when the *build itself* can't read hourly
  UsageStats at all (a capability check, not a permission check). Confirmed as a
  separate component in source, line 242 — don't collapse these into one gate, they
  have different causes and different copy.

**`LocalOnlyNotice` is a dismissible privacy notice** ("Your data stays here...
FocusFlow does not collect, upload, or share your analytics.") — directly tied to
the no-API-calls design decision made earlier in this project. Port it faithfully,
including the dismiss behavior; don't treat it as optional chrome.

**`hourLabel(hour: number): String` is a utility function, not a Composable** — a
small formatter (converts `14` to `"2pm"` or similar). Port it as a plain Kotlin
function in the same file or a shared utils file, not as a component.

**Do not port `UsageInsights.tsx` or `WeeklyReportModal.tsx`** — these are
superseded by the new stats experience and not referenced anywhere meaningful.

### Report for Prompt 3
```
| Component | Source lines | Target file | Key behaviors verified | Flags |
|---|---|---|---|---|
```

---

## PROMPT 4 of 12 — Settings screen family

Same rules as Prompt 1. **Set effort to xhigh for `DailyAllowanceModal`** — it
directly controls the app's core enforcement mechanism, and a dropped option here
means a user can't configure how the app limits their own usage.
```
app/(tabs)/settings.tsx                 → ui/settings/SettingsScreen.kt  route: "settings"
src/components/DarkModeToggle.tsx       → ui/settings/DarkModeToggle.kt
src/components/OverlayAppearanceModal.tsx → ui/settings/OverlayAppearanceModal.kt
src/components/DailyAllowanceModal.tsx  → ui/settings/DailyAllowanceModal.kt
```
### Report for Prompt 4
```
| Screen/component | Interactive elements found | Elements matched in output | Flags |
|---|---|---|---|
```

---

## PROMPT 5 of 12 — Defense screen family

Same rules as Prompt 1. **Set effort to xhigh for `block-defense.tsx` and
`StandaloneBlockModal`** — these configure the standalone-block feature, one of the
more complex flows in the app.
```
app/(tabs)/defense.tsx                  → ui/defense/DefenseScreen.kt         route: "defense"
app/block-defense.tsx                   → ui/defense/StandaloneBlockSetupScreen.kt  route: "block_defense"
app/keyword-blocker.tsx                 → ui/keyword/KeywordBlockerScreen.kt  route: "keyword_blocker"
src/components/StandaloneBlockModal.tsx → ui/defense/StandaloneBlockModal.kt
src/components/NuclearModeModal.tsx     → ui/defense/NuclearModeModal.kt
src/components/GreyoutScheduleModal.tsx → ui/defense/GreyoutScheduleModal.kt
src/components/BlockedWordsModal.tsx    → ui/defense/BlockedWordsModal.kt
src/components/BlockedAppOverlay.tsx    → ui/defense/BlockedAppOverlay.kt
```
### Report for Prompt 5
```
| Screen/component | Interactive elements found | Elements matched in output | Flags |
|---|---|---|---|
```

---

## PROMPT 6 of 12 — Onboarding and permissions

Same rules as Prompt 1. **Set effort to xhigh for `onboarding.tsx`** — it's one of
the largest source files and the first thing every new user sees; a dropped step
here affects every single install.
```
app/onboarding.tsx                      → ui/onboarding/OnboardingScreen.kt   route: "onboarding"
app/permissions.tsx                     → ui/permissions/PermissionsScreen.kt route: "permissions"
src/components/RestrictedSettingsBanner.tsx        → ui/permissions/RestrictedSettingsBanner.kt
src/components/AccessibilityRestrictedRecovery.tsx → ui/permissions/AccessibilityRestrictedRecovery.kt
```
### Report for Prompt 6
```
| Screen/component | Interactive elements found | Elements matched in output | Flags |
|---|---|---|---|
```

---

## PROMPT 7 of 12 — Launcher setup and block list

Same rules as Prompt 1. **Set effort to xhigh for `home-launcher.tsx`** — it's one of
the largest source files.
```
app/home-launcher.tsx                   → ui/launchersetup/LauncherSetupScreen.kt route: "home_launcher_setup"
app/vpn-block-list.tsx                  → ui/blocklist/VpnBlockListScreen.kt      route: "vpn_block_list"
src/components/AppPickerSheet.tsx       → ui/blocklist/AppPickerSheet.kt
src/components/AllowedAppsModal.tsx     → ui/blocklist/AllowedAppsModal.kt
src/components/QuickBlockSheet.tsx      → ui/blocklist/QuickBlockSheet.kt
```
### Report for Prompt 7
```
| Screen/component | Interactive elements found | Elements matched in output | Flags |
|---|---|---|---|
```

---

## PROMPT 8 of 12 — Always-on / network

Same rules as Prompt 1.
```
app/always-on.tsx                       → ui/alwayson/AlwaysOnScreen.kt        route: "always_on"
src/components/VpnConsentModal.tsx      → ui/alwayson/VpnConsentModal.kt
src/components/VpnPermissionLostBanner.tsx → ui/alwayson/VpnPermissionLostBanner.kt
```
### Report for Prompt 8
```
| Screen/component | Interactive elements found | Elements matched in output | Flags |
|---|---|---|---|
```

---

## PROMPT 9 of 12 — Profile and PIN

Same rules as Prompt 1. **Set effort to xhigh for `user-profile.tsx` and all three
PIN modals** — PIN logic gates every settings change in this app; a dropped
confirmation step here is a security regression, not just a UX one.
```
app/user-profile.tsx                    → ui/profile/UserProfileScreen.kt       route: "user_profile"
app/password-protection.tsx             → ui/profile/PasswordProtectionScreen.kt route: "password_protection"
src/components/PinSetupModal.tsx        → ui/profile/PinSetupModal.kt
src/components/PinVerifyModal.tsx       → ui/profile/PinVerifyModal.kt
src/components/PinRotationModal.tsx     → ui/profile/PinRotationModal.kt
```
### Report for Prompt 9
```
| Screen/component | Interactive elements found | Elements matched in output | Flags |
|---|---|---|---|
```

---

## PROMPT 10 of 12 — Legal and support

Same rules as Prompt 1. **Set effort to xhigh for `changelog.tsx`** — one of the
larger source files.
```
app/privacy-policy.tsx                  → ui/legal/PrivacyPolicyScreen.kt      route: "privacy_policy"
app/terms-of-service.tsx                → ui/legal/TermsOfServiceScreen.kt     route: "terms_of_service"
app/how-to-use.tsx                      → ui/support/HowToUseScreen.kt         route: "how_to_use"
app/changelog.tsx                       → ui/support/ChangelogScreen.kt        route: "changelog"
src/components/DiagnosticsModal.tsx     → ui/support/DiagnosticsModal.kt
src/components/TroubleshootModal.tsx    → ui/support/TroubleshootModal.kt
src/components/ReportIssueModal.tsx     → ui/support/ReportIssueModal.kt
```
### Report for Prompt 10
```
| Screen/component | Interactive elements found | Elements matched in output | Flags |
|---|---|---|---|
```

---

## PROMPT 11 of 12 — Shared components and NavGraph assembly

Same rules as Prompt 1. These components are used across multiple screens — that's
why they're grouped separately rather than folded into one screen's file above.
```
src/components/ErrorBoundary.tsx              → ui/common/ErrorBoundary.kt
src/components/ErrorFallback.tsx              → ui/common/ErrorFallback.kt
src/components/ErrorAlertBanner.tsx           → ui/common/ErrorAlertBanner.kt
src/components/SideMenu.tsx                   → ui/common/SideMenu.kt
src/components/AchievementCelebrationModal.tsx → ui/common/AchievementCelebrationModal.kt
src/components/withScreenErrorBoundary.tsx    → ui/common/withScreenErrorBoundary.kt
components/KeyboardAwareScrollViewCompat.tsx  → ui/common/KeyboardAwareScrollViewCompat.kt
```
Also: assemble `ui/navigation/FocusFlowNavGraph.kt` and `ui/navigation/Routes.kt` now
that every screen from Prompts 1–10 exists — wire every route string listed across
this whole stage into one NavHost. Cross-check against `ARCHITECTURE.md` section 3.1
for completeness; every route in that table needs an entry here.

### Report for Prompt 11
```
| Screen/component | Interactive elements found | Elements matched in output | Flags |
|---|---|---|---|
```
Plus: confirm every route string from ARCHITECTURE.md section 3.1 appears in the
NavGraph — list any that don't.

---

## PROMPT 12 of 12 — One consolidated final report

Pull together every report from Prompts 1–11 into one table, so this stage hands off
a single clean document instead of eleven scattered ones.
```
| Screen/component | Interactive elements found | Elements matched in output | Flags |
|---|---|---|---|
```
List all 57 files. Include every flag raised across all eleven prompts underneath,
even ones already mentioned — this is the version that gets handed to Claude for
final review, and it should be complete on its own.
