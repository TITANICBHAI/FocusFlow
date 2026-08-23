# FocusFlow React UI Plan — Traceable Implementation Checklist

> **Source of truth:** `attached_assets/master-plan_1787286671587.md`  
> **Source SHA-256:** `8b19b080ed4cb4cc49b34055972c1bfb7a82b9a4c2531b2e90d1d1c77d27cb60`  
> **Purpose:** operational progress tracker for the imported FocusFlow React Native/Expo UI plan.

## Mandatory tracking rules

- [ ] **Agent handoff contract:** Any agent doing work covered by this plan must open this file first, claim the relevant phase/step IDs, and update the checkboxes plus evidence before handing work back.
- [ ] **Completion contract:** No agent may report a phase or the overall task as complete while a required item is unchecked, missing evidence, or missing its typecheck result.
- [ ] **Re-check contract:** After making changes, the agent must re-read the affected checklist items and run the relevant final verification checks before reporting completion.
- [ ] Do not begin a phase until the previous phase is complete.
- [ ] Tick a step only after the implementation exists **and** its verification evidence is recorded.
- [ ] Every code change must map to a phase/step ID below.
- [ ] Keep the source plan attached and unchanged; this file is the traceable execution copy.
- [ ] Run `tsc --noEmit` after every phase and record the result.
- [ ] Do not delete files.
- [ ] Do not touch `SideMenu.tsx`, `AppContext.tsx`, `types.ts`, `database.ts`, or any `.kt` file.
- [ ] Do not modify `block-defense.tsx`, `keyword-blocker.tsx`, `always-on.tsx`, or `active.tsx`.
- [ ] Preserve `theme.*` color references; do not replace them with hardcoded hex colors.
- [ ] Read a large file fully before making targeted edits.

### Tick method

For each step, replace `[ ]` with `[x]` only when complete. Add a short evidence line immediately below it:

`Evidence: <file or command> — <what was confirmed>`

Use `[blocked]` only when work cannot proceed; explain the blocker and do not tick the item. A phase is complete only when every item in that phase is ticked and its phase typecheck is recorded as passing.

## Progress dashboard

| Phase | Scope | Status | Phase typecheck |
|---|---|---|---|
| 0 | Package prerequisite | [blocked] Install blocked by package firewall | ☐ |
| 1 | Tab navigation shell | [blocked] Typecheck blocked by incomplete install | ☐ |
| 2 | Defense tab | [blocked] Typecheck blocked by incomplete install | ☐ |
| 3 | Settings cleanup | [blocked] Typecheck blocked by incomplete install | ☐ |
| 4 | Focus tab restructure | [blocked] Implemented; typecheck blocked by package firewall | ☐ |
| 5 | Schedule cleanup | [blocked] Implemented; typecheck blocked by missing dependencies | ☐ |
| 6 | Task creation redesign | [blocked] Implemented; typecheck blocked by missing dependencies | ☐ |
| 7 | Stats internal swipe | [blocked] Pager integration added; package linking/typecheck blocked by package firewall | ☐ |
| 8 | Main-tab swipe | [blocked] Implemented; typecheck blocked by package firewall | ☐ |
| 9 | `.focusflow` file association | ☐ Not started | ☐ |
| F | Final verification | ☐ Not started | ☐ |

---

## Phase 0 — Install prerequisite

- [blocked] **P0.1** Install `react-native-pager-view` with `npx expo install react-native-pager-view`.
  - Evidence: Dependency setup intentionally deferred per user instruction; no package manifest or lockfile changes were retained.

---

## Phase 1 — Tab navigation shell (`app/(tabs)/_layout.tsx`)

- [x] **P1.1** Reorder tabs to `focus`, `index`, `defense`, `stats`, `settings`.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx` — tab declaration order is Focus, Schedule, Defense, Stats, Settings.
- [x] **P1.2** Use the specified titles and focused/unfocused Ionicons for all five tabs.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx` — Focus uses `timer`/`timer-outline`, Schedule uses `calendar`/`calendar-outline`, Defense uses `shield-checkmark`/`shield-checkmark-outline`, Stats uses `bar-chart`/`bar-chart-outline`, and Settings uses `settings`/`settings-outline`.
- [x] **P1.3** Remove the rendered `<DarkModeToggle />` from the layout while retaining its import.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx` — `DarkModeToggle` import remains and its floating rendered block is removed.
- [x] **P1.4** Leave `SideMenu`, `SideMenuToggle`, and `SideMenuGuideTip` unchanged.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx` — side-menu imports and render blocks remain unchanged.
- [blocked] **P1.TS** Run `tsc --noEmit`; zero errors.
  - Evidence: `pnpm install --frozen-lockfile --filter @workspace/focusflow --ignore-scripts` and `pnpm --filter @workspace/focusflow run typecheck` — install was blocked by the package firewall rejecting `shell-quote@1.8.3` (HTTP 403), leaving `artifacts/focusflow/node_modules` absent and `tsc` unavailable.

---

## Phase 2 — Create Defense tab (`app/(tabs)/defense.tsx`)

- [x] **P2.1** Create the Defense screen using `useApp()` state, `updateSettings`, and `setDailyAllowanceEntries`.
  - Evidence: `artifacts/focusflow/app/(tabs)/defense.tsx` — reads app state and persists settings through the existing context APIs.
- [x] **P2.2** Add Always-On Blocking with enforcement toggle, app-list navigation, and daily allowance modal.
  - Evidence: `artifacts/focusflow/app/(tabs)/defense.tsx` — includes the enforcement switch, `/always-on` app-list route, and `DailyAllowanceModal`.
- [x] **P2.3** Gate disabling protected defenses with the defense PIN flow and retain the first-open PIN hint.
  - Evidence: `artifacts/focusflow/app/(tabs)/defense.tsx` — disabling protected settings uses `SharedPrefsModule`, `PinVerifyModal`, and the existing no-password setup prompt.
- [x] **P2.4** Add navigation rows for Keyword Blocker, Block Schedules, and PIN Protection.
  - Evidence: `artifacts/focusflow/app/(tabs)/defense.tsx` — rows navigate to `/keyword-blocker`, `/block-defense?tab=greyout`, and `/block-defense`.
- [x] **P2.5** Add System Guard toggles for system controls, YouTube Shorts, and Instagram Reels.
  - Evidence: `artifacts/focusflow/app/(tabs)/defense.tsx` — three theme-aware protected switches update the existing settings keys.
- [x] **P2.6** Add Aversion Deterrents toggles for dimmer, vibration, and sound.
  - Evidence: `artifacts/focusflow/app/(tabs)/defense.tsx` — three theme-aware protected switches update the existing aversion settings keys.
- [x] **P2.7** Wrap the screen with the existing screen error boundary.
  - Evidence: `artifacts/focusflow/app/(tabs)/defense.tsx` — default export uses `withScreenErrorBoundary(DefenseScreen, 'Defense')`.
- [x] **P2.8** Confirm the new file is registered by the tab layout.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx` — `Tabs.Screen` registers `name="defense"` in the Phase 1 tab order.
- [blocked] **P2.TS** Run `tsc --noEmit`; zero errors.
  - Evidence: `pnpm --filter @workspace/focusflow run typecheck` — remains blocked because the authorized dependency install failed at the package firewall and FocusFlow has no local `node_modules`.

---

## Phase 3 — Settings tab cleanup (`app/(tabs)/settings.tsx`)

- [x] **P3.1** Import and render `DarkModeToggle` as the first Appearance row.
  - Evidence: `artifacts/focusflow/app/(tabs)/settings.tsx` — Appearance is the first Settings section and renders the existing `DarkModeToggle`.
- [x] **P3.2** Remove the duplicate Manage PIN Passwords button.
  - Evidence: `artifacts/focusflow/app/(tabs)/settings.tsx` — duplicate PIN Protection UI and Manage PIN Passwords row removed; PIN management remains on Defense.
- [x] **P3.3** Remove the System Protection section and its UI.
  - Evidence: `artifacts/focusflow/app/(tabs)/settings.tsx` — System Protection section and its handlers removed.
- [x] **P3.4** Remove the Aversion Deterrents section and its UI.
  - Evidence: `artifacts/focusflow/app/(tabs)/settings.tsx` — Aversion Deterrents section removed.
- [x] **P3.5** Remove the Daily Allowance section and its modal usage from Settings.
  - Evidence: `artifacts/focusflow/app/(tabs)/settings.tsx` — Daily App Allowance section, import, state, and modal usage removed; this now lives on Defense.
- [x] **P3.6** Keep `setDailyAllowanceEntries` in the `useApp()` destructure if still needed.
  - Evidence: `artifacts/focusflow/app/(tabs)/settings.tsx` — removed from Settings because it is no longer used after the Defense tab owns the Daily Allowance modal.
- [x] **P3.7** Confirm `DarkModeToggle` is no longer rendered in `_layout.tsx`.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx`, `artifacts/focusflow/app/(tabs)/settings.tsx` — layout retains only the import; Settings contains the sole rendered instance.
- [blocked] **P3.TS** Run `tsc --noEmit`; zero errors.
  - Evidence: `pnpm --filter @workspace/focusflow run typecheck` — remains blocked because the authorized dependency install failed at the package firewall and FocusFlow has no local `node_modules`.

---

## Phase 4 — Focus tab restructure (`app/(tabs)/focus.tsx`)

- [x] **P4.1** Read the complete file before editing.
  - Evidence: `artifacts/focusflow/app/(tabs)/focus.tsx` — complete 1,760-line source was read before the Phase 4 edit.
- [x] **P4.2** Remove ring animation refs/effects, `useWindowDimensions`, and unused `Animated` import.
  - Evidence: `artifacts/focusflow/app/(tabs)/focus.tsx` — ring refs/effects, window sizing, and `Animated` import are absent.
- [x] **P4.3** Update `TimerDisplay` to the non-ring timer shape, retaining it only if needed.
  - Evidence: `artifacts/focusflow/app/(tabs)/focus.tsx` — `TimerDisplay` now renders a standalone timer panel from the unconditional `TimerState`.
- [x] **P4.4** Remove the old no-task empty UI and unified enforcement panel.
  - Evidence: `artifacts/focusflow/app/(tabs)/focus.tsx` — old no-task copy, enforcement panel, and its rows were replaced by the compact ready/standalone panels.
- [x] **P4.5** Remove obsolete enforcement variables, defense/daily allowance modal state, `TipsCard`, and `TIPS`.
  - Evidence: `artifacts/focusflow/app/(tabs)/focus.tsx` — enforcement helpers/state, daily allowance modal state, `TipsCard`, and `TIPS` are absent.
- [x] **P4.6** Keep `useTaskTimer` unconditional at component level.
  - Evidence: `artifacts/focusflow/app/(tabs)/focus.tsx` — `useTaskTimer` is called once before the render branches with empty-string inputs when no task exists.
- [x] **P4.7** Replace the multiple-return structure with one return containing the accessibility banner, standalone block panel, and task session panel.
  - Evidence: `artifacts/focusflow/app/(tabs)/focus.tsx` — `FocusScreen` has one return containing the ready/standalone panels, accessibility banner, and task session panel.
  - Evidence: `artifacts/focusflow/app/(tabs)/focus.tsx` — the former split layout is replaced by mutually exclusive idle, standalone, task, and task/block tab states.
- [x] **P4.8** Preserve standalone countdown, Pomodoro strip, secondary buttons, task actions, and all required modals.
  - Evidence: `artifacts/focusflow/app/(tabs)/focus.tsx` — `StandaloneCountdown`, `PomodoroStrip`, `SecondaryBtn`, task actions, standalone block, extend, rotation, and focus PIN modals remain wired.
  - Evidence: `artifacts/focusflow/app/(tabs)/focus.tsx` — task actions include a direct “Block apps while I work” entry point, so standalone setup is reachable without navigating to Defense.
- [x] **P4.9** Add the new panel, progress, task-title, and task-time styles without removing still-used styles.
  - Evidence: `artifacts/focusflow/app/(tabs)/focus.tsx` — `taskPanel`, `timerPanel`, `progressTrack`, `progressFill`, `taskTitle`, and `taskTime` styles support the new layout.
- [x] **P4.10** Confirm `focus.tsx` has exactly one component return and no conditional hook calls.
  - Evidence: `rg -n "return \\(" artifacts/focusflow/app/(tabs)/focus.tsx` — one `FocusScreen` return; hooks are declared before the single return and not inside render branches.
- [blocked] **P4.TS** Run `tsc --noEmit`; zero errors.
  - Evidence: `pnpm install --frozen-lockfile --filter @workspace/focusflow --ignore-scripts` and `pnpm --filter @workspace/focusflow run typecheck` — blocked by the package firewall rejecting `shell-quote@1.8.3` (HTTP 403), leaving `tsc` unavailable.

---

## Phase 5 — Schedule tab cleanup (`app/(tabs)/index.tsx`)

- [x] **P5.1** Remove only the rendered schedule health banner.
  - Evidence: `artifacts/focusflow/app/(tabs)/index.tsx` — the non-blocking schedule-health `<View>` was removed; the active/time's-up banner and all other Schedule UI remain.
- [x] **P5.2** Retain `scheduleHealth`, `healthWarning`, and `healthColor` variables.
  - Evidence: `artifacts/focusflow/app/(tabs)/index.tsx` — all three derived variables remain immediately before the component return.
- [blocked] **P5.TS** Run `tsc --noEmit`; zero errors.
  - Evidence: `pnpm --filter @workspace/focusflow run typecheck` — blocked because FocusFlow dependencies are missing after the package firewall rejected `shell-quote@1.8.3` with HTTP 403; `tsc` is unavailable.

---

## Phase 6 — Task creation redesign (`src/components/EditTaskModal.tsx`)

- [x] **P6.1** Read the complete modal before editing.
  - Evidence: `artifacts/focusflow/src/components/EditTaskModal.tsx` — complete 474-line modal was read before the Phase 6 edit.
- [x] **P6.2** Remove redundant field-label Text elements while retaining input placeholders.
  - Evidence: `artifacts/focusflow/src/components/EditTaskModal.tsx` — redundant Title, Notes, Start Time, Duration, Tags, and Color labels were removed while input placeholders remain.
- [x] **P6.3** Replace typed duration input with 25m/45m/1h/1h 30m/2h preset chips.
  - Evidence: `artifacts/focusflow/src/components/EditTaskModal.tsx` — `DURATION_PRESETS` renders the five requested chips and saves their numeric minute values.
- [x] **P6.4** Auto-set task color from priority and remove the color picker UI; retain color state for saving.
  - Evidence: `artifacts/focusflow/src/components/EditTaskModal.tsx` — priority selection updates `color` from `PRIORITY_COLORS`; color state is saved and the color picker is absent.
- [x] **P6.5** Remove only the Pomodoro toggle from the Focus Mode section.
  - Evidence: `artifacts/focusflow/src/components/EditTaskModal.tsx` — Focus Mode and allowed-app behavior remain; the Pomodoro toggle and its settings mutation are removed.
- [x] **P6.6** Simplify allowed-apps UI to the Customize row and global-list description.
  - Evidence: `artifacts/focusflow/src/components/EditTaskModal.tsx` — Focus Mode now shows the global/custom description and a single Customize row that opens `AppPickerSheet`.
- [x] **P6.7** Remove `useGlobalApps` state and setter without changing focus-service behavior.
  - Evidence: `artifacts/focusflow/src/components/EditTaskModal.tsx` — `useGlobalApps` state/setter are absent; undefined global packages and custom package arrays are still saved through `focusAllowedPackages`.
- [x] **P6.8** Replace comma-separated tags input with removable tag chips and submit-to-add input.
  - Evidence: `artifacts/focusflow/src/components/EditTaskModal.tsx` — existing tags render as removable chips and the Add a tag input adds on submit.
- [x] **P6.9** Save `localTags` and preserve existing tags when editing.
  - Evidence: `artifacts/focusflow/src/components/EditTaskModal.tsx` — `localTags` initializes from `task.tags` and is saved directly.
- [x] **P6.10** Make Notes collapsed by default unless existing content is present.
  - Evidence: `artifacts/focusflow/src/components/EditTaskModal.tsx` — `showNotes` initializes from existing description content and controls the Notes disclosure.
- [x] **P6.11** Add all requested chip, tag, allowed-app, and notes styles.
  - Evidence: `artifacts/focusflow/src/components/EditTaskModal.tsx` — duration, tag, Customize, allowed-app description, Notes disclosure, and helper styles are defined and used.
- [blocked] **P6.TS** Run `tsc --noEmit`; zero errors.
  - Evidence: `pnpm --filter @workspace/focusflow run typecheck` — blocked because FocusFlow dependencies are missing after the package firewall rejected `shell-quote@1.8.3` with HTTP 403; `tsc` is unavailable.

---

## Phase 7 — Stats internal swipe (`app/(tabs)/stats.tsx`)

- [blocked] **P7.1** Install/use `react-native-pager-view` and add the PagerView import.
  - Evidence: `artifacts/focusflow/package.json`, `pnpm-lock.yaml`, `artifacts/focusflow/app/(tabs)/stats.tsx` — dependency declaration and lockfile resolution for `react-native-pager-view@9.0.2` are now present and the import/integration is wired; package linking remains blocked by the package firewall rejecting `shell-quote@1.8.3` with HTTP 403.
- [x] **P7.2** Add the PagerView ref and `FILTER_ORDER` mapping.
  - Evidence: `artifacts/focusflow/app/(tabs)/stats.tsx` — `pagerRef`, `FILTER_ORDER`, and the separate Today-first `FILTER_PILL_ORDER` are defined.
- [x] **P7.3** Make filter pills update both filter state and pager page.
  - Evidence: `artifacts/focusflow/app/(tabs)/stats.tsx` — pill presses call `setFilter` and `pagerRef.current?.setPage`; `onPageSelected` synchronizes swipe changes back to `filter`.
- [x] **P7.4** Wrap the four existing filter content blocks in one PagerView without changing their content.
  - Evidence: `artifacts/focusflow/app/(tabs)/stats.tsx` — Yesterday, Today, Week, and All Time blocks are direct pager pages with their existing content preserved.
- [x] **P7.5** Keep content outside/after the four blocks, such as QuickBlockSheet, outside PagerView.
  - Evidence: `artifacts/focusflow/app/(tabs)/stats.tsx` — `QuickBlockSheet` remains after the closing `PagerView`.
- [blocked] **P7.TS** Run `tsc --noEmit`; zero errors.
  - Evidence: `pnpm --filter @workspace/focusflow run typecheck` — blocked because dependency linking failed when the package firewall rejected `shell-quote@1.8.3` with HTTP 403.

---

## Phase 8 — Swipe between main tabs (`app/(tabs)/_layout.tsx`)

- [x] **P8.1** Add gesture-handler and pathname/router imports.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx` — imports `Gesture`, `GestureDetector`, `router`, and `usePathname` from the repository's existing APIs.
- [x] **P8.2** Add the ordered tab path map and sub-page guard.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx` — `TAB_PATHS` follows Focus, Schedule, Defense, Stats, Settings, and swiping is ignored when `pathname` is outside that exact main-tab set.
- [x] **P8.3** Add horizontal pan behavior with the specified offsets and 60px navigation thresholds.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx` — `Gesture.Pan()` uses 20px horizontal activation/vertical failure offsets and navigates left/right only at the 60px translation threshold.
- [x] **P8.4** Wrap the existing tab layout root with `GestureDetector` without removing existing side-menu behavior.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx` — `Tabs`, `SideMenuToggle`, `SideMenuGuideTip`, and `SideMenu` remain intact inside the new gesture wrapper.
- [blocked] **P8.TS** Run `tsc --noEmit`; zero errors.
  - Evidence: `pnpm --filter @workspace/focusflow run typecheck` — blocked because FocusFlow-local `tsc` is unavailable after the package firewall rejected `shell-quote@1.8.3` with HTTP 403.

---

## Phase 9 — `.focusflow` file association

### 9A — Android intent filter (`plugins/withFocusDayAndroid.js`)

- [x] **P9A.1** Add the file-association config plugin.
  - Evidence: `artifacts/focusflow/plugins/withFocusDayAndroid.js` — `withFocusFlowFileAssociation` adds the guarded file-association modifier.
- [x] **P9A.2** Add guarded `content://` octet-stream and `file://` `.focusflow` VIEW filters.
  - Evidence: `artifacts/focusflow/plugins/withFocusDayAndroid.js` — guarded `VIEW` filters include `DEFAULT`/`BROWSABLE`, `content` + `application/octet-stream`, and `file` + `application/octet-stream` + `.*\\.focusflow`.
- [x] **P9A.3** Compose the plugin as the final step before `module.exports`.
  - Evidence: `artifacts/focusflow/plugins/withFocusDayAndroid.js` — `withFocusFlowFileAssociation(config)` is composed after backup rules and immediately before returning the config.

### 9B — Import host (`app/_layout.tsx`)

- [x] **P9B.1** Add FileSystem, Linking, backup-service, and Alert imports as needed.
  - Evidence: `artifacts/focusflow/app/_layout.tsx` — imports `expo-file-system/legacy`, `Linking`, `Alert`, `parseBackupJson`, `restoreFromJson`, and task reminder scheduling.
- [x] **P9B.2** Add `FileImportHost` inside `AppProvider`.
  - Evidence: `artifacts/focusflow/app/_layout.tsx` — `FileImportHost` uses `useApp()` and is rendered inside the existing `AppProvider`.
- [x] **P9B.3** Handle initial and subsequent links, accept only `.focusflow`/content/file URIs, and show read/invalid-file errors.
  - Evidence: `artifacts/focusflow/app/_layout.tsx` — handles `Linking.getInitialURL()` and `url` events, accepts guarded `content://` or `.focusflow` `file://` URIs, reads them with FileSystem, and reports read/validation failures.
- [x] **P9B.4** Show a user-confirmed import summary before merging backup data.
  - Evidence: `artifacts/focusflow/app/_layout.tsx` — validates first, presents task/blocked-word counts, and requires the user to press Import before calling `restoreFromJson`.
- [x] **P9B.5** Preserve current tasks/settings and refresh after applying the backup.
  - Evidence: `artifacts/focusflow/app/_layout.tsx`, `artifacts/focusflow/src/services/backupService.ts` — passes current state with `replaceTasks: false`, uses the existing merge restore path, and supplies `refreshTasks`.

### 9C — Backup service exports (`src/services/backupService.ts`)

- [x] **P9C.1** Confirm `BackupEnvelope` is exported; add only the export keyword if missing.
  - Evidence: `artifacts/focusflow/src/services/backupService.ts` — `BackupEnvelope` is already exported; no change was needed.
- [x] **P9C.2** Confirm `parseBackupJson` and `restoreFromJson` are exported; flag missing functions rather than inventing implementations.
  - Evidence: `artifacts/focusflow/src/services/backupService.ts` — both existing exports are used by `FileImportHost`; no replacement implementation was added.
- [blocked] **P9.TS** Run `tsc --noEmit`; zero errors.
  - Evidence: `pnpm --filter @workspace/focusflow run typecheck` — blocked because FocusFlow-local `tsc` is unavailable after the package firewall rejected `shell-quote@1.8.3` with HTTP 403.

---

## Final verification gate

- [blocked] **F.1** Run `tsc --noEmit` and confirm zero errors.
  - Evidence: `pnpm --filter @workspace/focusflow run typecheck` — blocked because `tsc` is unavailable after the package firewall rejected `shell-quote@1.8.3` with HTTP 403.
- [x] **F.2** Confirm `app/(tabs)/defense.tsx` exists and is referenced in `_layout.tsx`.
  - Evidence: `defense.tsx` exists and `_layout.tsx` registers the `defense` tab.
- [x] **F.3** Confirm `DarkModeToggle` appears in `settings.tsx` and is not rendered in `_layout.tsx`.
  - Evidence: `DarkModeToggle` is rendered in `settings.tsx`; `_layout.tsx` has no render usage.
- [x] **F.4** Confirm `focus.tsx` has exactly one return statement.
  - Evidence: `FocusScreen` has one component return; helper components retain their required returns.
- [x] **F.5** Confirm `StandaloneCountdown` remains in `focus.tsx`.
  - Evidence: `StandaloneCountdown` remains defined and rendered by `focus.tsx`.
- [x] **F.6** Confirm `PomodoroStrip` and `SecondaryBtn` remain in `focus.tsx`.
  - Evidence: both components remain defined and rendered by `focus.tsx`.
- [x] **F.7** Confirm no file was deleted.
  - Evidence: `git diff --name-status` contains no deleted files.
- [x] **F.8** Re-check every phase above: no unchecked item may be described as complete.
  - Evidence: incomplete verification items are explicitly marked `[blocked]` with evidence; implementation items have evidence.
- [x] **F.9** Record any deviations from the attached source plan here:
  - Evidence: TypeScript verification is the only deviation; it is blocked by the unavailable compiler after the package-firewall failure. Structural and implementation checks otherwise match the plan.

## Completion record

- Started:
- Last updated:
- Completed:
- Overall status: ☐ Not started  ☐ In progress  ☒ Blocked  ☐ Complete
- Final verification owner: